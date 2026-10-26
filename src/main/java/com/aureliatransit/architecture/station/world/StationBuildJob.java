package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.RebuildDiff;
import com.aureliatransit.architecture.station.StylePalette;
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
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.state.property.Properties;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds the ops of a survey (or undoes a build) in small batches, driven by {@link StationSessions}. Every cell is probed
 * again just before it is written; the first surprise stops the job and keeps the undo log. The probe loads the chunks it
 * needs and holds a bounded window of them ({@link WorldSiteProbe}), so a station longer than the view distance builds and a
 * site of any size streams through; a batch ends after {@link #LOADS_PER_STEP} chunk loads so loading stays spread over
 * ticks. The job only pauses if a chunk still is not loaded. Once every op is done, the finishing steps (escalator shapes,
 * screens, signs, lifts, MTR) run in batches too, and the job lets go of its chunks when it ends.
 */
public final class StationBuildJob {

	/** Ops one job runs in a batch; the sessions run batches until the tick's time budget is used. */
	public static final int OPS_PER_TICK = 64;
	/** Chunks a batch may load before it ends early; loading or generating one costs milliseconds. */
	public static final int LOADS_PER_STEP = 4;
	/** Consecutive ticks a job may wait for a chunk before it gives up. */
	public static final int MAX_PAUSED_TICKS = 200;

	public enum Mode {
		BUILD,
		UNDO,
		/** A build at the spot of the last one that writes only what changed: first the cells the new plan leaves out, then the rest. */
		REBUILD
	}

	public enum State {
		RUNNING,
		DONE,
		CANCELLED,
		/** Ended early by a changed cell, a block that did not stay, or a chunk that stayed unloaded. */
		STOPPED
	}

	/** The planned blocks the finishing steps look for. */
	private enum Part {
		NONE,
		LIFT_STOP,
		LIFT_BUTTONS,
		ESCALATOR
	}

	private enum Step {
		DONE,
		WAIT,
		STOP
	}

	private final ServerWorld world;
	private WorldSiteProbe probe;
	private final Mode mode;
	private final UUID owner;
	private final Consumer<Text> out;
	private final List<Prepared> ops;
	private final boolean approved;
	private final UndoLog log;
	/** The MTR station area to register when the build finishes; null for none (undo, or plain op lists). */
	private final StationZone zone;
	private final int total;
	/** What each cell will hold, so a block is checked against the neighbours it is planned to have (a lower half its upper). */
	private final Map<BlockPos, BlockState> planned = new HashMap<>();
	private final List<BlockPos> signCells = new ArrayList<>();
	/** What each distinct planned block is to the finishing steps, worked out once per block instead of once per cell. */
	private final Map<Block, Part> parts = new IdentityHashMap<>();
	/** Departure screens placed with the display style they are set to, applied to each screen's owner when the build is done. */
	private final List<BlockPos> screenCells = new ArrayList<>();
	private final List<String> screenStyles = new ArrayList<>();
	private final List<String> signTexts = new ArrayList<>();
	/** Wayfinding signs placed with "ARROW:destination", written into each sign when the build is done. */
	private final Map<BlockPos, String> wayfindingTexts = new HashMap<>();
	/**
	 * Rebuild only: the cells the last build wrote that still hold its block, with the state each had before that build. They
	 * may be written over without approval, and the undo log keeps their first state, so undo still gives the original site back.
	 */
	private final Map<BlockPos, UndoLog.Entry> ours;
	/** Rebuild only: how many of the first ops are the left-out cells taken back, from the undo log, before any write. */
	private final int removals;
	private int kept;
	/** The finishing steps, one cell (or one lift group, or the MTR registration) each; null until every op is done. */
	private Deque<Runnable> finishing;
	/** Name sign row owners already written, so a joined row gets its text once. */
	private final Set<BlockPos> signOwners = new HashSet<>();
	/** Lift stops by column and call buttons found by the finishing steps. */
	private final Map<Long, TreeMap<Integer, BlockPos>> liftColumns = new HashMap<>();
	private final List<BlockPos> liftButtons = new ArrayList<>();

	private State state = State.RUNNING;
	private int done;
	private int pausedTicks;
	private boolean paused;
	private int placed;
	private int terrainChanged;
	private int restored;
	private int skipped;
	private int keptNodes;

	private StationBuildJob(ServerWorld world, UUID owner, Mode mode, List<Prepared> ops, boolean approved, UndoLog log, int total, Consumer<Text> out,
							StationZone zone) {
		this(world, owner, mode, ops, ops, approved, log, total, out, zone, Map.of(), 0);
	}

	/** @param all every op of the plan (what each cell will hold); {@code ops} is the part this job writes */
	private StationBuildJob(ServerWorld world, UUID owner, Mode mode, List<Prepared> all, List<Prepared> ops, boolean approved, UndoLog log, int total,
							Consumer<Text> out, StationZone zone, Map<BlockPos, UndoLog.Entry> ours, int removals) {
		this.ours = ours;
		this.removals = removals;
		this.world = world;
		this.zone = zone;
		this.probe = WorldSiteProbe.loading(world);
		this.owner = owner;
		this.mode = mode;
		this.ops = ops;
		this.approved = approved;
		this.log = log;
		this.total = total;
		this.out = out;
		for (final Prepared prepared : all) {
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
		return build(world, owner, resolution, approved, out, null);
	}

	public static StationBuildJob build(ServerWorld world, UUID owner, BlockStateResolver.Resolution resolution, boolean approved, Consumer<Text> out) {
		return build(world, owner, resolution, approved, out, null);
	}

	/** @param zone the MTR station area to register once built, or null */
	public static StationBuildJob build(ServerWorld world, UUID owner, BlockStateResolver.Resolution resolution, boolean approved, Consumer<Text> out,
										StationZone zone) {
		if (!resolution.ok()) {
			throw new IllegalArgumentException("unknown blocks: " + String.join(", ", resolution.missing()));
		}
		return new StationBuildJob(world, owner, Mode.BUILD, resolution.ops(), approved, new UndoLog(world.getRegistryKey()), resolution.ops().size(), out, zone);
	}

	/**
	 * Builds {@code resolution} over the station {@code last} logged, at the same spot: a cell that would be written the same
	 * stays as it stands, a cell the new plan leaves out goes back to its state before that build, and only the rest is written.
	 * The old station's MTR lifts and station area go first and are formed again when the job finishes. The new job's log
	 * takes over from {@code last}, so one undo still restores the site as it was before either build. The resolution must come
	 * from a survey that read our own cells as their state before the build ({@link WorldSiteProbe#under}).
	 */
	public static StationBuildJob rebuild(ServerWorld world, UUID owner, BlockStateResolver.Resolution resolution, boolean approved, Consumer<Text> out,
										  StationZone zone, UndoLog last) {
		return rebuild(world, owner, resolution, approved, out, zone, last, ours(world, last));
	}

	/** {@link #rebuild} with {@link #ours} of {@code last} already worked out (by an {@link Ours} walk). */
	public static StationBuildJob rebuild(ServerWorld world, UUID owner, BlockStateResolver.Resolution resolution, boolean approved, Consumer<Text> out,
										  StationZone zone, UndoLog last, Map<BlockPos, UndoLog.Entry> cells) {
		if (!resolution.ok()) {
			throw new IllegalArgumentException("unknown blocks: " + String.join(", ", resolution.missing()));
		}
		// a rebuild that cannot start must leave the last build's MTR lifts and station alone, so nothing above may fail after this
		final Map<BlockPos, String> built = new java.util.LinkedHashMap<>();
		for (final Map.Entry<BlockPos, String> key : last.keys().entrySet()) {
			if (cells.containsKey(key.getKey())) {
				built.put(key.getKey(), key.getValue());
			}
		}
		final List<RebuildDiff.Want<BlockPos>> wanted = new ArrayList<>();
		for (final Prepared prepared : resolution.ops()) {
			wanted.add(new RebuildDiff.Want<>(pos(prepared.op()), key(prepared)));
		}
		final RebuildDiff<BlockPos> diff = RebuildDiff.of(built, wanted);
		MtrLiftContract.remove(world, last.takeLifts());
		MtrStationContract.remove(world, last.takeStations());
		final UndoLog log = new UndoLog(world.getRegistryKey());
		log.copySoil(last);
		log.copyRails(last);
		// kept cells carry over first; then the left-out ones, oldest first, so the undo phase takes them newest first
		final Set<BlockPos> leaving = new HashSet<>(diff.remove());
		final Set<BlockPos> rewritten = new HashSet<>();
		for (final int i : diff.write()) {
			rewritten.add(wanted.get(i).pos());
		}
		for (final Map.Entry<BlockPos, String> key : built.entrySet()) {
			if (!leaving.contains(key.getKey()) && !rewritten.contains(key.getKey())) {
				log.add(cells.get(key.getKey()), key.getValue());
			}
		}
		for (int i = diff.remove().size() - 1; i >= 0; i--) {
			final BlockPos pos = diff.remove().get(i);
			log.add(cells.get(pos), built.get(pos));
		}
		final List<Prepared> writes = new ArrayList<>();
		for (final int i : diff.write()) {
			writes.add(resolution.ops().get(i));
		}
		final StationBuildJob job = new StationBuildJob(world, owner, Mode.REBUILD, resolution.ops(), writes, approved, log,
				diff.remove().size() + writes.size(), out, zone, cells, diff.remove().size());
		job.kept = diff.kept();
		return job;
	}

	/**
	 * The cells {@code log} wrote that still hold the block written there (a rail node connected in MTR since counts as changed),
	 * each with its state before the build and after its last write.
	 */
	static Map<BlockPos, UndoLog.Entry> ours(ServerWorld world, UndoLog log) {
		final Ours walk = new Ours(world, log);
		walk.step(Long.MAX_VALUE);
		return walk.result();
	}

	/**
	 * {@link #ours} walked a piece at a time, so a big build's cells are read over several ticks and the chunks they load can
	 * unload between them.
	 */
	public static final class Ours {

		private final ServerWorld world;
		private final UndoLog log;
		private final WorldSiteProbe probe;
		private final java.util.Iterator<Map.Entry<BlockPos, UndoLog.Entry>> cells;
		private final Map<BlockPos, UndoLog.Entry> ours = new java.util.LinkedHashMap<>();
		private boolean done;

		public Ours(ServerWorld world, UndoLog log) {
			this.world = world;
			this.log = log;
			this.probe = WorldSiteProbe.loading(world);
			this.cells = new ArrayList<>(log.cells().entrySet()).iterator();
		}

		/** Reads cells until all are read or {@code deadline} ({@link System#nanoTime}) has passed; true when done. */
		public boolean step(long deadline) {
			while (!done) {
				if (!cells.hasNext()) {
					done = true;
					probe.release();
					break;
				}
				final Map.Entry<BlockPos, UndoLog.Entry> cell = cells.next();
				final BlockPos pos = cell.getKey();
				// a node connected by the platform rail the build laid itself is still ours
				if (probe.loaded(pos.getX(), pos.getZ())) {
					final BlockState current = world.getBlockState(pos);
					if (stillPlaced(current, cell.getValue().placed()) || log.railNode(pos) && current.getBlock() == cell.getValue().placed().getBlock()) {
						ours.put(pos, cell.getValue());
					}
				}
				if (System.nanoTime() >= deadline) {
					break;
				}
			}
			return done;
		}

		/** Lets go of the chunks held before the walk is done (the rebuild was called off). */
		public void cancel() {
			probe.release();
		}

		/** @throws IllegalStateException before the walk is done */
		public Map<BlockPos, UndoLog.Entry> result() {
			if (!done) {
				throw new IllegalStateException("walk not finished");
			}
			return ours;
		}
	}

	/** What identifies a write completely, for {@link RebuildDiff}: the block state as resolved and any sign or screen text. */
	static String key(Prepared prepared) {
		return prepared.state() + "|" + prepared.op().text();
	}

	private static BlockPos pos(WorldOp op) {
		return new BlockPos(op.x(), op.y(), op.z());
	}

	/** Undoes the log's entries from the newest back; the log shrinks as cells are handled. */
	public static StationBuildJob undo(ServerWorld world, UUID owner, UndoLog log, Consumer<Text> out) {
		// MTR keeps lifts in its own data; a plain block reset would leave the lift behind
		final List<BlockPos> lifts = log.takeLifts();
		final List<Long> stations = log.takeStations();
		MtrLiftContract.remove(world, lifts);
		MtrStationContract.remove(world, stations);
		// the platform rails the build laid go with their nodes; a node the player connected on their own stays
		MtrRailContract.remove(world, log.takeRailNodes());
		// the build said what it added to MTR; undo says what it took away
		if (!lifts.isEmpty() || !stations.isEmpty()) {
			out.accept(Text.literal("Removed from MTR: " + (lifts.isEmpty() ? "" : lifts.size() + (lifts.size() == 1 ? " lift" : " lifts"))
					+ (!lifts.isEmpty() && !stations.isEmpty() ? " and " : "") + (stations.isEmpty() ? "" : "the station this build added") + ".")
					.formatted(Formatting.GRAY));
		}
		return new StationBuildJob(world, owner, Mode.UNDO, List.of(), true, log, log.size(), out, null);
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

	/** Rebuild only: cells that already held what the new plan wants and were left as they stand. */
	public int kept() {
		return kept;
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

	public UndoLog undoLog() {
		return log;
	}

	/** Chunks the job holds loaded right now (at most its window). */
	public int heldChunks() {
		return probe.held();
	}

	/** Holds at most {@code cap} chunks instead of {@link com.aureliatransit.architecture.station.ChunkHold#DEFAULT_CAP}; only before the first step. For the game tests. */
	public StationBuildJob holdingAtMost(int cap) {
		if (done > 0 || finishing != null) {
			throw new IllegalStateException("the job has started");
		}
		probe.release();
		probe = WorldSiteProbe.loading(world, cap);
		return this;
	}

	/** Runs at most {@code budget} ops and returns how many it handled. */
	public int step(int budget) {
		if (state != State.RUNNING) {
			return 0;
		}
		int used = 0;
		boolean blocked = false;
		final int loads = probe.loads() + LOADS_PER_STEP;
		while (used < budget && done < total && probe.loads() < loads) {
			final Step step = mode == Mode.UNDO || done < removals ? undoNext() : buildNext();
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
			if (finishing == null) {
				finishing = finishingSteps();
			}
			while (used < budget && !finishing.isEmpty() && probe.loads() < loads) {
				finishing.poll().run();
				used++;
			}
			if (finishing.isEmpty()) {
				finish();
			}
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
		probe.release();
		if (mode != Mode.UNDO) {
			say(Formatting.YELLOW, "Build cancelled at " + done + "/" + total + ": " + placed + " blocks placed, " + terrainChanged
					+ " terrain cells changed." + (log.isEmpty() ? "" : " /ata_station undo reverts them."));
		} else {
			say(Formatting.YELLOW, "Undo cancelled at " + done + "/" + total + ": " + restored + " restored, " + skipped
					+ " skipped. /ata_station undo continues.");
		}
	}

	// ---- build ------------------------------------------------------------------------------------------------------

	private Step buildNext() {
		final Prepared prepared = ops.get(done - removals);
		final WorldOp op = prepared.op();
		if (!probe.loaded(op.x(), op.z())) {
			return Step.WAIT;
		}
		final CellKind kind = probe.kind(op.x(), op.y(), op.z());
		if (op.kind() == WorldOp.Kind.CLEAR && kind == CellKind.AIR) {
			done++;
			return Step.DONE;
		}
		final BlockPos pos = new BlockPos(op.x(), op.y(), op.z());
		// a cell of the last build may be written over: it was surveyed as the site it replaced
		final UndoLog.Entry last = ours.get(pos);
		final BlockState original = last != null && stillPlaced(world.getBlockState(pos), last.placed()) ? last.previous() : null;
		if (original == null && !allowed(op.kind(), kind)) {
			stop("Stopped at " + op.x() + " " + op.y() + " " + op.z() + ": found " + probe.describe(op.x(), op.y(), op.z()) + " where "
					+ op.blockId() + " was planned (" + kind.name().toLowerCase(java.util.Locale.ROOT)
					+ "). The cell changed since the survey; nothing more was placed. ", true);
			return Step.STOP;
		}
		final BlockState before = original != null ? original : world.getBlockState(pos);
		final BlockState wanted = postProcessAsPlanned(prepared.state(), pos);
		if (wanted.getBlock() != prepared.state().getBlock()) {
			stop("Stopped at " + op.x() + " " + op.y() + " " + op.z() + ": " + op.blockId() + " would not stay there next to its neighbours; nothing more was placed. ", true);
			return Step.STOP;
		}
		final BlockState below = world.getBlockState(pos.down());
		if (below.getBlock() instanceof SpreadableBlock && !wanted.isAir()) {
			log.coverSoil(pos.down(), below);
		}
		world.setBlockState(pos, wanted, original != null ? removalFlags(world.getBlockState(pos)) : Block.NOTIFY_ALL);
		final BlockState after = world.getBlockState(pos);
		log.add(new UndoLog.Entry(pos, before, after), key(prepared));
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
		if (op.kind() == WorldOp.Kind.PLACE && !op.text().isEmpty() && after.getBlock() instanceof com.aureliatransit.architecture.live.PidsBlock) {
			screenCells.add(pos);
			screenStyles.add(op.text());
		}
		if (op.kind() == WorldOp.Kind.PLACE && !op.text().isEmpty() && after.getBlock() instanceof TextSignBlock) {
			signCells.add(pos);
			signTexts.add(op.text());
		}
		if (op.kind() == WorldOp.Kind.PLACE && !op.text().isEmpty() && after.getBlock() instanceof com.aureliatransit.architecture.block.wayfinding.WayfindingPlateBlock) {
			wayfindingTexts.put(pos, op.text());
		}
		return Step.DONE;
	}

	/**
	 * {@link Block#postProcessState} against the finished station: a neighbour cell that is still air but planned counts as its
	 * planned block. Without this the lower half of a two-high block (MTR's ticket machine) turns to air before its upper half
	 * is placed.
	 */
	private BlockState postProcessAsPlanned(BlockState state, BlockPos pos) {
		// MTR's escalator reads its step or handrail partner from the world, not the neighbour it is given; it is shaped once
		// the station is finished (escalators())
		if (part(state.getBlock()) == Part.ESCALATOR) {
			return state;
		}
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
		probe.release();
		final String tail = mode != Mode.UNDO
				? done + " of " + total + " ops done." + (undoHint && !log.isEmpty() ? " /ata_station undo reverts them." : "")
				: restored + " restored, " + skipped + " skipped, " + (total - done) + " left; /ata_station undo continues.";
		say(Formatting.RED, reason + tail);
	}

	/**
	 * What a build does once every op is done, in this order, one step per cell: escalator shapes, screen styles, name signs,
	 * wayfinding signs, then the lifts (one step per stop or button cell, then one forming them all) and the MTR station and
	 * platform rails. Nothing for an undo.
	 */
	private Deque<Runnable> finishingSteps() {
		final Deque<Runnable> steps = new ArrayDeque<>();
		if (mode == Mode.UNDO) {
			return steps;
		}
		for (final Map.Entry<BlockPos, BlockState> cell : planned.entrySet()) {
			if (part(cell.getValue().getBlock()) == Part.ESCALATOR) {
				steps.add(() -> escalator(cell.getKey()));
			}
		}
		for (int i = 0; i < screenCells.size(); i++) {
			final int screen = i;
			steps.add(() -> screen(screen));
		}
		for (int i = 0; i < signCells.size(); i++) {
			final int sign = i;
			steps.add(() -> nameSign(sign));
		}
		for (final Map.Entry<BlockPos, String> sign : wayfindingTexts.entrySet()) {
			steps.add(() -> wayfindingSign(sign.getKey(), sign.getValue()));
		}
		for (final Map.Entry<BlockPos, BlockState> cell : planned.entrySet()) {
			final Part part = part(cell.getValue().getBlock());
			if (part == Part.LIFT_STOP || part == Part.LIFT_BUTTONS) {
				steps.add(() -> liftPart(cell.getKey(), cell.getValue(), part));
			}
		}
		steps.add(this::lifts);
		steps.add(() -> {
			MtrStationContract.register(world, zone, log, out);
			if (zone != null) {
				MtrRailContract.lay(world, zone.rails(), log, out);
			}
		});
		return steps;
	}

	private void finish() {
		state = State.DONE;
		paused = false;
		probe.release();
		if (mode != Mode.UNDO) {
			out.accept(Text.literal((mode == Mode.REBUILD ? "Station rebuilt: " + kept + " cells kept as they stood, " + restored + " taken back, " : "Station built: ")
					+ placed + " blocks placed, " + terrainChanged + " terrain cells changed. ").formatted(Formatting.GREEN)
					.append(StationText.button("Undo", "/ata_station undo", Formatting.YELLOW, "Take the whole station away again")));
		} else {
			say(Formatting.GREEN, "Undo finished: " + restored + " cells restored"
					+ (keptNodes > 0 ? ", " + keptNodes + " rail nodes kept (connected in MTR since the build)" : "")
					+ (skipped > 0 ? ", " + skipped + " skipped (changed since the build)" : "") + ".");
		}
	}

	private Part part(Block block) {
		return parts.computeIfAbsent(block, key -> {
			final Identifier id = Registries.BLOCK.getId(key);
			if (id.getNamespace().equals("mtr") && id.getPath().startsWith("escalator_")) {
				return Part.ESCALATOR;
			}
			final String text = id.toString();
			return text.equals(StylePalette.MTR_LIFT_FLOOR) ? Part.LIFT_STOP : text.equals(StylePalette.MTR_LIFT_BUTTONS) ? Part.LIFT_BUTTONS : Part.NONE;
		});
	}

	private static boolean escalator(BlockState state) {
		final Identifier id = Registries.BLOCK.getId(state.getBlock());
		return id.getNamespace().equals("mtr") && id.getPath().startsWith("escalator_");
	}

	/** An escalator step takes its landing, transition or slope shape from the finished flight around it, kept steps too. */
	private void escalator(BlockPos pos) {
		if (!probe.loaded(pos.getX(), pos.getZ())) {
			return;
		}
		final BlockState state = world.getBlockState(pos);
		final BlockState shaped = escalator(state) ? Block.postProcessState(state, world, pos) : state;
		if (shaped != state && escalator(shaped)) {
			world.setBlockState(pos, shaped, Block.NOTIFY_LISTENERS);
		}
	}

	/** A built screen's owner (the top-left block of a joined screen) takes the planned display style. */
	private void screen(int i) {
		final BlockPos pos = screenCells.get(i);
		if (!probe.loaded(pos.getX(), pos.getZ())) {
			return;
		}
		final BlockState state = world.getBlockState(pos);
		final com.aureliatransit.architecture.live.DisplayStyle style = com.aureliatransit.architecture.live.DisplayConfig.styleById(screenStyles.get(i));
		if (style == null || !(state.getBlock() instanceof com.aureliatransit.architecture.live.PidsBlock block)) {
			return;
		}
		if (world.getBlockEntity(block.findOwner(world, pos, state)) instanceof com.aureliatransit.architecture.live.PidsBlockEntity screen
				&& screen.config().style() != style) {
			screen.setConfig(screen.config().withStyle(style));
		}
	}

	/** A planned wayfinding sign takes its arrow and destination ("DOWN:Platform 2"). */
	private void wayfindingSign(BlockPos pos, String text) {
		final int colon = text.indexOf(':');
		if (colon < 0 || !probe.loaded(pos.getX(), pos.getZ())
				|| !(world.getBlockEntity(pos) instanceof com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity entity)) {
			return;
		}
		SignArrow arrow = SignArrow.NONE;
		try {
			arrow = SignArrow.valueOf(text.substring(0, colon));
		} catch (IllegalArgumentException ignored) {
			// an unknown arrow name: no arrow
		}
		entity.setWayfinding(entity.getWayfinding().withDirection(arrow, text.substring(colon + 1)));
	}

	/** Name text goes to the block entity of each joined row's owner, once, after the whole row exists. */
	private void nameSign(int i) {
		final BlockPos pos = signCells.get(i);
		if (!probe.loaded(pos.getX(), pos.getZ())) {
			return;
		}
		final BlockState sign = world.getBlockState(pos);
		if (!(sign.getBlock() instanceof TextSignBlock block)) {
			return;
		}
		final BlockPos rowOwner = block.findRowOwner(world, pos, sign);
		if (probe.loaded(rowOwner.getX(), rowOwner.getZ()) && signOwners.add(rowOwner) && world.getBlockEntity(rowOwner) instanceof TextSignBlockEntity entity) {
			// a number plate shows its text as the big number, not as the small caption under it
			final boolean plate = block.getLayout().style().isNumberPlate();
			entity.setData(new SignData(plate ? "" : signTexts.get(i), "", TextAlignment.CENTER, AccentPalette.NONE, SignArrow.NONE,
					plate ? signTexts.get(i) : "", false, List.of()));
		}
	}

	/** A lift stop or call button cell that still holds its planned block joins its column (or the buttons) for {@link #lifts}. */
	private void liftPart(BlockPos pos, BlockState wanted, Part part) {
		if (!probe.loaded(pos.getX(), pos.getZ()) || world.getBlockState(pos).getBlock() != wanted.getBlock()) {
			return;
		}
		if (part == Part.LIFT_STOP) {
			liftColumns.computeIfAbsent(BlockPos.asLong(pos.getX(), 0, pos.getZ()), key -> new TreeMap<>()).put(pos.getY(), pos);
		} else {
			liftButtons.add(pos);
		}
	}

	/**
	 * Forms an MTR lift from every column of built stops, as the Lift Refresher would, and links the call buttons. A stop
	 * stands in a corner of the car against the side glass, its FACING towards the glass: the car is the 2×2 reaching from
	 * the stop away from the glass and one row towards the landing doors, which stand two cells along from the stop. So the
	 * car's centre is half a cell from the stop's centre away from the glass and half a cell towards the doors. A stop with
	 * no doors there is an older shaft's, against the back wall with the car in front of it and clockwise. The stops and buttons
	 * are the ones {@link #liftPart} found.
	 */
	private void lifts() {
		final Map<Long, TreeMap<Integer, BlockPos>> columns = liftColumns;
		final List<BlockPos> buttons = liftButtons;
		final List<MtrLiftContract.Shaft> shafts = new ArrayList<>();
		for (final TreeMap<Integer, BlockPos> stops : columns.values()) {
			if (stops.size() < 2) {
				continue;
			}
			final BlockState stop = world.getBlockState(stops.firstEntry().getValue());
			if (!stop.contains(Properties.HORIZONTAL_FACING)) {
				continue;
			}
			final Direction facing = stop.get(Properties.HORIZONTAL_FACING);
			final BlockPos bottom = stops.firstEntry().getValue();
			Direction doors = null;
			for (final Direction side : new Direction[]{facing.rotateYClockwise(), facing.rotateYCounterclockwise()}) {
				if (liftDoor(bottom.offset(side, 2))) {
					doors = side;
				}
			}
			if (doors != null) {
				// a door one cell the other way from the stop: the car opens at the back too (MTR opens a side only where it
				// finds doors)
				shafts.add(new MtrLiftContract.Shaft(List.copyOf(stops.values()), BlockStateResolver.heading(doors),
						0.5 + 0.5 * (doors.getOffsetX() - facing.getOffsetX()), 0.5 + 0.5 * (doors.getOffsetZ() - facing.getOffsetZ()),
						liftDoor(bottom.offset(doors.getOpposite()))));
			} else {
				final Direction beside = facing.rotateYClockwise();
				shafts.add(new MtrLiftContract.Shaft(List.copyOf(stops.values()), BlockStateResolver.heading(facing.getOpposite()),
						0.5 + (beside.getOffsetX() - facing.getOffsetX()) / 2.0, 0.5 + (beside.getOffsetZ() - facing.getOffsetZ()) / 2.0,
						liftDoor(bottom.offset(facing))));
			}
		}
		if (shafts.isEmpty()) {
			return;
		}
		if (MtrLiftContract.create(world, shafts, buttons)) {
			for (final MtrLiftContract.Shaft shaft : shafts) {
				log.addLift(shaft.stops().get(0));
			}
			say(Formatting.GREEN, shafts.size() == 1 ? "1 lift is running." : shafts.size() + " lifts are running.");
		} else {
			say(Formatting.YELLOW, "Lift shafts built; right-click a lift stop with MTR's Lift Refresher to start each lift.");
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
			world.setBlockState(pos, processed.getBlock() == entry.previous().getBlock() ? processed : entry.previous(), removalFlags(current));
			restored++;
			restoreSoil(pos.down());
		} else if (current.getBlock() == entry.previous().getBlock()) {
			restored++; // a neighbour put it back already (the lower half of a two-high block goes with its upper half)
		} else {
			skipped++;
		}
		return Step.DONE;
	}

	/**
	 * Flags for taking a block away. Half of a two-high block (a ticket machine, a kiosk) or one leaf of a door pair goes
	 * without neighbour shape updates: they would turn its partner to air, and vanilla breaks a block that a shape update
	 * removes with drops whatever flags the write had. The partner has its own log entry and goes in turn.
	 */
	static int removalFlags(BlockState current) {
		for (final Property<?> property : current.getProperties()) {
			if (property.getName().equals("side") || property.getName().equals("half") && pairedHalf(property)) {
				return Block.NOTIFY_ALL | Block.FORCE_STATE;
			}
		}
		return Block.NOTIFY_ALL;
	}

	/** A half property with upper and lower values (vanilla's or MTR's own enum), not a stair's or slab's top and bottom. */
	private static <T extends Comparable<T>> boolean pairedHalf(Property<T> property) {
		for (final T value : property.getValues()) {
			if (property.name(value).equals("upper")) {
				return true;
			}
		}
		return false;
	}

	/** Grass or mycelium the build covered and vanilla turned into dirt comes back once nothing of ours stands on it. */
	private void restoreSoil(BlockPos pos) {
		final BlockState soil = log.coveredSoil(pos);
		if (soil != null && world.getBlockState(pos).isOf(Blocks.DIRT) && !world.getBlockState(pos.up()).isOpaqueFullCube(world, pos.up())) {
			world.setBlockState(pos, soil, Block.NOTIFY_ALL);
		}
	}

	/** A rail node the build placed unconnected that MTR has connected since. */
	static boolean connectedNode(BlockState current, BlockState placed) {
		if (current.getBlock() != placed.getBlock()) {
			return false;
		}
		final Property<?> connected = current.getBlock().getStateManager().getProperty("is_connected");
		return connected != null && !current.get(connected).equals(placed.get(connected));
	}

	/**
	 * True when a cell still holds the block the build wrote. Join states (sign rows) change as neighbours come and go, so the
	 * block counts, not the exact state; but a rail node the player connected in MTR ({@code is_connected} set) stays, because
	 * removing it with setBlockState would orphan MTR's rail data.
	 */
	static boolean stillPlaced(BlockState current, BlockState placed) {
		return current.getBlock() == placed.getBlock() && !connectedNode(current, placed);
	}

	private boolean liftDoor(BlockPos pos) {
		return Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals(StylePalette.MTR_LIFT_DOOR);
	}

	private void say(Formatting colour, String message) {
		out.accept(Text.literal(message).formatted(colour));
	}
}
