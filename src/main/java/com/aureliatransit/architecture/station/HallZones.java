package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aureliatransit.architecture.station.ConcourseFit.Spot;

/**
 * The side building's ground-floor hall laid out the way real stations lay theirs out (Jah, 10-25: "a better concourse based
 * on real life stuff"): an unpaid hall from the street door to the gate line, a paid side from the gates to the stairs and
 * lifts, and what stands where by region. A Dutch hall is one big unpaid hall (machines by the door, the service desk, shops
 * along the walls) with the gates just before the stairs; a British one has the ticket office and machines by the door and
 * the gate line a third of the way in; a German one has no barrier hall at all, the Reisezentrum, a bakery and an info pillar
 * in the open hall and the validators at the platform access; a Japanese one has its gates in the middle, a long bank of
 * machines beside the ticket office and shops inside the paid side. Pure logic: the plan builds what it returns.
 */
final class HallZones {

	enum Region { DUTCH, BRITISH, GERMAN, JAPANESE }

	enum Kind { MACHINE, INFO_KIOSK, OFFICE, COUNTER, SHOP, PILLAR }

	/** One thing against the street wall, in order away from the door: {@code depth} out from the wall, at least {@code minDepth}. */
	record Item(Kind kind, int length, int depth, int minDepth, String name) {
		static Item machine() {
			return new Item(Kind.MACHINE, 1, 1, 1, "");
		}

		static Item info() {
			return new Item(Kind.INFO_KIOSK, 1, 1, 1, "");
		}
	}

	/**
	 * A region's hall: where the gate line stands ({@code gateShare} percent of the way from the street wall to the platform
	 * wall, as far as the stairs allow), the street wall either side of the door (south first) and the unpaid hall's end
	 * walls from the street corner, shops on the paid side, and an info pillar in the open hall.
	 */
	record Profile(Region region, int gateShare, List<Item> south, List<Item> north, List<Item> southEnd, List<Item> northEnd,
			boolean paidShops, boolean pillar) {
	}

	static final int SHOP_LENGTH = 7;
	static final int SHOP_DEPTH = 4;
	static final int COUNTER_LENGTH = 4;
	static final int COUNTER_DEPTH = 3;

	/** The shortest ticket office: two end walls and a window between. */
	static final int OFFICE_LENGTH = 3;

	/** Cells kept clear round a doorway in the hall's walls. */
	static final int APRON = 2;

	static Profile profile(Region region) {
		final Item m = Item.machine();
		final Item i = Item.info();
		return switch (region) {
			case DUTCH -> new Profile(region, 100, List.of(m, m, i), List.of(m, m, office("Service", 2)),
					List.of(shop(""), counter()), List.of(shop(""), shop("")), false, true);
			case BRITISH -> new Profile(region, 33, List.of(m, m, m, i), List.of(office("Tickets", 2)),
					List.of(shop("")), List.of(shop("")), false, false);
			case GERMAN -> new Profile(region, 100, List.of(m, m, i), List.of(office("Reisezentrum", 3)),
					List.of(shop("Bakery"), shop("")), List.of(shop(""), counter()), false, true);
			case JAPANESE -> new Profile(region, 50, List.of(m, m, m, m, m, m), List.of(i, office("Tickets", 2)),
					List.of(), List.of(), true, false);
		};
	}

	private static Item office(String name, int depth) {
		return new Item(Kind.OFFICE, 5, depth, 2, name);
	}

	private static Item counter() {
		return new Item(Kind.COUNTER, COUNTER_LENGTH, COUNTER_DEPTH, 2, "Kiosk");
	}

	private static Item shop(String name) {
		return new Item(Kind.SHOP, SHOP_LENGTH, SHOP_DEPTH, 3, name);
	}

	/** The region a station's hall follows: its operator's, else its building style's, else British (gated, compact). */
	static Region region(Operator operator, BuildingStyle style) {
		return switch (operator) {
			case NS, GVB -> Region.DUTCH;
			case DB, SBAHN, SBB -> Region.GERMAN;
			case JR -> Region.JAPANESE;
			case NATIONAL_RAIL, UNDERGROUND -> Region.BRITISH;
			case NONE -> switch (style) {
				case MODERN, AMSTERDAM, ANTWERP -> Region.DUTCH;
				case SANDSTONE, CLINKER, CONCRETE, ZURICH, BERLIN -> Region.GERMAN;
				case TOKYO, HONG_KONG, BEIJING -> Region.JAPANESE;
				default -> Region.BRITISH;
			};
		};
	}

	/**
	 * The hall: street wall at {@code uS} with the door {@code half} either side of {@code vEntr}, platform wall at {@code uP},
	 * end walls at {@code vS} and {@code vE}; {@code blocked} the cells inside already built on (stairs, lifts, their walls),
	 * {@code openings} the cells of the walls people walk through (the street door, wings, the platform access).
	 */
	record Hall(int uS, int uP, int vS, int vE, int vEntr, int half, Set<Spot> blocked, Set<Spot> openings) {
		boolean inside(int u, int v) {
			return u > uS && u < uP && v > vS && v < vE;
		}
	}

	/** A unit's cells from {@code (uMin, vMin)} to {@code (uMax, vMax)}, its front towards {@code front}; paid if past the gates. */
	record Unit(Kind kind, int uMin, int vMin, int uMax, int vMax, LocalDir front, String name, boolean paid) {
		boolean contains(int u, int v) {
			return u >= uMin && u <= uMax && v >= vMin && v <= vMax;
		}
	}

	/** Where the gate line stands, and what stands either side of it. */
	record Layout(int gateU, List<Unit> units) {
	}

	private HallZones() {
	}

	static Layout plan(Hall hall, Profile profile) {
		final int gateU = gateLine(hall, profile.gateShare());
		final Set<Spot> keep = keptClear(hall, gateU);
		final Set<Spot> taken = new HashSet<>();
		final List<Unit> units = new ArrayList<>();
		// the street wall either side of the door, away from it, then the end walls from the street corners
		streetWall(hall, gateU, profile.south(), -1, keep, taken, units);
		streetWall(hall, gateU, profile.north(), 1, keep, taken, units);
		officeSomewhere(hall, gateU, profile, keep, taken, units);
		endWall(hall, gateU, profile.southEnd(), -1, keep, taken, units);
		endWall(hall, gateU, profile.northEnd(), 1, keep, taken, units);
		if (profile.paidShops()) {
			platformWall(hall, gateU, keep, taken, units);
		}
		if (profile.pillar()) {
			pillar(hall, gateU, keep, taken, units);
		}
		return new Layout(gateU, units);
	}

	/**
	 * The gate line: {@code share} percent of the way in, never closer than four cells to the street wall, and with nothing
	 * built on at or before the line plus one (the stairs and lifts stay on the paid side, with a walk in front of them).
	 */
	static int gateLine(Hall hall, int share) {
		final int least = hall.uS() + 4;
		int most = hall.uP() - 2;
		for (final Spot b : hall.blocked()) {
			if (hall.inside(b.u(), b.v())) {
				most = Math.min(most, b.u() - 2);
			}
		}
		final int target = hall.uS() + (hall.uP() - hall.uS()) * share / 100;
		return Math.max(least, Math.min(target, most));
	}

	/** The walk from the door to the gates, the aisles either side of the gate line, round each opening and round what is built. */
	static Set<Spot> keptClear(Hall hall, int gateU) {
		final Set<Spot> keep = new HashSet<>();
		for (int u = hall.uS() + 1; u < gateU; u++) {
			for (int v = hall.vEntr() - hall.half() - 1; v <= hall.vEntr() + hall.half() + 1; v++) {
				keep.add(new Spot(u, v));
			}
		}
		for (int v = hall.vS() + 1; v < hall.vE(); v++) {
			for (int u = gateU - 1; u <= gateU + 1; u++) {
				keep.add(new Spot(u, v));
			}
		}
		for (final Spot o : hall.openings()) {
			for (int du = -APRON; du <= APRON; du++) {
				for (int dv = -APRON; dv <= APRON; dv++) {
					keep.add(new Spot(o.u() + du, o.v() + dv));
				}
			}
		}
		for (final Spot b : hall.blocked()) {
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					keep.add(new Spot(b.u() + du, b.v() + dv));
				}
			}
		}
		return keep;
	}

	/**
	 * The items along the street wall on one side of the door ({@code side} -1 towards vS, 1 towards vE), each as deep as it
	 * wants and the unpaid hall allows, a cell between unlike neighbours; an item that does not fit is left out.
	 */
	private static void streetWall(Hall hall, int gateU, List<Item> items, int side, Set<Spot> keep, Set<Spot> taken, List<Unit> units) {
		// the first free cell along the wall, away from the door
		int next = side < 0 ? hall.vEntr() - hall.half() - 2 : hall.vEntr() + hall.half() + 2;
		Kind last = null;
		for (final Item item : items) {
			// shops leave a two-wide aisle before the gate line's own; an office's counter may stand on its edge
			final int room = gateU - hall.uS() - (item.kind() == Kind.OFFICE ? 2 : 3);
			final int depth = Math.min(item.depth(), room);
			if (depth < item.minDepth()) {
				continue;
			}
			// a cell between unlike neighbours where there is room, else hard against the last one
			final boolean gap = last != null && (last != Kind.MACHINE || item.kind() != Kind.MACHINE);
			// an office or counter shortens to fit (its two end walls and one window at least)
			final int shortest = item.kind() == Kind.OFFICE || item.kind() == Kind.COUNTER ? OFFICE_LENGTH : item.length();
			placing:
			for (int length = item.length(); length >= shortest; length--) {
				for (final int near : gap ? new int[]{next + side, next} : new int[]{next}) {
					final Unit unit = along(hall, item, depth, length, near, side);
					if (fits(hall, unit, keep, taken)) {
						units.add(unit);
						mark(unit, keep, taken);
						next = near + side * length;
						last = item.kind();
						break placing;
					}
				}
			}
		}
	}

	/** An item against the street wall from {@code near} along it away from the door. */
	private static Unit along(Hall hall, Item item, int depth, int length, int near, int side) {
		final int far = near + side * (length - 1);
		return new Unit(item.kind(), hall.uS() + 1, Math.min(near, far), hall.uS() + depth, Math.max(near, far), LocalDir.PLUS_U,
				item.name(), false);
	}

	/**
	 * Every station sells tickets over a counter: where the profile's office found no room beside the machines, the shortest
	 * office anywhere along the street wall, nearest the door first.
	 */
	private static void officeSomewhere(Hall hall, int gateU, Profile profile, Set<Spot> keep, Set<Spot> taken, List<Unit> units) {
		if (units.stream().anyMatch(u -> u.kind() == Kind.OFFICE)) {
			return;
		}
		final Item office = java.util.stream.Stream.concat(profile.south().stream(), profile.north().stream())
				.filter(i -> i.kind() == Kind.OFFICE).findFirst().orElse(null);
		final int depth = Math.min(office == null ? 2 : office.depth(), gateU - hall.uS() - 2);
		if (office == null || depth < office.minDepth()) {
			return;
		}
		for (int step = hall.half() + 2; step < hall.vE() - hall.vS(); step++) {
			for (final int side : new int[]{1, -1}) {
				final Unit unit = along(hall, office, depth, OFFICE_LENGTH, hall.vEntr() + side * step, side);
				if (fits(hall, unit, keep, taken)) {
					units.add(unit);
					mark(unit, keep, taken);
					return;
				}
			}
		}
	}

	/**
	 * The items along one end wall of the unpaid hall ({@code end} -1 the one at vS), from the street corner towards the gate
	 * line, a cell between neighbours; an item that does not fit moves along, else is left out.
	 */
	private static void endWall(Hall hall, int gateU, List<Item> items, int end, Set<Spot> keep, Set<Spot> taken, List<Unit> units) {
		final int vWall = end < 0 ? hall.vS() + 1 : hall.vE() - 1;
		final LocalDir front = end < 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
		int next = hall.uS() + 1;
		for (final Item item : items) {
			final int vFar = vWall - end * (item.depth() - 1);
			for (int u0 = next; u0 + item.length() - 1 < gateU - 1; u0++) {
				final Unit unit = new Unit(item.kind(), u0, Math.min(vWall, vFar), u0 + item.length() - 1, Math.max(vWall, vFar), front,
						item.name(), false);
				if (fits(hall, unit, keep, taken)) {
					units.add(unit);
					mark(unit, keep, taken);
					next = unit.uMax() + 2;
					break;
				}
			}
		}
	}

	/**
	 * Shops inside the paid side (a Japanese station's ekinaka), backs on the platform wall, one towards each end wall where
	 * the stairs, lifts and platform access leave room.
	 */
	private static void platformWall(Hall hall, int gateU, Set<Spot> keep, Set<Spot> taken, List<Unit> units) {
		final int depth = Math.min(SHOP_DEPTH, hall.uP() - 1 - (gateU + 2));
		if (depth < 3) {
			return;
		}
		for (final int end : new int[]{-1, 1}) {
			final int from = end < 0 ? hall.vS() + 1 : hall.vE() - SHOP_LENGTH;
			for (int i = 0; i < hall.vE() - hall.vS(); i++) {
				final int v0 = from - end * i;
				final Unit unit = new Unit(Kind.SHOP, hall.uP() - depth, v0, hall.uP() - 1, v0 + SHOP_LENGTH - 1, LocalDir.MINUS_U, "", true);
				if (fits(hall, unit, keep, taken)) {
					units.add(unit);
					mark(unit, keep, taken);
					break;
				}
			}
		}
	}

	/** An information pillar in the open unpaid hall beside the walk in, with room all round. */
	private static void pillar(Hall hall, int gateU, Set<Spot> keep, Set<Spot> taken, List<Unit> units) {
		final int u = (hall.uS() + 1 + gateU - 2) / 2;
		for (final int v : new int[]{hall.vEntr() + hall.half() + 4, hall.vEntr() - hall.half() - 4}) {
			final Unit unit = new Unit(Kind.PILLAR, u, v, u, v, LocalDir.MINUS_U, "", false);
			boolean free = true;
			for (int du = -2; du <= 2 && free; du++) {
				for (int dv = -2; dv <= 2 && free; dv++) {
					final Spot c = new Spot(u + du, v + dv);
					free = hall.inside(c.u(), c.v()) && !keep.contains(c) && !taken.contains(c);
				}
			}
			if (free) {
				units.add(unit);
				mark(unit, keep, taken);
				return;
			}
		}
	}

	/** True when every cell of the unit is inside the hall, kept clear of nothing and free, and the row in front is walkable. */
	static boolean fits(Hall hall, Unit unit, Set<Spot> keep, Set<Spot> taken) {
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				final Spot c = new Spot(u, v);
				if (!hall.inside(u, v) || keep.contains(c) || taken.contains(c)) {
					return false;
				}
				final Spot ahead = new Spot(u + ConcourseFit.du(unit.front()), v + ConcourseFit.dv(unit.front()));
				if (!unit.contains(ahead.u(), ahead.v()) && (!hall.inside(ahead.u(), ahead.v()) || hall.blocked().contains(ahead))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Marks the unit's cells taken and the row in front of it kept clear, so nothing later stands in its way. */
	private static void mark(Unit unit, Set<Spot> keep, Set<Spot> taken) {
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				taken.add(new Spot(u, v));
				keep.add(new Spot(u + ConcourseFit.du(unit.front()), v + ConcourseFit.dv(unit.front())));
			}
		}
	}
}
