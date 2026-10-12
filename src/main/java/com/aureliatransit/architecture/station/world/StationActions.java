package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalFrame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What a player can do with the station generator, shared by {@code /ata_station}, the station wrench's clicks and its screen.
 * The callers gate access: the command through Brigadier's source level (so {@code /execute as} from an operator or a function
 * works), every wrench entry point through {@link #allowed} on the player. Terrain approval only ever comes from
 * {@link #confirm}'s explicit flag.
 */
public final class StationActions {

	/** Operator level needed for every action, the same as the {@code /ata_station} command. */
	public static final int PERMISSION_LEVEL = 2;

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
		final Consumer<Text> out = message(player);
		final ServerWorld world = player.getServerWorld();
		final Heading facing = BlockStateResolver.heading(player.getHorizontalFacing());
		final StationSessions.Pending pending;
		try {
			pending = clicked == null
					? StationCommand.prepare(world, description, player.getBlockPos(), facing)
					: StationCommand.prepare(world, description, layout -> LocalFrame.onBlock(clicked.getX(), clicked.getY(), clicked.getZ(), facing, layout));
		} catch (IllegalArgumentException e) {
			out.accept(Text.literal("Cannot plan that station: " + e.getMessage()).formatted(Formatting.RED));
			return false;
		}
		StationSessions.discard(player.getUuid());
		StationSessions.setPending(player.getUuid(), pending);
		StationText.shortSummary(pending, BlockStateResolver.check(pending.plan()), out);
		StationSessions.startPreview(new StationPreview(player.getUuid(), pending.dimension(), pending.plan(), pending.frame(), pending.report()));
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
		StationSessions.discard(id);
		player.sendMessage(Text.literal("Building " + job.total() + " changes, " + StationBuildJob.OPS_PER_TICK + " per tick. Cancel stops it.")
				.formatted(Formatting.GREEN), false);
		return true;
	}

	public static boolean cancel(ServerPlayerEntity player) {
		if (StationSessions.cancelJob(player.getUuid())) {
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
