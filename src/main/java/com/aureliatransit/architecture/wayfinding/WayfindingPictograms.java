package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.wayfinding.WayfindingLayout.Box;
import com.aureliatransit.architecture.wayfinding.WayfindingLayout.Canvas;

/**
 * Generic, original pictograms built from a few same-coloured rectangles (and the arrow glyphs every Minecraft font
 * has), drawn in the sign's text colour so they contrast with whatever panel they sit on. No operator symbols. All
 * strokes share one colour, so overlapping strokes never z-fight; everything stays inside the given cell.
 */
final class WayfindingPictograms {

	private WayfindingPictograms() {
	}

	/**
	 * Draws {@code pictogram} in the largest square centred in {@code cell}.
	 */
	static void draw(Canvas c, Pictogram pictogram, Box cell, int color) {
		final float side = Math.min(cell.w(), cell.h()) * 0.92F;
		if (side <= 0.5F || pictogram == Pictogram.NONE) {
			return;
		}
		final float cx = cell.cx();
		final float cy = cell.cy();
		final float u = side / 10F;
		switch (pictogram) {
			case STAIRS -> stairs(c, cx, cy, u, color, 4);
			case ESCALATOR -> {
				stairs(c, cx, cy - 0.6F * u, u, color, 3);
				glyph(c, "↗", cx - 2.4F * u, cy + 3.2F * u, 3.6F * u, color);
			}
			case ELEVATOR -> {
				frame(c, cx, cy, 7 * u, 9 * u, u, color);
				glyph(c, "↑", cx, cy + 2.0F * u, 3.4F * u, color);
				glyph(c, "↓", cx, cy - 2.0F * u, 3.4F * u, color);
			}
			case EXIT -> {
				// door frame open to the right, arrow leaving it
				c.edges(cx - 5 * u, cx - 4 * u, cy - 4.5F * u, cy + 4.5F * u, color);
				c.edges(cx - 5 * u, cx + 0.5F * u, cy + 3.5F * u, cy + 4.5F * u, color);
				c.edges(cx - 5 * u, cx + 0.5F * u, cy - 4.5F * u, cy - 3.5F * u, color);
				glyph(c, "→", cx + 2.6F * u, cy, 4.6F * u, color);
			}
			case ENTRANCE -> {
				// door frame open to the left, arrow entering it
				c.edges(cx + 4 * u, cx + 5 * u, cy - 4.5F * u, cy + 4.5F * u, color);
				c.edges(cx - 0.5F * u, cx + 5 * u, cy + 3.5F * u, cy + 4.5F * u, color);
				c.edges(cx - 0.5F * u, cx + 5 * u, cy - 4.5F * u, cy - 3.5F * u, color);
				glyph(c, "→", cx - 2.6F * u, cy, 4.6F * u, color);
			}
			case TRANSFER -> {
				glyph(c, "→", cx, cy + 2.4F * u, 4.2F * u, color);
				glyph(c, "←", cx, cy - 2.4F * u, 4.2F * u, color);
			}
			case BUS -> {
				frame(c, cx, cy + 0.8F * u, 9 * u, 6 * u, u, color);
				c.edges(cx - 4.5F * u, cx + 4.5F * u, cy - 0.3F * u, cy + 0.5F * u, color);
				wheels(c, cx, cy - 3.2F * u, 5.4F * u, 1.8F * u, color);
			}
			case TRAIN -> {
				frame(c, cx, cy + 0.6F * u, 7 * u, 8 * u, u, color);
				c.edges(cx - 3.5F * u, cx + 3.5F * u, cy + 1.6F * u, cy + 2.4F * u, color);
				c.edges(cx - 2.6F * u, cx - 1.4F * u, cy - 2.4F * u, cy - 1.2F * u, color);
				c.edges(cx + 1.4F * u, cx + 2.6F * u, cy - 2.4F * u, cy - 1.2F * u, color);
				c.edges(cx - 0.5F * u, cx + 0.5F * u, cy + 5.2F * u, cy + 4.6F * u, color);
			}
			case TROLLEY -> {
				frame(c, cx, cy - 0.6F * u, 9 * u, 5.4F * u, u, color);
				wheels(c, cx, cy - 4.2F * u, 5.4F * u, 1.5F * u, color);
				c.edges(cx - 0.5F * u, cx + 0.5F * u, cy + 2.1F * u, cy + 4.2F * u, color);
				c.edges(cx - 4.5F * u, cx + 4.5F * u, cy + 4.2F * u, cy + 5.0F * u, color);
			}
			case ACCESSIBLE_ROUTE -> {
				c.edges(cx - 2.6F * u, cx - 1.0F * u, cy + 3.2F * u, cy + 4.8F * u, color);
				c.edges(cx - 2.4F * u, cx - 1.2F * u, cy - 0.6F * u, cy + 2.6F * u, color);
				c.edges(cx - 2.4F * u, cx + 1.6F * u, cy - 1.8F * u, cy - 0.6F * u, color);
				frame(c, cx - 0.4F * u, cy - 2.8F * u, 5.6F * u, 4.4F * u, 0.9F * u, color);
			}
			case HELP_POINT -> glyph(c, "?", cx, cy, side, color);
			case INFORMATION -> glyph(c, "i", cx, cy, side, color);
			case TICKETS -> {
				frame(c, cx, cy, 9 * u, 6 * u, u, color);
				c.edges(cx - 2.5F * u, cx + 2.5F * u, cy + 1.0F * u, cy + 1.8F * u, color);
				c.edges(cx - 2.5F * u, cx + 2.5F * u, cy - 1.8F * u, cy - 1.0F * u, color);
			}
			default -> {
			}
		}
	}

	private static void stairs(Canvas c, float cx, float cy, float u, int color, int steps) {
		final float stepW = 8F * u / steps;
		final float bottom = cy - 4.5F * u;
		for (int i = 0; i < steps; i++) {
			final float height = (i + 1) * 8F * u / steps;
			final float x = cx - 4F * u + i * stepW;
			c.edges(x, x + stepW, bottom, bottom + height, color);
		}
	}

	/**
	 * Hollow rectangle of strokes {@code t} thick.
	 */
	private static void frame(Canvas c, float cx, float cy, float w, float h, float t, int color) {
		c.edges(cx - w / 2, cx - w / 2 + t, cy - h / 2, cy + h / 2, color);
		c.edges(cx + w / 2 - t, cx + w / 2, cy - h / 2, cy + h / 2, color);
		c.edges(cx - w / 2, cx + w / 2, cy + h / 2 - t, cy + h / 2, color);
		c.edges(cx - w / 2, cx + w / 2, cy - h / 2, cy - h / 2 + t, color);
	}

	private static void wheels(Canvas c, float cx, float cy, float spread, float size, int color) {
		c.edges(cx - spread / 2 - size / 2, cx - spread / 2 + size / 2, cy - size / 2, cy + size / 2, color);
		c.edges(cx + spread / 2 - size / 2, cx + spread / 2 + size / 2, cy - size / 2, cy + size / 2, color);
	}

	private static void glyph(Canvas c, String text, float cx, float cy, float size, int color) {
		c.glyph(text, new Box(cx - size / 2, cx + size / 2, cy - size / 2, cy + size / 2), 1.0F, color);
	}
}
