package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.station.Facing;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.StationPlacement;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.TrackLayout;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What a player can do with the station generator, shared by {@code /ata_station}, the station wrench's clicks and its screen.
 * The callers gate access: the command through Brigadier's source level (so {@code /execute as} from an operator or a function
 * works), every wrench entry point through {@link #allowed} on the player. Terrain approval only ever comes from
 * {@link #confirm}'s explicit flag.
 */
public final class StationActions {

	/** Operator level needed for every action, the same as the {@code /ata_station} command. */
	public static final int PERMISSION_LEVEL = 2;

	/**
	 * Track count times platform length up to which a station is planned at once on the server thread (well under a tenth of
	 * a second); bigger ones go to the planner thread.
	 */
	static final int PLAN_HERE_UP_TO = 2000;

	private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(task -> {
		final Thread thread = new Thread(task, "ATA station planner");
		thread.setDaemon(true);
		return thread;
	});
	/** Each player's latest plan request; an older one that finishes late is dropped. */
	private static final Map<UUID, Long> REQUESTS = new ConcurrentHashMap<>();
	/** Players whose big plan is on the planner thread. Server thread only. */
	private static final Set<UUID> PLANNING = new HashSet<>();

	private StationActions() {
	}

	/** True when {@code player} may use the generator; otherwise tells them why not. */
	public static boolean allowed(ServerPlayerEntity player) {
		if (player.hasPermissionLevel(PERMISSION_LEVEL)) {
			return true;
		}
		player.sendMessage(Text.literal("Only operators can use the station generator.").formatted(Formatting.RED), true);
		return false;
	}

	/**
	 * Plans a station from {@code description}: on top of {@code clicked} (a wrench click) or, when null, in front of the player's
	 * feet (the command and the wrench screen). Either way it faces the way the player looks. Replaces any pending plan.
	 */
	public static boolean plan(ServerPlayerEntity player, String description, @Nullable BlockPos clicked) {
		final UUID id = player.getUuid();
		final ServerWorld world = player.getServerWorld();
		final ParseResult parse = StationDescriptionParser.parse(description);
		// a station told which way its entrance faces ("facing north") is built that way round, not the way the player looks
		final Heading facing = parse.spec().facing() == Facing.AUTO ? BlockStateResolver.heading(player.getHorizontalFacing())
				: StationPlacement.heading(parse.spec());
		final BlockPos feet = player.getBlockPos();
		final Function<TrackLayout, LocalFrame> place = clicked == null
				? layout -> LocalFrame.inFrontOf(feet.getX(), feet.getY(), feet.getZ(), facing, layout)
				: layout -> LocalFrame.onBlock(clicked.getX(), clicked.getY(), clicked.getZ(), facing, layout);
		final long request = REQUESTS.merge(id, 1L, Long::sum);
		if (parse.spec().trackCount() * parse.spec().platformLength() <= PLAN_HERE_UP_TO) {
			StationCommand.Design design = null;
			RuntimeException failure = null;
			try {
				design = StationCommand.design(parse);
			} catch (RuntimeException e) {
				failure = e;
			}
			return finishPlan(player.getServer(), id, request, world, place, design, failure);
		}
		// a big station takes a good part of a second to plan: do that on the planner thread, so the server keeps ticking,
		// then place and survey it back on the server thread
		final MinecraftServer server = player.getServer();
		PLANNING.add(id);
		PLANNER.execute(() -> {
			StationCommand.Design design = null;
			RuntimeException failure = null;
			try {
				design = StationCommand.design(parse);
			} catch (RuntimeException e) {
				failure = e;
			}
			final StationCommand.Design done = design;
			final RuntimeException failed = failure;
			server.execute(() -> finishPlan(server, id, request, world, place, done, failed));
		});
		player.sendMessage(Text.literal("Planning a big station...").formatted(Formatting.GRAY), true);
		return true;
	}

	/** On the server thread: places and surveys a design, or explains why there is none. False when nothing was planned. */
	private static boolean finishPlan(MinecraftServer server, UUID id, long request, ServerWorld world, Function<TrackLayout, LocalFrame> place,
			@Nullable StationCommand.Design design, @Nullable RuntimeException failure) {
		if (REQUESTS.getOrDefault(id, 0L) != request) {
			// a newer plan request replaced this one while it was being worked out
			return false;
		}
		PLANNING.remove(id);
		final ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
		if (player == null) {
			return false;
		}
		final Consumer<Text> out = message(player);
		if (failure != null || design == null) {
			if (!(failure instanceof IllegalArgumentException)) {
				AureliaTransitArchitecture.LOGGER.error("Planning a station failed", failure);
			}
			out.accept(Text.literal("Cannot plan that station: " + (failure instanceof IllegalArgumentException ? failure.getMessage()
					: "something went wrong (see the server log)")).formatted(Formatting.RED));
			return false;
		}
		if (player.getServerWorld() != world) {
			out.accept(Text.literal("You changed dimension while it was planned; plan it again here.").formatted(Formatting.RED));
			return false;
		}
		final StationSessions.Pending pending;
		try {
			pending = StationCommand.place(world, design, place);
		} catch (IllegalArgumentException e) {
			out.accept(Text.literal("Cannot plan that station: " + e.getMessage()).formatted(Formatting.RED));
			return false;
		}
		StationSessions.discard(id);
		StationSessions.setPending(id, pending);
		StationText.shortSummary(pending, design.check(), out);
		StationSessions.startPreview(new StationPreview(id, pending.dimension(), pending.plan(), pending.frame(), pending.report()));
		return true;
	}

	/** True while a big plan of this player's is still being worked out; tells them so. */
	private static boolean stillPlanning(ServerPlayerEntity player) {
		if (!PLANNING.contains(player.getUuid())) {
			return false;
		}
		error(player, "Still planning the station; try again in a moment.");
		return true;
	}

	/** The full plan summary: what was understood, the footprint, rail nodes, block counts, both checks and the site. */
	public static boolean details(ServerPlayerEntity player) {
		final StationSessions.Pending pending = StationSessions.pending(player.getUuid());
		if (pending == null) {
			error(player, "No pending plan. Plan one with the station wrench or /ata_station plan.");
			return false;
		}
		StationText.summary(pending, BlockStateResolver.check(pending.plan()), message(player));
		return true;
	}

	public static boolean preview(ServerPlayerEntity player) {
		final StationSessions.Pending pending = StationSessions.pending(player.getUuid());
		if (pending == null) {
			error(player, "No pending plan. Plan one with /ata_station plan or the station wrench.");
			return false;
		}
		StationSessions.startPreview(new StationPreview(player.getUuid(), pending.dimension(), pending.plan(), pending.frame(), pending.report()));
		player.sendMessage(Text.literal("Showing the plan outline for " + StationPreview.DURATION_TICKS / 20 + " s.").formatted(Formatting.GRAY), false);
		return true;
	}

	/** Builds the pending plan; {@code replaceTerrain} is the explicit approval and must come from a deliberate choice. */
	public static boolean confirm(ServerPlayerEntity player, boolean replaceTerrain) {
		if (stillPlanning(player)) {
			return false;
		}
		final UUID id = player.getUuid();
		final StationSessions.Pending pending = StationSessions.pending(id);
		if (pending == null) {
			error(player, "Place the station first: press \"Place in front of me\" in the wrench screen, or right-click the ground with the wrench.");
			return false;
		}
		final ServerWorld world = player.getServer().getWorld(pending.dimension());
		if (world == null || player.getServerWorld() != world) {
			error(player, "The plan is in " + pending.dimension().getValue() + "; go back there to confirm it.");
			return false;
		}
		final StationBuildJob job = StationCommand.startBuild(world, id, pending, replaceTerrain, message(player));
		if (job == null) {
			return false;
		}
		StationSessions.setBuilt(id, pending);
		StationSessions.discard(id);
		player.sendMessage(Text.literal("Building " + job.total() + " changes, " + StationBuildJob.OPS_PER_TICK + " per tick. Cancel stops it.")
				.formatted(Formatting.GREEN), false);
		return true;
	}

	/**
	 * Reads the world back against a plan (see {@link StationVerify}): the pending plan, else the last build's, where it was planned
	 * (a station that was built or pasted there). With {@code paste} the plan is instead anchored with its schematic paste origin on the
	 * player's feet, for a schematic pasted standing here with the origin it was exported with. Read only.
	 */
	public static boolean verify(ServerPlayerEntity player, boolean paste) {
		final UUID id = player.getUuid();
		final StationSessions.Pending pending = StationSessions.pending(id);
		final StationSessions.Built built = pending != null ? null : StationSessions.built(id);
		if (pending == null && built == null) {
			error(player, "Nothing to check against: plan a station first (or build one).");
			return false;
		}
		final StationPlan plan = pending != null ? pending.plan() : built.plan();
		final StationSpec spec = pending != null ? pending.spec() : built.spec();
		final var dimension = pending != null ? pending.dimension() : built.dimension();
		final ServerWorld world = player.getServer().getWorld(dimension);
		if (world == null || player.getServerWorld() != world) {
			error(player, "The plan is in " + dimension.getValue() + "; go back there to check it.");
			return false;
		}
		final LocalFrame frame = paste
				? StationPlacement.frameAt(spec, plan, StationPlacement.heading(spec), player.getBlockX(), player.getBlockY(), player.getBlockZ())
				: pending != null ? pending.frame() : built.frame();
		final StationVerify.Report report = StationVerify.verify(world, plan, frame);
		StationText.verify(report, paste, message(player));
		return true;
	}

	public static boolean cancel(ServerPlayerEntity player) {
		if (StationSessions.cancelJob(player.getUuid())) {
			return true;
		}
		if (PLANNING.remove(player.getUuid())) {
			// the plan still being worked out is dropped when it arrives
			REQUESTS.merge(player.getUuid(), 1L, Long::sum);
			player.sendMessage(Text.literal("Plan discarded.").formatted(Formatting.YELLOW), false);
			return true;
		}
		if (StationSessions.discard(player.getUuid())) {
			player.sendMessage(Text.literal("Plan discarded.").formatted(Formatting.YELLOW), false);
			return true;
		}
		error(player, "Nothing to cancel.");
		return false;
	}

	public static boolean undo(ServerPlayerEntity player) {
		final UUID id = player.getUuid();
		final UndoLog log = StationSessions.undoLog(id);
		if (log == null || log.isEmpty()) {
			error(player, "Nothing to undo.");
			return false;
		}
		if (StationSessions.job(id) != null) {
			error(player, "A build of yours is still running; cancel it first.");
			return false;
		}
		final ServerWorld world = player.getServer().getWorld(log.dimension());
		if (world == null) {
			error(player, "The build's dimension is not loaded.");
			return false;
		}
		StationSessions.start(StationBuildJob.undo(world, id, log, message(player)));
		player.sendMessage(Text.literal("Undoing " + log.size() + " changes.").formatted(Formatting.YELLOW), false);
		return true;
	}

	public static boolean status(ServerPlayerEntity player) {
		final UUID id = player.getUuid();
		final StationSessions.Pending pending = StationSessions.pending(id);
		final StationBuildJob job = StationSessions.job(id);
		final UndoLog log = StationSessions.undoLog(id);
		player.sendMessage(Text.literal("Pending plan: " + (pending == null ? "none" : StationText.planLine(pending.spec()))), false);
		player.sendMessage(Text.literal("Job: " + (job == null ? "none"
				: job.mode().name().toLowerCase(Locale.ROOT) + " " + job.done() + "/" + job.total() + (job.paused() ? " (waiting for an unloaded chunk)" : ""))), false);
		player.sendMessage(Text.literal("Undo: " + (log == null || log.isEmpty() ? "nothing" : log.size() + " changes")), false);
		return true;
	}

	private static void error(ServerPlayerEntity player, String message) {
		player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
	}

	private static Consumer<Text> message(ServerPlayerEntity player) {
		return StationSessions.messenger(player.getServer(), player.getUuid());
	}
}
