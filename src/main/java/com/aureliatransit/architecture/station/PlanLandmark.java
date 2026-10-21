package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.Landmark.Arch;
import com.aureliatransit.architecture.station.Landmark.Ceiling;
import com.aureliatransit.architecture.station.Landmark.Clock;
import com.aureliatransit.architecture.station.Landmark.Colonnade;
import com.aureliatransit.architecture.station.Landmark.Dome;
import com.aureliatransit.architecture.station.Landmark.Facade;
import com.aureliatransit.architecture.station.Landmark.Gable;
import com.aureliatransit.architecture.station.Landmark.Mass;
import com.aureliatransit.architecture.station.Landmark.Pinnacles;
import com.aureliatransit.architecture.station.Landmark.Roof;
import com.aureliatransit.architecture.station.Landmark.Skin;
import com.aureliatransit.architecture.station.Landmark.Tower;
import com.aureliatransit.architecture.station.Landmark.Volume;
import com.aureliatransit.architecture.station.Landmark.Window;

import java.util.List;

/**
 * A real station's own buildings, from its {@link Landmark} sheet: drawn inside the head building's or side building's rule
 * once that rule has reserved its doors, stairs and bridge mouth, and before it puts up its own walls, so the sheet's walls,
 * roofs, towers and domes take their cells and the generic shell only fills what the sheet leaves open.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanLandmark extends PlanUnderground {

	/** An upside-down stair: a cornice, a sill or the shoulder of a round window head. */
	static final String TOP = "half=top,shape=straight";
	static final String BOTTOM = "half=bottom,shape=straight";
	/** A tower's or dome's finial. */
	static final String FINIAL = "minecraft:lightning_rod";
	/** A clock's face, hands and the iron cresting on a mansard. */
	static final String FACE = "minecraft:white_concrete";
	static final String HANDS = "minecraft:black_concrete";
	static final String CRESTING = "minecraft:iron_bars";

	PlanLandmark(StationSpec spec) {
		super(spec);
	}

	/** Directions in a sheet's front frame: out of the street front (-z), into the building (+z), and -x and +x. */
	enum Dir {
		OUT, IN, LEFT, RIGHT;

		Dir opposite() {
			return switch (this) {
				case OUT -> IN;
				case IN -> OUT;
				case LEFT -> RIGHT;
				case RIGHT -> LEFT;
			};
		}
	}

	/**
	 * A sheet's frame on the plan: on a head building {@code x} runs to -u (the right as seen from the street, which looks
	 * along -v) and {@code z} to -v from the street wall at {@code cv}; on a side building {@code x} runs to -v and {@code z}
	 * to +u from the street wall at {@code cu}.
	 */
	record Front(boolean head, int cu, int cv) {

		int u(int x, int z) {
			return head ? cu - x : cu + z;
		}

		int v(int x, int z) {
			return head ? cv - z : cv - x;
		}

		LocalDir dir(Dir d) {
			return switch (d) {
				case OUT -> head ? LocalDir.PLUS_V : LocalDir.MINUS_U;
				case IN -> head ? LocalDir.MINUS_V : LocalDir.PLUS_U;
				case RIGHT -> head ? LocalDir.MINUS_U : LocalDir.MINUS_V;
				case LEFT -> head ? LocalDir.PLUS_U : LocalDir.PLUS_V;
			};
		}
	}

	/** The head building's front frame: x = 0 in the middle of the head building, z = 0 on its street wall. */
	Front headFront() {
		return new Front(true, (layout.headFrom() + layout.headTo()) / 2, layout.headEnd());
	}

	/** The side building's front frame: x = 0 on the street entrance's middle, z = 0 on the street wall. */
	Front sideFront() {
		return new Front(false, streetWall(), site.entranceV());
	}

	@Override
	void landmarkHead() {
		if (landmark != null && landmark.anchor() == Landmark.Anchor.HEAD && layout.terminus()) {
			draw(headFront());
		}
	}

	@Override
	void landmarkSide() {
		if (landmark != null && landmark.anchor() == Landmark.Anchor.SIDE && site.building()) {
			draw(sideFront());
		}
	}

	/** True when the sheet may build at (u, v): inside the footprint, and never over the platforms or tracks. */
	boolean sheetZone(Front f, int u, int v) {
		if (u < 0 || u >= layout.width() || v < layout.vMin() || v > layout.vMax()) {
			return false;
		}
		return f.head() ? v >= length : u < layout.buildingWidth();
	}

	/** Places a block of the sheet at frame cell (x, y, z), facing {@code d}; false if it is outside its zone or taken. */
	boolean at(Front f, Role role, int x, int y, int z, Dir d, String id, String state) {
		final int u = f.u(x, z);
		final int v = f.v(x, z);
		if (id == null || y < StationGeometry.WALK_Y || y > 95 || !sheetZone(f, u, v)) {
			return false;
		}
		return put(role, u, y, v, f.dir(d), id, state);
	}

	boolean at(Front f, Role role, int x, int y, int z, Dir d, String id) {
		return at(f, role, x, y, z, d, id, "");
	}

	/** Draws every mass, the ones that cut into walls (clocks, arches, colonnades, gables, towers) before the walls. */
	void draw(Front f) {
		final List<Mass> masses = landmark.masses();
		for (final Class<?> kind : List.of(Clock.class, Arch.class, Ceiling.class, Colonnade.class, Gable.class, Tower.class, Volume.class, Dome.class,
				Pinnacles.class)) {
			for (final Mass m : masses) {
				if (!kind.isInstance(m)) {
					continue;
				}
				if (m instanceof Clock c) {
					clock(f, c);
				} else if (m instanceof Arch a) {
					arch(f, a);
				} else if (m instanceof Colonnade c) {
					colonnade(f, c);
				} else if (m instanceof Gable g) {
					gable(f, g);
				} else if (m instanceof Tower t) {
					tower(f, t);
				} else if (m instanceof Volume v) {
					volume(f, v);
				} else if (m instanceof Dome d) {
					dome(f, d);
				} else if (m instanceof Pinnacles p) {
					pinnacles(f, p);
				} else if (m instanceof Ceiling c) {
					ceiling(f, c);
				}
			}
		}
	}

	// ---- walls ----

	/** One wall of a volume: its cells run along {@code along} from (x, z), {@code n} of them, and it looks {@code out}. */
	record Face(int x, int z, Dir along, int n, Dir out) {

		int x(int s) {
			return along == Dir.RIGHT ? x + s : along == Dir.LEFT ? x - s : x;
		}

		int z(int s) {
			return along == Dir.IN ? z + s : along == Dir.OUT ? z - s : z;
		}

		int ox(int s) {
			return x(s) + (out == Dir.RIGHT ? 1 : out == Dir.LEFT ? -1 : 0);
		}

		int oz(int s) {
			return z(s) + (out == Dir.IN ? 1 : out == Dir.OUT ? -1 : 0);
		}
	}

	/** The deepest row the sheet may build on: the head zone ends where the tracks begin, the side zone at the building. */
	int zoneDepth(Front f) {
		return f.head() ? f.cv() - length : layout.buildingWidth() - 1 - f.cu();
	}

	void volume(Front f, Volume whole) {
		// a volume deeper than its zone ends at the zone's edge, with its back wall and parapet there
		final int z1 = Math.min(whole.z1(), zoneDepth(f));
		if (z1 <= whole.z0()) {
			return;
		}
		final Volume m = new Volume(whole.x0(), whole.x1(), whole.z0(), z1, whole.height(), whole.skin(), whole.facade(), whole.roof(),
				whole.rise(), whole.openBack(), whole.dormers(), whole.openFront(), whole.bottom());
		final List<Face> faces = List.of(new Face(m.x0(), m.z0(), Dir.RIGHT, m.x1() - m.x0() + 1, Dir.OUT),
				new Face(m.x0(), m.z1(), Dir.RIGHT, m.x1() - m.x0() + 1, Dir.IN),
				new Face(m.x0(), m.z0() + 1, Dir.IN, m.z1() - m.z0() - 1, Dir.LEFT),
				new Face(m.x1(), m.z0() + 1, Dir.IN, m.z1() - m.z0() - 1, Dir.RIGHT));
		for (int i = 0; i < faces.size(); i++) {
			wall(f, m, faces.get(i), i == 1 && m.openBack() || i == 0 && m.openFront(), i >= 2);
		}
		if (m.dormers() && m.roof() != Roof.FLAT) {
			dormers(f, m);
		}
		switch (m.roof()) {
			case FLAT -> flatRoof(f, m);
			case HIPPED, GABLE_X, GABLE_Z, MANSARD -> pitched(f, m);
		}
	}

	/** Where cell {@code s} of an {@code n}-cell wall stands in its bay: -bay..bay-1 in half cells from the bay's middle. */
	static int inBay(int s, int n, int bay) {
		final int d2 = 2 * s - (n - 1);
		return Math.floorMod(d2 + bay, 2 * bay) - bay;
	}

	/** A window column: within the window's width of its bay's middle (rounded to the wall's parity). */
	static boolean windowColumn(int s, int n, Facade fa) {
		return fa.window() != Window.NONE && Math.abs(inBay(s, n, fa.bay())) <= fa.width() - 1;
	}

	/** A pilaster column: the cell on each bay line. */
	static boolean pilasterColumn(int s, int n, Facade fa) {
		final int w = inBay(s, n, fa.bay());
		return w == -fa.bay() || w == -fa.bay() + 1;
	}

	/** The window rows of a storey: row r (0 at the storey's floor) from the sill to the window's head. */
	static boolean windowRow(int y, Facade fa) {
		final int r = Math.floorMod(y - 1, fa.storey());
		return r >= fa.sill() && r < fa.sill() + fa.height();
	}

	void wall(Front f, Volume m, Face face, boolean openGround, boolean side) {
		final Facade fa = m.facade();
		final Skin s = m.skin();
		final int n = face.n();
		if (n <= 0) {
			return;
		}
		for (int i = 0; i < n; i++) {
			final boolean corner = !side && (i == 0 || i == n - 1);
			final boolean pilaster = !corner && pilasterColumn(i, n, fa);
			final boolean column = !corner && !pilaster && windowColumn(i, n, fa);
			for (int y = Math.max(1, m.bottom()); y <= m.height(); y++) {
				if (openGround && y < StationGeometry.UPPER_FLOOR_Y) {
					continue;
				}
				final int r = Math.floorMod(y - 1, fa.storey());
				final boolean band = r == 0 && y > 1 || y == m.height();
				final boolean glass = column && !band && glassAt(fa, y, m.height());
				String id;
				String state = "";
				Dir d = face.out();
				Role role = Role.WALL;
				if (corner) {
					id = y <= fa.baseRows() ? s.base() : s.trim();
				} else if (pilaster) {
					// a pilaster is the wall's own block, standing proud with relief; the trim marks its corners and bands
					id = y <= fa.baseRows() ? s.base() : band ? s.trim() : s.wall();
				} else if (glass) {
					id = s.glass();
					role = Role.WINDOW;
					// the head of a round or pointed window: its outer cells are stairs turned away from the middle
					final boolean head = fa.window() != Window.RIBBON && fa.window() != Window.CURTAIN
							&& Math.floorMod(y - 1, fa.storey()) == fa.sill() + fa.height() - 1;
					final int w = inBay(i, n, fa.bay());
					if (head && (fa.window() == Window.ROUND || fa.window() == Window.POINTED) && fa.width() >= 2
							&& Math.abs(w) >= fa.width() - 2 && w != 0) {
						id = stairsOf(s.wall(), s.cornice());
						state = TOP;
						role = Role.WALL;
						d = (w < 0) == (face.along() == Dir.RIGHT || face.along() == Dir.IN) ? face.along().opposite() : face.along();
					}
				} else if (band) {
					id = s.trim();
				} else if (y <= fa.baseRows()) {
					id = s.base();
				} else if (fa.bandEvery() > 0 && y % fa.bandEvery() == 0 && s.accent() != null) {
					id = s.accent();
				} else {
					id = s.wall();
				}
				at(f, role, face.x(i), y, face.z(i), d, id, state);
				// relief: pilasters stand proud, string courses and the cornice overhang, sills under the windows
				// ... and none of them low enough to hit a passenger's head beside the wall
				if (openGround || y <= StationGeometry.HEADROOM) {
					continue;
				}
				if (y == m.height() || band && fa.relief()) {
					at(f, Role.WALL, face.ox(i), y, face.oz(i), face.out().opposite(), s.cornice(), TOP);
				} else if (pilaster && fa.relief()) {
					at(f, Role.WALL, face.ox(i), y, face.oz(i), face.out(), y <= fa.baseRows() ? s.base() : s.wall());
				} else if (column && fa.window() != Window.CURTAIN && fa.window() != Window.RIBBON && !band
						&& Math.floorMod(y - 1, fa.storey()) == fa.sill() - 1 && glassAt(fa, y + 1, m.height())) {
					at(f, Role.WALL, face.ox(i), y, face.oz(i), face.out().opposite(), s.cornice(), TOP);
				}
			}
		}
	}

	/** The stairs made of a full block, for a window head in the wall's own material; {@code fallback} when it has none. */
	static String stairsOf(String block, String fallback) {
		if (block == null) {
			return fallback;
		}
		final String stairs = switch (block) {
			case "minecraft:bricks" -> "minecraft:brick_stairs";
			case "minecraft:stone_bricks" -> "minecraft:stone_brick_stairs";
			case "minecraft:mud_bricks" -> "minecraft:mud_brick_stairs";
			case "minecraft:smooth_sandstone" -> "minecraft:smooth_sandstone_stairs";
			case "minecraft:sandstone", "minecraft:cut_sandstone" -> "minecraft:sandstone_stairs";
			case "minecraft:smooth_red_sandstone", "minecraft:cut_red_sandstone" -> "minecraft:smooth_red_sandstone_stairs";
			case "minecraft:quartz_block", "minecraft:smooth_quartz" -> "minecraft:smooth_quartz_stairs";
			case "minecraft:deepslate_bricks" -> "minecraft:deepslate_brick_stairs";
			case "minecraft:deepslate_tiles" -> "minecraft:deepslate_tile_stairs";
			case "minecraft:polished_andesite", "minecraft:andesite" -> "minecraft:polished_andesite_stairs";
			case "minecraft:polished_diorite", "minecraft:diorite" -> "minecraft:polished_diorite_stairs";
			case "minecraft:polished_granite", "minecraft:granite" -> "minecraft:polished_granite_stairs";
			case "minecraft:smooth_stone", "minecraft:stone" -> "minecraft:stone_stairs";
			case "minecraft:nether_bricks" -> "minecraft:nether_brick_stairs";
			case "minecraft:red_nether_bricks" -> "minecraft:red_nether_brick_stairs";
			case "minecraft:prismarine_bricks" -> "minecraft:prismarine_brick_stairs";
			case "minecraft:end_stone_bricks" -> "minecraft:end_stone_brick_stairs";
			case "minecraft:polished_blackstone_bricks" -> "minecraft:polished_blackstone_brick_stairs";
			case "minecraft:cut_copper" -> "minecraft:cut_copper_stairs";
			case "minecraft:oxidized_cut_copper" -> "minecraft:oxidized_cut_copper_stairs";
			case "minecraft:weathered_cut_copper" -> "minecraft:weathered_cut_copper_stairs";
			default -> null;
		};
		return stairs != null ? stairs : fallback;
	}

	/** True when a window column is glazed at row y of a wall {@code height} high. */
	static boolean glassAt(Facade fa, int y, int height) {
		if (y >= height) {
			return false;
		}
		return switch (fa.window()) {
			case NONE -> false;
			case CURTAIN -> true;
			case RIBBON, RECT, ROUND, POINTED -> windowRow(y, fa);
		};
	}

	void flatRoof(Front f, Volume m) {
		final Skin s = m.skin();
		final int top = m.height();
		for (int x = m.x0() + 1; x < m.x1(); x++) {
			for (int z = m.z0() + 1; z < m.z1(); z++) {
				final boolean lamp = Math.floorMod(x + z, 4) == 0;
				at(f, lamp ? Role.LIGHT_PANEL : Role.BUILDING_ROOF, x, top, z, Dir.OUT, lamp ? palette.building().light() : s.roof());
			}
		}
		// the parapet round the edge
		for (int x = m.x0(); x <= m.x1(); x++) {
			at(f, Role.BUILDING_ROOF, x, top + 1, m.z0(), Dir.OUT, s.trim());
			at(f, Role.BUILDING_ROOF, x, top + 1, m.z1(), Dir.IN, s.trim());
		}
		for (int z = m.z0() + 1; z < m.z1(); z++) {
			at(f, Role.BUILDING_ROOF, m.x0(), top + 1, z, Dir.LEFT, s.trim());
			at(f, Role.BUILDING_ROOF, m.x1(), top + 1, z, Dir.RIGHT, s.trim());
		}
	}

	/**
	 * A pitched roof on a volume, from the row over its walls: each cell's height is its distance in from the nearest eaves
	 * the roof slopes to (all four walls for a hip, the front and back for a ridge along x, the sides for one along z) times
	 * the rise; a stair on top turned uphill, the roof block under it, the wall block under the slope at a gable end. A
	 * mansard rises steeply two steps and is flat above, with iron cresting round its top.
	 */
	void pitched(Front f, Volume m) {
		final Skin s = m.skin();
		final int base = m.height() + 1;
		final Roof roof = m.roof();
		final int rise = roof == Roof.MANSARD ? 3 : Math.max(1, m.rise());
		final int rings = roof == Roof.MANSARD ? 2 : Integer.MAX_VALUE;
		int peak = 0;
		for (int x = m.x0(); x <= m.x1(); x++) {
			for (int z = m.z0(); z <= m.z1(); z++) {
				peak = Math.max(peak, ring(m, x, z));
			}
		}
		for (int x = m.x0(); x <= m.x1(); x++) {
			for (int z = m.z0(); z <= m.z1(); z++) {
				final int k = Math.min(ring(m, x, z), rings);
				final boolean flatTop = k >= rings || k == peak;
				final int topY = base + k * rise;
				// down to the next ring's top, so each step stands on the one below by a face, not only an edge
				for (int y = Math.max(base, topY - rise); y < topY; y++) {
					// a dormer window in the lowest step over each window column of the front and back
					final boolean dormer = m.dormers() && k == 0 && y == topY - 1 && (z == m.z0() || z == m.z1())
							&& windowColumn(x - m.x0(), m.x1() - m.x0() + 1, m.facade());
					at(f, dormer ? Role.WINDOW : Role.BUILDING_ROOF, x, y, z, z == m.z0() ? Dir.OUT : Dir.IN, dormer ? s.glass() : s.roof());
				}
				if (flatTop) {
					at(f, Role.BUILDING_ROOF, x, topY, z, Dir.OUT, s.roof());
					if (roof == Roof.MANSARD && k == rings && ring(m, x, z) == rings) {
						at(f, Role.WALL, x, topY + 1, z, Dir.OUT, CRESTING);
					}
				} else {
					at(f, Role.BUILDING_ROOF, x, topY, z, uphill(m, x, z), s.roofStair(), BOTTOM);
				}
				// gable ends: the wall fills the triangle under the roof
				final boolean end = roof == Roof.GABLE_X && (x == m.x0() || x == m.x1()) || roof == Roof.GABLE_Z && (z == m.z0() || z == m.z1());
				if (end) {
					for (int y = base; y < topY - rise + 1; y++) {
						at(f, Role.WALL, x, y, z, Dir.OUT, s.wall());
					}
				}
			}
		}
	}

	/**
	 * Gabled dormers on a pitched roof's front and back slopes, over every other window bay: a window two high in the wall's
	 * plane over the eaves, cheeks of wall either side running three cells back into the roof, and a little gable roof of
	 * stairs on top. A mansard's dormers are its lowest step's windows (see {@link #pitched}).
	 */
	void dormers(Front f, Volume m) {
		if (m.roof() == Roof.MANSARD) {
			return;
		}
		final Skin s = m.skin();
		final int n = m.x1() - m.x0() + 1;
		final int y = m.height() + 1;
		int bay = 0;
		for (int i = 2; i < n - 2; i++) {
			if (!windowColumn(i, n, m.facade()) || windowColumn(i - 1, n, m.facade())) {
				continue;
			}
			// the first column of a window: the dormer is centred on the window's middle
			int w = 1;
			while (i + w < n && windowColumn(i + w, n, m.facade())) {
				w++;
			}
			if (bay++ % 2 == 1) {
				continue;
			}
			final int cx = m.x0() + i + (w - 1) / 2;
			for (final boolean front : new boolean[]{true, false}) {
				final int z0 = front ? m.z0() : m.z1();
				final int dz = front ? 1 : -1;
				final Dir out = front ? Dir.OUT : Dir.IN;
				for (int k = 0; k < 3; k++) {
					final int z = z0 + dz * k;
					for (int j = 0; j < 3; j++) {
						at(f, k == 0 && j < 2 ? Role.WINDOW : Role.WALL, cx, y + j, z, out, k == 0 && j < 2 ? s.glass() : s.wall());
						at(f, Role.WALL, cx - 1, y + j, z, out, j == 2 && k == 0 ? s.trim() : s.wall());
						at(f, Role.WALL, cx + 1, y + j, z, out, j == 2 && k == 0 ? s.trim() : s.wall());
					}
					at(f, Role.BUILDING_ROOF, cx - 1, y + 3, z, Dir.RIGHT, s.roofStair(), BOTTOM);
					at(f, Role.BUILDING_ROOF, cx + 1, y + 3, z, Dir.LEFT, s.roofStair(), BOTTOM);
					at(f, Role.BUILDING_ROOF, cx, y + 3, z, out, s.roof());
				}
				at(f, Role.WALL, cx, y + 4, z0, out, FINIAL);
			}
		}
	}

	/** How many steps in from the eaves cell (x, z) of a volume's roof is. */
	static int ring(Volume m, int x, int z) {
		final int ax = Math.min(x - m.x0(), m.x1() - x);
		final int az = Math.min(z - m.z0(), m.z1() - z);
		return switch (m.roof()) {
			case GABLE_X -> az;
			case GABLE_Z -> ax;
			default -> Math.min(ax, az);
		};
	}

	/** The way the roof rises at (x, z): away from its nearest eaves. */
	static Dir uphill(Volume m, int x, int z) {
		final int[] d = {x - m.x0(), m.x1() - x, z - m.z0(), m.z1() - z};
		final Dir[] up = {Dir.RIGHT, Dir.LEFT, Dir.IN, Dir.OUT};
		int best = -1;
		for (int i = 0; i < 4; i++) {
			if (m.roof() == Roof.GABLE_X && i < 2 || m.roof() == Roof.GABLE_Z && i >= 2) {
				continue;
			}
			if (best < 0 || d[i] < d[best]) {
				best = i;
			}
		}
		return up[best];
	}

	// ---- towers and domes ----

	static boolean inside(boolean octagonal, int dx, int dz, int r) {
		return octagonal ? PlanHead.octagon(dx, dz, r) : r >= 0 && Math.max(Math.abs(dx), Math.abs(dz)) <= r;
	}

	static Dir outward(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Dir.RIGHT : Dir.LEFT;
		}
		return dz >= 0 ? Dir.IN : Dir.OUT;
	}

	void tower(Front f, Tower t) {
		final Skin s = t.skin();
		final int r = t.r();
		final int top = t.y0() + t.height();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				final boolean in = inside(t.octagonal(), dx, dz, r);
				final boolean shell = in && !inside(t.octagonal(), dx, dz, r - 1);
				final boolean cornerCell = Math.abs(dx) == Math.abs(dz) || t.octagonal() && Math.abs(dx) + Math.abs(dz) == r + r / 2;
				final boolean mid = dx == 0 || dz == 0;
				final Dir out = outward(dx, dz);
				for (int y = t.y0(); y < top; y++) {
					if (!shell) {
						continue;
					}
					final int fromTop = top - 1 - y;
					final boolean clockRow = t.clock() && r >= 2 && fromTop == 3;
					final boolean slit = mid && (y - t.y0()) % 6 >= 2 && (y - t.y0()) % 6 <= 3 && fromTop > 5;
					final boolean belfry = mid && fromTop >= 0 && fromTop <= 1 && r >= 2;
					String id = cornerCell || y == top - 1 ? s.trim() : y - t.y0() < 2 ? s.base() : s.wall();
					Role role = Role.WALL;
					if (clockRow && mid) {
						id = Landmarks.CLOCK_FACE;
						role = Role.WALL;
					} else if (slit || belfry) {
						id = s.glass();
						role = Role.WINDOW;
					}
					at(f, role, t.x() + dx, y, t.z() + dz, out, id);
				}
				// the cornice round the top of the shaft
				if (!in && inside(t.octagonal(), dx, dz, r + 1) && (Math.abs(dx) <= r || Math.abs(dz) <= r)) {
					at(f, Role.WALL, t.x() + dx, top - 1, t.z() + dz, out.opposite(), s.cornice(), TOP);
				}
				// a floor closing the shaft under its cap
				if (in) {
					at(f, Role.BUILDING_ROOF, t.x() + dx, top, t.z() + dz, Dir.OUT, s.roof());
				}
			}
		}
		cap(f, t.x(), t.z(), r, top + 1, t.octagonal(), t.cap(), t.capHeight(), s);
	}

	/** A tower's cap on its floor at y: see {@link Cap}. */
	void cap(Front f, int cx, int cz, int r, int y, boolean octagonal, Landmark.Cap cap, int height, Skin s) {
		switch (cap) {
			case FLAT -> {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (inside(octagonal, dx, dz, r) && !inside(octagonal, dx, dz, r - 1) && Math.floorMod(dx + dz, 2) == 0) {
							at(f, Role.WALL, cx + dx, y, cz + dz, outward(dx, dz), s.trim());
						}
					}
				}
			}
			case PYRAMID, SPIRE, MANSARD -> {
				final int steps = r + 1;
				final int rise = cap == Landmark.Cap.MANSARD ? 3 : Math.max(1, Math.max(cap == Landmark.Cap.SPIRE ? 2 : 1, height / steps));
				final int rings = cap == Landmark.Cap.MANSARD ? Math.max(1, r / 2) : steps;
				int yy = y;
				for (int k = 0; k < rings && r - k >= 0; k++) {
					final int rr = r - k;
					for (int dx = -rr; dx <= rr; dx++) {
						for (int dz = -rr; dz <= rr; dz++) {
							if (!inside(octagonal, dx, dz, rr) || inside(octagonal, dx, dz, rr - 1) && rr > 0) {
								continue;
							}
							// a ring above the first starts beside the top of the one below, so it stands on it by a face
							for (int j = k > 0 ? -1 : 0; j < rise; j++) {
								at(f, Role.BUILDING_ROOF, cx + dx, yy + j, cz + dz, outward(dx, dz), s.roof());
							}
						}
					}
					yy += rise;
				}
				if (cap == Landmark.Cap.MANSARD) {
					final int rr = r - rings;
					for (int dx = -rr; dx <= rr; dx++) {
						for (int dz = -rr; dz <= rr; dz++) {
							if (inside(octagonal, dx, dz, rr)) {
								at(f, Role.BUILDING_ROOF, cx + dx, yy, cz + dz, Dir.OUT, s.roof());
								if (!inside(octagonal, dx, dz, rr - 1)) {
									at(f, Role.WALL, cx + dx, yy + 1, cz + dz, Dir.OUT, CRESTING);
								}
							}
						}
					}
				} else {
					at(f, Role.WALL, cx, yy, cz, Dir.OUT, FINIAL);
				}
			}
			case DOME -> {
				shell(f, cx, cz, r, y, r + 1, false, s.roof());
				if (r >= 3) {
					// a lantern on a dome as wide as a tower's
					cap(f, cx, cz, 1, y + r + 1, false, Landmark.Cap.CUPOLA, 0, s);
				} else {
					at(f, Role.WALL, cx, y + r + 1, cz, Dir.OUT, FINIAL);
				}
			}
			case ONION -> {
				final int h = Math.max(height, 2 * r + 1);
				shell(f, cx, cz, r, y, h, true, s.roof());
				at(f, Role.WALL, cx, y + h, cz, Dir.OUT, FINIAL);
			}
			case CUPOLA -> {
				final int p = Math.max(1, r - 1);
				for (int j = 0; j < 3; j++) {
					for (final int[] c : new int[][]{{-p, -p}, {-p, p}, {p, -p}, {p, p}}) {
						at(f, Role.WALL, cx + c[0], y + j, cz + c[1], Dir.OUT, s.trim());
					}
				}
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (inside(octagonal, dx, dz, r)) {
							at(f, Role.BUILDING_ROOF, cx + dx, y + 3, cz + dz, Dir.OUT, s.trim());
						}
					}
				}
				shell(f, cx, cz, p, y + 4, p + 1, false, s.roof());
				at(f, Role.WALL, cx, y + 5 + p, cz, Dir.OUT, FINIAL);
			}
		}
	}

	/**
	 * A round shell of radius r from y, {@code height} rows high: a hemisphere, or an onion that swells a third wider than r
	 * at a quarter of its height and draws in to a point. Only the outer cells of each ring are placed.
	 */
	void shell(Front f, int cx, int cz, int r, int y, int height, boolean onion, String id) {
		final double[] rho = new double[height];
		for (int j = 0; j < height; j++) {
			rho[j] = shellRadius(r, (j + 0.5) / height, onion);
		}
		for (int j = 0; j < height; j++) {
			// each ring reaches in past the rings above and below it, so the shell has no holes where it flattens
			final double inner = Math.min(rho[j], Math.min(j > 0 ? rho[j - 1] : rho[j], j < height - 1 ? rho[j + 1] : 0)) - 1.2;
			for (int dx = -r - 2; dx <= r + 2; dx++) {
				for (int dz = -r - 2; dz <= r + 2; dz++) {
					final double d = Math.hypot(dx, dz);
					if (d <= rho[j] && d > inner) {
						at(f, Role.BUILDING_ROOF, cx + dx, y + j, cz + dz, outward(dx, dz), id);
					}
				}
			}
		}
	}

	/** A shell's radius at height fraction t: a hemisphere's, or an onion's swelling a third wider at a quarter up. */
	static double shellRadius(int r, double t, boolean onion) {
		return onion ? r * (1 + 0.35 * Math.sin(Math.PI * Math.min(1, t * 1.6))) * Math.pow(1 - t, 0.75)
				: (r + 0.5) * Math.sqrt(Math.max(0, 1 - t * t));
	}

	void dome(Front f, Dome d) {
		final Skin s = d.skin();
		final int r = d.r();
		// the drum: a round wall with a window in every third cell of its middle rows
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				final double dist = Math.hypot(dx, dz);
				if (dist > r + 0.5 || dist <= r - 0.7) {
					continue;
				}
				final boolean window = Math.floorMod(dx * 7 + dz * 3, 3) == 0;
				for (int y = d.y0(); y < d.y0() + d.drum(); y++) {
					final boolean mid = y > d.y0() && y < d.y0() + d.drum() - 1;
					at(f, window && mid ? Role.WINDOW : Role.WALL, d.x() + dx, y, d.z() + dz, outward(dx, dz),
							window && mid ? s.glass() : y == d.y0() + d.drum() - 1 ? s.trim() : s.wall());
				}
			}
		}
		final int base = d.y0() + d.drum();
		final int height = d.onion() ? 2 * r + 1 : r + 1;
		shell(f, d.x(), d.z(), r, base, height, d.onion(), s.roof());
		if (d.lantern()) {
			cap(f, d.x(), d.z(), Math.max(1, r / 5), base + height, true, Landmark.Cap.CUPOLA, 0, s);
		} else {
			at(f, Role.WALL, d.x(), base + height, d.z(), Dir.OUT, FINIAL);
		}
	}

	// ---- details on the walls ----

	/** The front plane at depth z faces out of the street front when the sheet's volumes start there; the back faces in. */
	static Dir faceOut(int z) {
		return z > 0 ? Dir.IN : Dir.OUT;
	}

	void arch(Front f, Arch a) {
		final Skin s = a.skin();
		final PlanHead.ArchShape shape = a.pointed() ? PlanHead.ArchShape.POINTED : PlanHead.ArchShape.ROUND;
		final int h = a.half();
		final int rise = PlanHead.archRise(shape, h);
		final Dir out = Dir.OUT;
		for (int dx = -h - 1; dx <= h + 1; dx++) {
			for (int y = a.bottom(); y <= a.spring() + rise + 1; y++) {
				final int k = y - a.spring();
				final double dist = k <= 0 ? Math.abs(dx) - h : PlanHead.archDistance(shape, dx, k, h);
				if (dist > 1) {
					continue;
				}
				final boolean ring = dist > -0.01;
				final boolean bar = !ring && (Math.floorMod(dx, 3) == 0 || k == 0 || k > 0 && Math.floorMod(k, 4) == 0 && k < rise - 1);
				final boolean glass = !ring && !bar;
				at(f, glass ? Role.WINDOW : Role.WALL, a.x() + dx, y, a.z(), out, ring ? s.trim() : bar ? s.trim() : s.glass());
			}
		}
	}

	void colonnade(Front f, Colonnade c) {
		final Skin s = c.skin();
		for (int x = c.x0(); x <= c.x1(); x++) {
			final boolean column = Math.floorMod(x - c.x0(), c.every()) == 0;
			if (column) {
				for (int y = 1; y < c.height(); y++) {
					at(f, Role.WALL, x, y, c.z(), Dir.OUT, y == 1 || y == c.height() - 1 ? s.trim() : c.column());
				}
			}
			at(f, Role.WALL, x, c.height(), c.z(), Dir.OUT, s.trim());
			at(f, Role.WALL, x, c.height() + 1, c.z(), Dir.IN, s.cornice(), TOP);
			// the portico's ceiling back to the wall behind it
			for (int z = c.z() + 1; z < 0; z++) {
				at(f, Role.BUILDING_ROOF, x, c.height(), z, Dir.OUT, s.trim());
			}
		}
	}

	void gable(Front f, Gable g) {
		final Skin s = g.skin();
		for (int j = 0; j < g.rise(); j++) {
			final int half = g.stepped() ? g.half() - 2 * (j / 2) * g.half() / Math.max(1, g.rise()) : g.half() - j * g.half() / Math.max(1, g.rise());
			if (half < 0) {
				break;
			}
			for (int dx = -half; dx <= half; dx++) {
				final boolean edge = Math.abs(dx) == half;
				if (!g.stepped() && edge && half > 0) {
					at(f, Role.WALL, g.x() + dx, g.y() + j, g.z(), dx < 0 ? Dir.RIGHT : Dir.LEFT, s.roofStair(), BOTTOM);
				} else {
					at(f, Role.WALL, g.x() + dx, g.y() + j, g.z(), Dir.OUT, edge || j == 0 ? s.trim() : s.wall());
				}
			}
		}
		at(f, Role.WALL, g.x(), g.y() + g.rise(), g.z(), Dir.OUT, FINIAL);
	}

	/**
	 * A clock face on the wall: one clock panel (r = 0), or a round white dial r cells each side of its middle with a rim and
	 * black hands at twenty past twelve and marks at the quarters.
	 */
	void clock(Front f, Clock c) {
		if (c.r() == 0) {
			at(f, Role.WALL, c.x(), c.y(), c.z(), faceOut(c.z()), c.face());
			return;
		}
		final Dir out = faceOut(c.z());
		for (int dx = -c.r() - 1; dx <= c.r() + 1; dx++) {
			for (int dy = -c.r() - 1; dy <= c.r() + 1; dy++) {
				final double d = Math.hypot(dx, dy);
				if (d > c.r() + 1.2) {
					continue;
				}
				final boolean rim = d > c.r() + 0.3;
				// the minute hand at twelve, the hour hand towards four, and marks at three, six and nine
				final boolean hand = dx == 0 && dy >= 0 && dy < c.r() || dx > 0 && dx == -dy && dx < c.r() - 1;
				final boolean mark = !rim && Math.round(d) == c.r() && (dx == 0 && dy < 0 || dy == 0);
				at(f, Role.WALL, c.x() + dx, c.y() + dy, c.z(), out, rim ? c.rim() : hand || mark ? HANDS : FACE);
			}
		}
	}

	void ceiling(Front f, Ceiling c) {
		for (int x = c.x0(); x <= c.x1(); x++) {
			for (int z = c.z0(); z <= c.z1(); z++) {
				final int every = Math.max(2, c.every());
				final boolean star = Math.floorMod(x + (Math.floorMod(z / every, 2) == 0 ? 0 : every / 2), every) == 0 && Math.floorMod(z, every) == 0;
				at(f, star ? Role.LIGHT_PANEL : Role.BUILDING_ROOF, x, c.y(), z, Dir.OUT, star ? c.light() : c.block());
			}
		}
	}

	void pinnacles(Front f, Pinnacles p) {
		for (int x = p.x0(); x <= p.x1(); x += Math.max(1, p.every())) {
			at(f, Role.WALL, x, p.y(), p.z(), Dir.OUT, p.shaft());
			at(f, Role.WALL, x, p.y() + 1, p.z(), Dir.OUT, p.shaft());
			at(f, Role.WALL, x, p.y() + 2, p.z(), Dir.OUT, p.cap());
		}
	}
}
