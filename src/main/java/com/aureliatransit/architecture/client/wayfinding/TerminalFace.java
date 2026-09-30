package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.text.PanelLayout;
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

	public static final String PROMPT = "Touch for information";
	public static final String FALLBACK_NAME = "Passenger information";
	private static final float PAD = 0.8F;
	private static final float MAX_NAME_SCALE = 0.7F;
	private static final float MAX_PROMPT_SCALE = 0.5F;
	private static final float MIN_SCALE = 0.2F;

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

		// Name in the upper 40 %, badges in the middle, prompt below.
		final String name = r.stationName().isEmpty() ? FALLBACK_NAME : r.stationName();
		block(labels, name, avail, top - h * 0.22F, h * 0.36F, MAX_NAME_SCALE, textColor, measure);

		final float badgeH = Math.min(3.4F, h * 0.2F);
		final float badgeY = top - h * 0.55F;
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
		block(labels, PROMPT, avail, -h * 0.28F, h * 0.36F, MAX_PROMPT_SCALE, 0xFF4FB8B0, measure);
		return new PanelLayout.Panel(rects, labels);
	}

	/**
	 * Word-wrapped text of up to two lines centred on {@code cy}, in a block {@code blockH} high.
	 */
	private static void block(List<PanelLayout.Label> out, String text, float avail, float cy, float blockH, float maxScale, int color, ToIntFunction<String> measure) {
		int longestWord = 1;
		for (final String word : text.split(" ")) {
			longestWord = Math.max(longestWord, measure.applyAsInt(word));
		}
		final float scale = Math.max(MIN_SCALE, Math.min(maxScale, avail / longestWord));
		final float lineH = Math.min(scale * 9F, blockH / 2);
		final List<String> lines = EPaperLayout.wrap(text, avail / scale, 2, measure);
		final float first = cy + (lines.size() - 1) * lineH / 2;
		for (int i = 0; i < lines.size(); i++) {
			out.add(new PanelLayout.Label(lines.get(i), 0, first - i * lineH, scale, color, measure.applyAsInt(lines.get(i))));
		}
	}

	static int contrast(int rgb) {
		final float lum = (0.2126F * ((rgb >> 16) & 0xFF) + 0.7152F * ((rgb >> 8) & 0xFF) + 0.0722F * (rgb & 0xFF)) / 255F;
		return lum > 0.6F ? 0xFF101010 : 0xFFFFFFFF;
	}
}
