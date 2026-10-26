package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The volume a deep station digs out of the ground, as a set of cells in local coordinates. Tunnels, shafts, passages and boxes
 * are added as shapes; what is left of each is the union, so two tunnels that meet are one void. The lining (the walls, floor and
 * roof of the void) is whatever touches the void and is not in it ({@link #lining}): no shape needs to know where it meets
 * another.
 * Pure logic, no Minecraft classes.
 */
final class Carve {

	private final int uMin;
	private final int uMax;
	private final int yMin;
	private final int yMax;
	private final int vMin;
	private final int vMax;
	private final int nu;
	private final int ny;
	private final int nv;
	private final boolean[] cells;
	private int count;

	/** A grid for the cells in the box; shapes are cut off at its edges. */
	Carve(Box bounds) {
		this.uMin = bounds.uMin();
		this.uMax = bounds.uMax();
		this.yMin = bounds.yMin();
		this.yMax = bounds.yMax();
		this.vMin = bounds.vMin();
		this.vMax = bounds.vMax();
		this.nu = uMax - uMin + 1;
		this.ny = yMax - yMin + 1;
		this.nv = vMax - vMin + 1;
		this.cells = new boolean[nu * ny * nv];
	}

	private int index(int u, int y, int v) {
		return ((u - uMin) * ny + (y - yMin)) * nv + (v - vMin);
	}

	private boolean in(int u, int y, int v) {
		return u >= uMin && u <= uMax && y >= yMin && y <= yMax && v >= vMin && v <= vMax;
	}

	/** True when the cell is in the void. */
	boolean has(int u, int y, int v) {
		return in(u, y, v) && cells[index(u, y, v)];
	}

	/** Adds one cell. */
	void add(int u, int y, int v) {
		if (in(u, y, v) && !cells[index(u, y, v)]) {
			cells[index(u, y, v)] = true;
			count++;
		}
	}

	/** Takes one cell out of the void (a wall left standing). */
	void remove(int u, int y, int v) {
		if (in(u, y, v) && cells[index(u, y, v)]) {
			cells[index(u, y, v)] = false;
			count--;
		}
	}

	/** Cells in the void. */
	int count() {
		return count;
	}

	/** Adds every cell of the box. */
	void box(int u0, int y0, int v0, int u1, int y1, int v1) {
		for (int u = Math.max(u0, uMin); u <= Math.min(u1, uMax); u++) {
			for (int y = Math.max(y0, yMin); y <= Math.min(y1, yMax); y++) {
				for (int v = Math.max(v0, vMin); v <= Math.min(v1, vMax); v++) {
					add(u, y, v);
				}
			}
		}
	}

	/** Removes every cell of the box. */
	void removeBox(int u0, int y0, int v0, int u1, int y1, int v1) {
		for (int u = Math.max(u0, uMin); u <= Math.min(u1, uMax); u++) {
			for (int y = Math.max(y0, yMin); y <= Math.min(y1, yMax); y++) {
				for (int v = Math.max(v0, vMin); v <= Math.min(v1, vMax); v++) {
					remove(u, y, v);
				}
			}
		}
	}

	/**
	 * A round tunnel along v: the cells within {@code radius} of the axis (u = {@code centreU}, y = {@code centreY}), from
	 * {@code yFloor} up, for v from {@code vFrom} to {@code vTo}; only where {@code uOk} accepts the column.
	 */
	void tube(double centreU, double centreY, double radius, int yFloor, int vFrom, int vTo, IntPredicate uOk) {
		final int lo = (int) Math.floor(centreU - radius);
		final int hi = (int) Math.ceil(centreU + radius);
		for (int u = Math.max(lo, uMin); u <= Math.min(hi, uMax); u++) {
			if (!uOk.test(u)) {
				continue;
			}
			final double du = u - centreU;
			for (int y = Math.max(yFloor, yMin); y <= yMax; y++) {
				final double dy = y - centreY;
				if (du * du + dy * dy > radius * radius) {
					continue;
				}
				for (int v = Math.max(vFrom, vMin); v <= Math.min(vTo, vMax); v++) {
					add(u, y, v);
				}
			}
		}
	}

	/** Whether the void is open or walled: the lining cells, each with the way it faces. */
	record Lining(int u, int y, int v, boolean floor, boolean ceiling) {
	}

	/**
	 * The lining: every cell outside the void with a face on it. {@code floor} is set where the void is above it (something to
	 * walk on), {@code ceiling} where the void is below it and nothing is above (a roof). The others are walls.
	 */
	List<Lining> lining() {
		final List<Lining> out = new ArrayList<>();
		final boolean[] seen = new boolean[cells.length];
		for (int u = uMin; u <= uMax; u++) {
			for (int y = yMin; y <= yMax; y++) {
				for (int v = vMin; v <= vMax; v++) {
					if (!cells[index(u, y, v)]) {
						continue;
					}
					edge(out, seen, u - 1, y, v);
					edge(out, seen, u + 1, y, v);
					edge(out, seen, u, y, v - 1);
					edge(out, seen, u, y, v + 1);
					edge(out, seen, u, y - 1, v);
					edge(out, seen, u, y + 1, v);
				}
			}
		}
		return out;
	}

	private void edge(List<Lining> out, boolean[] seen, int u, int y, int v) {
		if (!in(u, y, v)) {
			return;
		}
		final int i = index(u, y, v);
		if (cells[i] || seen[i]) {
			return;
		}
		seen[i] = true;
		// a cell with a void above it is a floor whatever else touches it; one with a void below and none above is a roof
		final boolean floor = has(u, y + 1, v);
		out.add(new Lining(u, y, v, floor, !floor && has(u, y - 1, v)));
	}

	/** The void as boxes, one per run of cells along v: what the site survey clears. */
	List<Box> runs() {
		final List<Box> boxes = new ArrayList<>();
		for (int u = uMin; u <= uMax; u++) {
			for (int y = yMin; y <= yMax; y++) {
				int start = Integer.MIN_VALUE;
				for (int v = vMin; v <= vMax + 1; v++) {
					final boolean on = v <= vMax && cells[index(u, y, v)];
					if (on && start == Integer.MIN_VALUE) {
						start = v;
					} else if (!on && start != Integer.MIN_VALUE) {
						boxes.add(new Box(u, y, start, u, y, v - 1));
						start = Integer.MIN_VALUE;
					}
				}
			}
		}
		return boxes;
	}
}
