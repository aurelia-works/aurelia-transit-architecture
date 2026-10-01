package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.Tr;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.wayfinding.ResolvedWayfinding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure layout of the idle face of a passenger information terminal: station name, line badges and a "Touch for
 * information" prompt, all static. The text scale adapts to the panel width so the face is usable on a one-block kiosk
 * as well as a wide wall terminal. Model pixels, origin at the panel centre, y up.
 */
public final class TerminalFace {

	public static final String PROMPT_KEY = "term_touch";
	public static final String FALLBACK_NAME_KEY = "term_passenger_info";
	private static final float PAD = 0.8F;
	private static final float MAX_NAME_SCALE = 0.7F;
	private static final float MAX_PROMPT_SCALE = 0.5F;

	private TerminalFace() {
	}

	public static PanelLayout.Panel layout(ResolvedWayfinding r, float w, float h, int textColor, ToIntFunction<String> measure) {
		final List<PanelLayout.Rect> rects = new ArrayList<>();
		final List<PanelLayout.Label> labels = new ArrayList<>();
		final float avail = w - 2 * PAD;
		if (avail <= 0 || h <= 0) {
			return PanelLayout.Panel.EMPTY;
		}
		final float top = h / 2;

		// Three bands that never overlap: name (4-40 % from the top), badges (centred at 52 %), prompt (64-95 %).
		final String name = r.stationName().isEmpty() ? Tr.t(FALLBACK_NAME_KEY) : r.stationName();
		block(labels, name, avail, top - h * 0.22F, h * 0.36F, MAX_NAME_SCALE, textColor, measure);

		final float badgeH = Math.min(3.4F, h * 0.16F);
		final float badgeY = top - h * 0.52F;
		float used = 0;
		final List<PanelLayout.Rect> badgeRects = new ArrayList<>();
		final List<PanelLayout.Label> badgeLabels = new ArrayList<>();
		final float labelScale = badgeH / 9F * 0.9F;
		for (final LineBadge badge : r.lines()) {
			final int units = measure.applyAsInt(badge.label());
			final float bw = Math.max(badgeH, units * labelScale + 1.4F);
			if (used + bw > avail) {
				break;
			}
			badgeRects.add(new PanelLayout.Rect(used + bw / 2, badgeY, bw, badgeH, badge.argb()));
			badgeLabels.add(new PanelLayout.Label(badge.label(), used + bw / 2, badgeY, labelScale, contrast(badge.rgb()), units));
			used += bw + 0.8F;
		}
		final float shift = -(used - 0.8F) / 2;
		for (final PanelLayout.Rect rect : badgeRects) {
			rects.add(new PanelLayout.Rect(rect.cx() + shift, rect.cy(), rect.w(), rect.h(), rect.argb()));
		}
		for (final PanelLayout.Label label : badgeLabels) {
			labels.add(new PanelLayout.Label(label.text(), label.cx() + shift, label.cy(), label.scale(), label.argb(), label.widthUnits()));
		}

		rects.add(new PanelLayout.Rect(0, -h / 2 + h * 0.03F, avail, 0.5F, 0xFF4FB8B0));
		block(labels, Tr.t(PROMPT_KEY), avail, top - h * 0.795F, h * 0.31F, MAX_PROMPT_SCALE, 0xFF4FB8B0, measure);
		return new PanelLayout.Panel(rects, labels);
	}

	/**
	 * Text of one or two lines centred on {@code cy} that fits {@code avail x blockH}: the larger of the best one-line
	 * scale and the best balanced two-line scale (split at the word boundary with the narrowest wider half). Nothing is
	 * truncated; long text only gets smaller.
	 */
	private static void block(List<PanelLayout.Label> out, String text, float avail, float cy, float blockH, float maxScale, int color, ToIntFunction<String> measure) {
		final int full = Math.max(1, measure.applyAsInt(text));
		final float one = Math.min(maxScale, Math.min(blockH / 9F, avail / full));
		String first = text;
		String second = "";
		float two = 0;
		int split = text.indexOf(' ');
		while (split > 0) {
			final String a = text.substring(0, split).trim();
			final String b = text.substring(split + 1).trim();
			final float scale = Math.min(maxScale, Math.min(blockH / 18F, avail / Math.max(1, Math.max(measure.applyAsInt(a), measure.applyAsInt(b)))));
			if (scale > two) {
				two = scale;
				first = a;
				second = b;
			}
			split = text.indexOf(' ', split + 1);
		}
		if (one >= two || second.isEmpty()) {
			out.add(new PanelLayout.Label(text, 0, cy, one, color, full));
			return;
		}
		final float lineH = two * 9F;
		out.add(new PanelLayout.Label(first, 0, cy + lineH / 2, two, color, measure.applyAsInt(first)));
		out.add(new PanelLayout.Label(second, 0, cy - lineH / 2, two, color, measure.applyAsInt(second)));
	}

	static int contrast(int rgb) {
		final float lum = (0.2126F * ((rgb >> 16) & 0xFF) + 0.7152F * ((rgb >> 8) & 0xFF) + 0.0722F * (rgb & 0xFF)) / 255F;
		return lum > 0.6F ? 0xFF101010 : 0xFFFFFFFF;
	}
}
