package com.aureliatransit.architecture.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure layout of sign and information panels, shared by the in-world renderers and the editor previews. A layout is
 * computed once per data change (callers cache it) from a text-width function, and is expressed in model pixels
 * relative to the panel centre (x to the right, y up) so the same result can be drawn in 3D or in a GUI.
 */
public final class PanelLayout {

	public static final float STRIPE = 1.0F;

	/** A filled rectangle. */
	public record Rect(float cx, float cy, float w, float h, int argb) {
	}

	/** A line of text centred on (cx, cy); {@code scale} is model pixels per font unit, {@code widthUnits} the measured width in font units. */
	public record Label(String text, float cx, float cy, float scale, int argb, int widthUnits) {
	}

	public record Panel(List<Rect> rects, List<Label> labels) {

		public static final Panel EMPTY = new Panel(List.of(), List.of());

		public boolean isEmpty() {
			return rects.isEmpty() && labels.isEmpty();
		}
	}

	/** Colours and limits of an information panel. */
	public record InfoStyle(int bodyColor, int headingPlainColor, float maxBodyScale, int maxBodyLines) {
	}

	private PanelLayout() {
	}

	private static Label label(ToIntFunction<String> measure, String text, float cx, float cy, float scale, int argb) {
		return new Label(text, cx, cy, scale, argb, measure.applyAsInt(text));
	}

	// ---- Signs ---------------------------------------------------------------------------------------------------

	/**
	 * @param primary the name to show (already resolved when the sign uses the automatic station name)
	 * @param w       panel width in model pixels
	 * @param h       panel height in model pixels
	 */
	public static Panel sign(SignData d, String primary, SignStyle style, float w, float h, ToIntFunction<String> measure) {
		final List<Rect> rects = new ArrayList<>();
		final List<Label> labels = new ArrayList<>();
		final float pad = 0.75F;
		final float stripe = d.accent() != AccentPalette.NONE ? STRIPE : 0;
		if (stripe > 0) {
			rects.add(new Rect(0, -h / 2 + stripe / 2, w, stripe, d.accent().argb()));
		}
		final float bottom = -h / 2 + stripe + (stripe > 0 ? 0.5F : pad * 0.5F);
		final float top = h / 2 - pad * 0.5F;
		final float areaH = top - bottom;
		final float cy = (top + bottom) / 2;
		float left = -w / 2 + pad;
		float right = w / 2 - pad;
		if (areaH <= 0 || right - left <= 0) {
			return new Panel(rects, labels);
		}
		final int textColor = style.textColor();

		if (style.isNumberPlate()) {
			placeRows(labels, new String[]{d.platform(), primary}, new float[]{2.2F, 0.75F}, new int[]{textColor, style.secondaryColor()},
					TextAlignment.CENTER, left, right, top, bottom, 10, measure);
			return new Panel(rects, labels);
		}

		final float gap = 1.0F;
		if (style.hasPlatform() && !d.platform().isEmpty()) {
			final int fill = d.accent() == AccentPalette.NONE ? 0xFFF2F2EE : d.accent().argb();
			final float heightScale = areaH * 0.68F / TextFit.ROW;
			final float textUnits = measure.applyAsInt(d.platform());
			final float badgeW = Math.min(areaH * 2, Math.max(areaH, textUnits * heightScale + 1.6F));
			final float scale = Math.min(heightScale, textUnits > 0 ? (badgeW - 1.2F) / textUnits : heightScale);
			rects.add(new Rect(left + badgeW / 2, cy, badgeW, areaH, fill));
			labels.add(label(measure, d.platform(), left + badgeW / 2, cy, scale, TextFit.contrastText(fill & 0xFFFFFF)));
			left += badgeW + gap;
		}
		if (style.hasArrow() && d.arrow() != SignArrow.NONE) {
			final float cell = areaH * 0.9F;
			final String glyph = d.arrow().glyph();
			final float scale = TextFit.fitOne(measure.applyAsInt(glyph), cell * 0.9F, areaH * 0.9F, 10);
			labels.add(label(measure, glyph, d.arrow().onLeft() ? left + cell / 2 : right - cell / 2, cy, scale, textColor));
			if (d.arrow().onLeft()) {
				left += cell + gap;
			} else {
				right -= cell + gap;
			}
		}

		if (style.hasRoutes()) {
			routes(rects, labels, d, primary, style, left, right, top, bottom, measure);
			return new Panel(rects, labels);
		}

		final boolean second = style.hasSecondary() && !d.secondary().isEmpty();
		if (!primary.isEmpty() && second) {
			placeRows(labels, new String[]{primary, d.secondary()}, new float[]{1, 0.6F}, new int[]{textColor, style.secondaryColor()},
					d.alignment(), left, right, top, bottom, 10, measure);
		} else if (!primary.isEmpty()) {
			placeRows(labels, new String[]{primary}, new float[]{1}, new int[]{textColor}, d.alignment(), left, right, top, bottom, 10, measure);
		} else if (second) {
			placeRows(labels, new String[]{d.secondary()}, new float[]{0.6F}, new int[]{style.secondaryColor()}, d.alignment(), left, right, top, bottom, 10, measure);
		}
		return new Panel(rects, labels);
	}

	private static void routes(List<Rect> rects, List<Label> labels, SignData d, String primary, SignStyle style, float left, float right, float top,
	                           float bottom, ToIntFunction<String> measure) {
		final boolean hasRoutes = !d.routes().isEmpty();
		final float badgeH = hasRoutes ? Math.min((top - bottom) * 0.45F, 3.6F) : 0;
		final float gap = hasRoutes ? 0.6F : 0;
		if (!primary.isEmpty()) {
			placeRows(labels, new String[]{primary}, new float[]{1}, new int[]{style.textColor()}, d.alignment(), left, right, top, bottom + badgeH + gap, 10, measure);
		}
		if (!hasRoutes) {
			return;
		}
		final int n = d.routes().size();
		final float between = 0.5F;
		final float badgeW = Math.min(badgeH * 1.8F, (right - left - (n - 1) * between) / n);
		final float total = n * badgeW + (n - 1) * between;
		float x = switch (d.alignment()) {
			case LEFT -> left;
			case RIGHT -> right - total;
			default -> (left + right) / 2 - total / 2;
		};
		final float cy = bottom + badgeH / 2;
		for (final RouteBadge badge : d.routes()) {
			final int fill = badge.color() == AccentPalette.NONE ? 0xFF5F6B75 : badge.color().argb();
			rects.add(new Rect(x + badgeW / 2, cy, badgeW, badgeH, fill));
			final float scale = TextFit.fitOne(measure.applyAsInt(badge.label()), badgeW * 0.86F, badgeH * 0.86F, 10);
			labels.add(label(measure, badge.label(), x + badgeW / 2, cy, scale, TextFit.contrastText(fill & 0xFFFFFF)));
			x += badgeW + between;
		}
	}

	// ---- Information panels --------------------------------------------------------------------------------------

	public static Panel info(ConfigurableTextData d, InfoStyle style, float w, float h, ToIntFunction<String> measure) {
		if (d.isEmpty()) {
			return Panel.EMPTY;
		}
		final List<Rect> rects = new ArrayList<>();
		final List<Label> labels = new ArrayList<>();
		final float pad = 0.5F;
		final float left = -w / 2 + pad;
		final float right = w / 2 - pad;
		float top = h / 2 - pad;
		final float bottom = -h / 2 + pad;
		if (!d.heading().isEmpty()) {
			final boolean band = d.accent() != AccentPalette.NONE;
			final float textW = right - left - (band ? 1.0F : 0);
			final float scale = TextFit.fitOne(measure.applyAsInt(d.heading()), textW, h * 0.26F, 0.9F);
			final float rowH = scale * TextFit.ROW;
			final float bandH = rowH + (band ? 1.2F : 0.4F);
			final float headCy = h / 2 - bandH / 2;
			final int color;
			if (band) {
				rects.add(new Rect(0, headCy, w, bandH, d.accent().argb()));
				color = TextFit.contrastText(d.accent().rgb());
			} else {
				color = style.headingPlainColor();
			}
			final float pw = measure.applyAsInt(d.heading()) * scale;
			final float innerL = left + (band ? 0.5F : 0);
			final float innerR = right - (band ? 0.5F : 0);
			final float cx = switch (d.alignment()) {
				case LEFT -> innerL + pw / 2;
				case RIGHT -> innerR - pw / 2;
				default -> (innerL + innerR) / 2;
			};
			labels.add(label(measure, d.heading(), cx, headCy, scale, color));
			top = h / 2 - bandH - pad;
		}
		final int rows = Math.min(d.body().size(), style.maxBodyLines());
		if (rows > 0 && top > bottom) {
			final String[] texts = new String[rows];
			final float[] weights = new float[rows];
			final int[] colors = new int[rows];
			for (int i = 0; i < rows; i++) {
				texts[i] = d.body().get(i);
				weights[i] = 1;
				colors[i] = style.bodyColor();
			}
			placeRows(labels, texts, weights, colors, d.alignment(), left, right, top, bottom, style.maxBodyScale(), measure, false);
		}
		return new Panel(rects, labels);
	}

	// ---- Shared row placement ------------------------------------------------------------------------------------

	private static void placeRows(List<Label> out, String[] texts, float[] weights, int[] colors, TextAlignment align, float left, float right,
	                              float top, float bottom, float maxScale, ToIntFunction<String> measure) {
		placeRows(out, texts, weights, colors, align, left, right, top, bottom, maxScale, measure, true);
	}

	/**
	 * Stacks rows at one common scale inside the box. Empty rows keep their space but draw nothing.
	 */
	private static void placeRows(List<Label> out, String[] texts, float[] weights, int[] colors, TextAlignment align, float left, float right,
	                              float top, float bottom, float maxScale, ToIntFunction<String> measure, boolean centerVertically) {
		final int n = texts.length;
		final float[] widths = new float[n];
		float totalWeight = 0;
		for (int i = 0; i < n; i++) {
			widths[i] = texts[i].isEmpty() ? 0 : measure.applyAsInt(texts[i]);
			totalWeight += weights[i];
		}
		final float scale = TextFit.fitStack(widths, weights, right - left, top - bottom, maxScale);
		if (scale <= 0) {
			return;
		}
		float y = centerVertically ? (top + bottom) / 2 + totalWeight * TextFit.ROW * scale / 2 : top;
		for (int i = 0; i < n; i++) {
			final float rowH = weights[i] * TextFit.ROW * scale;
			final float cy = y - rowH / 2;
			y -= rowH;
			if (texts[i].isEmpty()) {
				continue;
			}
			final float pw = widths[i] * weights[i] * scale;
			final float cx = switch (align) {
				case LEFT -> left + pw / 2;
				case RIGHT -> right - pw / 2;
				default -> (left + right) / 2;
			};
			out.add(label(measure, texts[i], cx, cy, scale * weights[i], colors[i]));
		}
	}
}
