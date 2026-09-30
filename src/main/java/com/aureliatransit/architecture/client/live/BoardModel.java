package com.aureliatransit.architecture.client.live;

import java.util.Arrays;

/**
 * Pre-computed draw list of one display: flat rectangles and text strings in "virtual units" (1 unit = 1/8 of a
 * default font line; origin top-left of the screen, y down). Rebuilt at most about once a second by {@link BoardBuilder},
 * then replayed every frame without any layout work. Arrays are reused between rebuilds.
 */
final class BoardModel {

	/**
	 * Depth layers of rectangles. Overlapping rectangles must not share a plane: coplanar quads z-fight, which showed as
	 * route chips and row bands flickering against the background as the camera moved.
	 */
	static final int LAYER_BACKGROUND = 0;
	static final int LAYER_BAND = 1;
	static final int LAYER_CHIP = 2;
	static final int LAYERS = 3;

	/** Blocks per virtual unit. */
	float scale;
	/** Virtual width and height of the screen area. */
	float width;
	float height;
	/** True when a marquee is active and the model must be rebuilt at the faster marquee cadence. */
	boolean marquee;

	int rectCount;
	float[] rx = new float[32];
	float[] ry = new float[32];
	float[] rw = new float[32];
	float[] rh = new float[32];
	int[] rc = new int[32];
	int[] rl = new int[32];

	int textCount;
	String[] ts = new String[32];
	float[] tx = new float[32];
	float[] ty = new float[32];
	float[] tk = new float[32];
	float[] tsx = new float[32];
	int[] tc = new int[32];

	void reset() {
		rectCount = 0;
		textCount = 0;
		marquee = false;
		Arrays.fill(ts, null);
	}

	void rect(float x, float y, float w, float h, int argb, int layer) {
		if (rectCount == rx.length) {
			final int n = rectCount * 2;
			rx = Arrays.copyOf(rx, n);
			ry = Arrays.copyOf(ry, n);
			rw = Arrays.copyOf(rw, n);
			rh = Arrays.copyOf(rh, n);
			rc = Arrays.copyOf(rc, n);
			rl = Arrays.copyOf(rl, n);
		}
		rx[rectCount] = x;
		ry[rectCount] = y;
		rw[rectCount] = w;
		rh[rectCount] = h;
		rc[rectCount] = argb;
		rl[rectCount] = layer;
		rectCount++;
	}

	/**
	 * @param k  uniform text scale (1 = normal 8-unit line)
	 * @param sx extra horizontal squeeze (1 = none)
	 */
	void text(String text, float x, float y, float k, float sx, int argb) {
		if (text.isEmpty()) {
			return;
		}
		if (textCount == ts.length) {
			final int n = textCount * 2;
			ts = Arrays.copyOf(ts, n);
			tx = Arrays.copyOf(tx, n);
			ty = Arrays.copyOf(ty, n);
			tk = Arrays.copyOf(tk, n);
			tsx = Arrays.copyOf(tsx, n);
			tc = Arrays.copyOf(tc, n);
		}
		ts[textCount] = text;
		tx[textCount] = x;
		ty[textCount] = y;
		tk[textCount] = k;
		tsx[textCount] = sx;
		tc[textCount] = argb;
		textCount++;
	}
}
