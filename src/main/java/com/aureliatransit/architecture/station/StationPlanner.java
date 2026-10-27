package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a {@link StationPlan} from a spec: railway geometry from {@link StationGeometry}, then roles for every cell, then
 * block ids from the {@link StylePalette}. Deterministic: the same spec always gives an equal plan.
 * <p>
 * Rules run in a fixed order and each cell holds at most one block: when two rules want the same cell the earlier rule wins
 * and the later block is skipped.
 */
public final class StationPlanner {

	static final String PIDS = "aurelia_transit_architecture:platform_pids";
	/** What fills the bed layer under platforms when the station has a track bed. */
	static final String PLATFORM_FILL = "minecraft:black_concrete";
	static final String HANGING_PIDS = "aurelia_transit_architecture:hanging_platform_pids";
	static final String HELP_POINT = "aurelia_transit_architecture:help_point";
	static final String CEILING_SPEAKER = "aurelia_transit_architecture:ceiling_speaker";
	static final String WALL_SPEAKER = "aurelia_transit_architecture:wall_speaker";
	static final String GIRDER = "aurelia_transit_architecture:station_girder";
	/** A shed's frame: the girder's states and joins with a lattice model. */
	static final String ROOF_TRUSS = "aurelia_transit_architecture:roof_truss";
	/**
	 * A walk-in waiting shelter, from the bus kit: a back wall of glass two high, a seat in front of it and a roof over both at
	 * two blocks above its seat. All three face the way the wall is from the seat (the kit is placed "away from the player").
	 */
	static final String SHELTER_GLASS = "aurelia_transit_architecture:bus_shelter_glass";
	static final String SHELTER_ROOF = "aurelia_transit_architecture:bus_shelter_roof";
	static final String SHELTER_SEAT = "aurelia_transit_architecture:bus_shelter_seat";
	/** Cells in a row of shelter blocks. */
	static final int SHELTER_LENGTH = 3;
	/** The narrowest island that takes a shelter wall with a seat on each side and a walkway beside each. */
	static final int SHELTER_ISLAND = 7;
	/** The shortest open platform that gets a shelter. */
	static final int SHELTER_MIN_PLATFORM = 24;
	static final String WAITING_ROOM_GLASS = "aurelia_transit_architecture:glass_wall";
	static final String NUMBER_SIGN = "aurelia_transit_architecture:platform_number_sign";
	/** The canopy's sloping blocks (the low end points the way the block faces): the two halves of a half-block slope. */
	static final String CANOPY_SLOPE_LOWER = "aurelia_transit_architecture:canopy_slope_lower";
	static final String CANOPY_SLOPE_UPPER = "aurelia_transit_architecture:canopy_slope_upper";
	/** One colour per platform at a terminus gate line, in turn: a stripe on the floor and over the arcade, to follow to the gates. */
	static final List<String> PLATFORM_COLOURS = List.of("aurelia_transit_architecture:metro_tile_blue",
			"aurelia_transit_architecture:metro_tile_green", "aurelia_transit_architecture:metro_tile_orange",
			"aurelia_transit_architecture:metro_tile_yellow");

	static final Comparator<PlannedBlock> BUILD_ORDER = Comparator.comparingInt((PlannedBlock b) -> b.role().ordinal())
			.thenComparingInt(PlannedBlock::y).thenComparingInt(PlannedBlock::v).thenComparingInt(PlannedBlock::u);

	/** How far a grand station's clock tower rises above the building block's roof, to its cornice. */
	static final int TOWER_HEIGHT = 11;
	/** The state of every stair in a pitched roof. */
	static final String PITCH_STATE = "half=bottom,shape=straight";
	/** Height of MTR's station name panel over a street door: on the lintel, one row over the three-high doorway. */
	static final int MTR_NAME_Y = 4;
	/** Copper for domes, and glass for giant arched windows (whole blocks, so the arch reads from the street). */
	static final String DOME = "minecraft:oxidized_cut_copper";
	static final String ARCH_GLASS = "aurelia_transit_architecture:clear_float_glass";
	/** A statue's base; its figure is a quartz pillar. */
	static final String STATUE = "minecraft:chiseled_quartz_block";

	private StationPlanner() {
	}

	public static StationPlan plan(StationSpec spec) {
		final Planning planning = new Planning(spec);
		if (spec.isAirport()) {
			// an airport has no tracks, platforms or station building: its own layer plans it all
			planning.airport();
			return planning.finish();
		}
		planning.underpass();
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
		planning.farFootbridge();
		planning.overrunFences();
		planning.columns();
		planning.canopyRoofs();
		planning.hallRoof();
		planning.stairFeet();
		planning.numberSigns();
		planning.lamps();
		planning.shelters();
		planning.benchesAndBins();
		planning.interior();
		planning.headInterior();
		planning.platformEquipment();
		planning.tramSigns();
		planning.nameSigns();
		planning.wayfinding();
		planning.mezzanine();
		planning.speakers();
		planning.hallLamps();
		planning.liftButtons();
		planning.entrances();
		planning.concourseFit();
		planning.endFences();
		planning.pitEndFences();
		planning.cornerPosts();
		planning.parking();
		planning.underground();
		planning.edgeRailings();
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

	record Cell(int u, int y, int v) {
	}

	/** Platforms at least this long (rows, both ends included) get travelators along them. */
	static final int TRAVELATOR_PLATFORM = 72;
	/** Cells of platform kept clear in front of a stair or escalator foot (about 4.5 m of queuing space). */
	static final int STAIR_APPROACH = 5;

	/** One announcement speaker per this many blocks of platform. */
	static final int SPEAKER_SPACING = 32;
	/** A speaker fixed to a post sits this high: above the screens. */
	static final int SPEAKER_Y = StationGeometry.WALK_Y + 3;
	/** The pole an open platform's lamp stands on. */
	static final String LAMP_POLE = "aurelia_transit_architecture:sign_pole";
	/** Roughly how many blocks of platform each departure screen serves. */
	static final int SCREEN_SPACING = 30;

}
