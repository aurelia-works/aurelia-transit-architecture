package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * The inside of a terminus head: gallery, retail, rooms, ticket office and seating.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanConcourse extends PlanHead {

	PlanConcourse(StationSpec spec) {
		super(spec);
	}

	/**
	 * Inside a terminus head building, street to trains, as a real terminus works (its passengers wait in the concourse, not
	 * on the platforms; docs/STATION_BUILDINGS.md):
	 * <ol>
	 * <li>the unpaid ticket hall against the street: a ticket office, ticket machines and an information point on one side,
	 * toilets and a shop on the other;</li>
	 * <li>one gate line across the hall: a bank of gates centred on the entrance with a wide gate in the middle, glass
	 * barriers out to the side walls;</li>
	 * <li>the paid concourse, where people wait: the main departure board and clock over the platform ends, benches facing
	 * them, a kiosk, and in a hall big enough a gallery down each side, reached by a lift at its platform end and a stair at
	 * its street end.</li>
	 * </ol>
	 * Narrow or shallow halls keep what fits: the gate line always, a ticket window in the corner, the board and the clock.
	 */
	void headInterior() {
		if (!layout.terminus()) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int u0 = layout.headFrom();
		final int u1 = layout.headTo();
		final int vL = length;
		final int vE = layout.headEnd();
		final int door = headDoor();
		final int depth = vE - vL;
		final int gateV = headGateV();
		final int inner = u1 - u0 - 1;
		// the gate line: banks of gates with a wide gate in the middle of each, one on the walk from the entrance and in a big
		// hall one more each side, glass fence between them and out to the side walls (Jah 10-20: one long bank across the
		// hall felt cluttered)
		final int half = Math.max(2, Math.min((inner - 4) / 2, GATE_BANK_HALF));
		final int pitch = 2 * half + 1 + GATE_BANK_GAP;
		final int banks = inner >= 3 * pitch && layout.tracks().size() >= 6 ? 3 : 1;
		for (int u = u0 + 1; u < u1; u++) {
			// from the middle of the nearest bank
			final int d = banks == 1 ? u - door : u - door - pitch * Math.max(-1, Math.min(1, Math.round((u - door) / (float) pitch)));
			if (Math.abs(d) <= half) {
				final String kind = Math.abs(d) == half ? "kind=end" : d == 0 ? "kind=wide" : "kind=gate";
				// the green "go" side faces the ticket hall, where people come in; the red X faces the concourse
				gate(u, StationGeometry.WALK_Y, gateV, LocalDir.PLUS_V, kind, d > 0);
			} else {
				fence(u, StationGeometry.WALK_Y, gateV, LocalDir.PLUS_V);
			}
		}
		gateSigns(banks == 1 ? List.of(door) : List.of(door - pitch, door, door + pitch), gateV);
		gateBooth(door + half + 2, gateV, u1, b);
		// a big number under the lintel over each platform end
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (gateNumbered(band)) {
				// an island's two numbers side by side over its middle, the left face's on the left
				final List<Integer> numbers = layout.platformNumbers(band);
				final int mid = (band.uMin() + band.uMax() + 1) / 2;
				if (numbers.size() == 2) {
					numberSign(mid - 1, StationGeometry.UPPER_FLOOR_Y - 1, vL, LocalDir.PLUS_V, Integer.toString(numbers.get(0)));
					numberSign(mid, StationGeometry.UPPER_FLOOR_Y - 1, vL, LocalDir.PLUS_V, Integer.toString(numbers.get(1)));
				} else if (numbers.size() == 1) {
					numberSign(mid, StationGeometry.UPPER_FLOOR_Y - 1, vL, LocalDir.PLUS_V, Integer.toString(numbers.get(0)));
				}
			}
		}
		// the main departure board hung from the lintel over the platform ends, facing the concourse, and the clock over it
		final int boardHalf = Math.min((inner - 1) / 2, scale == StationScale.GRAND ? 4 : scale == StationScale.CITY ? 3 : 1);
		for (int u = door - boardHalf; u <= door + boardHalf; u++) {
			put(Role.FIXTURE, u, StationGeometry.UPPER_FLOOR_Y, vL + 1, LocalDir.PLUS_V, "aurelia_transit_architecture:concourse_board");
		}
		put(Role.FIXTURE, door, StationGeometry.UPPER_WALK_Y, vL + 1, LocalDir.PLUS_V, "aurelia_transit_architecture:wall_digital_clock");
		final int gallery = galleryWidth(inner, gateV - vL);
		if (gallery > 0) {
			for (final int side : new int[]{-1, 1}) {
				gallery(side, gallery, u0, u1, vL, gateV, b);
				if (spec.hub()) {
					retailRow(side, gallery, u0, u1, vL, gateV, b);
				}
			}
		}
		// the ticket hall's rooms stand against the side walls, leaving the walk from the entrance to the gates clear
		final boolean rooms = inner >= 16 && vE - 1 - gateV >= 7;
		if (rooms) {
			ticketOffice(u0 + 1, gateV + 2, b);
			toilets(u1 - 5, gateV + 2, b);
			if (vE - 2 - (gateV + 8) >= 4) {
				shop(u1 - 5, gateV + 8, vE - 2, b);
			}
			for (int i = 0; i < 3 && gateV + 7 + i < vE - 1; i++) {
				ticketMachine(u0 + 1, 1, gateV + 7 + i, LocalDir.PLUS_U);
			}
			put(Role.FIXTURE, door - 3, StationGeometry.WALK_Y, vE - 2, LocalDir.PLUS_V, "aurelia_transit_architecture:information_pillar");
		} else {
			// too small for rooms: a ticket window in the side wall's corner by the entrance, the machines opposite
			put(Role.WALL, u0 + 1, 1, vE - 1, LocalDir.PLUS_U, b.interiorWall());
			put(Role.FIXTURE, u0 + 1, 2, vE - 1, LocalDir.PLUS_U, "aurelia_transit_architecture:booth_window");
			for (int i = 0; i < 2 && vE - 1 - i > gateV + 1; i++) {
				ticketMachine(u1 - 1, 1, vE - 1 - i, LocalDir.MINUS_U);
			}
		}
		// on the paid side: the kiosk against a side wall where passengers wait, a help point by the gates
		final int clearOfGallery = gallery > 0 ? gallery + 1 : 1;
		tallFixture(u1 - clearOfGallery, 1, gateV - 2, LocalDir.MINUS_U, "aurelia_transit_architecture:passenger_info_kiosk");
		if (door + half + 2 < u1) {
			put(Role.WALL, door + half + 2, StationGeometry.WALK_Y, gateV - 1, LocalDir.MINUS_V, b.trim());
			put(Role.FIXTURE, door + half + 2, StationGeometry.WALK_Y + 1, gateV - 1, LocalDir.MINUS_V, HELP_POINT);
		}
		// seating islands facing the board, either side of the walk from the gates to the platforms
		if (spec.furniture()) {
			concourseSeats(u0 + clearOfGallery + 1, u1 - clearOfGallery - 1, door, vL, gateV, b);
		}
		// MTR's station name over the street door and its tactile map inside beside it
		for (int u = door - headEntranceHalf(); u <= door + headEntranceHalf(); u++) {
			put(Role.FIXTURE, u, MTR_NAME_Y, vE + 1, LocalDir.PLUS_V, StylePalette.MTR_STATION_NAME);
		}
		tallFixture(door + headEntranceHalf() + 1, 1, vE - 1, LocalDir.MINUS_V, StylePalette.MTR_TACTILE_MAP);
	}

	/**
	 * A hanging sign on a rod over each bank of gates, facing the ticket hall, naming the platforms nearest to it
	 * ("Platforms 1–2"). A bank no platform is nearest to gets none.
	 */
	void gateSigns(List<Integer> centres, int gateV) {
		final Map<Integer, List<Integer>> served = new TreeMap<>();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (band.number() <= 0 || layout.platformNumbers(band).isEmpty()) {
				continue;
			}
			final int mid = (band.uMin() + band.uMax() + 1) / 2;
			final int nearest = centres.stream().min(Comparator.comparingInt(c -> Math.abs(c - mid))).orElseThrow();
			served.computeIfAbsent(nearest, c -> new ArrayList<>()).addAll(layout.platformNumbers(band));
		}
		final int y = StationGeometry.WALK_Y + 4;
		served.forEach((centre, numbers) -> {
			if (putText(Role.FIXTURE, centre, y, gateV, LocalDir.PLUS_V, HANGING_SIGN, "UP:" + gateSignText(numbers))) {
				dropRod(centre, y, gateV);
			}
		});
	}

	/**
	 * The gate-line staff booth on the ticket-hall side, just past the end of the middle bank (Jah 10-20): 3 x 3, a counter
	 * wall under glass between corner posts, a booth window looking along the gates to the wide gate and one looking at the gate line, a door at
	 * the back. Only where the whole booth is free and clear of the rooms against the side wall.
	 */
	void gateBooth(int ua, int gateV, int u1, StylePalette.Building b) {
		final int ub = ua + 2;
		final int va = gateV + 1;
		final int vb = gateV + 3;
		final int roof = StationGeometry.DOOR_HEIGHT + 1;
		if (ub >= u1 - 6 || vb >= layout.headEnd() - 2) {
			return;
		}
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				for (int y = 1; y <= roof; y++) {
					if (taken(u, y, v)) {
						return;
					}
				}
			}
		}
		final int mid = va + 1;
		for (int y = 1; y <= StationGeometry.DOOR_HEIGHT; y++) {
			keepClear(ub, y, mid);
		}
		put(Role.FIXTURE, ua, 2, mid, LocalDir.MINUS_U, "aurelia_transit_architecture:booth_window");
		put(Role.FIXTURE, ua + 1, 2, va, LocalDir.MINUS_V, "aurelia_transit_architecture:booth_window");
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				if (u == ua || u == ub || v == va || v == vb) {
					put(Role.WALL, u, 1, v, LocalDir.PLUS_V, b.interiorWall());
					// posts at the corners hold the roof; glass between them
					final boolean corner = (u == ua || u == ub) && (v == va || v == vb);
					for (int y = 2; y <= StationGeometry.DOOR_HEIGHT; y++) {
						if (corner) {
							put(Role.WALL, u, y, v, LocalDir.PLUS_V, b.trim());
						} else {
							put(Role.WINDOW, u, y, v, u == ua ? LocalDir.MINUS_U : u == ub ? LocalDir.PLUS_U : v == va ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.window());
						}
					}
				}
				put(Role.WALL, u, roof, v, LocalDir.PLUS_V, b.trim());
			}
		}
	}

	/** "Platform 3", or "Platforms 1–2" for a run of them. */
	static String gateSignText(List<Integer> numbers) {
		return TrackLayout.platformsText(numbers);
	}

	/** Half a bank of gates: an end, two gates and the wide one in the middle, so four gates and a wide one to a bank. */
	static final int GATE_BANK_HALF = 3;

	/** Glass fence between two banks of gates. */
	static final int GATE_BANK_GAP = 5;

	/** v of a terminus head's gate line: about two fifths of the hall from the street wall, the ticket hall at least 7 deep. */
	int headGateV() {
		final int vE = layout.headEnd();
		return vE - Math.max(8, (vE - length) * 2 / 5);
	}

	/**
	 * Width of each side gallery of a terminus concourse (its floor, the railing on its inner edge included), or 0 where the
	 * hall is too narrow or the paid concourse too shallow for a lift, a gallery and a stair one after another.
	 */
	static int galleryWidth(int inner, int paidRows) {
		if (inner < 24 || paidRows < 4 + 3 + StationGeometry.STAIR_STEPS + 2) {
			return 0;
		}
		return inner >= 40 ? 4 : 3;
	}

	/**
	 * A gallery down one side of the paid concourse ({@code side} -1 along the u0 wall, +1 along u1), {@code width} cells
	 * wide, a storey up (floor at {@link StationGeometry#UPPER_FLOOR_Y}): a glass lift at its platform end, a stair down
	 * along the wall at its street end landing short of the gates, a rail on its open edge and on the stair, piers every bay
	 * under the edge, and benches looking over the concourse.
	 */
	void gallery(int side, int width, int u0, int u1, int vL, int gateV, StylePalette.Building b) {
		final int wall = side < 0 ? u0 : u1;
		final int edge = wall - side * width;
		// the lift: car two rows deep against the platform end, doors facing the street at both levels
		final int c0 = side < 0 ? u0 + 1 : u1 - 2;
		final StationLayout.Lift lift = new StationLayout.Lift(c0, vL + 4, -1);
		shaft(lift, b.bridgeWall(), true, null, false);
		// the stair: its top step level with the gallery, its foot two rows short of the gate line
		final int top = gateV - 2 - StationGeometry.STAIR_STEPS;
		final int s0 = wall - side;
		final int s1 = wall - 2 * side;
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = top + StationGeometry.STAIR_STEPS - y;
			for (final int u : new int[]{s0, s1}) {
				put(Role.STAIR, u, y, v, LocalDir.MINUS_V, b.stair());
				// a stringer under each step, so nobody walks in under the stair
				for (int below = 1; below < y; below++) {
					put(Role.WALL, u, below, v, LocalDir.MINUS_V, b.trim());
				}
				for (int clear = y + 1; clear <= y + 3; clear++) {
					keepClear(u, clear, v);
				}
			}
			// the stair's open side: a solid balustrade up to the step, the rail on it
			for (int below = 1; below <= y; below++) {
				put(Role.WALL, wall - 3 * side, below, v, LocalDir.MINUS_V, b.trim());
			}
			put(Role.RAILING, wall - 3 * side, y + 1, v, side < 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U);
		}
		// the floor, from the lift's door row to the stair's top
		for (int v = lift.doorV(); v < top; v++) {
			for (int u = Math.min(wall - side, edge); u <= Math.max(wall - side, edge); u++) {
				final boolean lit = Math.floorMod(u + v, 3) == 0;
				put(lit ? Role.LIGHT_PANEL : Role.FLOOR, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, lit ? b.light() : b.upperFloor());
			}
			// the rail runs the whole open edge, the lift's door row too, whose corner is otherwise open on two sides
			put(Role.RAILING, edge, StationGeometry.UPPER_WALK_Y, v, side < 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U);
			// a pier under the open edge every bay, clear of the lift's landing
			if (v > lift.doorV() + 1 && Math.floorMod(v - vL, StationGeometry.BAY) == 0) {
				for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					put(Role.WALL, edge, y, v, LocalDir.PLUS_V, b.pier());
				}
			}
		}
		// the stair's top row past its balustrade, out to the open edge: floor and a rail across, so the edge rail turns the
		// corner onto the stair's rail instead of the two only touching corner to corner (a gap, Jah 10-20)
		for (int u = wall - 4 * side; side < 0 ? u <= edge : u >= edge; u -= side) {
			put(Role.FLOOR, u, StationGeometry.UPPER_FLOOR_Y, top, LocalDir.PLUS_V, b.upperFloor());
			put(Role.RAILING, u, StationGeometry.UPPER_WALK_Y, top, LocalDir.PLUS_V);
		}
		// no benches up here: a bench every few rows along the gallery wall looked random (Enviro, 10-20)
	}

	/**
	 * A hub's retail row under one side gallery (Phase 3): shop units between the lift's landing and the gallery stair, each
	 * a glazed shopfront with a doorway on the gallery's open edge, a fascia up to the gallery floor, partitions between units
	 * and a counter against the back wall. The gallery's piers stand in the shopfront line.
	 */
	void retailRow(int side, int width, int u0, int u1, int vL, int gateV, StylePalette.Building b) {
		final int wall = side < 0 ? u0 : u1;
		final int edge = wall - side * width;
		final int from = vL + 4 + RETAIL_CLEAR;
		final int to = gateV - 2 - StationGeometry.STAIR_STEPS - 1;
		int a = from;
		while (a + RETAIL_UNIT - 1 <= to) {
			final int bEnd = a + RETAIL_UNIT - 1;
			final int doorV = a + RETAIL_UNIT / 2 - 1;
			// partitions at both ends of the unit, from the back wall to the shopfront
			for (final int v : new int[]{a - 1, bEnd + 1}) {
				for (int u = Math.min(wall - side, edge); u <= Math.max(wall - side, edge); u++) {
					for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
						put(Role.WALL, u, y, v, LocalDir.PLUS_V, b.interiorWall());
					}
				}
			}
			for (int v = a; v <= bEnd; v++) {
				final boolean door = v == doorV || v == doorV + 1;
				for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					if (y <= StationGeometry.DOOR_HEIGHT && door) {
						keepClear(edge, y, v);
					} else if (y <= StationGeometry.DOOR_HEIGHT) {
						put(Role.WINDOW, edge, y, v, side < 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U, b.window());
					} else {
						put(Role.WALL, edge, y, v, LocalDir.PLUS_V, b.trim());
					}
				}
				if (!door) {
					put(Role.FIXTURE, wall - side, 1, v, side < 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U, "minecraft:barrel");
				}
			}
			a = bEnd + 2;
		}
	}

	/**
	 * A closed room of the ticket hall: walls four high (2.7 m) and a ceiling over them, with a doorway {@code doorWidth}
	 * wide from {@code (doorU, doorV)} along v in its {@code doorU} wall.
	 */
	void room(int ua, int ub, int va, int vb, String wallId, int doorU, int doorV, int doorWidth) {
		final int height = StationGeometry.DOOR_HEIGHT + 1;
		for (int dw = 0; dw < doorWidth; dw++) {
			for (int y = 1; y <= StationGeometry.DOOR_HEIGHT; y++) {
				keepClear(doorU, y, doorV + dw);
			}
		}
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= vb; v++) {
				final boolean rim = u == ua || u == ub || v == va || v == vb;
				if (rim) {
					for (int y = 1; y <= height; y++) {
						put(Role.WALL, u, y, v, LocalDir.PLUS_V, wallId);
					}
				}
				put(Role.WALL, u, height + 1, v, LocalDir.PLUS_V, wallId);
			}
		}
	}

	/** The ticket office against the u0 wall: a 4 x 4 room from {@code (u, v)} with two counter windows facing the hall. */
	void ticketOffice(int u, int v, StylePalette.Building b) {
		for (final int w : new int[]{v + 1, v + 2}) {
			put(Role.WALL, u + 3, 1, w, LocalDir.PLUS_U, b.interiorWall());
			put(Role.FIXTURE, u + 3, 2, w, LocalDir.PLUS_U, "aurelia_transit_architecture:booth_window");
		}
		room(u, u + 3, v, v + 3, b.interiorWall(), u, v + 1, 0);
	}

	/** Toilets against the u1 wall: a tiled 5 x 5 room ending at {@code u + 4}, its doorway facing the hall, basins inside. */
	void toilets(int u, int v, StylePalette.Building b) {
		room(u, u + 4, v, v + 4, ata("metro_pale_concrete"), u, v + 2, 1);
		for (final int w : new int[]{v + 1, v + 3}) {
			put(Role.FIXTURE, u + 3, 1, w, LocalDir.MINUS_U, "minecraft:cauldron");
		}
	}

	/** A shop unit against the u1 wall from {@code v} to {@code vb}: a glazed front with a two-wide door, shelves and a counter. */
	void shop(int u, int v, int vb, StylePalette.Building b) {
		final int doorV = (v + vb) / 2;
		for (int w = v + 1; w < vb; w++) {
			if (w != doorV && w != doorV + 1) {
				for (int y = 1; y <= StationGeometry.DOOR_HEIGHT; y++) {
					put(Role.WINDOW, u, y, w, LocalDir.MINUS_U, b.window());
				}
			}
		}
		room(u, u + 4, v, vb, b.interiorWall(), u, doorV, 2);
		for (int w = v + 1; w < vb; w++) {
			put(Role.FIXTURE, u + 3, 1, w, LocalDir.MINUS_U, "minecraft:barrel");
		}
		put(Role.WALL, u + 1, 1, v + 1, LocalDir.MINUS_U, b.trim());
	}

	/**
	 * Benches in islands of four, back to back in two rows across the middle of the paid concourse, facing the board and
	 * the gates, with the walks from the gates to every platform end kept clear.
	 */
	void concourseSeats(int uFrom, int uTo, int door, int vL, int gateV, StylePalette.Building b) {
		final int row = (vL + gateV) / 2;
		int placed = 0;
		for (int u = uFrom; u <= uTo; u++) {
			final int k = Math.floorMod(u - door, 7);
			if (Math.abs(u - door) <= 2 || k == 0 || k == 1 || k == 6) {
				continue;
			}
			if (put(Role.BENCH, u, StationGeometry.WALK_Y, row, LocalDir.MINUS_V, b.bench())) {
				placed++;
			}
			put(Role.BENCH, u, StationGeometry.WALK_Y, row + 1, LocalDir.PLUS_V, b.bench());
		}
		// a narrow concourse: one bench beside the walk, in the first row with room
		for (int v = row; v < gateV - 1 && placed == 0; v++) {
			for (int u = uFrom - 1; u <= uTo + 1 && placed == 0; u++) {
				if (Math.abs(u - door) > 1 && put(Role.BENCH, u, StationGeometry.WALK_Y, v, LocalDir.MINUS_V, b.bench())) {
					placed++;
				}
			}
		}
	}
}
