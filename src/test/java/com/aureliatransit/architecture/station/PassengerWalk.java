package com.aureliatransit.architecture.station;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks a plan the way a passenger would: from the ground beside the station, on floors, up and down stairs, escalators and
 * ramps (never more than one up or down a step elsewhere), through fare gates, and in lifts between their landings. What it
 * reaches shows a lift or a stair that leads nowhere, or a platform nobody can get to. Rails between the platforms are not
 * walkable, so nobody crosses a track; nor is the ground past an end the rails run off (only a terminus's head end is street),
 * so nobody walks round the platforms' ends.
 */
final class PassengerWalk {

	private final Map<Long, PlannedBlock> at = new HashMap<>();
	private int uLo = Integer.MAX_VALUE;
	private int uHi = Integer.MIN_VALUE;
	private int vLo = Integer.MAX_VALUE;
	private int vHi = Integer.MIN_VALUE;
	/** Whether the ground past the low and the high end is street: no rail ends near that end. */
	private boolean streetLo = true;
	private boolean streetHi = true;
	/** Every cell a passenger's feet reach. */
	final Set<Long> reached = new HashSet<>();
	/** Each lift: its landings (the cell in front of the bottom of each door), by level. */
	final List<List<int[]>> lifts = new ArrayList<>();
	/** Each stair, escalator or ramp, as its connected cells. */
	final List<List<int[]>> flights = new ArrayList<>();

	PassengerWalk(StationPlan plan) {
		for (final PlannedBlock block : plan.blocks()) {
			at.put(key(block.u(), block.y(), block.v()), block);
			uLo = Math.min(uLo, block.u());
			uHi = Math.max(uHi, block.u());
			vLo = Math.min(vLo, block.v());
			vHi = Math.max(vHi, block.v());
		}
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.RAIL_NODE || block.role() == Role.TRACK_BED) {
				streetLo &= block.v() > vLo + 2;
				streetHi &= block.v() < vHi - 2;
			}
		}
		findLifts(plan);
		findFlights(plan);
		walk();
	}

	static long key(int u, int y, int v) {
		return ((long) (u + 4096) << 40) | ((long) (y + 4096) << 20) | (v + 4096);
	}

	boolean reached(int u, int y, int v) {
		return reached.contains(key(u, y, v));
	}

	private PlannedBlock block(int u, int y, int v) {
		return at.get(key(u, y, v));
	}

	/**
	 * Air, a fare gate, a hanging chain, a sign overhead, or an escalator's upper half (MTR's escalator is two blocks high: you
	 * stand on the step and between the balustrades of the block above it): nothing a passenger bumps into.
	 */
	private boolean open(int u, int y, int v) {
		final PlannedBlock block = block(u, y, v);
		if (block == null) {
			// the ground outside the station
			return !(y <= 0 && outside(u, v));
		}
		return block.role().passable() || block.blockId().equals("minecraft:chain") || block.blockId().equals(StylePalette.MTR_ESCALATOR_SIDE)
				|| block.role() == Role.NAME_SIGN && y >= 3;
	}

	private static boolean sloped(PlannedBlock block) {
		return block != null && (block.role() == Role.STAIR || block.role() == Role.ESCALATOR || block.role() == Role.RAMP_LOWER
				|| block.role() == Role.RAMP_UPPER);
	}

	private boolean floor(int u, int y, int v) {
		final PlannedBlock block = block(u, y, v);
		if (block == null) {
			// beside the station, or past an end that is street, the ground is walkable
			return y == 0 && outside(u, v) && (v >= vLo || streetLo) && (v <= vHi || streetHi);
		}
		return block.role().flatFloor() || sloped(block);
	}

	private boolean outside(int u, int v) {
		return u < uLo || u > uHi || v < vLo || v > vHi;
	}

	private boolean stand(int u, int y, int v) {
		return floor(u, y - 1, v) && open(u, y, v) && open(u, y + 1, v);
	}

	private void findLifts(StationPlan plan) {
		// lift blocks in clusters (a shaft and its doors), each door's bottom gives a landing outside the shaft
		final List<List<int[]>> clusters = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() != Role.LIFT) {
				continue;
			}
			List<int[]> found = null;
			for (final List<int[]> cluster : clusters) {
				for (final int[] c : cluster) {
					if (Math.abs(c[0] - block.u()) <= 2 && Math.abs(c[2] - block.v()) <= 2) {
						found = cluster;
						break;
					}
				}
				if (found != null) {
					break;
				}
			}
			if (found == null) {
				found = new ArrayList<>();
				clusters.add(found);
			}
			found.add(new int[]{block.u(), block.y(), block.v()});
		}
		for (final List<int[]> cluster : clusters) {
			final List<int[]> landings = new ArrayList<>();
			for (final int[] d : cluster) {
				final PlannedBlock door = block(d[0], d[1], d[2]);
				final PlannedBlock below = block(d[0], d[1] - 1, d[2]);
				if (!door.blockId().contains("lift_door") || below != null && below.blockId().contains("lift_door")) {
					continue;
				}
				for (final int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					final PlannedBlock next = block(d[0] + n[0], d[1], d[2] + n[1]);
					if (next == null || next.role() != Role.LIFT) {
						landings.add(new int[]{d[0] + n[0], d[1], d[2] + n[1]});
					}
				}
			}
			lifts.add(landings);
		}
	}

	private void findFlights(StationPlan plan) {
		final Set<Long> done = new HashSet<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (!sloped(block) || !done.add(key(block.u(), block.y(), block.v()))) {
				continue;
			}
			final List<int[]> cells = new ArrayList<>();
			final ArrayDeque<int[]> queue = new ArrayDeque<>();
			queue.add(new int[]{block.u(), block.y(), block.v()});
			while (!queue.isEmpty()) {
				final int[] c = queue.poll();
				cells.add(c);
				for (int du = -1; du <= 1; du++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dv = -1; dv <= 1; dv++) {
							if (sloped(block(c[0] + du, c[1] + dy, c[2] + dv)) && done.add(key(c[0] + du, c[1] + dy, c[2] + dv))) {
								queue.add(new int[]{c[0] + du, c[1] + dy, c[2] + dv});
							}
						}
					}
				}
			}
			flights.add(cells);
		}
	}

	private void walk() {
		final Map<Long, Integer> landingLift = new HashMap<>();
		for (int i = 0; i < lifts.size(); i++) {
			for (final int[] l : lifts.get(i)) {
				landingLift.put(key(l[0], l[1], l[2]), i);
			}
		}
		final boolean[] ridden = new boolean[lifts.size()];
		final ArrayDeque<int[]> queue = new ArrayDeque<>();
		// in from the ground all round the station, wherever it is walkable
		for (int u = uLo - 1; u <= uHi + 1; u++) {
			for (int v = vLo - 1; v <= vHi + 1; v++) {
				if (outside(u, v) && (u == uLo - 1 || u == uHi + 1 || v == vLo - 1 || v == vHi + 1) && stand(u, 1, v) && reached.add(key(u, 1, v))) {
					queue.add(new int[]{u, 1, v});
				}
			}
		}
		while (!queue.isEmpty()) {
			final int[] p = queue.poll();
			final Integer lift = landingLift.get(key(p[0], p[1], p[2]));
			if (lift != null && !ridden[lift]) {
				ridden[lift] = true;
				for (final int[] l : lifts.get(lift)) {
					if (stand(l[0], l[1], l[2]) && reached.add(key(l[0], l[1], l[2]))) {
						queue.add(l);
					}
				}
			}
			for (final int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				final int u = p[0] + n[0];
				final int v = p[2] + n[1];
				if (u < uLo - 1 || u > uHi + 1 || v < vLo - 1 || v > vHi + 1) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					final int y = p[1] + dy;
					if (!stand(u, y, v)) {
						continue;
					}
					// up only by a step, an escalator or a ramp, with headroom to step up; down a step with room to step down
					if (dy == 1 && !(sloped(block(u, y - 1, v)) || sloped(block(p[0], p[1] - 1, p[2]))) || dy == 1 && !open(p[0], p[1] + 2, p[2])
							|| dy == -1 && !open(u, p[1] + 1, v)) {
						continue;
					}
					if (reached.add(key(u, y, v))) {
						queue.add(new int[]{u, y, v});
					}
				}
			}
		}
	}

	/** Each lift whose landings are reached at fewer than two levels: one that goes nowhere. */
	List<String> liftsToNowhere() {
		final List<String> bad = new ArrayList<>();
		for (final List<int[]> landings : lifts) {
			final Set<Integer> levels = new HashSet<>();
			for (final int[] l : landings) {
				if (reached(l[0], l[1], l[2])) {
					levels.add(l[1]);
				}
			}
			if (levels.size() < 2) {
				final int[] l = landings.isEmpty() ? new int[3] : landings.get(0);
				bad.add("lift by " + l[0] + "," + l[2] + " reached at levels " + levels);
			}
		}
		return bad;
	}

	/**
	 * Each stair, escalator or ramp a passenger does not walk on near both its foot and its top (within a level of its lowest and
	 * highest cell: an escalator's ends are its balustrades).
	 */
	List<String> flightsToNowhere() {
		final List<String> bad = new ArrayList<>();
		for (final List<int[]> cells : flights) {
			int lo = Integer.MAX_VALUE;
			int hi = Integer.MIN_VALUE;
			for (final int[] c : cells) {
				lo = Math.min(lo, c[1]);
				hi = Math.max(hi, c[1]);
			}
			boolean foot = false;
			boolean top = false;
			for (final int[] c : cells) {
				final boolean on = reached(c[0], c[1] + 1, c[2]);
				foot |= on && c[1] <= lo + 1;
				top |= on && c[1] >= hi - 1;
			}
			if (!foot || !top) {
				final int[] c = cells.get(0);
				bad.add("flight by " + c[0] + "," + c[2] + " y " + lo + ".." + hi + (foot ? "" : ", foot not reached") + (top ? "" : ", top not reached"));
			}
		}
		return bad;
	}

	/** Cells of a platform's walking surface that nobody reaches, by platform number. */
	Map<Integer, Integer> unreachedPlatform(StationPlan plan) {
		final Map<Integer, Integer> missed = new HashMap<>();
		for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				for (int v = band.vFrom(); v <= band.vTo(); v++) {
					if (stand(u, StationGeometry.WALK_Y, v) && !reached(u, StationGeometry.WALK_Y, v)) {
						missed.merge(band.number(), 1, Integer::sum);
					}
				}
			}
		}
		return missed;
	}
}
