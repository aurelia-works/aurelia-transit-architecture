package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * The planner's state (spec, layout, the cells planned so far) and the primitives every rule uses to place a block.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanState {

	// Rules that live further down the chain but are called from earlier layers.
	abstract boolean gateNumbered(TrackLayout.PlatformBand band);

	abstract List<Integer> opening(TrackLayout.PlatformBand band, StationLayout.Flight flight);

	abstract RoofType roof(TrackLayout.PlatformBand band);

	abstract int hallBeamY();

	abstract int ceiling(int u, int v);

	abstract void stairShaft(TrackLayout.PlatformBand band, StationLayout.Flight flight, StylePalette.Building b);

	abstract void stairHood(TrackLayout.PlatformBand band, StationLayout.Flight flight, StylePalette.Building b);

	abstract boolean nearStairs(TrackLayout.PlatformBand band, int v);

	abstract int apronU();

	abstract int streetWall();

	final StationSpec spec;

	final TrackLayout layout;

	final StylePalette palette;

	final int length;

	final Map<Cell, PlannedBlock> cells = new HashMap<>();

	/** Cells that must stay empty: doorways, stair headroom, landings, the space in front of entrances. */
	final Set<Cell> air = new HashSet<>();

	/** Lift call buttons, placed once everything else has its cells. */
	final List<LiftButton> buttons = new ArrayList<>();

	/**
	 * A call button beside a lift door: in the shaft's front wall at {@code (u, y, v)}, with the landing on the -o side;
	 * {@code otherU} is the wall cell on the doors' other side.
	 */
	record LiftButton(int u, int y, int v, int o, LocalDir in, int otherU) {
	}

	final List<Integer> columnV;

	final List<Box> extraSupport = new ArrayList<>();

	/** Terrain to clear besides the platforms' headroom: an underpass's corridor and stair wells. */
	final List<Box> extraHeadroom = new ArrayList<>();

	/** Support-layer cells an underpass's stair wells pass through; the platform's support is not asked for there. */
	final Set<Cell> holes = new HashSet<>();

	/** The station's layout; {@link #farFootbridge} points it at the far bridge while that bridge is planned. */
	StationLayout site;

	/** The second footbridge at the platforms' far end (phase 2), or null. */
	final StationLayout far;

	List<Integer> hallRows;

	/** v of the building's platform-side doorways: no shed column stands in front of them. */
	final java.util.Set<Integer> doorV = new java.util.HashSet<>();

	final StationScale scale;

	/** Whole bays each wing runs along the platform from the building block's end wall; 0 for no wings. */
	final int wingBays;

	PlanState(StationSpec spec) {
		this.spec = spec;
		this.layout = StationGeometry.layout(spec);
		this.palette = StylePalette.of(spec);
		this.length = layout.platformLength();
		this.columnV = columnPositions(length);
		this.site = StationLayout.of(spec, layout);
		this.far = site.farBridge(spec, layout);
		this.scale = spec.buildingScale();
		this.wingBays = site.building() ? wingBays(scale, length, site.vStart(), site.vEnd()) : 0;
	}

	/** Whole bays per wing so the block and both wings cover about the scale's share of the platform, the same both sides. */
	static int wingBays(StationScale scale, int length, int vStart, int vEnd) {
		final int span = length * scale.wingPercent() / 100;
		if (span == 0) {
			return 0;
		}
		final int from = (length - span) / 2;
		final int to = from + span - 1;
		return Math.max(0, Math.min((vStart - from) / StationGeometry.BAY, (to - vEnd) / StationGeometry.BAY));
	}

	/** First and last v the building and its wings stand on. */
	int buildingFrom() {
		return site.vStart() - wingBays * StationGeometry.BAY;
	}

	int buildingTo() {
		return site.vEnd() + wingBays * StationGeometry.BAY;
	}

	/** Places a block unless the cell is taken or must stay empty; true if placed. */
	boolean put(Role role, int u, int y, int v, LocalDir front) {
		return put(role, u, y, v, front, palette.blockId(role), "");
	}

	boolean put(Role role, int u, int y, int v, LocalDir front, String blockId) {
		return put(role, u, y, v, front, blockId, "");
	}

	boolean put(Role role, int u, int y, int v, LocalDir front, String blockId, String state) {
		final Cell cell = new Cell(u, y, v);
		if (cells.containsKey(cell) || air.contains(cell)) {
			return false;
		}
		// a screen carries the display style it is set to when built
		final String text = role == Role.NAME_SIGN ? spec.name() : screen(blockId) ? palette.screenStyle().id() : "";
		cells.put(cell, new PlannedBlock(u, y, v, role, blockId, front, text, state));
		return true;
	}

	/** A block carrying its own text (a wayfinding sign's "ARROW:destination", see {@link #wayfinding}). */
	boolean putText(Role role, int u, int y, int v, LocalDir front, String blockId, String text) {
		final Cell cell = new Cell(u, y, v);
		if (cells.containsKey(cell) || air.contains(cell)) {
			return false;
		}
		cells.put(cell, new PlannedBlock(u, y, v, role, blockId, front, text, ""));
		return true;
	}

	static boolean screen(String blockId) {
		return blockId.equals(PIDS) || blockId.equals(HANGING_PIDS) || blockId.equals("aurelia_transit_architecture:concourse_board");
	}

	/** A two-high fixture (MTR's ticket machine, the kiosk): both halves or neither, the lower one at {@code y}. */
	boolean tallFixture(int u, int y, int v, LocalDir front, String blockId) {
		if (taken(u, y, v) || taken(u, y + 1, v)) {
			return false;
		}
		put(Role.FIXTURE, u, y, v, front, blockId, "half=lower");
		put(Role.FIXTURE, u, y + 1, v, front, blockId, "half=upper");
		return true;
	}

	boolean taken(int u, int y, int v) {
		final Cell cell = new Cell(u, y, v);
		return cells.containsKey(cell) || air.contains(cell);
	}

	/** True when no cell from y0 to y1 at (u, v) is taken or kept clear. */
	boolean freeColumn(int u, int y0, int y1, int v) {
		for (int y = y0; y <= y1; y++) {
			if (taken(u, y, v)) {
				return false;
			}
		}
		return true;
	}

	void keepClear(int u, int y, int v) {
		air.add(new Cell(u, y, v));
	}

	/** The direction from a side platform's back column towards its track. */
	static LocalDir towardTrack(TrackLayout.PlatformBand band) {
		return band.trackRight() ? LocalDir.PLUS_U : LocalDir.MINUS_U;
	}

	static int backColumn(TrackLayout.PlatformBand band) {
		return band.trackRight() ? band.uMin() : band.uMax();
	}

	static boolean isEdge(TrackLayout.PlatformBand band, int u) {
		return u == band.uMin() && band.trackLeft() || u == band.uMax() && band.trackRight();
	}

	/** The platform in column u, or null. */
	TrackLayout.PlatformBand bandAt(int u) {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (u >= band.uMin() && u <= band.uMax()) {
				return band;
			}
		}
		return null;
	}

	/** Clear half-width of the footbridge corridor: the walkway either side of its centre line. */
	int bridgeHalf() {
		return (site.bridgeWidth() - 1) / 2;
	}

	/** Row offset from the corridor's centre line to its long wall. */
	int bridgeWall() {
		return StationGeometry.bridgeWallOffset(site.bridgeWidth());
	}

	/** A platform's colour at the gate line. */
	/** Each platform's colour, in turn from blue; a seed starts the turn at another colour. */
	String platformColour(TrackLayout.PlatformBand band) {
		return PLATFORM_COLOURS.get((band.number() - 1 + StationSeed.colourOffset(spec.seed(), PLATFORM_COLOURS.size())) % PLATFORM_COLOURS.size());
	}

	/** The lowest rail beside a stair flight on a boarding edge: above the clearway kept for people on the platform. */
	static final int EDGE_RAIL_Y = StationGeometry.WALK_Y + StationGeometry.HEADROOM;

	/**
	 * The lowest y of each column of a stepped surface whose tops are {@code top}. Full blocks ({@code faceJoined} false)
	 * reach down to one above a lower neighbour's top, meeting it edge to edge, which is watertight; girders, which only
	 * join through faces, reach down to the lower neighbour's top.
	 */
	static int[] steps(int[] top, boolean faceJoined) {
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

	static String ata(String id) {
		return "aurelia_transit_architecture:" + id;
	}

	/** v of an underpass's centre line from the platforms' end: its walls at 1 and 5, inside the platforms. */
	static final int UNDERPASS_END = 3;

	/** A hub's shop unit under the gallery: rows along the wall, and rows kept open past the lift's landing before the first. */
	static final int RETAIL_UNIT = 4;

	static final int RETAIL_CLEAR = 3;
}
