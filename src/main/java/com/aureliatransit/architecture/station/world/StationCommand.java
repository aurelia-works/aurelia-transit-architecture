package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LayoutValidator;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.SiteSurvey;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.Violation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /ata_station}: plan a station from a description, preview it, confirm (optionally approving terrain changes), cancel,
 * undo. Operators only (level 2), like the other ATA admin commands. Terrain approval comes only from the literal
 * {@code confirm replace_terrain}, never from the description.
 */
public final class StationCommand {

	public static final String REPLACE_TERRAIN = "replace_terrain";

	private StationCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("ata_station").requires(source -> source.hasPermissionLevel(2))
				.executes(StationCommand::help)
				.then(CommandManager.literal("help").executes(StationCommand::help))
				.then(CommandManager.literal("plan").then(CommandManager.argument("description", StringArgumentType.greedyString())
						.executes(StationCommand::plan)))
				.then(CommandManager.literal("preview").executes(StationCommand::preview))
				.then(CommandManager.literal("confirm").executes(context -> confirm(context, false))
						.then(CommandManager.literal(REPLACE_TERRAIN).executes(context -> confirm(context, true))))
				.then(CommandManager.literal("cancel").executes(StationCommand::cancel))
				.then(CommandManager.literal("undo").executes(StationCommand::undo))
				.then(CommandManager.literal("status").executes(StationCommand::status)));
	}

	// ---- shared with the game tests --------------------------------------------------------------------------------

	/** Parses, plans, validates and surveys (without approval) a station in front of a player standing at {@code feet}. */
	public static StationSessions.Pending prepare(ServerWorld world, String description, BlockPos feet, Heading facing) {
		final ParseResult parse = StationDescriptionParser.parse(description);
		final StationPlan plan = StationPlanner.plan(parse.spec());
		final List<Violation> violations = LayoutValidator.validate(plan);
		final LocalFrame frame = LocalFrame.inFrontOf(feet.getX(), feet.getY(), feet.getZ(), facing, plan.layout());
		final BlockStateResolver.Check check = BlockStateResolver.check(plan);
		final SurveyReport report = SiteSurvey.survey(plan, frame, new WorldSiteProbe(world), false);
		return new StationSessions.Pending(parse, parse.spec(), plan, frame, world.getRegistryKey(), violations, report, check.railSkipped());
	}

	/**
	 * Re-surveys a pending plan where it was planned and starts its build; returns the job, or null after explaining why not.
	 */
	public static StationBuildJob startBuild(ServerWorld world, UUID owner, StationSessions.Pending pending, boolean replaceTerrain, Consumer<Text> out) {
		if (!pending.violations().isEmpty()) {
			out.accept(Text.literal("This plan breaks " + pending.violations().size() + " design rule(s) and cannot be built; plan again with "
					+ "different options.").formatted(Formatting.RED));
			return null;
		}
		final BlockStateResolver.Check check = BlockStateResolver.check(pending.plan());
		if (!check.ok()) {
			out.accept(Text.literal("Unknown blocks: " + String.join(", ", check.missing())).formatted(Formatting.RED));
			return null;
		}
		final SurveyReport report = SiteSurvey.survey(pending.plan(), pending.frame(), new WorldSiteProbe(world), replaceTerrain);
		if (!report.buildable()) {
			out.accept(Text.literal("The site changed or needs approval:").formatted(Formatting.RED));
			StationText.site(report, out);
			return null;
		}
		final StationBuildJob job = StationBuildJob.build(world, owner, BlockStateResolver.resolve(report.ops()), replaceTerrain, out);
		if (!StationSessions.start(job)) {
			out.accept(Text.literal("A build of yours is still running; /ata_station cancel stops it.").formatted(Formatting.RED));
			return null;
		}
		return job;
	}

	// ---- commands --------------------------------------------------------------------------------------------------

	private static int help(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		source.sendFeedback(() -> Text.literal("ATA station generator").formatted(Formatting.GOLD), false);
		source.sendFeedback(() -> Text.literal("/ata_station plan <description>: plan a station in front of you and preview it"), false);
		source.sendFeedback(() -> Text.literal("/ata_station confirm [" + REPLACE_TERRAIN + "]: build it (the token approves terrain changes)"), false);
		source.sendFeedback(() -> Text.literal("/ata_station preview | cancel | undo | status"), false);
		source.sendFeedback(() -> Text.literal("Examples:").formatted(Formatting.GRAY), false);
		source.sendFeedback(() -> Text.literal("  two track dutch station, island platform, 64 long, glass canopy, named Leidsche Rijn").formatted(Formatting.GRAY), false);
		source.sendFeedback(() -> Text.literal("  small single track s-bahn halt, no roof").formatted(Formatting.GRAY), false);
		source.sendFeedback(() -> Text.literal("  4 track metro station, 96 m long, hall roof, terrazzo floors").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int plan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		final ServerWorld world = player.getServerWorld();
		final StationSessions.Pending pending;
		try {
			pending = prepare(world, StringArgumentType.getString(context, "description"), player.getBlockPos(),
					BlockStateResolver.heading(player.getHorizontalFacing()));
		} catch (IllegalArgumentException e) {
			context.getSource().sendError(Text.literal("Cannot plan that station: " + e.getMessage()));
			return 0;
		}
		StationSessions.discard(player.getUuid());
		StationSessions.setPending(player.getUuid(), pending);
		StationText.summary(pending, BlockStateResolver.check(pending.plan()), message(player));
		StationSessions.startPreview(new StationPreview(player.getUuid(), pending.dimension(), pending.plan(), pending.frame(), pending.report()));
		return 1;
	}

	private static int preview(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		final StationSessions.Pending pending = StationSessions.pending(player.getUuid());
		if (pending == null) {
			context.getSource().sendError(Text.literal("No pending plan. /ata_station plan <description> makes one."));
			return 0;
		}
		StationSessions.startPreview(new StationPreview(player.getUuid(), pending.dimension(), pending.plan(), pending.frame(), pending.report()));
		player.sendMessage(Text.literal("Showing the plan outline for " + StationPreview.DURATION_TICKS / 20 + " s.").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int confirm(CommandContext<ServerCommandSource> context, boolean replaceTerrain) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		final UUID id = player.getUuid();
		final StationSessions.Pending pending = StationSessions.pending(id);
		if (pending == null) {
			context.getSource().sendError(Text.literal("No pending plan. /ata_station plan <description> makes one."));
			return 0;
		}
		final ServerWorld world = context.getSource().getServer().getWorld(pending.dimension());
		if (world == null || player.getServerWorld() != world) {
			context.getSource().sendError(Text.literal("The plan is in " + pending.dimension().getValue() + "; go back there to confirm it."));
			return 0;
		}
		final StationBuildJob job = startBuild(world, id, pending, replaceTerrain, message(player));
		if (job == null) {
			return 0;
		}
		StationSessions.discard(id);
		player.sendMessage(Text.literal("Building " + job.total() + " changes, " + StationBuildJob.OPS_PER_TICK + " per tick. /ata_station cancel stops it.")
				.formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int cancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		if (StationSessions.cancelJob(player.getUuid())) {
			return 1;
		}
		if (StationSessions.discard(player.getUuid())) {
			player.sendMessage(Text.literal("Plan discarded.").formatted(Formatting.YELLOW), false);
			return 1;
		}
		context.getSource().sendError(Text.literal("Nothing to cancel."));
		return 0;
	}

	private static int undo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		final UUID id = player.getUuid();
		final UndoLog log = StationSessions.undoLog(id);
		if (log == null || log.isEmpty()) {
			context.getSource().sendError(Text.literal("Nothing to undo."));
			return 0;
		}
		if (StationSessions.job(id) != null) {
			context.getSource().sendError(Text.literal("A build of yours is still running; /ata_station cancel stops it first."));
			return 0;
		}
		final ServerWorld world = context.getSource().getServer().getWorld(log.dimension());
		if (world == null) {
			context.getSource().sendError(Text.literal("The build's dimension is not loaded."));
			return 0;
		}
		StationSessions.start(StationBuildJob.undo(world, id, log, message(player)));
		player.sendMessage(Text.literal("Undoing " + log.size() + " changes.").formatted(Formatting.YELLOW), false);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		final UUID id = player.getUuid();
		final StationSessions.Pending pending = StationSessions.pending(id);
		final StationBuildJob job = StationSessions.job(id);
		final UndoLog log = StationSessions.undoLog(id);
		player.sendMessage(Text.literal("Pending plan: " + (pending == null ? "none" : StationText.planLine(pending.spec()))), false);
		player.sendMessage(Text.literal("Job: " + (job == null ? "none"
				: job.mode().name().toLowerCase(java.util.Locale.ROOT) + " " + job.done() + "/" + job.total() + (job.paused() ? " (waiting for an unloaded chunk)" : ""))), false);
		player.sendMessage(Text.literal("Undo: " + (log == null || log.isEmpty() ? "nothing" : log.size() + " changes")), false);
		return 1;
	}

	private static Consumer<Text> message(ServerPlayerEntity player) {
		return StationSessions.messenger(player.getServer(), player.getUuid());
	}
}
