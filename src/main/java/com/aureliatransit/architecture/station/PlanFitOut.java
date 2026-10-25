package com.aureliatransit.architecture.station;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Fitting out (Jah, 10-21: "a better concourse and better entrance"): an upper concourse's boards, shops, kiosks and seats
 * (see {@link ConcourseFit}), and a street entrance that reads as the way in: a portal round the doors, a canopy, the name
 * over it and a pylon outside.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanFitOut extends PlanLandmark {

	PlanFitOut(StationSpec spec) {
		super(spec);
	}

	/** Shop names over the units' fronts, in turn: what each one's back shelves hold, low and high, and its fascia's colour. */
	static final List<String[]> SHOPS = List.of(
			new String[]{"Coffee", "minecraft:smoker", "minecraft:barrel", "minecraft:green_concrete"},
			new String[]{"Bakery", "minecraft:barrel", "minecraft:hay_block", "minecraft:orange_concrete"},
			new String[]{"Books & News", "minecraft:bookshelf", "minecraft:bookshelf", "minecraft:blue_concrete"},
			new String[]{"Flowers", "minecraft:moss_block", "minecraft:potted_flowering_azalea_bush", "minecraft:pink_concrete"});

	/** A kiosk's fascia. */
	static final String KIOSK_FASCIA = "minecraft:yellow_concrete";

	/** A kiosk: its name, what its back shelves hold and its fascia. */
	static final String[] KIOSK_SHOP = {"Kiosk", "minecraft:barrel", "minecraft:barrel", KIOSK_FASCIA};

	static final String SHOP_SIGN = "aurelia_transit_architecture:wall_wayfinding_sign";

	static final String CONCOURSE_BOARD = "aurelia_transit_architecture:concourse_board";

	/**
	 * Fits the upper concourse out (see {@link ConcourseFit}): departure boards, shops and kiosks along its walls and islands
	 * of seats, wherever the walks between its stairs, lifts, gates and ways out leave room. Only big floors (a mezzanine, a
	 * hall over the tracks, a station building's upper floor); a plain footbridge keeps its benches.
	 */
	void concourseFit() {
		if (!spec.furniture() || !site.bridge() || spec.options().underground()) {
			return;
		}
		final int floorY = StationGeometry.UPPER_FLOOR_Y;
		final int y = StationGeometry.UPPER_WALK_Y;
		final Set<ConcourseFit.Spot> floor = new HashSet<>();
		final Set<ConcourseFit.Spot> keep = new HashSet<>();
		for (final PlannedBlock block : cells.values()) {
			if (block.y() == floorY && block.role().flatFloor() && at(block.u(), y, block.v()) == null && at(block.u(), y + 1, block.v()) == null
					&& at(block.u(), y + 2, block.v()) == null) {
				final ConcourseFit.Spot spot = new ConcourseFit.Spot(block.u(), block.v());
				floor.add(spot);
				if (cleared(block.u(), y, block.v()) || taken(block.u(), y + 3, block.v())) {
					keep.add(spot);
				}
			}
		}
		final Set<ConcourseFit.Spot> doors = new HashSet<>();
		final Set<ConcourseFit.Spot> walls = new HashSet<>();
		for (final ConcourseFit.Spot spot : floor) {
			for (final LocalDir dir : LocalDir.values()) {
				final ConcourseFit.Spot next = spot.step(dir);
				if (floor.contains(next)) {
					continue;
				}
				final PlannedBlock walk = at(next.u(), y, next.v());
				final PlannedBlock head = at(next.u(), y + 1, next.v());
				final PlannedBlock deck = at(next.u(), floorY, next.v());
				final PlannedBlock below = at(next.u(), floorY - 1, next.v());
				if (walk != null && (walk.role() == Role.GATE || walk.role() == Role.LIFT || sloped(walk.role()))
						|| walk == null && deck != null && sloped(deck.role())
						|| walk == null && deck == null && below != null && sloped(below.role())) {
					doors.add(spot);
				} else if (walk != null && head != null && wallLike(walk.role()) && wallLike(head.role())) {
					walls.add(next);
				}
			}
		}
		final StylePalette.Building b = palette.building();
		int shop = 0;
		for (final ConcourseFit.Unit unit : ConcourseFit.fit(floor, doors, walls, keep)) {
			switch (unit.kind()) {
				case BOARD -> board(unit, y, b);
				case SEATS -> seats(unit, y, b);
				default -> {
					final boolean kiosk = unit.kind() == ConcourseFit.Kind.KIOSK;
					shopUnit(unit, y, kiosk ? KIOSK_SHOP : SHOPS.get(shop++ % SHOPS.size()), kiosk, b);
				}
			}
		}
	}

	private static boolean sloped(Role role) {
		return role == Role.STAIR || role == Role.ESCALATOR || role == Role.RAMP_LOWER || role == Role.RAMP_UPPER;
	}

	private static boolean wallLike(Role role) {
		return role == Role.WALL || role == Role.WINDOW;
	}

	/** Along a unit from its first cell: 0 to length - 1. */
	private static int along(ConcourseFit.Unit unit, int u, int v) {
		return unit.front() == LocalDir.PLUS_U || unit.front() == LocalDir.MINUS_U ? v - unit.vMin() : u - unit.uMin();
	}

	/** A departure board two high on a plinth against the wall. */
	private void board(ConcourseFit.Unit unit, int y, StylePalette.Building b) {
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				put(Role.WALL, u, y, v, unit.front(), b.plinth());
				put(Role.FIXTURE, u, y + 1, v, unit.front(), CONCOURSE_BOARD);
				put(Role.FIXTURE, u, y + 2, v, unit.front(), CONCOURSE_BOARD);
			}
		}
	}

	/** Benches back to back, a bin and a planter at the end. */
	private void seats(ConcourseFit.Unit unit, int y, StylePalette.Building b) {
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			final boolean end = u == unit.uMax();
			put(end ? Role.BIN : Role.BENCH, u, y, unit.vMin(), LocalDir.MINUS_V, end ? palette.blockId(Role.BIN) : b.bench());
			put(end ? Role.FIXTURE : Role.BENCH, u, y, unit.vMax(), LocalDir.PLUS_V, end ? "minecraft:potted_flowering_azalea_bush" : b.bench());
		}
	}

	/**
	 * A shop (glazed front, a doorway two wide in the middle, shelves along the back, a counter) or, with {@code counter}, a
	 * kiosk or ticket office (a counter under a booth window all along the front); clad end walls, a ceiling with a light, a
	 * fascia in the shop's colour over the front and its name on it. {@code shop} is the name, the back shelves low and high,
	 * and the fascia.
	 */
	@Override
	void shopUnit(ConcourseFit.Unit unit, int y, String[] shop, boolean counter, StylePalette.Building b) {
		final LocalDir front = unit.front();
		final int du = ConcourseFit.du(front);
		final int dv = ConcourseFit.dv(front);
		final int length = unit.length();
		final String fascia = shop[3];
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				final int i = along(unit, u, v);
				final boolean end = i == 0 || i == length - 1;
				final boolean inFront = !unit.contains(u + du, v + dv);
				final boolean atBack = !unit.contains(u - du, v - dv);
				final int top = y + 3;
				if (end) {
					for (int h = y; h <= top; h++) {
						put(Role.WALL, u, h, v, front, h == top && inFront ? fascia : b.trim());
					}
					continue;
				}
				put(Role.WALL, u, top, v, front, inFront ? fascia : i == length / 2 && !atBack ? b.light() : b.interiorWall());
				if (inFront) {
					if (counter) {
						put(Role.WALL, u, y, v, front, b.trim());
						put(Role.FIXTURE, u, y + 1, v, front, "aurelia_transit_architecture:booth_window");
						put(Role.WINDOW, u, y + 2, v, front, b.window());
					} else if (i != length / 2 && i != length / 2 - 1) {
						for (int h = y; h < top; h++) {
							put(Role.WINDOW, u, h, v, front, b.window());
						}
					}
				} else if (atBack) {
					put(Role.FIXTURE, u, y, v, front, shop[1]);
					put(Role.FIXTURE, u, y + 1, v, front, shop[2]);
				} else if (!counter && i == length - 2 && !unit.contains(u + 2 * du, v + 2 * dv)) {
					// the counter, beside the doorway just inside the glass
					put(Role.WALL, u, y, v, front, b.trim());
				}
			}
		}
		// the name on the fascia, over the middle of the front
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				if (along(unit, u, v) == length / 2 && !unit.contains(u + du, v + dv)) {
					putText(Role.FIXTURE, u + du, y + 3, v + dv, front, SHOP_SIGN, "NONE:" + shop[0]);
				}
			}
		}
	}

	/** How far an entrance canopy reaches out from the wall: over the ramps, inside the site. */
	static final int CANOPY_DEPTH = 2;

	/** The canopy's height: over the doors and MTR's name board ({@link StationPlanner#MTR_NAME_Y}) under it. */
	static final int CANOPY_Y = MTR_NAME_Y + 1;

	static final String CANOPY = "aurelia_transit_architecture:canopy_flat";
	static final String CANOPY_EDGE = "aurelia_transit_architecture:canopy_edge";
	static final String CANOPY_LIGHT = "aurelia_transit_architecture:canopy_light";
	/** The station's name on its facade. */
	static final String LETTERING = "aurelia_transit_architecture:station_lettering";

	/**
	 * Every street entrance, the side building's and the terminus head's: a portal of trim round the doorway (posts either
	 * side up to the canopy, a lintel over the doors), a canopy over the ramps with lights in it and an edge along its front,
	 * the station's name in big letters on the wall over the canopy, and an entrance pylon with the name beside the ramps.
	 */
	void entrances() {
		if (spec.options().underground()) {
			return;
		}
		if (site.building() && spec.sideBuilding()) {
			entrance(sideFront(), 0, entranceHalf());
		}
		if (layout.terminus()) {
			final Front front = headFront();
			entrance(front, front.cu() - headDoor(), headEntranceHalf());
		}
	}

	/** One entrance in {@code f}'s frame (z = 0 the street wall, out at z < 0), its doorway {@code half} either side of {@code x0}. */
	void entrance(Front f, int x0, int half) {
		final StylePalette.Building b = palette.building();
		final LocalDir out = f.dir(PlanLandmark.Dir.OUT);
		final int door = StationGeometry.DOOR_HEIGHT;
		// the portal: posts either side of the doorway, a lintel over it, on the wall
		for (int x = x0 - half - 1; x <= x0 + half + 1; x++) {
			final boolean post = Math.abs(x - x0) == half + 1;
			for (int y = post ? 1 : door + 1; y <= CANOPY_Y; y++) {
				face(f.u(x, 0), y, f.v(x, 0), out, b.trim());
			}
		}
		// the canopy over the ramps, its lights down the middle and an edge along the front
		for (int z = -1; z >= -CANOPY_DEPTH; z--) {
			for (int x = x0 - half - 1; x <= x0 + half + 1; x++) {
				final String block = z == -CANOPY_DEPTH ? CANOPY_EDGE : Math.floorMod(x - x0, 2) == 0 ? CANOPY_LIGHT : CANOPY;
				put(Role.BUILDING_ROOF, f.u(x, z), CANOPY_Y, f.v(x, z), out, block);
			}
		}
		// the name, over the canopy
		if (!spec.name().isEmpty()) {
			for (int x = x0 - half; x <= x0 + half; x++) {
				if (taken(f.u(x, 0), CANOPY_Y + 1, f.v(x, 0))) {
					// lettering fixed flat to the wall, not the operator's platform name board on its legs (Jah, 10-22: that
					// belongs inside); a fixture, so the platform sign rules (one row, on a floor) are not about it
					putText(Role.FIXTURE, f.u(x, -1), CANOPY_Y + 1, f.v(x, -1), out, LETTERING, spec.name());
				}
			}
			// the pylon right beside the canopy's end, its faces to the street, on a paved footing (Jah, 10-22: it stood a
			// cell further off and turned along the wall)
			final int x = x0 + half + 2;
			final int u = f.u(x, -CANOPY_DEPTH);
			final int v = f.v(x, -CANOPY_DEPTH);
			if (onSite(f, x) && !taken(u, StationGeometry.PLATFORM_Y, v) && !taken(u, 1, v) && !taken(u, 2, v)) {
				// paved back to the wall, so a head out past the ground still holds it
				for (int z = -1; z >= -CANOPY_DEPTH; z--) {
					put(Role.FLOOR, f.u(x, z), StationGeometry.PLATFORM_Y, f.v(x, z), out, b.plinth());
				}
				putText(Role.FIXTURE, u, 1, v, out, PlanUnderground.PYLON, "NONE:" + spec.name());
				put(Role.FIXTURE, u, 2, v, out, PlanUnderground.PYLON, "half=upper");
			}
		}
	}

	/** The pylon and its footing, out to the canopy's edge, all inside the station's footprint. */
	private boolean onSite(Front f, int x) {
		for (int z = -1; z >= -CANOPY_DEPTH; z--) {
			final int u = f.u(x, z);
			final int v = f.v(x, z);
			if (u < 0 || u >= layout.width() || v < layout.vMin() || v > layout.vMax()) {
				return false;
			}
		}
		return true;
	}

	/** Re-faces a wall cell of the street front: only a wall or window, never a doorway or a sign. */
	private void face(int u, int y, int v, LocalDir out, String blockId) {
		final PlannedBlock block = at(u, y, v);
		if (block != null && (block.role() == Role.WALL || block.role() == Role.WINDOW)) {
			cells.put(new Cell(u, y, v), new PlannedBlock(u, y, v, Role.WALL, blockId, out, "", ""));
		}
	}
}
