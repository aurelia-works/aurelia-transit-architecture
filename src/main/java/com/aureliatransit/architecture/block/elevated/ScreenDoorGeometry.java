package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.CurveKind;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.ScreenCurveKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Boxes (north-facing model pixels {x1, y1, z1, x2, y2, z2}) of a platform screen door piece (1.4). The screen stands on
 * the platform just inside its edge: the straight piece along z = 0 (track to the north, like the straight platform
 * edge), the curved pieces along the edge line x = {@link CurveKind#reach} of the edge piece they stand on, as
 * {@link #STRIPS} strips 2 px deep. Shared by the collision shape and, by the same formula, the generated model
 * (tools/assets_screens.py). Pure.
 */
public final class ScreenDoorGeometry {

	public static final int STRIPS = 8;
	public static final double STRAIGHT_THICKNESS = 1.5;
	/** Thicker than one step in x, so 45 degree strips overlap instead of touching at corners. */
	public static final double CURVE_THICKNESS = 2.5;
	public static final double HEADER_BOTTOM = 13;
	public static final double HEIGHT = 16;

	private ScreenDoorGeometry() {
	}

	/**
	 * @param doorway an open doorway: only the header band and the two end posts, nothing in the opening
	 */
	public static List<double[]> boxes(ScreenCurveKind kind, boolean doorway) {
		final List<double[]> out = new ArrayList<>();
		if (kind.curve() == null) {
			if (doorway) {
				out.add(new double[]{0, 0, 0, 1.5, HEIGHT, STRAIGHT_THICKNESS});
				out.add(new double[]{14.5, 0, 0, 16, HEIGHT, STRAIGHT_THICKNESS});
				out.add(new double[]{1.5, HEADER_BOTTOM, 0, 14.5, HEIGHT, STRAIGHT_THICKNESS});
			} else {
				out.add(new double[]{0, 0, 0, 16, HEIGHT, STRAIGHT_THICKNESS});
			}
			return out;
		}
		final double step = 16.0 / STRIPS;
		int first = -1;
		int last = -1;
		final double[][] strips = new double[STRIPS][];
		for (int i = 0; i < STRIPS; i++) {
			final double z1 = i * step;
			final double x2 = Math.round(kind.curve().reach(z1 + step / 2) * 2) / 2.0;
			if (x2 <= 0) {
				continue;
			}
			strips[i] = new double[]{Math.max(0, x2 - CURVE_THICKNESS), z1, x2, z1 + step};
			first = first < 0 ? i : first;
			last = i;
		}
		for (int i = 0; i < STRIPS; i++) {
			final double[] s = strips[i];
			if (s == null) {
				continue;
			}
			final boolean post = i == first || i == last;
			out.add(new double[]{s[0], doorway && !post ? HEADER_BOTTOM : 0, s[1], s[2], HEIGHT, s[3]});
		}
		return out;
	}
}
