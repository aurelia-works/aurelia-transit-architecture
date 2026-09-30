package com.aureliatransit.architecture.interactive;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure seat geometry. Seat positions are authored in model pixels for a north-facing block and rotated clockwise
 * (viewed from above) by {@code quarterTurns}, exactly like the models and shapes.
 */
public final class SeatGeometry {

	/**
	 * How far below the seat surface the seat entity sits: a riding player's hips hang about this far above the vehicle position.
	 */
	public static final double SEAT_DROP = 0.22;

	private SeatGeometry() {
	}

	/**
	 * Rotates a horizontal offset from the block centre by clockwise quarter turns: north (0,-1) becomes east (1,0).
	 */
	public static double[] rotate(double x, double z, int quarterTurns) {
		double rx = x;
		double rz = z;
		for (int i = 0; i < (quarterTurns & 3); i++) {
			final double nx = -rz;
			final double nz = rx;
			rx = nx;
			rz = nz;
		}
		return new double[]{rx, rz};
	}

	/**
	 * World offset from the block centre (in blocks) of a seat slot.
	 *
	 * @param slot      slot index
	 * @param slots     number of slots in the block (1 centres the seat, 2 puts one in each half)
	 * @param seatZPx   model-space z of the seat centre
	 */
	public static double[] slotOffset(int slot, int slots, double seatZPx, int quarterTurns) {
		final double localX = slots <= 1 ? 8 : (slot == 0 ? 4 : 12);
		return rotate((localX - 8) / 16, (seatZPx - 8) / 16, quarterTurns);
	}

	/**
	 * Which slot a click landed in; {@code relX/relZ} are the hit position relative to the block centre, in blocks.
	 */
	public static int slotForHit(double relX, double relZ, int slots, int quarterTurns) {
		if (slots <= 1) {
			return 0;
		}
		// Undo the block rotation to get back to model space.
		final double[] local = rotate(relX, relZ, 4 - (quarterTurns & 3));
		return local[0] < 0 ? 0 : 1;
	}

	/**
	 * Slot indices in the order they should be tried: the clicked one first.
	 */
	public static int[] slotOrder(int preferred, int slots) {
		final int[] order = new int[slots];
		order[0] = preferred;
		int next = 1;
		for (int i = 0; i < slots; i++) {
			if (i != preferred) {
				order[next++] = i;
			}
		}
		return order;
	}

	/**
	 * Candidate dismount offsets (dx, dz in blocks) around a seat whose occupant looks along {@code yawDegrees}
	 * (Minecraft convention: 0 = south, 90 = west): in front first, then the sides, then behind.
	 */
	public static List<double[]> dismountOffsets(float yawDegrees) {
		final double yaw = Math.toRadians(yawDegrees);
		final double fx = -Math.sin(yaw);
		final double fz = Math.cos(yaw);
		final double rx = -Math.cos(yaw);
		final double rz = -Math.sin(yaw);
		final double[][] combos = {{1, 0}, {1, 1}, {1, -1}, {0, 1}, {0, -1}, {-1, 0}, {-1, 1}, {-1, -1}};
		final List<double[]> out = new ArrayList<>(combos.length);
		for (final double[] c : combos) {
			out.add(new double[]{Math.round(fx * c[0] + rx * c[1]), Math.round(fz * c[0] + rz * c[1])});
		}
		return out;
	}
}
