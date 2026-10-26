package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LayoutValidator;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.SiteSurvey;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlacement;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.TrackLayout;
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
import java.util.function.Function;
import java.util.function.Predicate;

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
				.then(CommandManager.literal("preview").executes(context -> run(context, StationActions::preview)))
				.then(CommandManager.literal("details").executes(context -> run(context, StationActions::details)))
				.then(CommandManager.literal("confirm").executes(context -> run(context, player -> StationActions.confirm(player, false)))
						.then(CommandManager.literal(REPLACE_TERRAIN).executes(context -> run(context, player -> StationActions.confirm(player, true)))))
				.then(CommandManager.literal("cancel").executes(context -> run(context, StationActions::cancel)))
				.then(CommandManager.literal("undo").executes(context -> run(context, StationActions::undo)))
				.then(CommandManager.literal("rebuild").executes(context -> run(context, player -> StationActions.rebuild(player, false)))
						.then(CommandManager.literal(REPLACE_TERRAIN).executes(context -> run(context, player -> StationActions.rebuild(player, true)))))
				.then(CommandManager.literal("verify").executes(context -> run(context, player -> StationActions.verify(player, false)))
						.then(CommandManager.literal("paste").executes(context -> run(context, player -> StationActions.verify(player, true)))))
				.then(CommandManager.literal("status").executes(context -> run(context, StationActions::status))));
	}

	// ---- shared with the game tests --------------------------------------------------------------------------------

	/** Parses, plans, validates and surveys (without approval) a station in front of a player standing at {@code feet}. */
	public static StationSessions.Pending prepare(ServerWorld world, String description, BlockPos feet, Heading facing) {
		return prepare(world, description, layout -> LocalFrame.inFrontOf(feet.getX(), feet.getY(), feet.getZ(), facing, layout));
	}

	/** Parses, plans, validates, audits and surveys (without approval) a station placed by {@code place}. */
	public static StationSessions.Pending prepare(ServerWorld world, String description, Function<TrackLayout, LocalFrame> place) {
		return place(world, design(StationDescriptionParser.parse(description)), place);
	}

	/** A parsed, planned, validated and audited station, not yet placed in a world. */
	public record Design(ParseResult parse, StationPlan plan, List<Violation> violations, StationAudit.Result audit, BlockStateResolver.Check check) {
	}

	/** Plans, validates and audits a parsed station. Touches no world, so it may run off the server thread. */
	public static Design design(ParseResult parse) {
		final StationPlan plan = StationPlanner.plan(parse.spec());
		return new Design(parse, plan, LayoutValidator.validate(plan), StationAudit.audit(plan), BlockStateResolver.check(plan));
	}

	/** Places a design with {@code place} and surveys its site (without approval), all at once. On the server thread. */
	public static StationSessions.Pending place(ServerWorld world, Design design, Function<TrackLayout, LocalFrame> place) {
		final StationSessions.Pending[] pending = {null};
		place(world, null, false, design, place, placed -> pending[0] = placed);
		return pending[0];
	}

	/**
	 * {@link #place} as a site task of {@code owner}'s ({@link StationSessions#startTask}): the survey reads the site over as many
	 * ticks as it needs, then {@code then} gets the plan. A small site is done before this returns.
	 *
	 * @throws IllegalArgumentException at once, when the design cannot be placed there
	 */
	public static void placeLater(ServerWorld world, UUID owner, Design design, Function<TrackLayout, LocalFrame> place,
			Consumer<StationSessions.Pending> then) {
		place(world, owner, true, design, place, then);
	}

	private static void place(ServerWorld world, UUID owner, boolean later, Design design, Function<TrackLayout, LocalFrame> place,
			Consumer<StationSessions.Pending> then) {
		final StationPlan plan = design.plan();
		final LocalFrame frame = StationPlacement.sunk(design.parse().spec(), place.apply(plan.layout()));
		survey(owner, later, WorldSiteProbe.loading(world), plan, frame, false, report -> then.accept(new StationSessions.Pending(design.parse(),
				design.parse().spec(), plan, frame, world.getRegistryKey(), design.violations(), design.audit(), report, design.check().railSkipped())));
	}

	/** Surveys with {@code probe} (as {@code owner}'s site task when {@code later}, else at once), lets go of its chunks, then hands on the report. */
	private static void survey(UUID owner, boolean later, WorldSiteProbe probe, StationPlan plan, LocalFrame frame, boolean replaceTerrain,
			Consumer<SurveyReport> then) {
		final SiteSurvey.Survey survey = SiteSurvey.start(plan, frame, probe, replaceTerrain);
		run(owner, later, new StationSessions.Task() {
			@Override
			public boolean step(long deadline) {
				if (!survey.step(deadline)) {
					return false;
				}
				probe.release();
				then.accept(survey.report());
				return true;
			}

			@Override
			public void cancel() {
				probe.release();
			}
		});
	}

	private static void run(UUID owner, boolean later, StationSessions.Task task) {
		if (later) {
			StationSessions.startTask(owner, task);
		} else {
			task.step(Long.MAX_VALUE);
		}
	}

	/**
	 * Re-surveys a pending plan where it was planned and starts its build, all at once; returns the job, or null after
	 * explaining why not.
	 */
	public static StationBuildJob startBuild(ServerWorld world, UUID owner, StationSessions.Pending pending, boolean replaceTerrain, Consumer<Text> out) {
		final StationBuildJob[] job = {null};
		startBuild(world, owner, pending, replaceTerrain, out, false, started -> job[0] = started);
		return job[0];
	}

	/**
	 * {@link #startBuild} with the survey as {@code owner}'s site task: {@code then} gets the job, or null after the reason was
	 * explained. A small site is done before this returns.
	 */
	public static void startBuildLater(ServerWorld world, UUID owner, StationSessions.Pending pending, boolean replaceTerrain, Consumer<Text> out,
			Consumer<StationBuildJob> then) {
		startBuild(world, owner, pending, replaceTerrain, out, true, then);
	}

	private static void startBuild(ServerWorld world, UUID owner, StationSessions.Pending pending, boolean replaceTerrain, Consumer<Text> out,
			boolean later, Consumer<StationBuildJob> then) {
		if (!buildable(pending, out) || stillBuilding(owner, out)) {
			then.accept(null);
			return;
		}
		survey(owner, later, WorldSiteProbe.loading(world), pending.plan(), pending.frame(), replaceTerrain, report -> {
			if (!report.buildable()) {
				out.accept(Text.literal("The site changed or needs approval:").formatted(Formatting.RED));
				StationText.site(report, out);
				then.accept(null);
				return;
			}
			final StationBuildJob job = StationBuildJob.build(world, owner, BlockStateResolver.resolve(report.ops()), replaceTerrain, out,
					zone(pending, pending.frame(), report));
			if (!StationSessions.start(job)) {
				stillBuilding(owner, out);
				then.accept(null);
				return;
			}
			then.accept(job);
		});
	}

	/** True, after saying so, when a build of the owner's is still running. */
	private static boolean stillBuilding(UUID owner, Consumer<Text> out) {
		if (StationSessions.job(owner) == null) {
			return false;
		}
		out.accept(Text.literal("A build of yours is still running; /ata_station cancel stops it.").formatted(Formatting.RED));
		return true;
	}

	/**
	 * Builds a pending plan where the last build stands, writing only what changed (see {@link StationBuildJob#rebuild}): the
	 * plan is surveyed in {@code frame} with the last build's own cells read as the site they replaced. All at once; returns the
	 * job, or null after explaining why not.
	 */
	public static StationBuildJob startRebuild(ServerWorld world, UUID owner, StationSessions.Pending pending, LocalFrame frame, UndoLog last,
			boolean replaceTerrain, Consumer<Text> out) {
		final StationBuildJob[] job = {null};
		startRebuild(world, owner, pending, frame, last, replaceTerrain, out, false, started -> job[0] = started);
		return job[0];
	}

	/**
	 * {@link #startRebuild} with the look at the last build's cells and the survey as {@code owner}'s site task: {@code then}
	 * gets the job, or null after the reason was explained. A small site is done before this returns.
	 */
	public static void startRebuildLater(ServerWorld world, UUID owner, StationSessions.Pending pending, LocalFrame frame, UndoLog last,
			boolean replaceTerrain, Consumer<Text> out, Consumer<StationBuildJob> then) {
		startRebuild(world, owner, pending, frame, last, replaceTerrain, out, true, then);
	}

	private static void startRebuild(ServerWorld world, UUID owner, StationSessions.Pending pending, LocalFrame frame, UndoLog last,
			boolean replaceTerrain, Consumer<Text> out, boolean later, Consumer<StationBuildJob> then) {
		if (!buildable(pending, out) || stillBuilding(owner, out)) {
			then.accept(null);
			return;
		}
		final StationBuildJob.Ours walk = new StationBuildJob.Ours(world, last);
		run(owner, later, new StationSessions.Task() {
			@Override
			public boolean step(long deadline) {
				if (!walk.step(deadline)) {
					return false;
				}
				final java.util.Map<BlockPos, UndoLog.Entry> cells = walk.result();
				final java.util.Map<BlockPos, net.minecraft.block.BlockState> site = new java.util.HashMap<>();
				cells.forEach((pos, entry) -> site.put(pos, entry.previous()));
				survey(owner, later, WorldSiteProbe.under(world, site), pending.plan(), frame, replaceTerrain, report -> {
					if (!report.buildable()) {
						out.accept(Text.literal("The site changed or needs approval:").formatted(Formatting.RED));
						StationText.site(report, out);
						then.accept(null);
						return;
					}
					if (stillBuilding(owner, out)) {
						then.accept(null);
						return;
					}
					final StationBuildJob job = StationBuildJob.rebuild(world, owner, BlockStateResolver.resolve(report.ops()), replaceTerrain, out,
							zone(pending, frame, report), last, cells);
					StationSessions.start(job);
					then.accept(job);
				});
				return true;
			}

			@Override
			public void cancel() {
				walk.cancel();
			}
		});
	}

	/** The MTR station area of a build and the platform rails it lays, or null when nothing is placed. */
	private static StationZone zone(StationSessions.Pending pending, LocalFrame frame, SurveyReport report) {
		final StationZone zone = StationZone.of(pending.spec().name(), report.ops());
		if (zone == null) {
			return null;
		}
		final StationZone railed = zone.withRails(com.aureliatransit.architecture.station.PlatformRail.of(pending.plan(), frame));
		if (!pending.spec().isAirport()) {
			return railed;
		}
		return railed.withAirRails(com.aureliatransit.architecture.station.AirportRails.of(
				com.aureliatransit.architecture.station.AirportRoute.of(com.aureliatransit.architecture.station.AirportGeometry.of(pending.spec().airport())), frame));
	}

	/** The checks before any build: no broken design rules, no failed station checks, every block known. */
	private static boolean buildable(StationSessions.Pending pending, Consumer<Text> out) {
		if (!pending.violations().isEmpty()) {
			out.accept(Text.literal("This plan breaks " + pending.violations().size() + " design rule(s) and cannot be built; plan again with "
					+ "different options.").formatted(Formatting.RED));
			return false;
		}
		if (!pending.audit().buildable()) {
			out.accept(Text.literal("This plan fails " + pending.audit().count(StationAudit.Status.FAIL) + " station check(s) and cannot be built; "
					+ "plan again with different options.").formatted(Formatting.RED));
			return false;
		}
		final BlockStateResolver.Check check = BlockStateResolver.check(pending.plan());
		if (!check.ok()) {
			out.accept(Text.literal("Unknown blocks: " + String.join(", ", check.missing())).formatted(Formatting.RED));
			return false;
		}
		return true;
	}

	// ---- commands --------------------------------------------------------------------------------------------------

	private static int help(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		source.sendFeedback(() -> Text.literal("ATA station generator").formatted(Formatting.GOLD), false);
		source.sendFeedback(() -> Text.literal("/ata_station plan <description>: plan a station in front of you and preview it"), false);
		source.sendFeedback(() -> Text.literal("/ata_station confirm [" + REPLACE_TERRAIN + "]: build it (the token approves terrain changes)"), false);
		source.sendFeedback(() -> Text.literal("/ata_station rebuild [" + REPLACE_TERRAIN + "]: build the pending plan where your last station stands, changing only what differs"), false);
		source.sendFeedback(() -> Text.literal("/ata_station verify [paste]: read the world back against the plan (paste: a schematic pasted at your feet)"), false);
		source.sendFeedback(() -> Text.literal("/ata_station preview | cancel | undo | status"), false);
		source.sendFeedback(() -> Text.literal("Or use the Station Wrench: right-click a block to plan, sneak + right-click the air to edit").formatted(Formatting.GRAY), false);
		source.sendFeedback(() -> Text.literal("Examples:").formatted(Formatting.GRAY), false);
		source.sendFeedback(() -> Text.literal("  two track dutch station, island platform, 64 long, glass canopy, named Leidsche Rijn").formatted(Formatting.GRAY), false);
		source.sendFeedback(() -> Text.literal("  small single track s-bahn halt, no roof").formatted(Formatting.GRAY), false);
		source.sendFeedback(() -> Text.literal("  4 track metro station, 96 m long, hall roof, terrazzo floors").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int plan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		return StationActions.plan(context.getSource().getPlayerOrThrow(), StringArgumentType.getString(context, "description"), null) ? 1 : 0;
	}

	private static int run(CommandContext<ServerCommandSource> context, Predicate<ServerPlayerEntity> action) throws CommandSyntaxException {
		return action.test(context.getSource().getPlayerOrThrow()) ? 1 : 0;
	}
}
