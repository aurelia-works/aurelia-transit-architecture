package com.aureliatransit.architecture.gametest;

import com.aureliatransit.architecture.station.ChunkHold;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.WorldOp;
import com.aureliatransit.architecture.station.world.StationBuildJob;
import com.aureliatransit.architecture.station.world.StationSessions;
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
 * Airport-scale builds (from the 1.7 spike, Jah 10-25): does the build job carry an airport-sized build at 1.5:1 with its
 * chunks streaming through? Each test builds through {@link StationSessions#runBatches} with the server's own per-tick
 * budget, then undoes, and prints its numbers with the prefix {@code AIRPORT-SPIKE}. It fails when the job holds more than
 * its window or the loaded chunks have not come back near the start once it is over. They take many minutes, so they only
 * run with {@code ATA_SCALE_TESTS=1} in the environment ({@code ATA_SCALE_TESTS=1 ./gradlew runGametest --offline});
 * otherwise they pass at once.
 */
public class AirportScaleGameTests implements FabricGameTest {

	/** A 3,000 m runway at 1.5:1: 4,500 long, 68 wide, one layer. */
	private static final int RUNWAY_LENGTH = 4500;
	private static final int RUNWAY_WIDTH = 68;
	/** Chunks over the start still loaded once the build and undo are over and they had time to unload. */
	private static final int SETTLED = 2000;

	private static boolean enabled() {
		return "1".equals(System.getenv("ATA_SCALE_TESTS"));
	}

	private static void say(String line) {
		System.out.println("AIRPORT-SPIKE " + line);
	}

	/** Heap in use, uncollected garbage included: for reading, not for asserting. */
	private static long heapMb() {
		final Runtime runtime = Runtime.getRuntime();
		return (runtime.totalMemory() - runtime.freeMemory()) >> 20;
	}

	/** Heap in use after a collection: closer to what is really kept. */
	private static long liveHeapMb() {
		System.gc();
		return heapMb();
	}

	private static WorldOp place(int x, int y, int z, String block) {
		return new WorldOp(WorldOp.Kind.PLACE, x, y, z, Role.FLOOR, block, Heading.NORTH, "", "");
	}

	/** Runs the job tick by tick at the server's pacing, then undoes it, then waits for the chunks to unload, printing what it took. */
	private static void run(TestContext context, String name, List<WorldOp> ops, int chunksTouched) {
		final ServerWorld world = context.getWorld();
		final UUID owner = new UUID(17, name.hashCode());
		final long heapBefore = liveHeapMb();
		final int loadedBefore = world.getChunkManager().getLoadedChunkCount();
		final long start = System.nanoTime();
		final StationBuildJob job = StationBuildJob.build(world, owner, ops, true, text -> say(name + " says: " + text.getString()));
		// build ticks, paused build ticks, undo ticks, settling ticks
		final int[] ticks = {0, 0, 0, 0};
		final long[] peak = {heapBefore, 0};
		final StationBuildJob[] undo = {null};
		say(name + ": " + ops.size() + " ops over about " + chunksTouched + " chunks; live heap " + heapBefore + " MB of "
				+ (Runtime.getRuntime().maxMemory() >> 20) + ", loaded chunks " + loadedBefore);
		context.runAtEveryTick(() -> {
			if (undo[0] == null) {
				ticks[0]++;
				StationSessions.runBatches(List.of(job), StationSessions.NANOS_PER_TICK, StationSessions.OPS_PER_TICK);
				if (job.paused()) {
					ticks[1]++;
				}
				if (job.heldChunks() > ChunkHold.DEFAULT_CAP) {
					throw new GameTestException(name + ": the build holds " + job.heldChunks() + " chunks");
				}
				peak[0] = Math.max(peak[0], heapMb());
				peak[1] = Math.max(peak[1], world.getChunkManager().getLoadedChunkCount());
				if (ticks[0] % 200 == 0) {
					say(name + ": tick " + ticks[0] + " done " + job.done() + "/" + job.total() + " heap " + heapMb() + " MB loaded chunks "
							+ world.getChunkManager().getLoadedChunkCount() + " held " + job.heldChunks());
				}
				if (job.finished()) {
					final long ms = (System.nanoTime() - start) / 1_000_000;
					say(name + ": BUILD " + job.state() + " " + job.done() + "/" + job.total() + " in " + ticks[0] + " ticks (" + ms + " ms wall, "
							+ (ticks[0] * 50L) + " ms at 20 tps), paused ticks " + ticks[1] + ", undo entries " + job.undoLog().size()
							+ ", peak heap " + peak[0] + " MB, peak loaded chunks " + peak[1]);
					undo[0] = StationBuildJob.undo(world, owner, job.undoLog(), text -> say(name + " undo says: " + text.getString()));
				}
			} else if (!undo[0].finished()) {
				ticks[2]++;
				StationSessions.runBatches(List.of(undo[0]), StationSessions.NANOS_PER_TICK, StationSessions.OPS_PER_TICK);
				if (undo[0].finished()) {
					say(name + ": UNDO " + undo[0].state() + " restored " + undo[0].restored() + " skipped " + undo[0].skipped() + " in " + ticks[2]
							+ " ticks; loaded chunks now " + world.getChunkManager().getLoadedChunkCount() + ", heap " + heapMb() + " MB");
					if (job.state() != StationBuildJob.State.DONE || undo[0].state() != StationBuildJob.State.DONE) {
						throw new GameTestException(name + ": build " + job.state() + ", undo " + undo[0].state());
					}
				}
			} else {
				// the last window unloads over the next ticks
				final int loaded = world.getChunkManager().getLoadedChunkCount();
				if (loaded - loadedBefore <= SETTLED || ++ticks[3] > 1200) {
					say(name + ": SETTLED loaded chunks " + loaded + " (start " + loadedBefore + ") after " + ticks[3] + " ticks, live heap "
							+ liveHeapMb() + " MB (start " + heapBefore + ")");
					if (loaded - loadedBefore > SETTLED) {
						throw new GameTestException(name + ": " + (loaded - loadedBefore) + " chunks still loaded over the start");
					}
					context.complete();
				}
			}
		});
	}

	/** One runway, 306,000 blocks along 4,500 blocks: about 1,400 chunks in a strip, far past any view distance. */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 24000, batchId = "airport_runway")
	public void runwayStripAcrossChunks(TestContext context) {
		if (!enabled()) {
			context.complete();
			return;
		}
		final BlockPos origin = context.getAbsolutePos(new BlockPos(0, 30, 0));
		final List<WorldOp> ops = new ArrayList<>(RUNWAY_LENGTH * RUNWAY_WIDTH);
		for (int x = 0; x < RUNWAY_LENGTH; x++) {
			for (int z = 0; z < RUNWAY_WIDTH; z++) {
				ops.add(place(origin.getX() + 64 + x, origin.getY(), origin.getZ() + 2000 + z, "minecraft:gray_concrete"));
			}
		}
		run(context, "runway", ops, (RUNWAY_LENGTH / 16 + 1) * (RUNWAY_WIDTH / 16 + 2));
	}

	/**
	 * A hub's footprint at 1.5:1 (4,500 x 2,300) touched sparsely: one marker block in every chunk. The block count is small;
	 * what it measures is whether the build keeps only its window of an airport's chunks loaded.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 48000, batchId = "airport_footprint")
	public void hubFootprintEveryChunk(TestContext context) {
		if (!enabled()) {
			context.complete();
			return;
		}
		final BlockPos origin = context.getAbsolutePos(new BlockPos(0, 30, 0));
		final int length = 4500;
		final int width = 2300;
		final List<WorldOp> ops = new ArrayList<>();
		for (int x = 0; x < length; x += 16) {
			for (int z = 0; z < width; z += 16) {
				ops.add(place(origin.getX() - 64 - length + x, origin.getY(), origin.getZ() - 4000 - width + z, "minecraft:white_concrete"));
			}
		}
		run(context, "footprint", ops, ops.size());
	}
}
