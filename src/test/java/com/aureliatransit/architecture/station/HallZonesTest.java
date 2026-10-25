package com.aureliatransit.architecture.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aureliatransit.architecture.station.ConcourseFit.Spot;
import com.aureliatransit.architecture.station.HallZones.Kind;
import com.aureliatransit.architecture.station.HallZones.Region;

class HallZonesTest {

	/** A city side hall like Amsterdam's: a lift and a stair box on the platform side, the platform access between them. */
	static HallZones.Hall bigHall() {
		final Set<Spot> blocked = new HashSet<>();
		for (int u = 19; u <= 21; u++) {
			for (int v = 115; v <= 118; v++) {
				blocked.add(new Spot(u, v));
			}
			for (int v = 123; v <= 129; v++) {
				blocked.add(new Spot(u, v));
			}
		}
		final Set<Spot> openings = new HashSet<>();
		for (int v = 119; v <= 122; v++) {
			openings.add(new Spot(22, v));
		}
		return new HallZones.Hall(2, 22, 105, 135, 120, 3, blocked, openings);
	}

	/** A small suburban hall like Teddington's: the lift's wall three cells from the platform wall. */
	static HallZones.Hall smallHall() {
		final Set<Spot> blocked = new HashSet<>();
		for (int v = 95; v <= 109; v++) {
			if (v < 99 || v > 101) {
				blocked.add(new Spot(7, v));
			}
		}
		return new HallZones.Hall(2, 10, 85, 115, 100, 2, blocked, Set.of());
	}

	static HallZones.Layout plan(HallZones.Hall hall, Region region) {
		return HallZones.plan(hall, HallZones.profile(region));
	}

	static long count(HallZones.Layout layout, Kind kind) {
		return layout.units().stream().filter(u -> u.kind() == kind).count();
	}

	@Test
	void theRegionFollowsTheOperatorThenTheLook() {
		assertEquals(Region.DUTCH, HallZones.region(Operator.NS, BuildingStyle.VICTORIAN));
		assertEquals(Region.GERMAN, HallZones.region(Operator.DB, BuildingStyle.MODERN));
		assertEquals(Region.JAPANESE, HallZones.region(Operator.JR, BuildingStyle.NONE));
		assertEquals(Region.BRITISH, HallZones.region(Operator.NATIONAL_RAIL, BuildingStyle.AMSTERDAM));
		assertEquals(Region.DUTCH, HallZones.region(Operator.NONE, BuildingStyle.AMSTERDAM));
		assertEquals(Region.GERMAN, HallZones.region(Operator.NONE, BuildingStyle.CONCRETE));
		assertEquals(Region.JAPANESE, HallZones.region(Operator.NONE, BuildingStyle.TOKYO));
		assertEquals(Region.BRITISH, HallZones.region(Operator.NONE, BuildingStyle.VICTORIAN));
	}

	@Test
	void theGateLineStandsWhereTheRegionPutsIt() {
		final HallZones.Hall hall = bigHall();
		final int dutch = plan(hall, Region.DUTCH).gateU();
		final int german = plan(hall, Region.GERMAN).gateU();
		final int british = plan(hall, Region.BRITISH).gateU();
		final int japanese = plan(hall, Region.JAPANESE).gateU();
		// Dutch and German: just before the stairs and lifts, a walk in front of them
		assertEquals(17, dutch);
		assertEquals(17, german);
		// British a third of the way in, Japanese halfway
		assertTrue(british < japanese && japanese < dutch, british + " " + japanese + " " + dutch);
		for (final Region region : Region.values()) {
			assertTrue(plan(smallHall(), region).gateU() >= smallHall().uS() + 4, region.toString());
		}
	}

	@Test
	void unitsStayInsideTheirSideClearOfTheWalksAndEachOther() {
		for (final HallZones.Hall hall : List.of(bigHall(), smallHall())) {
			for (final Region region : Region.values()) {
				final HallZones.Layout layout = plan(hall, region);
				final Set<Spot> keep = HallZones.keptClear(hall, layout.gateU());
				final Set<Spot> seen = new HashSet<>();
				for (final HallZones.Unit unit : layout.units()) {
					for (int u = unit.uMin(); u <= unit.uMax(); u++) {
						for (int v = unit.vMin(); v <= unit.vMax(); v++) {
							final Spot c = new Spot(u, v);
							assertTrue(hall.inside(u, v), region + " " + unit);
							assertFalse(keep.contains(c), region + " in a walk: " + unit);
							assertTrue(seen.add(c), region + " overlaps: " + unit);
							assertTrue(unit.paid() ? u > layout.gateU() + 1 : u < layout.gateU() - 1, region + " wrong side: " + unit);
						}
					}
				}
			}
		}
	}

	@Test
	void everyUnitCanBeReachedFromTheStreetDoorOrTheGates() {
		for (final HallZones.Hall hall : List.of(bigHall(), smallHall())) {
			for (final Region region : Region.values()) {
				final HallZones.Layout layout = plan(hall, region);
				final Set<Spot> solid = new HashSet<>(hall.blocked());
				for (final HallZones.Unit unit : layout.units()) {
					for (int u = unit.uMin(); u <= unit.uMax(); u++) {
						for (int v = unit.vMin(); v <= unit.vMax(); v++) {
							solid.add(new Spot(u, v));
						}
					}
				}
				// the gate line is passable only through its bank
				for (int v = hall.vS() + 1; v < hall.vE(); v++) {
					if (Math.abs(v - hall.vEntr()) > hall.half()) {
						solid.add(new Spot(layout.gateU(), v));
					}
				}
				final Set<Spot> reached = walk(hall, new Spot(hall.uS() + 1, hall.vEntr()), solid);
				for (final HallZones.Unit unit : layout.units()) {
					// in front of the middle of the unit, where a shop has its doorway
					final Spot front = new Spot(
							unit.front() == LocalDir.PLUS_U ? unit.uMax() + 1 : unit.front() == LocalDir.MINUS_U ? unit.uMin() - 1 : (unit.uMin() + unit.uMax()) / 2,
							unit.front() == LocalDir.PLUS_V ? unit.vMax() + 1 : unit.front() == LocalDir.MINUS_V ? unit.vMin() - 1 : (unit.vMin() + unit.vMax()) / 2);
					assertTrue(reached.contains(front) || unit.kind() == Kind.PILLAR, region + " cut off: " + unit);
				}
				assertTrue(reached.contains(new Spot(layout.gateU() + 1, hall.vEntr())), region + " no way through the gates");
			}
		}
	}

	private static Set<Spot> walk(HallZones.Hall hall, Spot start, Set<Spot> solid) {
		final Set<Spot> reached = new HashSet<>(List.of(start));
		final ArrayDeque<Spot> queue = new ArrayDeque<>(List.of(start));
		while (!queue.isEmpty()) {
			final Spot at = queue.poll();
			for (final LocalDir dir : LocalDir.values()) {
				final Spot next = at.step(dir);
				if (hall.inside(next.u(), next.v()) && !solid.contains(next) && reached.add(next)) {
					queue.add(next);
				}
			}
		}
		return reached;
	}

	@Test
	void eachRegionFurnishesItsHallItsOwnWay() {
		final HallZones.Hall hall = bigHall();
		for (final Region region : Region.values()) {
			final HallZones.Layout layout = plan(hall, region);
			assertTrue(count(layout, Kind.MACHINE) >= 2, region + " machines");
			assertEquals(1, count(layout, Kind.OFFICE), region + " office");
			assertEquals(1, count(layout, Kind.INFO_KIOSK), region + " info");
		}
		// shops in the Dutch and German unpaid hall's corners and an info pillar in it
		for (final Region region : List.of(Region.DUTCH, Region.GERMAN)) {
			final HallZones.Layout layout = plan(hall, region);
			assertTrue(count(layout, Kind.SHOP) >= 2, region + " shops");
			assertEquals(1, count(layout, Kind.PILLAR), region + " pillar");
		}
		assertEquals(1, count(plan(hall, Region.DUTCH), Kind.COUNTER));
		// a Japanese hall: a long bank of machines, shops inside the paid side
		final HallZones.Layout japan = plan(hall, Region.JAPANESE);
		assertEquals(6, count(japan, Kind.MACHINE));
		assertTrue(japan.units().stream().anyMatch(u -> u.kind() == Kind.SHOP && u.paid()));
		assertTrue(plan(hall, Region.BRITISH).units().stream().noneMatch(HallZones.Unit::paid));
		// the machines stand by the door
		for (final HallZones.Unit unit : plan(hall, Region.BRITISH).units()) {
			if (unit.kind() == Kind.MACHINE) {
				assertTrue(Math.abs(unit.vMin() - hall.vEntr()) <= hall.half() + 5, unit.toString());
			}
		}
	}

	@Test
	void aSmallHallStillSellsTickets() {
		for (final Region region : Region.values()) {
			final HallZones.Layout layout = plan(smallHall(), region);
			assertEquals(1, count(layout, Kind.OFFICE), region + " office");
			assertTrue(count(layout, Kind.MACHINE) >= 2, region + " machines");
		}
	}
}
