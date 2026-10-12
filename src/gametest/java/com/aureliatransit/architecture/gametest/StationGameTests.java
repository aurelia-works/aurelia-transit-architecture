package com.aureliatransit.architecture.gametest;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.registry.ModItems;
import com.aureliatransit.architecture.station.world.MtrLiftContract;
import com.aureliatransit.architecture.station.world.StationActions;
import com.aureliatransit.architecture.station.world.StationGuide;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalDir;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.RoofMaterial;
import com.aureliatransit.architecture.station.RoofType;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.SiteIssue;
import com.aureliatransit.architecture.station.SiteSurvey;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationGeometry;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.StationStyle;
import com.aureliatransit.architecture.station.StylePalette;
import com.aureliatransit.architecture.station.SurfaceMaterial;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.TrackLayout;
import com.aureliatransit.architecture.station.WorldOp;
import com.aureliatransit.architecture.station.world.BlockStateResolver;
import com.aureliatransit.architecture.station.world.StationBuildJob;
import com.aureliatransit.architecture.station.world.StationCommand;
import com.aureliatransit.architecture.station.world.StationPreview;
import com.aureliatransit.architecture.station.world.StationSessions;
import com.aureliatransit.architecture.station.world.WorldSiteProbe;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.aureliatransit.architecture.gametest.Fixtures.fail;

/**
 * The station generator in a real world: block state resolution, the site probe, a full plan-survey-build, batching, cancel,
 * undo, the terrain rule and approval, rail nodes and MTR's platform marker on every palette edge and surface.
 *
 * <p>The sites are stone floors two blocks deep with air above ({@code station_site_z}: tracks along z, the planner faces
 * south; {@code station_site_x}: tracks along x, faces east), so a small station fits inside its own test plot.
 */
public class StationGameTests implements FabricGameTest {

	private static final String SITE_Z = "aurelia_transit_architecture_gametest:station_site_z";
	private static final String SITE_X = "aurelia_transit_architecture_gametest:station_site_x";
	/** Like station_site_z, 24 wide, 14 high and 38 long: room for a station building beside the tracks or a terminus. */
	private static final String SITE_LARGE = "aurelia_transit_architecture_gametest:station_site_large";
	private static final BlockPos FEET_LARGE = new BlockPos(12, 3, 1);
	private static final String TERMINUS = "two track terminus, island platform, length 16, width 3, train shed, named Test";
	private static final String SIDE_BUILDING = "single track station building, length 16, width 3, named Test";
	/** The smallest footbridge station with lifts: platforms six wide take a two-column stair and a lift beside it. */
	private static final String LIFTS = "two track station, side platforms, length 24, width 6, footbridge, named Test";
	/**
	 * Feet of the imaginary planning player in each site: rail level, on the template's two stone layers. Test-relative y is one
	 * below template y (the structure sits one above the test origin), so template y = 2 is relative y = 3.
	 */
	private static final BlockPos FEET_Z = new BlockPos(6, 3, 1);
	private static final BlockPos FEET_X = new BlockPos(1, 3, 6);
	private static final String SMALL = "1 track, length 16, width 3, canopy, named Test";
	private static final String ATA = AureliaTransitArchitecture.MOD_ID + ":";

	// ---- resolution and probing ------------------------------------------------------------------------------------

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void resolverSetsFacingAxisAndRailNode(TestContext context) {
		final Block edge = BlockStateResolver.block(ATA + "platform_edge").orElseThrow(() -> fail("platform_edge missing"));
		for (final Heading heading : Heading.values()) {
			final BlockState state = BlockStateResolver.state(edge, heading);
			check(state.get(Properties.HORIZONTAL_FACING) == BlockStateResolver.direction(heading), "edge facing " + heading);
		}
		final Block beam = BlockStateResolver.block(ATA + "structural_beam").orElseThrow(() -> fail("structural_beam missing"));
		check(BlockStateResolver.state(beam, Heading.EAST).get(Properties.HORIZONTAL_AXIS) == Direction.Axis.X, "beam axis east");
		check(BlockStateResolver.state(beam, Heading.NORTH).get(Properties.HORIZONTAL_AXIS) == Direction.Axis.Z, "beam axis north");
		check(BlockStateResolver.block(ATA + "no_such_block").isEmpty(), "an unknown id must not resolve (never air)");
		check(BlockStateResolver.block("not an id").isEmpty(), "a malformed id must not resolve");
		final Block rail = BlockStateResolver.block(StylePalette.MTR_RAIL_NODE).orElseThrow(() -> fail("mtr:rail missing (MTR not loaded?)"));
		for (final Heading heading : Heading.values()) {
			final BlockState state = BlockStateResolver.state(rail, heading);
			check(flag(state, "facing") == heading.alongX(), "rail node facing for " + heading);
			check(!flag(state, "is_connected") && !flag(state, "is_45") && !flag(state, "is_22_5"), "rail node flags for " + heading);
		}
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void probeClassifiesCells(TestContext context) {
		final WorldSiteProbe probe = new WorldSiteProbe(context.getWorld());
		context.setBlockState(1, 1, 1, Blocks.STONE);
		context.setBlockState(3, 1, 1, Blocks.GRASS_BLOCK);
		context.setBlockState(3, 2, 1, Blocks.GRASS);
		context.setBlockState(5, 1, 1, Blocks.WATER);
		context.setBlockState(1, 1, 3, Blocks.CHEST);
		context.setBlockState(3, 1, 3, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
		check(kind(context, probe, 1, 4, 1) == CellKind.AIR, "air");
		check(kind(context, probe, 1, 1, 1) == CellKind.SOLID && sturdy(context, probe, 1, 1, 1), "stone is solid and sturdy");
		check(kind(context, probe, 3, 2, 1) == CellKind.SOFT, "grass plant is soft");
		check(kind(context, probe, 5, 1, 1) == CellKind.FLUID, "water is fluid");
		check(kind(context, probe, 1, 1, 3) == CellKind.PROTECTED, "a chest is protected");
		check(kind(context, probe, 3, 1, 3) == CellKind.SOLID && !sturdy(context, probe, 3, 1, 3), "a bottom slab is solid but not sturdy");
		final BlockPos top = context.getAbsolutePos(new BlockPos(0, 0, 0)).withY(context.getWorld().getTopY());
		check(probe.kind(top.getX(), top.getY(), top.getZ()) == CellKind.OUT_OF_WORLD, "above the world");
		context.complete();
	}

	// ---- full flow -------------------------------------------------------------------------------------------------

	/** Plan, survey and build through the command's own path; the server tick runs the job. */
	@GameTest(templateName = SITE_Z, tickLimit = 100)
	public void stationBuildsThroughTheCommandPath(TestContext context) {
		final ServerWorld world = context.getWorld();
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, context.getAbsolutePos(FEET_Z), Heading.SOUTH);
		check(pending.violations().isEmpty(), "design violations: " + pending.violations());
		check(pending.report().counts().isEmpty(), "site issues on flat ground: " + pending.report().counts());
		check(pending.audit().buildable() && pending.audit().problems().isEmpty(), "station checks: " + pending.audit().problems());
		final List<Text> said = new ArrayList<>();
		final UUID owner = new UUID(0, 1);
		final StationAudit.Result failed = new StationAudit.Result(List.of(new StationAudit.Check(StationAudit.Topic.FACES, StationAudit.Status.FAIL, "test")),
				List.of(), List.of(), 0, 0, 0);
		final StationSessions.Pending failing = new StationSessions.Pending(pending.parse(), pending.spec(), pending.plan(), pending.frame(), pending.dimension(),
				pending.violations(), failed, pending.report(), pending.railSkipped());
		check(StationCommand.startBuild(world, owner, failing, false, said::add) == null, "a failed station check did not stop the build");
		said.clear();
		final StationBuildJob job = StationCommand.startBuild(world, owner, pending, false, said::add);
		check(job != null, "build refused: " + said);
		check(job.total() > StationBuildJob.OPS_PER_TICK, "the test station must need more than one tick (" + job.total() + " ops)");
		context.waitAndRun(10, () -> {
			check(job.state() == StationBuildJob.State.DONE, "job state " + job.state() + " " + job.done() + "/" + job.total() + " " + said);
			check(job.terrainChanged() == 0, "terrain changed without approval");
			checkBuilt(context, pending);
			StationSessions.cancelJob(owner);
			context.complete();
		});
	}

	/** A terminus (head building, buffer stops, gate line, train shed) built in the world. */
	@GameTest(templateName = SITE_LARGE, tickLimit = 200)
	public void terminusBuildsThroughTheCommandPath(TestContext context) {
		buildsEveryPlannedBlock(context, TERMINUS, new UUID(0, 11));
	}

	/** A station building beside the tracks (ticket hall, two-high ticket machines, stairs, upper floor) built in the world. */
	@GameTest(templateName = SITE_LARGE, tickLimit = 200)
	public void sideBuildingBuildsThroughTheCommandPath(TestContext context) {
		buildsEveryPlannedBlock(context, SIDE_BUILDING, new UUID(0, 12));
	}

	/**
	 * A footbridge station's lifts: shafts, MTR stops, landing doors and call buttons built as planned, each button linked to
	 * the stop at its level, the stops numbered from the bottom, an MTR lift formed per shaft and deleted again by undo.
	 */
	@GameTest(templateName = SITE_LARGE, tickLimit = 200)
	public void footbridgeLiftsAreFormedAndUndone(TestContext context) {
		check(MtrLiftContract.available(), "MTR's lift API was not found");
		buildsEveryPlannedBlock(context, LIFTS, new UUID(0, 13), (pending, job) -> {
			final ServerWorld world = context.getWorld();
			final List<BlockPos> stops = new ArrayList<>();
			final List<BlockPos> buttons = new ArrayList<>();
			for (final PlannedBlock block : pending.plan().blocks()) {
				if (block.blockId().equals(StylePalette.MTR_LIFT_FLOOR)) {
					stops.add(cellPos(pending, block));
				} else if (block.blockId().equals(StylePalette.MTR_LIFT_BUTTONS)) {
					buttons.add(cellPos(pending, block));
				}
			}
			check(stops.size() == 4 && buttons.size() == 4, "expected two lifts with two stops and two buttons each: " + stops + " " + buttons);
			check(job.undoLog().lifts() == 2, "lifts formed: " + job.undoLog().lifts());
			for (final BlockPos stop : stops) {
				final NbtCompound data = world.getBlockEntity(stop).createNbt();
				final boolean bottom = stops.stream().noneMatch(other -> other.getX() == stop.getX() && other.getZ() == stop.getZ() && other.getY() < stop.getY());
				check(data.getString("floor_number").equals(bottom ? "0" : "1"), "stop " + stop + " numbered " + data.getString("floor_number"));
			}
			for (final BlockPos button : buttons) {
				final long[] linked = world.getBlockEntity(button).createNbt().getLongArray("track_floor_pos");
				final BlockPos stop = linked.length == 1 ? BlockPos.fromLong(linked[0]) : null;
				check(stop != null && stops.contains(stop) && stop.getY() == button.getY() && stop.getSquaredDistance(button) <= 9,
						"button " + button + " linked to " + java.util.Arrays.toString(linked));
			}
		});
	}

	/** The cells of a planned station and one around them (neighbouring test plots are cleared with drops of their own). */
	private static Box around(StationSessions.Pending pending) {
		Box box = null;
		for (final PlannedBlock block : pending.plan().blocks()) {
			final Box cell = new Box(cellPos(pending, block));
			box = box == null ? cell : box.union(cell);
		}
		return box.expand(1);
	}

	private static void buildsEveryPlannedBlock(TestContext context, String description, UUID owner) {
		buildsEveryPlannedBlock(context, description, owner, (pending, job) -> {
		});
	}

	/**
	 * Plans a description through the command path without rule breaks, site issues, warnings or failed checks, builds it, and
	 * checks every planned block is in the world with its planned id, then runs {@code built}; then undo restores every cell.
	 */
	private static void buildsEveryPlannedBlock(TestContext context, String description, UUID owner,
			java.util.function.BiConsumer<StationSessions.Pending, StationBuildJob> built) {
		final ServerWorld world = context.getWorld();
		final StationSessions.Pending pending = StationCommand.prepare(world, description, context.getAbsolutePos(FEET_LARGE), Heading.SOUTH);
		check(pending.parse().warnings().isEmpty() && pending.parse().ignored().isEmpty(),
				"not a clean description: " + pending.parse().warnings() + " " + pending.parse().ignored());
		check(pending.violations().isEmpty(), "design violations: " + pending.violations());
		check(pending.report().counts().isEmpty(), "site issues on flat ground: " + pending.report().counts());
		check(pending.audit().buildable() && pending.audit().problems().isEmpty(), "station checks: " + pending.audit().problems());
		final List<Text> said = new ArrayList<>();
		final StationBuildJob job = StationCommand.startBuild(world, owner, pending, false, said::add);
		check(job != null, "build refused: " + said);
		context.waitAndRun(60, () -> {
			check(job.state() == StationBuildJob.State.DONE, "job state " + job.state() + " " + job.done() + "/" + job.total() + " " + said);
			final List<String> problems = new ArrayList<>();
			for (final PlannedBlock block : pending.plan().blocks()) {
				final String id = Registries.BLOCK.getId(world.getBlockState(cellPos(pending, block)).getBlock()).toString();
				if (!id.equals(block.blockId())) {
					problems.add(block + ": found " + id);
				}
			}
			Fixtures.failIfAny(problems, "built blocks of \"" + description + "\"");
			built.accept(pending, job);
			StationSessions.cancelJob(owner);
			// undo puts every cell back, two-high blocks included, and skips none
			final StationBuildJob undo = StationBuildJob.undo(world, owner, job.undoLog(), text -> {
			});
			while (!undo.finished()) {
				undo.step(StationBuildJob.OPS_PER_TICK);
			}
			check(undo.restored() == job.placed() && undo.skipped() == 0, "undo restored " + undo.restored() + " of " + job.placed() + ", skipped "
					+ undo.skipped());
			check(job.undoLog().lifts() == 0, "undo left " + job.undoLog().lifts() + " lifts to delete");
			// two-high and paired blocks (ticket machines, kiosks, lift doors) go without dropping as items (none dropped this tick)
			final List<ItemEntity> dropped = world.getEntitiesByClass(ItemEntity.class, around(pending), entity -> entity.getItemAge() == 0);
			check(dropped.isEmpty(), "undo dropped items: " + dropped.stream().map(entity -> entity.getStack().toString()).toList());
			for (final PlannedBlock block : pending.plan().blocks()) {
				check(world.getBlockState(cellPos(pending, block)).isAir(), "not restored to air at " + block);
			}
			context.complete();
		});
	}

	@GameTest(templateName = SITE_Z)
	public void jobRunsInBatchesAndCancelThenUndoRestoresAir(TestContext context) {
		final ServerWorld world = context.getWorld();
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, context.getAbsolutePos(FEET_Z), Heading.SOUTH);
		final StationBuildJob job = StationBuildJob.build(world, new UUID(0, 2), pending.report().ops(), false, text -> {
		});
		check(job.step(StationBuildJob.OPS_PER_TICK) == StationBuildJob.OPS_PER_TICK, "first batch size");
		check(!job.finished() && job.done() == StationBuildJob.OPS_PER_TICK, "one batch must not finish the station");
		job.cancel();
		check(job.state() == StationBuildJob.State.CANCELLED && job.step(64) == 0, "cancelled job must not continue");
		final int placed = job.placed();
		check(placed == StationBuildJob.OPS_PER_TICK && placed < job.total(), "placed " + placed + " of " + job.total());
		// a player changes one built cell: undo must leave it
		final BlockPos changed = cellPos(pending, pending.plan().blocks().get(0));
		world.setBlockState(changed, Blocks.GLASS.getDefaultState());
		final StationBuildJob undo = StationBuildJob.undo(world, new UUID(0, 2), job.undoLog(), text -> {
		});
		while (!undo.finished()) {
			undo.step(StationBuildJob.OPS_PER_TICK);
		}
		check(undo.restored() == placed - 1 && undo.skipped() == 1, "undo restored " + undo.restored() + ", skipped " + undo.skipped());
		check(world.getBlockState(changed).isOf(Blocks.GLASS), "undo removed a cell the player changed");
		for (final PlannedBlock block : pending.plan().blocks()) {
			final BlockPos pos = cellPos(pending, block);
			if (!pos.equals(changed)) {
				check(world.getBlockState(pos).isAir(), "not restored to air at " + block);
			}
		}
		context.complete();
	}

	@GameTest(templateName = SITE_Z)
	public void cellChangedAfterSurveyStopsTheJob(TestContext context) {
		final ServerWorld world = context.getWorld();
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, context.getAbsolutePos(FEET_Z), Heading.SOUTH);
		final List<PlannedBlock> blocks = pending.plan().blocks();
		final int blocked = blocks.size() / 2;
		world.setBlockState(cellPos(pending, blocks.get(blocked)), Blocks.STONE.getDefaultState());
		final StationBuildJob job = StationBuildJob.build(world, new UUID(0, 3), pending.report().ops(), false, text -> {
		});
		while (!job.finished()) {
			job.step(StationBuildJob.OPS_PER_TICK);
		}
		check(job.state() == StationBuildJob.State.STOPPED, "job must stop at the changed cell, was " + job.state());
		check(job.done() == blocked, "stopped after " + job.done() + " ops, expected " + blocked);
		for (int i = blocked + 1; i < blocks.size(); i++) {
			check(world.getBlockState(cellPos(pending, blocks.get(i))).isAir(), "a cell after the stop was written: " + blocks.get(i));
		}
		context.complete();
	}

	@GameTest(templateName = SITE_Z, tickLimit = 120)
	public void occupiedCellNeedsApprovalAndUndoRestoresTerrain(TestContext context) {
		final ServerWorld world = context.getWorld();
		final BlockPos feet = context.getAbsolutePos(FEET_Z);
		final StationSessions.Pending probe = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);
		final PlannedBlock target = firstOf(probe, Role.PLATFORM_SURFACE);
		final BlockPos rock = cellPos(probe, target);
		world.setBlockState(rock, Blocks.STONE.getDefaultState());
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);
		check(pending.report().count(SiteIssue.Kind.OCCUPIED) == 1 && !pending.report().buildable(), "stone in the footprint: " + pending.report().counts());
		final List<Text> said = new ArrayList<>();
		final UUID owner = new UUID(0, 4);
		check(StationCommand.startBuild(world, owner, pending, false, said::add) == null, "built over terrain without approval");
		check(StationSessions.job(owner) == null, "a refused build must not start a job");
		check(world.getBlockState(rock).isOf(Blocks.STONE), "refused build changed the terrain");
		check(world.getBlockState(cellPos(pending, firstOf(pending, Role.PLATFORM_EDGE))).isAir(), "refused build placed blocks");
		final StationBuildJob job = StationCommand.startBuild(world, owner, pending, true, said::add);
		check(job != null, "approved build refused: " + said);
		context.waitAndRun(10, () -> {
			check(job.state() == StationBuildJob.State.DONE && job.terrainChanged() == 1, "approved job " + job.state() + ", terrain " + job.terrainChanged());
			check(Registries.BLOCK.getId(world.getBlockState(rock).getBlock()).toString().equals(target.blockId()), "stone not replaced by the plan");
			final StationBuildJob undo = StationBuildJob.undo(world, owner, StationSessions.undoLog(owner), text -> {
			});
			while (!undo.finished()) {
				undo.step(StationBuildJob.OPS_PER_TICK);
			}
			check(world.getBlockState(rock).isOf(Blocks.STONE), "undo did not restore the terrain");
			context.complete();
		});
	}

	/**
	 * The real {@code /ata_station} tree, run by a mock player: the operator gate, the greedy description, approval words in the
	 * description being ignored, the {@code confirm replace_terrain} literal, and undo through the server tick.
	 */
	@GameTest(templateName = SITE_Z, tickLimit = 160)
	public void slashCommandGatesPlansConfirmsAndUndoes(TestContext context) {
		final ServerWorld world = context.getWorld();
		final BlockPos feet = context.getAbsolutePos(FEET_Z);
		final StationSessions.Pending probe = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);
		final PlannedBlock target = firstOf(probe, Role.PLATFORM_SURFACE);
		final BlockPos rock = cellPos(probe, target);
		world.setBlockState(rock, Blocks.STONE.getDefaultState());
		final ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		player.refreshPositionAndAngles(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Direction.SOUTH.asRotation(), 0.0F);
		final UUID id = player.getUuid();
		final ServerCommandSource denied = player.getCommandSource().withLevel(0);
		final ServerCommandSource op = player.getCommandSource().withLevel(2);
		final CommandManager commands = world.getServer().getCommandManager();

		check(commands.executeWithPrefix(denied, "/ata_station plan " + SMALL) == 0, "a non-operator ran /ata_station plan");
		check(StationSessions.pending(id) == null, "a non-operator got a pending plan");

		check(commands.executeWithPrefix(op, "/ata_station plan " + SMALL + ", replace terrain") == 1, "plan failed");
		final StationSessions.Pending pending = StationSessions.pending(id);
		check(pending != null && pending.spec().name().equals("Test") && pending.spec().platformLength() == 16, "greedy description not parsed: "
				+ (pending == null ? "no plan" : pending.spec()));
		check(pending.frame().forward() == Heading.SOUTH, "plan faces " + pending.frame().forward());
		check(pending.report().count(SiteIssue.Kind.OCCUPIED) == 1 && !pending.report().replaceTerrain(), "description approved terrain: "
				+ pending.report().counts());
		check(commands.executeWithPrefix(op, "/ata_station confirm") == 0, "confirm built over terrain without the literal approval");
		check(StationSessions.job(id) == null && world.getBlockState(rock).isOf(Blocks.STONE), "a refused confirm started a build");
		check(StationSessions.pending(id) != null, "a refused confirm dropped the plan");
		check(commands.executeWithPrefix(op, "/ata_station status") == 1, "status failed");

		check(commands.executeWithPrefix(op, "/ata_station confirm " + StationCommand.REPLACE_TERRAIN) == 1, "approved confirm failed");
		final StationBuildJob job = StationSessions.job(id);
		check(job != null && StationSessions.pending(id) == null, "approved confirm must start a job and consume the plan");
		context.waitAndRun(10, () -> {
			check(job.state() == StationBuildJob.State.DONE && job.terrainChanged() == 1, "job " + job.state() + ", terrain " + job.terrainChanged());
			check(Registries.BLOCK.getId(world.getBlockState(rock).getBlock()).toString().equals(target.blockId()), "stone not replaced by the plan");
			check(commands.executeWithPrefix(op, "/ata_station undo") == 1, "undo failed");
			context.waitAndRun(10, () -> {
				check(world.getBlockState(rock).isOf(Blocks.STONE), "undo did not restore the terrain");
				check(world.getBlockState(cellPos(pending, firstOf(pending, Role.PLATFORM_EDGE))).isAir(), "undo left the platform edge");
				StationSessions.cancelJob(id);
				StationSessions.forgetPlayer(id);
				Fixtures.removePlayer(context, player);
				context.complete();
			});
		});
	}

	/**
	 * Stations planned onto an existing MTR line: a node already on the track line (a station without nodes, or one in a planned
	 * node cell) is kept and never cleared or re-placed, even with terrain approval; a node anywhere else is never touched.
	 * Removing or replacing a connected node with a block change would orphan MTR's rail data.
	 */
	@GameTest(templateName = SITE_Z)
	public void existingMtrRailNodesAreKeptOrRefusedNeverReplaced(TestContext context) {
		final ServerWorld world = context.getWorld();
		final BlockPos feet = context.getAbsolutePos(FEET_Z);
		final Block rail = BlockStateResolver.block(StylePalette.MTR_RAIL_NODE).orElseThrow(() -> fail("mtr:rail missing (MTR not loaded?)"));
		final BlockState connected = with(BlockStateResolver.state(rail, Heading.SOUTH), "is_connected", true);
		final List<String> problems = new ArrayList<>();

		// a: a station without nodes, over a node on its track line
		final StationSessions.Pending bare = StationCommand.prepare(world, SMALL + ", no rail nodes", feet, Heading.SOUTH);
		final BlockPos onLine = pos(bare.frame(), bare.plan().layout().tracks().get(0).u(), StationGeometry.PLATFORM_Y, 5);
		world.setBlockState(onLine, connected);
		existingNodeKept("a (track line, no nodes planned)", world, StationCommand.prepare(world, SMALL + ", no rail nodes", feet, Heading.SOUTH),
				onLine, connected, problems);
		world.setBlockState(onLine, Blocks.AIR.getDefaultState());

		// b: a station with nodes, over a node already in a planned node cell
		final StationSessions.Pending withNodes = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);
		final BlockPos nodeCell = cellPos(withNodes, firstOf(withNodes, Role.RAIL_NODE));
		world.setBlockState(nodeCell, connected);
		existingNodeKept("b (planned node cell)", world, StationCommand.prepare(world, SMALL, feet, Heading.SOUTH), nodeCell, connected, problems);
		world.setBlockState(nodeCell, Blocks.AIR.getDefaultState());

		// c: a node where the platform goes is refused outright
		final BlockPos floor = cellPos(withNodes, firstOf(withNodes, Role.PLATFORM_SURFACE));
		world.setBlockState(floor, connected);
		final StationSessions.Pending onFloor = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);
		final List<Text> said = new ArrayList<>();
		final StationBuildJob refused = StationCommand.startBuild(world, new UUID(0, 9), onFloor, true, said::add);
		if (refused != null || !onFloor.report().hasHardIssue() || !world.getBlockState(floor).equals(connected)) {
			problems.add("c (node on a platform cell): kind " + new WorldSiteProbe(world).kind(floor.getX(), floor.getY(), floor.getZ())
					+ ", issues " + onFloor.report().counts() + ", approved build started " + (refused != null) + ", cell now " + world.getBlockState(floor));
		}
		StationSessions.cancelJob(new UUID(0, 9));
		if (!problems.isEmpty()) {
			throw fail(problems.size() + " problem(s):\n  - " + String.join("\n  - ", problems));
		}
		context.complete();
	}

	/** Surveys with and without approval, builds with approval and checks the node at {@code cell} was never touched. */
	private static void existingNodeKept(String what, ServerWorld world, StationSessions.Pending pending, BlockPos cell, BlockState node,
			List<String> problems) {
		final WorldSiteProbe probe = new WorldSiteProbe(world);
		final SurveyReport plain = pending.report();
		final SurveyReport approved = SiteSurvey.survey(pending.plan(), pending.frame(), probe, true);
		final String facts = "kind " + probe.kind(cell.getX(), cell.getY(), cell.getZ()) + ", issues " + plain.counts() + ", approved ops there "
				+ opsAt(approved, cell);
		if (!plain.counts().isEmpty() || !plain.buildable()) {
			problems.add(what + ": an existing node must not need approval; " + facts);
		}
		if (!opsAt(approved, cell).isEmpty()) {
			problems.add(what + ": an approved build would write the node cell; " + facts);
		}
		final StationBuildJob job = StationBuildJob.build(world, new UUID(0, 9), BlockStateResolver.resolve(approved.ops()), true, text -> {
		});
		while (!job.finished()) {
			job.step(StationBuildJob.OPS_PER_TICK);
		}
		if (!world.getBlockState(cell).equals(node)) {
			problems.add(what + ": the build changed the node to " + world.getBlockState(cell) + "; job " + job.state());
		}
		final StationBuildJob undo = StationBuildJob.undo(world, new UUID(0, 9), job.undoLog(), text -> {
		});
		while (!undo.finished()) {
			undo.step(StationBuildJob.OPS_PER_TICK);
		}
		if (!world.getBlockState(cell).equals(node)) {
			problems.add(what + ": undo changed the node to " + world.getBlockState(cell));
		}
	}

	private static List<WorldOp> opsAt(SurveyReport report, BlockPos pos) {
		return report.ops().stream().filter(op -> op.x() == pos.getX() && op.y() == pos.getY() && op.z() == pos.getZ()).toList();
	}

	/**
	 * Grass under a built station turns into dirt (vanilla, on a random tick, not a write of ours): undo puts the grass back, but
	 * leaves a cell the player changed in the meantime.
	 */
	@GameTest(templateName = SITE_Z)
	public void undoRestoresGrassThatDecayedUnderTheStation(TestContext context) {
		final ServerWorld world = context.getWorld();
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, context.getAbsolutePos(FEET_Z), Heading.SOUTH);
		final List<PlannedBlock> floors = pending.plan().blocks().stream().filter(block -> block.role() == Role.PLATFORM_SURFACE).toList();
		final BlockPos decayed = cellPos(pending, floors.get(0)).down();
		final BlockPos changed = cellPos(pending, floors.get(1)).down();
		world.setBlockState(decayed, Blocks.GRASS_BLOCK.getDefaultState());
		world.setBlockState(changed, Blocks.GRASS_BLOCK.getDefaultState());
		final StationBuildJob job = StationBuildJob.build(world, new UUID(0, 10), pending.report().ops(), false, text -> {
		});
		while (!job.finished()) {
			job.step(StationBuildJob.OPS_PER_TICK);
		}
		check(job.state() == StationBuildJob.State.DONE, "build " + job.state());
		world.setBlockState(decayed, Blocks.DIRT.getDefaultState());
		world.setBlockState(changed, Blocks.COBBLESTONE.getDefaultState());
		final StationBuildJob undo = StationBuildJob.undo(world, new UUID(0, 10), job.undoLog(), text -> {
		});
		while (!undo.finished()) {
			undo.step(StationBuildJob.OPS_PER_TICK);
		}
		check(world.getBlockState(cellPos(pending, floors.get(0))).isAir(), "floor not removed");
		check(world.getBlockState(decayed).isOf(Blocks.GRASS_BLOCK), "decayed grass not restored: " + world.getBlockState(decayed));
		check(world.getBlockState(changed).isOf(Blocks.COBBLESTONE), "undo changed a cell the player changed: " + world.getBlockState(changed));
		check(undo.restored() == job.placed() && undo.skipped() == 0, "undo restored " + undo.restored() + " of " + job.placed());
		context.complete();
	}

	// ---- station wrench --------------------------------------------------------------------------------------------

	/**
	 * The wrench as a player uses it: a click plans on the clicked block (the same frame as planning from the feet two blocks
	 * back), a sneak-click builds without approving terrain, a left-click cancels, and it never breaks blocks. A non-operator's
	 * clicks and screen packets do nothing.
	 */
	@GameTest(templateName = SITE_Z, tickLimit = 120)
	public void stationWrenchPlansBuildsCancelsAndIsOperatorOnly(TestContext context) {
		final ServerWorld world = context.getWorld();
		final ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		final BlockPos feet = context.getAbsolutePos(FEET_Z);
		player.refreshPositionAndAngles(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Direction.SOUTH.asRotation(), 0.0F);
		final UUID id = player.getUuid();
		final ItemStack wrench = new ItemStack(ModItems.STATION_WRENCH);
		StationWrenchItem.setDescription(wrench, SMALL);
		player.setStackInHand(Hand.MAIN_HAND, wrench);
		final BlockPos clicked = feet.offset(Direction.SOUTH, LocalFrame.GAP_IN_FRONT).down();
		final StationSessions.Pending fromFeet = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);

		// not an operator: nothing happens, and the screen packet does not even save the description
		check(!player.hasPermissionLevel(StationActions.PERMISSION_LEVEL), "the mock player is already an operator; this test needs one that is not");
		click(player, clicked);
		StationWrenchItem.perform(player, Hand.MAIN_HAND, StationWrenchItem.Action.PLAN, "4 track metro station", "");
		check(StationSessions.pending(id) == null, "a non-operator planned with the wrench");
		check(StationWrenchItem.description(player.getMainHandStack()).equals(SMALL), "a non-operator changed the wrench description");

		// an explicit level: the test server's default operator level grants nothing
		world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
		check(player.hasPermissionLevel(StationActions.PERMISSION_LEVEL), "op did not take");
		check(!ModItems.STATION_WRENCH.canMine(world.getBlockState(clicked), world, clicked, player), "the wrench must never break blocks");

		click(player, clicked);
		final StationSessions.Pending pending = StationSessions.pending(id);
		check(pending != null && pending.frame().equals(fromFeet.frame()), "click frame " + (pending == null ? "none" : pending.frame()) + ", expected " + fromFeet.frame());
		check(StationWrenchItem.attack(player, world, Hand.MAIN_HAND) == ActionResult.SUCCESS && StationSessions.pending(id) == null, "left-click did not discard the plan");

		// the screen: save + plan from the feet, then a sneak-click builds
		StationWrenchItem.perform(player, Hand.MAIN_HAND, StationWrenchItem.Action.PLAN, SMALL + "  ", "");
		check(StationWrenchItem.description(player.getMainHandStack()).equals(SMALL), "screen did not save the description");
		check(StationSessions.pending(id) != null && StationSessions.pending(id).frame().equals(fromFeet.frame()), "screen plan is not in front of the feet");
		player.setSneaking(true);
		click(player, clicked);
		final StationBuildJob job = StationSessions.job(id);
		check(job != null && !job.approved(), "sneak-click must build without approving terrain");
		context.waitAndRun(10, () -> {
			check(job.state() == StationBuildJob.State.DONE, "job " + job.state());
			checkBuilt(context, fromFeet);
			StationSessions.cancelJob(id);
			StationSessions.forgetPlayer(id);
			world.getServer().getPlayerManager().removeFromOperators(player.getGameProfile());
			Fixtures.removePlayer(context, player);
			context.complete();
		});
	}

	private static void click(ServerPlayerEntity player, BlockPos clicked) {
		final BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(clicked).add(0, 0.5, 0), Direction.UP, clicked, false);
		player.getMainHandStack().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
	}

	@GameTest(templateName = SITE_X)
	public void eastFacingStationAndConnectedRailNodeSurvivesUndo(TestContext context) {
		final ServerWorld world = context.getWorld();
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, context.getAbsolutePos(FEET_X), Heading.EAST);
		check(pending.report().buildable(), "site not buildable: " + pending.report().counts());
		final StationBuildJob job = StationBuildJob.build(world, new UUID(0, 5), pending.report().ops(), false, text -> {
		});
		while (!job.finished()) {
			job.step(StationBuildJob.OPS_PER_TICK);
		}
		checkBuilt(context, pending);
		final List<BlockPos> nodes = new ArrayList<>();
		for (final PlannedBlock block : pending.plan().blocks()) {
			if (block.role() == Role.RAIL_NODE) {
				nodes.add(cellPos(pending, block));
			}
		}
		check(nodes.size() == 2, "rail nodes " + nodes.size());
		for (final BlockPos node : nodes) {
			check(flag(world.getBlockState(node), "facing"), "a track along x needs facing=true at " + node);
		}
		// what MTR's rail connector does to both ends when the player connects them
		final BlockState connected = with(world.getBlockState(nodes.get(0)), "is_connected", true);
		world.setBlockState(nodes.get(0), connected);
		final StationBuildJob undo = StationBuildJob.undo(world, new UUID(0, 5), job.undoLog(), text -> {
		});
		while (!undo.finished()) {
			undo.step(StationBuildJob.OPS_PER_TICK);
		}
		check(world.getBlockState(nodes.get(0)) == connected, "undo removed a connected rail node");
		check(world.getBlockState(nodes.get(1)).isAir(), "undo left an unconnected rail node");
		check(undo.keptNodes() == 1 && undo.skipped() == 0, "kept nodes " + undo.keptNodes() + ", skipped " + undo.skipped());
		context.complete();
	}

	@GameTest(templateName = SITE_Z)
	public void previewAndPlanningChangeNothing(TestContext context) {
		final ServerWorld world = context.getWorld();
		final Map<BlockPos, BlockState> before = snapshot(context);
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, context.getAbsolutePos(FEET_Z), Heading.SOUTH);
		final StationPreview preview = new StationPreview(new UUID(0, 6), pending.dimension(), pending.plan(), pending.frame(), pending.report());
		// no such player is online: the preview ends at once and never touches the world
		check(!preview.tick(world.getServer()), "a preview for an absent player must end");
		check(before.equals(snapshot(context)), "planning or previewing changed the world");
		context.complete();
	}

	@GameTest(templateName = SITE_Z)
	public void deepGapIsRefusedEvenWithApproval(TestContext context) {
		final ServerWorld world = context.getWorld();
		final BlockPos feet = context.getAbsolutePos(FEET_Z);
		final StationSessions.Pending probe = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);
		final PlannedBlock target = firstOf(probe, Role.PLATFORM_SURFACE);
		final BlockPos cell = cellPos(probe, target);
		// dig a shaft deeper than the fill limit under one platform cell
		for (int y = 1; y <= SiteSurvey.MAX_FILL_DEPTH + 1; y++) {
			world.setBlockState(cell.down(y), Blocks.AIR.getDefaultState());
		}
		final StationSessions.Pending pending = StationCommand.prepare(world, SMALL, feet, Heading.SOUTH);
		final SurveyReport approved = SiteSurvey.survey(pending.plan(), pending.frame(), new WorldSiteProbe(world), true);
		check(approved.count(SiteIssue.Kind.GAP_TOO_DEEP) == 1 && !approved.buildable(), "deep gap: " + approved.counts());
		check(StationCommand.startBuild(world, new UUID(0, 7), pending, true, text -> {
		}) == null, "built over a deep gap");
		context.complete();
	}

	// ---- guide book -------------------------------------------------------------------------------------------------

	/** The guide is a valid written book with every page; an operator gets it once on joining, a non-operator never. */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void guideBookIsGivenOnceToOperators(TestContext context) {
		final ItemStack book = StationGuide.book();
		check(book.isOf(Items.WRITTEN_BOOK) && WrittenBookItem.isValid(book.getNbt()), "the guide is not a valid written book: " + book.getNbt());
		check(WrittenBookItem.getPageCount(book) == StationGuide.PAGES.size(), "guide pages " + WrittenBookItem.getPageCount(book));

		final ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		check(!player.hasPermissionLevel(StationActions.PERMISSION_LEVEL), "the mock player is already an operator; this test needs one that is not");
		check(!StationGuide.welcome(player) && books(player) == 0, "a non-operator got the guide");
		context.getWorld().getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(), 4, false));
		check(StationGuide.welcome(player) && books(player) == 1, "an operator's first join gave " + books(player) + " guides");
		check(!StationGuide.welcome(player) && books(player) == 1, "a second join gave another guide");
		context.complete();
	}

	private static int books(ServerPlayerEntity player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			if (player.getInventory().getStack(slot).isOf(Items.WRITTEN_BOOK)) {
				count += player.getInventory().getStack(slot).getCount();
			}
		}
		return count;
	}

	// ---- palettes --------------------------------------------------------------------------------------------------

	/**
	 * Extra block state properties ({@code kind=end}, {@code half=lower} ...) are applied leniently, so a typo would only show in
	 * game. Every property a preset station plans must exist on its block and take the planned value.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void plannedStatePropertiesExistOnTheirBlocks(TestContext context) {
		final List<String> problems = new ArrayList<>();
		int checked = 0;
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			for (final PlannedBlock block : StationPlanner.plan(StationDescriptionParser.parse(preset.description()).spec()).blocks()) {
				if (block.state().isEmpty() || BlockStateResolver.block(block.blockId()).isEmpty()) {
					continue; // missing blocks (MTR absent) are reported by the palette tests
				}
				final BlockState state = BlockStateResolver.withState(BlockStateResolver.block(block.blockId()).get().getDefaultState(), block.state());
				for (final String pair : block.state().split(",")) {
					final String[] nameValue = pair.split("=", 2);
					final Property<?> property = state.getBlock().getStateManager().getProperty(nameValue[0].trim());
					checked++;
					if (property == null) {
						problems.add(preset.key() + " " + block.blockId() + " has no property " + nameValue[0]);
					} else if (nameValue.length < 2 || !name(state, property).equals(nameValue[1].trim())) {
						problems.add(preset.key() + " " + block.blockId() + " " + pair + " resolved to " + name(state, property));
					}
				}
			}
		}
		check(checked > 0, "no preset planned a block with extra state; the check proves nothing");
		Fixtures.failIfAny(problems, "planned state properties");
		context.complete();
	}

	private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
		return property.name(state.get(property));
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPaletteResolvesAndEdgesAndFloorsAreMtrPlatforms(TestContext context) {
		Class<?> platformHelper = null;
		try {
			platformHelper = Class.forName("org.mtr.mod.block.PlatformHelper");
		} catch (ClassNotFoundException e) {
			AureliaTransitArchitecture.LOGGER.warn("[gametest] MTR PlatformHelper not found; skipping the platform marker check");
		}
		final List<String> problems = new ArrayList<>();
		for (final StationStyle style : StationStyle.values()) {
			for (final SurfaceMaterial surface : SurfaceMaterial.values()) {
				for (final RoofMaterial roofMaterial : RoofMaterial.values()) {
					for (final RoofType roof : RoofType.values()) {
						for (final boolean tactile : new boolean[]{true, false}) {
							final StationSpec spec = StationSpec.builder().style(style).surface(surface).roofMaterial(roofMaterial).roof(roof)
									.tactile(tactile).build();
							final StylePalette palette = StylePalette.of(spec);
							for (final Role role : Role.values()) {
								if (BlockStateResolver.block(palette.blockId(role)).isEmpty()) {
									problems.add(spec + " " + role + " -> unknown " + palette.blockId(role));
								}
							}
							if (platformHelper != null) {
								for (final Role role : new Role[]{Role.PLATFORM_EDGE, Role.PLATFORM_SURFACE}) {
									final Block block = Registries.BLOCK.get(new Identifier(palette.blockId(role)));
									if (!platformHelper.isInstance(block)) {
										problems.add(style + " " + role + " " + palette.blockId(role) + " is not an MTR platform block");
									}
								}
							}
						}
					}
				}
			}
		}
		Fixtures.failIfAny(problems, "station palettes");
		context.complete();
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** Every planned block is in the world with the planned block and facing; the station's geometry is what MTR needs. */
	private static void checkBuilt(TestContext context, StationSessions.Pending pending) {
		final ServerWorld world = context.getWorld();
		final LocalFrame frame = pending.frame();
		final List<String> problems = new ArrayList<>();
		for (final PlannedBlock block : pending.plan().blocks()) {
			final BlockState state = world.getBlockState(cellPos(pending, block));
			final String id = Registries.BLOCK.getId(state.getBlock()).toString();
			if (!id.equals(block.blockId())) {
				problems.add(block + ": found " + id);
			} else if (state.contains(Properties.HORIZONTAL_FACING)
					&& state.get(Properties.HORIZONTAL_FACING) != BlockStateResolver.direction(frame.heading(block.front()))) {
				problems.add(block + ": facing " + state.get(Properties.HORIZONTAL_FACING));
			}
		}
		final TrackLayout layout = pending.plan().layout();
		final TrackLayout.Track track = layout.tracks().get(0);
		final TrackLayout.PlatformBand platform = layout.platforms().get(0);
		// the edge is EDGE_OFFSET from the rail node column and faces it, at rail level, for the whole platform
		if (platform.uMax() != track.u() - StationGeometry.EDGE_OFFSET) {
			problems.add("edge column " + platform.uMax() + " is not " + StationGeometry.EDGE_OFFSET + " from track " + track.u());
		}
		for (int v = 0; v < layout.platformLength(); v++) {
			final BlockState edge = world.getBlockState(pos(frame, platform.uMax(), 0, v));
			if (!edge.contains(Properties.HORIZONTAL_FACING) || edge.get(Properties.HORIZONTAL_FACING) != BlockStateResolver.direction(frame.heading(LocalDir.PLUS_U))) {
				problems.add("edge at v=" + v + " does not face the track: " + edge);
			}
			// the clearance envelope above the track stays empty (rail nodes sit at its floor at the platform ends)
			for (int y = 1; y < StationGeometry.ENVELOPE_HEIGHT; y++) {
				for (int du = -1; du <= 1; du++) {
					if (!world.getBlockState(pos(frame, track.u() + du, y, v)).isAir()) {
						problems.add("envelope not clear at u=" + (track.u() + du) + " y=" + y + " v=" + v);
					}
				}
			}
		}
		// step-free ramps beyond both ends, low end pointing away from the platform
		final BlockState rampStart = world.getBlockState(pos(frame, platform.uMin(), 0, -2));
		if (!Registries.BLOCK.getId(rampStart.getBlock()).getPath().equals("platform_ramp_lower")
				|| rampStart.get(Properties.HORIZONTAL_FACING) != BlockStateResolver.direction(frame.heading(LocalDir.MINUS_V))) {
			problems.add("start ramp wrong: " + rampStart);
		}
		// the three name signs join into one row and its owner holds the name
		final List<BlockPos> signs = new ArrayList<>();
		for (final PlannedBlock block : pending.plan().blocks()) {
			if (block.role() == Role.NAME_SIGN) {
				signs.add(cellPos(pending, block));
			}
		}
		if (signs.size() != 3) {
			problems.add("name signs " + signs.size());
		} else {
			int joins = 0;
			final java.util.Set<BlockPos> owners = new java.util.HashSet<>();
			for (final BlockPos sign : signs) {
				final BlockState state = world.getBlockState(sign);
				joins += (state.get(TextSignBlock.LEFT) ? 1 : 0) + (state.get(TextSignBlock.RIGHT) ? 1 : 0);
				owners.add(((TextSignBlock) state.getBlock()).findRowOwner(world, sign, state));
			}
			if (joins != 4 || owners.size() != 1) {
				problems.add("sign row not joined: " + joins + " joins, " + owners.size() + " owners");
			} else if (!(world.getBlockEntity(owners.iterator().next()) instanceof TextSignBlockEntity entity) || !entity.getData().primary().equals("Test")) {
				problems.add("row owner does not hold the station name");
			}
		}
		Fixtures.failIfAny(problems, "built station");
	}

	private static PlannedBlock firstOf(StationSessions.Pending pending, Role role) {
		return pending.plan().blocks().stream().filter(block -> block.role() == role).findFirst().orElseThrow(() -> fail("no " + role));
	}

	private static BlockPos cellPos(StationSessions.Pending pending, PlannedBlock block) {
		return pos(pending.frame(), block.u(), block.y(), block.v());
	}

	private static BlockPos pos(LocalFrame frame, int u, int y, int v) {
		return new BlockPos(frame.x(u, v), frame.y(y), frame.z(u, v));
	}

	private static Map<BlockPos, BlockState> snapshot(TestContext context) {
		final Map<BlockPos, BlockState> states = new HashMap<>();
		for (int x = 0; x < 12; x++) {
			for (int y = 0; y <= 8; y++) {
				for (int z = 0; z < 26; z++) {
					final BlockPos pos = context.getAbsolutePos(new BlockPos(x, y, z));
					states.put(pos, context.getWorld().getBlockState(pos));
				}
			}
		}
		return states;
	}

	private static CellKind kind(TestContext context, WorldSiteProbe probe, int x, int y, int z) {
		final BlockPos pos = context.getAbsolutePos(new BlockPos(x, y, z));
		return probe.kind(pos.getX(), pos.getY(), pos.getZ());
	}

	private static boolean sturdy(TestContext context, WorldSiteProbe probe, int x, int y, int z) {
		final BlockPos pos = context.getAbsolutePos(new BlockPos(x, y, z));
		return probe.sturdyTop(pos.getX(), pos.getY(), pos.getZ());
	}

	private static boolean flag(BlockState state, String name) {
		final Property<?> property = state.getBlock().getStateManager().getProperty(name);
		if (!(property instanceof BooleanProperty flag)) {
			throw fail(Registries.BLOCK.getId(state.getBlock()) + " has no boolean " + name);
		}
		return state.get(flag);
	}

	private static BlockState with(BlockState state, String name, boolean value) {
		return state.with((BooleanProperty) state.getBlock().getStateManager().getProperty(name), value);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw fail(message);
		}
	}
}
