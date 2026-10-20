package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Lamps, shelters, benches, waiting rooms, speakers and departure screens.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanFurniture extends PlanRoofs {

	PlanFurniture(StationSpec spec) {
		super(spec);
	}

	void lamps() {
		if (!spec.furniture()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (roof(band) != RoofType.NONE) {
				continue;
			}
			final int u = band.island() ? (band.uMin() + band.uMax()) / 2 : backColumn(band);
			final LocalDir front = band.island() ? LocalDir.MINUS_U : towardTrack(band);
			for (int v = band.vFrom() + 4; v <= band.vTo(); v += 8) {
				// a lamp standard where there is room: the lamp on a pole, high enough to carry a speaker above head height
				boolean room = true;
				for (int y = StationGeometry.WALK_Y; y <= LAMP_HEAD_Y; y++) {
					room &= !taken(u, y, v);
				}
				if (room) {
					for (int y = StationGeometry.WALK_Y; y < LAMP_HEAD_Y; y++) {
						put(Role.FIXTURE, u, y, v, front, LAMP_POLE);
					}
				}
				put(Role.LAMP, u, room ? LAMP_HEAD_Y : StationGeometry.WALK_Y, v, front);
			}
		}
	}

	/**
	 * A waiting shelter on an open platform (no roof over it): {@link #SHELTER_LENGTH} cells long, a glass wall two high in the back
	 * column of a side platform with a seat in front of it facing the track and a roof over both, or on an island at least
	 * {@link #SHELTER_ISLAND} wide a glass wall down the middle with a seat on each side, at the free spot nearest the middle of the
	 * platform, clear of the edge, the stairs and any travelator. Platforms under a roof have cover already.
	 */
	void shelters() {
		if (!spec.furniture()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (roof(band) != RoofType.NONE || band.length() < SHELTER_MIN_PLATFORM) {
				continue;
			}
			final int middle = (band.vFrom() + band.vTo()) / 2;
			for (int d = 0; d <= band.length() / 2 - SHELTER_LENGTH; d++) {
				boolean placed = false;
				for (final int v : d == 0 ? new int[]{middle} : new int[]{middle + d, middle - d}) {
					placed = shelterAt(band, v);
					if (placed) {
						break;
					}
				}
				if (placed) {
					break;
				}
			}
		}
	}

	/** A shelter whose first cell is at {@code v}; true if all of its cells were free and it was placed. */
	boolean shelterAt(TrackLayout.PlatformBand band, int v) {
		if (v < band.vFrom() + 2 || v + SHELTER_LENGTH - 1 > band.vTo() - 2) {
			return false;
		}
		for (int i = 0; i < SHELTER_LENGTH; i++) {
			if (nearStairs(band, v + i)) {
				return false;
			}
		}
		if (!band.island()) {
			final LocalDir toTrack = towardTrack(band);
			final int wall = backColumn(band);
			final int seat = wall + (toTrack == LocalDir.PLUS_U ? 1 : -1);
			if (isEdge(band, wall) || isEdge(band, seat) || nearTravelatorRow(band, v, wall, seat)) {
				return false;
			}
			return shelter(StationGeometry.WALK_Y, wall, seat, toTrack.opposite(), v, null, 0);
		}
		if (band.width() < SHELTER_ISLAND) {
			return false;
		}
		final int middle = (band.uMin() + band.uMax()) / 2;
		if (nearTravelatorRow(band, v, middle - 1, middle + 1)) {
			return false;
		}
		// one glass wall down the middle with a seat each side, each facing its own track
		return shelter(StationGeometry.WALK_Y, middle, middle - 1, LocalDir.PLUS_U, v, LocalDir.MINUS_U, middle + 1);
	}

	boolean nearTravelatorRow(TrackLayout.PlatformBand band, int v, int u1, int u2) {
		for (int i = 0; i < SHELTER_LENGTH; i++) {
			if (nearTravelator(band, u1, v + i) || nearTravelator(band, u2, v + i)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A waiting shelter {@link #SHELTER_LENGTH} long from {@code v0}: a glass wall in column {@code wallU} two high, a seat in column
	 * {@code seatU} beside it, a roof over both columns; {@code back} is the way the wall is from that seat. With {@code seat2U} (not
	 * null) a second seat on the wall's other side, whose back is {@code back2}. True if every cell was free and it was placed.
	 */
	boolean shelter(int baseY, int wallU, int seatU, LocalDir back, int v0, LocalDir back2, int seat2U) {
		final List<Integer> columns = new ArrayList<>(List.of(wallU, seatU));
		if (back2 != null) {
			columns.add(seat2U);
		}
		final int roofY = baseY + 2;
		for (int i = 0; i < SHELTER_LENGTH; i++) {
			for (final int u : columns) {
				for (int y = baseY; y <= roofY; y++) {
					if (taken(u, y, v0 + i)) {
						return false;
					}
				}
			}
		}
		for (int i = 0; i < SHELTER_LENGTH; i++) {
			put(Role.FIXTURE, wallU, baseY, v0 + i, back, SHELTER_GLASS);
			put(Role.FIXTURE, wallU, baseY + 1, v0 + i, back, SHELTER_GLASS);
			put(Role.FIXTURE, seatU, baseY, v0 + i, back, SHELTER_SEAT);
			if (back2 != null) {
				put(Role.FIXTURE, seat2U, baseY, v0 + i, back2, SHELTER_SEAT);
			}
			for (final int u : columns) {
				put(Role.FIXTURE, u, roofY, v0 + i, u == wallU || u == seatU ? back : back2, SHELTER_ROOF);
			}
		}
		return true;
	}

	void benchesAndBins() {
		if (!spec.furniture()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			int index = 0;
			// the first bench 8 in from the platform's start, or with a seed anywhere from 4 to 12 in
			final int first = StationSeed.benchStart(spec.seed(), PLATFORM_COLOURS.size());
			for (int v = band.vFrom() + first; v <= band.vTo() - 1; v += 16, index++) {
				final int u;
				final LocalDir front;
				if (band.island()) {
					u = nearestFree(band, (band.uMin() + band.uMax()) / 2, v);
					front = index % 2 == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U;
				} else {
					u = backColumn(band);
					front = towardTrack(band);
				}
				// never by or under a stair flight or a travelator; a two-seat bench (two benches side by side) with a bin beside it
				if (u < 0 || isEdge(band, u) || nearStairs(band, v) || nearStairs(band, v + 1)
						|| nearTravelator(band, u, v) || nearTravelator(band, u, v + 1)
						|| taken(u, StationGeometry.WALK_Y, v) || taken(u, StationGeometry.WALK_Y, v + 1)) {
					continue;
				}
				put(Role.BENCH, u, StationGeometry.WALK_Y, v, front);
				put(Role.BENCH, u, StationGeometry.WALK_Y, v + 1, front);
				// the bin after the pair, when that cell is still platform (else the bench stands without one)
				if (v + 2 <= band.vTo()) {
					put(Role.BIN, u, StationGeometry.WALK_Y, v + 2, front);
				}
			}
		}
	}

	/** The free, non-edge walking cell of an island nearest to u0 at v (lower u wins ties); -1 if there is none. */
	int nearestFree(TrackLayout.PlatformBand band, int u0, int v) {
		for (int d = 0; d <= band.width(); d++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				final int u = u0 + sign * d;
				if (u >= band.uMin() && u <= band.uMax() && !isEdge(band, u) && !taken(u, StationGeometry.WALK_Y, v)) {
					return u;
				}
				if (d == 0) {
					break;
				}
			}
		}
		return -1;
	}

	/** True within two cells (each way) of a travelator step on this platform, so furniture never crowds the walkway. */
	boolean nearTravelator(TrackLayout.PlatformBand band, int u, int v) {
		for (final PlannedBlock block : cells.values()) {
			if (block.role() == Role.ESCALATOR && block.y() == StationGeometry.PLATFORM_Y && band.contains(block.u(), block.v())
					&& Math.abs(block.v() - v) <= 2 && Math.abs(block.u() - u) <= 2) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The furnished ground and upper floors: the gate line with a wide gate, glass barriers elsewhere, the ticket
	 * office with its window, MTR ticket machines, the information kiosk, benches, the clock above the entrance and
	 * departure boards on the end walls, plus benches and boards upstairs.
	 */
	void interior() {
		if (!site.building()) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		final int vS = site.vStart();
		final int vE = site.vEnd();
		final int vEntr = site.entranceV();
		final int uGate = uS + 4;
		// gate bank around the entrance: END, GATE, WIDE, GATE, END; glass barriers elsewhere
		final String[] bank = {"kind=end", "kind=gate", "kind=wide", "kind=gate", "kind=end"};
		for (int i = 0; i < bank.length; i++) {
			final int v = vEntr - 2 + i;
			// green towards the street door, red towards the platforms
			put(Role.GATE, uGate, StationGeometry.WALK_Y, v, LocalDir.MINUS_U, palette.blockId(Role.GATE), bank[i]);
		}
		for (int v = vS + 1; v <= vE - 1; v++) {
			if (v >= vEntr - 2 && v <= vEntr + 2) {
				continue;
			}
			put(Role.BARRIER, uGate, StationGeometry.WALK_Y, v, LocalDir.PLUS_U);
		}
		// ticket office on the unpaid side with its window facing +v (north of the entrance when it fits, else south)
		final int officeU0 = uS + 1;
		final int officeU1 = uS + 2;
		int officeV0 = vEntr + 3;
		int officeV1 = vEntr + 4;
		if (officeV1 > vE - 1) {
			officeV0 = vEntr - 4;
			officeV1 = vEntr - 3;
		}
		if (officeV0 >= vS + 1 && officeV1 <= vE - 1) {
			for (int u = officeU0; u <= officeU1; u++) {
				put(Role.WALL, u, 1, officeV0 - 1, LocalDir.MINUS_V, b.interiorWall());
				put(Role.WALL, u, 2, officeV0 - 1, LocalDir.MINUS_V, b.interiorWall());
			}
			put(Role.WALL, officeU0 - 1, 1, officeV0, LocalDir.MINUS_U, b.interiorWall());
			put(Role.WALL, officeU0 - 1, 2, officeV0, LocalDir.MINUS_U, b.interiorWall());
			put(Role.WALL, officeU0, 1, officeV0, LocalDir.PLUS_V, b.interiorWall());
			put(Role.FIXTURE, officeU0, 2, officeV0, LocalDir.PLUS_V, "aurelia_transit_architecture:booth_window");
			put(Role.WALL, officeU0 - 1, 1, officeV1, LocalDir.MINUS_U, b.interiorWall());
			put(Role.WALL, officeU0 - 1, 2, officeV1, LocalDir.MINUS_U, b.interiorWall());
			for (int u = officeU0; u <= officeU1; u++) {
				put(Role.WALL, u, 1, officeV1 + 1, LocalDir.PLUS_V, b.interiorWall());
				put(Role.WALL, u, 2, officeV1 + 1, LocalDir.PLUS_V, b.interiorWall());
			}
		}
		// MTR ticket machines, two tall, against the unpaid side south of the entrance
		for (int i = 0; i < 2; i++) {
			final int v = vEntr - 4 - i;
			if (v <= vS) {
				continue;
			}
			tallFixture(uS + 1, 1, v, LocalDir.PLUS_U, "mtr:ticket_machine");
		}
		// information kiosk on the unpaid side, against the street wall beside the machines (or by the entrance)
		for (final int v : new int[]{vEntr - 6, vEntr - 3}) {
			if (v >= vS + 1 && v <= vE - 1 && tallFixture(uS + 1, 1, v, LocalDir.PLUS_U, "aurelia_transit_architecture:passenger_info_kiosk")) {
				break;
			}
		}
		// benches in the paid hall and on the unpaid side (only with furniture, only inside the building)
		if (spec.furniture()) {
			if (vEntr + 6 >= vS + 1 && vEntr + 6 <= vE - 1) {
				put(Role.BENCH, uS + 2, 1, vEntr + 6, LocalDir.PLUS_V, b.bench());
			}
			if (vEntr - 6 >= vS + 1 && vEntr - 6 <= vE - 1) {
				put(Role.BENCH, uP - 2, 1, vEntr - 6, LocalDir.MINUS_V, b.bench());
			}
			final int[] room = waitingRoom(b);
			// upstairs benches on the upper slab (walk level y=6) outside the waiting room
			for (int bay = 0; bay < site.bays(); bay++) {
				final int v = site.bayCentre(bay);
				if (room != null && v >= room[0] && v <= room[1]) {
					continue;
				}
				if (v >= site.innerStairMin() - 1 && v <= site.innerStairMax() + 1) {
					continue;
				}
				if (v >= site.bridgeV() - bridgeWall() && v <= site.bridgeV() + bridgeWall()) {
					continue;
				}
				put(Role.BENCH, uS + 3, StationGeometry.UPPER_WALK_Y, v, LocalDir.PLUS_V, b.bench());
			}
		}
		// clock above the entrance, facing into the hall
		put(Role.FIXTURE, uS + 1, 4, vEntr, LocalDir.PLUS_U, "aurelia_transit_architecture:wall_digital_clock");
		// MTR's station name over the street door and its tactile map inside beside it
		for (int v = vEntr - 1; v <= vEntr + 1; v++) {
			put(Role.FIXTURE, uS - 1, MTR_NAME_Y, v, LocalDir.MINUS_U, StylePalette.MTR_STATION_NAME);
		}
		tallFixture(uS + 1, 1, vEntr - 2, LocalDir.PLUS_U, StylePalette.MTR_TACTILE_MAP);
		// departure boards on both end walls, facing into the hall
		// (with wings the end walls are open there, so the ground-floor boards hang only where a wall is behind them)
		if (cells.containsKey(new Cell((uS + uP) / 2, 2, vS))) {
			put(Role.FIXTURE, (uS + uP) / 2, 2, vS + 1, LocalDir.PLUS_V, "aurelia_transit_architecture:concourse_board");
		}
		if (cells.containsKey(new Cell((uS + uP) / 2, 2, vE))) {
			put(Role.FIXTURE, (uS + uP) / 2, 2, vE - 1, LocalDir.MINUS_V, "aurelia_transit_architecture:concourse_board");
		}
		put(Role.FIXTURE, (uS + uP) / 2, StationGeometry.UPPER_WALK_Y + 1, vS + 1, LocalDir.PLUS_V,
				"aurelia_transit_architecture:concourse_board");
	}

	/**
	 * The upstairs waiting room: the end bay away from the inner stair, closed off by a glass partition with one doorway,
	 * with benches along both long walls, bins in the far corners, a departure board on the end wall and a hanging clock.
	 * Only when the bay is clear of the stair and the footbridge doorway (buildings of three bays or more).
	 *
	 * @return the room's lowest and highest v including the partition, or null when there is no room
	 */
	int[] waitingRoom(StylePalette.Building b) {
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		final boolean atStart = site.innerStairSide() > 0;
		final int partition = atStart ? site.vStart() + StationGeometry.BAY : site.vEnd() - StationGeometry.BAY;
		final int lo = atStart ? site.vStart() + 1 : partition + 1;
		final int hi = atStart ? partition - 1 : site.vEnd() - 1;
		final int endWall = atStart ? lo : hi;
		final int from = Math.min(lo, partition);
		final int to = Math.max(hi, partition);
		final StationLayout.Lift lift = site.buildingLift(layout);
		if (to >= site.innerStairMin() - 1 && from <= site.innerStairMax() + 1
				|| site.bridge() && to >= site.bridgeV() - bridgeWall() && from <= site.bridgeV() + bridgeWall()
				|| lift != null && to >= lift.vMin() - 1 && from <= lift.vMax() + 1) {
			return null;
		}
		final int door = (uS + uP) / 2;
		final int y = StationGeometry.UPPER_WALK_Y;
		// the board and clock face from the end wall towards the doorway
		final LocalDir towardDoor = atStart ? LocalDir.PLUS_V : LocalDir.MINUS_V;
		keepClear(door, y, partition);
		keepClear(door, y + 1, partition);
		for (int u = uS + 1; u <= uP - 1; u++) {
			for (int h = y; h < StationGeometry.BUILDING_ROOF_Y; h++) {
				put(Role.WINDOW, u, h, partition, towardDoor, WAITING_ROOM_GLASS);
			}
		}
		for (int v = lo; v <= hi; v++) {
			final boolean corner = v == endWall;
			put(corner ? Role.BIN : Role.BENCH, uS + 1, y, v, LocalDir.PLUS_U, corner ? palette.blockId(Role.BIN) : b.bench());
			put(corner ? Role.BIN : Role.BENCH, uP - 1, y, v, LocalDir.MINUS_U, corner ? palette.blockId(Role.BIN) : b.bench());
		}
		put(Role.FIXTURE, door, y + 1, endWall, towardDoor, "aurelia_transit_architecture:concourse_board");
		put(Role.FIXTURE, door, StationGeometry.BUILDING_ROOF_Y - 1, (lo + hi) / 2, towardDoor,
				"aurelia_transit_architecture:hanging_digital_clock");
		return new int[]{from, to};
	}

	/**
	 * Departure screens on every platform of a furnished station: one screen point on platforms shorter than 48, two (at a
	 * quarter and three quarters of the length) on longer ones. A screen spans from pole to pole where the platform has two
	 * column lines, else is fixed to a column where it has one;
	 * under a roof without, it hangs from the ceiling, double-sided and facing along the platform, four blocks wide where the platform and the ceiling have room, else two (never over an
	 * edge cell); on an open
	 * platform a standing screen is fixed to the back of a lamp post. Points keep clear of footbridge stairs and their
	 * landings. Each platform also gets one help point, a wall unit, fixed to the post nearest its first screen.
	 */
	void platformEquipment() {
		if (!spec.furniture()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final List<Integer> targets = screenTargets(band);
			for (final int target : targets) {
				// a screen is always a proper board: from pole to pole across a column pair where the platform has one (raised
				// clear of heads, under a girder of its own), else fixed to a column (it joins the pole rather than hanging
				// beside it), hung, or two blocks on a post. Never a lone single block on the floor.
				if (!screenBetweenColumns(band, target) && !screenOnColumn(band, target) && !hangScreen(band, target)) {
					screenOnPost(band, target);
				}
			}
			onPost(band, targets.get(0), StationGeometry.WALK_Y, HELP_POINT);
		}
	}

	/** Where a platform's screens go: one per {@link #SCREEN_SPACING} blocks of platform, evenly spread, at least one. */
	static List<Integer> screenTargets(TrackLayout.PlatformBand band) {
		final int run = band.length();
		final int count = Math.max(1, Math.round(run / (float) SCREEN_SPACING));
		final List<Integer> targets = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			targets.add(band.vFrom() + run * (2 * i + 1) / (2 * count));
		}
		return targets;
	}

	/**
	 * Announcement speakers along every platform, one per {@link #SPEAKER_SPACING} blocks: under a low roof hung from the
	 * ceiling, otherwise fixed high on a column or lamp post (under a train shed too high to hang from, or in the open), never
	 * at floor level. Planned after the name signs, so they only take cells nothing else wanted.
	 */
	void speakers() {
		if (!spec.furniture()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final int count = Math.max(1, Math.round(band.length() / (float) SPEAKER_SPACING));
			for (int i = 0; i < count; i++) {
				final int target = band.vFrom() + band.length() * (2 * i + 1) / (2 * count) + 3;
				if (!hangSpeaker(band, target) && !onPost(band, target, SPEAKER_Y, WALL_SPEAKER)) {
					onPost(band, target, SPEAKER_Y - 1, WALL_SPEAKER);
				}
			}
		}
	}

	/**
	 * Hangs a ceiling speaker under the roof over the middle of an island (the back of a side platform) at the free v
	 * nearest {@code target}; true if hung.
	 */
	boolean hangSpeaker(TrackLayout.PlatformBand band, int target) {
		final int u = band.island() ? (band.uMin() + band.uMax()) / 2 : backColumn(band);
		if (isEdge(band, u)) {
			return false;
		}
		for (int d = 0; d <= 12; d++) {
			for (final int v : d == 0 ? new int[]{target} : new int[]{target + d, target - d}) {
				if (v < band.vFrom() + 1 || v > band.vTo() - 1 || nearStairs(band, v)) {
					continue;
				}
				final int y = ceiling(u, v) - 1;
				if (y >= StationGeometry.WALK_Y + 2 && y <= StationGeometry.WALK_Y + 5 && !taken(u, y, v)) {
					put(Role.FIXTURE, u, y, v, LocalDir.MINUS_V, CEILING_SPEAKER);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The column spans a hanging screen may take, widest first: four across the middle of an island or from the back of a
	 * side platform, then two (the middle and the next of an island, the back two of a side platform), each clear of the
	 * boarding edges. Centred on the platform, not on a girder line: an even-width island's girder is half a cell off its
	 * middle, and the screen is read against the platform.
	 */
	static List<List<Integer>> screenSpans(TrackLayout.PlatformBand band) {
		final List<List<Integer>> spans = new ArrayList<>();
		final int step = band.island() || band.trackRight() ? 1 : -1;
		final int wide = band.island() ? (band.uMin() + band.uMax()) / 2 - 1 : backColumn(band);
		final List<Integer> four = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			four.add(wide + i * step);
		}
		if (four.stream().allMatch(u -> u >= band.uMin() && u <= band.uMax() && !isEdge(band, u))) {
			four.sort(null);
			spans.add(four);
		}
		final List<Integer> columns = new ArrayList<>();
		final int first = band.island() ? (band.uMin() + band.uMax()) / 2 : backColumn(band);
		columns.add(first);
		if (band.width() >= 4 && !isEdge(band, first + step)) {
			columns.add(first + step);
		}
		spans.add(columns);
		return spans;
	}

	/**
	 * Hangs a screen under the ceiling at the free v nearest {@code target} (searching 0, +2, -2 ... up to 12), as wide as
	 * it fits there; true if hung.
	 */
	boolean hangScreen(TrackLayout.PlatformBand band, int target) {
		for (final List<Integer> columns : screenSpans(band)) {
			if (hangScreen(band, target, columns)) {
				return true;
			}
		}
		return false;
	}

	boolean hangScreen(TrackLayout.PlatformBand band, int target, List<Integer> columns) {
		for (int d = 0; d <= 12; d += 2) {
			for (final int v : d == 0 ? new int[]{target} : new int[]{target + d, target - d}) {
				if (v < band.vFrom() + 1 || v > band.vTo() - 1 || nearStairs(band, v)) {
					continue;
				}
				// it hangs from the lowest ceiling over its columns; every column has one close above (a hall's girder line
				// may sit a block below the roof beside it)
				final int ceiling = columns.stream().mapToInt(u -> ceiling(u, v)).min().orElse(-1);
				final int y = ceiling - 1;
				if (ceiling < 0 || y < StationGeometry.WALK_Y + 2 || y > StationGeometry.WALK_Y + 4
						|| columns.stream().anyMatch(u -> ceiling(u, v) < 0 || ceiling(u, v) > ceiling + 1 || taken(u, y, v) || isEdge(band, u))) {
					continue;
				}
				for (final int u : columns) {
					put(Role.FIXTURE, u, y, v, LocalDir.MINUS_V, HANGING_PIDS);
				}
				// where the roof is a cell higher than the girder the screen hangs from, a cross beam joins the two, so
				// every block of the screen hangs from steel rather than from the air under the roof
				for (final int u : columns) {
					if (ceiling(u, v) == y + 2 && !taken(u, y + 1, v)) {
						// a hanger, not part of the roof's frame: it carries only the screen
						put(Role.FIXTURE, u, y + 1, v, LocalDir.PLUS_U, palette.blockId(Role.BEAM), "kind=" + palette.girderKind());
					}
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * The first choice where the platform has two column lines: one double-sided screen across the whole gap between a pair
	 * of columns, in their own row, under a girder from column to column it hangs from: a single display read from both
	 * ways, like a screen on a gantry. It hangs {@link #GANTRY_SCREEN_Y} up where the columns reach its girder, else two
	 * blocks lower. The pair nearest {@code target} with the gap clear is taken. True if hung.
	 */
	boolean screenBetweenColumns(TrackLayout.PlatformBand band, int target) {
		return screenBetweenColumns(band, target, GANTRY_SCREEN_Y) || screenBetweenColumns(band, target, StationGeometry.WALK_Y + 2);
	}

	/** Height of a screen hung from pole to pole: four blocks of clear space under it. */
	static final int GANTRY_SCREEN_Y = StationGeometry.WALK_Y + 4;

	boolean screenBetweenColumns(TrackLayout.PlatformBand band, int target, int y) {
		final Map<Integer, List<Integer>> rows = new TreeMap<>();
		for (final PlannedBlock block : cells.values()) {
			if (block.role() == Role.COLUMN && block.y() == StationGeometry.WALK_Y && band.contains(block.u(), block.v())) {
				rows.computeIfAbsent(block.v(), v -> new ArrayList<>()).add(block.u());
			}
		}
		final List<Integer> order = new ArrayList<>(rows.keySet());
		order.sort(Comparator.comparingInt((Integer v) -> Math.abs(v - target)).thenComparingInt(v -> v));
		for (final int v : order) {
			final List<Integer> us = rows.get(v);
			if (us.size() < 2 || nearStairs(band, v)) {
				continue;
			}
			us.sort(null);
			for (int i = 0; i + 1 < us.size(); i++) {
				final int a = us.get(i);
				final int b = us.get(i + 1);
				if (b - a < 3 || !column(a, y + 1, v) || !column(b, y + 1, v)) {
					continue;
				}
				boolean clear = true;
				for (int u = a + 1; u < b && clear; u++) {
					clear = !isEdge(band, u) && !taken(u, y, v) && !taken(u, y + 1, v);
				}
				if (!clear) {
					continue;
				}
				for (int u = a + 1; u < b; u++) {
					put(Role.FIXTURE, u, y, v, LocalDir.MINUS_V, HANGING_PIDS);
					put(Role.FIXTURE, u, y + 1, v, LocalDir.PLUS_U, palette.blockId(Role.BEAM), "kind=" + palette.girderKind());
				}
				return true;
			}
		}
		return false;
	}

	boolean column(int u, int y, int v) {
		final PlannedBlock block = cells.get(new Cell(u, y, v));
		return block != null && block.role() == Role.COLUMN;
	}

	/**
	 * The first choice for a departure screen: a screen two blocks wide fixed to the column nearest
	 * {@code target} above head height, its middle against the column so the two blocks join into one display; on both
	 * faces of the column along the platform where there is room, so it reads from either way. True if one was fixed.
	 */
	boolean screenOnColumn(TrackLayout.PlatformBand band, int target) {
		final int y = StationGeometry.WALK_Y + 2;
		final List<Cell> posts = new ArrayList<>();
		for (final PlannedBlock block : cells.values()) {
			if (block.role() == Role.COLUMN && block.y() == StationGeometry.WALK_Y && band.contains(block.u(), block.v())) {
				posts.add(new Cell(block.u(), block.y(), block.v()));
			}
		}
		posts.sort(Comparator.comparingInt((Cell post) -> Math.abs(post.v() - target)).thenComparingInt(Cell::v).thenComparingInt(Cell::u));
		final int middle = (band.uMin() + band.uMax()) / 2;
		for (final Cell post : posts) {
			final PlannedBlock behind = cells.get(new Cell(post.u(), y, post.v()));
			if (behind == null || behind.role() != Role.COLUMN || isEdge(band, post.u())) {
				continue;
			}
			// the second block towards the platform's middle, else away from it
			final int toward = post.u() <= middle ? 1 : -1;
			boolean fixed = false;
			for (final LocalDir side : new LocalDir[]{LocalDir.PLUS_V, LocalDir.MINUS_V}) {
				final int v = post.v() + (side == LocalDir.PLUS_V ? 1 : -1);
				if (!band.runs(v) || nearStairs(band, v) || taken(post.u(), y, v)) {
					continue;
				}
				for (final int step : new int[]{toward, -toward}) {
					final int u = post.u() + step;
					if (band.contains(u, v) && !isEdge(band, u) && !taken(u, y, v)) {
						put(Role.FIXTURE, post.u(), y, v, side, PIDS);
						put(Role.FIXTURE, u, y, v, side, PIDS);
						fixed = true;
						break;
					}
				}
			}
			if (fixed) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A standing departure screen two blocks wide against the back of a column or lamp post, at the free cell nearest
	 * {@code target} (an open platform with no roof or columns to hang from). The two blocks join into one display, as a
	 * wall unit does. True if placed.
	 */
	boolean screenOnPost(TrackLayout.PlatformBand band, int target) {
		final List<Cell> posts = new ArrayList<>();
		for (final PlannedBlock block : cells.values()) {
			final boolean post = block.role() == Role.COLUMN || block.role() == Role.LAMP || block.blockId().equals(LAMP_POLE);
			if (post && block.y() == StationGeometry.WALK_Y && band.contains(block.u(), block.v())) {
				posts.add(new Cell(block.u(), block.y(), block.v()));
			}
		}
		posts.sort(Comparator.comparingInt((Cell post) -> Math.abs(post.v() - target)).thenComparingInt(Cell::v).thenComparingInt(Cell::u));
		final int middle = (band.uMin() + band.uMax()) / 2;
		for (final Cell post : posts) {
			if (isEdge(band, post.u())) {
				continue;
			}
			for (final LocalDir side : new LocalDir[]{LocalDir.PLUS_V, LocalDir.MINUS_V}) {
				final int v = post.v() + (side == LocalDir.PLUS_V ? 1 : -1);
				if (!band.runs(v) || nearStairs(band, v)) {
					continue;
				}
				final int toward = post.u() <= middle ? 1 : -1;
				for (final int step : new int[]{toward, -toward}) {
					final int other = post.u() + step;
					if (!band.contains(other, v) || isEdge(band, other) || taken(post.u(), StationGeometry.WALK_Y, v)
							|| taken(other, StationGeometry.WALK_Y, v)) {
						continue;
					}
					put(Role.FIXTURE, post.u(), StationGeometry.WALK_Y, v, side, PIDS);
					put(Role.FIXTURE, other, StationGeometry.WALK_Y, v, side, PIDS);
					return true;
				}
			}
		}
		// a platform too narrow for two blocks side by side still gets one standing screen rather than none
		for (final Cell post : posts) {
			if (isEdge(band, post.u())) {
				continue;
			}
			for (final LocalDir side : new LocalDir[]{LocalDir.PLUS_V, LocalDir.MINUS_V}) {
				final int v = post.v() + (side == LocalDir.PLUS_V ? 1 : -1);
				if (band.runs(v) && !nearStairs(band, v) && !taken(post.u(), StationGeometry.WALK_Y, v)
						&& put(Role.FIXTURE, post.u(), StationGeometry.WALK_Y, v, side, PIDS)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The lowest roof, deck or slab above the walking level at (u, v), or -1 if open to the sky. */
	int ceiling(int u, int v) {
		for (int y = StationGeometry.WALK_Y + 1; y <= StationGeometry.BUILDING_ROOF_Y + 2; y++) {
			final PlannedBlock block = cells.get(new Cell(u, y, v));
			if (block != null && block.role().covers()) {
				return y;
			}
		}
		return -1;
	}

	/**
	 * Fixes a wall unit to the back of a column or lamp post on this platform, at height {@code y}, in the free cell along
	 * the platform next to the post nearest {@code target}, facing away from it. True if placed.
	 */
	boolean onPost(TrackLayout.PlatformBand band, int target, int y, String blockId) {
		final List<Cell> posts = new ArrayList<>();
		for (final PlannedBlock block : cells.values()) {
			final boolean post = block.role() == Role.COLUMN || block.role() == Role.LAMP || block.blockId().equals(LAMP_POLE);
			if (post && block.y() == StationGeometry.WALK_Y && band.contains(block.u(), block.v())) {
				posts.add(new Cell(block.u(), block.y(), block.v()));
			}
		}
		posts.sort(Comparator.comparingInt((Cell post) -> Math.abs(post.v() - target)).thenComparingInt(Cell::v).thenComparingInt(Cell::u));
		for (final Cell post : posts) {
			for (final LocalDir side : new LocalDir[]{LocalDir.PLUS_V, LocalDir.MINUS_V}) {
				final int v = post.v() + (side == LocalDir.PLUS_V ? 1 : -1);
				// above the floor the unit needs the post itself at that height to be fixed to
				final boolean held = y == StationGeometry.WALK_Y || cells.containsKey(new Cell(post.u(), y, post.v()));
				if (held && band.runs(v) && !isEdge(band, post.u()) && !nearStairs(band, v) && !taken(post.u(), y, v)
						&& put(Role.FIXTURE, post.u(), y, v, side, blockId)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A stairwell's flank: glass like the corridor, or solid wall when the stairs are asked for enclosed. */
	void flank(int u, int y, int v, LocalDir out, StylePalette.Building b) {
		if (spec.enclosedStairs()) {
			put(Role.WALL, u, y, v, out, b.wall());
		} else {
			put(Role.WINDOW, u, y, v, out, b.bridgeWall());
		}
	}
}
