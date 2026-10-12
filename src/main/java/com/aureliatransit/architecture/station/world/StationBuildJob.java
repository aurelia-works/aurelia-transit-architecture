package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.WorldOp;
import com.aureliatransit.architecture.station.world.BlockStateResolver.Prepared;
import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.SignData;
import com.aureliatransit.architecture.text.TextAlignment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SpreadableBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds the ops of a survey (or undoes a build) in small batches, driven by {@link StationSessions}. Every cell is probed
 * again just before it is written; the first surprise stops the job and keeps the undo log. A cell in an unloaded chunk
 * pauses the job, it never loads the chunk.
 */
public final class StationBuildJob {

	/** Ops one job may run in a tick. */
	public static final int OPS_PER_TICK = 64;
	/** Consecutive ticks a job may wait for a chunk before it gives up. */
	public static final int MAX_PAUSED_TICKS = 200;

	public enum Mode {
		BUILD,
		UNDO
	}

	public enum State {
		RUNNING,
		DONE,
		CANCELLED,
		/** Ended early by a changed cell, a block that did not stay, or a chunk that stayed unloaded. */
		STOPPED
	}

	private enum Step {
		DONE,
		WAIT,
		STOP
	}

	private final ServerWorld world;
	private final WorldSiteProbe probe;
	private final Mode mode;
	private final UUID owner;
	private final Consumer<Text> out;
	private final List<Prepared> ops;
	private final boolean approved;
	private final UndoLog log;
	private final int total;
	/** What each cell will hold, so a block is checked against the neighbours it is planned to have (a lower half its upper). */
	private final Map<BlockPos, BlockState> planned = new HashMap<>();
	private final List<BlockPos> signCells = new ArrayList<>();
	private final List<String> signTexts = new ArrayList<>();

	private State state = State.RUNNING;
	private int done;
	private int pausedTicks;
	private boolean paused;
	private int placed;
	private int terrainChanged;
	private int restored;
	private int skipped;
	private int keptNodes;
	private String stopReason = "";

	private StationBuildJob(ServerWorld world, UUID owner, Mode mode, List<Prepared> ops, boolean approved, UndoLog log, int total, Consumer<Text> out) {
		this.world = world;
		this.probe = new WorldSiteProbe(world);
		this.owner = owner;
		this.mode = mode;
		this.ops = ops;
		this.approved = approved;
		this.log = log;
		this.total = total;
		this.out = out;
		for (final Prepared prepared : ops) {
			if (prepared.op().kind() != WorldOp.Kind.CLEAR) {
				planned.put(new BlockPos(prepared.op().x(), prepared.op().y(), prepared.op().z()), prepared.state());
			}
		}
	}

	/** @throws IllegalArgumentException when an op names a block that does not exist */
	public static StationBuildJob build(ServerWorld world, UUID owner, List<WorldOp> ops, boolean approved, Consumer<Text> out) {
		final BlockStateResolver.Resolution resolution = BlockStateResolver.resolve(ops);
		if (!resolution.ok()) {
			throw new IllegalArgumentException("unknown blocks: " + String.join(", ", resolution.missing()));
		}
		return build(world, owner, resolution, approved, out);
	}

	public static StationBuildJob build(ServerWorld world, UUID owner, BlockStateResolver.Resolution resolution, boolean approved, Consumer<Text> out) {
		if (!resolution.ok()) {
			throw new IllegalArgumentException("unknown blocks: " + String.join(", ", resolution.missing()));
		}
		return new StationBuildJob(world, owner, Mode.BUILD, resolution.ops(), approved, new UndoLog(world.getRegistryKey()), resolution.ops().size(), out);
	}

	/** Undoes the log's entries from the newest back; the log shrinks as cells are handled. */
	public static StationBuildJob undo(ServerWorld world, UUID owner, UndoLog log, Consumer<Text> out) {
		return new StationBuildJob(world, owner, Mode.UNDO, List.of(), true, log, log.size(), out);
	}

	public UUID owner() {
		return owner;
	}

	public Mode mode() {
		return mode;
	}

	public State state() {
		return state;
	}

	public boolean finished() {
		return state != State.RUNNING;
	}

	/** Ops (or undo entries) handled so far, including skipped ones. */
	public int done() {
		return done;
	}

	public int total() {
		return total;
	}

	/** True while the job waits for an unloaded chunk. */
	public boolean paused() {
		return paused;
	}

	/** Blocks written by a build (PLACE and FILL ops). */
	public int placed() {
		return placed;
	}

	/** Cells a build changed that were not air before. */
	public int terrainChanged() {
		return terrainChanged;
	}

	public int restored() {
		return restored;
	}

	public int skipped() {
		return skipped;
	}

	/** Whether this build may change terrain (the player approved it explicitly). */
	public boolean approved() {
		return approved;
	}

	/** Undo only: rail nodes left in place because the player connected them in MTR after the build. */
	public int keptNodes() {
		return keptNodes;
	}

	public String stopReason() {
		return stopReason;
	}

	public UndoLog undoLog() {
		return log;
	}

	/** Runs at most {@code budget} ops and returns how many it handled. */
	public int step(int budget) {
		if (state != State.RUNNING) {
			return 0;
		}
		int used = 0;
		boolean blocked = false;
		while (used < budget && done < total) {
			final Step step = mode == Mode.BUILD ? buildNext() : undoNext();
			if (step == Step.WAIT) {
				blocked = true;
				break;
			}
			if (step == Step.STOP) {
				return used;
			}
			used++;
		}
		paused = blocked;
		if (used > 0) {
			pausedTicks = 0;
		} else if (blocked && ++pausedTicks >= MAX_PAUSED_TICKS) {
			stop("Stopped: the area stayed unloaded for " + MAX_PAUSED_TICKS / 20 + " s. ", false);
			return used;
		}
		if (done >= total) {
			finish();
		}
		return used;
	}

	/** Stops between batches; what is done stays and is covered by the undo log. */
	public void cancel() {
		if (state != State.RUNNING) {
			return;
		}
		state = State.CANCELLED;
		paused = false;
		if (mode == Mode.BUILD) {
			say(Formatting.YELLOW, "Build cancelled at " + done + "/" + total + ": " + placed + " blocks placed, " + terrainChanged
					+ " terrain cells changed." + (log.isEmpty() ? "" : " /ata_station undo reverts them."));
		} else {
			say(Formatting.YELLOW, "Undo cancelled at " + done + "/" + total + ": " + restored + " restored, " + skipped
					+ " skipped. /ata_station undo continues.");
		}
	}

	// ---- build ------------------------------------------------------------------------------------------------------

	private Step buildNext() {
		final Prepared prepared = ops.get(done);
		final WorldOp op = prepared.op();
		if (!probe.loaded(op.x(), op.z())) {
			return Step.WAIT;
		}
		final CellKind kind = probe.kind(op.x(), op.y(), op.z());
		if (op.kind() == WorldOp.Kind.CLEAR && kind == CellKind.AIR) {
			done++;
			return Step.DONE;
		}
		if (!allowed(op.kind(), kind)) {
			stop("Stopped at " + op.x() + " " + op.y() + " " + op.z() + ": found " + probe.describe(op.x(), op.y(), op.z()) + " where "
					+ op.blockId() + " was planned (" + kind.name().toLowerCase(java.util.Locale.ROOT)
					+ "). The cell changed since the survey; nothing more was placed. ", true);
			return Step.STOP;
		}
		final BlockPos pos = new BlockPos(op.x(), op.y(), op.z());
		final BlockState before = world.getBlockState(pos);
		final BlockState wanted = postProcessAsPlanned(prepared.state(), pos);
		if (wanted.getBlock() != prepared.state().getBlock()) {
			stop("Stopped at " + op.x() + " " + op.y() + " " + op.z() + ": " + op.blockId() + " would not stay there next to its neighbours; nothing more was placed. ", true);
			return Step.STOP;
		}
		final BlockState below = world.getBlockState(pos.down());
		if (below.getBlock() instanceof SpreadableBlock && !wanted.isAir()) {
			log.coverSoil(pos.down(), below);
		}
		world.setBlockState(pos, wanted, Block.NOTIFY_ALL);
		final BlockState after = world.getBlockState(pos);
		log.add(new UndoLog.Entry(pos, before, after));
		done++;
		if (op.kind() != WorldOp.Kind.CLEAR) {
			placed++;
		}
		if (!before.isAir()) {
			terrainChanged++;
		}
		if (after.getBlock() != wanted.getBlock()) {
			stop("Stopped at " + op.x() + " " + op.y() + " " + op.z() + ": " + op.blockId() + " was removed again by a neighbour update; nothing more was placed. ", true);
			return Step.STOP;
		}
		if (op.kind() == WorldOp.Kind.PLACE && !op.text().isEmpty() && after.getBlock() instanceof TextSignBlock) {
			signCells.add(pos);
			signTexts.add(op.text());
		}
		return Step.DONE;
	}

	/**
	 * {@link Block#postProcessState} against the finished station: a neighbour cell that is still air but planned counts as its
	 * planned block. Without this the lower half of a two-high block (MTR's ticket machine) turns to air before its upper half
	 * is placed.
	 */
	private BlockState postProcessAsPlanned(BlockState state, BlockPos pos) {
		BlockState result = state;
		for (final Direction direction : Direction.values()) {
			final BlockPos neighbour = pos.offset(direction);
			BlockState there = world.getBlockState(neighbour);
			final BlockState future = planned.get(neighbour);
			if (future != null && there.isAir()) {
				there = future;
			}
			result = result.getStateForNeighborUpdate(direction, there, world, pos, neighbour);
		}
		return result;
	}

	private boolean allowed(WorldOp.Kind kind, CellKind cell) {
		return switch (kind) {
			case PLACE -> cell == CellKind.AIR || (approved && (cell == CellKind.SOFT || cell == CellKind.SOLID || cell == CellKind.FLUID));
			case FILL, CLEAR -> approved && cell != CellKind.PROTECTED && cell != CellKind.RAIL_NODE && cell != CellKind.UNLOADED && cell != CellKind.OUT_OF_WORLD;
		};
	}

	private void stop(String reason, boolean undoHint) {
		state = State.STOPPED;
		paused = false;
		stopReason = reason;
		final String tail = mode == Mode.BUILD
				? done + " of " + total + " ops done." + (undoHint && !log.isEmpty() ? " /ata_station undo reverts them." : "")
				: restored + " restored, " + skipped + " skipped, " + (total - done) + " left; /ata_station undo continues.";
		say(Formatting.RED, reason + tail);
	}

	private void finish() {
		state = State.DONE;
		paused = false;
		if (mode == Mode.BUILD) {
			nameSigns();
			out.accept(Text.literal("Station built: " + placed + " blocks placed, " + terrainChanged + " terrain cells changed. ").formatted(Formatting.GREEN)
					.append(StationText.button("Undo", "/ata_station undo", Formatting.YELLOW, "Take the whole station away again")));
		} else {
			say(Formatting.GREEN, "Undo finished: " + restored + " cells restored"
					+ (keptNodes > 0 ? ", " + keptNodes + " rail nodes kept (connected in MTR since the build)" : "")
					+ (skipped > 0 ? ", " + skipped + " skipped (changed since the build)" : "") + ".");
		}
	}

	/** Name text goes to the block entity of each joined row's owner, once, after the whole row exists. */
	private void nameSigns() {
		final Set<BlockPos> owners = new HashSet<>();
		for (int i = 0; i < signCells.size(); i++) {
			final BlockPos pos = signCells.get(i);
			if (!probe.loaded(pos.getX(), pos.getZ())) {
				continue;
			}
			final BlockState sign = world.getBlockState(pos);
			if (!(sign.getBlock() instanceof TextSignBlock block)) {
				continue;
			}
			final BlockPos rowOwner = block.findRowOwner(world, pos, sign);
			if (probe.loaded(rowOwner.getX(), rowOwner.getZ()) && owners.add(rowOwner) && world.getBlockEntity(rowOwner) instanceof TextSignBlockEntity entity) {
				entity.setData(new SignData(signTexts.get(i), "", TextAlignment.CENTER, AccentPalette.NONE, SignArrow.NONE, "", false, List.of()));
			}
		}
	}

	// ---- undo -------------------------------------------------------------------------------------------------------

	private Step undoNext() {
		final UndoLog.Entry entry = log.last();
		final BlockPos pos = entry.pos();
		if (!probe.loaded(pos.getX(), pos.getZ())) {
			return Step.WAIT;
		}
		log.removeLast();
		done++;
		final BlockState current = world.getBlockState(pos);
		if (connectedNode(current, entry.placed())) {
			keptNodes++;
		} else if (stillPlaced(current, entry.placed())) {
			final BlockState processed = Block.postProcessState(entry.previous(), world, pos);
			world.setBlockState(pos, processed.getBlock() == entry.previous().getBlock() ? processed : entry.previous(), Block.NOTIFY_ALL);
			restored++;
			restoreSoil(pos.down());
		} else if (current.getBlock() == entry.previous().getBlock()) {
			restored++; // a neighbour put it back already (the lower half of a two-high block goes with its upper half)
		} else {
			skipped++;
		}
		return Step.DONE;
	}

	/** Grass or mycelium the build covered and vanilla turned into dirt comes back once nothing of ours stands on it. */
	private void restoreSoil(BlockPos pos) {
		final BlockState soil = log.coveredSoil(pos);
		if (soil != null && world.getBlockState(pos).isOf(Blocks.DIRT) && !world.getBlockState(pos.up()).isOpaqueFullCube(world, pos.up())) {
			world.setBlockState(pos, soil, Block.NOTIFY_ALL);
		}
	}

	/**
	 * True when a cell still holds the block the build wrote. Join states (sign rows) change as neighbours come and go, so the
	 * block counts, not the exact state; but a rail node the player connected in MTR ({@code is_connected} set) stays, because
	 * removing it with setBlockState would orphan MTR's rail data.
	 */
	/** A rail node the build placed unconnected that MTR has connected since. */
	static boolean connectedNode(BlockState current, BlockState placed) {
		if (current.getBlock() != placed.getBlock()) {
			return false;
		}
		final Property<?> connected = current.getBlock().getStateManager().getProperty("is_connected");
		return connected != null && !current.get(connected).equals(placed.get(connected));
	}

	static boolean stillPlaced(BlockState current, BlockState placed) {
		if (current.getBlock() != placed.getBlock()) {
			return false;
		}
		final Property<?> connected = current.getBlock().getStateManager().getProperty("is_connected");
		return connected == null || current.get(connected).equals(placed.get(connected));
	}

	private void say(Formatting colour, String message) {
		out.accept(Text.literal(message).formatted(colour));
	}
}
