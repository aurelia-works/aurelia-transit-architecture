package com.aureliatransit.architecture.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aureliatransit.architecture.station.AirportRails.AirRail;
import com.aureliatransit.architecture.station.AirportRails.Dir;
import com.aureliatransit.architecture.station.AirportRails.Pos;
import com.aureliatransit.architecture.station.AirportRoute.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class AirportRailsTest {

	private static final LocalFrame FRAME = new LocalFrame(100, 64, 200, Heading.SOUTH);

	private static List<AirRail> rails() {
		return AirportRails.of(AirportRoute.of(AirportGeometry.of(new AirportSpec(1, 1500, 30, 5, true))), FRAME);
	}

	@Test
	void everyGateHasAPlatformRail() {
		final List<AirRail> rails = rails();
		for (int gate = 0; gate < 5; gate++) {
			final int g = gate;
			assertTrue(rails.stream().anyMatch(r -> r.gate() == g && r.kind() == Kind.GATE), "gate " + g + " has no platform rail");
		}
	}

	@Test
	void curvesPassThroughAOneBlockCornerBetweenTheirEnds() {
		int curves = 0;
		for (final AirRail rail : rails()) {
			assertNotEquals(rail.from(), rail.to());
			if (rail.via() == null) {
				// a straight rail leaves each end back along itself
				assertEquals(rail.startDir(), opposite(rail.endDir()), "straight rail " + rail);
				continue;
			}
			curves++;
			assertOneStep(rail.from(), rail.via());
			assertOneStep(rail.via(), rail.to());
		}
		assertTrue(curves > 0, "the loops have corners");
	}

	/** Every two rails that meet inside a loop pass straight through the node: one leaves it back along itself, the other away. */
	@Test
	void joinsPassStraightThrough() {
		final List<AirRail> rails = rails();
		for (int i = 0; i + 1 < rails.size(); i++) {
			final AirRail a = rails.get(i);
			final AirRail b = rails.get(i + 1);
			if (a.gate() == b.gate() && a.to().equals(b.from())) {
				assertEquals(opposite(a.endDir()), b.startDir(), "join " + i + " at " + a.to() + " of gate " + a.gate());
			}
		}
	}

	private static Dir opposite(Dir d) {
		return switch (d) {
			case E -> Dir.W;
			case W -> Dir.E;
			case N -> Dir.S;
			case S -> Dir.N;
		};
	}

	private static void assertOneStep(Pos a, Pos b) {
		assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()), "corner step " + a + " " + b);
	}
}
