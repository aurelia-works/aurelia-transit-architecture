package com.aureliatransit.architecture.gametest;

import static com.aureliatransit.architecture.gametest.AirportMtrGameTests.DEPARTURE_TICKS;
import static com.aureliatransit.architecture.gametest.AirportMtrGameTests.SETUP_TICKS;
import static com.aureliatransit.architecture.gametest.AirportMtrGameTests.WORDS;
import static com.aureliatransit.architecture.gametest.AirportMtrGameTests.say;
import static com.aureliatransit.architecture.gametest.AirportMtrGameTests.simulator;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.world.StationBuildJob;
import com.aureliatransit.architecture.station.world.StationCommand;
import com.aureliatransit.architecture.station.world.StationSessions;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.simulation.Simulator;

/**
 * The rebuild half of {@link AirportMtrGameTests}, in a class of its own so CI can run it, the slowest game test (two airport
 * builds in real time), in a job of its own. Same airport words, timings and helpers.
 */
public class AirportMtrRebuildGameTests implements FabricGameTest {

	/** Far from the other airport, in its own batch, so the two never share MTR's areas or depot names at once. */
	private static final BlockPos REBUILD_FEET = new BlockPos(9000, 3, 9000);

	/**
	 * Review 10-27: a rebuild deletes the airport's gate platforms, route and depot from MTR and lays the same rails again, trusting MTR
	 * to report the platforms for rails it already holds. This rebuilds a finished airport in place and waits for a new depot with its
	 * gates and a generated path.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60000, batchId = "airport_mtr_rebuild")
	public void aRebuiltAirportKeepsItsGatesAndDepot(TestContext context) {
		final ServerWorld world = context.getWorld();
		final UUID owner = new UUID(23, 2);
		final BlockPos feet = context.getAbsolutePos(REBUILD_FEET);
		final StationSessions.Pending pending = StationCommand.prepare(world, WORDS, feet, Heading.SOUTH);
		final StationBuildJob job = StationCommand.startBuild(world, owner, pending, true, text -> say("build says: " + text.getString()));
		if (job == null) {
			throw new GameTestException("the airport did not start");
		}
		final int[] ticks = {0};
		final long[] lastTick = {System.currentTimeMillis()};
		final long[] firstDepot = {0};
		final StationBuildJob[] rebuild = {null};
		final int[] rebuiltAt = {-1};
		final Simulator[] sim = {null};
		context.runAtEveryTick(() -> {
			ticks[0]++;
			final long wait = 50 - (System.currentTimeMillis() - lastTick[0]);
			if (wait > 0) {
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			lastTick[0] = System.currentTimeMillis();
			if (ticks[0] > 3 * SETUP_TICKS) {
				throw new GameTestException("timed out: first depot " + firstDepot[0] + ", rebuild " + (rebuild[0] == null ? "not started" : rebuild[0].state()));
			}
			if (job.state() != StationBuildJob.State.DONE || ticks[0] % 20 != 0) {
				return;
			}
			if (sim[0] == null) {
				sim[0] = simulator(world);
			}
			final Simulator simulator = sim[0];
			Depot depot = null;
			for (final Depot candidate : simulator.depotIdMap.values()) {
				if ("Airport".equals(candidate.getName())) {
					depot = candidate;
				}
			}
			int gates = 0;
			for (final Platform platform : simulator.platforms) {
				if (platform.getName().startsWith("Gate ")) {
					gates++;
				}
			}
			if (rebuild[0] == null) {
				if (depot == null || depot.getLastGeneratedStatus() != Depot.GeneratedStatus.SUCCESSFUL) {
					return;
				}
				firstDepot[0] = depot.getId();
				say("first build: depot " + depot.getId() + ", " + gates + " gates; rebuilding in place");
				final StationSessions.Pending again = StationCommand.prepare(world, WORDS, feet, Heading.SOUTH);
				rebuild[0] = StationCommand.startRebuild(world, owner, again, again.frame(), job.undoLog(), true, text -> say("rebuild says: " + text.getString()));
				if (rebuild[0] == null) {
					throw new GameTestException("the rebuild did not start");
				}
				return;
			}
			if (rebuild[0].state() != StationBuildJob.State.DONE) {
				return;
			}
			if (rebuiltAt[0] < 0) {
				rebuiltAt[0] = ticks[0];
			}
			if (depot == null || depot.getId() == firstDepot[0] || depot.getLastGeneratedStatus() == Depot.GeneratedStatus.NONE) {
				if (ticks[0] - rebuiltAt[0] > DEPARTURE_TICKS) {
					throw new GameTestException("no new airport depot " + DEPARTURE_TICKS + " ticks after the rebuild; gate platforms " + gates);
				}
				return;
			}
			if (depot.getLastGeneratedStatus() != Depot.GeneratedStatus.SUCCESSFUL || gates < 2) {
				throw new GameTestException("after the rebuild: depot path " + depot.getLastGeneratedStatus() + ", gate platforms " + gates);
			}
			say("rebuild: new depot " + depot.getId() + ", " + gates + " gates, path generated");
			final StationBuildJob undo = StationBuildJob.undo(world, owner, rebuild[0].undoLog(), text -> say("undo says: " + text.getString()));
			while (!undo.finished()) {
				undo.step(StationBuildJob.OPS_PER_TICK);
			}
			context.complete();
		});
	}
}
