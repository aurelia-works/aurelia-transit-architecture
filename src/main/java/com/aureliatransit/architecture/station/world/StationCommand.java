package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LayoutValidator;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.SiteSurvey;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlan;
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

	/** Places a design with {@code place} and surveys its site (without approval). On the server thread. */
	public static StationSessions.Pending place(ServerWorld world, Design design, Function<TrackLayout, LocalFrame> place) {
		final StationPlan plan = design.plan();
		final LocalFrame frame = place.apply(plan.layout());
		final SurveyReport report = SiteSurvey.survey(plan, frame, WorldSiteProbe.loading(world), false);
		return new StationSessions.Pending(design.parse(), design.parse().spec(), plan, frame, world.getRegistryKey(), design.violations(), design.audit(),
				report, design.check().railSkipped());
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
		if (!pending.audit().buildable()) {
			out.accept(Text.literal("This plan fails " + pending.audit().count(StationAudit.Status.FAIL) + " station check(s) and cannot be built; "
					+ "plan again with different options.").formatted(Formatting.RED));
			return null;
		}
		final BlockStateResolver.Check check = BlockStateResolver.check(pending.plan());
		if (!check.ok()) {
			out.accept(Text.literal("Unknown blocks: " + String.join(", ", check.missing())).formatted(Formatting.RED));
			return null;
		}
		final SurveyReport report = SiteSurvey.survey(pending.plan(), pending.frame(), WorldSiteProbe.loading(world), replaceTerrain);
		if (!report.buildable()) {
			out.accept(Text.literal("The site changed or needs approval:").formatted(Formatting.RED));
			StationText.site(report, out);
			return null;
		}
		final StationBuildJob job = StationBuildJob.build(world, owner, BlockStateResolver.resolve(report.ops()), replaceTerrain, out,
				StationZone.of(pending.spec().name(), report.ops()));
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
