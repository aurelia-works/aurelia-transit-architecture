package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds a {@link StationPlan} from a spec: railway geometry from {@link StationGeometry}, then roles for every cell, then
 * block ids from the {@link StylePalette}. Deterministic: the same spec always gives an equal plan.
 * <p>
 * Rules run in a fixed order and each cell holds at most one block: when two rules want the same cell the earlier rule wins
 * and the later block is skipped.
 */
public final class StationPlanner {

	private static final String PIDS = "aurelia_transit_architecture:platform_pids";
	/** What fills the bed layer under platforms when the station has a track bed. */
	private static final String PLATFORM_FILL = "minecraft:black_concrete";
	private static final String HANGING_PIDS = "aurelia_transit_architecture:hanging_platform_pids";
	private static final String HELP_POINT = "aurelia_transit_architecture:help_point";
	private static final String CEILING_SPEAKER = "aurelia_transit_architecture:ceiling_speaker";
	private static final String WALL_SPEAKER = "aurelia_transit_architecture:wall_speaker";
	/**
	 * A walk-in waiting shelter, from the bus kit: a back wall of glass two high, a seat in front of it and a roof over both at
	 * two blocks above its seat. All three face the way the wall is from the seat (the kit is placed "away from the player").
	 */
	private static final String SHELTER_GLASS = "aurelia_transit_architecture:bus_shelter_glass";
	private static final String SHELTER_ROOF = "aurelia_transit_architecture:bus_shelter_roof";
	private static final String SHELTER_SEAT = "aurelia_transit_architecture:bus_shelter_seat";
	/** Cells in a row of shelter blocks. */
	private static final int SHELTER_LENGTH = 3;
	/** The narrowest island that takes a shelter wall with a seat on each side and a walkway beside each. */
	private static final int SHELTER_ISLAND = 7;
	/** The shortest open platform that gets a shelter. */
	private static final int SHELTER_MIN_PLATFORM = 24;
	private static final String WAITING_ROOM_GLASS = "aurelia_transit_architecture:glass_wall";
	private static final String NUMBER_SIGN = "aurelia_transit_architecture:platform_number_sign";
	/** The canopy's sloping blocks (the low end points the way the block faces): the two halves of a half-block slope. */
	private static final String CANOPY_SLOPE_LOWER = "aurelia_transit_architecture:canopy_slope_lower";
	private static final String CANOPY_SLOPE_UPPER = "aurelia_transit_architecture:canopy_slope_upper";
	/** One colour per platform at a terminus gate line, in turn: a stripe on the floor and over the arcade, to follow to the gates. */
	static final List<String> PLATFORM_COLOURS = List.of("aurelia_transit_architecture:metro_tile_blue",
			"aurelia_transit_architecture:metro_tile_green", "aurelia_transit_architecture:metro_tile_orange",
			"aurelia_transit_architecture:metro_tile_yellow");

	private static final Comparator<PlannedBlock> BUILD_ORDER = Comparator.comparingInt((PlannedBlock b) -> b.role().ordinal())
			.thenComparingInt(PlannedBlock::y).thenComparingInt(PlannedBlock::v).thenComparingInt(PlannedBlock::u);

	/** How far a grand station's clock tower rises above the building block's roof, to its cornice. */
	private static final int TOWER_HEIGHT = 11;
	/** The state of every stair in a pitched roof. */
	private static final String PITCH_STATE = "half=bottom,shape=straight";
	/** Height of MTR's station name panel over a street door: on the lintel, one row over the three-high doorway. */
	private static final int MTR_NAME_Y = 4;
	/** Copper for domes, and glass for giant arched windows (whole blocks, so the arch reads from the street). */
	private static final String DOME = "minecraft:oxidized_cut_copper";
	private static final String ARCH_GLASS = "aurelia_transit_architecture:clear_float_glass";
	/** A statue's base; its figure is a quartz pillar. */
	private static final String STATUE = "minecraft:chiseled_quartz_block";

	private StationPlanner() {
	}

	public static StationPlan plan(StationSpec spec) {
		final Planning planning = new Planning(spec);
		planning.escalators();
		planning.travelators();
		planning.floors();
		planning.ramps();
		planning.railNodes();
		planning.trackBed();
		planning.bayEnds();
		planning.lifts();
		planning.building();
		planning.wings();
		planning.head();
		planning.forecourt();
		planning.footbridge();
		planning.columns();
		planning.canopyRoofs();
		planning.hallRoof();
		planning.numberSigns();
		planning.lamps();
		planning.shelters();
		planning.benchesAndBins();
		planning.interior();
		planning.headInterior();
		planning.platformEquipment();
		planning.nameSigns();
		planning.speakers();
		planning.liftButtons();
		planning.endFences();
		planning.cornerPosts();
		planning.parking();
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
			// one line per stretch its arms and edge girders carry (an arm reaches MAX_CANTILEVER out, the edge girder on it the
			// roof MAX_CANTILEVER further), each in the middle of its stretch; the stretch is the canopy's width, which reaches
			// one cell out over each boarding edge
			final int reach = 4 * StationGeometry.MAX_CANTILEVER + 1;
			final int from = canopyFrom(band);
			final int width = canopyTo(band) - from + 1;
			final int count = (width + reach - 1) / reach;
			// never on a boarding edge, where passengers step on and off
			final int low = band.trackLeft() ? band.uMin() + 1 : band.uMin();
			final int high = band.trackRight() ? band.uMax() - 1 : band.uMax();
			for (int i = 0; i < count; i++) {
				lines.add(Math.max(low, Math.min(high, from + ((2 * i + 1) * width - 1) / (2 * count))));
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

	/** The u of the footbridge piers on a platform: one per stretch the deck can overhang both ways from, in its middle. */
	static List<Integer> bridgePierLines(TrackLayout.PlatformBand band) {
		final List<Integer> lines = new ArrayList<>();
		final int reach = 2 * StationGeometry.MAX_CANTILEVER + 1;
		final int count = (band.width() + reach - 1) / reach;
		for (int i = 0; i < count; i++) {
			lines.add(band.uMin() + ((2 * i + 1) * band.width() - 1) / (2 * count));
		}
		return lines;
	}

	/** First u of a platform's canopy: one cell out over a boarding edge on the low side (clear of the trains at its height). */
	static int canopyFrom(TrackLayout.PlatformBand band) {
		return band.trackLeft() ? band.uMin() - 1 : band.uMin();
	}

	/** Last u of a platform's canopy: one cell out over a boarding edge on the high side. */
	static int canopyTo(TrackLayout.PlatformBand band) {
		return band.trackRight() ? band.uMax() + 1 : band.uMax();
	}

	/**
	 * The v of a canopy's columns along one line, from v = 2: as far apart as the girder along the line may span
	 * ({@link StationGeometry#MAX_BEAM_SPAN}), so a canopy stands on a few posts rather than a forest of them.
	 */
	static List<Integer> canopyPositions(int platformLength) {
		final List<Integer> positions = new ArrayList<>();
		final int first = 2;
		final int last = Math.max(first, platformLength - 3);
		final int bays = Math.max(1, (last - first + StationGeometry.MAX_BEAM_SPAN - 1) / StationGeometry.MAX_BEAM_SPAN);
		for (int i = 0; i <= bays; i++) {
			final int p = first + (last - first) * i / bays;
			if (positions.isEmpty() || positions.get(positions.size() - 1) != p) {
				positions.add(p);
			}
		}
		return positions;
	}

	private record Cell(int u, int y, int v) {
	}

	/** Platforms at least this long (rows, both ends included) get travelators along them. */
	static final int TRAVELATOR_PLATFORM = 72;

	/** One announcement speaker per this many blocks of platform. */
	static final int SPEAKER_SPACING = 32;
	/** A speaker fixed to a post sits this high: above the screens. */
	static final int SPEAKER_Y = StationGeometry.WALK_Y + 3;
	/** The pole an open platform's lamp stands on. */
	static final String LAMP_POLE = "aurelia_transit_architecture:sign_pole";
	/** Roughly how many blocks of platform each departure screen serves. */
	static final int SCREEN_SPACING = 30;

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

		/**
		 * A call button beside a lift door: in the shaft's front wall at {@code (u, y, v)}, with the landing on the -o side;
		 * {@code otherU} is the wall cell on the doors' other side.
		 */
		private record LiftButton(int u, int y, int v, int o, LocalDir in, int otherU) {
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
			// a screen carries the display style it is set to when built
			final String text = role == Role.NAME_SIGN ? spec.name() : screen(blockId) ? palette.screenStyle().id() : "";
			cells.put(cell, new PlannedBlock(u, y, v, role, blockId, front, text, state));
			return true;
		}

		private static boolean screen(String blockId) {
			return blockId.equals(PIDS) || blockId.equals(HANGING_PIDS) || blockId.equals("aurelia_transit_architecture:concourse_board");
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

		/** Clear half-width of the footbridge corridor: the walkway either side of its centre line. */
		private int bridgeHalf() {
			return (site.bridgeWidth() - 1) / 2;
		}

		/** Row offset from the corridor's centre line to its long wall. */
		private int bridgeWall() {
			return StationGeometry.bridgeWallOffset(site.bridgeWidth());
		}

		/** A platform's colour at the gate line. */
		/** Each platform's colour, in turn from blue; a seed starts the turn at another colour. */
		private String platformColour(TrackLayout.PlatformBand band) {
			return PLATFORM_COLOURS.get((band.number() - 1 + StationSeed.colourOffset(spec.seed(), PLATFORM_COLOURS.size())) % PLATFORM_COLOURS.size());
		}

		/**
		 * A big number beside each platform's pier, seen as you come off the stairs, hung right under whatever is above it (the
		 * deck, or a girder under it). Planned after the roofs, so it never takes a girder's cell; off the middle when a lift
		 * landing or the structure has the middle. A platform numbered over its gates at a terminus head gets no second one.
		 */
		void numberSigns() {
			if (!site.bridge()) {
				return;
			}
			final int v = site.bridgeV() - site.stairSide();
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				if (!site.served().contains(band.number()) || gateNumbered(band)) {
					continue;
				}
				final int mid = (band.uMin() + band.uMax()) / 2;
				for (int d = 0; d <= band.width(); d++) {
					final int u = mid + (d % 2 == 0 ? d / 2 : -(d + 1) / 2);
					if (u < band.uMin() || u > band.uMax() || isEdge(band, u)) {
						continue;
					}
					final int y = ceiling(u, v) - 1;
					if (y >= StationGeometry.WALK_Y + 2 && numberSign(band, u, y, v, LocalDir.PLUS_V)) {
						break;
					}
				}
			}
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
			final TrackLayout.PlatformBand street = site.street();
			if (street != null) {
				// the street entrance: paved like the platforms, railed off along the track, which nobody boards from here
				for (int v = street.vFrom(); v <= street.vTo(); v++) {
					for (int u = street.uMin(); u <= street.uMax(); u++) {
						put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
					}
					put(Role.RAILING, street.uMax(), StationGeometry.WALK_Y, v, LocalDir.PLUS_U);
				}
			}
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
			for (final TrackLayout.PlatformBand band : site.street() != null ? site.bridgeBands(layout) : layout.platforms()) {
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

		/**
		 * A handrail across each end of every platform that has no ramp there, edge cells too, so nobody walks off the end
		 * onto the tracks; a terminus's platforms run on into its concourse at the head end and stay open there. Planned last,
		 * so it only takes cells nothing else wanted.
		 */
		void endFences() {
			final Set<TrackLayout.PlatformBand> ramped = new HashSet<>(layout.rampLength() == 0 ? List.of()
					: site.street() != null ? site.bridgeBands(layout) : layout.platforms());
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (ramped.contains(band)) {
					continue;
				}
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					put(Role.RAILING, u, StationGeometry.WALK_Y, band.vFrom(), LocalDir.MINUS_V);
					if (!layout.terminus()) {
						put(Role.RAILING, u, StationGeometry.WALK_Y, band.vTo(), LocalDir.PLUS_V);
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
		 * One solid layer at y = -1 under the whole railway: ballast under every track's clearance for its whole run and across
		 * the gaps between neighbouring tracks, with a lit strip in the row at the foot of each platform wall along that
		 * platform, and black concrete under every platform floor and ramp, so the platforms stand on something and the bed
		 * has no holes.
		 */
		void trackBed() {
			final TrackBed bed = spec.trackBed();
			if (bed == TrackBed.NONE) {
				return;
			}
			for (final PlannedBlock block : List.copyOf(cells.values())) {
				if (block.y() == StationGeometry.PLATFORM_Y && (block.role().platformFloor() || block.role() == Role.RAMP_LOWER || block.role() == Role.RAMP_UPPER)) {
					put(Role.TRACK_BED, block.u(), -1, block.v(), LocalDir.PLUS_V, PLATFORM_FILL);
				}
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				for (final int side : new int[]{-1, 1}) {
					if (side < 0 ? track.platformLeft() : track.platformRight()) {
						for (int v = layout.platformFrom(track); v <= layout.platformTo(track); v++) {
							// the channel against the wall, under the platform's lip
							put(Role.TRACK_BED, track.u() + side, -1, v, side < 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U, bed.light());
						}
					}
				}
				for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
					for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
					}
				}
			}
			for (final Box gap : bedGaps()) {
				for (int u = gap.uMin(); u <= gap.uMax(); u++) {
					for (int v = gap.vMin(); v <= gap.vMax(); v++) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
					}
				}
			}
		}

		/**
		 * The strips between two neighbouring tracks' clearances where no platform stands (y = -1), as far as both tracks run:
		 * ballast fills them so the bed is one surface from wall to wall.
		 */
		private List<Box> bedGaps() {
			final List<TrackLayout.Track> tracks = new ArrayList<>(layout.tracks());
			tracks.sort(Comparator.comparingInt(TrackLayout.Track::u));
			final List<Box> gaps = new ArrayList<>();
			for (int i = 0; i + 1 < tracks.size(); i++) {
				final TrackLayout.Track a = tracks.get(i);
				final TrackLayout.Track b = tracks.get(i + 1);
				final int u0 = a.u() + StationGeometry.ENVELOPE_HALF_WIDTH + 1;
				final int u1 = b.u() - StationGeometry.ENVELOPE_HALF_WIDTH - 1;
				if (u0 > u1 || layout.platforms().stream().anyMatch(band -> band.uMin() <= u1 && band.uMax() >= u0)) {
					continue;
				}
				gaps.add(new Box(u0, -1, layout.vMin(), u1, -1, Math.min(layout.trackEnd(a), layout.trackEnd(b))));
			}
			return gaps;
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

		/**
		 * True for a row of a building's walls that carries windows: tall windows through the ground storey (y = 2..5), and two
		 * rows in the middle of the first floor and of every storey above it, with a solid band at each floor and under the roof.
		 */
		private static boolean windowRow(int y) {
			if (y < StationGeometry.UPPER_FLOOR_Y) {
				return y >= 2 && y <= StationGeometry.UPPER_FLOOR_Y - 2;
			}
			if (y < StationGeometry.BUILDING_ROOF_Y) {
				return y >= StationGeometry.UPPER_WALK_Y + 1 && y <= StationGeometry.BUILDING_ROOF_Y - 2;
			}
			final int k = Math.floorMod(y - StationGeometry.BUILDING_ROOF_Y, StationGeometry.STOREY);
			return k == 2 || k == 3;
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
			final int uS = streetWall();
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
				for (int v = site.bridgeV() - bridgeHalf(); v <= site.bridgeV() + bridgeHalf(); v++) {
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
				put(Role.RAMP_LOWER, apronU(), StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
				put(Role.RAMP_UPPER, apronU() + 1, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
				for (int y = 1; y <= 3; y++) {
					keepClear(apronU(), y, v);
					keepClear(apronU() + 1, y, v);
				}
			}
			// perimeter walls, ground storey y=1..4
			for (int v = vS; v <= vE; v++) {
				final boolean pilaster = (v - vS) % StationGeometry.BAY == 0;
				for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					if (y == 1) {
						put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.plinth());
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.plinth());
					} else if (!windowRow(y)) {
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
				if (!(site.bridge() && v >= site.bridgeV() - bridgeHalf() && v <= site.bridgeV() + bridgeHalf())) {
					put(Role.WALL, uP, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_U, b.trim());
				}
				// the upper storey, windows in its middle rows
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					if (windowRow(y) && isBayCentre(v)) {
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
				for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					if (y == 1) {
						put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.plinth());
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.plinth());
					} else if (!windowRow(y)) {
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
					if (windowRow(y) && u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0) {
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
				storey(StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * storey, uS, uP, vS, vE, true);
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
			return StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * scale.extraStoreys();
		}

		/**
		 * One more storey on a building box from {@code floorY}: the slab (a trim band round the edge, the floor with light
		 * panels inside, which light the storey below too) and three wall rows with a window row in the middle, pilasters
		 * every bay along the long walls. {@code endWindows} puts windows in the end walls too (a wing's end walls are shared).
		 */
		private void storey(int floorY, int uS, int uP, int vS, int vE, boolean endWindows) {
			storey(floorY, uS, uP, vS, vE, endWindows, false);
		}

		/**
		 * {@link #storey(int, int, int, int, int, boolean)}; with {@code allRound} the side walls take the end walls' rhythm (a
		 * window every other cell, trim every third), so a head building's street front carries on round its corners.
		 */
		private void storey(int floorY, int uS, int uP, int vS, int vE, boolean endWindows, boolean allRound) {
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
			for (int y = floorY + 1; y < floorY + StationGeometry.STOREY; y++) {
				final boolean windowRow = windowRow(y);
				for (int v = vS; v <= vE; v++) {
					final boolean pilaster = allRound ? (v - vS) % 3 == 0 : Math.floorMod(v - vS, StationGeometry.BAY) == 0;
					final boolean window = allRound ? windowRow && v >= vS + 2 && v <= vE - 2 && (v - vS) % 2 == 0
							: windowRow && !pilaster && Math.abs(Math.floorMod(v - vS, StationGeometry.BAY) - 3) <= 1;
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
				case ANTWERP, BEAUX_ARTS -> new String[]{"minecraft:oxidized_cut_copper_stairs", "minecraft:oxidized_cut_copper"};
				case AMSTERDAM, GOTHIC_BRICK, SANDSTONE, CLINKER, TOKYO, ZURICH -> new String[]{"minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles"};
				// Mumbai's red clay tiles
				case MUMBAI -> new String[]{"minecraft:brick_stairs", "minecraft:bricks"};
				case NONE, MODERN, CONCRETE, PARIS, MILAN, BERLIN, HONG_KONG, BEIJING, BANGKOK, KUALA_LUMPUR -> null;
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

		/** Copper domes, except white ones in Kuala Lumpur, stone ones in Mumbai and dark slate ones in Tokyo. */
		private String domeBlock() {
			return switch (spec.building()) {
				case KUALA_LUMPUR -> "minecraft:calcite";
				case MUMBAI -> "minecraft:cut_sandstone";
				case TOKYO -> "minecraft:deepslate_tiles";
				default -> DOME;
			};
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
				final int cu = side == LocalDir.MINUS_U ? u0 - 1 : side == LocalDir.PLUS_U ? u1 + 1 : u0 + 2;
				final int cv = side == LocalDir.MINUS_V ? v0 - 1 : side == LocalDir.PLUS_V ? v1 + 1 : vC;
				// on a head building as narrow as the tower a side face would hang outside the station's footprint
				if (cu >= 0 && cu < layout.width() && cv >= layout.vMin() && cv <= layout.vMax()) {
					put(Role.FIXTURE, cu, clockY, cv, side, clockFace);
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
								put(Role.BUILDING_ROOF, u, crown + 1, v, LocalDir.PLUS_V, domeBlock());
							}
							if (u > u0 && u < u1 && v > v0 && v < v1) {
								put(Role.BUILDING_ROOF, u, crown + 2, v, LocalDir.PLUS_V, domeBlock());
							}
						}
					}
					put(Role.BUILDING_ROOF, u0 + 2, crown + 3, vC, LocalDir.PLUS_V, domeBlock());
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
			final int uS = streetWall();
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
				final int pavilionRoof = StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY;
				for (int u = uS + 1; u <= uP - 1; u++) {
					for (int y = 1; y < (pavilions ? pavilionRoof : StationGeometry.BUILDING_ROOF_Y); y++) {
						final boolean band = y == StationGeometry.UPPER_FLOOR_Y || y == StationGeometry.BUILDING_ROOF_Y;
						final boolean window = !band && windowRow(y)
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
							if (windowBay && windowRow(y) && !band) {
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
					// at the platform the car also opens at the back, onto the platform, when the platform runs on past it;
					// at the footbridge it opens only into the corridor
					shaft(lift, b.bridgeWall(), true, b.bridgeRoof(), band.runs(lift.doorV() + 4 * lift.out()));
				}
			}
			if (site.street() != null) {
				final StationLayout.Lift street = site.platformLift(site.street(), layout);
				if (street != null) {
					shaft(street, b.bridgeWall(), true, b.bridgeRoof(), site.street().runs(street.doorV() + 4 * street.out()));
				}
			}
			final StationLayout.Lift hall = site.buildingLift(layout);
			if (hall != null) {
				// the building's own platform wall closes the shaft's far side, and its roof covers it
				shaft(hall, b.interiorWall(), false, null, false);
			}
		}

		/**
		 * Lift call buttons, at hand height beside each door. MTR's button is a thin plate on its block's front side, meant to
		 * stand in front of a wall; in the shaft's own wall it would sit flush with the car, which clips it. So it stands on the
		 * landing against a corner post, on whichever side of the doors has a free landing cell (a corridor's glass wall takes
		 * one side at the footbridge); then against the wall; and only with both sides taken does it stay in the shaft wall.
		 */
		void liftButtons() {
			final String post = palette.building().pier();
			for (final LiftButton button : buttons) {
				final int landing = button.v() - button.o();
				Integer at = null;
				for (final boolean onPost : new boolean[]{true, false}) {
					for (final int u : new int[]{button.u(), button.otherU()}) {
						final PlannedBlock holder = cells.get(new Cell(u, button.y(), button.v()));
						if (at == null && holder != null && (!onPost || holder.blockId().equals(post)) && !taken(u, button.y(), landing)) {
							at = u;
						}
					}
				}
				if (at != null) {
					put(Role.LIFT, at, button.y(), landing, button.in(), StylePalette.MTR_LIFT_BUTTONS, "unlocked=true");
				} else {
					final Cell wall = new Cell(button.u(), button.y(), button.v());
					cells.put(wall, new PlannedBlock(button.u(), button.y(), button.v(), Role.LIFT, StylePalette.MTR_LIFT_BUTTONS, button.in(), "",
							"unlocked=true"));
				}
			}
		}

		/**
		 * One shaft: a track column fixed to the inside of the side glass on the left as you step in (in the car's back row, out
		 * of the way of the doors, the glass wall left whole), with a stop at the walk level and at the upper walk
		 * level, a pair of landing doors at each stop with a call button beside them, walls, and a roof unless {@code roofId}
		 * is null. The car space stays empty from the walk level to the roof. With {@code backDoor} the back wall also has a pair of doors at the
		 * walk level only, and the car is formed double-sided: MTR opens each side only where it finds doors at that floor.
		 */
		private void shaft(StationLayout.Lift lift, String wallId, boolean farWall, String roofId, boolean backDoor) {
			final int o = lift.out();
			final LocalDir in = o > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
			final int c0 = lift.c0();
			final int c1 = c0 + 1;
			final int vD = lift.doorV();
			final int vBack = vD + 3 * o;
			// seen from the landing, facing in, the left door leaf is c0 facing +v and c1 facing -v
			final int cT = in == LocalDir.PLUS_V ? c0 : c1;
			final int low = StationGeometry.WALK_Y;
			final int high = StationGeometry.UPPER_WALK_Y;
			final int roof = StationGeometry.BUILDING_ROOF_Y;
			// u is to the right of v, so the left wall is c0 - 1 facing +v and c1 + 1 facing -v; a shaft without a far wall
			// (the building's wall closes it) keeps its track against its own glass wall
			final boolean left = !farWall || o > 0;
			// the track is fixed to the inside of the side glass, in the car's back row, out of the way of both door pairs; its
			// plate is on the side it faces, so it faces the glass, and the stop's build finds the doors two cells along it
			final int cTrack = left ? c0 : c1;
			final LocalDir toGlass = left ? LocalDir.MINUS_U : LocalDir.PLUS_U;
			for (int y = low; y <= high + 1; y++) {
				put(Role.LIFT, cTrack, y, vD + 2 * o, toGlass, y == low || y == high ? StylePalette.MTR_LIFT_FLOOR : StylePalette.MTR_LIFT_TRACK);
			}
			for (int c = c0; c <= c1; c++) {
				for (int v = vD + o; v != vBack; v += o) {
					for (int y = low; y < roof; y++) {
						keepClear(c, y, v);
					}
				}
				for (int y = low; y <= low + 2; y++) {
					keepClear(c, y, vD - o);
					if (backDoor) {
						// three rows: a roof column just past the landing would still stand in the way of whoever steps out
						for (int k = 1; k <= 3; k++) {
							keepClear(c, y, vBack + k * o);
						}
					}
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
				buttons.add(new LiftButton(c0 - 1, stop + 1, vD, o, in, c1 + 1));
			}
			if (backDoor) {
				// seen from the platform behind the shaft the pair is mirrored: the left leaf is the one away from the track
				for (int c = c0; c <= c1; c++) {
					final String side = c == cT ? "side=right" : "side=left";
					put(Role.LIFT, c, low, vBack, in.opposite(), StylePalette.MTR_LIFT_DOOR, "half=lower," + side + ",unlocked=true");
					put(Role.LIFT, c, low + 1, vBack, in.opposite(), StylePalette.MTR_LIFT_DOOR, "half=upper," + side + ",unlocked=true");
				}
				buttons.add(new LiftButton(c1 + 1, low + 1, vBack, -o, in.opposite(), c0 - 1));
			}
			final int cMax = farWall ? c1 + 1 : c1;
			for (int y = low; y < roof; y++) {
				for (int c = c0 - 1; c <= cMax; c++) {
					// a glass shaft stands on solid corner posts: the panes end on them and the call buttons are fixed to them
					final boolean corner = farWall && (c == c0 - 1 || c == cMax);
					final String id = corner ? palette.building().pier() : wallId;
					// the door row's floor at the upper level is the deck or the slab
					if (y != StationGeometry.UPPER_FLOOR_Y) {
						put(Role.WALL, c, y, vD, in.opposite(), id);
					}
					if (!backDoor || y > low + 1 || c < c0 || c > c1) {
						put(Role.WALL, c, y, vBack, in, id);
					}
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
					final boolean window = !pilaster && windowRow(y);
					put(window ? Role.WINDOW : Role.WALL, u0, y, v, LocalDir.MINUS_U, window ? b.window() : id);
					put(window ? Role.WINDOW : Role.WALL, u1, y, v, LocalDir.PLUS_U, window ? b.window() : id);
				}
			}
			// street wall with the entrance, and the platform-side lintel and clerestory over the open arcade
			for (int u = u0 + 1; u < u1; u++) {
				final boolean pilaster = (u - u0) % 3 == 0;
				for (int y = 1; y <= top; y++) {
					final String id = y == 1 ? (pilaster ? b.trim() : b.plinth()) : y == StationGeometry.UPPER_FLOOR_Y || pilaster ? b.trim() : b.wall();
					final boolean window = !pilaster && y > StationGeometry.UPPER_FLOOR_Y && windowRow(y);
					// ground-floor windows either side of the entrance
					final boolean street = window || !pilaster && windowRow(y) && Math.abs(u - door) > 2;
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
					storey(StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * storey, u0, u1, vL, vE, true, true);
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
					if (look == BuildingStyle.MUMBAI || look == BuildingStyle.GOTHIC_BRICK || look == BuildingStyle.TOKYO) {
						// a tall arch over the entrance and an arcade of arches along the ground floor: pointed (Gothic) in Mumbai
						// and London, round-headed in Tokyo
						final ArchShape shape = look == BuildingStyle.TOKYO ? ArchShape.ROUND : ArchShape.POINTED;
						giantArch(door, 3, StationGeometry.UPPER_FLOOR_Y + 2, vE, u0, u1, shape);
						for (int c = door - 9; c - 2 > u0; c -= 6) {
							giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, shape);
						}
						for (int c = door + 9; c + 2 < u1; c += 6) {
							giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, shape);
						}
					}
				}
				case ARCHED_GLASS -> {
					final int arches = look == BuildingStyle.SANDSTONE && width >= 30 ? 3 : look == BuildingStyle.CLINKER && width >= 28 ? 2 : 1;
					if (look == BuildingStyle.BANGKOK) {
						// a colonnade of white columns, one every third cell, along the whole front; the great window rises over
						// the roof with the crown (headCrown)
						for (int c = u0 + 2; c < u1 - 1; c += 3) {
							if (Math.abs(c - door) > 1) {
								columnRun(c, 1, StationGeometry.BUILDING_ROOF_Y - 1, vE, u0, u1, 1);
							}
						}
					} else if (arches == 1) {
						final int h = Math.min(top - 4, Math.max(2, width / 6));
						giantArch(door, h, top, vE, u0, u1);
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
					if (look == BuildingStyle.KUALA_LUMPUR) {
						// an arcade of pointed arches along the ground floor, and one taller round arch over the entrance
						final int h = Math.min(top - 4, Math.max(2, width / 8));
						giantArch(door, h, top, vE, u0, u1);
						for (int c = door - h - 4; c - 2 > u0; c -= 5) {
							giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, ArchShape.POINTED);
						}
						for (int c = door + h + 4; c + 2 < u1; c += 5) {
							giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, ArchShape.POINTED);
						}
					} else if (look == BuildingStyle.PARIS || width < 30) {
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
					// a curtain wall: glass between mullions every three cells, with a band at every floor; a glass hall (Utrecht,
					// Hong Kong) keeps only the first-floor band, and Munich's concrete has ribbon windows, two rows of glass in
					// every storey
					final boolean hall = look == BuildingStyle.MODERN || look == BuildingStyle.HONG_KONG || look == BuildingStyle.BEIJING;
					final boolean ribbons = look == BuildingStyle.CONCRETE;
					for (int u = u0 + 1; u < u1; u++) {
						for (int y = 1; y < roofY(); y++) {
							final boolean upper = y >= StationGeometry.BUILDING_ROOF_Y && (y - StationGeometry.BUILDING_ROOF_Y) % 4 == 0;
							final boolean floorLine = y == StationGeometry.UPPER_FLOOR_Y || !hall && upper
									|| ribbons && Math.floorMod(y - 1, 4) >= 2;
							if (floorLine || !ribbons && (u - u0) % 3 == 0) {
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
			giantArch(cu, h, top, vE, u0, u1, ArchShape.ROUND);
		}

		/** How the head of an arched window is drawn. */
		private enum ArchShape {
			/** A semicircle: as high as half the span. */
			ROUND,
			/** Two arcs of radius the full span meeting at a point (equilateral Gothic): about 1.7 times the half span. */
			POINTED,
			/** A flat arc, part of a circle, rising three tenths of the span (Bangkok's great window). */
			SEGMENTAL
		}

		/** Height of an arch's head above its springing line, for half span {@code h}. */
		private static int archRise(ArchShape shape, int h) {
			return switch (shape) {
				case ROUND -> h;
				case POINTED -> (int) Math.round(Math.sqrt(3) * h);
				case SEGMENTAL -> Math.max(1, (int) Math.round(h * 0.6));
			};
		}

		/**
		 * Distance of cell (du, k) from the arch's curve, in the curve's own radius units: below zero inside the opening, at zero
		 * on the curve. {@code k} counts rows above the springing line.
		 */
		private static double archDistance(ArchShape shape, int du, int k, int h) {
			return switch (shape) {
				case ROUND -> Math.hypot(du, k) - h;
				// each side is an arc of radius 2h centred on the far side's springing point
				case POINTED -> Math.hypot(Math.abs(du) + h, k) - 2 * h;
				case SEGMENTAL -> {
					final int r = archRise(shape, h);
					final double radius = (h * h + r * r) / (2.0 * r);
					yield Math.hypot(du, k + radius - r) - radius;
				}
			};
		}

		/** {@link #giantArch(int, int, int, int, int, int)} with a round, pointed or segmental head. */
		private void giantArch(int cu, int h, int top, int vE, int u0, int u1, ArchShape shape) {
			if (h < 2 || cu - h <= u0 || cu + h >= u1) {
				return;
			}
			final StylePalette.Building b = palette.building();
			final int spring = top - archRise(shape, h);
			// the ring and glazing bars in a stone that stands out from the wall
			final String frame = switch (spec.building()) {
				case SANDSTONE -> ata("antwerp_ornate_limestone");
				case CLINKER, ANTWERP, ZURICH -> ata("frankfurt_sandstone_rusticated");
				case BANGKOK -> "minecraft:smooth_quartz";
				default -> b.trim();
			};
			// Mumbai's arch stones alternate red and cream
			final boolean banded = spec.building() == BuildingStyle.MUMBAI;
			for (int u = cu - h; u <= cu + h; u++) {
				final int du = Math.abs(u - cu);
				for (int y = 1; y <= top; y++) {
					final boolean inside;
					final boolean ring;
					if (y <= spring) {
						inside = du < h;
						ring = du == h;
					} else {
						final double d = archDistance(shape, du, y - spring, h);
						inside = d < -0.5;
						ring = !inside && d < 0.5;
					}
					final boolean bar = y == StationGeometry.UPPER_FLOOR_Y || y > 3 && du % 3 == 0;
					if (ring && banded && y > spring && (u + y) % 2 == 0) {
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, "minecraft:smooth_red_sandstone");
					} else if (ring || inside && bar) {
						put(Role.WALL, u, y, vE, LocalDir.PLUS_V, frame);
					} else if (inside) {
						put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, ARCH_GLASS);
					}
				}
			}
		}

		/**
		 * A barrel vault standing on the head building's roof at {@code top}, centred on {@code cu}, {@code h} cells each side
		 * and a segmental arch in section, from the platform side ({@code vL}) to the street front ({@code vE}): its street end
		 * a great glazed window with a white frame, glazing bars every three cells and a clock, its platform end glazed, its
		 * shell in smooth stone.
		 */
		private void lunetteVault(int cu, int h, int top, int vL, int vE) {
			final int rise = archRise(ArchShape.SEGMENTAL, h);
			for (int u = cu - h; u <= cu + h; u++) {
				final int du = Math.abs(u - cu);
				for (int k = 1; k <= rise + 1; k++) {
					final double d = archDistance(ArchShape.SEGMENTAL, du, k, h);
					if (d >= 0.5) {
						continue;
					}
					final boolean ring = d > -0.5;
					for (int v = vL; v <= vE; v++) {
						final boolean end = v == vL || v == vE;
						if (ring) {
							put(Role.BUILDING_ROOF, u, top + k, v, LocalDir.PLUS_V, v == vE ? "minecraft:smooth_quartz" : "minecraft:smooth_stone");
						} else if (end) {
							final boolean bar = v == vE && (du % 3 == 0 || k == rise / 2);
							put(bar ? Role.WALL : Role.WINDOW, u, top + k, v, v == vL ? LocalDir.MINUS_V : LocalDir.PLUS_V,
									bar ? "minecraft:smooth_quartz" : ARCH_GLASS);
						} else {
							keepClear(u, top + k, v);
						}
					}
				}
			}
			put(Role.FIXTURE, cu, top + Math.max(2, rise - 2), vE + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
		}

		/** Two columns side by side in the street wall from the ground to {@code top}, from {@code u} going {@code dir}. */
		private void columnPair(int u, int dir, int top, int vE, int u0, int u1) {
			columnRun(u, dir, top, vE, u0, u1, 2);
		}

		/** {@code count} columns side by side in the street wall from the ground to {@code top}, from {@code u} going {@code dir}. */
		private void columnRun(int u, int dir, int top, int vE, int u0, int u1, int count) {
			for (int k = 0; k < count; k++) {
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
					if (look == BuildingStyle.MUMBAI && width >= 20) {
						// over the entrance a clock tower crowned by a stone onion dome, tall spired Gothic towers at both ends, and
						// where there is room a smaller domed pavilion halfway between them on each side
						final int centre = Math.max(2, Math.min(4, width / 12));
						final int corner = width >= 30 ? 3 : 2;
						octTower(door, vE - centre, centre, top, grand ? 9 : 6, OctCap.ONION, true);
						octTower(u0 + corner, vE - corner, corner, top, grand ? 11 : 8, OctCap.SPIRE, false);
						octTower(u1 - corner, vE - corner, corner, top, grand ? 11 : 8, OctCap.SPIRE, false);
						final int gap = door - centre - (u0 + 2 * corner);
						if (gap >= 8) {
							octTower((u0 + 2 * corner + door - centre) / 2, vE - 2, 2, top, 2, OctCap.ONION, false);
							octTower((u1 - 2 * corner + door + centre) / 2, vE - 2, 2, top, 2, OctCap.ONION, false);
						}
					} else if (look == BuildingStyle.TOKYO && width >= 20) {
						// octagonal corner pavilions under dark slate domes, and over the entrance a domed clock tower or a gable
						final int r = Math.max(2, Math.min(3, width / 12));
						octTower(u0 + r, vE - r, r, top, 4, OctCap.DOME, false);
						octTower(u1 - r, vE - r, r, top, 4, OctCap.DOME, false);
						if (grand) {
							octTower(door, vE - 2, 2, top, 7, OctCap.DOME, true);
						} else {
							gable(door, top, vE);
						}
					} else if (look == BuildingStyle.AMSTERDAM && width >= 30) {
						// twin clock towers at a quarter and three quarters of the front, a gable between them
						tower(top, u0 + width / 4 - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
						tower(top, u1 - width / 4 - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
						gable(door, top, vE);
					} else if (grand || look == BuildingStyle.GOTHIC_BRICK) {
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
					} else if (look == BuildingStyle.BANGKOK) {
						// the end of the vaulted hall over the colonnade: a great segmental window three fifths of the front wide
						// with a clock in it, between square end pavilions a storey higher
						lunetteVault(door, Math.max(3, width * 3 / 10), top, vL, vE);
						if (width >= 24) {
							tower(top, u0, towerV, 4, LocalDir.MINUS_V, Cap.FLAT, false, false);
							tower(top, u1 - 4, towerV, 4, LocalDir.MINUS_V, Cap.FLAT, false, false);
						}
					} else if (look == BuildingStyle.CLINKER) {
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
					if (look == BuildingStyle.KUALA_LUMPUR) {
						// a flat-topped centre block with a slim pinnacle at each corner, wide octagonal towers at the corners of the
						// front under open domed pavilions, a small domed kiosk a quarter of the front out on each side, and
						// pinnacles along the parapet between
						final int centre = Math.max(3, Math.min(6, width / 8));
						attic(door, centre, 2, top, vE, u0, u1, false);
						for (final int u : new int[]{door - centre, door + centre}) {
							pinnacle(u, top + 4, vE);
							pinnacle(u, top + 4, vE - 3);
						}
						final int r = Math.max(2, Math.min(4, width / 12));
						if (width >= 20) {
							// a cell in from the corners, so the pavilions' flared eaves stay over the building
							octTower(u0 + r + 1, vE - r - 1, r, top, grand ? 8 : 5, OctCap.CHHATRI, false);
							octTower(u1 - r - 1, vE - r - 1, r, top, grand ? 8 : 5, OctCap.CHHATRI, false);
						}
						for (final int side : new int[]{-1, 1}) {
							final int kiosk = door + side * (width / 4);
							if (Math.abs(kiosk - door) > centre + 2) {
								chhatri(kiosk, top + 1, vE - 1);
							}
						}
						for (int u = u0 + 2 * r + 4; u <= u1 - 2 * r - 4; u += 4) {
							if (Math.abs(u - door) > centre + 1 && Math.abs(Math.abs(u - door) - width / 4) > 2) {
								pinnacle(u, top + 2, vE);
							}
						}
					} else if (look == BuildingStyle.MILAN && grand) {
						// a stepped centre: a wide attic and a taller narrow one on it
						attic(door, half, 3, top, vE, u0, u1, false);
						attic(door, Math.max(2, half / 2), 3, top + 4, vE, u0, u1, true);
					} else {
						attic(door, half, look == BuildingStyle.PARIS ? 2 : 3, top, vE, u0, u1, true);
					}
					if (look == BuildingStyle.PARIS) {
						// statues along the parapet
						for (int u = u0 + 3; u <= u1 - 3; u += 4) {
							statue(u, top + 2, vE);
						}
					}
					roof(top, u0, u1, vL, vE, null);
				}
				case GLASS_MODERN -> {
					// waves over glass halls: Hong Kong's tall white sweep, Utrecht's low one, and Beijing's shallow steel crown
					// under its deep eaves
					switch (look) {
						case HONG_KONG -> waveRoof(top, u0, u1, vL, vE, Math.max(4, Math.min(12, width / 4)), "minecraft:smooth_quartz_stairs",
								"minecraft:smooth_quartz");
						case MODERN -> waveRoof(top, u0, u1, vL, vE, Math.max(2, Math.min(4, width / 10)), "minecraft:polished_diorite_stairs",
								"minecraft:polished_diorite");
						case BEIJING -> waveRoof(top, u0, u1, vL, vE, Math.max(3, Math.min(6, width / 8)), "minecraft:polished_andesite_stairs",
								"minecraft:polished_andesite");
						default -> {
						}
					}
					if (look == BuildingStyle.BERLIN && width >= 20) {
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

		/**
		 * A small domed kiosk (a chhatri) three cells across standing on the roof at {@code y}: four white posts, a slab, a
		 * stepped dome and a finial, centred on cell ({@code cu}, {@code cv}).
		 */
		private void chhatri(int cu, int y, int cv) {
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					final boolean corner = du != 0 && dv != 0;
					if (corner) {
						put(Role.WALL, cu + du, y, cv + dv, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
						put(Role.WALL, cu + du, y + 1, cv + dv, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
					}
					put(Role.BUILDING_ROOF, cu + du, y + 2, cv + dv, LocalDir.PLUS_V, "minecraft:smooth_quartz_slab", "type=bottom");
				}
			}
			put(Role.BUILDING_ROOF, cu, y + 2, cv, LocalDir.PLUS_V, "minecraft:smooth_quartz");
			put(Role.BUILDING_ROOF, cu, y + 3, cv, LocalDir.PLUS_V, "minecraft:smooth_quartz");
			put(Role.BUILDING_ROOF, cu, y + 4, cv, LocalDir.PLUS_V, "minecraft:end_rod", "facing=up");
		}

		/** How an octagonal tower ends. */
		private enum OctCap {
			/** A stepped stone spire with a pinnacle at every corner (Mumbai's Gothic turrets). */
			SPIRE,
			/** An open domed pavilion on eight posts (Kuala Lumpur's corner towers). */
			CHHATRI,
			/** A bulbous dome swelling out over its drum, with a figure on top (Mumbai's central tower). */
			ONION,
			/** A round dome (Tokyo's corner pavilions). */
			DOME
		}

		/** Whether (du, dv) lies inside an octagon of radius {@code r}: a square with its corners cut at 45 degrees. */
		private static boolean octagon(int du, int dv, int r) {
			return r >= 0 && Math.max(Math.abs(du), Math.abs(dv)) <= r && Math.abs(du) + Math.abs(dv) <= r + r / 2;
		}

		private static LocalDir facing(int du, int dv) {
			return Math.abs(du) >= Math.abs(dv) ? (du < 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U) : (dv < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V);
		}

		/**
		 * An octagonal tower of radius {@code r} standing on the roof at {@code top}, centred on ({@code cu}, {@code cv}),
		 * {@code height} rows of hollow shaft with a band at its foot and crown and slit windows on its four main faces, a
		 * solid floor over the shaft, then its {@code cap}. A clock goes on the street face when {@code clock}. Its inside is
		 * kept clear, so a roof planned later leaves it open.
		 */
		private void octTower(int cu, int cv, int r, int top, int height, OctCap cap, boolean clock) {
			final StylePalette.Building b = palette.building();
			final int crown = top + height;
			for (int du = -r; du <= r; du++) {
				for (int dv = -r; dv <= r; dv++) {
					if (!octagon(du, dv, r)) {
						continue;
					}
					final boolean ring = !octagon(du, dv, r - 1);
					for (int y = top + 1; y < crown; y++) {
						if (!ring) {
							keepClear(cu + du, y, cv + dv);
							continue;
						}
						final boolean band = y == top + 1 || y == crown - 1 || (y - top) % 5 == 0;
						final boolean slit = (du == 0 || dv == 0) && !band && (y - top) % 5 >= 2;
						put(slit ? Role.WINDOW : Role.WALL, cu + du, y, cv + dv, facing(du, dv), slit ? b.window() : band ? b.trim() : b.wall());
					}
					put(Role.BUILDING_ROOF, cu + du, crown, cv + dv, facing(du, dv), ring ? b.cornice() : b.roof());
				}
			}
			if (clock) {
				put(Role.FIXTURE, cu, crown - 3, cv + r + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
			}
			switch (cap) {
				case SPIRE -> spire(cu, cv, r, crown);
				case CHHATRI -> openChhatri(cu, cv, r, crown);
				case ONION -> onionDome(cu, cv, r, crown);
				case DOME -> {
					roundDome(cu, cv, r, crown + 1);
					put(Role.BUILDING_ROOF, cu, crown + 1 + r, cv, LocalDir.PLUS_V, "minecraft:lightning_rod");
				}
			}
		}

		/**
		 * A stepped spire of dark slate on an octagon of radius {@code r} from {@code y}: rings narrowing every second row to a
		 * point, a slim pinnacle on each of the four corners of the base, and a finial.
		 */
		private void spire(int cu, int cv, int r, int y) {
			final String solid = "minecraft:deepslate_tiles";
			int k = 1;
			for (int rr = r - 1; rr >= 0; rr--) {
				for (int step = 0; step < 2; step++, k++) {
					for (int du = -rr; du <= rr; du++) {
						for (int dv = -rr; dv <= rr; dv++) {
							if (octagon(du, dv, rr)) {
								put(Role.BUILDING_ROOF, cu + du, y + k, cv + dv, LocalDir.PLUS_V, solid);
							}
						}
					}
				}
			}
			put(Role.BUILDING_ROOF, cu, y + k, cv, LocalDir.PLUS_V, solid);
			put(Role.BUILDING_ROOF, cu, y + k + 1, cv, LocalDir.PLUS_V, "minecraft:lightning_rod");
			if (r >= 2) {
				for (final int[] corner : new int[][]{{-r, -r / 2}, {r, -r / 2}, {-r, r / 2}, {r, r / 2}}) {
					put(Role.BUILDING_ROOF, cu + corner[0], y + 1, cv + corner[1], LocalDir.PLUS_V, palette.building().trim());
					put(Role.BUILDING_ROOF, cu + corner[0], y + 2, cv + corner[1], LocalDir.PLUS_V, "minecraft:end_rod", "facing=up");
				}
			}
		}

		/**
		 * An open pavilion on the octagon of radius {@code r} from {@code y}: posts three high at its eight corners, a slab eave
		 * flaring a cell out past them, and a tall dome in the look's dome stone with a finial.
		 */
		private void openChhatri(int cu, int cv, int r, int y) {
			final int s = r / 2;
			for (final int[] post : new int[][]{{r, s}, {r, -s}, {-r, s}, {-r, -s}, {s, r}, {-s, r}, {s, -r}, {-s, -r}}) {
				for (int k = 1; k <= 3; k++) {
					put(Role.WALL, cu + post[0], y + k, cv + post[1], LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
				}
			}
			for (int du = -r - 1; du <= r + 1; du++) {
				for (int dv = -r - 1; dv <= r + 1; dv++) {
					if (!octagon(du, dv, r + 1)) {
						continue;
					}
					if (octagon(du, dv, r - 1)) {
						for (int k = 1; k <= 3; k++) {
							keepClear(cu + du, y + k, cv + dv);
						}
					}
					put(Role.BUILDING_ROOF, cu + du, y + 4, cv + dv, LocalDir.PLUS_V, "minecraft:smooth_quartz_slab", "type=top");
				}
			}
			onionDome(cu, cv, Math.max(1, r - 1), y + 4);
		}

		/**
		 * A solid round dome of radius {@code r} whose bottom row is {@code y}: rings of the look's dome block narrowing like a
		 * hemisphere, the top cell a finial seat.
		 */
		private void roundDome(int cu, int cv, int r, int y) {
			for (int k = 0; k < r; k++) {
				final double radius = Math.sqrt(Math.max(0, (r + 0.5) * (r + 0.5) - (k + 0.5) * (k + 0.5)));
				for (int du = -r; du <= r; du++) {
					for (int dv = -r; dv <= r; dv++) {
						if (Math.hypot(du, dv) <= radius) {
							put(Role.BUILDING_ROOF, cu + du, y + k, cv + dv, LocalDir.PLUS_V, domeBlock());
						}
					}
				}
			}
		}

		/**
		 * An onion dome on the octagon of radius {@code r} at {@code y}: a shell that swells a third wider than its drum then
		 * draws in to a point, {@code 2r + 1} rows high, with a figure on a short pillar and a finial on top.
		 */
		private void onionDome(int cu, int cv, int r, int y) {
			final int rows = 2 * r + 1;
			final double[] radius = new double[rows + 1];
			for (int k = 0; k <= rows; k++) {
				final double t = (double) k / rows;
				radius[k] = r * (t < 0.3 ? 1 + t : 1.3 * Math.cos((t - 0.3) / 0.7 * Math.PI / 2)) + 0.3;
			}
			final int reach = (int) Math.ceil(1.3 * r + 0.3);
			for (int k = 1; k <= rows; k++) {
				for (int du = -reach; du <= reach; du++) {
					for (int dv = -reach; dv <= reach; dv++) {
						final double d = Math.hypot(du, dv);
						if (d > radius[k]) {
							continue;
						}
						if (k == rows || d > Math.min(radius[k], radius[Math.min(rows, k + 1)]) - 1.2) {
							put(Role.BUILDING_ROOF, cu + du, y + k, cv + dv, LocalDir.PLUS_V, domeBlock());
						} else {
							keepClear(cu + du, y + k, cv + dv);
						}
					}
				}
			}
			put(Role.BUILDING_ROOF, cu, y + rows + 1, cv, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
			put(Role.BUILDING_ROOF, cu, y + rows + 2, cv, LocalDir.PLUS_V, "minecraft:lightning_rod");
		}

		/** A slim pinnacle on the parapet at {@code y}: a white shaft two high, a slab, a dome-stone cap and a finial. */
		private void pinnacle(int u, int y, int v) {
			put(Role.WALL, u, y, v, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
			put(Role.WALL, u, y + 1, v, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
			put(Role.BUILDING_ROOF, u, y + 2, v, LocalDir.PLUS_V, domeBlock());
			put(Role.BUILDING_ROOF, u, y + 3, v, LocalDir.PLUS_V, "minecraft:end_rod", "facing=up");
		}

		/** A statue two cells high from {@code y}: a carved base and a pillar figure. */
		private void statue(int u, int y, int v) {
			put(Role.WALL, u, y, v, LocalDir.PLUS_V, STATUE);
			put(Role.WALL, u, y + 1, v, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
		}

		/**
		 * A roof sweeping up from both side walls to a crest {@code crest} rows over the middle, on the flat roof at
		 * {@code top}: {@code stair} steps on the slopes and {@code solid} blocks under them where the slope is steep, so the
		 * shell is closed. Its ends are glazed gables with a mullion every third cell: the head building's own wave.
		 */
		private void waveRoof(int top, int u0, int u1, int vL, int vE, int crest, String stair, String solid) {
			final StylePalette.Building b = palette.building();
			final int span = u1 - u0;
			final int[] surface = new int[span + 1];
			for (int u = u0; u <= u1; u++) {
				surface[u - u0] = top + (int) Math.round(crest * Math.sin(Math.PI * (u - u0) / span));
			}
			final int[] low = steps(surface, false);
			for (int u = u0; u <= u1; u++) {
				final int i = u - u0;
				final int y0 = surface[i];
				if (y0 <= top) {
					continue;
				}
				final LocalDir rising = i < span && surface[i + 1] > y0 ? LocalDir.PLUS_U : i > 0 && surface[i - 1] > y0 ? LocalDir.MINUS_U : null;
				for (int v = vL; v <= vE; v++) {
					for (int y = Math.max(top + 1, low[i]); y < y0; y++) {
						put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, solid);
					}
					if (rising != null) {
						put(Role.BUILDING_ROOF, u, y0, v, rising, stair, PITCH_STATE);
					} else {
						put(Role.BUILDING_ROOF, u, y0, v, LocalDir.PLUS_V, solid);
					}
					if (v == vL || v == vE) {
						for (int y = top + 1; y < Math.max(top + 1, low[i]); y++) {
							final boolean mullion = (u - u0) % 3 == 0;
							put(mullion ? Role.WALL : Role.WINDOW, u, y, v, v == vL ? LocalDir.MINUS_V : LocalDir.PLUS_V, mullion ? b.trim() : b.window());
						}
					}
				}
			}
		}

		/**
		 * A roof running on two cells past the street wall at {@code y}, across the whole front: a deep street canopy, or with
		 * {@code upturned} its outer row turned up a cell and its corners two, like a Chinese station roof (the station's footprint
		 * ends two cells past the front, at the foot of the entrance ramps).
		 */
		private void overhang(int y, int u0, int u1, int vE, boolean upturned) {
			final StylePalette.Building b = palette.building();
			final int depth = 2;
			for (int u = u0; u <= u1; u++) {
				for (int v = vE + 1; v <= vE + depth; v++) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, b.roof());
				}
				if (upturned) {
					put(Role.BUILDING_ROOF, u, y + 1, vE + depth, LocalDir.PLUS_V, b.trim());
					// the corners sweep up another row
					if (u - u0 < 2 || u1 - u < 2) {
						put(Role.BUILDING_ROOF, u, y + 2, vE + depth, LocalDir.PLUS_V, b.trim());
					}
				}
			}
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
			// the gate line: a bank centred on the walk from the entrance, glass barriers out to the side walls (run-off for the
			// staff and the crowd), a wide gate in the middle
			final int half = Math.max(2, Math.min((inner - 4) / 2, layout.tracks().size() + 1));
			for (int u = u0 + 1; u < u1; u++) {
				final int d = u - door;
				if (Math.abs(d) <= half) {
					final String kind = Math.abs(d) == half ? "kind=end" : d == 0 ? "kind=wide" : "kind=gate";
					// the green "go" side faces the ticket hall, where people come in; the red X faces the concourse
					put(Role.GATE, u, StationGeometry.WALK_Y, gateV, LocalDir.PLUS_V, palette.blockId(Role.GATE), kind);
				} else {
					put(Role.BARRIER, u, StationGeometry.WALK_Y, gateV, LocalDir.PLUS_V);
				}
			}
			// a big number under the lintel over each platform end
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (gateNumbered(band)) {
					numberSign(band, (band.uMin() + band.uMax() + 1) / 2, StationGeometry.UPPER_FLOOR_Y - 1, vL, LocalDir.PLUS_V);
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
					tallFixture(u0 + 1, 1, gateV + 7 + i, LocalDir.PLUS_U, "mtr:ticket_machine");
				}
				put(Role.FIXTURE, door - 3, StationGeometry.WALK_Y, vE - 2, LocalDir.PLUS_V, "aurelia_transit_architecture:information_pillar");
			} else {
				// too small for rooms: a ticket window in the side wall's corner by the entrance, the machines opposite
				put(Role.WALL, u0 + 1, 1, vE - 1, LocalDir.PLUS_U, b.interiorWall());
				put(Role.FIXTURE, u0 + 1, 2, vE - 1, LocalDir.PLUS_U, "aurelia_transit_architecture:booth_window");
				for (int i = 0; i < 2 && vE - 1 - i > gateV + 1; i++) {
					tallFixture(u1 - 1, 1, vE - 1 - i, LocalDir.MINUS_U, "mtr:ticket_machine");
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
			for (int u = door - 1; u <= door + 1; u++) {
				put(Role.FIXTURE, u, MTR_NAME_Y, vE + 1, LocalDir.PLUS_V, StylePalette.MTR_STATION_NAME);
			}
			tallFixture(door + 2, 1, vE - 1, LocalDir.MINUS_V, StylePalette.MTR_TACTILE_MAP);
		}

		/** v of a terminus head's gate line: about two fifths of the hall from the street wall, the ticket hall at least 7 deep. */
		private int headGateV() {
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
		private void gallery(int side, int width, int u0, int u1, int vL, int gateV, StylePalette.Building b) {
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
				if (v > lift.doorV()) {
					put(Role.RAILING, edge, StationGeometry.UPPER_WALK_Y, v, side < 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U);
				}
				// a pier under the open edge every bay, clear of the lift's landing
				if (v > lift.doorV() + 1 && Math.floorMod(v - vL, StationGeometry.BAY) == 0) {
					for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
						put(Role.WALL, edge, y, v, LocalDir.PLUS_V, b.pier());
					}
				}
			}
			// benches against the wall, looking out over the concourse
			if (spec.furniture()) {
				for (int v = lift.doorV() + 3; v < top - 1; v += 3) {
					put(Role.BENCH, wall - side, StationGeometry.UPPER_WALK_Y, v, side < 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U, b.bench());
				}
			}
		}

		/**
		 * A closed room of the ticket hall: walls four high (2.7 m) and a ceiling over them, with a doorway {@code doorWidth}
		 * wide from {@code (doorU, doorV)} along v in its {@code doorU} wall.
		 */
		private void room(int ua, int ub, int va, int vb, String wallId, int doorU, int doorV, int doorWidth) {
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
		private void ticketOffice(int u, int v, StylePalette.Building b) {
			for (final int w : new int[]{v + 1, v + 2}) {
				put(Role.WALL, u + 3, 1, w, LocalDir.PLUS_U, b.interiorWall());
				put(Role.FIXTURE, u + 3, 2, w, LocalDir.PLUS_U, "aurelia_transit_architecture:booth_window");
			}
			room(u, u + 3, v, v + 3, b.interiorWall(), u, v + 1, 0);
		}

		/** Toilets against the u1 wall: a tiled 5 x 5 room ending at {@code u + 4}, its doorway facing the hall, basins inside. */
		private void toilets(int u, int v, StylePalette.Building b) {
			room(u, u + 4, v, v + 4, ata("metro_pale_concrete"), u, v + 2, 1);
			for (final int w : new int[]{v + 1, v + 3}) {
				put(Role.FIXTURE, u + 3, 1, w, LocalDir.MINUS_U, "minecraft:cauldron");
			}
		}

		/** A shop unit against the u1 wall from {@code v} to {@code vb}: a glazed front with a two-wide door, shelves and a counter. */
		private void shop(int u, int v, int vb, StylePalette.Building b) {
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
		private void concourseSeats(int uFrom, int uTo, int door, int vL, int gateV, StylePalette.Building b) {
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
			final int wallLo = vB - bridgeWall();
			final int wallHi = vB + bridgeWall();
			// keep the stair corridors and landings clear before the deck and walls go up
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				for (final StationLayout.Flight flight : site.flights(band)) {
					for (final int u : flight.columns()) {
						for (int y = 1; y <= 3; y++) {
							keepClear(u, y, flight.landingV());
						}
						// two clear above every step, and a third: stepping up from it your head passes under the cell above it
						for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
							keepClear(u, y + 1, flight.stepV(y));
							keepClear(u, y + 2, flight.stepV(y));
							keepClear(u, y + 3, flight.stepV(y));
						}
					}
				}
			}
			// deck, out to and under both corridor walls, so the stair exits have a floor and the walls do not float
			for (int u = from; u <= to; u++) {
				for (int v = wallLo; v <= wallHi; v++) {
					// lights only under the walkway: in a wall row one sits under the glass, or in a lift's doorway, half seen
					// through the glass from the platform
					if ((u + v) % 5 == 0 && Math.abs(v - vB) <= bridgeHalf()) {
						// the building's recessed light: a full block with the light underneath, so the walkway has no pit
						put(Role.BRIDGE_DECK, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
					} else {
						put(Role.BRIDGE_DECK, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.deck());
					}
				}
			}
			// corridor walls (glass, facing outwards); each wall opens where a flight leaves through it
			for (int u = from; u <= to; u++) {
				boolean exitLo = false;
				boolean exitHi = false;
				for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
					if (u < band.uMin() || u > band.uMax()) {
						continue;
					}
					for (final StationLayout.Flight flight : site.flights(band)) {
						if (opening(band, flight).contains(u)) {
							exitLo |= flight.wallV() == wallLo;
							exitHi |= flight.wallV() == wallHi;
						}
					}
				}
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					final boolean low = y <= StationGeometry.UPPER_WALK_Y + 1;
					if (!(low && exitLo)) {
						put(Role.WINDOW, u, y, wallLo, LocalDir.MINUS_V, b.bridgeWall());
					}
					if (!(low && exitHi)) {
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
			// each flight glazed (or walled) and roofed like the corridor it leaves
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				for (final StationLayout.Flight flight : site.flights(band)) {
					if (roof(band) == RoofType.CANOPY) {
						stairShaft(band, flight, b);
					} else {
						stairHood(band, flight, b);
					}
				}
			}
			// piers on every platform at the bridge line, up to the deck: down the middle, or evenly spaced on a platform too wide
			// for the deck to overhang one
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				for (final int uPier : bridgePierLines(band)) {
					for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
						put(Role.BRIDGE_PIER, uPier, y, vB, LocalDir.PLUS_V, b.pier());
					}
				}
			}
			// flanking piers at the stair landings (never on the landing itself) so the canopy ends stay carried; under a canopy
			// they stop below it, so the canopy runs over them
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				final int flankTop = roof(band) == RoofType.CANOPY ? StationGeometry.CANOPY_Y : StationGeometry.UPPER_FLOOR_Y;
				for (final StationLayout.Flight flight : site.flights(band)) {
					final List<Integer> open = opening(band, flight);
					final int landing = flight.landingV();
					for (final int su : open) {
						for (final int du : new int[]{-1, 1}) {
							final int u = su + du;
							if (u < band.uMin() || u > band.uMax() || isEdge(band, u) || open.contains(u)) {
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
			}
			// platform stairs with landings kept clear
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				for (final StationLayout.Flight flight : site.flights(band)) {
					final List<Integer> columns = flight.columns();
					for (final int u : columns) {
						for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
							put(Role.STAIR, u, y, flight.stepV(y), flight.front(), b.stair());
						}
					}
					// balustrades on the outer flanks of the whole flight, from the floor up to each step, so no rail hangs at
					// head height over the platform (never at the canopy roof level, which the canopy fills, never on boarding
					// edges)
					final int left = columns.get(0) - 1;
					final int right = columns.get(columns.size() - 1) + 1;
					for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
						final int v = flight.stepV(y);
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
			}
			// six-foot piers where the deck span between supports exceeds 12
			final List<Integer> supports = new ArrayList<>();
			supports.add(from);
			for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
				supports.addAll(bridgePierLines(band));
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
				for (int i = 0; i <= StationLayout.ESCALATOR_TOP; i++) {
					final int v = site.escalatorV(i);
					final int y = StationLayout.escalatorY(i);
					for (final int u : columns) {
						final String side = u == left ? "left" : "right";
						put(Role.ESCALATOR, u, y, v, up, StylePalette.MTR_ESCALATOR_STEP, "side=" + side + ",direction=true,status=true");
						put(Role.ESCALATOR, u, y + 1, v, up, StylePalette.MTR_ESCALATOR_SIDE, "side=" + side);
						for (int below = StationGeometry.WALK_Y; below < y; below++) {
							put(Role.BRIDGE_PIER, u, below, v, up, palette.building().pier());
						}
						if (i < StationLayout.ESCALATOR_TOP) {
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

		/** Shortest travelator run, landings included; and the longest platform stretch before a gap to cross. */
		private static final int MIN_TRAVELATOR = 8;
		private static final int MAX_PLATFORM_TRAVELATOR = 16;

		/**
		 * MTR escalators laid flat as moving walkways, planned before the floors so those leave them their cells. Only on the
		 * platforms (in the footbridge corridor they looked wrong and are left out). Along a platform at least {@link #TRAVELATOR_PLATFORM} long: two wide in a lane
		 * clear of the column lines, away from the stairs and platform ends, in stretches of at most
		 * {@link #MAX_PLATFORM_TRAVELATOR} with a gap to cross between, each carrying passengers towards the footbridge or the
		 * terminus head (outwards to the platform ends without either).
		 */
		void travelators() {
			if (!spec.travelators()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (band.length() >= TRAVELATOR_PLATFORM) {
					platformTravelators(band);
				}
			}
		}

		private void platformTravelators(TrackLayout.PlatformBand band) {
			final int c = travelatorLane(band);
			if (c < 0) {
				return;
			}
			// rows kept for the stairs, escalators, lifts and the bridge's own piers
			final java.util.Set<Integer> blocked = new java.util.HashSet<>();
			for (final StationLayout.Flight flight : site.flights(band)) {
				final int a = Math.min(flight.wallV(), flight.landingV());
				final int z = Math.max(flight.wallV(), flight.landingV());
				for (int v = a - 3; v <= z + 3; v++) {
					blocked.add(v);
				}
			}
			if (site.hasEscalator(band)) {
				for (int v = site.escalatorFootV() - 3; v <= site.escalatorFootV() + 3; v++) {
					blocked.add(v);
				}
			}
			final StationLayout.Lift lift = site.platformLift(band, layout);
			if (lift != null) {
				for (int v = lift.vMin() - 2; v <= lift.vMax() + 2; v++) {
					blocked.add(v);
				}
			}
			if (site.bridge()) {
				for (int v = site.bridgeV() - 3; v <= site.bridgeV() + 3; v++) {
					blocked.add(v);
				}
			}
			final int target = site.bridge() ? site.bridgeV() : layout.terminus() ? layout.platformLength() : -1;
			final int first = band.vFrom() + 6;
			final int last = band.vTo() - 6;
			int v = first;
			while (v <= last) {
				if (blocked.contains(v)) {
					v++;
					continue;
				}
				int end = v;
				while (end + 1 <= last && !blocked.contains(end + 1) && end + 1 - v + 1 <= MAX_PLATFORM_TRAVELATOR) {
					end++;
				}
				if (end - v + 1 >= MIN_TRAVELATOR) {
					final int middle = (v + end) / 2;
					final boolean plus = target >= 0 ? target > middle : middle > (band.vFrom() + band.vTo()) / 2;
					final LocalDir way = plus ? LocalDir.PLUS_V : LocalDir.MINUS_V;
					for (int w = v; w <= end; w++) {
						// two lanes side by side, one each way, as real paired walkways run: the balustrades meet in the
						// middle (both "right" of their own direction) and passengers step on from the outside
						travelatorStep(c, StationGeometry.PLATFORM_Y, w, way, "right");
						travelatorStep(c + 1, StationGeometry.PLATFORM_Y, w, way.opposite(), "right");
					}
				}
				// a gap of three to cross before the next stretch
				v = end + 4;
			}
		}

		/**
		 * The travelator's two columns on a platform (the first returned): off the column lines and the bridge pier lines, a
		 * walkway beside both, nearest the platform's middle; -1 if the platform has no room.
		 */
		private int travelatorLane(TrackLayout.PlatformBand band) {
			if (band.width() < (band.island() ? StationLayout.MIN_ESCALATOR_ISLAND : StationLayout.MIN_ESCALATOR_SIDE)) {
				return -1;
			}
			final java.util.Set<Integer> columns = new java.util.HashSet<>(columnLines(band, roof(band)));
			final java.util.Set<Integer> lines = new java.util.HashSet<>(columns);
			lines.addAll(bridgePierLines(band));
			final double middle = (band.uMin() + band.uMax() - 1) / 2.0;
			int best = -1;
			for (int c = band.uMin() + 2; c + 1 <= band.uMax() - 2; c++) {
				// never on a line; and not squeezed between two column lines (a pier stands only at the footbridge)
				if (lines.contains(c) || lines.contains(c + 1) || columns.contains(c - 1) && columns.contains(c + 2)) {
					continue;
				}
				if (best < 0 || Math.abs(c - middle) < Math.abs(best - middle)) {
					best = c;
				}
			}
			return best;
		}

		/** One flat escalator step running {@code way}, with its handrail block above and headroom over that. */
		private void travelatorStep(int u, int y, int v, LocalDir way, String side) {
			put(Role.ESCALATOR, u, y, v, way, StylePalette.MTR_ESCALATOR_STEP, "side=" + side + ",direction=true,status=true");
			put(Role.ESCALATOR, u, y + 1, v, way, StylePalette.MTR_ESCALATOR_SIDE, "side=" + side);
			keepClear(u, y + 2, v);
			keepClear(u, y + 3, v);
		}

		/** The columns a flight opens out of the corridor: the main stair's include the escalator beside it. */
		private List<Integer> opening(TrackLayout.PlatformBand band, StationLayout.Flight flight) {
			return flight.side() == site.stairSide() ? site.flightColumns(band) : flight.columns();
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
						// a canopy column's cap carries the girder along its line, which carries the roof
						final int capY = roof(band) == RoofType.CANOPY ? top - 2 : top - 1;
						for (int y = StationGeometry.WALK_Y; y < capY; y++) {
							put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
						}
						put(Role.COLUMN_CAP, u, capY, v, LocalDir.PLUS_V);
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

		private List<Integer> canopyV(TrackLayout.PlatformBand band) {
			final List<Integer> positions = new ArrayList<>();
			for (final int v : canopyPositions(band.length())) {
				positions.add(band.vFrom() + v);
			}
			return positions;
		}

		/** Column v for one column line: the platform's spacing minus cells where stairs, railings or piers stand, with gaps of at most 9. */
		private List<Integer> columnVForLine(TrackLayout.PlatformBand band, int line) {
			if (!site.bridge()) {
				return canopyV(band);
			}
			// up to the roof cell over the cap: a sign or a stair there leaves the column carrying nothing
			final int top = StationGeometry.CANOPY_Y;
			final List<Integer> lines = List.of(line);
			final List<Integer> kept = new ArrayList<>();
			for (final int v : canopyV(band)) {
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
			// fill any remaining gap longer than the girder spans with a midpoint column that stands free
			final List<Integer> extra = new ArrayList<>();
			for (int i = 0; i + 1 < supports.size(); i++) {
				int a = supports.get(i);
				int b = supports.get(i + 1);
				while (b - a > StationGeometry.MAX_BEAM_SPAN) {
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
			// a column kept off a stair or an escalator's run-off can leave an end of the girders past their reach: stand one
			// nearer that end, as near the usual spot (two cells in) as is free
			final int reach = StationGeometry.MAX_CANTILEVER;
			supports.addAll(extra);
			supports.sort(Comparator.naturalOrder());
			if (supports.get(supports.size() - 1) < band.vTo() - reach) {
				endColumn(kept, band, line, top, new int[]{band.vTo() - 2, band.vTo() - 1, band.vTo() - 3, band.vTo() - 4, band.vTo()});
			}
			if (supports.get(0) > band.vFrom() + reach) {
				endColumn(kept, band, line, top, new int[]{band.vFrom() + 2, band.vFrom() + 1, band.vFrom() + 3, band.vFrom() + 4, band.vFrom()});
			}
			return kept;
		}

		/** Adds the first of {@code candidates} where the column line is free from the walking surface to the roof. */
		private void endColumn(List<Integer> kept, TrackLayout.PlatformBand band, int line, int top, int[] candidates) {
			for (final int v : candidates) {
				if (!band.runs(v) || kept.contains(v)) {
					continue;
				}
				boolean clash = false;
				for (int y = StationGeometry.WALK_Y; y <= top && !clash; y++) {
					clash = taken(line, y, v);
				}
				if (!clash) {
					kept.add(v);
					kept.sort(Comparator.naturalOrder());
					return;
				}
			}
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
					// past a shorter platform's end a column needs its footing cell (columns() skips one on a ramp)
					if (!band.runs(v) && taken(u, StationGeometry.PLATFORM_Y, v) && standsOn(u, v, top) < StationGeometry.WALK_Y) {
						return false;
					}
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
			// the street entrance (number 0) is open to the sky, under a train shed too: the shed starts at the first track
			return band.number() == 0 ? RoofType.NONE : StationGeometry.roof(spec, band.number());
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
				final int from = canopyFrom(band);
				final int to = canopyTo(band);
				final String girder = palette.blockId(Role.BEAM);
				final String kind = "kind=" + palette.girderKind();
				final CanopyShape shape = spec.canopy();
				final RoofPiece[] across = acrossProfile(shape, lines, from, to);
				final int[] lamp = lampsAlong(band.length(), shape);
				// a butterfly is a thin V on cantilevers: arms at the columns, the valley beam along the line and (wide) a girder
				// up under each side; braces under its raised edges at the columns alone, so nothing hangs under its eaves
				final boolean butterfly = shape == CanopyShape.BUTTERFLY;
				// each line's arms reach towards the roof edge (or halfway to the next line), stopping a cell short of it
				final int[] armLeft = new int[lines.size()];
				final int[] armRight = new int[lines.size()];
				// a butterfly's girder along the platform under each side, only where the roof runs on further than the valley
				// beam reaches: as far in as it can carry the edge from, so it sits up under the V, not under the eaves
				final int[] purlinLeft = new int[lines.size()];
				final int[] purlinRight = new int[lines.size()];
				for (int i = 0; i < lines.size(); i++) {
					final int u = lines.get(i);
					final int left = i == 0 ? u - from : (u - lines.get(i - 1)) / 2;
					final int right = i == lines.size() - 1 ? to - u : (lines.get(i + 1) - u) / 2;
					armLeft[i] = Math.max(0, Math.min(StationGeometry.MAX_CANTILEVER, left - 1));
					armRight[i] = Math.max(0, Math.min(StationGeometry.MAX_CANTILEVER, right - 1));
					purlinLeft[i] = left > StationGeometry.MAX_CANTILEVER ? Math.min(armLeft[i], left - StationGeometry.MAX_CANTILEVER) : 0;
					purlinRight[i] = right > StationGeometry.MAX_CANTILEVER ? Math.min(armRight[i], right - StationGeometry.MAX_CANTILEVER) : 0;
				}
				for (int v = band.vFrom(); v <= band.vTo(); v++) {
					// on every column: an arm across the platform on its cap; between columns a girder along the line and an edge
					// girder along each arm's end, resting on the arms
					for (int i = 0; i < lines.size(); i++) {
						final int u = lines.get(i);
						final PlannedBlock cap = cells.get(new Cell(u, y - 2, v));
						if (cap != null && cap.role() == Role.COLUMN_CAP) {
							// the arm runs out from the cap until something stands in its way (a stairwell, an escalator); past
							// that, what a wall or pier can carry is framed into it and the edge girder runs on along the line
							put(Role.BEAM, u, y - 1, v, LocalDir.PLUS_U, girder, kind);
							for (final int step : new int[]{-1, 1}) {
								final int reach = step < 0 ? armLeft[i] : armRight[i];
								int a = u + step;
								while (Math.abs(a - u) <= reach && !taken(a, y - 1, v)) {
									put(Role.BEAM, a, y - 1, v, LocalDir.PLUS_U, girder, kind);
									a += step;
								}
								if (Math.abs(a - u) <= reach) {
									final int lo = Math.min(a, u + step * reach);
									final int hi = Math.max(a, u + step * reach);
									final java.util.Set<Integer> framed = framedIntoPiers(lo, hi, y - 1, v, true);
									for (final int f : framed) {
										put(Role.BEAM, f, y - 1, v, LocalDir.PLUS_U, girder, kind);
									}
									// the edge girder rests on the arm's end: past an arm cut short it would hang over the stairwell on
									// nothing, unless it is framed into the far wall
									if (reach > 0 && framed.contains(u + step * (reach - 1))) {
										put(Role.BEAM, u + step * reach, y - 1, v, LocalDir.PLUS_V, girder, kind);
									}
								}
							}
						} else {
							// a footbridge or stair pier rising through the girders carries a cross beam framed into its sides,
							// as far as a cantilever reaches (on the footbridge line, right across the canopy); the girders run on
							// past the rest of the row
							final boolean bridgeRow = site.bridge() && v == site.bridgeV();
							final int start = bridgeRow && i > 0 ? lines.get(i - 1) + armRight[i - 1] + 1 : u - armLeft[i];
							final int end = u + armRight[i];
							// where a stairwell or lift shaft wall stops the girder along the line, the cross beam frames into it
							final java.util.Set<Integer> framed = framedIntoPiers(start, end, y - 1, v, taken(u, y - 1, v));
							for (final int a : framed) {
								put(Role.BEAM, a, y - 1, v, LocalDir.PLUS_U, girder, kind);
							}
							for (final int a : butterfly ? new int[]{u, u - purlinLeft[i], u + purlinRight[i]} : new int[]{u, u - armLeft[i], u + armRight[i]}) {
								if (!framed.contains(a)) {
									put(Role.BEAM, a, y - 1, v, LocalDir.PLUS_V, girder, kind);
								}
							}
						}
					}
					// the roof row: its profile across the platform (butterfly, gable) lifts some cells above y
					final int along = v - band.vFrom();
					boolean armRow = false;
					for (final int line : lines) {
						final PlannedBlock cap = cells.get(new Cell(line, y - 2, v));
						armRow |= cap != null && cap.role() == Role.COLUMN_CAP;
					}
					for (int u = from; u <= to; u++) {
						final RoofPiece piece = across[u - from];
						final boolean edge = u == from || u == to;
						final int rise = edge ? piece.edgeRise() : piece.rise();
						// a roof lifted above the platform's level leaves the cells below it alone: a footbridge or stairwell there
						// has its own cover
						boolean clear = true;
						for (int level = y; level < y + rise && clear; level++) {
							clear = !taken(u, level, v);
						}
						if (!clear) {
							continue;
						}
						// a girder under the raised roof, where it meets the sloping blocks
						if (rise > 0 && piece.braced() && (armRow || !butterfly)) {
							for (int level = y; level < y + rise; level++) {
								put(Role.RIB, u, level, v, LocalDir.PLUS_V, palette.blockId(Role.RIB), kind);
							}
						}
						if (edge) {
							put(Role.ROOF_EDGE, u, y + rise, v, u == from ? LocalDir.MINUS_U : LocalDir.PLUS_U);
						} else if (lamp[along] != NO_LAMP && piece.block() == null && onLamp(lines, u, lamp[along])) {
							put(Role.ROOF_LIGHT, u, y + rise, v, LocalDir.PLUS_V);
						} else if (piece.block() == null) {
							put(Role.ROOF, u, y + rise, v, LocalDir.PLUS_V);
						} else {
							put(Role.ROOF, u, y + rise, v, piece.low(), piece.block(), "");
						}
					}
				}
			}
			pruneLooseGirders(y - 1);
		}

		/** The lamp offset of a row with no lamp. */
		private static final int NO_LAMP = 2;
		/** Cells from a column line to the roof edge (or the next line's half) below which a canopy stays level. */
		private static final int MIN_SLOPE_REACH = 3;

		/**
		 * One cell of a canopy's roof: how many blocks it stands above the canopy level, the sloping block it is made of (null
		 * for the style's flat roof block), the way that block's low end points, how high the edge cell of its row stands, and
		 * whether a girder block is set under it. A raised roof touches the sloping block beside it only at an edge, so the
		 * girder under a ridge, a fold or a crest is what joins it to the rest (and carries it, as a post).
		 */
		/**
		 * Girders along a canopy's lines and edges that a stairwell, lift or escalator cut off from their columns: a piece resting
		 * on nothing is left out, one carried at a single end is cut back to what a cantilever reaches, and one longer than a
		 * girder spans between two supports becomes a cantilever off each. The rules match
		 * {@link LayoutValidator}'s, so what stands is what the checks accept.
		 */
		private void pruneLooseGirders(int y) {
			final Map<Integer, java.util.TreeSet<Integer>> lines = new TreeMap<>();
			for (final PlannedBlock block : cells.values()) {
				if (block.role() == Role.BEAM && block.y() == y && (block.front() == LocalDir.PLUS_V || block.front() == LocalDir.MINUS_V)) {
					lines.computeIfAbsent(block.u(), k -> new java.util.TreeSet<>()).add(block.v());
				}
			}
			for (final Map.Entry<Integer, java.util.TreeSet<Integer>> line : lines.entrySet()) {
				final int u = line.getKey();
				final List<int[]> runs = new ArrayList<>();
				for (final int v : line.getValue()) {
					if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == v - 1) {
						runs.get(runs.size() - 1)[1] = v;
					} else {
						runs.add(new int[]{v, v});
					}
				}
				for (final int[] run : runs) {
					final boolean before = carriesGirder(u, y, run[0] - 1);
					final boolean after = carriesGirder(u, y, run[1] + 1);
					boolean capped = false;
					for (int v = run[0]; v <= run[1] && !capped; v++) {
						final PlannedBlock cap = cells.get(new Cell(u, y - 1, v));
						capped = cap != null && cap.role() == Role.COLUMN_CAP;
					}
					if (capped || before && after && run[1] - run[0] + 1 <= StationGeometry.MAX_BEAM_SPAN) {
						continue;
					}
					if (before && after) {
						// longer than a girder spans between its two supports: two cantilevers, one off each
						for (int v = run[0] + StationGeometry.MAX_CANTILEVER; v <= run[1] - StationGeometry.MAX_CANTILEVER; v++) {
							cells.remove(new Cell(u, y, v));
						}
						continue;
					}
					final int keepFrom = before ? run[0] : after ? run[1] - StationGeometry.MAX_CANTILEVER + 1 : Integer.MAX_VALUE;
					final int keepTo = before ? run[0] + StationGeometry.MAX_CANTILEVER - 1 : after ? run[1] : Integer.MIN_VALUE;
					for (int v = run[0]; v <= run[1]; v++) {
						if (v < keepFrom || v > keepTo) {
							cells.remove(new Cell(u, y, v));
						}
					}
				}
			}
		}

		/** A cross beam, a column cap, a wall or a pier at (u, y, v) that a girder along v can end on. */
		private boolean carriesGirder(int u, int y, int v) {
			final PlannedBlock block = cells.get(new Cell(u, y, v));
			if (block == null) {
				return false;
			}
			final Role role = block.role();
			return role == Role.BEAM && block.front() != LocalDir.PLUS_V && block.front() != LocalDir.MINUS_V || role == Role.COLUMN_CAP
					|| role == Role.WALL || role == Role.BRIDGE_PIER;
		}

		private record RoofPiece(int rise, String block, LocalDir low, int edgeRise, boolean braced) {

			static RoofPiece level(int rise) {
				return new RoofPiece(rise, null, null, rise, false);
			}

			static RoofPiece slope(int rise, String block, LocalDir low) {
				return new RoofPiece(rise, block, low, rise, false);
			}

			RoofPiece withBrace() {
				return new RoofPiece(rise, block, low, edgeRise, true);
			}
		}

		/**
		 * The roof's profile across a platform, one piece per u from {@code from} to {@code to}, for the shapes that have one
		 * (butterfly, gable; the others are level). Each column line owns the cells up to halfway to the next line, or to the
		 * roof edge. A butterfly is level over its line and rises one block over the next two cells, outwards; a gable stands
		 * its ridge over the line, one block high (two on a wide roof), and falls the same way to the edge. A stretch of
		 * fewer than three cells to either side is left level: there is no room for the slope.
		 */
		private RoofPiece[] acrossProfile(CanopyShape shape, List<Integer> lines, int from, int to) {
			final RoofPiece[] pieces = new RoofPiece[to - from + 1];
			java.util.Arrays.fill(pieces, RoofPiece.level(0));
			if (shape != CanopyShape.BUTTERFLY && shape != CanopyShape.GABLE) {
				return pieces;
			}
			for (int i = 0; i < lines.size(); i++) {
				final int line = lines.get(i);
				final int low = i == 0 ? from : lines.get(i - 1) + (line - lines.get(i - 1)) / 2 + 1;
				final int high = i == lines.size() - 1 ? to : line + (lines.get(i + 1) - line) / 2;
				final int reach = Math.min(line - low, high - line);
				if (reach < MIN_SLOPE_REACH) {
					continue;
				}
				// the gable's ridge height; the butterfly's rise is always one block
				final int ridge = shape == CanopyShape.GABLE ? Math.min(2, (reach - 1) / 2) : 1;
				for (int u = low; u <= high; u++) {
					final int d = Math.abs(u - line);
					final LocalDir outward = u < line ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					if (shape == CanopyShape.GABLE) {
						if (d == 0) {
							pieces[u - from] = RoofPiece.level(ridge).withBrace();
						} else if (d <= 2 * ridge) {
							// counting from the eaves: the lowest half-block step first
							final int step = 2 * ridge - d + 1;
							pieces[u - from] = RoofPiece.slope((step - 1) / 2, step % 2 == 1 ? CANOPY_SLOPE_LOWER : CANOPY_SLOPE_UPPER, outward);
						}
					} else if (d >= 1 && d <= 2) {
						pieces[u - from] = RoofPiece.slope(0, d == 1 ? CANOPY_SLOPE_LOWER : CANOPY_SLOPE_UPPER, outward.opposite());
					} else if (d > 2) {
						pieces[u - from] = d == 3 ? RoofPiece.level(1).withBrace() : RoofPiece.level(1);
					}
				}
			}
			return pieces;
		}

		/**
		 * Where the lamps of a canopy go along a platform, per cell along it: every 4th cell, beside each girder on alternating
		 * sides (a butterfly or gable lights its line itself: the valley or the ridge). Each entry is -1 or 1 for the side of the line, 0 for on it, or {@link #NO_LAMP}.
		 */
		private static int[] lampsAlong(int length, CanopyShape shape) {
			final int[] lamps = new int[length];
			java.util.Arrays.fill(lamps, NO_LAMP);
			for (int along = 2; along < length; along += 4) {
				lamps[along] = shape == CanopyShape.BUTTERFLY || shape == CanopyShape.GABLE ? 0 : along % 8 == 2 ? 1 : -1;
			}
			return lamps;
		}

		/** True when the roof cell at {@code u} is lit by this row's lamp: beside a line on the lamp's side, or on the line. */
		private static boolean onLamp(List<Integer> lines, int u, int lamp) {
			return lines.contains(u - lamp);
		}

		/**
		 * The free cells from u0 to u1 at height y in row v that a cross beam framed into a footbridge or stair pier can reach:
		 * out from each pier (one just outside the range counts) through free cells, at most a cantilever's reach; with
		 * {@code walls}, out from walls too.
		 */
		private java.util.Set<Integer> framedIntoPiers(int u0, int u1, int y, int v, boolean walls) {
			final java.util.Set<Integer> framed = new java.util.TreeSet<>();
			for (int p = u0 - 1; p <= u1 + 1; p++) {
				final PlannedBlock block = cells.get(new Cell(p, y, v));
				if (block == null || block.role() != Role.BRIDGE_PIER && !(walls && block.role() == Role.WALL)) {
					continue;
				}
				for (final int step : new int[]{-1, 1}) {
					for (int a = p + step; Math.abs(a - p) <= StationGeometry.MAX_CANTILEVER && a >= u0 && a <= u1 && !taken(a, y, v); a += step) {
						framed.add(a);
					}
				}
			}
			return framed;
		}

		/**
		 * y of the hall's girder frame: just above the track envelopes; higher under a wide vault; above the footbridge
		 * and a building beside the tracks when the station has them; and higher again for a city or grand station, whose
		 * shed is a real train-shed height rather than the compact frame (see {@link StationGeometry#hallBeamY}).
		 */
		private int hallBeamY() {
			if (scale == StationScale.GRAND) {
				return StationGeometry.tallHallBeamY(scale);
			}
			if (site.bridge() || spec.sideBuilding()) {
				return StationGeometry.tallHallBeamY(scale);
			}
			if (spec.shed().arched() && layout.width() - layout.buildingWidth() >= 16) {
				return StationGeometry.highHallBeamY(scale);
			}
			return StationGeometry.hallBeamY(scale);
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
			final int u0 = site.street() != null ? StationGeometry.STREET_WIDTH : layout.buildingWidth();
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
			// a vault is glazed above a solid lower band; pitched roofs are solid with a glass lantern along each ridge
			final boolean ridgeLights = spec.shed() == ShedShape.GABLED;
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
							final boolean glazed = palette.roofMaterialGlass() || (ridgeLights ? rise[i] >= peak - 1 : y - roofY >= Math.max(1, peak / 3));
							final boolean eaves = edge && y == top[i];
							put(eaves ? Role.ROOF_EDGE : Role.ROOF, u, y, v, i == 0 ? LocalDir.MINUS_U : i == n - 1 ? LocalDir.PLUS_U : LocalDir.PLUS_V,
									glazed && !eaves ? glass : ata("metro_steel_cladding"));
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

		/** Height of a round vault over its span. */
		private static final double ARCH_RISE = 0.4;
		/** Height of a pitched roof's ridge over its span. */
		private static final double GABLE_RISE = 0.25;

		/** A round vault's rise over its span for the look: Berlin's glass tube is flat, Amsterdam's and Hamburg's are low. */
		private double vaultRise() {
			return switch (spec.building()) {
				case BERLIN -> 0.2;
				// Bangkok's hall vault stays under its great window
				case AMSTERDAM, BANGKOK -> 0.3;
				case CLINKER -> 0.35;
				default -> ARCH_RISE;
			};
		}
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
				case ARCH -> vault(rise, 0, n - 1, Profile.ROUND, vaultRise());
				case POINTED -> vault(rise, 0, n - 1, Profile.POINTED, ARCH_RISE);
				case MULTI_ARCH, GABLED -> {
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
					final boolean gabled = spec.shed() == ShedShape.GABLED;
					for (int k = 0; k + 1 < cuts.size(); k++) {
						vault(rise, cuts.get(k), cuts.get(k + 1), gabled ? Profile.GABLE : Profile.ROUND, gabled ? GABLE_RISE : ARCH_RISE);
					}
				}
			}
			return rise;
		}

		/** The cross-section of one span of a shed roof. */
		private enum Profile {
			ROUND, POINTED, GABLE
		}

		/** The middle of a vault's flat crown: as high as both neighbours and not left of an equal one. */
		private static boolean isCrown(int[] rise, int i) {
			return rise[i] >= rise[i + 1] && rise[i] > rise[i - 1];
		}

		private static void vault(int[] rise, int a, int b, Profile profile, double ratio) {
			final int span = b - a + 1;
			final double half = (b - a) / 2.0;
			final double centre = (a + b) / 2.0;
			final int height = Math.min(MAX_VAULT_RISE, profile == Profile.POINTED ? Math.max(3, (int) Math.round(span * 0.42))
					: Math.max(2, (int) Math.round(span * ratio)));
			for (int u = a; u <= b; u++) {
				final double x = Math.min(1, Math.abs(u - centre) / half);
				// pointed: two arcs centred half a span outside and below the crown's opposite side, meeting at the crown at an
				// angle; round: half an ellipse, which springs steeply from the eaves like a real barrel vault; gable: straight
				// slopes up to a ridge
				final double f = switch (profile) {
					case POINTED -> Math.sqrt(Math.max(0, 2.5 - (x + 0.5) * (x + 0.5))) - 0.5;
					case ROUND -> Math.sqrt(1 - x * x);
					case GABLE -> 1 - x;
				};
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

		/** The height of an open platform's lamp head, on its pole. */
		private static final int LAMP_HEAD_Y = StationGeometry.WALK_Y + 3;

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
		private boolean shelterAt(TrackLayout.PlatformBand band, int v) {
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

		private boolean nearTravelatorRow(TrackLayout.PlatformBand band, int v, int u1, int u2) {
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
		private boolean shelter(int baseY, int wallU, int seatU, LocalDir back, int v0, LocalDir back2, int seat2U) {
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

		/** True within two cells (each way) of a travelator step on this platform, so furniture never crowds the walkway. */
		private boolean nearTravelator(TrackLayout.PlatformBand band, int u, int v) {
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
		 * quarter and three quarters of the length) on longer ones. Under a roof the screen hangs from the ceiling, double-sided
		 * and facing along the platform, four blocks wide where the platform and the ceiling have room, else two (never over an
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
					// a screen is always a proper board: hung, across a column pair, fixed to a column, or two blocks on a post.
					// Never a lone single block on the floor.
					if (!hangScreen(band, target) && !screenBetweenColumns(band, target) && !screenOnColumn(band, target)) {
						screenOnPost(band, target);
					}
				}
				onPost(band, targets.get(0), StationGeometry.WALK_Y, HELP_POINT);
			}
		}

		/** Where a platform's screens go: one per {@link #SCREEN_SPACING} blocks of platform, evenly spread, at least one. */
		private static List<Integer> screenTargets(TrackLayout.PlatformBand band) {
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
		private boolean hangSpeaker(TrackLayout.PlatformBand band, int target) {
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
		private static List<List<Integer>> screenSpans(TrackLayout.PlatformBand band) {
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
		private boolean hangScreen(TrackLayout.PlatformBand band, int target) {
			for (final List<Integer> columns : screenSpans(band)) {
				if (hangScreen(band, target, columns)) {
					return true;
				}
			}
			return false;
		}

		private boolean hangScreen(TrackLayout.PlatformBand band, int target, List<Integer> columns) {
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
		 * Where the roof is too high to hang a screen from (a train shed) and the platform has two column lines, one
		 * double-sided screen across the whole gap between a pair of columns, in their own row, under a girder from column to
		 * column it hangs from: a single display read from both ways, like a screen on a gantry. The pair nearest {@code target}
		 * with the gap clear is taken. True if hung.
		 */
		private boolean screenBetweenColumns(TrackLayout.PlatformBand band, int target) {
			final int y = StationGeometry.WALK_Y + 2;
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

		private boolean column(int u, int y, int v) {
			final PlannedBlock block = cells.get(new Cell(u, y, v));
			return block != null && block.role() == Role.COLUMN;
		}

		/**
		 * Where the roof is too high to hang a screen from (a train shed), a screen two blocks wide fixed to the column nearest
		 * {@code target} above head height, its middle against the column so the two blocks join into one display; on both
		 * faces of the column along the platform where there is room, so it reads from either way. True if one was fixed.
		 */
		private boolean screenOnColumn(TrackLayout.PlatformBand band, int target) {
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
		private boolean screenOnPost(TrackLayout.PlatformBand band, int target) {
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
		 * Fixes a wall unit to the back of a column or lamp post on this platform, at height {@code y}, in the free cell along
		 * the platform next to the post nearest {@code target}, facing away from it. True if placed.
		 */
		private boolean onPost(TrackLayout.PlatformBand band, int target, int y, String blockId) {
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
		private void flank(int u, int y, int v, LocalDir out, StylePalette.Building b) {
			if (spec.enclosedStairs()) {
				put(Role.WALL, u, y, v, out, b.wall());
			} else {
				put(Role.WINDOW, u, y, v, out, b.bridgeWall());
			}
		}

		/**
		 * Under a canopy, a platform stair is enclosed in the footbridge's glazing (or walls): both flanks of the whole flight
		 * from the platform up to the bridge roof (no metal rails; never on a boarding edge), across its far end above the
		 * canopy, and a roof over just that shaft. So the steps are not left standing in the open above the canopy.
		 */
		private void stairShaft(TrackLayout.PlatformBand band, StationLayout.Flight flight, StylePalette.Building b) {
			final List<Integer> open = opening(band, flight);
			final int left = open.get(0) - 1;
			final int right = open.get(open.size() - 1) + 1;
			// steps whose headroom reaches the canopy level pass through it; the step below them ends the shaft
			final int lowest = Math.max(1, StationGeometry.CANOPY_Y - 2);
			final int top = flight.stepV(StationGeometry.STAIR_STEPS);
			final int end = lowest > 1 ? flight.stepV(lowest - 1) : flight.landingV();
			final int side = flight.side();
			final LocalDir out = side > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
			final int foot = flight.stepV(1);
			final java.util.Set<Integer> shaftRows = new java.util.HashSet<>();
			for (int v = top; v != end + side; v += side) {
				shaftRows.add(v);
			}
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.BUILDING_ROOF_Y; y++) {
				for (int v = top; v != foot + side; v += side) {
					// past the shaft the flight is under the canopy: its flanks stop below the roof, which covers them
					if (!shaftRows.contains(v) && y >= StationGeometry.CANOPY_Y) {
						continue;
					}
					// below the canopy a flank on a boarding edge stays open
					final boolean high = y > StationGeometry.CANOPY_Y;
					if (high || !isEdge(band, left) && left >= band.uMin()) {
						flank(left, y, v, LocalDir.MINUS_U, b);
					}
					if (high || !isEdge(band, right) && right <= band.uMax()) {
						flank(right, y, v, LocalDir.PLUS_U, b);
					}
				}
				if (y > StationGeometry.CANOPY_Y) {
					for (int u = left + 1; u < right; u++) {
						flank(u, y, end, out, b);
					}
				}
			}
			for (int u = left; u <= right; u++) {
				for (int v = top; v != end + side; v += side) {
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, (u + v) % 4 == 1 ? b.bridgeLight() : b.bridgeRoof());
				}
			}
			flankPosts(band, flight, left, right, b);
		}

		/**
		 * Posts under the deck at the top of a flight's glazed flanks, in the corridor's wall row: the panes would otherwise end
		 * in the air there, below the deck edge.
		 */
		private void flankPosts(TrackLayout.PlatformBand band, StationLayout.Flight flight, int left, int right, StylePalette.Building b) {
			if (spec.enclosedStairs()) {
				return;
			}
			for (final int u : new int[]{left, right}) {
				if (u < band.uMin() || u > band.uMax() || isEdge(band, u) || flight.columns().contains(u)) {
					continue;
				}
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
					if (!taken(u, y, flight.wallV())) {
						put(Role.BRIDGE_PIER, u, y, flight.wallV(), LocalDir.PLUS_V, b.pier());
					}
				}
			}
		}

		/**
		 * Without a canopy, a stair flight is glazed (or walled) like the corridor it leaves: walls on both flanks from the
		 * platform up to a roof over the flight alone, open at the bottom step. A flank on a boarding edge stays open.
		 */
		private void stairHood(TrackLayout.PlatformBand band, StationLayout.Flight flight, StylePalette.Building b) {
			final List<Integer> open = opening(band, flight);
			final int left = Math.max(band.uMin(), open.get(0) - 1);
			final int right = Math.min(band.uMax(), open.get(open.size() - 1) + 1);
			final boolean wallLeft = left < open.get(0) && !isEdge(band, left);
			final boolean wallRight = right > open.get(open.size() - 1) && !isEdge(band, right);
			final int top = flight.stepV(StationGeometry.STAIR_STEPS);
			final int bottom = flight.stepV(1);
			final int side = flight.side();
			for (int v = top; v != bottom + side; v += side) {
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.BUILDING_ROOF_Y; y++) {
					if (wallLeft) {
						flank(left, y, v, LocalDir.MINUS_U, b);
					}
					if (wallRight) {
						flank(right, y, v, LocalDir.PLUS_U, b);
					}
				}
				for (int u = left; u <= right; u++) {
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, (u + v) % 4 == 1 ? b.bridgeLight() : b.bridgeRoof());
				}
			}
			flankPosts(band, flight, wallLeft ? left : -1, wallRight ? right : -1, b);
		}

		/** True within 2 of a footbridge stair flight or its landing on this platform, or within 3 of its lift shaft. */
		private boolean nearStairs(TrackLayout.PlatformBand band, int v) {
			final StationLayout.Lift lift = site.platformLift(band, layout);
			// the shaft and the landings at both its doors stay clear: nobody wants a bench or a screen against the lift
			if (lift != null && v >= lift.vMin() - 3 && v <= lift.vMax() + 3) {
				return true;
			}
			for (final StationLayout.Flight flight : site.flights(band)) {
				final int a = Math.min(flight.wallV(), flight.landingV());
				final int z = Math.max(flight.wallV(), flight.landingV());
				if (v >= a - 2 && v <= z + 2) {
					return true;
				}
			}
			return false;
		}

		/** True in a flight's columns or the one beside them, from its top step to two rows past its landing. */
		private boolean atStairFoot(TrackLayout.PlatformBand band, int u, int v) {
			for (final StationLayout.Flight flight : site.flights(band)) {
				final int a = Math.min(flight.wallV(), flight.landingV() + 2 * flight.side());
				final int z = Math.max(flight.wallV(), flight.landingV() + 2 * flight.side());
				if (v >= a && v <= z && u >= flight.columns().get(0) - 1 && u <= flight.columns().get(flight.columns().size() - 1) + 1) {
					return true;
				}
			}
			return false;
		}

		void nameSigns() {
			if (spec.name().isEmpty()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				// a row of three where one fits in any column; on a short platform full of stairs and a lift, two, else one
				rows:
				for (int n = 3; n >= 1; n--) {
					if (band.island()) {
						// one row down the middle: the signs are double-sided, so it reads from both tracks; where stairs, a lift
						// or a walkway fill the middle column, the one beside it
						final int middle = (band.uMin() + band.uMax()) / 2;
						for (final int u : new int[]{middle, middle + 1, middle - 1}) {
							if (!isEdge(band, u) && u >= band.uMin() && u <= band.uMax() && signRow(band, u, LocalDir.MINUS_U, n)) {
								break rows;
							}
						}
					} else if (!signBeside(band)) {
						// against the back, else a column further in where the back is walled (an underpass stair, a building)
						final int step = towardTrack(band) == LocalDir.PLUS_U ? 1 : -1;
						for (int u = backColumn(band), k = 0; k < 3 && !isEdge(band, u) && u >= band.uMin() && u <= band.uMax(); u += step, k++) {
							if (signRow(band, u, towardTrack(band), n)) {
								break rows;
							}
						}
					} else {
						break;
					}
				}
			}
		}

		/**
		 * True when a side platform backs onto one that already has its name signs: the two backs touch with nothing between,
		 * and the signs are double-sided, so the row already there reads from this track too. A second row beside it read as
		 * two signs stacked.
		 */
		private boolean signBeside(TrackLayout.PlatformBand band) {
			final int u = backColumn(band);
			for (final Map.Entry<Cell, PlannedBlock> entry : cells.entrySet()) {
				final Cell cell = entry.getKey();
				if (entry.getValue().role() == Role.NAME_SIGN && cell.u() != u && Math.abs(cell.u() - u) <= 2
						&& cell.v() >= band.vFrom() && cell.v() <= band.vTo()) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Three signs side by side along v, centred on the platform's middle; shifted along v until the cells are free. Shifts
		 * run towards the platform end first and, if the end is reached, back towards its start. False if there is no room.
		 */
		private boolean signRow(TrackLayout.PlatformBand band, int u, LocalDir front, int n) {
			final int start = band.vFrom() + band.length() / 2 - n / 2;
			for (int v = start; v + n - 1 <= band.vTo(); v++) {
				if (placeRow(band, u, v, front, n)) {
					return true;
				}
			}
			for (int v = start - 1; v >= band.vFrom(); v--) {
				if (placeRow(band, u, v, front, n)) {
					return true;
				}
			}
			return false;
		}

		private boolean placeRow(TrackLayout.PlatformBand band, int u, int v, LocalDir front, int n) {
			for (int i = 0; i < n; i++) {
				// never across the foot of a stair: a sign there walled in whoever came down it
				if (taken(u, StationGeometry.WALK_Y, v + i) || atStairFoot(band, u, v + i)) {
					return false;
				}
			}
			for (int i = 0; i < n; i++) {
				put(Role.NAME_SIGN, u, StationGeometry.WALK_Y, v + i, front);
			}
			return true;
		}

		/** A car park: a kerb, a run of bays, a lane, a central planter, a lane and a second run of bays, across PARKING_DEPTH cells. */
		private static final int LOT_HALF = 14;
		private static final int LOT_BAY = 4;
		private static final int LOT_PITCH = 3;
		private static final String LOT_PAVING = "aurelia_transit_architecture:platform_paving_light";
		private static final String LOT_MARK = "aurelia_transit_architecture:platform_paving_dark";
		private static final String LOT_KERB = "aurelia_transit_architecture:bus_curb";
		private static final String LOT_HEDGE = "minecraft:oak_leaves";
		/** The way from a hub's door across its forecourt is this far either side of the door, in cells. */
		private static final int FORECOURT_WAY = 2;
		/** The width of a hub's bus island. */
		private static final int FORECOURT_ISLAND = 3;
		/** Taxi bays: how many, how wide and how long. */
		private static final int FORECOURT_BAYS = 3;
		private static final int FORECOURT_BAY = 3;
		private static final int FORECOURT_BAY_LENGTH = 5;
		private static final String FORECOURT_STOP_SIGN = "aurelia_transit_architecture:bus_stop_sign";
		private static final String FORECOURT_TIMETABLE = "aurelia_transit_architecture:bus_timetable_case";
		private static final String LOT_TRUNK = "minecraft:oak_log";
		private static final String LOT_BOLLARD = "aurelia_transit_architecture:bollard";
		/** A cycle stand: a post and a rail beside it, from the station fence and bollard blocks. */
		private static final String LOT_STAND_POST = "aurelia_transit_architecture:bollard";
		private static final String LOT_STAND_RAIL = "aurelia_transit_architecture:station_fence";
		/** The drop-off reaches this far either side of the door, in cells. */
		private static final int LOT_DROP_OFF = 4;
		/** Pairs of cycle stands in the row by the kerb. */
		private static final int LOT_CYCLE_ROWS = 5;
		/** A tree every this many cells along the planter. */
		private static final int LOT_TREE_PITCH = 8;

		/** u of the building's entrance apron (its two ramp cells on the street side): past a car park band when there is one. */
		private int apronU() {
			return spec.parking() && spec.sideBuilding() ? StationGeometry.PARKING_DEPTH : 0;
		}

		/** u of the building's street wall: a car park stands in front of it (see {@link StationGeometry#PARKING_DEPTH}). */
		private int streetWall() {
			return apronU() + StationGeometry.APRON;
		}

		/**
		 * A surface car park in front of the street wall (the "parking" / "car park" word): a kerb along the street edge, two runs
		 * of bays with their heads and separators marked out, a lane either side of a central planter, and a driveway onto the
		 * street at the entrance. Built from the platform paving and bus kerb blocks already in the mod (and vanilla leaves for
		 * the planter). It fills the band the layout leaves in front of the building ({@link StationGeometry#PARKING_DEPTH}
		 * cells, u = 0..depth-1), so nothing stands outside the footprint.
		 */
		/**
		 * A hub's forecourt, past the head building's entrance ramps: paving to the street kerb, a pedestrian way from the door, a taxi
		 * rank on one side (three marked bays with a bollard at the head of each) and a bus interchange on the other (a kerbed island
		 * with a walk-in waiting shelter, stop signs and a timetable case, and a marked carriageway beside it). Built from the paving,
		 * bus kerb, bus kit and bollard blocks already in the mod; there is no tram stop (no tram blocks) and no taxi sign block.
		 */
		void forecourt() {
			if (!layout.terminus() || layout.forecourtDepth() <= 0) {
				return;
			}
			final int u0 = layout.headFrom();
			final int u1 = layout.headTo();
			final int door = headDoor();
			final int f0 = layout.forecourtFrom();
			final int f1 = layout.vMax();
			final int busKerb = door + FORECOURT_WAY + FORECOURT_ISLAND + 1;
			final int taxiTo = door - FORECOURT_WAY - 1;
			for (int u = u0; u <= u1; u++) {
				for (int v = f0; v <= f1; v++) {
					final int c = u - door;
					final int d = v - f0;
					final boolean way = Math.abs(c) <= FORECOURT_WAY || d < 3;
					final boolean street = v == f1;
					String block = LOT_PAVING;
					LocalDir front = LocalDir.PLUS_V;
					if (street && !way) {
						// the kerb along the street, open where the pedestrian way meets it
						block = LOT_KERB;
					} else if (!way && c > 0 && u == busKerb && d >= 3 && d < f1 - f0) {
						block = LOT_KERB;
						front = LocalDir.PLUS_U;
					} else if (!way && c > 0 && u > busKerb) {
						// the bus carriageway
						block = LOT_MARK;
					} else if (!way && c < 0 && u <= taxiTo) {
						// the taxi rank: bays of paving three wide, five long, between dark lines
						final int bay = (taxiTo - u) / (FORECOURT_BAY + 1);
						final boolean line = (taxiTo - u) % (FORECOURT_BAY + 1) == FORECOURT_BAY || d < 3 || d >= 3 + FORECOURT_BAY_LENGTH;
						block = bay < FORECOURT_BAYS && !line ? LOT_PAVING : LOT_MARK;
					}
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y - 1, v, front, block);
				}
			}
			// taxi rank: a bollard at the head of each bay
			for (int bay = 0; bay < FORECOURT_BAYS; bay++) {
				final int u = taxiTo - bay * (FORECOURT_BAY + 1) - FORECOURT_BAY / 2;
				if (u > u0) {
					put(Role.FIXTURE, u, StationGeometry.PLATFORM_Y, f0 + 2, LocalDir.PLUS_V, LOT_BOLLARD);
				}
			}
			// bus interchange: the island between the way and the kerb, a shelter on its way side facing the buses, signs at both ends
			if (busKerb < u1) {
				final int wall = door + FORECOURT_WAY + 1;
				shelter(StationGeometry.PLATFORM_Y, wall, wall + 1, LocalDir.MINUS_U, f0 + 5, null, 0);
				put(Role.FIXTURE, wall + 2, StationGeometry.PLATFORM_Y, f0 + 3, LocalDir.MINUS_V, FORECOURT_STOP_SIGN);
				put(Role.FIXTURE, wall + 2, StationGeometry.PLATFORM_Y, f1 - 3, LocalDir.MINUS_V, FORECOURT_STOP_SIGN);
				put(Role.FIXTURE, wall, StationGeometry.PLATFORM_Y, f0 + 9, LocalDir.PLUS_U, FORECOURT_TIMETABLE);
			}
		}

		void parking() {
			if (!spec.parking() || !spec.sideBuilding()) {
				return;
			}
			final int front = site.entranceV();
			final int vFrom = Math.max(layout.vMin(), front - LOT_HALF);
			final int vTo = Math.min(layout.vMax(), front + LOT_HALF);
			final int first = vFrom + 2;
			for (int a = 0; a < StationGeometry.PARKING_DEPTH; a++) {
				final boolean kerb = a == 0;
				final boolean bays = a >= 1 && a <= LOT_BAY || a >= LOT_BAY + 6;
				final boolean head = a == LOT_BAY + 1 || a == LOT_BAY + 5;
				final boolean lane = a == LOT_BAY + 2 || a == LOT_BAY + 4;
				final boolean planter = a == LOT_BAY + 3;
				for (int v = vFrom; v <= vTo; v++) {
					final boolean entrance = Math.abs(v - front) <= 1;
					final boolean end = v == vFrom || v == vTo;
					// the drop-off: the bays by the building's door are one plain forecourt, with no bay markings to park in
					final boolean dropOff = a >= LOT_BAY + 6 && Math.abs(v - front) <= LOT_DROP_OFF;
					// cycle parking: the bays at the street end of the lot, by the kerb
					final boolean cycles = a >= 1 && a <= 3 && v >= first && v < first + LOT_CYCLE_ROWS * 2;
					final boolean separator = bays && !dropOff && !cycles && Math.floorMod(v - vFrom, LOT_PITCH) == 0;
					final String block;
					String state = "";
					boolean tree = false;
					if (kerb && !entrance) {
						block = LOT_KERB;
					} else if (planter && !end && !entrance) {
						tree = Math.abs(v - front) > LOT_DROP_OFF && Math.floorMod(v - vFrom, LOT_TREE_PITCH) == LOT_TREE_PITCH / 2 && v > vFrom + 1 && v < vTo - 1;
						// a tree stands in its own cell of the planter; the hedge round it is leaves that stay (persistent, so they never decay)
						block = tree ? LOT_TRUNK : LOT_HEDGE;
						state = tree ? "axis=y" : "persistent=true";
					} else if (dropOff && !lane && !head) {
						block = LOT_PAVING;
					} else if (cycles) {
						block = LOT_PAVING;
					} else if (lane || head || end || separator) {
						block = LOT_MARK;
					} else {
						block = LOT_PAVING;
					}
					put(Role.FLOOR, a, StationGeometry.PLATFORM_Y - 1, v, LocalDir.MINUS_U, block, state);
					if (tree) {
						tree(a, v);
					}
					if (cycles && (a == 2 || a == 3) && Math.floorMod(v - first, 2) == 0) {
						// a stand: a post and a rail beside it, two cells long
						put(Role.FIXTURE, a, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, LOT_STAND_POST);
						put(Role.RAILING, a, StationGeometry.PLATFORM_Y, v + 1, LocalDir.PLUS_V, LOT_STAND_RAIL);
					}
				}
			}
			// bollards close the ends of the drop-off so nothing parks across the way to the door
			for (final int v : new int[]{front - LOT_DROP_OFF - 1, front + LOT_DROP_OFF + 1}) {
				if (v > vFrom && v < vTo) {
					put(Role.FIXTURE, StationGeometry.PARKING_DEPTH - 1, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U, LOT_BOLLARD);
					put(Role.FIXTURE, LOT_BAY + 6, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U, LOT_BOLLARD);
				}
			}
		}

		/** A tree in the planter: a trunk four high (the first block is the planter cell itself) and a crown of leaves round its top. */
		private void tree(int a, int v) {
			for (int y = StationGeometry.PLATFORM_Y; y <= StationGeometry.PLATFORM_Y + 3; y++) {
				put(Role.FIXTURE, a, y, v, LocalDir.PLUS_V, LOT_TRUNK, "axis=y");
			}
			final int top = StationGeometry.PLATFORM_Y + 3;
			for (int da = -1; da <= 1; da++) {
				for (int dv = -1; dv <= 1; dv++) {
					if (da != 0 || dv != 0) {
						put(Role.FIXTURE, a + da, top, v + dv, LocalDir.PLUS_V, LOT_HEDGE, "persistent=true");
					}
					put(Role.FIXTURE, a + da, top + 1, v + dv, LocalDir.PLUS_V, LOT_HEDGE, "persistent=true");
				}
			}
		}

		/**
		 * Where two walls meet (a corner, or a wall butting into another), a window becomes a solid post in the building's
		 * trim. Most window blocks are thin (a panel in the middle of the cell, or a pane), so at a corner their edge showed
		 * as a sliver and the corner looked open; a full glass block at a corner had no frame. Decided on the walls as
		 * planned, so one post never turns its neighbour into another.
		 */
		void cornerPosts() {
			final String post = palette.building().trim();
			final List<PlannedBlock> corners = new ArrayList<>();
			for (final PlannedBlock block : cells.values()) {
				if (block.role() != Role.WINDOW) {
					continue;
				}
				final boolean alongU = wallAt(block.u() - 1, block.y(), block.v()) || wallAt(block.u() + 1, block.y(), block.v());
				final boolean alongV = wallAt(block.u(), block.y(), block.v() - 1) || wallAt(block.u(), block.y(), block.v() + 1);
				if (alongU && alongV) {
					corners.add(block);
				}
			}
			for (final PlannedBlock block : corners) {
				cells.put(new Cell(block.u(), block.y(), block.v()), new PlannedBlock(block.u(), block.y(), block.v(), Role.WALL, post, block.front(), "", ""));
			}
		}

		private boolean wallAt(int u, int y, int v) {
			final PlannedBlock block = cells.get(new Cell(u, y, v));
			return block != null && (block.role() == Role.WALL || block.role() == Role.WINDOW);
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
					// gravel needs a block under it (here and between tracks)
					supports.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, -2, layout.vMin(), track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, -2,
							layout.trackEnd(track)));
				}
				if (layout.deadEnd(track)) {
					// the ground under a bay track's buffer stop
					final int v = layout.trackEnd(track) + 1;
					supports.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, -1, v, track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, -1, v));
				}
			}
			if (spec.trackBed().falls()) {
				for (final Box gap : bedGaps()) {
					supports.add(new Box(gap.uMin(), -2, gap.vMin(), gap.uMax(), -2, gap.vMax()));
				}
			}
			if (layout.terminus()) {
				supports.add(new Box(layout.headFrom(), -1, length, layout.headTo(), -1, layout.headEnd()));
				supports.add(new Box(headDoor() - 1, -1, layout.headEnd() + 1, headDoor() + 1, -1, layout.vMax()));
				if (layout.forecourtDepth() > 0) {
					supports.add(new Box(layout.headFrom(), -1, layout.forecourtFrom(), layout.headTo(), -1, layout.vMax()));
				}
			}
			if (site.building()) {
				supports.add(new Box(0, -1, buildingFrom(), layout.buildingWidth() - 1, -1, buildingTo()));
			}
			supports.addAll(extraSupport);
			return new StationPlan(spec, layout, blocks, envelopes, headroom, supports, palette.blockId(Role.FOUNDATION));
		}
	}
}
