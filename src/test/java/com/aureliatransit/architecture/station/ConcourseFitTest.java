package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcourseFitTest {

	/** A room {@code width} by {@code depth}, walled all round. */
	private static Set<ConcourseFit.Spot> room(int u0, int width, int depth) {
		final Set<ConcourseFit.Spot> floor = new HashSet<>();
		for (int u = u0; u < u0 + width; u++) {
			for (int v = 0; v < depth; v++) {
				floor.add(new ConcourseFit.Spot(u, v));
			}
		}
		return floor;
	}

	private static Set<ConcourseFit.Spot> wallsRound(Set<ConcourseFit.Spot> floor) {
		final Set<ConcourseFit.Spot> walls = new HashSet<>();
		for (final ConcourseFit.Spot s : floor) {
			for (final LocalDir dir : LocalDir.values()) {
				if (!floor.contains(s.step(dir))) {
					walls.add(s.step(dir));
				}
			}
		}
		return walls;
	}

	@Test
	void aPlainFootbridgeGetsNothing() {
		final Set<ConcourseFit.Spot> floor = room(0, 40, 5);
		assertEquals(List.of(), ConcourseFit.fit(floor, Set.of(new ConcourseFit.Spot(0, 2), new ConcourseFit.Spot(39, 2)), wallsRound(floor), Set.of()));
	}

	@Test
	void aHallGetsBoardsShopsAndSeatsClearOfItsWalks() {
		final Set<ConcourseFit.Spot> floor = room(0, 60, 30);
		final Set<ConcourseFit.Spot> walls = wallsRound(floor);
		// a way in at each end and two stair heads in the middle
		final Set<ConcourseFit.Spot> doors = Set.of(new ConcourseFit.Spot(0, 15), new ConcourseFit.Spot(59, 15), new ConcourseFit.Spot(20, 14),
				new ConcourseFit.Spot(40, 14));
		final Set<ConcourseFit.Spot> keep = Set.of(new ConcourseFit.Spot(30, 3));
		final List<ConcourseFit.Unit> units = ConcourseFit.fit(floor, doors, walls, keep);
		assertTrue(units.stream().anyMatch(u -> u.kind() == ConcourseFit.Kind.BOARD), "a departure board: " + units);
		assertTrue(units.stream().anyMatch(u -> u.kind() == ConcourseFit.Kind.SHOP), "shops: " + units);
		assertTrue(units.stream().anyMatch(u -> u.kind() == ConcourseFit.Kind.SEATS), "seats: " + units);
		final Set<ConcourseFit.Spot> clear = ConcourseFit.keptClear(floor, doors);
		for (final ConcourseFit.Unit unit : units) {
			for (int u = unit.uMin(); u <= unit.uMax(); u++) {
				for (int v = unit.vMin(); v <= unit.vMax(); v++) {
					final ConcourseFit.Spot c = new ConcourseFit.Spot(u, v);
					assertTrue(floor.contains(c), unit + " off the floor");
					assertFalse(clear.contains(c) || keep.contains(c), unit + " on a walk, by a door or on a kept cell at " + c);
				}
			}
			if (unit.kind() != ConcourseFit.Kind.SEATS) {
				// its back on the wall all along
				final LocalDir back = unit.front().opposite();
				for (int u = unit.uMin(); u <= unit.uMax(); u++) {
					for (int v = unit.vMin(); v <= unit.vMax(); v++) {
						final ConcourseFit.Spot c = new ConcourseFit.Spot(u, v);
						if (!unit.contains(u + ConcourseFit.du(back), v + ConcourseFit.dv(back))) {
							assertTrue(walls.contains(c.step(back)), unit + " not against the wall at " + c);
						}
					}
				}
			}
		}
		assertTrue(ConcourseFit.connected(floor, doors, units), "every door still reaches every other");
		for (int i = 0; i < units.size(); i++) {
			for (int j = i + 1; j < units.size(); j++) {
				final ConcourseFit.Unit a = units.get(i);
				final ConcourseFit.Unit b = units.get(j);
				assertTrue(a.uMax() < b.uMin() || b.uMax() < a.uMin() || a.vMax() < b.vMin() || b.vMax() < a.vMin(), a + " overlaps " + b);
			}
		}
	}

	@Test
	void aConcourseSplitByGatesIsFittedOnBothSides() {
		// two halls with no way between them on this floor (a gate line between them is not floor)
		final Set<ConcourseFit.Spot> floor = room(0, 40, 30);
		floor.addAll(room(41, 40, 30));
		final Set<ConcourseFit.Spot> walls = wallsRound(floor);
		final Set<ConcourseFit.Spot> doors = Set.of(new ConcourseFit.Spot(0, 15), new ConcourseFit.Spot(39, 15), new ConcourseFit.Spot(41, 15),
				new ConcourseFit.Spot(80, 15));
		final List<ConcourseFit.Unit> units = ConcourseFit.fit(floor, doors, walls, Set.of());
		assertTrue(units.stream().anyMatch(u -> u.uMax() < 40), "units in the first hall: " + units);
		assertTrue(units.stream().anyMatch(u -> u.uMin() > 40), "units in the second hall: " + units);
	}

	@Test
	void aUnitThatWouldCutADoorOffIsNotKept() {
		final Set<ConcourseFit.Spot> floor = room(0, 40, 30);
		final Set<ConcourseFit.Spot> doors = Set.of(new ConcourseFit.Spot(0, 0), new ConcourseFit.Spot(39, 29));
		// a wall of seats right across the room
		final ConcourseFit.Unit across = new ConcourseFit.Unit(ConcourseFit.Kind.SEATS, 0, 10, 39, 11, LocalDir.MINUS_V);
		assertFalse(ConcourseFit.connected(floor, doors, List.of(across)));
		assertTrue(ConcourseFit.connected(floor, doors, List.of()));
	}
}
