package com.aureliatransit.architecture.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure layout of the lift status panel (A8): lift name, the levels it serves, and a status bar. The status is set by
 * hand in the panel's editor and stored as a block state; MTR's lifts are never read. Static: rebuilt only when the
 * text, status or size changes. Model pixels, origin at the panel centre, y up.
 */
public final class LiftPanelLayout {

	public static final int IN_SERVICE = 0xFF1F8A4C;
	public static final int OUT_OF_SERVICE = 0xFFC62828;
	public static final int MAINTENANCE = 0xFFE0A800;
	private static final float PAD = 0.6F;

	/** Status as drawn: bar colour and text. */
	public record Status(int fill, int text, String label) {
	}

	private LiftPanelLayout() {
	}

	public static Status status(int ordinal) {
		return switch (ordinal) {
			case 1 -> new Status(OUT_OF_SERVICE, 0xFFFFFFFF, Tr.t("lift_out_of_service"));
			case 2 -> new Status(MAINTENANCE, 0xFF1E1E1E, Tr.t("lift_maintenance"));
			default -> new Status(IN_SERVICE, 0xFFFFFFFF, Tr.t("lift_in_service"));
		};
	}

	/**
	 * @param name   lift name ("Lift", "Lift A"); "Lift" when empty
	 * @param levels levels served ("Street - Concourse - Platforms"), may be empty
	 */
	public static PanelLayout.Panel layout(String name, String levels, Status status, float w, float h, int textColor, int dimColor, ToIntFunction<String> measure) {
		if (w <= 2 * PAD || h <= 2 * PAD) {
			return PanelLayout.Panel.EMPTY;
		}
		final List<PanelLayout.Rect> rects = new ArrayList<>();
		final List<PanelLayout.Label> labels = new ArrayList<>();
		final float barH = h * 0.3F;
		final float barCy = -h / 2 + barH / 2;
		final float inner = w - 2 * PAD;
		rects.add(new PanelLayout.Rect(0, barCy, w, barH, status.fill()));
		label(labels, status.label(), barCy, inner, barH * 0.7F, status.text(), measure);

		final String title = name.isBlank() ? Tr.t("lift_default_name") : name;
		final float top = h / 2 - PAD;
		final float textH = h - barH - 2 * PAD;
		if (levels.isBlank()) {
			label(labels, title, top - textH / 2, inner, textH * 0.6F, textColor, measure);
		} else {
			label(labels, title, top - textH * 0.3F, inner, textH * 0.5F, textColor, measure);
			label(labels, levels, top - textH * 0.8F, inner, textH * 0.32F, dimColor, measure);
		}
		return new PanelLayout.Panel(rects, labels);
	}

	/** One centred line scaled to fit {@code maxW} x {@code maxH} (font is 8 units tall). */
	private static void label(List<PanelLayout.Label> out, String text, float cy, float maxW, float maxH, int argb, ToIntFunction<String> measure) {
		if (text.isEmpty()) {
			return;
		}
		final int units = Math.max(1, measure.applyAsInt(text));
		final float scale = Math.min(maxH / 8F, maxW / units);
		out.add(new PanelLayout.Label(text, 0, cy, scale, argb, units));
	}
}
