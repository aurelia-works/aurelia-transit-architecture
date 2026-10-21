package com.aureliatransit.architecture.station;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills an upper concourse with what a real one has (Jah, 10-21: "go up these stairs and then, what else?"): departure
 * boards, shop units and kiosks against its walls, islands of seats in the open, with the walks between every way in and
 * out kept clear.
 * <p>
 * Works on the finished floor alone: the cells a passenger can stand on, its doors (stair heads, lift landings, gates, the
 * way on to the street) and the walls round it. The shortest walk between every two doors is kept clear, a lane three wide,
 * and an apron round each door; units go only where nothing is kept clear, and a unit that would cut a door off from the
 * rest is dropped. Pure logic: the plan builds what it returns.
 */
final class ConcourseFit {

	/** Cells kept clear either side of a walk between two doors: lanes three wide. */
	static final int LANE = 1;

	/** Cells kept clear round a door. */
	static final int APRON = 2;

	/** A shop unit: this deep from its back wall, this long along it. */
	static final int SHOP_DEPTH = 4;
	static final int SHOP_LENGTH = 7;

	/** A kiosk: a counter under glass, this deep and long. */
	static final int KIOSK_DEPTH = 3;
	static final int KIOSK_LENGTH = 4;

	/** A departure board on a plinth against a wall: one deep, this long; one more for each this many floor cells. */
	static final int BOARD_LENGTH = 4;
	static final int CELLS_PER_BOARD = 4000;

	/** Boards this far apart at least, one to a stretch of wall. */
	static final int BOARD_SPACING = 30;

	/** On the first pass along the walls, shops this far apart at least, so they spread before they fill. */
	static final int SHOP_SPREAD = 20;

	/** Cells between two units along a wall. */
	static final int UNIT_GAP = 2;

	/** An island of seats: two rows back to back, this long (benches and a bin at the end). */
	static final int SEATS_LENGTH = 4;

	/** Floor cells for each unit along the walls, and for each island of seats. */
	static final int CELLS_PER_UNIT = 350;
	static final int CELLS_PER_ISLAND = 140;

	/** No more than this many units or islands, however big the floor. */
	static final int MAX_UNITS = 24;
	static final int MAX_ISLANDS = 36;

	/** A floor smaller than this (a plain footbridge) gets nothing. */
	static final int MIN_FLOOR = 600;

	enum Kind { BOARD, SHOP, KIOSK, SEATS }

	record Spot(int u, int v) {
		Spot step(LocalDir dir) {
			return new Spot(u + du(dir), v + dv(dir));
		}
	}

	/**
	 * A unit's cells, from {@code (uMin, vMin)} to {@code (uMax, vMax)}. A shop or kiosk opens towards {@code front}, its back on
	 * the wall; an island's first row (the lower v, or lower u when it runs along v) faces {@code front}, the other row away.
	 */
	record Unit(Kind kind, int uMin, int vMin, int uMax, int vMax, LocalDir front) {
		boolean contains(int u, int v) {
			return u >= uMin && u <= uMax && v >= vMin && v <= vMax;
		}

		int length() {
			return front == LocalDir.PLUS_U || front == LocalDir.MINUS_U ? vMax - vMin + 1 : uMax - uMin + 1;
		}
	}

	private ConcourseFit() {
	}

	/**
	 * The units for a floor.
	 *
	 * @param floor cells a passenger can stand on
	 * @param doors cells of {@code floor} where people come on or leave it
	 * @param walls cells beside the floor a unit can stand against (walls and glass, not the barriers round openings)
	 * @param keep  cells of {@code floor} no unit may stand on (a stair's headroom, under a hanging sign)
	 */
	static List<Unit> fit(Set<Spot> floor, Set<Spot> doors, Set<Spot> walls, Set<Spot> keep) {
		final List<Unit> units = new ArrayList<>();
		if (floor.size() < MIN_FLOOR || doors.isEmpty()) {
			return units;
		}
		final Set<Spot> clear = keptClear(floor, doors);
		clear.addAll(keep);
		final Set<Spot> taken = new HashSet<>();
		alongWalls(floor, walls, clear, taken, units);
		islands(floor, clear, taken, units);
		// drop the latest unit until every door still reaches every other
		while (!units.isEmpty() && !connected(floor, doors, units)) {
			units.remove(units.size() - 1);
		}
		return units;
	}

	/** The aprons round the doors and the lanes between them. */
	static Set<Spot> keptClear(Set<Spot> floor, Set<Spot> doors) {
		final Set<Spot> path = new HashSet<>();
		final List<Spot> heads = groupHeads(doors);
		for (int i = 0; i < heads.size(); i++) {
			final Map<Spot, Spot> parent = bfs(floor, heads.get(i), Set.of());
			for (int j = i + 1; j < heads.size(); j++) {
				for (Spot at = heads.get(j); at != null && parent.containsKey(at); at = parent.get(at)) {
					path.add(at);
				}
			}
		}
		final Set<Spot> clear = new HashSet<>();
		grow(path, LANE, floor, clear);
		grow(doors, APRON, floor, clear);
		return clear;
	}

	/** One door cell from each group of touching door cells (a stair head, a gate line), in a fixed order. */
	static List<Spot> groupHeads(Set<Spot> doors) {
		final List<Spot> sorted = new ArrayList<>(doors);
		sorted.sort(Comparator.comparingInt(Spot::u).thenComparingInt(Spot::v));
		final Set<Spot> seen = new HashSet<>();
		final List<Spot> heads = new ArrayList<>();
		for (final Spot start : sorted) {
			if (!seen.add(start)) {
				continue;
			}
			heads.add(start);
			final ArrayDeque<Spot> queue = new ArrayDeque<>(List.of(start));
			while (!queue.isEmpty()) {
				final Spot at = queue.poll();
				for (int du = -1; du <= 1; du++) {
					for (int dv = -1; dv <= 1; dv++) {
						final Spot next = new Spot(at.u() + du, at.v() + dv);
						if (doors.contains(next) && seen.add(next)) {
							queue.add(next);
						}
					}
				}
			}
		}
		return heads;
	}

	private static void grow(Set<Spot> from, int by, Set<Spot> floor, Set<Spot> into) {
		for (final Spot s : from) {
			for (int du = -by; du <= by; du++) {
				for (int dv = -by; dv <= by; dv++) {
					final Spot c = new Spot(s.u() + du, s.v() + dv);
					if (floor.contains(c)) {
						into.add(c);
					}
				}
			}
		}
	}

	/** Breadth-first from {@code start} over the floor, round {@code blocked}: each reached cell's previous one (start's is null). */
	private static Map<Spot, Spot> bfs(Set<Spot> floor, Spot start, Set<Spot> blocked) {
		final Map<Spot, Spot> parent = new HashMap<>();
		parent.put(start, null);
		final ArrayDeque<Spot> queue = new ArrayDeque<>(List.of(start));
		while (!queue.isEmpty()) {
			final Spot at = queue.poll();
			for (final LocalDir dir : LocalDir.values()) {
				final Spot next = at.step(dir);
				if (floor.contains(next) && !blocked.contains(next) && !parent.containsKey(next)) {
					parent.put(next, at);
					queue.add(next);
				}
			}
		}
		return parent;
	}

	/**
	 * Boards first, then shops and kiosks (two shops to a kiosk), with their backs on the walls and a gap between neighbours;
	 * corner by corner across the floor, so they line both sides of a hall as they go.
	 */
	private static void alongWalls(Set<Spot> floor, Set<Spot> walls, Set<Spot> clear, Set<Spot> taken, List<Unit> units) {
		final int boards = Math.max(1, floor.size() / CELLS_PER_BOARD);
		final int shops = Math.min(MAX_UNITS, floor.size() / CELLS_PER_UNIT);
		final List<Spot> sorted = new ArrayList<>(floor);
		sorted.sort(Comparator.comparingInt(Spot::u).thenComparingInt(Spot::v));
		int boarded = 0;
		int placed = 0;
		// boards; shops spread out; shops in the gaps
		for (int pass = 0; pass < 3; pass++) {
			final boolean board = pass == 0;
			final int spread = pass == 0 ? BOARD_SPACING : pass == 1 ? SHOP_SPREAD : 0;
			for (final Spot corner : sorted) {
				for (final LocalDir back : LocalDir.values()) {
					if (board ? boarded >= boards : placed >= shops) {
						break;
					}
					final Kind kind = board ? Kind.BOARD : placed % 3 == 2 ? Kind.KIOSK : Kind.SHOP;
					final Unit unit = againstWall(corner, back, kind, floor, walls, clear, taken);
					if (unit != null && !near(unit, units, board, spread)) {
						units.add(unit);
						mark(unit, UNIT_GAP, taken);
						if (board) {
							boarded++;
						} else {
							placed++;
						}
					}
				}
			}
		}
	}

	/** True when a unit of the same sort (a board, or a shop or kiosk) is closer than {@code spread}. */
	private static boolean near(Unit unit, List<Unit> units, boolean board, int spread) {
		return units.stream().anyMatch(o -> (o.kind() == Kind.BOARD) == board
				&& Math.abs(o.uMin() - unit.uMin()) + Math.abs(o.vMin() - unit.vMin()) < spread);
	}

	/**
	 * A unit with one corner at {@code corner}, its back on the wall towards {@code back} and running along the wall towards
	 * +U or +V; null unless all of it is free, its whole back is wall and the row in front of it is floor.
	 */
	static Unit againstWall(Spot corner, LocalDir back, Kind kind, Set<Spot> floor, Set<Spot> walls, Set<Spot> clear, Set<Spot> taken) {
		final int depth = switch (kind) {
			case BOARD -> 1;
			case KIOSK -> KIOSK_DEPTH;
			default -> SHOP_DEPTH;
		};
		final int length = switch (kind) {
			case BOARD -> BOARD_LENGTH;
			case KIOSK -> KIOSK_LENGTH;
			default -> SHOP_LENGTH;
		};
		final LocalDir along = back == LocalDir.PLUS_U || back == LocalDir.MINUS_U ? LocalDir.PLUS_V : LocalDir.PLUS_U;
		final LocalDir in = back.opposite();
		int uMin = Integer.MAX_VALUE;
		int vMin = Integer.MAX_VALUE;
		int uMax = Integer.MIN_VALUE;
		int vMax = Integer.MIN_VALUE;
		for (int i = 0; i < length; i++) {
			final Spot base = new Spot(corner.u() + du(along) * i, corner.v() + dv(along) * i);
			if (!walls.contains(base.step(back))) {
				return null;
			}
			for (int d = 0; d < depth; d++) {
				final Spot c = new Spot(base.u() + du(in) * d, base.v() + dv(in) * d);
				if (!floor.contains(c) || clear.contains(c) || taken.contains(c)) {
					return null;
				}
				uMin = Math.min(uMin, c.u());
				vMin = Math.min(vMin, c.v());
				uMax = Math.max(uMax, c.u());
				vMax = Math.max(vMax, c.v());
			}
			final Spot front = new Spot(base.u() + du(in) * depth, base.v() + dv(in) * depth);
			if (!floor.contains(front) || taken.contains(front)) {
				return null;
			}
		}
		return new Unit(kind, uMin, vMin, uMax, vMax, in);
	}

	/** Islands of seats along u in the open floor, each with a free cell all round, spread over the floor. */
	private static void islands(Set<Spot> floor, Set<Spot> clear, Set<Spot> taken, List<Unit> units) {
		final int most = Math.min(MAX_ISLANDS, floor.size() / CELLS_PER_ISLAND);
		final List<Spot> sorted = new ArrayList<>(floor);
		// every third row and fourth column first, so the islands spread before they fill
		sorted.sort(Comparator.comparingInt((Spot s) -> Math.floorMod(s.v(), 7) == 0 && Math.floorMod(s.u(), 9) == 0 ? 0 : 1)
				.thenComparingInt(Spot::v).thenComparingInt(Spot::u));
		int placed = 0;
		for (final Spot corner : sorted) {
			if (placed >= most) {
				return;
			}
			final Unit island = new Unit(Kind.SEATS, corner.u(), corner.v(), corner.u() + SEATS_LENGTH - 1, corner.v() + 1, LocalDir.MINUS_V);
			if (free(island, 1, floor, clear, taken)) {
				units.add(island);
				mark(island, 3, taken);
				placed++;
			}
		}
	}

	/** True when the unit and a ring {@code margin} wide round it are floor, kept clear of nothing, and not taken. */
	private static boolean free(Unit unit, int margin, Set<Spot> floor, Set<Spot> clear, Set<Spot> taken) {
		for (int u = unit.uMin() - margin; u <= unit.uMax() + margin; u++) {
			for (int v = unit.vMin() - margin; v <= unit.vMax() + margin; v++) {
				final Spot c = new Spot(u, v);
				if (!floor.contains(c) || clear.contains(c) || taken.contains(c)) {
					return false;
				}
			}
		}
		return true;
	}

	private static void mark(Unit unit, int margin, Set<Spot> taken) {
		for (int u = unit.uMin() - margin; u <= unit.uMax() + margin; u++) {
			for (int v = unit.vMin() - margin; v <= unit.vMax() + margin; v++) {
				taken.add(new Spot(u, v));
			}
		}
	}

	/**
	 * True when, round the units, each door still reaches every door it reaches on the bare floor (gates split a concourse
	 * into its paid and unpaid parts, each its own floor).
	 */
	static boolean connected(Set<Spot> floor, Set<Spot> doors, List<Unit> units) {
		final Set<Spot> blocked = new HashSet<>();
		for (final Unit unit : units) {
			mark(unit, 0, blocked);
		}
		final Set<Spot> done = new HashSet<>();
		for (final Spot head : groupHeads(doors)) {
			if (done.contains(head)) {
				continue;
			}
			final Map<Spot, Spot> bare = bfs(floor, head, Set.of());
			final Map<Spot, Spot> round = bfs(floor, head, blocked);
			for (final Spot door : doors) {
				if (bare.containsKey(door)) {
					done.add(door);
					if (!blocked.contains(door) && !round.containsKey(door)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	static int du(LocalDir dir) {
		return dir == LocalDir.PLUS_U ? 1 : dir == LocalDir.MINUS_U ? -1 : 0;
	}

	static int dv(LocalDir dir) {
		return dir == LocalDir.PLUS_V ? 1 : dir == LocalDir.MINUS_V ? -1 : 0;
	}
}
