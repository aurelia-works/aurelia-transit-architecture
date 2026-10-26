package com.aureliatransit.architecture.gametest;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalDir;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.SiteSurvey;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.TrackLayout;
import com.aureliatransit.architecture.station.WorldOp;
import com.aureliatransit.architecture.station.world.StationBuildJob;
import com.aureliatransit.architecture.station.world.StationSessions;
import com.aureliatransit.architecture.station.world.WorldSiteProbe;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A survey and a build walk a site far bigger than their chunk window: they never hold more than the window, let go of
 * everything when done, and the loaded chunk count comes back near the start once the walk is over. One marker block in each
 * chunk of a 400-chunk strip, walked with a window of 8 chunks. While a walk runs fast the count runs ahead of the window
 * (chunks unload some ticks after they are let go of; 10-26: about 6,000 over the start during the build, flat through the
 * undo), so the peak is not asserted here; {@code AirportScaleGameTests} measures it over 40,000 chunks.
 */
public class ChunkStreamingGameTests implements FabricGameTest {

	private static final int CHUNKS = 400;
	private static final int CAP = 8;
	/** Chunks over the start still loaded {@link #SETTLE_TICKS} after the walk is over. */
	private static final int SETTLED = 2500;
	private static final int SETTLE_TICKS = 200;

	private static final String MARKER = "minecraft:white_concrete";

	private static int loaded(ServerWorld world) {
		return world.getChunkManager().getLoadedChunkCount();
	}

	/** Far from every test plot (those are force-loaded), so nothing else keeps the strip's chunks. */
	private static BlockPos stripStart(TestContext context, int side) {
		final BlockPos origin = context.getAbsolutePos(new BlockPos(0, 4, 0));
		return new BlockPos(origin.getX() + 1024, origin.getY(), origin.getZ() + side * 4096);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 6000, batchId = "chunk_streaming_build")
	public void buildAndUndoKeepOnlyTheirWindowLoaded(TestContext context) {
		final ServerWorld world = context.getWorld();
		final BlockPos start = stripStart(context, 1);
		final List<WorldOp> ops = new ArrayList<>();
		for (int i = 0; i < CHUNKS; i++) {
			ops.add(new WorldOp(WorldOp.Kind.PLACE, start.getX() + i * 16, start.getY(), start.getZ(), Role.FLOOR, MARKER, Heading.NORTH, "", ""));
		}
		final UUID owner = new UUID(31, 7);
		final int before = loaded(world);
		final StationBuildJob build = StationBuildJob.build(world, owner, ops, false, text -> { }).holdingAtMost(CAP);
		final StationBuildJob[] undo = {null};
		final int[] settle = {0};
		context.runAtEveryTick(() -> {
			final StationBuildJob job = undo[0] == null ? build : undo[0];
			if (!job.finished()) {
				StationSessions.runBatches(List.of(job), StationSessions.NANOS_PER_TICK, StationSessions.OPS_PER_TICK);
				if (job.heldChunks() > CAP) {
					throw new GameTestException(job.mode() + " holds " + job.heldChunks() + " chunks, window " + CAP);
				}
				if (job.finished() && job.state() != StationBuildJob.State.DONE) {
					throw new GameTestException(job.mode() + " ended " + job.state() + " at " + job.done() + "/" + job.total());
				}
				if (job == build && job.finished()) {
					if (build.placed() != CHUNKS || !world.getBlockState(start.east(16 * (CHUNKS - 1))).isOf(net.minecraft.block.Blocks.WHITE_CONCRETE)) {
						throw new GameTestException("built " + build.placed() + " of " + CHUNKS);
					}
					if (build.heldChunks() != 0) {
						throw new GameTestException("the build still holds " + build.heldChunks() + " chunks when done");
					}
					undo[0] = StationBuildJob.undo(world, owner, build.undoLog(), text -> { }).holdingAtMost(CAP);
				}
				return;
			}
			if (job.restored() != CHUNKS) {
				throw new GameTestException("undo restored " + job.restored() + " of " + CHUNKS);
			}
			// the last window unloads over the next ticks
			if (++settle[0] >= SETTLE_TICKS) {
				System.out.println("CHUNK-STREAMING build+undo: " + (loaded(world) - before) + " chunks over the start " + SETTLE_TICKS + " ticks after");
				if (loaded(world) - before > SETTLED) {
					throw new GameTestException("still " + (loaded(world) - before) + " chunks over the start " + SETTLE_TICKS + " ticks after the undo");
				}
				context.complete();
			}
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 6000, batchId = "chunk_streaming_survey")
	public void aSurveyOverTicksKeepsOnlyItsWindowLoaded(TestContext context) {
		final ServerWorld world = context.getWorld();
		final BlockPos start = stripStart(context, -1);
		final List<PlannedBlock> blocks = new ArrayList<>();
		for (int i = 0; i < CHUNKS; i++) {
			blocks.add(new PlannedBlock(i * 16, 0, 0, Role.FLOOR, MARKER, LocalDir.PLUS_V, ""));
		}
		final TrackLayout layout = new TrackLayout(CHUNKS * 16, 1, 0, List.of(), List.of());
		final StationPlan plan = new StationPlan(StationSpec.defaults(), layout, blocks, List.of(), List.of(), List.of(), "minecraft:stone");
		// north-facing: +u is east, so the plan's cells run east along the strip
		final LocalFrame frame = new LocalFrame(start.getX(), start.getY(), start.getZ(), Heading.NORTH);
		final WorldSiteProbe probe = WorldSiteProbe.loading(world, CAP);
		final SiteSurvey.Survey survey = SiteSurvey.start(plan, frame, probe, false);
		final int before = loaded(world);
		final int[] settle = {0};
		context.runAtEveryTick(() -> {
			if (!survey.done()) {
				if (survey.step(System.nanoTime() + 5_000_000L)) {
					probe.release();
				}
				if (probe.held() > CAP) {
					throw new GameTestException("the survey holds " + probe.held() + " chunks, window " + CAP);
				}
				return;
			}
			final SurveyReport report = survey.report();
			if (!report.issues().isEmpty() || report.ops().size() != CHUNKS) {
				throw new GameTestException("survey: " + report.issues().size() + " issues, " + report.ops().size() + " ops of " + CHUNKS);
			}
			if (++settle[0] >= SETTLE_TICKS) {
				System.out.println("CHUNK-STREAMING survey: " + (loaded(world) - before) + " chunks over the start " + SETTLE_TICKS + " ticks after");
				if (loaded(world) - before > SETTLED) {
					throw new GameTestException("still " + (loaded(world) - before) + " chunks over the start " + SETTLE_TICKS + " ticks after the survey");
				}
				context.complete();
			}
		});
	}
}
