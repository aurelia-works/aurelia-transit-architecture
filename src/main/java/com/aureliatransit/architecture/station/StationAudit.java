package com.aureliatransit.architecture.station;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Measures a finished plan and compares it with what was asked for, the way a reviewer would walk the built station: boarding
 * faces and their lengths, rail nodes at the face ends, roof coverage, headroom, whether a passenger can walk from outside to
 * every platform (and how), name signs, that nothing floats, and the overall size. Runs after {@link LayoutValidator} on a
 * plan without violations; works on the blocks, never on the spec's numbers, so a planner bug shows up here.
 * <p>
 * A {@link Status#FAIL} means the plan does not deliver what was asked and must not be built; a {@link Status#WARN} is a real
 * shortcoming the player should know about (an island platform reached only by walking across a track).
 */
public final class StationAudit {

	public enum Status {
		PASS, WARN, FAIL
	}

	public enum Topic {
		TRACKS, FACES, RAIL_NODES, ROOF, HEADROOM, ACCESS, NAME_SIGNS, GROUNDED, SIZE, LIGHTING, FURNISHING
	}

	/** How a passenger arriving beside the station reaches a platform. */
	public enum Access {
		/** Without crossing a track and without a full-block step (ramps). */
		STEP_FREE,
		/** Without crossing a track, but only via stairs (a warning when step-free was asked for). */
		STAIRS,
		/** Without crossing a track, but only with a one-block step up. */
		STEP,
		/** Only by walking across a track at grade. */
		AT_GRADE,
		UNREACHABLE
	}

	public record Check(Topic topic, Status status, String message) {
	}

	/** One measured boarding face: the run of platform edge cells facing a track. */
	public record Face(int track, int platform, boolean leftOfTrack, int vStart, int length) {

		public int vEnd() {
			return vStart + length - 1;
		}
	}

	/**
	 * @param checks deterministic order: topic by topic as in {@link Topic}, failures and warnings in plan order
	 * @param faces  measured faces, track by track, left side first
	 * @param access per platform, index = platform number - 1
	 */
	public record Result(List<Check> checks, List<Face> faces, List<Access> access, int width, int length, int height) {

		public Result {
			checks = List.copyOf(checks);
			faces = List.copyOf(faces);
			access = List.copyOf(access);
		}

		/** False if any check failed. */
		public boolean buildable() {
			return worst() != Status.FAIL;
		}

		public Status worst() {
			Status worst = Status.PASS;
			for (final Check check : checks) {
				if (check.status().ordinal() > worst.ordinal()) {
					worst = check.status();
				}
			}
			return worst;
		}

		/** Warnings and failures, in check order. */
		public List<Check> problems() {
			final List<Check> problems = new ArrayList<>();
			for (final Check check : checks) {
				if (check.status() != Status.PASS) {
					problems.add(check);
				}
			}
			return problems;
		}

		public List<Check> checks(Topic topic) {
			final List<Check> found = new ArrayList<>();
			for (final Check check : checks) {
				if (check.topic() == topic) {
					found.add(check);
				}
			}
			return found;
		}

		public long count(Status status) {
			return checks.stream().filter(check -> check.status() == status).count();
		}
	}

	private StationAudit() {
	}

	public static Result audit(StationPlan plan) {
		return new Audit(plan).run();
	}

	/** Packs a local cell into one long (21 bits per axis, offset so negative v and y = -1 fit). */
	static long key(int u, int y, int v) {
		final long offset = 1 << 20;
		return ((u + offset) << 42) | ((y + offset) << 21) | (v + offset);
	}

	private static final class Audit {

		private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

		private final StationPlan plan;
		private final StationSpec spec;
		private final TrackLayout layout;
		private final Map<Long, PlannedBlock> cells = new HashMap<>();
		private final List<Check> checks = new ArrayList<>();
		private final List<Face> faces = new ArrayList<>();
		private final List<Access> access = new ArrayList<>();

		Audit(StationPlan plan) {
			this.plan = plan;
			this.spec = plan.spec();
			this.layout = plan.layout();
			for (final PlannedBlock block : plan.blocks()) {
				cells.putIfAbsent(key(block.u(), block.y(), block.v()), block);
			}
		}

		private PlannedBlock at(int u, int y, int v) {
			return cells.get(key(u, y, v));
		}

		private boolean has(int u, int y, int v, Role role) {
			final PlannedBlock block = at(u, y, v);
			return block != null && block.role() == role;
		}

		private void pass(Topic topic, String message) {
			checks.add(new Check(topic, Status.PASS, message));
		}

		private void warn(Topic topic, String message) {
			checks.add(new Check(topic, Status.WARN, message));
		}

		private void fail(Topic topic, String message) {
			checks.add(new Check(topic, Status.FAIL, message));
		}

		Result run() {
			tracks();
			faces();
			railNodes();
			roof();
			headroom();
			access();
			nameSigns();
			grounded();
			lighting();
			furnishing();
			return size();
		}

		private void tracks() {
			final int reserved = plan.trackEnvelopes().size();
			if (reserved == spec.trackCount()) {
				pass(Topic.TRACKS, reserved + (reserved == 1 ? " track corridor" : " track corridors") + " reserved, as requested");
			} else {
				fail(Topic.TRACKS, reserved + " track corridors reserved; " + spec.trackCount() + " requested");
			}
		}

		private void faces() {
			final int before = checks.size();
			final List<Integer> through = new ArrayList<>();
			for (final TrackLayout.Track track : layout.tracks()) {
				face(track, true, track.platformLeft());
				face(track, false, track.platformRight());
				if (track.through()) {
					through.add(track.number());
				}
			}
			if (checks.size() > before) {
				return;
			}
			final StringBuilder message = new StringBuilder()
					.append(faces.size()).append(faces.size() == 1 ? " boarding face" : " boarding faces")
					.append(", each measured at ").append(spec.platformLength()).append(" blocks, as requested");
			if (!through.isEmpty()) {
				message.append("; ").append(through.size() == 1 ? "track " : "tracks ").append(join(through))
						.append(through.size() == 1 ? " runs" : " run").append(" through without a platform");
			}
			pass(Topic.FACES, message.toString());
		}

		/** Measures the edge cells facing one side of a track and records the face, or fails. */
		private void face(TrackLayout.Track track, boolean left, boolean wanted) {
			final int edgeU = left ? track.u() - StationGeometry.EDGE_OFFSET : track.u() + StationGeometry.EDGE_OFFSET;
			final LocalDir toward = left ? LocalDir.PLUS_U : LocalDir.MINUS_U;
			final String side = "track " + track.number() + "'s " + (left ? "left" : "right") + " side";
			final List<int[]> runs = new ArrayList<>();
			int start = Integer.MIN_VALUE;
			for (int v = layout.vMin(); v <= layout.vMax() + 1; v++) {
				final PlannedBlock block = v <= layout.vMax() ? at(edgeU, StationGeometry.PLATFORM_Y, v) : null;
				final boolean edge = block != null && block.role() == Role.PLATFORM_EDGE && block.front() == toward;
				if (edge && start == Integer.MIN_VALUE) {
					start = v;
				} else if (!edge && start != Integer.MIN_VALUE) {
					runs.add(new int[]{start, v - start});
					start = Integer.MIN_VALUE;
				}
			}
			if (!wanted) {
				if (!runs.isEmpty()) {
					fail(Topic.FACES, side + " has boarding edge cells where no platform was planned");
				}
				return;
			}
			if (runs.isEmpty()) {
				fail(Topic.FACES, side + " has no boarding face");
				return;
			}
			if (runs.size() > 1) {
				fail(Topic.FACES, side + " is broken into " + runs.size() + " pieces (first gap at v=" + (runs.get(0)[0] + runs.get(0)[1]) + ")");
				return;
			}
			final int[] run = runs.get(0);
			final TrackLayout.PlatformBand band = bandAt(edgeU);
			faces.add(new Face(track.number(), band == null ? 0 : band.number(), left, run[0], run[1]));
			if (run[1] != spec.platformLength()) {
				fail(Topic.FACES, side + " measures " + run[1] + " blocks of boarding edge; " + spec.platformLength() + " requested");
			}
		}

		private TrackLayout.PlatformBand bandAt(int u) {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (u >= band.uMin() && u <= band.uMax()) {
					return band;
				}
			}
			return null;
		}

		private void railNodes() {
			int present = 0;
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.RAIL_NODE) {
					present++;
				}
			}
			if (!spec.railNodes()) {
				if (present == 0) {
					pass(Topic.RAIL_NODES, "No rail nodes, as requested");
				} else {
					fail(Topic.RAIL_NODES, present + " rail nodes planned although none were requested");
				}
				return;
			}
			final int before = checks.size();
			for (final TrackLayout.Track track : layout.tracks()) {
				int first = 0;
				int last = layout.platformLength() - 1;
				for (final Face face : faces) {
					if (face.track() == track.number()) {
						first = face.vStart();
						last = face.vEnd();
						break;
					}
				}
				for (final int v : new int[]{first, last}) {
					if (!has(track.u(), StationGeometry.PLATFORM_Y, v, Role.RAIL_NODE)) {
						fail(Topic.RAIL_NODES, "track " + track.number() + " has no rail node at the platform end v=" + v);
					}
				}
			}
			if (checks.size() == before) {
				pass(Topic.RAIL_NODES, "Rail nodes at both platform ends of all " + layout.tracks().size() + (layout.tracks().size() == 1 ? " track" : " tracks"));
			}
		}

		private void roof() {
			final Set<Long> roofed = new HashSet<>();
			final boolean open = spec.roof() == RoofType.NONE;
			for (final PlannedBlock block : plan.blocks()) {
				final boolean shelter = open ? block.role().roofPart() : block.role().covers();
				if (shelter && block.y() > StationGeometry.WALK_Y) {
					roofed.add(key(block.u(), 0, block.v()));
				}
			}
			int floor = 0;
			int covered = 0;
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role().platformFloor()) {
					floor++;
					if (roofed.contains(key(block.u(), 0, block.v()))) {
						covered++;
					}
				}
			}
			final int percent = floor == 0 ? 0 : covered * 100 / floor;
			if (spec.roof() == RoofType.NONE) {
				if (covered == 0) {
					pass(Topic.ROOF, "Open platforms, no roof, as requested");
				} else {
					fail(Topic.ROOF, "A roof covers " + percent + "% of the platforms although none was requested");
				}
			} else if (covered == floor && floor > 0) {
				pass(Topic.ROOF, "The " + (spec.roof() == RoofType.HALL ? "hall roof" : "canopies") + " cover 100% of the platform area");
			} else {
				fail(Topic.ROOF, "The " + (spec.roof() == RoofType.HALL ? "hall roof" : "canopies") + " cover " + percent + "% of the platform area; all of it requested");
			}
		}

		/** True if a passenger can stand on top of this walking-surface cell (nothing in the cell above it). */
		private boolean standable(int u, int v) {
			return standableAt(u, StationGeometry.WALK_Y, v);
		}

		private boolean standableAt(int u, int walkY, int v) {
			final PlannedBlock block = at(u, walkY, v);
			return block == null || block.role().passable();
		}

		private void headroom() {
			int lowest = Integer.MAX_VALUE;
			int tooLow = 0;
			PlannedBlock example = null;
			for (final PlannedBlock block : plan.blocks()) {
				final Role role = block.role();
				if (block.y() != StationGeometry.PLATFORM_Y && block.y() != StationGeometry.UPPER_FLOOR_Y
						|| !(role.flatFloor() || role == Role.RAMP_LOWER || role == Role.RAMP_UPPER)
						|| !standableAt(block.u(), block.y() + 1, block.v())) {
					continue;
				}
				final int walkY = block.y() + 1;
				boolean underStairs = false;
				for (int y = walkY; y <= walkY + StationGeometry.STAIR_STEPS; y++) {
					final PlannedBlock above = at(block.u(), y, block.v());
					if (above != null && above.role() == Role.STAIR) {
						underStairs = true;
						break;
					}
				}
				if (underStairs) {
					continue;
				}
				for (int y = walkY + 1; y <= plan.maxY(); y++) {
					final PlannedBlock above = at(block.u(), y, block.v());
					if (above != null && above.role().overhead()) {
						final int clear = y - walkY;
						lowest = Math.min(lowest, clear);
						if (clear < StationGeometry.HEADROOM) {
							tooLow++;
							if (example == null) {
								example = above;
							}
						}
						break;
					}
				}
			}
			if (tooLow > 0) {
				fail(Topic.HEADROOM, tooLow + " walkable cells have less than " + StationGeometry.HEADROOM + " blocks of headroom, e.g. under "
						+ example.role() + " at u=" + example.u() + " y=" + example.y() + " v=" + example.v());
			} else if (lowest == Integer.MAX_VALUE) {
				pass(Topic.HEADROOM, "Open sky over every walkable platform cell");
			} else {
				pass(Topic.HEADROOM, "At least " + lowest + " blocks of headroom over every walkable platform cell");
			}
		}

		// --- access -------------------------------------------------------------------------------------------------------

		private enum Ground {
			/** Beside the station (u < 0 or u >= width), where passengers arrive. */
			APPROACH,
			/** Open ground inside the footprint or past its ends that is not a track. */
			GROUND,
			/** A track corridor column; the line runs on past both ends of the footprint. */
			TRACK,
			PLATFORM,
			BUILDING,
			RAMP_LOWER,
			RAMP_UPPER,
			BLOCKED
		}

		private int uMin() {
			return -1;
		}

		private int uMax() {
			return layout.width();
		}

		private int vMin() {
			return layout.vMin() - 1;
		}

		private int vMax() {
			return layout.vMax() + 1;
		}

		private boolean inGrid(int u, int v) {
			return u >= uMin() && u <= uMax() && v >= vMin() && v <= vMax();
		}

		private int index(int u, int v) {
			return (u - uMin()) * (vMax() - vMin() + 1) + (v - vMin());
		}

		/**
		 * A track's ground: its envelope columns, which run on one cell past both footprint ends of a through station; at a
		 * terminus the tracks stop at the buffers, so nothing past them is track.
		 */
		private boolean trackColumn(int u, int v) {
			for (final Box envelope : plan.trackEnvelopes()) {
				if (u >= envelope.uMin() && u <= envelope.uMax() && v >= envelope.vMin() - 1 && v <= envelope.vMax() + (layout.terminus() ? 0 : 1)) {
					return true;
				}
			}
			return false;
		}

		private Ground ground(int u, int v) {
			if (u < 0 || u >= layout.width()) {
				return Ground.APPROACH;
			}
			for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 2; y++) {
				final PlannedBlock over = at(u, y, v);
				if (over != null && over.role() == Role.STAIR) {
					return Ground.BLOCKED;
				}
			}
			final PlannedBlock floor = at(u, StationGeometry.PLATFORM_Y, v);
			if (floor != null && floor.role() != Role.RAIL_NODE) {
				if (!standable(u, v)) {
					return Ground.BLOCKED;
				}
				if (floor.role().platformFloor()) {
					return Ground.PLATFORM;
				}
				if (floor.role() == Role.FLOOR || floor.role() == Role.LIGHT_PANEL) {
					return Ground.BUILDING;
				}
				if (floor.role() == Role.RAMP_LOWER) {
					return Ground.RAMP_LOWER;
				}
				if (floor.role() == Role.RAMP_UPPER) {
					return Ground.RAMP_UPPER;
				}
				return Ground.BLOCKED;
			}
			final PlannedBlock atWalk = at(u, StationGeometry.WALK_Y, v);
			if (atWalk != null && !atWalk.role().passable()) {
				return Ground.BLOCKED;
			}
			return trackColumn(u, v) ? Ground.TRACK : Ground.GROUND;
		}

		private static boolean flat(Ground ground) {
			return ground == Ground.APPROACH || ground == Ground.GROUND || ground == Ground.TRACK;
		}

		private static boolean walkable(Ground ground) {
			return ground == Ground.PLATFORM || ground == Ground.BUILDING;
		}

		private static boolean ramp(Ground ground) {
			return ground == Ground.RAMP_LOWER || ground == Ground.RAMP_UPPER;
		}

		private static final int NO_MOVE = 0;
		private static final int LEVEL = 1;
		private static final int STEP = 2;

		/** Whether a passenger can move from (u, v) one cell in (du, dv), and how. Ramps are only climbed along their front. */
		private int move(Ground[] grid, int u, int v, int du, int dv) {
			final Ground a = grid[index(u, v)];
			final Ground b = grid[index(u + du, v + dv)];
			if (a == Ground.BLOCKED || b == Ground.BLOCKED) {
				return NO_MOVE;
			}
			if (flat(a) && flat(b) || walkable(a) && walkable(b)) {
				return LEVEL;
			}
			if (flat(a) && walkable(b) || walkable(a) && flat(b)) {
				return STEP;
			}
			if (ramp(a)) {
				return rampMove(a, at(u, StationGeometry.PLATFORM_Y, v).front(), b, u + du, v + dv, du, dv);
			}
			if (ramp(b)) {
				return rampMove(b, at(u + du, StationGeometry.PLATFORM_Y, v + dv).front(), a, u, v, -du, -dv);
			}
			return NO_MOVE;
		}

		/**
		 * A move between a ramp cell and its neighbour {@code other} in direction (du, dv) from the ramp. A ramp's front points to
		 * its low end: the lower half meets flat ground in front and the upper half behind; the upper half meets the lower half in
		 * front and the platform behind. Sideways a ramp only continues into the same half with the same front.
		 */
		private int rampMove(Ground ramp, LocalDir front, Ground other, int ou, int ov, int du, int dv) {
			final LocalDir direction = direction(du, dv);
			if (direction == front.opposite() || direction == front) {
				final boolean forward = direction == front;
				if (ramp == Ground.RAMP_LOWER) {
					return forward ? (flat(other) ? LEVEL : NO_MOVE) : (other == Ground.RAMP_UPPER && frontAt(ou, ov) == front ? LEVEL : NO_MOVE);
				}
				return forward ? (other == Ground.RAMP_LOWER && frontAt(ou, ov) == front ? LEVEL : NO_MOVE)
						: (walkable(other) ? LEVEL : NO_MOVE);
			}
			return other == ramp && frontAt(ou, ov) == front ? LEVEL : NO_MOVE;
		}

		private LocalDir frontAt(int u, int v) {
			final PlannedBlock block = at(u, StationGeometry.PLATFORM_Y, v);
			return block == null ? null : block.front();
		}

		private static LocalDir direction(int du, int dv) {
			if (du > 0) {
				return LocalDir.PLUS_U;
			}
			if (du < 0) {
				return LocalDir.MINUS_U;
			}
			return dv > 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
		}

		/** Upper-floor walkable cells (y=6): the slab or deck below must be a flat floor and the corridor free. */
		private Ground upperGround(int u, int v) {
			if (u < 0 || u >= layout.width()) {
				return Ground.BLOCKED;
			}
			final PlannedBlock floor = at(u, StationGeometry.UPPER_FLOOR_Y, v);
			if (floor == null || !(floor.role() == Role.FLOOR || floor.role() == Role.LIGHT_PANEL || floor.role() == Role.BRIDGE_DECK)) {
				return Ground.BLOCKED;
			}
			if (!standableAt(u, StationGeometry.UPPER_WALK_Y, v)) {
				return Ground.BLOCKED;
			}
			return Ground.BUILDING;
		}

		/** One stair flight the audit can climb: ground foot to upper top. Both ends must be walkable. */
		private record Flight(int footU, int footV, int topU, int topV) {
		}

		/** Every intact stair flight: all steps present with the flight's front, both landings walkable. */
		private List<Flight> flights(Ground[] ground, Ground[] upper) {
			final List<Flight> flights = new ArrayList<>();
			final StationLayout site = StationLayout.of(spec, layout);
			if (site.building()) {
				final int uS = StationLayout.streetWall();
				final int uP = site.platformWall(layout);
				boolean intact = true;
				for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
					final int v = site.innerStairV(y);
					for (int u = uP - 2; u <= uP - 1; u++) {
						final PlannedBlock step = at(u, y, v);
						if (step == null || step.role() != Role.STAIR || step.front() != site.innerStairFront()) {
							intact = false;
							break;
						}
					}
					if (!intact) {
						break;
					}
				}
				if (intact) {
					final int footV = site.innerStairV(1) + site.innerStairSide();
					final int topStepV = site.innerStairV(StationGeometry.STAIR_STEPS);
					for (int u = uP - 2; u <= uP - 1; u++) {
						final int topV = nearestUpper(upper, u, topStepV, site.innerStairSide());
						if (topV >= 0 && inGrid(u, footV) && inGrid(u, topV) && ground[index(u, footV)] != Ground.BLOCKED
								&& upper[index(u, topV)] != Ground.BLOCKED) {
							flights.add(new Flight(u, footV, u, topV));
						}
					}
				}
			}
			if (site.bridge()) {
				for (final TrackLayout.PlatformBand band : layout.platforms()) {
					if (!site.served().contains(band.number())) {
						continue;
					}
					for (final int u : StationLayout.stairColumns(band)) {
						boolean intact = true;
						for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
							final PlannedBlock step = at(u, y, site.stairV(y));
							if (step == null || step.role() != Role.STAIR || step.front() != site.stairFront()) {
								intact = false;
								break;
							}
						}
						if (intact && inGrid(u, site.landingV()) && inGrid(u, site.bridgeV())
								&& ground[index(u, site.landingV())] != Ground.BLOCKED && upper[index(u, site.bridgeV())] != Ground.BLOCKED) {
							flights.add(new Flight(u, site.landingV(), u, site.bridgeV()));
						}
					}
				}
			}
			return flights;
		}

		/** Nearest walkable upper cell from the top step towards the landing (at most 6 cells); -1 if none. */
		private int nearestUpper(Ground[] upper, int u, int v0, int dir) {
			for (int d = 1; d <= 6; d++) {
				final int v = v0 - dir * d;
				if (inGrid(u, v) && upper[index(u, v)] != Ground.BLOCKED) {
					return v;
				}
			}
			for (int d = 1; d <= 6; d++) {
				final int v = v0 + dir * d;
				if (inGrid(u, v) && upper[index(u, v)] != Ground.BLOCKED) {
					return v;
				}
			}
			return -1;
		}

		/** Cells reached from the approach, allowing one-block steps and walking across tracks only when asked. */
		private boolean[] reach(Ground[] grid, boolean steps, boolean tracks) {
			return reach(grid, null, List.of(), steps, tracks, false);
		}

		/** Ground and upper cells reached together, climbing intact stairs only when asked. */
		private boolean[] reach(Ground[] grid, Ground[] upper, List<Flight> flights, boolean steps, boolean tracks, boolean stairs) {
			final boolean[] seen = new boolean[grid.length];
			final boolean[] seenUp = upper == null ? null : new boolean[upper.length];
			final ArrayDeque<int[]> queue = new ArrayDeque<>();
			for (int u = uMin(); u <= uMax(); u++) {
				for (int v = vMin(); v <= vMax(); v++) {
					if (grid[index(u, v)] == Ground.APPROACH) {
						seen[index(u, v)] = true;
						queue.add(new int[]{u, 1, v});
					}
				}
			}
			while (!queue.isEmpty()) {
				final int[] cell = queue.poll();
				final int u = cell[0];
				final int y = cell[1];
				final int v = cell[2];
				if (y == 1) {
					for (final int[] side : SIDES) {
						final int nu = u + side[0];
						final int nv = v + side[1];
						if (!inGrid(nu, nv) || seen[index(nu, nv)]) {
							continue;
						}
						final int move = move(grid, u, v, side[0], side[1]);
						if (move == NO_MOVE || move == STEP && !steps || grid[index(nu, nv)] == Ground.TRACK && !tracks) {
							continue;
						}
						seen[index(nu, nv)] = true;
						queue.add(new int[]{nu, 1, nv});
					}
					if (stairs && upper != null) {
						for (final Flight flight : flights) {
							if (flight.footU() == u && flight.footV() == v && upper[index(flight.topU(), flight.topV())] != Ground.BLOCKED
									&& !seenUp[index(flight.topU(), flight.topV())]) {
								seenUp[index(flight.topU(), flight.topV())] = true;
								queue.add(new int[]{flight.topU(), 6, flight.topV()});
							}
						}
					}
				} else {
					for (final int[] side : SIDES) {
						final int nu = u + side[0];
						final int nv = v + side[1];
						if (!inGrid(nu, nv) || seenUp[index(nu, nv)]) {
							continue;
						}
						if (upper[index(nu, nv)] == Ground.BLOCKED || upper[index(u, v)] == Ground.BLOCKED) {
							continue;
						}
						seenUp[index(nu, nv)] = true;
						queue.add(new int[]{nu, 6, nv});
					}
					if (stairs) {
						for (final Flight flight : flights) {
							if (flight.topU() == u && flight.topV() == v && !seen[index(flight.footU(), flight.footV())]
									&& grid[index(flight.footU(), flight.footV())] != Ground.BLOCKED) {
								seen[index(flight.footU(), flight.footV())] = true;
								queue.add(new int[]{flight.footU(), 1, flight.footV()});
							}
						}
					}
				}
			}
			if (upper == null) {
				return seen;
			}
			// fold the upper reach back so callers grading ground cells see stair-fed ground as reached
			return seen;
		}

		private void access() {
			final Ground[] grid = new Ground[(uMax() - uMin() + 1) * (vMax() - vMin() + 1)];
			for (int u = uMin(); u <= uMax(); u++) {
				for (int v = vMin(); v <= vMax(); v++) {
					grid[index(u, v)] = ground(u, v);
				}
			}
			final StationLayout site = StationLayout.of(spec, layout);
			final boolean hasStairs = site.building() || site.bridge();
			final Ground[] upper;
			final List<Flight> flights;
			if (hasStairs) {
				upper = new Ground[grid.length];
				for (int u = uMin(); u <= uMax(); u++) {
					for (int v = vMin(); v <= vMax(); v++) {
						upper[index(u, v)] = upperGround(u, v);
					}
				}
				flights = flights(grid, upper);
			} else {
				upper = null;
				flights = List.of();
			}
			final boolean[] stepFree = hasStairs ? reach(grid, upper, flights, false, false, false) : reach(grid, false, false);
			final boolean[] viaStairs = hasStairs ? stairFed(grid, upper, flights) : new boolean[grid.length];
			final boolean[] stepped = hasStairs ? reach(grid, upper, flights, true, false, true) : reach(grid, true, false);
			final boolean[] atGrade = hasStairs ? reach(grid, upper, flights, true, true, true) : reach(grid, true, true);
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				// a platform is only as accessible as its worst boarding cell: every place along the edge must be reachable (a dead
				// corner behind a column does not matter)
				int edges = 0;
				Access worst = Access.STEP_FREE;
				int atWorst = 0;
				String firstWorst = "";
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					for (int v = 0; v < layout.platformLength(); v++) {
						if (grid[index(u, v)] != Ground.PLATFORM || !has(u, StationGeometry.PLATFORM_Y, v, Role.PLATFORM_EDGE)) {
							continue;
						}
						edges++;
						final int i = index(u, v);
						final Access cell;
						if (stepFree[i]) {
							cell = Access.STEP_FREE;
						} else if (hasStairs && viaStairs[i]) {
							cell = Access.STAIRS;
						} else if (stepped[i]) {
							cell = Access.STEP;
						} else if (atGrade[i]) {
							cell = Access.AT_GRADE;
						} else {
							cell = Access.UNREACHABLE;
						}
						if (cell.ordinal() > worst.ordinal()) {
							worst = cell;
							atWorst = 0;
							firstWorst = " (e.g. u=" + u + " v=" + v + ")";
						}
						if (cell == worst) {
							atWorst++;
						}
					}
				}
				access.add(edges == 0 ? Access.UNREACHABLE : worst);
				final String name = "Platform " + band.number();
				if (edges == 0) {
					fail(Topic.ACCESS, name + " has no free boarding edge to stand on");
					continue;
				}
				final String which = atWorst == edges ? name + " is"
						: atWorst + " of " + edges + " boarding cells on platform " + band.number() + firstWorst + " are";
				switch (worst) {
					case STEP_FREE -> pass(Topic.ACCESS, name + " is reached step-free from beside the station");
					case STAIRS -> {
						if (spec.stepFree()) {
							// step-free is the default, so this is not always something the description asked for
							warn(Topic.ACCESS, which + " only reached by stairs, so not step-free (lifts are not built yet)");
						} else {
							pass(Topic.ACCESS, which + " reached by stairs (no ramps, as requested)");
						}
					}
					case STEP -> {
						if (spec.stepFree()) {
							fail(Topic.ACCESS, which + " only reached by a one-block step although step-free access was requested");
						} else {
							pass(Topic.ACCESS, which + " reached by a one-block step (no ramps, as requested)");
						}
					}
					case AT_GRADE -> warn(Topic.ACCESS, which + " only reached by walking across a track; it needs a footbridge or underpass");
					case UNREACHABLE -> fail(Topic.ACCESS, which + " cut off and cannot be reached on foot");
				}
			}
		}

		/** Ground cells reached step-free via stairs (but not without them): the STAIRS grade. */
		private boolean[] stairFed(Ground[] grid, Ground[] upper, List<Flight> flights) {
			final boolean[] plain = reach(grid, upper, flights, false, false, false);
			final boolean[] withStairs = reach(grid, upper, flights, false, false, true);
			final boolean[] fed = new boolean[grid.length];
			for (int i = 0; i < fed.length; i++) {
				fed[i] = withStairs[i] && !plain[i];
			}
			// stair-fed ground spreads along the ground without steps or tracks
			final ArrayDeque<Integer> queue = new ArrayDeque<>();
			for (int i = 0; i < fed.length; i++) {
				if (fed[i]) {
					queue.add(i);
				}
			}
			while (!queue.isEmpty()) {
				final int i = queue.poll();
				final int u = uMin() + i / (vMax() - vMin() + 1);
				final int v = vMin() + i % (vMax() - vMin() + 1);
				for (final int[] side : SIDES) {
					final int nu = u + side[0];
					final int nv = v + side[1];
					if (!inGrid(nu, nv)) {
						continue;
					}
					final int ni = index(nu, nv);
					if (fed[ni]) {
						continue;
					}
					final int move = move(grid, u, v, side[0], side[1]);
					if (move != LEVEL || grid[ni] == Ground.TRACK) {
						continue;
					}
					fed[ni] = true;
					queue.add(ni);
				}
			}
			return fed;
		}

		// --- the rest -------------------------------------------------------------------------------------------------------

		private void nameSigns() {
			final Map<Integer, Integer> perPlatform = new HashMap<>();
			int wrongText = 0;
			int total = 0;
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() != Role.NAME_SIGN) {
					continue;
				}
				total++;
				if (!block.text().equals(spec.name())) {
					wrongText++;
				}
				final TrackLayout.PlatformBand band = bandAt(block.u());
				if (band != null) {
					perPlatform.merge(band.number(), 1, Integer::sum);
				}
			}
			if (spec.name().isEmpty()) {
				if (total == 0) {
					pass(Topic.NAME_SIGNS, "No name given, so no name signs");
				} else {
					fail(Topic.NAME_SIGNS, total + " name signs planned without a name");
				}
				return;
			}
			if (wrongText > 0) {
				fail(Topic.NAME_SIGNS, wrongText + " name signs do not carry the name \"" + spec.name() + "\"");
				return;
			}
			final List<Integer> missing = new ArrayList<>();
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (!perPlatform.containsKey(band.number())) {
					missing.add(band.number());
				}
			}
			if (missing.isEmpty()) {
				pass(Topic.NAME_SIGNS, "\"" + spec.name() + "\" signed on every platform");
			} else {
				warn(Topic.NAME_SIGNS, (missing.size() == 1 ? "Platform " : "Platforms ") + join(missing) + " had no free place for a name sign");
			}
		}

		/** Every block must be joined, through faces or edges, to a block standing on the station's support layer. */
		private void grounded() {
			final Set<Long> support = new HashSet<>();
			for (final Box area : plan.supportAreas()) {
				for (int u = area.uMin(); u <= area.uMax(); u++) {
					for (int v = area.vMin(); v <= area.vMax(); v++) {
						support.add(key(u, 0, v));
					}
				}
			}
			final Set<Long> joined = new HashSet<>();
			final ArrayDeque<PlannedBlock> queue = new ArrayDeque<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.y() == StationGeometry.PLATFORM_Y && support.contains(key(block.u(), 0, block.v()))
						&& joined.add(key(block.u(), block.y(), block.v()))) {
					queue.add(block);
				}
			}
			while (!queue.isEmpty()) {
				final PlannedBlock block = queue.poll();
				for (int du = -1; du <= 1; du++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dv = -1; dv <= 1; dv++) {
							final int moved = Math.abs(du) + Math.abs(dy) + Math.abs(dv);
							if (moved == 0 || moved == 3) {
								continue;
							}
							final PlannedBlock next = at(block.u() + du, block.y() + dy, block.v() + dv);
							if (next != null && joined.add(key(next.u(), next.y(), next.v()))) {
								queue.add(next);
							}
						}
					}
				}
			}
			int floating = 0;
			PlannedBlock example = null;
			for (final PlannedBlock block : plan.blocks()) {
				if (!joined.contains(key(block.u(), block.y(), block.v()))) {
					floating++;
					if (example == null) {
						example = block;
					}
				}
			}
			if (floating == 0) {
				pass(Topic.GROUNDED, "No floating parts: all " + plan.blocks().size() + " blocks are joined to the ground");
			} else {
				fail(Topic.GROUNDED, floating + (floating == 1 ? " block is" : " blocks are") + " not joined to the ground, e.g. " + example.role()
						+ " at u=" + example.u() + " y=" + example.y() + " v=" + example.v());
			}
		}

		private void lighting() {
			if (!spec.hasBuilding()) {
				pass(Topic.LIGHTING, "No indoor areas, no lighting needed");
				return;
			}
			final List<int[]> lights = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() == Role.LIGHT_PANEL || block.role() == Role.BUILDING_ROOF && block.blockId().contains("light")) {
					lights.add(new int[]{block.u(), block.y(), block.v()});
				}
				if (block.role() == Role.BRIDGE_DECK && block.blockId().contains("light")) {
					lights.add(new int[]{block.u(), block.y(), block.v()});
				}
			}
			if (lights.isEmpty()) {
				fail(Topic.LIGHTING, "The station building has no lights");
				return;
			}
			int dark = 0;
			PlannedBlock example = null;
			for (final PlannedBlock block : plan.blocks()) {
				if (block.role() != Role.FLOOR || block.y() != StationGeometry.PLATFORM_Y) {
					continue;
				}
				if (!layout.indoor(block.u(), block.v())) {
					continue;
				}
				if (!standableAt(block.u(), block.y() + 1, block.v())) {
					continue;
				}
				boolean lit = false;
				for (final int[] light : lights) {
					if (Math.abs(light[0] - block.u()) + Math.abs(light[2] - block.v()) <= 4) {
						lit = true;
						break;
					}
				}
				if (!lit) {
					dark++;
					if (example == null) {
						example = block;
					}
				}
			}
			if (dark > 0) {
				fail(Topic.LIGHTING, dark + " indoor cells are too far from any light, e.g. u=" + example.u() + " v=" + example.v());
			} else {
				pass(Topic.LIGHTING, "Every indoor cell is near a light (" + lights.size() + " lights)");
			}
		}

		private void furnishing() {
			if (!spec.hasBuilding()) {
				pass(Topic.FURNISHING, "No station building, no interior fitting needed");
				return;
			}
			boolean window = false;
			boolean gate = false;
			boolean board = false;
			boolean clock = false;
			boolean bench = false;
			for (final PlannedBlock block : plan.blocks()) {
				if (block.blockId().contains("booth_window")) {
					window = true;
				}
				if (block.role() == Role.GATE) {
					gate = true;
				}
				if (block.blockId().contains("concourse_board")) {
					board = true;
				}
				if (block.blockId().contains("clock")) {
					clock = true;
				}
				if (block.role() == Role.BENCH && layout.indoor(block.u(), block.v())) {
					bench = true;
				}
			}
			final List<String> missing = new ArrayList<>();
			if (!window) {
				missing.add("ticket window");
			}
			if (!gate) {
				missing.add("fare gates");
			}
			if (!board) {
				missing.add("departure board");
			}
			if (!clock) {
				missing.add("clock");
			}
			if (!bench && spec.furniture()) {
				missing.add("benches");
			}
			if (missing.isEmpty()) {
				pass(Topic.FURNISHING, "Ticket window, fare gates, departure boards, clock and benches fitted");
			} else {
				fail(Topic.FURNISHING, "The station building is missing " + String.join(", ", missing));
			}
		}

		private Result size() {
			int top = 0;
			for (final PlannedBlock block : plan.blocks()) {
				top = Math.max(top, block.y());
			}
			final int width = layout.width();
			final int length = layout.vMax() - layout.vMin() + 1;
			final int height = top + 1;
			pass(Topic.SIZE, width + " wide x " + length + " long x " + height + " high");
			return new Result(checks, faces, access, width, length, height);
		}

		private static String join(List<Integer> numbers) {
			final StringBuilder text = new StringBuilder();
			for (int i = 0; i < numbers.size(); i++) {
				if (i > 0) {
					text.append(i == numbers.size() - 1 ? " and " : ", ");
				}
				text.append(numbers.get(i));
			}
			return text.toString();
		}
	}
}
