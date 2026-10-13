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
	private static final String HANGING_PIDS = "aurelia_transit_architecture:hanging_platform_pids";
	private static final String HELP_POINT = "aurelia_transit_architecture:help_point";
	private static final String WAITING_ROOM_GLASS = "aurelia_transit_architecture:glass_wall";
	private static final String NUMBER_SIGN = "aurelia_transit_architecture:platform_number_sign";
	/** One colour per platform at a terminus gate line, in turn: a stripe on the floor and over the arcade, to follow to the gates. */
	private static final List<String> PLATFORM_COLOURS = List.of("aurelia_transit_architecture:metro_tile_blue",
			"aurelia_transit_architecture:metro_tile_green", "aurelia_transit_architecture:metro_tile_orange",
			"aurelia_transit_architecture:metro_tile_yellow");

	private static final Comparator<PlannedBlock> BUILD_ORDER = Comparator.comparingInt((PlannedBlock b) -> b.role().ordinal())
			.thenComparingInt(PlannedBlock::y).thenComparingInt(PlannedBlock::v).thenComparingInt(PlannedBlock::u);

	/** How far a grand station's clock tower rises above the building block's roof, to its cornice. */
	private static final int TOWER_HEIGHT = 11;
	/** The state of every stair in a pitched roof. */
	private static final String PITCH_STATE = "half=bottom,shape=straight";
	/** Copper for domes, and glass for giant arched windows (whole blocks, so the arch reads from the street). */
	private static final String DOME = "minecraft:oxidized_cut_copper";
	private static final String ARCH_GLASS = "aurelia_transit_architecture:curtain_wall_glass";
	/** A statue's base; its figure is a quartz pillar. */
	private static final String STATUE = "minecraft:chiseled_quartz_block";

	private StationPlanner() {
	}

	public static StationPlan plan(StationSpec spec) {
		final Planning planning = new Planning(spec);
		planning.escalators();
		planning.floors();
		planning.ramps();
		planning.railNodes();
		planning.trackBed();
		planning.bayEnds();
		planning.lifts();
		planning.building();
		planning.wings();
		planning.head();
		planning.footbridge();
		planning.columns();
		planning.canopyRoofs();
		planning.hallRoof();
		planning.lamps();
		planning.benchesAndBins();
		planning.interior();
		planning.headInterior();
		planning.platformEquipment();
		planning.nameSigns();
		planning.liftButtons();
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
	 * The u of each column line of a platform, ascending. A canopy has one line down the middle, or evenly spaced lines on a
	 * platform too wide for the roof to cantilever from one. A hall has one line next to
	 * the track edge of a side platform (plus one next to the back column when the platform is so wide that the beams would
	 * overhang the back by more than the cantilever limit), and one or two lines in an island, with lines between when the
	 * two are further apart than a beam spans.
	 */
	static List<Integer> columnLines(TrackLayout.PlatformBand band, RoofType roof) {
		final List<Integer> lines = new ArrayList<>();
		if (roof == RoofType.CANOPY) {
			// one line per stretch the roof can cantilever both ways from, each in the middle of its stretch
			final int reach = 2 * StationGeometry.MAX_CANTILEVER + 1;
			final int count = (band.width() + reach - 1) / reach;
			for (int i = 0; i < count; i++) {
				lines.add(band.uMin() + ((2 * i + 1) * band.width() - 1) / (2 * count));
			}
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
			// a platform so wide that one beam cannot span its two lines gets evenly spaced lines between them
			if (lines.size() == 2 && lines.get(1) - lines.get(0) > StationGeometry.MAX_BEAM_SPAN) {
				final int first = lines.get(0);
				final int span = lines.get(1) - first;
				final int bays = (span + StationGeometry.MAX_BEAM_SPAN - 1) / StationGeometry.MAX_BEAM_SPAN;
				for (int i = 1; i < bays; i++) {
					lines.add(i, first + span * i / bays);
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
		/** Lift call buttons, placed once everything else has its cells. */
		private final List<LiftButton> buttons = new ArrayList<>();

		/** A call button beside a lift door: in the shaft's front wall at {@code (u, y, v)}, with the landing on the -o side. */
		private record LiftButton(int u, int y, int v, int o, LocalDir in) {
		}
		private final List<Integer> columnV;
		private final List<Box> extraSupport = new ArrayList<>();
		private final StationLayout site;
		private List<Integer> hallRows;
		/** v of the building's platform-side doorways: no shed column stands in front of them. */
		private final java.util.Set<Integer> doorV = new java.util.HashSet<>();
		private final StationScale scale;
		/** Whole bays each wing runs along the platform from the building block's end wall; 0 for no wings. */
		private final int wingBays;

		Planning(StationSpec spec) {
			this.spec = spec;
			this.layout = StationGeometry.layout(spec);
			this.palette = StylePalette.of(spec);
			this.length = layout.platformLength();
			this.columnV = columnPositions(length);
			this.site = StationLayout.of(spec, layout);
			this.scale = spec.buildingScale();
			this.wingBays = site.building() ? wingBays(scale, length, site.vStart(), site.vEnd()) : 0;
		}

		/** Whole bays per wing so the block and both wings cover about the scale's share of the platform, the same both sides. */
		private static int wingBays(StationScale scale, int length, int vStart, int vEnd) {
			final int span = length * scale.wingPercent() / 100;
			if (span == 0) {
				return 0;
			}
			final int from = (length - span) / 2;
			final int to = from + span - 1;
			return Math.max(0, Math.min((vStart - from) / StationGeometry.BAY, (to - vEnd) / StationGeometry.BAY));
		}

		/** First and last v the building and its wings stand on. */
		private int buildingFrom() {
			return site.vStart() - wingBays * StationGeometry.BAY;
		}

		private int buildingTo() {
			return site.vEnd() + wingBays * StationGeometry.BAY;
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

		/** A two-high fixture (MTR's ticket machine, the kiosk): both halves or neither, the lower one at {@code y}. */
		private boolean tallFixture(int u, int y, int v, LocalDir front, String blockId) {
			if (taken(u, y, v) || taken(u, y + 1, v)) {
				return false;
			}
			put(Role.FIXTURE, u, y, v, front, blockId, "half=lower");
			put(Role.FIXTURE, u, y + 1, v, front, blockId, "half=upper");
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

		/** The platform in column u, or null. */
		private TrackLayout.PlatformBand bandAt(int u) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (u >= band.uMin() && u <= band.uMax()) {
					return band;
				}
			}
			return null;
		}

		/** A platform's colour at the gate line. */
		private static String platformColour(TrackLayout.PlatformBand band) {
			return PLATFORM_COLOURS.get((band.number() - 1) % PLATFORM_COLOURS.size());
		}

		/** A big platform number plate hung from the structure above; double-sided, so it reads from both ways. */
		private boolean numberSign(TrackLayout.PlatformBand band, int u, int y, int v, LocalDir front) {
			final Cell cell = new Cell(u, y, v);
			if (cells.containsKey(cell) || air.contains(cell)) {
				return false;
			}
			cells.put(cell, new PlannedBlock(u, y, v, Role.FIXTURE, NUMBER_SIGN, front, Integer.toString(band.number()), ""));
			return true;
		}

		void floors() {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (int v = band.vFrom(); v <= band.vTo(); v++) {
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
					put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, band.vFrom() - 2, LocalDir.MINUS_V);
					put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, band.vFrom() - 1, LocalDir.MINUS_V);
					if (!layout.terminus()) { // a terminus's platforms run level into its concourse
						put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, band.vTo() + 1, LocalDir.PLUS_V);
						put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, band.vTo() + 2, LocalDir.PLUS_V);
					}
				}
			}
		}

		void railNodes() {
			if (!spec.railNodes()) {
				return;
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformFrom(track), LocalDir.PLUS_V);
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformTo(track), LocalDir.PLUS_V);
			}
		}

		/**
		 * Ballast under every track's clearance (y = -1) for its whole run, with a glowstone strip in the row at the foot of
		 * each platform wall along that platform.
		 */
		void trackBed() {
			final TrackBed bed = spec.trackBed();
			if (bed == TrackBed.NONE) {
				return;
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				for (final int side : new int[]{-1, 1}) {
					if (side < 0 ? track.platformLeft() : track.platformRight()) {
						for (int v = layout.platformFrom(track); v <= layout.platformTo(track); v++) {
							put(Role.TRACK_BED, track.u() + side, -1, v, LocalDir.PLUS_V, TrackBed.LIGHT);
						}
					}
				}
				for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
					for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
					}
				}
			}
		}

		/**
		 * A buffer stop across each bay track's clearance at rail level, just past its platform's end, on ground the build makes
		 * sure of (a terminus has its own, on the head building's floor).
		 */
		void bayEnds() {
			for (final TrackLayout.Track track : layout.tracks()) {
				if (!layout.deadEnd(track)) {
					continue;
				}
				final int v = layout.trackEnd(track) + 1;
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					put(Role.BUFFER_STOP, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U);
				}
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
				final StationLayout.Lift lift = site.buildingLift(layout);
				final boolean clearOfLift = lift == null || centre + 1 < lift.vMin() || centre - 1 > lift.vMax();
				if (clearOfLift && (centre + 1 < site.innerStairMin() || centre - 1 > site.innerStairMax())) {
					for (int v = centre - 1; v <= centre + 1; v++) {
						for (int y = 1; y <= 3; y++) {
							keepClear(uP, y, v);
						}
						doorV.add(v);
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
					// the plinth under the walls, so the patterned floor does not show as a band along the base
					final boolean edge = u == uS || u == uP || v == vS || v == vE;
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : b.floor());
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
			// upper slab with light panels, leaving the stair well open; a panel that falls in the well moves to the slab beside
			// it, so the stair's landing is not left dark
			final Set<Cell> moved = new HashSet<>();
			for (int u = uA; u <= uB; u++) {
				for (int v = site.innerStairMin(); v <= site.innerStairMax(); v++) {
					if ((u + v) % 4 != 0) {
						continue;
					}
					for (final int[] c : new int[][]{{uA - 1, v}, {uB + 1, v}, {u, site.innerStairMin() - 1}, {u, site.innerStairMax() + 1}}) {
						if (c[0] > uS && c[0] < uP && c[1] > vS && c[1] < vE && !(c[0] >= uA && c[0] <= uB && c[1] >= site.innerStairMin()
								&& c[1] <= site.innerStairMax())) {
							moved.add(new Cell(c[0], StationGeometry.UPPER_FLOOR_Y, c[1]));
							break;
						}
					}
				}
			}
			for (int u = uS + 1; u <= uP - 1; u++) {
				for (int v = vS + 1; v <= vE - 1; v++) {
					if (u >= uA && u <= uB && v >= site.innerStairMin() && v <= site.innerStairMax()) {
						continue;
					}
					if ((u + v) % 4 == 0 || moved.contains(new Cell(u, StationGeometry.UPPER_FLOOR_Y, v))) {
						put(Role.LIGHT_PANEL, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
					} else {
						put(Role.FLOOR, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.upperFloor());
					}
				}
			}
			// storeys above the second (a city or grand station), the clock tower, then the roof and cornice parapet
			for (int storey = 0; storey < scale.extraStoreys(); storey++) {
				storey(StationGeometry.BUILDING_ROOF_Y + 4 * storey, uS, uP, vS, vE, true);
			}
			final int top = roofY();
			if (scale == StationScale.GRAND) {
				clockTower(top, uS, site.entranceV() - 2, LocalDir.PLUS_U);
			}
			roof(top, uS, uP, vS, vE);
			// inner stair flights (two columns wide)
			for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
				final int v = site.innerStairV(y);
				for (int u = uA; u <= uB; u++) {
					put(Role.STAIR, u, y, v, site.innerStairFront(), b.stair());
				}
			}
		}

		/** y of the building block's roof: above its two storeys and any storeys a city or grand station adds. */
		private int roofY() {
			return StationGeometry.BUILDING_ROOF_Y + 4 * scale.extraStoreys();
		}

		/**
		 * One more storey on a building box from {@code floorY}: the slab (a trim band round the edge, the floor with light
		 * panels inside, which light the storey below too) and three wall rows with a window row in the middle, pilasters
		 * every bay along the long walls. {@code endWindows} puts windows in the end walls too (a wing's end walls are shared).
		 */
		private void storey(int floorY, int uS, int uP, int vS, int vE, boolean endWindows) {
			final StylePalette.Building b = palette.building();
			for (int u = uS; u <= uP; u++) {
				for (int v = vS; v <= vE; v++) {
					final boolean edge = u == uS || u == uP || v == vS || v == vE;
					if (edge) {
						put(Role.WALL, u, floorY, v, outward(u, v, uS, uP, vS, vE), b.trim());
					} else {
						slabCell(u, floorY, v);
					}
				}
			}
			for (int y = floorY + 1; y <= floorY + 3; y++) {
				final boolean windowRow = y == floorY + 2;
				for (int v = vS; v <= vE; v++) {
					final boolean pilaster = Math.floorMod(v - vS, StationGeometry.BAY) == 0;
					final boolean window = windowRow && !pilaster && Math.abs(Math.floorMod(v - vS, StationGeometry.BAY) - 3) <= 1;
					for (final int u : new int[]{uS, uP}) {
						final LocalDir front = u == uS ? LocalDir.MINUS_U : LocalDir.PLUS_U;
						if (window) {
							put(Role.WINDOW, u, y, v, front, b.window());
						} else {
							put(Role.WALL, u, y, v, front, pilaster ? b.trim() : b.wall());
						}
					}
				}
				for (int u = uS + 1; u <= uP - 1; u++) {
					final boolean window = endWindows && windowRow && u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0;
					for (final int v : new int[]{vS, vE}) {
						final LocalDir front = v == vS ? LocalDir.MINUS_V : LocalDir.PLUS_V;
						put(window ? Role.WINDOW : Role.WALL, u, y, v, front, window ? b.window() : (u - uS) % 3 == 0 ? b.trim() : b.wall());
					}
				}
			}
		}

		/** A floor slab cell: a light panel on every fourth diagonal, so no floor cell above or below is more than 2 from one. */
		private void slabCell(int u, int y, int v) {
			final StylePalette.Building b = palette.building();
			if (Math.floorMod(u + v, 4) == 0) {
				put(Role.LIGHT_PANEL, u, y, v, LocalDir.PLUS_V, b.light());
			} else {
				put(Role.FLOOR, u, y, v, LocalDir.PLUS_V, b.upperFloor());
			}
		}

		private static LocalDir outward(int u, int v, int uS, int uP, int vS, int vE) {
			if (u == uS) {
				return LocalDir.MINUS_U;
			}
			if (u == uP) {
				return LocalDir.PLUS_U;
			}
			return v == vS ? LocalDir.MINUS_V : LocalDir.PLUS_V;
		}

		/**
		 * The roof of a building box at {@code top}: a flat roof with a cornice parapet, or for a masonry look at city or grand
		 * scale a hipped roof of stairs inside the cornice, over an attic lit through its floor.
		 */
		private void roof(int top, int uS, int uP, int vS, int vE) {
			roof(top, uS, uP, vS, vE, pitchedRoof());
		}

		/** {@link #roof(int, int, int, int, int)} with the given pitched-roof blocks, or flat for null. */
		private void roof(int top, int uS, int uP, int vS, int vE, String[] pitched) {
			final StylePalette.Building b = palette.building();
			for (int u = uS; u <= uP; u++) {
				for (int v = vS; v <= vE; v++) {
					final boolean edge = u == uS || u == uP || v == vS || v == vE;
					if (pitched != null && !edge && Math.floorMod(u + v, 4) == 0) {
						put(Role.LIGHT_PANEL, u, top, v, LocalDir.PLUS_V, b.light());
					} else {
						put(Role.BUILDING_ROOF, u, top, v, LocalDir.PLUS_V, b.roof());
					}
				}
			}
			for (int v = vS; v <= vE; v++) {
				put(Role.BUILDING_ROOF, uS, top + 1, v, LocalDir.MINUS_U, b.cornice());
				put(Role.BUILDING_ROOF, uP, top + 1, v, LocalDir.PLUS_U, b.cornice());
			}
			for (int u = uS; u <= uP; u++) {
				put(Role.BUILDING_ROOF, u, top + 1, vS, LocalDir.MINUS_V, b.cornice());
				put(Role.BUILDING_ROOF, u, top + 1, vE, LocalDir.PLUS_V, b.cornice());
			}
			if (pitched == null) {
				return;
			}
			hipped(uS + 1, uP - 1, vS + 1, vE - 1, top + 1, pitched[0], pitched[1], Integer.MAX_VALUE);
		}

		/**
		 * A hipped roof over u0..u1 x v0..v1 from {@code y}: rings of stairs stepping in one cell and up one each, every stair
		 * rising towards the middle, at most {@code rings} rings, then a full-block top. Each ring above the first rests on a
		 * full block, the roof's thickness, so every tile touches the one below it face to face.
		 */
		private void hipped(int u0, int u1, int v0, int v1, int y, String stair, String solid, int rings) {
			for (int k = 0; ; k++) {
				final int a0 = u0 + k;
				final int a1 = u1 - k;
				final int b0 = v0 + k;
				final int b1 = v1 - k;
				final int at = y + k;
				if (a0 >= a1 || b0 >= b1 || k == rings) {
					for (int u = a0; u <= a1; u++) {
						for (int v = b0; v <= b1; v++) {
							if (k > 0) {
								put(Role.BUILDING_ROOF, u, at - 1, v, LocalDir.PLUS_V, solid);
							}
							put(Role.BUILDING_ROOF, u, at, v, LocalDir.PLUS_V, solid);
						}
					}
					return;
				}
				for (int u = a0; u <= a1; u++) {
					for (int v = b0; v <= b1; v++) {
						if (u != a0 && u != a1 && v != b0 && v != b1) {
							continue;
						}
						if (k > 0) {
							put(Role.BUILDING_ROOF, u, at - 1, v, LocalDir.PLUS_V, solid);
						}
						final LocalDir up = v == b0 ? LocalDir.PLUS_V : v == b1 ? LocalDir.MINUS_V : u == a0 ? LocalDir.PLUS_U : LocalDir.MINUS_U;
						put(Role.BUILDING_ROOF, u, at, v, up, stair, PITCH_STATE);
					}
				}
			}
		}

		/** Stairs and the matching full block for a pitched roof, or null for a flat one (modern and concrete looks, compact). */
		private String[] pitchedRoof() {
			if (scale == StationScale.COMPACT) {
				return null;
			}
			return switch (spec.building()) {
				case AMSTERDAM, ANTWERP, BEAUX_ARTS -> new String[]{"minecraft:oxidized_cut_copper_stairs", "minecraft:oxidized_cut_copper"};
				case GOTHIC_BRICK, SANDSTONE, CLINKER, TOKYO, ZURICH -> new String[]{"minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles"};
				case NONE, MODERN, CONCRETE, PARIS, MILAN, BERLIN, HONG_KONG, BEIJING -> null;
			};
		}

		/**
		 * A grand station's clock tower over the entrance, standing on the street wall: a hollow square shaft five cells across
		 * (u0..u0+4, v0..v0+4) rising {@link #TOWER_HEIGHT} above the roof, louvred openings, a clock face on every side but the
		 * one facing {@code inward} (into the building), a cornice and a pyramid cap with a finial. Planned before the roof so the
		 * roof leaves the tower open.
		 */
		private void clockTower(int top, int u0, int v0, LocalDir inward) {
			tower(top, u0, v0, TOWER_HEIGHT, inward, Cap.PYRAMID, true, false);
		}

		/** How a tower ends: a pyramid, a pyramid with a tall spire, a copper dome, or flat (a glass tower). */
		private enum Cap {
			PYRAMID, SPIRE, DOME, FLAT
		}

		/**
		 * A tower five cells across standing on the roof at {@code top}, {@code height} high, like {@link #clockTower}: clock faces
		 * when {@code clock}, glass between the corners and bands when {@code glass} (a modern glass tower), and its {@code cap}.
		 */
		private void tower(int top, int u0, int v0, int height, LocalDir inward, Cap cap, boolean clock, boolean glass) {
			final StylePalette.Building b = palette.building();
			final int u1 = u0 + 4;
			final int v1 = v0 + 4;
			final int vC = v0 + 2;
			final int crown = top + height;
			for (int u = u0 + 1; u <= u1 - 1; u++) {
				for (int v = v0 + 1; v <= v1 - 1; v++) {
					// the roof under the tower is lit so the closed shaft stays dark-free; the rest of the shaft stays empty
					if (u == u0 + 2 && v == vC) {
						put(Role.LIGHT_PANEL, u, top, v, LocalDir.PLUS_V, b.light());
					}
					for (int y = top + 1; y <= crown; y++) {
						keepClear(u, y, v);
					}
				}
			}
			for (int y = top + 1; y <= crown; y++) {
				final boolean band = y == top + 1 || y == crown;
				final boolean louvre = y >= top + 3 && y <= top + 5;
				for (int u = u0; u <= u1; u++) {
					for (int v = v0; v <= v1; v++) {
						final boolean ring = u == u0 || u == u1 || v == v0 || v == v1;
						if (!ring) {
							continue;
						}
						final boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
						final boolean middle = u == u0 + 2 || v == vC;
						final LocalDir front = outward(u, v, u0, u1, v0, v1);
						if (band || corner) {
							put(Role.WALL, u, y, v, front, b.trim());
						} else if (glass || louvre && middle) {
							put(Role.WINDOW, u, y, v, front, b.window());
						} else {
							put(Role.WALL, u, y, v, front, b.wall());
						}
					}
				}
			}
			// clock faces on the outer sides, one row under the cornice
			final int clockY = crown - 2;
			final String clockFace = ata("amsterdam_clock_face_panel");
			for (final LocalDir side : LocalDir.values()) {
				if (!clock || side == inward) {
					continue;
				}
				switch (side) {
					case MINUS_U -> put(Role.FIXTURE, u0 - 1, clockY, vC, side, clockFace);
					case PLUS_U -> put(Role.FIXTURE, u1 + 1, clockY, vC, side, clockFace);
					case MINUS_V -> put(Role.FIXTURE, u0 + 2, clockY, v0 - 1, side, clockFace);
					case PLUS_V -> put(Role.FIXTURE, u0 + 2, clockY, v1 + 1, side, clockFace);
				}
			}
			// the cap: a closed slab, then a pyramid of two rings and a point, then the finial
			final String[] pitched = pitchedRoof();
			final String stair = pitched != null ? pitched[0] : "minecraft:stone_brick_stairs";
			final String solid = pitched != null ? pitched[1] : b.trim();
			for (int u = u0 + 1; u <= u1 - 1; u++) {
				for (int v = v0 + 1; v <= v1 - 1; v++) {
					air.remove(new Cell(u, crown, v));
					put(Role.BUILDING_ROOF, u, crown, v, LocalDir.PLUS_V, b.roof());
				}
			}
			switch (cap) {
				case PYRAMID -> {
					hipped(u0, u1, v0, v1, crown + 1, stair, solid, 2);
					put(Role.BUILDING_ROOF, u0 + 2, crown + 4, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
				}
				case SPIRE -> {
					hipped(u0, u1, v0, v1, crown + 1, stair, solid, 2);
					for (int y = crown + 4; y <= crown + 6; y++) {
						put(Role.BUILDING_ROOF, u0 + 2, y, vC, LocalDir.PLUS_V, solid);
					}
					put(Role.BUILDING_ROOF, u0 + 2, crown + 7, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
				}
				case DOME -> {
					// a round copper dome: the ring without its corners, the middle three by three, then the top and a finial
					for (int u = u0; u <= u1; u++) {
						for (int v = v0; v <= v1; v++) {
							final boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
							if (!corner) {
								put(Role.BUILDING_ROOF, u, crown + 1, v, LocalDir.PLUS_V, DOME);
							}
							if (u > u0 && u < u1 && v > v0 && v < v1) {
								put(Role.BUILDING_ROOF, u, crown + 2, v, LocalDir.PLUS_V, DOME);
							}
						}
					}
					put(Role.BUILDING_ROOF, u0 + 2, crown + 3, vC, LocalDir.PLUS_V, DOME);
					put(Role.BUILDING_ROOF, u0 + 2, crown + 4, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
				}
				case FLAT -> {
					// a parapet round the flat top
					for (int u = u0; u <= u1; u++) {
						for (int v = v0; v <= v1; v++) {
							if (u == u0 || u == u1 || v == v0 || v == v1) {
								put(Role.BUILDING_ROOF, u, crown + 1, v, outward(u, v, u0, u1, v0, v1), b.cornice());
							}
						}
					}
				}
			}
		}

		/**
		 * The wings of a city or grand station: lower blocks along the platform on both sides of the building block, whole bays
		 * long, two storeys with the same cornice height as a compact building. Their ground floors are waiting rooms on the
		 * paid side, opening onto platform 1 through a doorway in every bay, with benches along the street wall; the upper
		 * storeys are closed. A grand station's wings end in pavilions a storey higher. The block's own end walls separate them.
		 */
		void wings() {
			if (wingBays == 0) {
				return;
			}
			final StylePalette.Building b = palette.building();
			final int uS = StationLayout.streetWall();
			final int uP = site.platformWall(layout);
			final boolean pavilions = scale == StationScale.GRAND && wingBays >= 2;
			for (final int side : new int[]{-1, 1}) {
				final int inner = side < 0 ? site.vStart() : site.vEnd();
				final int end = inner + side * wingBays * StationGeometry.BAY;
				final int pavilionFrom = inner + side * (wingBays - 1) * StationGeometry.BAY;
				for (int j = 0; j < wingBays; j++) {
					final int centre = inner + side * (StationGeometry.BAY * j + 3);
					for (int v = centre - 1; v <= centre + 1; v++) {
						for (int y = 1; y <= 3; y++) {
							keepClear(uP, y, v);
						}
						doorV.add(v);
					}
				}
				final int lo = Math.min(inner + side, end);
				final int hi = Math.max(inner + side, end);
				// the wing's own end wall and, before a pavilion, the cross wall above the wing roof
				final int pavilionRoof = StationGeometry.BUILDING_ROOF_Y + 4;
				for (int u = uS + 1; u <= uP - 1; u++) {
					for (int y = 1; y < (pavilions ? pavilionRoof : StationGeometry.BUILDING_ROOF_Y); y++) {
						final boolean band = y == StationGeometry.UPPER_FLOOR_Y || y == StationGeometry.BUILDING_ROOF_Y;
						final boolean window = !band && (y == 2 || y == 3 || y == StationGeometry.UPPER_WALK_Y + 1 || y == StationGeometry.BUILDING_ROOF_Y + 2)
								&& u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0;
						final String id = y == 1 ? b.plinth() : band || (u - uS) % 3 == 0 ? b.trim() : b.wall();
						put(window ? Role.WINDOW : Role.WALL, u, y, end, side < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V, window ? b.window() : id);
					}
					if (pavilions) {
						for (int y = StationGeometry.BUILDING_ROOF_Y + 1; y < pavilionRoof; y++) {
							put(Role.WALL, u, y, pavilionFrom, side < 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V, b.wall());
						}
					}
				}
				for (int v = lo; v <= hi; v++) {
					final int along = Math.abs(v - inner);
					final boolean pilaster = along % StationGeometry.BAY == 0;
					final boolean inPavilion = pavilions && along >= Math.abs(pavilionFrom - inner);
					final int roofAt = inPavilion ? pavilionRoof : StationGeometry.BUILDING_ROOF_Y;
					final boolean windowBay = !pilaster && Math.abs(along % StationGeometry.BAY - 3) <= 1;
					for (int u = uS; u <= uP; u++) {
						final boolean edge = u == uS || u == uP || v == end;
						put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : b.floor());
					}
					for (final int u : new int[]{uS, uP}) {
						final LocalDir front = u == uS ? LocalDir.MINUS_U : LocalDir.PLUS_U;
						for (int y = 1; y < roofAt; y++) {
							final boolean band = y == StationGeometry.UPPER_FLOOR_Y || y == StationGeometry.BUILDING_ROOF_Y;
							final boolean windowRow = y == 2 || y == 3 || y == StationGeometry.UPPER_WALK_Y + 1 || y == StationGeometry.BUILDING_ROOF_Y + 2;
							if (windowBay && windowRow && !band) {
								put(Role.WINDOW, u, y, v, front, b.window());
							} else {
								put(Role.WALL, u, y, v, front, band || pilaster ? b.trim() : y == 1 ? b.plinth() : b.wall());
							}
						}
					}
					for (int u = uS + 1; u <= uP - 1; u++) {
						slabCell(u, StationGeometry.UPPER_FLOOR_Y, v);
						if (inPavilion) {
							slabCell(u, StationGeometry.BUILDING_ROOF_Y, v);
						}
					}
					if (!inPavilion) {
						for (int u = uS; u <= uP; u++) {
							put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.roof());
						}
						put(Role.BUILDING_ROOF, uS, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.MINUS_U, b.cornice());
						put(Role.BUILDING_ROOF, uP, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.PLUS_U, b.cornice());
					}
					// benches against the street wall, either side of each bay's middle
					if (!pilaster && Math.abs(along % StationGeometry.BAY - 3) == 1) {
						put(Role.BENCH, uS + 1, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, b.bench());
					}
				}
				if (pavilions) {
					roof(pavilionRoof, uS, uP, Math.min(pavilionFrom, end), Math.max(pavilionFrom, end));
				} else {
					for (int u = uS; u <= uP; u++) {
						put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y + 1, end, side < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.cornice());
					}
				}
			}
		}

		/**
		 * Lift shafts: one per footbridge platform wide enough for a two-column stair, beside the corridor on the side away from
		 * the stair, and one in the station building beside the inner stair. Planned before the building and the footbridge so
		 * the slab, deck and corridor walls leave the shafts open; MTR turns each column of stops into a lift when the build is
		 * done.
		 */
		void lifts() {
			final StylePalette.Building b = palette.building();
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final StationLayout.Lift lift = site.platformLift(band, layout);
				if (lift != null) {
					shaft(lift, b.bridgeWall(), true, b.bridgeRoof());
				}
			}
			final StationLayout.Lift hall = site.buildingLift(layout);
			if (hall != null) {
				// the building's own platform wall closes the shaft's far side, and its roof covers it
				shaft(hall, b.interiorWall(), false, null);
			}
		}

		/**
		 * Lift call buttons, at hand height beside each door. MTR's button is a thin plate on its block's front side, meant to
		 * stand in front of a wall; in the shaft's own wall it would sit flush with the car, which clips it. So it stands on the
		 * landing against the wall, unless that cell is taken (a corridor's glass wall), where it stays in the shaft wall.
		 */
		void liftButtons() {
			for (final LiftButton button : buttons) {
				final int landing = button.v() - button.o();
				if (!taken(button.u(), button.y(), landing)) {
					put(Role.LIFT, button.u(), button.y(), landing, button.in(), StylePalette.MTR_LIFT_BUTTONS, "unlocked=true");
				} else {
					final Cell wall = new Cell(button.u(), button.y(), button.v());
					cells.put(wall, new PlannedBlock(button.u(), button.y(), button.v(), Role.LIFT, StylePalette.MTR_LIFT_BUTTONS, button.in(), "",
							"unlocked=true"));
				}
			}
		}

		/**
		 * One shaft: a track column against the back wall with a stop at the walk level and at the upper walk level, a pair of
		 * landing doors at each stop with a call button beside them, walls, and a roof unless {@code roofId} is null. The car
		 * space stays empty from the walk level to the roof.
		 */
		private void shaft(StationLayout.Lift lift, String wallId, boolean farWall, String roofId) {
			final int o = lift.out();
			final LocalDir in = o > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
			final int c0 = lift.c0();
			final int c1 = c0 + 1;
			final int vD = lift.doorV();
			final int vBack = vD + 3 * o;
			// MTR centres the car on the track cell's corner; the track goes in the column whose clockwise neighbour (seen
			// facing the back wall) is the car's other column, so the car lies in front of it
			final int cT = in == LocalDir.PLUS_V ? c0 : c1;
			final int low = StationGeometry.WALK_Y;
			final int high = StationGeometry.UPPER_WALK_Y;
			final int roof = StationGeometry.BUILDING_ROOF_Y;
			for (int y = low; y <= high + 1; y++) {
				put(Role.LIFT, cT, y, vD + 2 * o, in, y == low || y == high ? StylePalette.MTR_LIFT_FLOOR : StylePalette.MTR_LIFT_TRACK);
			}
			for (int c = c0; c <= c1; c++) {
				for (int v = vD + o; v != vBack; v += o) {
					for (int y = low; y < roof; y++) {
						keepClear(c, y, v);
					}
				}
				for (int y = low; y <= low + 2; y++) {
					keepClear(c, y, vD - o);
				}
				// MTR draws the car's floor level with the stop; a floor block there flickers through it, so the car rests in a pit
				for (int v = vD + o; v != vBack; v += o) {
					final Cell pit = new Cell(c, low - 1, v);
					cells.remove(pit);
					air.add(pit);
				}
			}
			for (final int stop : new int[]{low, high}) {
				for (int c = c0; c <= c1; c++) {
					final String side = c == cT ? "side=left" : "side=right";
					put(Role.LIFT, c, stop, vD, in, StylePalette.MTR_LIFT_DOOR, "half=lower," + side + ",unlocked=true");
					put(Role.LIFT, c, stop + 1, vD, in, StylePalette.MTR_LIFT_DOOR, "half=upper," + side + ",unlocked=true");
				}
				buttons.add(new LiftButton(c0 - 1, stop + 1, vD, o, in));
			}
			final int cMax = farWall ? c1 + 1 : c1;
			for (int y = low; y < roof; y++) {
				for (int c = c0 - 1; c <= cMax; c++) {
					// the door row's floor at the upper level is the deck or the slab
					if (y != StationGeometry.UPPER_FLOOR_Y) {
						put(Role.WALL, c, y, vD, in.opposite(), wallId);
					}
					put(Role.WALL, c, y, vBack, in, wallId);
				}
				for (int v = vD + o; v != vBack; v += o) {
					put(Role.WALL, c0 - 1, y, v, LocalDir.MINUS_U, wallId);
					if (farWall) {
						put(Role.WALL, c1 + 1, y, v, LocalDir.PLUS_U, wallId);
					}
				}
			}
			if (roofId != null) {
				for (int c = c0 - 1; c <= c1 + 1; c++) {
					for (int v = vD + o; v != vBack + o; v += o) {
						put(Role.BUILDING_ROOF, c, roof, v, LocalDir.PLUS_V, roofId);
					}
				}
			}
		}

		/** True when the platform ends at a terminus head's gate line, which carries its big number. */
		private boolean gateNumbered(TrackLayout.PlatformBand band) {
			return layout.terminus() && band.uMax() > layout.headFrom() && band.uMin() < layout.headTo();
		}

		/** The u of a terminus head building's street entrance (the middle of the 3-wide doorway). */
		private int headDoor() {
			return (layout.headFrom() + layout.headTo() + 1) / 2;
		}

		/**
		 * A terminus head building across the track ends (v = L .. L+11, the full width, or only a hub's platforms): the floor
		 * runs level from the platform ends; buffer stops close every bay track at v = L; the platform side is open under a
		 * lintel with clerestory windows; side and street walls carry windows and a trim band; a 3-wide street entrance with
		 * ramps outside; a flat roof with light panels and a cornice. The gate line and fittings are in {@link #headInterior()}.
		 */
		void head() {
			if (!layout.terminus()) {
				return;
			}
			final StylePalette.Building b = palette.building();
			// a hub's head building spans its platforms only; the outer tracks run on past its side walls
			final int u0 = layout.headFrom();
			final int u1 = layout.headTo();
			final int vL = length;
			final int vE = layout.headEnd();
			final int door = headDoor();
			for (int u = door - 1; u <= door + 1; u++) {
				for (int y = 1; y <= 3; y++) {
					keepClear(u, y, vE);
					keepClear(u, y, vE + 1);
					keepClear(u, y, vE + 2);
				}
			}
			if (scale != StationScale.COMPACT) {
				headFront(u0, u1, vE, door);
			}
			for (int u = u0; u <= u1; u++) {
				final TrackLayout.PlatformBand band = bandAt(u);
				for (int v = vL; v <= vE; v++) {
					final boolean edge = u == u0 || u == u1 || v == vE;
					// each platform's colour runs across the floor either side of its gates
					final boolean stripe = !edge && band != null && (v == vL + 1 || v == vL + 3);
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : stripe ? platformColour(band) : b.floor());
				}
			}
			// buffer stops across each track's clearance, just past its last rail node
			for (final TrackLayout.Track track : layout.tracks()) {
				if (!layout.bay(track)) {
					continue;
				}
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					put(Role.BUFFER_STOP, u, StationGeometry.WALK_Y, vL, LocalDir.PLUS_U);
				}
			}
			final int top = StationGeometry.BUILDING_ROOF_Y - 1;
			// side walls
			for (int v = vL; v <= vE; v++) {
				final boolean pilaster = (v - vL) % 3 == 0;
				for (int y = 1; y <= top; y++) {
					final String id = y == 1 ? (pilaster ? b.trim() : b.plinth()) : y == StationGeometry.UPPER_FLOOR_Y || pilaster ? b.trim() : b.wall();
					final boolean window = !pilaster && (y == 2 || y == 3 || y == 6 || y == 7);
					put(window ? Role.WINDOW : Role.WALL, u0, y, v, LocalDir.MINUS_U, window ? b.window() : id);
					put(window ? Role.WINDOW : Role.WALL, u1, y, v, LocalDir.PLUS_U, window ? b.window() : id);
				}
			}
			// street wall with the entrance, and the platform-side lintel and clerestory over the open arcade
			for (int u = u0 + 1; u < u1; u++) {
				final boolean pilaster = (u - u0) % 3 == 0;
				for (int y = 1; y <= top; y++) {
					final String id = y == 1 ? (pilaster ? b.trim() : b.plinth()) : y == StationGeometry.UPPER_FLOOR_Y || pilaster ? b.trim() : b.wall();
					final boolean window = !pilaster && (y == 6 || y == 7);
					// ground-floor windows either side of the entrance
					final boolean street = window || !pilaster && (y == 2 || y == 3) && Math.abs(u - door) > 2;
					put(street ? Role.WINDOW : Role.WALL, u, y, vE, LocalDir.PLUS_V, street ? b.window() : id);
					if (y >= StationGeometry.UPPER_FLOOR_Y) {
						// the lintel over each platform carries its colour, with its number hung below
						final TrackLayout.PlatformBand band = bandAt(u);
						final String lintel = y == StationGeometry.UPPER_FLOOR_Y && band != null ? platformColour(band) : id;
						put(window ? Role.WINDOW : Role.WALL, u, y, vL, LocalDir.MINUS_V, window ? b.window() : lintel);
					}
				}
			}
			// entrance ramps outside the street wall
			for (int u = door - 1; u <= door + 1; u++) {
				put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, vE + 1, LocalDir.PLUS_V);
				put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, vE + 2, LocalDir.PLUS_V);
			}
			if (scale != StationScale.COMPACT) {
				// a city or grand head building: storeys over the whole concourse (its ceiling is their first floor), the roof,
				// and a grand one's clock tower over the entrance
				for (int storey = 0; storey < scale.extraStoreys(); storey++) {
					storey(StationGeometry.BUILDING_ROOF_Y + 4 * storey, u0, u1, vL, vE, true);
				}
				headCrown(u0, u1, vL, vE, door);
				return;
			}
			// a ceiling with light panels, covered by a flat roof flush with the cornice (the panels' tops would otherwise show
			// from above as a checkerboard)
			for (int u = u0; u <= u1; u++) {
				for (int v = vL; v <= vE; v++) {
					final boolean inner = u > u0 && u < u1 && v > vL && v < vE;
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, inner && (u + v) % 4 == 0 ? b.light() : b.roof());
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.PLUS_V, inner ? b.roof() : b.cornice());
				}
			}
		}

		/**
		 * The street front of a city or grand head building, planned before its plain walls so it takes their cells: giant
		 * arched windows (one, two or three), tall round-headed windows between columns, or a glass curtain wall, by the look's
		 * {@link BuildingStyle.Facade}. Brick looks keep their banded walls; their towers come with the roof.
		 */
		private void headFront(int u0, int u1, int vE, int door) {
			final StylePalette.Building b = palette.building();
			final BuildingStyle look = spec.building();
			final int width = u1 - u0 + 1;
			// windows stop two rows under the roof, so the cornice band runs unbroken over them
			final int top = roofY() - 2;
			switch (look.facade()) {
				case BRICK_TOWERS -> {
				}
				case ARCHED_GLASS -> {
					final int arches = look == BuildingStyle.SANDSTONE && width >= 40 ? 3 : look == BuildingStyle.CLINKER && width >= 28 ? 2 : 1;
					if (arches == 1) {
						giantArch(door, Math.min(top - 4, Math.max(2, width / 6)), top, vE, u0, u1);
					} else if (arches == 2) {
						final int h = Math.min(top - 4, width / 8);
						giantArch(u0 + width / 4, h, top, vE, u0, u1);
						giantArch(u1 - width / 4, h, top, vE, u0, u1);
					} else {
						final int h = Math.min(top - 4, width / 8);
						for (final int c : new int[]{door - width / 3, door, door + width / 3}) {
							giantArch(c, h, top, vE, u0, u1);
						}
					}
				}
				case SCULPTED_ATTIC -> {
					if (look == BuildingStyle.PARIS || width < 30) {
						// one great window over the entrance between pairs of columns
						final int h = Math.min(top - 4, Math.max(2, width / 6));
						giantArch(door, h, top, vE, u0, u1);
						columnPair(door - h - 1, -1, top, vE, u0, u1);
						columnPair(door + h + 1, 1, top, vE, u0, u1);
					} else {
						// three tall round-headed windows, with paired columns between and beside them
						final int h = 3;
						for (final int c : new int[]{door - 10, door, door + 10}) {
							giantArch(c, h, top, vE, u0, u1);
							columnPair(c - h - 1, -1, top, vE, u0, u1);
							columnPair(c + h + 1, 1, top, vE, u0, u1);
						}
					}
				}
				case GLASS_MODERN -> {
					// a curtain wall: glass between mullions every three cells and a band at every floor
					for (int u = u0 + 1; u < u1; u++) {
						for (int y = 1; y < roofY(); y++) {
							final boolean floorLine = y == StationGeometry.UPPER_FLOOR_Y
									|| y >= StationGeometry.BUILDING_ROOF_Y && (y - StationGeometry.BUILDING_ROOF_Y) % 4 == 0;
							if (floorLine || (u - u0) % 3 == 0) {
								put(Role.WALL, u, y, vE, LocalDir.PLUS_V, b.trim());
							} else {
								put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
							}
						}
					}
				}
			}
		}

		/**
		 * A giant round-headed window in the street wall (v = {@code vE}), centred on {@code cu}, {@code h} cells each side of
		 * the middle, its crown at {@code top}: glass with glazing bars every three cells and at the first floor, framed by a
		 * ring of stone. Left out where it would not fit between the side walls.
		 */
		private void giantArch(int cu, int h, int top, int vE, int u0, int u1) {
			if (h < 2 || cu - h <= u0 || cu + h >= u1) {
				return;
			}
			final StylePalette.Building b = palette.building();
			final int spring = top - h;
			// the ring and glazing bars in a stone that stands out from the wall
			final String frame = switch (spec.building()) {
				case SANDSTONE -> ata("antwerp_ornate_limestone");
				case CLINKER, ANTWERP, ZURICH -> ata("frankfurt_sandstone_rusticated");
				default -> b.trim();
			};
			for (int u = cu - h; u <= cu + h; u++) {
				final int du = Math.abs(u - cu);
				for (int y = 1; y <= top; y++) {
					final boolean inside;
					final boolean ring;
					if (y <= spring) {
						inside = du < h;
						ring = du == h;
					} else {
						final double d = Math.hypot(du, y - spring);
						inside = d < h - 0.5;
						ring = !inside && d < h + 0.5;
					}
					final boolean bar = y == StationGeometry.UPPER_FLOOR_Y || y > 3 && du % 3 == 0;
					if (ring || inside && bar) {
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, frame);
					} else if (inside) {
						put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, ARCH_GLASS);
					}
				}
			}
		}

		/** Two columns side by side in the street wall from the ground to {@code top}, from {@code u} going {@code dir}. */
		private void columnPair(int u, int dir, int top, int vE, int u0, int u1) {
			for (int k = 0; k < 2; k++) {
				final int at = u + dir * k;
				if (at <= u0 || at >= u1) {
					continue;
				}
				for (int y = 1; y <= top; y++) {
					put(Role.WALL, at, y, vE, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
				}
			}
		}

		/**
		 * The top of a city or grand head building, by the look's {@link BuildingStyle.Facade}: clock towers, gables and domed
		 * pavilions over a hipped roof (brick), a tower or a statue group over the arches (arched), a raised attic with a clock
		 * and statues on a flat roof (Beaux-Arts), or a flat roof with a street canopy, a glass tower, a wave or a deep
		 * overhang (modern). Towers and attics come first, so the roof leaves them open.
		 */
		private void headCrown(int u0, int u1, int vL, int vE, int door) {
			final BuildingStyle look = spec.building();
			final int top = roofY();
			final int width = u1 - u0 + 1;
			final boolean grand = scale == StationScale.GRAND;
			final int towerV = vE - 4;
			switch (look.facade()) {
				case BRICK_TOWERS -> {
					if (look == BuildingStyle.TOKYO && width >= 20) {
						// domed corner pavilions, and over the entrance a domed clock tower or a gable
						tower(top, u0, towerV, 5, LocalDir.MINUS_V, Cap.DOME, false, false);
						tower(top, u1 - 4, towerV, 5, LocalDir.MINUS_V, Cap.DOME, false, false);
						if (grand) {
							tower(top, door - 2, towerV, 7, LocalDir.MINUS_V, Cap.DOME, true, false);
						} else {
							gable(door, top, vE);
						}
					} else if (look == BuildingStyle.AMSTERDAM && grand && width >= 30) {
						// twin clock towers at a quarter and three quarters of the front, a gable between them
						tower(top, u0 + width / 4 - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
						tower(top, u1 - width / 4 - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
						gable(door, top, vE);
					} else if (grand) {
						final boolean gothic = look == BuildingStyle.GOTHIC_BRICK;
						tower(top, door - 2, towerV, gothic ? TOWER_HEIGHT + 4 : TOWER_HEIGHT, LocalDir.MINUS_V, gothic ? Cap.SPIRE : Cap.PYRAMID, true, false);
					} else {
						gable(door, top, vE);
					}
					roof(top, u0, u1, vL, vE);
				}
				case ARCHED_GLASS -> {
					if (look == BuildingStyle.ZURICH) {
						attic(door, Math.max(3, Math.min(6, width / 8)), 2, top, vE, u0, u1, true);
					} else if (grand && look == BuildingStyle.CLINKER) {
						// the clock tower near one end of the front, a cell in so its side clock stays over the building
						tower(top, u1 - 5, towerV, TOWER_HEIGHT + 2, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
					} else if (grand) {
						tower(top, door - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, look == BuildingStyle.ANTWERP ? Cap.DOME : Cap.PYRAMID, true, false);
					} else {
						gable(door, top, vE);
					}
					roof(top, u0, u1, vL, vE);
				}
				case SCULPTED_ATTIC -> {
					final int half = Math.max(3, Math.min(9, width / 6));
					if (look == BuildingStyle.MILAN && grand) {
						// a stepped centre: a wide attic and a taller narrow one on it
						attic(door, half, 3, top, vE, u0, u1, false);
						attic(door, Math.max(2, half / 2), 3, top + 4, vE, u0, u1, true);
					} else {
						attic(door, half, look == BuildingStyle.PARIS ? 2 : 3, top, vE, u0, u1, true);
					}
					if (look == BuildingStyle.PARIS) {
						// statues along the parapet
						for (int u = u0 + 3; u <= u1 - 3; u += 6) {
							statue(u, top + 2, vE);
						}
					}
					roof(top, u0, u1, vL, vE, null);
				}
				case GLASS_MODERN -> {
					if (look == BuildingStyle.HONG_KONG) {
						waveRoof(top, u0, u1, vL, vE);
					}
					if (look == BuildingStyle.BERLIN && grand && width >= 20) {
						tower(top, u1 - 4, towerV, TOWER_HEIGHT + 6, LocalDir.MINUS_V, Cap.FLAT, false, true);
					}
					roof(top, u0, u1, vL, vE);
					if (look == BuildingStyle.CONCRETE) {
						// a deep concrete canopy over the street, at the first floor
						overhang(StationGeometry.UPPER_FLOOR_Y, u0, u1, vE, false);
					}
					if (look == BuildingStyle.BEIJING) {
						overhang(top, u0, u1, vE, true);
					}
				}
			}
		}

		/** A stepped gable on the street wall over the entrance, seven cells wide at the cornice, with a clock face. */
		private void gable(int door, int top, int vE) {
			final StylePalette.Building b = palette.building();
			for (int k = 0; k < 3; k++) {
				for (int u = door - 3 + k; u <= door + 3 - k; u++) {
					put(Role.WALL, u, top + 1 + k, vE, LocalDir.PLUS_V, k == 0 || u == door - 3 + k || u == door + 3 - k ? b.trim() : b.wall());
				}
			}
			put(Role.FIXTURE, door, top + 2, vE + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
		}

		/**
		 * A raised attic over the street front centred on {@code cu}, {@code half} cells each side and {@code high} rows above
		 * {@code top}, four cells deep: closed walls, a flat top with a cornice, a clock face on its front and, when
		 * {@code statues}, a statue group round the clock and one at each end.
		 */
		private void attic(int cu, int half, int high, int top, int vE, int u0, int u1, boolean statues) {
			final StylePalette.Building b = palette.building();
			final int a0 = Math.max(u0 + 1, cu - half);
			final int a1 = Math.min(u1 - 1, cu + half);
			final int v0 = vE - 3;
			final int capY = top + high + 1;
			for (int u = a0; u <= a1; u++) {
				for (int v = v0; v <= vE; v++) {
					final boolean ring = u == a0 || u == a1 || v == v0 || v == vE;
					if (ring) {
						for (int y = top + 1; y <= top + high; y++) {
							put(Role.WALL, u, y, v, outward(u, v, a0, a1, v0, vE), y == top + high ? b.trim() : b.wall());
						}
					}
					put(Role.BUILDING_ROOF, u, capY, v, outward(u, v, a0, a1, v0, vE), ring ? b.cornice() : b.roof());
				}
			}
			put(Role.FIXTURE, cu, top + (high + 1) / 2, vE + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
			if (statues) {
				for (final int u : new int[]{cu - 1, cu, cu + 1, a0, a1}) {
					statue(u, capY + 1, vE - 1);
				}
			}
		}

		/** A statue two cells high from {@code y}: a carved base and a pillar figure. */
		private void statue(int u, int y, int v) {
			put(Role.WALL, u, y, v, LocalDir.PLUS_V, STATUE);
			put(Role.WALL, u, y + 1, v, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
		}

		/**
		 * A white roof sweeping up from both side walls to a crest over the middle, on the flat roof at {@code top}, closed at
		 * both ends with curved gable walls: the head building's own wave.
		 */
		private void waveRoof(int top, int u0, int u1, int vL, int vE) {
			final StylePalette.Building b = palette.building();
			final int span = u1 - u0;
			final int crest = Math.max(2, Math.min(6, (span + 1) / 8));
			for (int u = u0; u <= u1; u++) {
				final int rise = (int) Math.round(crest * Math.sin(Math.PI * (u - u0) / span));
				for (int v = vL; v <= vE; v++) {
					final boolean end = v == vL || v == vE;
					for (int y = top + 1; y <= top + rise; y++) {
						if (y == top + rise) {
							put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, b.roof());
						} else if (end) {
							put(Role.WALL, u, y, v, v == vL ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.wall());
						}
					}
				}
			}
		}

		/**
		 * A roof running on two cells past the street wall at {@code y}, across the whole front: a deep street canopy, or with
		 * {@code upturned} its outer row turned up one cell, like a Chinese station roof.
		 */
		private void overhang(int y, int u0, int u1, int vE, boolean upturned) {
			final StylePalette.Building b = palette.building();
			for (int u = u0; u <= u1; u++) {
				for (int v = vE + 1; v <= vE + 2; v++) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, b.roof());
				}
				if (upturned) {
					put(Role.BUILDING_ROOF, u, y + 1, vE + 2, LocalDir.PLUS_V, b.trim());
				}
			}
		}

		/**
		 * The terminus concourse: a gate line two rows in from the buffers (fare gates across each platform with a wide gate in
		 * the middle, glass barriers behind the tracks), departure boards and a clock over the gate line facing the concourse,
		 * the ticket office with its window on one side, MTR ticket machines and the kiosk on the other, and benches facing the
		 * boards.
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
			final int gateV = vL + 2;
			final int door = headDoor();
			for (int u = u0 + 1; u < u1; u++) {
				TrackLayout.PlatformBand band = null;
				for (final TrackLayout.PlatformBand candidate : layout.platforms()) {
					if (u >= candidate.uMin() && u <= candidate.uMax()) {
						band = candidate;
					}
				}
				if (band == null) {
					put(Role.BARRIER, u, StationGeometry.WALK_Y, gateV, LocalDir.MINUS_V);
				} else {
					final int i = u - band.uMin();
					final String kind = i == 0 || i == band.width() - 1 ? "kind=end" : i == band.width() / 2 ? "kind=wide" : "kind=gate";
					// the green "go" side faces the concourse, where people come in; the red X faces the platforms
					put(Role.GATE, u, StationGeometry.WALK_Y, gateV, LocalDir.PLUS_V, palette.blockId(Role.GATE), kind);
				}
			}
			// a big number under the lintel over each platform, in the middle of its gates
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (gateNumbered(band)) {
					numberSign(band, (band.uMin() + band.uMax() + 1) / 2, StationGeometry.CANOPY_Y, vL, LocalDir.PLUS_V);
				}
			}
			// boards over the gate line, hung from the lintel, and the clock above them
			for (int u = door - 1; u <= door + 1; u++) {
				put(Role.FIXTURE, u, StationGeometry.UPPER_FLOOR_Y, vL + 1, LocalDir.PLUS_V, "aurelia_transit_architecture:concourse_board");
			}
			put(Role.FIXTURE, door, StationGeometry.UPPER_WALK_Y, vL + 1, LocalDir.PLUS_V, "aurelia_transit_architecture:wall_digital_clock");
			// ticket office against the first side wall: interior u0+1..u0+2, v=vE-5..vE-4, window facing the concourse
			final int o0 = vE - 5;
			final int o1 = vE - 4;
			if (door - 1 - u0 > 4) {
				for (int u = u0 + 1; u <= u0 + 3; u++) {
					for (int y = 1; y <= 2; y++) {
						put(Role.WALL, u, y, o0 - 1, LocalDir.MINUS_V, b.interiorWall());
						put(Role.WALL, u, y, o1 + 1, LocalDir.PLUS_V, b.interiorWall());
					}
				}
				// the counter at chest height, wall below it
				put(Role.WALL, u0 + 3, 1, o0, LocalDir.PLUS_U, b.interiorWall());
				put(Role.FIXTURE, u0 + 3, 2, o0, LocalDir.PLUS_U, "aurelia_transit_architecture:booth_window");
				put(Role.WALL, u0 + 3, 1, o1, LocalDir.PLUS_U, b.interiorWall());
				put(Role.WALL, u0 + 3, 2, o1, LocalDir.PLUS_U, b.interiorWall());
			} else { // too narrow for an office: a ticket window in the side wall's corner by the entrance
				put(Role.WALL, u0 + 1, 1, vE - 1, LocalDir.PLUS_U, b.interiorWall());
				put(Role.FIXTURE, u0 + 1, 2, vE - 1, LocalDir.PLUS_U, "aurelia_transit_architecture:booth_window");
			}
			// ticket machines and the kiosk against the far wall
			for (int i = 0; i < 2; i++) {
				tallFixture(u1 - 1, 1, o0 + i, LocalDir.MINUS_U, "mtr:ticket_machine");
			}
			tallFixture(u1 - 1, 1, o1 + 2, LocalDir.MINUS_U, "aurelia_transit_architecture:passenger_info_kiosk");
			// benches facing the boards, either side of the walk from the entrance
			if (spec.furniture()) {
				int seats = 0;
				for (final int u : new int[]{door - 3, door - 2, door + 2, door + 3}) {
					if (u > u0 && u < u1 && put(Role.BENCH, u, StationGeometry.WALK_Y, vL + 6, LocalDir.MINUS_V, b.bench())) {
						seats++;
					}
				}
				// a narrow concourse: any free cell in the row that keeps the walk from the entrance clear
				for (int u = u0 + 1; u < u1 && seats == 0; u++) {
					if (Math.abs(u - door) > 1 && put(Role.BENCH, u, StationGeometry.WALK_Y, vL + 6, LocalDir.MINUS_V, b.bench())) {
						seats++;
					}
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
				for (final int u : site.stairs(band)) {
					for (int y = 1; y <= 3; y++) {
						keepClear(u, y, landing);
					}
					// two clear above every step, and a third: stepping up from it your head passes under the cell above it
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						keepClear(u, y + 1, site.stairV(y));
						keepClear(u, y + 2, site.stairV(y));
						keepClear(u, y + 3, site.stairV(y));
					}
				}
			}
			// deck, out to and under both corridor walls, so the stair exits have a floor and the walls do not float
			for (int u = from; u <= to; u++) {
				for (int v = wallLo; v <= wallHi; v++) {
					if ((u + v) % 5 == 0) {
						// the building's recessed light: a full block with the light underneath, so the walkway has no pit
						put(Role.BRIDGE_DECK, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
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
							&& site.flightColumns(band).contains(u)) {
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
			// glass across the corridor's open ends (the building closes its own end)
			for (final int u : site.building() ? new int[]{to} : new int[]{from, to}) {
				for (int v = wallLo + 1; v < wallHi; v++) {
					for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
						put(Role.WINDOW, u, y, v, u == to ? LocalDir.PLUS_U : LocalDir.MINUS_U, b.bridgeWall());
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
				if (roof(band) == RoofType.CANOPY ? stairShaft(band, b) : stairHood(band, b)) {
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
			// piers on every platform at the bridge line, up to the deck: down the middle, or on the canopy's column lines on a
			// platform too wide for the deck to overhang one
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int uPier : columnLines(band, RoofType.CANOPY)) {
					for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
						put(Role.BRIDGE_PIER, uPier, y, vB, LocalDir.PLUS_V, b.pier());
					}
				}
			}
			// a big number under the deck beside each platform's pier, seen as you come off the stairs; a platform numbered over
			// its gates at a terminus head gets no second one hanging over the platform
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (site.served().contains(band.number()) && !gateNumbered(band)) {
					numberSign(band, (band.uMin() + band.uMax()) / 2, StationGeometry.CANOPY_Y, vB - site.stairSide(), LocalDir.PLUS_V);
				}
			}
			// flanking piers at the stair landings (never on the landing itself) so the canopy ends stay carried; under a canopy
			// they stop below it, so the canopy runs over them
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (!site.served().contains(band.number())) {
					continue;
				}
				final int flankTop = roof(band) == RoofType.CANOPY ? StationGeometry.CANOPY_Y : StationGeometry.UPPER_FLOOR_Y;
				for (final int su : site.flightColumns(band)) {
					for (final int du : new int[]{-1, 1}) {
						final int u = su + du;
						if (u < band.uMin() || u > band.uMax() || isEdge(band, u) || site.flightColumns(band).contains(u)) {
							continue;
						}
						boolean free = true;
						for (int y = StationGeometry.WALK_Y; y < flankTop; y++) {
							if (taken(u, y, landing)) {
								free = false;
								break;
							}
						}
						if (free) {
							for (int y = StationGeometry.WALK_Y; y < flankTop; y++) {
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
				final List<Integer> flight = site.stairs(band);
				for (final int u : flight) {
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						put(Role.STAIR, u, y, site.stairV(y), site.stairFront(), b.stair());
					}
				}
				if (flight.isEmpty()) {
					continue;
				}
				// balustrades on the outer flanks of the whole flight, from the floor up to each step, so no rail hangs at head
				// height over the platform (never at the canopy roof level, which the canopy fills, never on boarding edges)
				final int left = flight.get(0) - 1;
				final int right = flight.get(flight.size() - 1) + 1;
				for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
					final int v = site.stairV(y);
					for (int rail = StationGeometry.WALK_Y; rail <= y; rail++) {
						if (rail == StationGeometry.CANOPY_Y && roof(band) == RoofType.CANOPY) {
							continue;
						}
						if (!isEdge(band, left)) {
							put(Role.RAILING, left, rail, v, LocalDir.MINUS_U);
						}
						if (!isEdge(band, right)) {
							put(Role.RAILING, right, rail, v, LocalDir.PLUS_U);
						}
					}
				}
			}
			// six-foot piers where the deck span between supports exceeds 12
			final List<Integer> supports = new ArrayList<>();
			supports.add(from);
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				supports.addAll(columnLines(band, RoofType.CANOPY));
			}
			supports.add(to);
			supports.sort(Comparator.naturalOrder());
			for (int i = 0; i + 1 < supports.size(); i++) {
				int a = supports.get(i);
				int b2 = supports.get(i + 1);
				while (b2 - a > 12) {
					// the farthest free column within 12 of the last support, so no span is left longer (halving a span over 24
					// would leave both halves too long)
					int pierU = -1;
					for (int u = Math.min(a + 12, b2 - 1); u > a && pierU < 0; u--) {
						if (nearestFreeColumn(u) == u) {
							pierU = u;
						}
					}
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

		/** A platform's boarding edge column. */
		private boolean onEdge(int u) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (isEdge(band, u)) {
					return true;
				}
			}
			return false;
		}

		/** A free column near u0 at the bridge line that is not inside any track envelope or on a boarding edge; -1 if none nearby. */
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
					if (!envelope && !onEdge(u) && !taken(u, 1, site.bridgeV())) {
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
		 * MTR up escalators beside the platform stairs, planned first so the platform floor and the deck leave their landings
		 * to them. The slope runs level with the stair beside it; a flat landing step at each end lies in the platform floor
		 * and in the corridor wall row. Each step has its handrail block above it, and a casing fills under the slope.
		 */
		void escalators() {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final List<Integer> columns = site.escalator(band);
				if (columns.isEmpty()) {
					continue;
				}
				final LocalDir up = site.stairFront();
				// MTR's left half is on the left looking up the escalator; +U is to the right of +V
				final int left = up == LocalDir.PLUS_V ? columns.get(0) : columns.get(1);
				for (int i = 0; i <= 7; i++) {
					final int v = site.escalatorV(i);
					final int y = StationLayout.escalatorY(i);
					for (final int u : columns) {
						final String side = u == left ? "left" : "right";
						put(Role.ESCALATOR, u, y, v, up, StylePalette.MTR_ESCALATOR_STEP, "side=" + side + ",direction=true,status=true");
						put(Role.ESCALATOR, u, y + 1, v, up, StylePalette.MTR_ESCALATOR_SIDE, "side=" + side);
						for (int below = StationGeometry.WALK_Y; below < y; below++) {
							put(Role.BRIDGE_PIER, u, below, v, up, palette.building().pier());
						}
						if (i < 7) {
							keepClear(u, y + 2, v);
							keepClear(u, y + 3, v);
						}
					}
				}
				for (final int u : columns) {
					for (int y = StationGeometry.WALK_Y; y <= 3; y++) {
						keepClear(u, y, site.escalatorFootV());
					}
				}
			}
		}

		/** Bands with a stair flight down from the footbridge: served and wide enough for steps. */
		private boolean hasStairs(TrackLayout.PlatformBand band) {
			return site.bridge() && site.served().contains(band.number()) && !StationLayout.stairColumns(band).isEmpty();
		}

		void columns() {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (roof(band) == RoofType.NONE) {
					continue;
				}
				final int top = roof(band) == RoofType.CANOPY ? StationGeometry.CANOPY_Y : hallBeamY();
				for (final int u : columnLines(band, roof(band))) {
					for (final int v : spec.roof() == RoofType.HALL ? hallRows() : columnVForLine(band, u)) {
						final int base = spec.roof() == RoofType.HALL ? standsOn(u, v, top) : StationGeometry.WALK_Y - 1;
						if (base >= StationGeometry.WALK_Y) {
							// the row crosses a lift shaft or another roof: a pier on that roof carries the frame
							for (int y = base + 1; y < top; y++) {
								put(Role.BRIDGE_PIER, u, y, v, LocalDir.PLUS_V, palette.building().pier());
							}
							continue;
						}
						if (!band.runs(v)) {
							// past a shorter platform's end a hall column stands on a footing of platform floor, never on its ramp
							if (taken(u, StationGeometry.PLATFORM_Y, v)) {
								continue;
							}
							put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
						}
						for (int y = StationGeometry.WALK_Y; y < top - 1; y++) {
							put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
						}
						put(Role.COLUMN_CAP, u, top - 1, v, LocalDir.PLUS_V);
					}
				}
			}
			trackColumns();
		}

		/** A short pier on the footbridge roof at u, up to the hall frame. */
		private void bridgePier(int u, int bridgeRoof, int beamY) {
			for (int y = bridgeRoof + 1; y < beamY; y++) {
				put(Role.BRIDGE_PIER, u, y, site.bridgeV(), LocalDir.PLUS_V, palette.building().pier());
			}
		}

		/**
		 * Columns between tracks, where a hall's cross girders would otherwise span further than
		 * {@link StationGeometry#MAX_BEAM_SPAN} between two platforms' column lines (several tracks side by side with no platform
		 * between them). Each stands on a plinth in the free cell between two tracks' clearances, at every hall row.
		 */
		private void trackColumns() {
			// a single vault spans the tracks by itself, with no tie girders across them for a column to carry
			if (spec.roof() != RoofType.HALL || spec.shed() == ShedShape.ARCH || spec.shed() == ShedShape.POINTED) {
				return;
			}
			final java.util.TreeSet<Integer> lines = new java.util.TreeSet<>();
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				lines.addAll(columnLines(band, RoofType.HALL));
			}
			final java.util.TreeSet<Integer> free = new java.util.TreeSet<>();
			for (int u = layout.buildingWidth(); u < layout.width(); u++) {
				if (betweenTracks(u)) {
					free.add(u);
				}
			}
			final List<Integer> added = new ArrayList<>();
			Integer previous = null;
			for (final int line : List.copyOf(lines)) {
				int from = previous == null ? line : previous;
				while (previous != null && line - from > StationGeometry.MAX_BEAM_SPAN) {
					final Integer pick = free.floor(from + StationGeometry.MAX_BEAM_SPAN);
					if (pick == null || pick <= from) {
						break;
					}
					added.add(pick);
					from = pick;
				}
				previous = line;
			}
			final int top = hallBeamY();
			for (final int u : added) {
				for (final int v : hallRows()) {
					if (standsOn(u, v, top) >= StationGeometry.WALK_Y || taken(u, StationGeometry.PLATFORM_Y, v)) {
						continue;
					}
					// on a plinth of platform floor, level with the platforms
					put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
					for (int y = StationGeometry.WALK_Y; y < top - 1; y++) {
						put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
					}
					put(Role.COLUMN_CAP, u, top - 1, v, LocalDir.PLUS_V);
					extraSupport.add(new Box(u, -1, v, u, -1, v));
				}
			}
		}

		/** Outside every track's clearance and every platform: the strip between two tracks. */
		private boolean betweenTracks(int u) {
			boolean left = false;
			boolean right = false;
			for (final TrackLayout.Track track : layout.tracks()) {
				if (Math.abs(u - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH) {
					return false;
				}
				left |= track.u() < u;
				right |= track.u() > u;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (u >= band.uMin() && u <= band.uMax()) {
					return false;
				}
			}
			return left && right;
		}

		/** Column v along one platform: the station's spacing, or the same spacing along a shorter platform's own run. */
		private List<Integer> columnV(TrackLayout.PlatformBand band) {
			if (band.vFrom() == 0 && band.vTo() == length - 1) {
				return columnV;
			}
			final List<Integer> positions = new ArrayList<>();
			for (final int v : columnPositions(band.length())) {
				positions.add(band.vFrom() + v);
			}
			return positions;
		}

		/** Column v for one column line: the platform's spacing minus cells where stairs, railings or piers stand, with gaps of at most 9. */
		private List<Integer> columnVForLine(TrackLayout.PlatformBand band, int line) {
			if (!site.bridge()) {
				return columnV(band);
			}
			// up to the roof cell over the cap: a sign or a stair there leaves the column carrying nothing
			final int top = StationGeometry.CANOPY_Y;
			final List<Integer> lines = List.of(line);
			final List<Integer> kept = new ArrayList<>();
			for (final int v : columnV(band)) {
				boolean clash = false;
				for (final int u : lines) {
					for (int y = StationGeometry.WALK_Y; y <= top; y++) {
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
					if (mid <= a || mid >= b || !band.runs(mid)) {
						break;
					}
					boolean clash = false;
					for (final int u : lines) {
						for (int y = StationGeometry.WALK_Y; y <= top; y++) {
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

		/**
		 * The v of every column row of a hall: the regular spacing, each row moved by up to two cells (nearest first) to
		 * where every column line of every platform is free from the floor to the frame (clear of stairs, landings and
		 * footbridge walls), so each cross girder stands on a full row of columns.
		 */
		private List<Integer> hallRows() {
			if (hallRows != null) {
				return hallRows;
			}
			final int top = hallBeamY();
			final List<Integer> rows = new ArrayList<>();
			for (final int v0 : columnV) {
				// clear of the building's doorways when a shift of up to 3 allows it, else only clear of what is built
				int found = -1;
				for (final int d : new int[] {0, 1, -1, 2, -2, 3, -3}) {
					final int v = v0 + d;
					if (v >= 0 && v < length && !rows.contains(v) && rowFree(v, top, true)) {
						found = v;
						break;
					}
				}
				for (final int d : new int[] {0, 1, -1, 2, -2}) {
					final int v = v0 + d;
					if (found < 0 && v >= 0 && v < length && !rows.contains(v) && rowFree(v, top, false)) {
						found = v;
					}
				}
				if (found >= 0) {
					rows.add(found);
				}
			}
			// over a footbridge the frame also rests on the bridge roof (see hallRoof): that row carries the girders too, so the
			// gaps either side of it are measured from it and no extra row of columns is squeezed in beside the stairs
			final boolean bridgeRow = site.bridge() && top >= StationGeometry.BUILDING_ROOF_Y + 2 && !rows.contains(site.bridgeV());
			if (bridgeRow) {
				rows.add(site.bridgeV());
			}
			rows.sort(Comparator.naturalOrder());
			// a row kept off a doorway or a stair can leave two rows further apart than a girder spans: add one between them,
			// nearest the middle, clear of doorways if possible
			boolean filled = true;
			while (filled) {
				filled = false;
				for (int i = 0; i + 1 < rows.size() && !filled; i++) {
					final int a = rows.get(i);
					final int b = rows.get(i + 1);
					if (b - a <= StationGeometry.MAX_BEAM_SPAN) {
						continue;
					}
					final int found = gapRow(a, b, top);
					if (found >= 0) {
						rows.add(i + 1, found);
						filled = true;
					}
				}
			}
			// shifting rows off doorways can leave an end out of the girders' reach (short platforms): add a row near that end
			final int reach = StationGeometry.MAX_CANTILEVER;
			if (!rows.isEmpty() && rows.get(rows.size() - 1) < length - 1 - reach) {
				endRow(rows, length - 1 - reach, length - 1, top);
			}
			if (!rows.isEmpty() && rows.get(0) > reach) {
				endRow(rows, 0, reach, top);
			}
			if (bridgeRow) {
				rows.remove(Integer.valueOf(site.bridgeV()));
			}
			rows.sort(Comparator.naturalOrder());
			hallRows = rows;
			return rows;
		}

		/** A free row strictly between rows a and b, nearest their middle (clear of doorways first); -1 if there is none. */
		private int gapRow(int a, int b, int top) {
			final int mid = (a + b) / 2;
			for (final boolean doors : new boolean[]{true, false}) {
				for (int d = 0; d <= (b - a) / 2; d++) {
					for (final int v : new int[]{mid + d, mid - d}) {
						if (v > a && v < b && rowFree(v, top, doors)) {
							return v;
						}
					}
				}
			}
			return -1;
		}

		/**
		 * Adds the first free row in {@code from..to}, clear of doorways if one is, else clear of what is built, else one whose
		 * columns can stand on a roof, wall or deck below the frame (a lift shaft's roof on a short platform).
		 */
		private void endRow(List<Integer> rows, int from, int to, int top) {
			for (final boolean doors : new boolean[] {true, false}) {
				for (int v = from; v <= to; v++) {
					if (!rows.contains(v) && rowFree(v, top, doors)) {
						rows.add(v);
						return;
					}
				}
			}
			for (int v = to; v >= from; v--) {
				if (!rows.contains(v) && rowCarried(v, top)) {
					rows.add(v);
					return;
				}
			}
		}

		/** The y of the highest block under the frame at (u, v) that a pier can stand on, or WALK_Y - 1 when the cell column is clear. */
		private int standsOn(int u, int v, int top) {
			for (int y = top - 1; y >= StationGeometry.WALK_Y; y--) {
				if (cells.containsKey(new Cell(u, y, v))) {
					return y;
				}
			}
			return StationGeometry.WALK_Y - 1;
		}

		/** Every column line at {@code v} is clear up to the frame or ends on a solid roof, wall or deck it can stand on. */
		private boolean rowCarried(int v, int top) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int u : columnLines(band, RoofType.HALL)) {
					for (int y = top - 1; y >= StationGeometry.WALK_Y; y--) {
						final PlannedBlock below = cells.get(new Cell(u, y, v));
						if (air.contains(new Cell(u, y, v)) && below == null) {
							return false;
						}
						if (below != null) {
							final Role role = below.role();
							if (role != Role.BUILDING_ROOF && role != Role.WALL && role != Role.BRIDGE_DECK && role != Role.ROOF) {
								return false;
							}
							break;
						}
					}
				}
			}
			return true;
		}

		private boolean rowFree(int v, int top, boolean doors) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int u : columnLines(band, RoofType.HALL)) {
					if (doors && site.building() && Math.abs(u - site.platformWall(layout)) <= 3 && (doorV.contains(v - 1) || doorV.contains(v)
							|| doorV.contains(v + 1))) {
						return false;
					}
					for (int y = StationGeometry.WALK_Y; y < top; y++) {
						if (taken(u, y, v) || air.contains(new Cell(u, y, v))) {
							return false;
						}
					}
					// past a shorter platform's end the column needs its footing cell free (not the platform's ramp)
					if (!band.runs(v) && taken(u, StationGeometry.PLATFORM_Y, v)) {
						return false;
					}
				}
			}
			return true;
		}

		/** The roof over this platform (see {@link StationGeometry#roof}). */
		private RoofType roof(TrackLayout.PlatformBand band) {
			return StationGeometry.roof(spec, band.number());
		}

		private boolean isColumnV(int v) {
			return spec.roof() == RoofType.HALL ? hallRows().contains(v) : columnV.contains(v);
		}

		/** True for the cells of a roof lit from above: every 4th cell along a column line, except where a column stands. */
		private boolean isLightV(int v) {
			return v >= 4 && v % 4 == 0 && !isColumnV(v);
		}

		void canopyRoofs() {
			final int y = StationGeometry.CANOPY_Y;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (roof(band) != RoofType.CANOPY) {
					continue;
				}
				final List<Integer> lines = columnLines(band, RoofType.CANOPY);
				final List<Integer> posts = columnV(band);
				for (int v = band.vFrom(); v <= band.vTo(); v++) {
					// lit every 4th cell along the platform, except where a column stands
					final int along = v - band.vFrom();
					final boolean light = along >= 4 && along % 4 == 0 && !posts.contains(v);
					for (int u = band.uMin(); u <= band.uMax(); u++) {
						if (u == band.uMin()) {
							put(Role.ROOF_EDGE, u, y, v, LocalDir.MINUS_U);
						} else if (u == band.uMax()) {
							put(Role.ROOF_EDGE, u, y, v, LocalDir.PLUS_U);
						} else if (lines.contains(u) && light) {
							put(Role.ROOF_LIGHT, u, y, v, LocalDir.PLUS_V);
						} else {
							put(Role.ROOF, u, y, v, LocalDir.PLUS_V);
						}
					}
				}
			}
		}

		/**
		 * y of the hall's girder frame: just above the track envelopes; higher under a wide vault; and above the footbridge
		 * and a building beside the tracks when the station has them.
		 */
		private int hallBeamY() {
			if (scale == StationScale.GRAND) {
				return StationGeometry.TALL_HALL_BEAM_Y + 3;
			}
			if (site.bridge() || spec.sideBuilding()) {
				return StationGeometry.TALL_HALL_BEAM_Y;
			}
			if (spec.shed().arched() && layout.width() - layout.buildingWidth() >= 16) {
				return StationGeometry.HIGH_HALL_BEAM_Y;
			}
			return StationGeometry.HALL_BEAM_Y;
		}

		/**
		 * A train shed over the railway (u from the end of any side building to the far edge, the whole platform length): a
		 * closed girder frame (cross girders over every column row, girders along every column line, both eaves and every
		 * valley), then the roof. A flat roof sits on the frame with lights over the column lines. A vault rises from the
		 * eaves in face-joined steps, glazed above a solid lower band; stepped girder ribs follow its underside over every
		 * column row and at both ends, with hangers from the column lines up to the rib. A terminus closes the vault above
		 * the head building with a glass screen.
		 */
		void hallRoof() {
			if (spec.roof() != RoofType.HALL) {
				return;
			}
			final String girder = palette.blockId(Role.BEAM);
			final String kind = "kind=" + palette.girderKind();
			final int beamY = hallBeamY();
			final int u0 = layout.buildingWidth();
			final int u1 = layout.width() - 1;
			final int n = u1 - u0 + 1;
			final int[] rise = shedRise(u0, n);
			final boolean[] lit = new boolean[layout.width()];
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int u : columnLines(band, RoofType.HALL)) {
					lit[u] = true;
				}
			}
			final List<Integer> rows = new ArrayList<>(hallRows());
			// over a footbridge the frame also rests on the bridge roof: short piers under every girder line, a cross girder on them
			final int bridgeRoof = StationGeometry.BUILDING_ROOF_Y;
			final boolean onBridge = site.bridge() && beamY >= bridgeRoof + 2;
			if (onBridge && !rows.contains(site.bridgeV())) {
				rows.add(site.bridgeV());
			}
			rows.sort(Comparator.naturalOrder());
			// a single vault is its own arch: no tie girders across the tracks, only brackets over the platforms and eaves (the
			// bridge row still crosses on its piers); several vaults keep full ties, which carry their valley gutters
			final boolean freeArch = spec.shed() == ShedShape.ARCH || spec.shed() == ShedShape.POINTED;
			final boolean[] untied = freeArch ? betweenPlatforms() : new boolean[layout.width()];
			for (final int v : rows) {
				for (int u = u0; u <= u1; u++) {
					if (untied[u] && !(onBridge && v == site.bridgeV())) {
						continue;
					}
					put(Role.BEAM, u, beamY, v, LocalDir.PLUS_U, girder, kind);
				}
			}
			// girders along v: column lines, eaves, valleys, and (under a flat roof) purlins halving any gap wider than 8 so
			// every roof cell is within reach of one; a vault carries its purlins in the curve instead (below)
			final java.util.TreeSet<Integer> along = new java.util.TreeSet<>();
			for (int i = 0; i < n; i++) {
				if (lit[u0 + i] || i == 0 || i == n - 1 || spec.shed().arched() && rise[i] == 0) {
					along.add(i);
				}
			}
			for (boolean added = !spec.shed().arched(); added; ) {
				added = false;
				Integer previous = null;
				for (final int i : List.copyOf(along)) {
					if (previous != null && i - previous > 2 * StationGeometry.MAX_CANTILEVER) {
						along.add((previous + i) / 2);
						added = true;
					}
					previous = i;
				}
			}
			for (final int i : along) {
				for (int v = 0; v < length; v++) {
					put(Role.BEAM, u0 + i, beamY, v, LocalDir.PLUS_V, girder, kind);
				}
			}
			// an end frame across each end of the shed, joined to the girders along v so they finish on it instead of stopping
			// short; it hangs from them (a rib, not a beam on columns)
			for (final int end : new int[]{0, length - 1}) {
				for (int u = u0 + along.first(); u <= u0 + along.last(); u++) {
					if (!untied[u]) {
						put(Role.RIB, u, beamY, end, LocalDir.PLUS_U, girder, kind);
					}
				}
			}
			if (onBridge) {
				for (int u = u0; u <= u1; u++) {
					final PlannedBlock below = cells.get(new Cell(u, bridgeRoof, site.bridgeV()));
					if (below != null && below.role() == Role.BUILDING_ROOF && (along.contains(u - u0) || lit[u])) {
						bridgePier(u, bridgeRoof, beamY);
					}
				}
				// where the cross girder would still span more than a girder may (a vault's tie over several tracks), more piers
				// on the bridge roof, each as far along as the span allows
				final java.util.TreeSet<Integer> carried = new java.util.TreeSet<>();
				for (int u = u0; u <= u1; u++) {
					final PlannedBlock under = cells.get(new Cell(u, beamY - 1, site.bridgeV()));
					if (under != null && (under.role() == Role.BRIDGE_PIER || under.role() == Role.COLUMN_CAP)) {
						carried.add(u);
					}
				}
				Integer previous = null;
				for (final int u : List.copyOf(carried)) {
					int from = previous == null ? u : previous;
					while (previous != null && u - from > StationGeometry.MAX_BEAM_SPAN) {
						int pick = -1;
						for (int c = from + StationGeometry.MAX_BEAM_SPAN; c > from && pick < 0; c--) {
							final PlannedBlock roofBelow = cells.get(new Cell(c, bridgeRoof, site.bridgeV()));
							if (roofBelow != null && roofBelow.role() == Role.BUILDING_ROOF && !taken(c, bridgeRoof + 1, site.bridgeV())) {
								pick = c;
							}
						}
						if (pick < 0) {
							break;
						}
						bridgePier(pick, bridgeRoof, beamY);
						from = pick;
					}
					previous = u;
				}
			}
			final int roofY = beamY + 1;
			final int[] top = new int[n];
			for (int i = 0; i < n; i++) {
				top[i] = roofY + rise[i];
			}
			final int[] low = steps(top, false);
			final int peak = java.util.Arrays.stream(rise).max().orElse(0);
			final String glass = ata("glass_wall");
			final String solid = palette.blockId(Role.ROOF);
			for (int v = 0; v < length; v++) {
				for (int i = 0; i < n; i++) {
					final int u = u0 + i;
					final boolean edge = i == 0 || i == n - 1;
					for (int y = low[i]; y <= top[i]; y++) {
						if (!spec.shed().arched()) {
							if (edge) {
								put(Role.ROOF_EDGE, u, y, v, i == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U);
							} else if (lit[u] && isLightV(v)) {
								put(Role.ROOF_LIGHT, u, y, v, LocalDir.PLUS_V);
							} else {
								put(Role.ROOF, u, y, v, LocalDir.PLUS_V);
							}
						} else {
							final boolean glazed = palette.roofMaterialGlass() || y - roofY >= Math.max(1, peak / 3);
							final boolean eaves = edge && y == top[i];
							put(eaves ? Role.ROOF_EDGE : Role.ROOF, u, y, v, i == 0 ? LocalDir.MINUS_U : i == n - 1 ? LocalDir.PLUS_U : LocalDir.PLUS_V,
									glazed && !eaves ? glass : vaultCladding());
						}
					}
				}
			}
			if (!spec.shed().arched()) {
				return;
			}
			// ribs under the vault, one cell below its underside, joined face to face like the roof above them
			final int[] ribTop = new int[n];
			for (int i = 0; i < n; i++) {
				ribTop[i] = low[i] - 1;
			}
			final int[] ribLow = steps(ribTop, true);
			final List<Integer> ribV = new ArrayList<>(rows);
			ribV.add(0);
			ribV.add(length - 1);
			for (final int v : ribV) {
				for (int i = 0; i < n; i++) {
					for (int y = Math.max(ribLow[i], beamY); y <= ribTop[i]; y++) {
						put(Role.RIB, u0 + i, y, v, LocalDir.PLUS_U, girder, kind);
					}
				}
			}
			// purlins in the curve: girder lines along v just under the vault, joining every rib, about every 4 across
			for (int i = 2; i < n - 2; i++) {
				if (rise[i] > 0 && (i % 4 == 2 || isCrown(rise, i))) {
					for (int v = 0; v < length; v++) {
						put(Role.RIB, u0 + i, ribTop[i], v, LocalDir.PLUS_V, girder, kind);
					}
				}
			}
			// hangers from the frame over each column line up to the rib
			for (final int v : rows) {
				for (int i = 0; i < n; i++) {
					if (lit[u0 + i]) {
						for (int y = beamY + 1; y < ribLow[i]; y++) {
							put(Role.RIB, u0 + i, y, v, LocalDir.PLUS_U, girder, kind);
						}
					}
				}
			}
			if (layout.terminus()) {
				// glass screen closing the vault above the head building's cornice
				final int screenFrom = StationGeometry.BUILDING_ROOF_Y + 2;
				for (int i = 0; i < n; i++) {
					for (int y = screenFrom; y <= top[i]; y++) {
						put(Role.WINDOW, u0 + i, y, length, LocalDir.PLUS_V, glass);
					}
				}
			}
		}

		/**
		 * Cells strictly between the column lines of two neighbouring platforms: the bays over the tracks that a single vault
		 * spans without a tie. Every other piece of a cross girder ends on a column line.
		 */
		private boolean[] betweenPlatforms() {
			final boolean[] out = new boolean[layout.width()];
			int previous = -1;
			TrackLayout.PlatformBand previousBand = null;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final List<Integer> lines = columnLines(band, RoofType.HALL);
				if (lines.isEmpty()) {
					continue;
				}
				final int first = java.util.Collections.min(lines);
				if (previousBand != null) {
					for (int u = previous + 1; u < first; u++) {
						out[u] = true;
					}
				}
				previous = java.util.Collections.max(lines);
				previousBand = band;
			}
			return out;
		}

		/** The vault's solid lower band: the style's roof when that is a full block, otherwise steel cladding. */
		private String vaultCladding() {
			return ata("metro_steel_cladding");
		}

		/** Height of a round vault over its span. */
		private static final double ARCH_RISE = 0.4;
		/** Highest a vault rises over its frame: the widest single vault (100 blocks) would otherwise top the station's height limit. */
		private static final int MAX_VAULT_RISE = 40;

		/**
		 * Rise of the vault above the eaves at each u0 + i: 0 for a flat roof; half an ellipse (barrel vault), a pointed arch, or
		 * one half ellipse per span between the island platforms' centre lines (several vaults side by side).
		 */
		private int[] shedRise(int u0, int n) {
			final int[] rise = new int[n];
			switch (spec.shed()) {
				case FLAT -> {
				}
				case ARCH -> vault(rise, 0, n - 1, false);
				case POINTED -> vault(rise, 0, n - 1, true);
				case MULTI_ARCH -> {
					// a valley between two neighbouring tracks with no platform between them: one vault per platform and its tracks
					final List<Integer> cuts = new ArrayList<>();
					cuts.add(0);
					final List<TrackLayout.Track> tracks = layout.tracks();
					for (int t = 0; t + 1 < tracks.size(); t++) {
						final TrackLayout.Track left = tracks.get(t);
						final TrackLayout.Track right = tracks.get(t + 1);
						final int c = (left.u() + right.u()) / 2 - u0;
						if (!left.platformRight() && !right.platformLeft() && c >= 4 && c <= n - 5) {
							cuts.add(c);
						}
					}
					cuts.add(n - 1);
					for (int k = 0; k + 1 < cuts.size(); k++) {
						vault(rise, cuts.get(k), cuts.get(k + 1), false, ARCH_RISE);
					}
				}
			}
			return rise;
		}

		private static void vault(int[] rise, int a, int b, boolean pointed) {
			vault(rise, a, b, pointed, ARCH_RISE);
		}

		/** The middle of a vault's flat crown: as high as both neighbours and not left of an equal one. */
		private static boolean isCrown(int[] rise, int i) {
			return rise[i] >= rise[i + 1] && rise[i] > rise[i - 1];
		}

		private static void vault(int[] rise, int a, int b, boolean pointed, double ratio) {
			final int span = b - a + 1;
			final double half = (b - a) / 2.0;
			final double centre = (a + b) / 2.0;
			final int height = Math.min(MAX_VAULT_RISE, pointed ? Math.max(3, (int) Math.round(span * 0.42)) : Math.max(2, (int) Math.round(span * ratio)));
			for (int u = a; u <= b; u++) {
				final double x = Math.min(1, Math.abs(u - centre) / half);
				// pointed: two arcs centred half a span outside and below the crown's opposite side, meeting at the crown at an
				// angle; round: half an ellipse, which springs steeply from the eaves like a real barrel vault
				final double f = pointed ? Math.sqrt(Math.max(0, 2.5 - (x + 0.5) * (x + 0.5))) - 0.5 : Math.sqrt(1 - x * x);
				rise[u] = Math.max(rise[u], (int) Math.round(height * f));
			}
		}

		/**
		 * The lowest y of each column of a stepped surface whose tops are {@code top}. Full blocks ({@code faceJoined} false)
		 * reach down to one above a lower neighbour's top, meeting it edge to edge, which is watertight; girders, which only
		 * join through faces, reach down to the lower neighbour's top.
		 */
		private static int[] steps(int[] top, boolean faceJoined) {
			final int reach = faceJoined ? 0 : 1;
			final int[] low = new int[top.length];
			for (int i = 0; i < top.length; i++) {
				int y = top[i];
				if (i > 0) {
					y = Math.min(y, top[i - 1] + reach);
				}
				if (i + 1 < top.length) {
					y = Math.min(y, top[i + 1] + reach);
				}
				low[i] = y;
			}
			return low;
		}

		private static String ata(String id) {
			return "aurelia_transit_architecture:" + id;
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
				for (int v = band.vFrom() + 8; v <= band.vTo() - 1; v += 16, index++) {
					final int u;
					final LocalDir front;
					if (band.island()) {
						u = nearestFree(band, (band.uMin() + band.uMax()) / 2, v);
						front = index % 2 == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					} else {
						u = backColumn(band);
						front = towardTrack(band);
					}
					// never by or under a stair flight; a two-seat bench (two benches side by side) with a bin beside it
					if (u < 0 || isEdge(band, u) || nearStairs(band, v) || nearStairs(band, v + 1)
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
			final StationLayout.Lift lift = site.buildingLift(layout);
			if (to >= site.innerStairMin() - 1 && from <= site.innerStairMax() + 1
					|| site.bridge() && to >= site.bridgeV() - 2 && from <= site.bridgeV() + 2
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
		 * quarter and three quarters of the length) on longer ones. Under a roof the screen hangs from the ceiling, double-sided
		 * and facing along the platform, two blocks wide where the platform has room (never over an edge cell); on an open
		 * platform a standing screen is fixed to the back of a lamp post. Points keep clear of footbridge stairs and their
		 * landings. Each platform also gets one help point, a wall unit, fixed to the post nearest its first screen.
		 */
		void platformEquipment() {
			if (!spec.furniture()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final int run = band.length();
				final int from = band.vFrom();
				final List<Integer> targets = run >= 48 ? List.of(from + run / 4, from + run * 3 / 4) : List.of(from + run / 2);
				for (final int target : targets) {
					if (!hangScreen(band, target)) {
						onPost(band, target, PIDS);
					}
				}
				onPost(band, targets.get(0), HELP_POINT);
			}
		}

		/** The columns a hanging screen spans: the middle (and the next one) of an island, the back two of a side platform. */
		private static List<Integer> screenColumns(TrackLayout.PlatformBand band) {
			final List<Integer> columns = new ArrayList<>();
			final int first = band.island() ? (band.uMin() + band.uMax()) / 2 : backColumn(band);
			final int step = band.island() || band.trackRight() ? 1 : -1;
			columns.add(first);
			if (band.width() >= 4 && !isEdge(band, first + step)) {
				columns.add(first + step);
			}
			return columns;
		}

		/** Hangs a screen under the ceiling at the free v nearest {@code target} (searching 0, +2, -2 ... up to 12); true if hung. */
		private boolean hangScreen(TrackLayout.PlatformBand band, int target) {
			final List<Integer> columns = screenColumns(band);
			for (int d = 0; d <= 12; d += 2) {
				for (final int v : d == 0 ? new int[]{target} : new int[]{target + d, target - d}) {
					if (v < band.vFrom() + 1 || v > band.vTo() - 1 || nearStairs(band, v)) {
						continue;
					}
					final int ceiling = ceiling(columns.get(0), v);
					final int y = ceiling - 1;
					if (ceiling < 0 || y < StationGeometry.WALK_Y + 2 || y > StationGeometry.WALK_Y + 4
							|| columns.stream().anyMatch(u -> ceiling(u, v) != ceiling || taken(u, y, v) || isEdge(band, u))) {
						continue;
					}
					for (final int u : columns) {
						put(Role.FIXTURE, u, y, v, LocalDir.MINUS_V, HANGING_PIDS);
					}
					return true;
				}
			}
			return false;
		}

		/** The lowest roof, deck or slab above the walking level at (u, v), or -1 if open to the sky. */
		private int ceiling(int u, int v) {
			for (int y = StationGeometry.WALK_Y + 1; y <= StationGeometry.BUILDING_ROOF_Y + 2; y++) {
				final PlannedBlock block = cells.get(new Cell(u, y, v));
				if (block != null && block.role().covers()) {
					return y;
				}
			}
			return -1;
		}

		/**
		 * Fixes a wall unit to the back of a column or lamp post on this platform, in the free cell along the platform next to
		 * the post nearest {@code target}, facing away from it. True if placed.
		 */
		private boolean onPost(TrackLayout.PlatformBand band, int target, String blockId) {
			final List<Cell> posts = new ArrayList<>();
			for (final PlannedBlock block : cells.values()) {
				if ((block.role() == Role.COLUMN || block.role() == Role.LAMP) && block.y() == StationGeometry.WALK_Y
						&& band.contains(block.u(), block.v())) {
					posts.add(new Cell(block.u(), block.y(), block.v()));
				}
			}
			posts.sort(Comparator.comparingInt((Cell post) -> Math.abs(post.v() - target)).thenComparingInt(Cell::v).thenComparingInt(Cell::u));
			for (final Cell post : posts) {
				for (final LocalDir side : new LocalDir[]{LocalDir.PLUS_V, LocalDir.MINUS_V}) {
					final int v = post.v() + (side == LocalDir.PLUS_V ? 1 : -1);
					if (band.runs(v) && !isEdge(band, post.u()) && !nearStairs(band, v) && !taken(post.u(), StationGeometry.WALK_Y, v)
							&& put(Role.FIXTURE, post.u(), StationGeometry.WALK_Y, v, side, blockId)) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Under a canopy, a platform stair is enclosed in glass in the footbridge's glazing: both flanks of the whole flight from
		 * the platform up to the bridge roof (glass balustrades, no metal rails; never on a boarding edge), across its far end
		 * above the canopy, and a roof over just that shaft. So the steps are not left standing in the open above the canopy.
		 * False if the flight has no steps (nothing to enclose).
		 */
		private boolean stairShaft(TrackLayout.PlatformBand band, StylePalette.Building b) {
			final List<Integer> flight = site.flightColumns(band);
			if (flight.isEmpty()) {
				return false;
			}
			final int left = flight.get(0) - 1;
			final int right = flight.get(flight.size() - 1) + 1;
			// steps whose headroom reaches the canopy level pass through it; the step below them ends the shaft
			final int lowest = Math.max(1, StationGeometry.CANOPY_Y - 2);
			final int top = site.stairV(StationGeometry.STAIR_STEPS);
			final int end = lowest > 1 ? site.stairV(lowest - 1) : site.landingV();
			final int side = Integer.signum(end - top);
			final LocalDir out = side > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
			final int foot = site.stairV(1);
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.BUILDING_ROOF_Y; y++) {
				for (int v = top; v != foot + side; v += side) {
					// below the canopy a flank on a boarding edge stays open
					final boolean high = y > StationGeometry.CANOPY_Y;
					if (high || !isEdge(band, left) && left >= band.uMin()) {
						put(Role.WINDOW, left, y, v, LocalDir.MINUS_U, b.bridgeWall());
					}
					if (high || !isEdge(band, right) && right <= band.uMax()) {
						put(Role.WINDOW, right, y, v, LocalDir.PLUS_U, b.bridgeWall());
					}
				}
				if (y > StationGeometry.CANOPY_Y) {
					for (int u = left + 1; u < right; u++) {
						put(Role.WINDOW, u, y, end, out, b.bridgeWall());
					}
				}
			}
			for (int u = left; u <= right; u++) {
				for (int v = top; v != end + side; v += side) {
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, (u + v) % 4 == 1 ? b.bridgeLight() : b.bridgeRoof());
				}
			}
			return true;
		}

		/**
		 * Without a canopy, a stair flight is glazed like the corridor it leaves: glass walls on both flanks from the platform
		 * up to a roof over the flight alone, open at the bottom step. A flank on a boarding edge stays open.
		 */
		private boolean stairHood(TrackLayout.PlatformBand band, StylePalette.Building b) {
			final List<Integer> flight = site.flightColumns(band);
			if (flight.isEmpty()) {
				return false;
			}
			final int left = Math.max(band.uMin(), flight.get(0) - 1);
			final int right = Math.min(band.uMax(), flight.get(flight.size() - 1) + 1);
			final boolean wallLeft = left < flight.get(0) && !isEdge(band, left);
			final boolean wallRight = right > flight.get(flight.size() - 1) && !isEdge(band, right);
			final int top = site.stairV(StationGeometry.STAIR_STEPS);
			final int bottom = site.stairV(1);
			final int side = Integer.signum(bottom - top);
			for (int v = top; v != bottom + side; v += side) {
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.BUILDING_ROOF_Y; y++) {
					if (wallLeft) {
						put(Role.WINDOW, left, y, v, LocalDir.MINUS_U, b.bridgeWall());
					}
					if (wallRight) {
						put(Role.WINDOW, right, y, v, LocalDir.PLUS_U, b.bridgeWall());
					}
				}
				for (int u = left; u <= right; u++) {
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, (u + v) % 4 == 1 ? b.bridgeLight() : b.bridgeRoof());
				}
			}
			return true;
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
					// one row down the middle: the signs are double-sided, so it reads from both tracks
					signRow(band, (band.uMin() + band.uMax()) / 2, LocalDir.MINUS_U);
				} else {
					signRow(band, backColumn(band), towardTrack(band));
				}
			}
		}

		/**
		 * Three signs side by side along v, centred on the platform's middle; shifted by 2 along v until the cells are free.
		 * Shifts run towards the platform end first and, if the end is reached, back towards its start.
		 */
		private void signRow(TrackLayout.PlatformBand band, int u, LocalDir front) {
			final int start = band.vFrom() + band.length() / 2 - 1;
			for (int v = start; v + 2 <= band.vTo(); v += 2) {
				if (placeRow(u, v, front)) {
					return;
				}
			}
			for (int v = start - 2; v >= band.vFrom(); v -= 2) {
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
						track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, StationGeometry.ENVELOPE_HEIGHT - 1, layout.trackEnd(track)));
			}
			final List<Box> headroom = new ArrayList<>();
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				headroom.add(new Box(band.uMin(), StationGeometry.WALK_Y, band.vFrom(), band.uMax(), StationGeometry.WALK_Y + StationGeometry.HEADROOM - 1,
						band.vTo()));
				supports.add(new Box(band.uMin(), -1, layout.vMin(), band.uMax(), -1, layout.trackEnd()));
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				supports.add(new Box(track.u(), -1, layout.vMin(), track.u(), -1, layout.trackEnd(track)));
				if (spec.trackBed().falls()) {
					// gravel needs a block under it
					supports.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, -2, layout.vMin(), track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, -2,
							layout.trackEnd(track)));
				}
				if (layout.deadEnd(track)) {
					// the ground under a bay track's buffer stop
					final int v = layout.trackEnd(track) + 1;
					supports.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, -1, v, track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, -1, v));
				}
			}
			if (layout.terminus()) {
				supports.add(new Box(layout.headFrom(), -1, length, layout.headTo(), -1, layout.headEnd()));
				supports.add(new Box(headDoor() - 1, -1, layout.headEnd() + 1, headDoor() + 1, -1, layout.vMax()));
			}
			if (site.building()) {
				supports.add(new Box(0, -1, buildingFrom(), layout.buildingWidth() - 1, -1, buildingTo()));
			}
			supports.addAll(extraSupport);
			return new StationPlan(spec, layout, blocks, envelopes, headroom, supports, palette.blockId(Role.FOUNDATION));
		}
	}
}
