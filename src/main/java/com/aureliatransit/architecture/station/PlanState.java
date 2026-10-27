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

	/** The block an underground station's walls are tiled with (see PlanUnderground). */
	abstract String undergroundTile();

	abstract int apronU();

	abstract int streetWall();

	/** Draws the real station's own head building or side building from its sheet (see PlanLandmark). */
	abstract void landmarkHead();

	abstract void landmarkSide();

	final StationSpec spec;

	/** Not final: a stacked deep station moves its second bore over the first once the shared layers have planned it beside it. */
	TrackLayout layout;

	final StylePalette palette;

	/** The real station's own buildings, when the station is named after one with a sheet ({@link Landmarks}); else null. */
	final Landmark landmark;

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
		final Landmark named = Landmarks.of(spec);
		this.length = layout.platformLength();
		this.columnV = columnPositions(length);
		this.site = StationLayout.of(spec, layout);
		this.far = site.farBridge(spec, layout);
		this.scale = spec.buildingScale();
		// a real train shed is the station's whole mass, entered at its ends (Hamburg's Wandelhalle and bridges): beside it the
		// building is the entrance block under its tower, not a wing along the full shed (10-24)
		this.wingBays = site.building() && !spec.real().trainShed() ? wingBays(scale, length, site.vStart(), site.vEnd()) : 0;
		// a side sheet that runs the length of the building needs a grand one with room for its end pavilions; else the generic one stays
		this.landmark = named != null && named.anchor() == Landmark.Anchor.SIDE
				&& (named.compactOnly() && scale != StationScale.COMPACT || named.usesEnd() && (scale != StationScale.GRAND || wingBays < Landmarks.MIN_SIDE_WINGS)) ? null : named;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			canopySpans.put(band, canopySpan(spec, layout, site, band));
		}
	}

	/** Each platform's first and last canopy row. */
	final java.util.Map<TrackLayout.PlatformBand, int[]> canopySpans = new java.util.HashMap<>();

	/**
	 * Platforms at least this long beside a compact building get a canopy over part of their length only: a town station's
	 * canopy covers the building and the footbridge stairs, and the platform runs on past it in the open (Teddington, 10-24).
	 */
	static final int PARTIAL_CANOPY_PLATFORM = 96;

	/** A partial canopy's share of its platform, in fifths: two fifths, so 80 of a 200-long platform (53 m). */
	static final int PARTIAL_CANOPY_FIFTHS = 2;

	/** Rows a partial canopy reaches past the building, stairs and lift it covers. */
	static final int PARTIAL_CANOPY_MARGIN = 4;

	/**
	 * First and last row a platform canopy covers: the whole platform, except beside a compact building on a long through
	 * platform, where it covers the building's frontage and the main footbridge's flights and lift on that platform, and at
	 * least {@link #PARTIAL_CANOPY_FIFTHS} fifths of the platform, centred on them.
	 */
	static int[] canopySpan(StationSpec spec, TrackLayout layout, StationLayout site, TrackLayout.PlatformBand band) {
		final int[] whole = {band.vFrom(), band.vTo()};
		if (spec.buildingScale() != StationScale.COMPACT || !site.building() || layout.terminus() || band.length() < PARTIAL_CANOPY_PLATFORM) {
			return whole;
		}
		int lo = site.vStart();
		int hi = site.vEnd();
		// the main footbridge's flights and lift; the far one's stand in the open under hoods of their own
		for (final StationLayout.Flight flight : site.flights(band)) {
			lo = Math.min(lo, Math.min(flight.landingV(), flight.wallV()));
			hi = Math.max(hi, Math.max(flight.landingV(), flight.wallV()));
		}
		final StationLayout.Lift lift = site.platformLift(band, layout);
		if (lift != null) {
			lo = Math.min(lo, lift.vMin());
			hi = Math.max(hi, lift.vMax());
		}
		lo -= PARTIAL_CANOPY_MARGIN;
		hi += PARTIAL_CANOPY_MARGIN;
		final int least = band.length() * PARTIAL_CANOPY_FIFTHS / 5;
		if (hi - lo + 1 < least) {
			final int grow = least - (hi - lo + 1);
			lo -= grow / 2;
			hi += grow - grow / 2;
		}
		// slid back onto the platform where it ran off one end
		if (lo < band.vFrom()) {
			hi += band.vFrom() - lo;
			lo = band.vFrom();
		}
		if (hi > band.vTo()) {
			lo = Math.max(band.vFrom(), lo - (hi - band.vTo()));
			hi = band.vTo();
		}
		return new int[]{lo, hi};
	}

	/** First row of this platform's canopy. */
	int canopyStart(TrackLayout.PlatformBand band) {
		return canopySpans.getOrDefault(band, new int[]{band.vFrom(), band.vTo()})[0];
	}

	/** Last row of this platform's canopy. */
	int canopyEnd(TrackLayout.PlatformBand band) {
		return canopySpans.getOrDefault(band, new int[]{band.vFrom(), band.vTo()})[1];
	}

	/**
	 * True when a platform canopy runs over the whole of this flight, so the canopy roofs it; a flight past a partial canopy
	 * (the far footbridge's at a town station) has a hood of its own.
	 */
	boolean canopyOver(TrackLayout.PlatformBand band, StationLayout.Flight flight) {
		if (roof(band) != RoofType.CANOPY) {
			return false;
		}
		// a canopy over the whole platform roofs every flight on it, even one whose top runs on past a short platform's end
		final boolean partial = canopyStart(band) > band.vFrom() || canopyEnd(band) < band.vTo();
		return !partial || underCanopy(band, flight.wallV()) && underCanopy(band, flight.landingV());
	}

	/** True where row v of this platform is under its canopy. */
	boolean underCanopy(TrackLayout.PlatformBand band, int v) {
		return v >= canopyStart(band) && v <= canopyEnd(band);
	}

	/** Whole bays per wing so the block and both wings cover about the scale's share of the platform, the same both sides. */
	static int wingBays(StationScale scale, int length, int vStart, int vEnd) {
		final int span = scale.wingSpan(length);
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

	/**
	 * True for a real station's train shed that springs from the platforms and spans them freely (St Pancras): no columns or
	 * cross girders under it, only its ribs, the girders along its eaves and its side walls.
	 */
	boolean freeShed() {
		final Landmark.Shed shed = landmark == null ? null : landmark.shed();
		return shed != null && shed.eavesY() > 0 && spec.roof() == RoofType.HALL && spec.shed().arched() && hallBeamY() == shed.eavesY();
	}

	/** Places a block unless the cell is taken or must stay empty; true if placed. */
	/**
	 * How far the street doorway reaches either side of its middle cell: a 5-wide door has 2. Doors are 5 wide, 7 at a grand
	 * station, or as wide as asked (Enviro, 10-20: the old 3-wide door was too narrow), and an even width is built one wider.
	 * A side building's door stays clear of its end walls with room for the gate bank's end cells.
	 */
	int entranceHalf() {
		final int asked = spec.options().entranceWidth();
		int half = (asked > 0 ? asked : spec.buildingScale() == StationScale.GRAND ? 7 : 5) / 2;
		if (site.building()) {
			half = Math.min(half, Math.min(site.vEnd() - 2 - site.entranceV(), site.entranceV() - site.vStart() - 2));
		}
		return Math.max(1, half);
	}

	/**
	 * One cell of a gate line. {@code green} is the side people come in from (the ticket hall or the street); {@code kind}
	 * is the ATA gate's kind ({@code kind=end}, {@code kind=gate} or {@code kind=wide}); {@code exit} picks, for MTR's one-way
	 * barriers, a lane that lets people out. MTR's barrier faces the way people walk through it, and the end of a bank is a
	 * glass panel instead of a cabinet: the line's own {@link #fence} facing {@code fenceFront} where the line is MTR glass
	 * fence (Jah, 10-21: a mid-cell panel stood half a block out of that line, a stray glass block in front of the gates),
	 * else ATA's barrier.
	 */
	void gate(int u, int y, int v, LocalDir green, String kind, boolean exit) {
		gate(u, y, v, green, kind, exit, null);
	}

	void gate(int u, int y, int v, LocalDir green, String kind, boolean exit, LocalDir fenceFront) {
		if (spec.options().gates() == StationOptions.GateStyle.ATA) {
			put(Role.GATE, u, y, v, green, palette.blockId(Role.GATE), kind);
		} else if (kind.equals("kind=end")) {
			if (fenceFront != null) {
				fence(u, y, v, fenceFront);
			} else {
				put(Role.BARRIER, u, y, v, green);
			}
		} else {
			put(Role.GATE, u, y, v, exit ? green : green.opposite(), exit ? StylePalette.MTR_BARRIER_EXIT : StylePalette.MTR_BARRIER_ENTRANCE);
		}
	}

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

	/** Longest drop rod: a screen further below the roof frame than this is held by its columns alone. */
	static final int MAX_DROP = 24;

	static final String DROP_ROD = "aurelia_transit_architecture:drop_rod";

	/** The hanging wayfinding sign: its hanger stubs line up with a drop rod's hangers like a hanging screen's. */
	static final String HANGING_SIGN = "aurelia_transit_architecture:hanging_wayfinding_sign";

	/** The y of the first roof, beam or rib above (u, y, v) within {@link #MAX_DROP} with every cell between free, or -1. */
	int ceilingAbove(int u, int y, int v) {
		for (int h = y + 1; h <= y + MAX_DROP; h++) {
			final PlannedBlock block = cells.get(new Cell(u, h, v));
			if (block != null) {
				return block.role().covers() || block.role() == Role.BEAM || block.role() == Role.RIB ? h : -1;
			}
		}
		return -1;
	}

	/**
	 * A rod from the fixture at (u, y, v) up to the first roof, beam or rib above it, when one is within {@link #MAX_DROP} and
	 * every cell between is free; nothing otherwise.
	 */
	void dropRod(int u, int y, int v) {
		final int top = ceilingAbove(u, y, v);
		if (top < 0) {
			return;
		}
		// over a hanging screen or sign the rod splits into hangers onto the stubs on its top, turned with it
		final PlannedBlock below = cells.get(new Cell(u, y, v));
		final String hanger = rodHanger(below);
		for (int h = y + 1; h < top; h++) {
			put(Role.FIXTURE, u, h, v, hanger.equals(PLAIN_ROD) ? LocalDir.PLUS_V : below.front(), DROP_ROD, hanger);
		}
	}

	/** A drop rod with no hangers: a plain rod. */
	static final String PLAIN_ROD = "hanger=none";

	/**
	 * The hangers of a rod over {@code below}, as the rod works them out in the world ({@code DropRodBlock}): both over a
	 * hanging sign or a one-block screen; over the end block of a wide screen only the stub on its outer side (low at model
	 * x 3-4, where the screen does not go on to its right; high at 12-13, where it does not go on to its left), none over a
	 * middle block or anything else. The screen's left, seen from its front, is a quarter turn clockwise from its facing.
	 */
	String rodHanger(PlannedBlock below) {
		if (below == null || !below.blockId().equals(HANGING_PIDS) && !below.blockId().equals(HANGING_SIGN)) {
			return PLAIN_ROD;
		}
		if (below.blockId().equals(HANGING_SIGN)) {
			return "hanger=both";
		}
		final LocalDir left = clockwise(below.front());
		final boolean low = !sameScreen(below, left.opposite());
		final boolean high = !sameScreen(below, left);
		return "hanger=" + (low && high ? "both" : low ? "low" : high ? "high" : "none");
	}

	/** True when the cell beside {@code screen} towards {@code side} is the same screen, facing the same way. */
	private boolean sameScreen(PlannedBlock screen, LocalDir side) {
		final int du = side == LocalDir.PLUS_U ? 1 : side == LocalDir.MINUS_U ? -1 : 0;
		final int dv = side == LocalDir.PLUS_V ? 1 : side == LocalDir.MINUS_V ? -1 : 0;
		final PlannedBlock next = cells.get(new Cell(screen.u() + du, screen.y(), screen.v() + dv));
		return next != null && next.blockId().equals(screen.blockId()) && next.front() == screen.front();
	}

	/** A quarter turn clockwise seen from above: +V to +U, as a heading turns to its right. */
	static LocalDir clockwise(LocalDir dir) {
		return switch (dir) {
			case PLUS_V -> LocalDir.PLUS_U;
			case PLUS_U -> LocalDir.MINUS_V;
			case MINUS_V -> LocalDir.MINUS_U;
			case MINUS_U -> LocalDir.PLUS_V;
		};
	}

	/** True when a departure screen is planned in this cell. */
	boolean screenAt(int u, int y, int v) {
		final PlannedBlock block = cells.get(new Cell(u, y, v));
		return block != null && screen(block.blockId());
	}

	/** MTR's plain glass fence: a two-high panel along one side of its cell, with slim posts at its ends. */
	static final String MTR_FENCE = "mtr:glass_fence_plain";

	/**
	 * A barrier in a ground-floor gate line: MTR's glass fence (Jah, 10-20: "like the MTR fences", set to one side of the cell
	 * rather than down its middle) where the cell above is free too; ATA's own barrier where it is not.
	 */
	void fence(int u, int y, int v, LocalDir front) {
		if (!taken(u, y, v) && !taken(u, y + 1, v)) {
			put(Role.BARRIER, u, y, v, front, MTR_FENCE, "half=lower,number=1");
			put(Role.BARRIER, u, y + 1, v, front, MTR_FENCE, "half=upper,number=1");
		} else {
			put(Role.BARRIER, u, y, v, front);
		}
	}

	static final String TICKET_MACHINE = "aurelia_transit_architecture:ticket_vending_machine";

	/**
	 * ATA's ticket vending machine (Jah, 10-20: a better one than MTR's, two blocks tall; it opens MTR's own ticket screen).
	 * False if either cell is taken.
	 */
	boolean ticketMachine(int u, int y, int v, LocalDir front) {
		return tallFixture(u, y, v, front, TICKET_MACHINE);
	}

	static final String KIOSK = "aurelia_transit_architecture:passenger_info_kiosk";

	/**
	 * The passenger information kiosk, two high. Against a wall it is pushed back flush to it: centred in its cell it stood a
	 * quarter block off the wall and looked unattached (Jah, 10-22). False if either cell is taken.
	 */
	boolean kiosk(int u, int y, int v, LocalDir front) {
		final PlannedBlock wall = cells.get(new Cell(u - dU(front), y, v - dV(front)));
		final boolean flush = wall != null && (wall.role() == Role.WALL || wall.role() == Role.WINDOW || wall.role() == Role.COLUMN);
		return tallFixture(u, y, v, front, KIOSK, flush ? ",offset=flush_back" : "");
	}

	private static int dU(LocalDir dir) {
		return dir == LocalDir.PLUS_U ? 1 : dir == LocalDir.MINUS_U ? -1 : 0;
	}

	private static int dV(LocalDir dir) {
		return dir == LocalDir.PLUS_V ? 1 : dir == LocalDir.MINUS_V ? -1 : 0;
	}

	/** A two-high fixture (MTR's ticket machine, the kiosk): both halves or neither, the lower one at {@code y}. */
	boolean tallFixture(int u, int y, int v, LocalDir front, String blockId) {
		return tallFixture(u, y, v, front, blockId, "");
	}

	/** As {@link #tallFixture(int, int, int, LocalDir, String)}, with {@code extra} (",name=value...") on both halves. */
	boolean tallFixture(int u, int y, int v, LocalDir front, String blockId, String extra) {
		if (taken(u, y, v) || taken(u, y + 1, v)) {
			return false;
		}
		put(Role.FIXTURE, u, y, v, front, blockId, "half=lower" + extra);
		put(Role.FIXTURE, u, y + 1, v, front, blockId, "half=upper" + extra);
		return true;
	}

	boolean taken(int u, int y, int v) {
		final Cell cell = new Cell(u, y, v);
		return cells.containsKey(cell) || air.contains(cell);
	}

	/** True when the cell must stay empty (a stair's headroom, an opening in a floor). */
	boolean cleared(int u, int y, int v) {
		return air.contains(new Cell(u, y, v));
	}

	/** The block planned at a cell, or null. */
	PlannedBlock at(int u, int y, int v) {
		return cells.get(new Cell(u, y, v));
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

	/** True on a row under a real station's hall, between its long walls: its floor covers the platforms there. */
	boolean underHall(int v) {
		return spec.real().hallOverTracks() && site.bridge() && Math.abs(v - site.bridgeV()) <= bridgeWall();
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
