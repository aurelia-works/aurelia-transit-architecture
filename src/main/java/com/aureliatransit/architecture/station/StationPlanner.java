package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link StationPlan} from a spec: railway geometry from {@link StationGeometry}, then roles for every cell, then
 * block ids from the {@link StylePalette}. Deterministic: the same spec always gives an equal plan.
 * <p>
 * Rules run in a fixed order and each cell holds at most one block: when two rules want the same cell the earlier rule wins
 * and the later block is skipped.
 */
public final class StationPlanner {

	private static final String PIDS = "aurelia_transit_architecture:platform_pids";
	private static final String WAITING_ROOM_GLASS = "aurelia_transit_architecture:glass_wall";

	private static final Comparator<PlannedBlock> BUILD_ORDER = Comparator.comparingInt((PlannedBlock b) -> b.role().ordinal())
			.thenComparingInt(PlannedBlock::y).thenComparingInt(PlannedBlock::v).thenComparingInt(PlannedBlock::u);

	private StationPlanner() {
	}

	public static StationPlan plan(StationSpec spec) {
		final Planning planning = new Planning(spec);
		planning.floors();
		planning.ramps();
		planning.railNodes();
		planning.building();
		planning.footbridge();
		planning.columns();
		planning.canopyRoofs();
		planning.hallRoof();
		planning.lamps();
		planning.benchesAndBins();
		planning.interior();
		planning.platformEquipment();
		planning.nameSigns();
		return planning.finish();
	}

	/** The v positions of the columns of every column line: from v = 2 every COLUMN_SPACING, plus one near the far end if needed. */
	static List<Integer> columnPositions(int platformLength) {
		final List<Integer> positions = new ArrayList<>();
		int last = 0;
		for (int p = 2; p <= platformLength - 3; p += StationGeometry.COLUMN_SPACING) {
			positions.add(p);
			last = p;
		}
		if (positions.isEmpty() || platformLength - 1 - last > StationGeometry.MAX_CANTILEVER) {
			positions.add(platformLength - 3);
		}
		return positions;
	}

	/**
	 * The u of each column line of a platform, ascending. A canopy has one line down the middle. A hall has one line next to
	 * the track edge of a side platform (plus one next to the back column when the platform is so wide that the beams would
	 * overhang the back by more than the cantilever limit), and one or two lines in an island.
	 */
	static List<Integer> columnLines(TrackLayout.PlatformBand band, RoofType roof) {
		final List<Integer> lines = new ArrayList<>();
		if (roof == RoofType.CANOPY) {
			lines.add((band.uMin() + band.uMax()) / 2);
		} else if (roof == RoofType.HALL) {
			if (band.island()) {
				lines.add(band.uMin() + 1);
				if (band.width() >= 4) {
					lines.add(band.uMax() - 1);
				}
			} else if (band.trackRight()) {
				if (band.width() - 2 > StationGeometry.MAX_CANTILEVER) {
					lines.add(band.uMin() + 1);
				}
				lines.add(band.uMax() - 1);
			} else {
				lines.add(band.uMin() + 1);
				if (band.width() - 2 > StationGeometry.MAX_CANTILEVER) {
					lines.add(band.uMax() - 1);
				}
			}
		}
		return lines;
	}

	private record Cell(int u, int y, int v) {
	}

	private static final class Planning {

		private final StationSpec spec;
		private final TrackLayout layout;
		private final StylePalette palette;
		private final int length;
		private final Map<Cell, PlannedBlock> cells = new HashMap<>();
		/** Cells that must stay empty: doorways, stair headroom, landings, the space in front of entrances. */
		private final Set<Cell> air = new HashSet<>();
		private final List<Integer> columnV;
		private final List<Box> extraSupport = new ArrayList<>();
		private final StationLayout site;

		Planning(StationSpec spec) {
			this.spec = spec;
			this.layout = StationGeometry.layout(spec);
			this.palette = StylePalette.of(spec);
			this.length = layout.platformLength();
			this.columnV = columnPositions(length);
			this.site = StationLayout.of(spec, layout);
		}

		/** Places a block unless the cell is taken or must stay empty; true if placed. */
		private boolean put(Role role, int u, int y, int v, LocalDir front) {
			return put(role, u, y, v, front, palette.blockId(role), "");
		}

		private boolean put(Role role, int u, int y, int v, LocalDir front, String blockId) {
			return put(role, u, y, v, front, blockId, "");
		}

		private boolean put(Role role, int u, int y, int v, LocalDir front, String blockId, String state) {
			final Cell cell = new Cell(u, y, v);
			if (cells.containsKey(cell) || air.contains(cell)) {
				return false;
			}
			final String text = role == Role.NAME_SIGN ? spec.name() : "";
			cells.put(cell, new PlannedBlock(u, y, v, role, blockId, front, text, state));
			return true;
		}

		private boolean taken(int u, int y, int v) {
			final Cell cell = new Cell(u, y, v);
			return cells.containsKey(cell) || air.contains(cell);
		}

		private void keepClear(int u, int y, int v) {
			air.add(new Cell(u, y, v));
		}

		/** The direction from a side platform's back column towards its track. */
		private static LocalDir towardTrack(TrackLayout.PlatformBand band) {
			return band.trackRight() ? LocalDir.PLUS_U : LocalDir.MINUS_U;
		}

		private static int backColumn(TrackLayout.PlatformBand band) {
			return band.trackRight() ? band.uMin() : band.uMax();
		}

		private static boolean isEdge(TrackLayout.PlatformBand band, int u) {
			return u == band.uMin() && band.trackLeft() || u == band.uMax() && band.trackRight();
		}

		void floors() {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (int v = 0; v < length; v++) {
					for (int u = band.uMin(); u <= band.uMax(); u++) {
						if (u == band.uMin() && band.trackLeft()) {
							put(Role.PLATFORM_EDGE, u, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
						} else if (u == band.uMax() && band.trackRight()) {
							put(Role.PLATFORM_EDGE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U);
						} else {
							put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
						}
					}
				}
			}
		}

		void ramps() {
			if (layout.rampLength() == 0) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, -2, LocalDir.MINUS_V);
					put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, -1, LocalDir.MINUS_V);
					put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, length, LocalDir.PLUS_V);
					put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, length + 1, LocalDir.PLUS_V);
				}
			}
		}

		void railNodes() {
			if (!spec.railNodes()) {
				return;
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, 0, LocalDir.PLUS_V);
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, length - 1, LocalDir.PLUS_V);
			}
		}

		/** True when v is the centre of a facade bay. */
		private boolean isBayCentre(int v) {
			if (!site.building()) {
				return false;
			}
			for (int bay = 0; bay < site.bays(); bay++) {
				if (site.bayCentre(bay) == v) {
					return true;
				}
			}
			return false;
		}

		/**
		 * The station building shell beside the tracks: floor, entrance ramps, perimeter walls with pilasters and windows,
		 * the band and upper slab with lights, the flat roof with a cornice parapet, the street entrance and platform
		 * doorways, and the inner stair to the upper floor. Interior fitting is in {@link #interior()}.
		 */
		void building() {
			if (!site.building()) {
				return;
			}
			final StylePalette.Building b = palette.building();
			final int uS = StationLayout.streetWall();
			final int uP = site.platformWall(layout);
			final int vS = site.vStart();
			final int vE = site.vEnd();
			final int vEntr = site.entranceV();
			final int uA = uP - 2;
			final int uB = uP - 1;
			// reserve the openings before any wall goes up
			for (int v = vEntr - 1; v <= vEntr + 1; v++) {
				for (int y = 1; y <= 3; y++) {
					keepClear(uS, y, v);
				}
			}
			if (site.bridge()) {
				for (int v = site.bridgeV() - 1; v <= site.bridgeV() + 1; v++) {
					for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
						keepClear(uP, y, v);
					}
				}
			}
			for (int bay = 0; bay < site.bays(); bay++) {
				final int centre = site.bayCentre(bay);
				if (centre + 1 < site.innerStairMin() || centre - 1 > site.innerStairMax()) {
					for (int v = centre - 1; v <= centre + 1; v++) {
						for (int y = 1; y <= 3; y++) {
							keepClear(uP, y, v);
						}
					}
				}
			}
			for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
				final int v = site.innerStairV(y);
				for (int u = uA; u <= uB; u++) {
					keepClear(u, y + 1, v);
					keepClear(u, y + 2, v);
				}
			}
			final int vBot = site.innerStairV(1) + site.innerStairSide();
			for (int u = uA; u <= uB; u++) {
				for (int y = 1; y <= 3; y++) {
					keepClear(u, y, vBot);
				}
			}
			// ground floor and entrance ramps on the apron
			for (int u = uS; u <= uP; u++) {
				for (int v = vS; v <= vE; v++) {
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, b.floor());
				}
			}
			for (int v = vEntr - 1; v <= vEntr + 1; v++) {
				put(Role.RAMP_LOWER, 0, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
				put(Role.RAMP_UPPER, 1, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
				for (int y = 1; y <= 3; y++) {
					keepClear(0, y, v);
					keepClear(1, y, v);
				}
			}
			// perimeter walls, ground storey y=1..4
			for (int v = vS; v <= vE; v++) {
				final boolean pilaster = (v - vS) % StationGeometry.BAY == 0;
				for (int y = 1; y <= 4; y++) {
					if (y == 1) {
						put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.plinth());
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.plinth());
					} else if (y == 4) {
						put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.wall());
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.wall());
					} else if (isBayCentre(v)) {
						put(Role.WINDOW, uS, y, v, LocalDir.MINUS_U, b.window());
						put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
					} else {
						put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.wall());
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.wall());
					}
				}
				// band at the slab level (leaving the bridge doorway open)
				put(Role.WALL, uS, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.MINUS_U, b.trim());
				if (!(site.bridge() && v >= site.bridgeV() - 1 && v <= site.bridgeV() + 1)) {
					put(Role.WALL, uP, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_U, b.trim());
				}
				// upper storey y=6..8, windows in the middle row
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					if (y == StationGeometry.UPPER_WALK_Y + 1 && isBayCentre(v)) {
						put(Role.WINDOW, uS, y, v, LocalDir.MINUS_U, b.window());
						put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
					} else {
						put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.wall());
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.wall());
					}
				}
			}
			for (int u = uS; u <= uP; u++) {
				final boolean pilaster = (u - uS) % 3 == 0;
				for (int y = 1; y <= 4; y++) {
					if (y == 1) {
						put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.plinth());
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.plinth());
					} else if (y == 4) {
						put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
					} else if (u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0) {
						put(Role.WINDOW, u, y, vS, LocalDir.MINUS_V, b.window());
						put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
					} else {
						put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
					}
				}
				put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vS, LocalDir.MINUS_V, b.trim());
				put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vE, LocalDir.PLUS_V, b.trim());
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					if (y == StationGeometry.UPPER_WALK_Y + 1 && u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0) {
						put(Role.WINDOW, u, y, vS, LocalDir.MINUS_V, b.window());
						put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
					} else {
						put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
					}
				}
			}
			// upper slab with light panels, leaving the stair well open
			for (int u = uS + 1; u <= uP - 1; u++) {
				for (int v = vS + 1; v <= vE - 1; v++) {
					if (u >= uA && u <= uB && v >= site.innerStairMin() && v <= site.innerStairMax()) {
						continue;
					}
					if ((u + v) % 4 == 0) {
						put(Role.LIGHT_PANEL, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
					} else {
						put(Role.FLOOR, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.upperFloor());
					}
				}
			}
			// flat roof and cornice parapet
			for (int u = uS; u <= uP; u++) {
				for (int v = vS; v <= vE; v++) {
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.roof());
				}
			}
			for (int v = vS; v <= vE; v++) {
				put(Role.BUILDING_ROOF, uS, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.MINUS_U, b.cornice());
				put(Role.BUILDING_ROOF, uP, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.PLUS_U, b.cornice());
			}
			for (int u = uS; u <= uP; u++) {
				put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y + 1, vS, LocalDir.MINUS_V, b.cornice());
				put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y + 1, vE, LocalDir.PLUS_V, b.cornice());
			}
			// inner stair flights (two columns wide)
			for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
				final int v = site.innerStairV(y);
				for (int u = uA; u <= uB; u++) {
					put(Role.STAIR, u, y, v, site.innerStairFront(), b.stair());
				}
			}
		}

		/**
		 * The footbridge over the tracks: deck, glazed corridor walls, roof with lights, support piers on every platform
		 * and six-foot piers where the span exceeds 12, and a stair with a landing down to every served platform.
		 */
		void footbridge() {
			if (!site.bridge()) {
				return;
			}
			final StylePalette.Building b = palette.building();
			final int vB = site.bridgeV();
			final int from = site.building() ? site.platformWall(layout) : site.deckFrom();
			final int to = site.deckTo();
			final int wallLo = vB - 2;
			final int wallHi = vB + 2;
			final int stairWall = site.stairWallV();
			final int landing = site.landingV();
			final int loStair = Math.min(stairWall, landing) - 1;
			final int hiStair = Math.max(stairWall, landing) + 1;
			// keep the stair corridor and landings clear before the deck and walls go up
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (!site.served().contains(band.number())) {
					continue;
				}
				for (final int u : StationLayout.stairColumns(band)) {
					for (int y = 1; y <= 3; y++) {
						keepClear(u, y, landing);
					}
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						keepClear(u, y + 1, site.stairV(y));
						keepClear(u, y + 2, site.stairV(y));
					}
				}
			}
			// deck
			for (int u = from; u <= to; u++) {
				for (int v = vB - 1; v <= vB + 1; v++) {
					if ((u + v) % 5 == 0) {
						put(Role.BRIDGE_DECK, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.bridgeLight());
					} else {
						put(Role.BRIDGE_DECK, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.deck());
					}
				}
			}
			// corridor walls (glass, facing outwards); only the exit-side wall opens at stair columns
			for (int u = from; u <= to; u++) {
				boolean exit = false;
				for (final TrackLayout.PlatformBand band : layout.platforms()) {
					if (u >= band.uMin() && u <= band.uMax() && site.served().contains(band.number())
							&& StationLayout.stairColumns(band).contains(u)) {
						exit = true;
						break;
					}
				}
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					final boolean open = exit && y <= StationGeometry.UPPER_WALK_Y + 1;
					if (!(open && wallLo == stairWall)) {
						put(Role.WINDOW, u, y, wallLo, LocalDir.MINUS_V, b.bridgeWall());
					}
					if (!(open && wallHi == stairWall)) {
						put(Role.WINDOW, u, y, wallHi, LocalDir.PLUS_V, b.bridgeWall());
					}
				}
			}
			// roof over the corridor and the stairs
			for (int u = from; u <= to; u++) {
				for (int v = wallLo; v <= wallHi; v++) {
					if ((u + v) % 4 == 0) {
						put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.bridgeLight());
					} else {
						put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.bridgeRoof());
					}
				}
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (!hasStairs(band)) {
					continue;
				}
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					for (int v = loStair; v <= hiStair; v++) {
						if ((u + v) % 4 == 1) {
							put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.bridgeLight());
						} else {
							put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.bridgeRoof());
						}
					}
				}
			}
			// piers on every platform at the bridge line, up to the deck
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final int uMid = (band.uMin() + band.uMax()) / 2;
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					put(Role.BRIDGE_PIER, uMid, y, vB, LocalDir.PLUS_V, b.pier());
				}
			}
			// flanking piers at the stair landings (never on the landing itself) so the canopy ends stay carried
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (!site.served().contains(band.number())) {
					continue;
				}
				for (final int su : StationLayout.stairColumns(band)) {
					for (final int du : new int[]{-1, 1}) {
						final int u = su + du;
						if (u < band.uMin() || u > band.uMax() || isEdge(band, u) || StationLayout.stairColumns(band).contains(u)) {
							continue;
						}
						boolean free = true;
						for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
							if (taken(u, y, landing)) {
								free = false;
								break;
							}
						}
						if (free) {
							for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
								put(Role.BRIDGE_PIER, u, y, landing, LocalDir.PLUS_V, b.pier());
							}
							break;
						}
					}
				}
			}
			// platform stairs with landings kept clear
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (!site.served().contains(band.number())) {
					continue;
				}
				for (final int u : StationLayout.stairColumns(band)) {
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						put(Role.STAIR, u, y, site.stairV(y), site.stairFront(), b.stair());
					}
					// handrails flanking the flight (never at the canopy roof level, never on boarding edges)
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						if (y == StationGeometry.CANOPY_Y) {
							continue;
						}
						final int v = site.stairV(y);
						if (!isEdge(band, u - 1)) {
							put(Role.RAILING, u - 1, y, v, LocalDir.MINUS_U);
						}
						if (!isEdge(band, u + 1)) {
							put(Role.RAILING, u + 1, y, v, LocalDir.PLUS_U);
						}
					}
				}
			}
			// six-foot piers where the deck span between supports exceeds 12
			final List<Integer> supports = new ArrayList<>();
			supports.add(from);
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				supports.add((band.uMin() + band.uMax()) / 2);
			}
			supports.add(to);
			supports.sort(Comparator.naturalOrder());
			for (int i = 0; i + 1 < supports.size(); i++) {
				int a = supports.get(i);
				int b2 = supports.get(i + 1);
				while (b2 - a > 12) {
					final int mid = (a + b2) / 2;
					final int pierU = nearestFreeColumn(mid);
					if (pierU < 0) {
						break;
					}
					for (int y = 0; y < StationGeometry.UPPER_FLOOR_Y; y++) {
						put(Role.BRIDGE_PIER, pierU, y, vB, LocalDir.PLUS_V, b.pier());
					}
					extraSupport.add(new Box(pierU, -1, vB, pierU, -1, vB));
					a = pierU;
				}
			}
		}

		/** A free column near u0 at the bridge line that is not inside any track envelope; -1 if none nearby. */
		private int nearestFreeColumn(int u0) {
			for (int d = 0; d <= 6; d++) {
				for (final int u : new int[]{u0 - d, u0 + d}) {
					if (u < 0 || u >= layout.width()) {
						continue;
					}
					boolean envelope = false;
					for (final TrackLayout.Track track : layout.tracks()) {
						if (Math.abs(u - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH) {
							envelope = true;
							break;
						}
					}
					if (!envelope && !taken(u, 1, site.bridgeV())) {
						return u;
					}
					if (d == 0) {
						break;
					}
				}
			}
			return -1;
		}

		/** Bands with a stair flight down from the footbridge: served and wide enough for steps. */
		private boolean hasStairs(TrackLayout.PlatformBand band) {
			return site.bridge() && site.served().contains(band.number()) && !StationLayout.stairColumns(band).isEmpty();
		}

		void columns() {
			if (spec.roof() == RoofType.NONE) {
				return;
			}
			final int top = spec.roof() == RoofType.CANOPY ? StationGeometry.CANOPY_Y : StationGeometry.HALL_BEAM_Y;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int u : columnLines(band, spec.roof())) {
					for (final int v : columnVForBand(band)) {
						for (int y = StationGeometry.WALK_Y; y < top - 1; y++) {
							put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
						}
						put(Role.COLUMN_CAP, u, top - 1, v, LocalDir.PLUS_V);
					}
				}
			}
		}

		/** Column v for one platform band: the global spacing minus cells where stairs, railings or piers stand, with gaps of at most 9. */
		private List<Integer> columnVForBand(TrackLayout.PlatformBand band) {
			if (!site.bridge()) {
				return columnV;
			}
			final int top = spec.roof() == RoofType.CANOPY ? StationGeometry.CANOPY_Y : StationGeometry.HALL_BEAM_Y;
			final List<Integer> lines = columnLines(band, spec.roof());
			final List<Integer> kept = new ArrayList<>();
			for (final int v : columnV) {
				boolean clash = false;
				for (final int u : lines) {
					for (int y = StationGeometry.WALK_Y; y < top; y++) {
						if (taken(u, y, v)) {
							clash = true;
							break;
						}
					}
					if (clash) {
						break;
					}
				}
				if (!clash) {
					kept.add(v);
				}
			}
			// the footbridge pier carries the roof at bridgeV, so gaps are measured with it as a support
			final List<Integer> supports = new ArrayList<>(kept);
			supports.add(site.bridgeV());
			supports.sort(Comparator.naturalOrder());
			// fill any remaining gap longer than 9 with a midpoint column that stands free
			final List<Integer> extra = new ArrayList<>();
			for (int i = 0; i + 1 < supports.size(); i++) {
				int a = supports.get(i);
				int b = supports.get(i + 1);
				while (b - a > 9) {
					int mid = (a + b) / 2;
					if (mid <= a || mid >= b || mid < 0 || mid >= length) {
						break;
					}
					boolean clash = false;
					for (final int u : lines) {
						for (int y = StationGeometry.WALK_Y; y < top; y++) {
							if (taken(u, y, mid)) {
								clash = true;
								break;
							}
						}
						if (clash) {
							break;
						}
					}
					if (clash) {
						break;
					}
					extra.add(mid);
					a = mid;
				}
			}
			kept.addAll(extra);
			kept.sort(Comparator.naturalOrder());
			return kept;
		}

		private boolean isColumnV(int v) {
			return columnV.contains(v);
		}

		/** True for the cells of a roof lit from above: every 4th cell along a column line, except where a column stands. */
		private boolean isLightV(int v) {
			return v >= 4 && v % 4 == 0 && !isColumnV(v);
		}

		void canopyRoofs() {
			if (spec.roof() != RoofType.CANOPY) {
				return;
			}
			final int y = StationGeometry.CANOPY_Y;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final int line = columnLines(band, RoofType.CANOPY).get(0);
				for (int v = 0; v < length; v++) {
					for (int u = band.uMin(); u <= band.uMax(); u++) {
						if (u == band.uMin()) {
							put(Role.ROOF_EDGE, u, y, v, LocalDir.MINUS_U);
						} else if (u == band.uMax()) {
							put(Role.ROOF_EDGE, u, y, v, LocalDir.PLUS_U);
						} else if (u == line && isLightV(v)) {
							put(Role.ROOF_LIGHT, u, y, v, LocalDir.PLUS_V);
						} else {
							put(Role.ROOF, u, y, v, LocalDir.PLUS_V);
						}
					}
				}
			}
		}

		void hallRoof() {
			if (spec.roof() != RoofType.HALL || site.bridge()) {
				return;
			}
			for (final int v : columnV) {
				for (int u = 0; u < layout.width(); u++) {
					put(Role.BEAM, u, StationGeometry.HALL_BEAM_Y, v, LocalDir.PLUS_U);
				}
			}
			final boolean[] lit = new boolean[layout.width()];
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int u : columnLines(band, RoofType.HALL)) {
					lit[u] = true;
				}
			}
			final int y = StationGeometry.HALL_ROOF_Y;
			for (int v = 0; v < length; v++) {
				for (int u = 0; u < layout.width(); u++) {
					if (u == 0) {
						put(Role.ROOF_EDGE, u, y, v, LocalDir.MINUS_U);
					} else if (u == layout.width() - 1) {
						put(Role.ROOF_EDGE, u, y, v, LocalDir.PLUS_U);
					} else if (lit[u] && isLightV(v)) {
						put(Role.ROOF_LIGHT, u, y, v, LocalDir.PLUS_V);
					} else {
						put(Role.ROOF, u, y, v, LocalDir.PLUS_V);
					}
				}
			}
		}

		void lamps() {
			if (spec.roof() != RoofType.NONE || !spec.furniture()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final int u = band.island() ? (band.uMin() + band.uMax()) / 2 : backColumn(band);
				final LocalDir front = band.island() ? LocalDir.MINUS_U : towardTrack(band);
				for (int v = 4; v <= length - 1; v += 8) {
					put(Role.LAMP, u, StationGeometry.WALK_Y, v, front);
				}
			}
		}

		void benchesAndBins() {
			if (!spec.furniture()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				int index = 0;
				for (int v = 8; v <= length - 2; v += 16, index++) {
					final int u;
					final LocalDir front;
					if (band.island()) {
						u = nearestFree(band, (band.uMin() + band.uMax()) / 2, v);
						front = index % 2 == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					} else {
						u = backColumn(band);
						front = towardTrack(band);
					}
					if (u < 0 || isEdge(band, u)) {
						continue;
					}
					if (put(Role.BENCH, u, StationGeometry.WALK_Y, v, front)) {
						put(Role.BIN, u, StationGeometry.WALK_Y, v + 1, front);
					}
				}
			}
		}

		/** The free, non-edge walking cell of an island nearest to u0 at v (lower u wins ties); -1 if there is none. */
		private int nearestFree(TrackLayout.PlatformBand band, int u0, int v) {
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
			final int uS = StationLayout.streetWall();
			final int uP = site.platformWall(layout);
			final int vS = site.vStart();
			final int vE = site.vEnd();
			final int vEntr = site.entranceV();
			final int uGate = uS + 4;
			// gate bank around the entrance: END, GATE, WIDE, GATE, END; glass barriers elsewhere
			final String[] bank = {"kind=end", "kind=gate", "kind=wide", "kind=gate", "kind=end"};
			for (int i = 0; i < bank.length; i++) {
				final int v = vEntr - 2 + i;
				put(Role.GATE, uGate, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, palette.blockId(Role.GATE), bank[i]);
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
				put(Role.FIXTURE, officeU0, 1, officeV0, LocalDir.PLUS_V, "aurelia_transit_architecture:booth_window");
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
				put(Role.FIXTURE, uS + 1, 1, v, LocalDir.PLUS_U, "mtr:ticket_machine", "half=lower");
				put(Role.FIXTURE, uS + 1, 2, v, LocalDir.PLUS_U, "mtr:ticket_machine", "half=upper");
			}
			// information kiosk on the unpaid side
			if (vEntr - 4 >= vS + 1 && vEntr - 4 <= vE - 1) {
				put(Role.FIXTURE, uS + 2, 1, vEntr - 4, LocalDir.PLUS_V, "aurelia_transit_architecture:passenger_info_kiosk");
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
					if (v >= site.bridgeV() - 2 && v <= site.bridgeV() + 2) {
						continue;
					}
					put(Role.BENCH, uS + 3, StationGeometry.UPPER_WALK_Y, v, LocalDir.PLUS_V, b.bench());
				}
			}
			// clock above the entrance, facing into the hall
			put(Role.FIXTURE, uS + 1, 4, vEntr, LocalDir.PLUS_U, "aurelia_transit_architecture:wall_digital_clock");
			// departure boards on both end walls, facing into the hall
			put(Role.FIXTURE, (uS + uP) / 2, 2, vS + 1, LocalDir.PLUS_V, "aurelia_transit_architecture:concourse_board");
			put(Role.FIXTURE, (uS + uP) / 2, 2, vE - 1, LocalDir.MINUS_V, "aurelia_transit_architecture:concourse_board");
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
		private int[] waitingRoom(StylePalette.Building b) {
			final int uS = StationLayout.streetWall();
			final int uP = site.platformWall(layout);
			final boolean atStart = site.innerStairSide() > 0;
			final int partition = atStart ? site.vStart() + StationGeometry.BAY : site.vEnd() - StationGeometry.BAY;
			final int lo = atStart ? site.vStart() + 1 : partition + 1;
			final int hi = atStart ? partition - 1 : site.vEnd() - 1;
			final int endWall = atStart ? lo : hi;
			final int from = Math.min(lo, partition);
			final int to = Math.max(hi, partition);
			if (to >= site.innerStairMin() - 1 && from <= site.innerStairMax() + 1
					|| site.bridge() && to >= site.bridgeV() - 2 && from <= site.bridgeV() + 2) {
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
		 * quarter and three quarters of the length) on longer ones. Islands four or more wide get a back-to-back pair so both
		 * tracks' passengers see one. Points keep clear of footbridge stairs and their landings; the first point of each
		 * platform also gets a help point.
		 */
		void platformEquipment() {
			if (!spec.furniture()) {
				return;
			}
			final List<Integer> targets = length >= 48 ? List.of(length / 4, length * 3 / 4) : List.of(length / 2);
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final boolean pair = band.island() && band.width() >= 4;
				final int u0 = band.island() ? (band.uMin() + band.uMax()) / 2 : backColumn(band);
				final LocalDir front0 = band.island() ? LocalDir.MINUS_U : towardTrack(band);
				boolean helped = false;
				for (final int target : targets) {
					final int v = pidsV(band, u0, pair, target);
					if (v < 0) {
						continue;
					}
					put(Role.FIXTURE, u0, StationGeometry.WALK_Y, v, front0, PIDS);
					if (pair) {
						put(Role.FIXTURE, u0 + 1, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, PIDS);
					}
					if (!helped && !taken(u0, StationGeometry.WALK_Y, v + 1) && !nearStairs(band, v + 1)) {
						helped = put(Role.FIXTURE, u0, StationGeometry.WALK_Y, v + 1, front0, "aurelia_transit_architecture:help_point");
					}
				}
			}
		}

		/** The free v nearest {@code target} for a screen point (searching 0, +2, -2, +4 ...); -1 if none within 12. */
		private int pidsV(TrackLayout.PlatformBand band, int u0, boolean pair, int target) {
			for (int d = 0; d <= 12; d += 2) {
				for (final int v : new int[]{target + d, target - d}) {
					if (v >= 1 && v <= length - 2 && !isEdge(band, u0) && !(pair && isEdge(band, u0 + 1)) && !nearStairs(band, v)
							&& !taken(u0, StationGeometry.WALK_Y, v) && !(pair && taken(u0 + 1, StationGeometry.WALK_Y, v))) {
						return v;
					}
					if (d == 0) {
						break;
					}
				}
			}
			return -1;
		}

		/** True within 2 of a footbridge stair flight or its landing on this platform. */
		private boolean nearStairs(TrackLayout.PlatformBand band, int v) {
			if (!hasStairs(band)) {
				return false;
			}
			final int a = Math.min(site.stairWallV(), site.landingV());
			final int z = Math.max(site.stairWallV(), site.landingV());
			return v >= a - 2 && v <= z + 2;
		}

		void nameSigns() {
			if (spec.name().isEmpty()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (band.island()) {
					final int centre = (band.uMin() + band.uMax()) / 2;
					signRow(centre, LocalDir.MINUS_U);
					if (band.width() >= 4) {
						signRow(centre + 1, LocalDir.PLUS_U);
					}
				} else {
					signRow(backColumn(band), towardTrack(band));
				}
			}
		}

		/**
		 * Three signs side by side along v, centred on the platform's middle; shifted by 2 along v until the cells are free.
		 * Shifts run towards the platform end first and, if the end is reached, back towards its start.
		 */
		private void signRow(int u, LocalDir front) {
			final int start = length / 2 - 1;
			for (int v = start; v + 2 <= length - 1; v += 2) {
				if (placeRow(u, v, front)) {
					return;
				}
			}
			for (int v = start - 2; v >= 0; v -= 2) {
				if (placeRow(u, v, front)) {
					return;
				}
			}
		}

		private boolean placeRow(int u, int v, LocalDir front) {
			for (int i = 0; i < 3; i++) {
				if (taken(u, StationGeometry.WALK_Y, v + i)) {
					return false;
				}
			}
			for (int i = 0; i < 3; i++) {
				put(Role.NAME_SIGN, u, StationGeometry.WALK_Y, v + i, front);
			}
			return true;
		}

		StationPlan finish() {
			final List<PlannedBlock> blocks = new ArrayList<>(cells.values());
			blocks.sort(BUILD_ORDER);
			final List<Box> envelopes = new ArrayList<>();
			final List<Box> supports = new ArrayList<>();
			for (final TrackLayout.Track track : layout.tracks()) {
				envelopes.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, StationGeometry.PLATFORM_Y, layout.vMin(),
						track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, StationGeometry.ENVELOPE_HEIGHT - 1, layout.vMax()));
			}
			final List<Box> headroom = new ArrayList<>();
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				headroom.add(new Box(band.uMin(), StationGeometry.WALK_Y, 0, band.uMax(), StationGeometry.WALK_Y + StationGeometry.HEADROOM - 1,
						length - 1));
				supports.add(new Box(band.uMin(), -1, layout.vMin(), band.uMax(), -1, layout.vMax()));
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				supports.add(new Box(track.u(), -1, layout.vMin(), track.u(), -1, layout.vMax()));
			}
			if (site.building()) {
				supports.add(new Box(0, -1, site.vStart(), layout.buildingWidth() - 1, -1, site.vEnd()));
			}
			supports.addAll(extraSupport);
			return new StationPlan(spec, layout, blocks, envelopes, headroom, supports, palette.blockId(Role.FOUNDATION));
		}
	}
}
