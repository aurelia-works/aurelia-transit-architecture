package com.aureliatransit.architecture.gametest;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.world.StationBuildJob;
import com.aureliatransit.architecture.station.world.StationCommand;
import com.aureliatransit.architecture.station.world.StationSessions;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.operation.DepotOperationByIds;
import org.mtr.core.simulation.Simulator;

/**
 * An airport built through the normal station build path (StationCommand, as the Station Wrench does) gets its gate platforms,
 * route, depot and siding in MTR, and a plane leaves a gate. The simulator is read through reflection, as the spike did, and
 * the depot is deployed at once (instantDeploy), so the test does not wait for the frequency. Ticks are held to 50 ms because
 * MTR moves vehicles by wall-clock time.
 */
public class AirportMtrGameTests implements FabricGameTest {

	private static final String WORDS = "airport with 1 runway, 5 gates and a 1500 m runway, 30 wide";
	/** Far from the other tests: MTR areas are columns, and an airport station would cover their sites. */
	private static final BlockPos FEET = new BlockPos(6000, 3, 6000);
	/** Two minutes at 50 ms a tick, the wait for a plane to leave once the depot is deployed. */
	private static final int DEPARTURE_TICKS = 2400;
	/** Ten seconds at 50 ms, for MTR to drop the airport after undo. */
	private static final int UNDO_TICKS = 200;
	/** Ten minutes at 50 ms, for the build and the depot to generate. */
	private static final int SETUP_TICKS = 12000;

	private static void say(String line) {
		System.out.println("AIRPORT-MTR: " + line);
	}

	/** The simulator of this world: MTR's core runs in this JVM ({@code Init.main}, private). */
	@SuppressWarnings("unchecked")
	private static Simulator simulator(ServerWorld world) {
		try {
			final Field mainField = org.mtr.mod.Init.class.getDeclaredField("main");
			mainField.setAccessible(true);
			final Object main = mainField.get(null);
			final Field simulators = org.mtr.core.Main.class.getDeclaredField("simulators");
			simulators.setAccessible(true);
			final String id = org.mtr.mod.Init.getWorldId(new org.mtr.mapping.holder.World(world));
			for (final Simulator simulator : (List<Simulator>) simulators.get(main)) {
				if (simulator.dimension.equals(id)) {
					return simulator;
				}
			}
			throw new GameTestException("no MTR simulator for " + id);
		} catch (ReflectiveOperationException e) {
			throw new GameTestException("MTR internals changed: " + e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Collection<Vehicle> vehicles(Siding siding) {
		try {
			final Field field = Siding.class.getDeclaredField("vehicles");
			field.setAccessible(true);
			return (Collection<Vehicle>) field.get(siding);
		} catch (ReflectiveOperationException e) {
			return List.of();
		}
	}

	/** True while MTR still holds the airport: a gate platform or the depot. */
	private static boolean airportInMtr(Simulator simulator) {
		for (final Platform platform : simulator.platforms) {
			if (platform.getName().startsWith("Gate ")) {
				return true;
			}
		}
		for (final Depot candidate : simulator.depotIdMap.values()) {
			if ("Airport".equals(candidate.getName())) {
				return true;
			}
		}
		return false;
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60000, batchId = "airport_mtr")
	public void planeLeavesAGate(TestContext context) {
		final ServerWorld world = context.getWorld();
		final UUID owner = new UUID(23, 1);
		final List<String> said = new ArrayList<>();
		final StationSessions.Pending pending = StationCommand.prepare(world, WORDS, context.getAbsolutePos(FEET), Heading.SOUTH);
		final StationBuildJob job = StationCommand.startBuild(world, owner, pending, true, text -> {
			said.add(text.getString());
			say("build says: " + text.getString());
		});
		if (job == null) {
			throw new GameTestException("the airport did not start: " + said);
		}
		final int[] ticks = {0};
		final long[] lastTick = {System.currentTimeMillis()};
		final int[] built = {-1};
		final long[] deployedAt = {-1};
		final long[] undoneAt = {-1};
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
			if (job.state() != StationBuildJob.State.DONE) {
				if (ticks[0] > SETUP_TICKS) {
					throw new GameTestException("the airport build did not finish: " + job.state() + " " + job.done() + "/" + job.total());
				}
				return;
			}
			if (built[0] < 0) {
				built[0] = ticks[0];
				say("built after " + ticks[0] + " ticks, " + job.total() + " changes");
			}
			if (sim[0] == null) {
				sim[0] = simulator(world);
			}
			if (ticks[0] % 20 != 0) {
				return;
			}
			if (undoneAt[0] >= 0) {
				if (!airportInMtr(sim[0])) {
					say("undo removed the airport's route, depot and gate platforms from MTR");
					context.complete();
					return;
				}
				if (ticks[0] - undoneAt[0] > UNDO_TICKS) {
					throw new GameTestException("undo left the airport in MTR " + UNDO_TICKS + " ticks later");
				}
				return;
			}
			final Simulator simulator = sim[0];
			int gates = 0;
			for (final Platform platform : simulator.platforms) {
				if (platform.getName().startsWith("Gate ")) {
					gates++;
				}
			}
			Depot depot = null;
			for (final Depot candidate : simulator.depotIdMap.values()) {
				if ("Airport".equals(candidate.getName())) {
					depot = candidate;
				}
			}
			if (depot == null) {
				if (ticks[0] - built[0] > SETUP_TICKS) {
					throw new GameTestException("no airport depot in MTR " + SETUP_TICKS + " ticks after the build; gate platforms " + gates);
				}
				return;
			}
			final Depot.GeneratedStatus status = depot.getLastGeneratedStatus();
			if (deployedAt[0] < 0) {
				if (status == Depot.GeneratedStatus.NONE) {
					return;
				}
				if (status != Depot.GeneratedStatus.SUCCESSFUL) {
					throw new GameTestException("depot path " + status + " (gate platforms " + gates + ")");
				}
				// the test's shortcut: deploy now instead of waiting for the frequency
				final DepotOperationByIds deploy = new DepotOperationByIds();
				deploy.addDepotId(depot.getId());
				deploy.instantDeploy(simulator);
				deployedAt[0] = ticks[0];
				say("depot generated (" + gates + " gate platforms), deployed at tick " + ticks[0]);
				return;
			}
			for (final Siding siding : simulator.sidings) {
				for (final Vehicle vehicle : vehicles(siding)) {
					if (vehicle.getIsOnRoute()) {
						say("plane on its route " + (ticks[0] - deployedAt[0]) + " ticks after deploy");
						// undo takes the airport's route, depot and gate platforms out of MTR; the next checks see whether it did
						final StationBuildJob undo = StationBuildJob.undo(world, owner, job.undoLog(), text -> say("undo says: " + text.getString()));
						while (!undo.finished()) {
							undo.step(StationBuildJob.OPS_PER_TICK);
						}
						undoneAt[0] = ticks[0];
						return;
					}
				}
			}
			if (ticks[0] - deployedAt[0] > DEPARTURE_TICKS) {
				throw new GameTestException("no plane left a gate " + DEPARTURE_TICKS + " ticks after deploy; depot " + status);
			}
		});
	}
}
