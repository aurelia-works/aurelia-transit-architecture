package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.AirportRoute.Cell;
import com.aureliatransit.architecture.station.AirportRoute.Gate;
import com.aureliatransit.architecture.station.AirportRoute.Kind;
import com.aureliatransit.architecture.station.AirportRoute.Leg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The airplane route of an airport: per gate, a loop of siding, taxiway, runway and stand, as pure geometry. */
class AirportRouteTest {

	/** A full-size airport (REAL dimensions): 30 wide, 1500 long, five gates, a terminal. */
	private static final AirportGeometry REAL = AirportGeometry.of(new AirportSpec(1, 1500, 30, 5, true));

	/** A compact airport (narrow runway): its siding cannot hold a plane. */
	private static final AirportGeometry COMPACT = AirportGeometry.of(new AirportSpec(1, 1500, AirportSpec.MIN_RUNWAY_WIDTH, 5, true));

	private static Cell first(Leg leg) {
		return leg.cells().get(0);
	}

	private static Cell last(Leg leg) {
		return leg.cells().get(leg.cells().size() - 1);
	}

	@Test
	void everyGateGetsALoop() {
		final AirportRoute route = AirportRoute.of(REAL);
		assertEquals("", route.problem());
		assertEquals(5, route.gates().size());
		for (final Gate gate : route.gates()) {
			assertEquals(10, gate.legs().size(), "gate " + gate.index());
			assertFalse(gate.platform().isEmpty());
		}
	}

	@Test
	void theLoopIsConnectedExceptTheFlight() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			final List<Leg> legs = gate.legs();
			for (final Leg leg : legs) {
				for (int i = 1; i < leg.cells().size(); i++) {
					assertTrue(leg.cells().get(i - 1).adjacent(leg.cells().get(i)), "gate " + gate.index() + " " + leg.kind() + " at " + i);
				}
			}
			for (int k = 1; k < legs.size(); k++) {
				final Leg before = legs.get(k - 1);
				final Leg after = legs.get(k);
				if (before.kind() == Kind.TAKE_OFF && after.kind() == Kind.LANDING) {
					continue; // the flight: the plane leaves the far end and lands at the threshold, off the grid
				}
				assertTrue(last(before).adjacent(first(after)), "gate " + gate.index() + " joins " + before.kind() + " to " + after.kind());
			}
			assertEquals(first(legs.get(0)), last(legs.get(legs.size() - 1)), "the loop closes");
		}
	}

	@Test
	void theLoopStartsAndEndsAtTheSiding() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			final Leg siding = gate.legs().get(0);
			assertEquals(Kind.SIDING, siding.kind());
			final Leg turn = gate.legs().get(gate.legs().size() - 1);
			assertEquals(Kind.TURN, turn.kind());
			assertEquals(first(siding), last(turn), "the loop closes on the siding's deep end");
			assertEquals(REAL.stopU(), first(siding).u(), "the siding's deep end is by the nose");
			assertEquals(gate.sidingRow(), first(siding).v());
		}
	}

	@Test
	void theRouteNeverCrossesTheTerminal() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			for (final Leg leg : gate.legs()) {
				for (final Cell cell : leg.cells()) {
					assertFalse(REAL.inTerminal(cell.u(), cell.v()), gate.index() + " " + leg.kind() + " " + cell);
					assertTrue(cell.u() > REAL.terminalU1(), gate.index() + " " + leg.kind() + " " + cell + " is on the terminal side");
				}
			}
		}
	}

	@Test
	void theSidingHoldsAPlane() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			final Leg siding = gate.legs().get(0);
			assertTrue(siding.cells().size() >= AirportRoute.PLANE_LENGTH, "siding of gate " + gate.index());
		}
	}

	@Test
	void theRunwayIsEnteredOnlyAtTheNearLinkAndLeftOnlyAtTheFarLink() {
		final AirportRoute route = AirportRoute.of(REAL);
		final int[] nearLink = REAL.links().get(0);
		final int takeOffRow = (nearLink[0] + nearLink[1]) / 2;
		final int[] farLink = REAL.links().get(1);
		final int exitRow = (farLink[0] + farLink[1]) / 2;
		for (final Gate gate : route.gates()) {
			final List<Leg> legs = gate.legs();
			final Leg takeOffLink = legs.get(3);
			final Leg exitLink = legs.get(6);
			assertEquals(Kind.LINK, takeOffLink.kind());
			assertEquals(Kind.LINK, exitLink.kind());
			for (final Cell cell : takeOffLink.cells()) {
				assertEquals(takeOffRow, cell.v(), "take-off link row");
			}
			for (final Cell cell : exitLink.cells()) {
				assertEquals(exitRow, cell.v(), "exit link row");
			}
			final Leg takeOff = legs.get(4);
			final Leg landing = legs.get(5);
			assertEquals(new Cell(REAL.runwayCentre(0), REAL.runwayV1()), last(takeOff), "the take-off roll ends at the far end");
			assertEquals(new Cell(REAL.runwayCentre(0), REAL.runwayV0()), first(landing), "the landing roll starts at the threshold");
		}
	}

	/** Review 10-27: the planes took off and stopped in about 16 blocks of a 1,500-block runway. */
	@Test
	void takeOffAndLandingUseNearlyTheWholeRunway() {
		final int runway = REAL.spec().runwayLength();
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			assertTrue(gate.legs().get(4).cells().size() >= runway * 9 / 10, "take-off roll of gate " + gate.index());
			assertTrue(gate.legs().get(5).cells().size() >= runway * 9 / 10, "landing roll of gate " + gate.index());
		}
	}

	/** The taxiway is flown one way (-v), so no plane meets another head on. */
	@Test
	void theTaxiwayIsFlownOneWay() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			for (final Leg leg : gate.legs()) {
				if (leg.kind() == Kind.TAXI) {
					assertTrue(last(leg).v() < first(leg).v(), "gate " + gate.index() + " taxis -v");
				}
			}
		}
	}

	@Test
	void platformsAreOnTheirRowsBetweenTheNoseAndTheApron() {
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			for (final Cell cell : gate.platform()) {
				assertEquals(gate.row(), cell.v());
				assertTrue(cell.u() >= REAL.stopU() && cell.u() <= REAL.apronU1(), gate.index() + " " + cell);
			}
		}
	}

	@Test
	void sidingsAndPlatformsNeverShareARow() {
		final List<Integer> rows = new ArrayList<>();
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			assertFalse(rows.contains(gate.sidingRow()), "siding row " + gate.sidingRow());
			rows.add(gate.sidingRow());
		}
		for (final Gate gate : AirportRoute.of(REAL).gates()) {
			assertFalse(rows.contains(gate.row()), "platform row " + gate.row() + " is a siding row");
			rows.add(gate.row());
		}
	}

	@Test
	void aCompactAirportHasNoRouteBecauseItsSidingIsTooShort() {
		final AirportRoute route = AirportRoute.of(COMPACT);
		assertTrue(route.gates().isEmpty());
		assertTrue(route.problem().contains("siding"), route.problem());
	}
}
