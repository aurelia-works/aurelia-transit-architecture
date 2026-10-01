package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.Tr;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure layout of the station information board: the same {@link ResolvedWayfinding} every other wayfinding block uses,
 * shown as one of the {@link BoardView}s. Nothing new is stored or invented: the board only picks and arranges facts
 * the shared model already resolved (station, lines, platform, transfers, exits) plus the service messages that apply
 * to the station. Static: callers cache the result and rebuild only when the resolution or the messages change.
 *
 * <p>Model pixels, origin at the panel centre, y up (same as {@link WayfindingLayout}).
 */
public final class StationBoardLayout {

	private static final float PAD = 0.9F;
	private static final float HEADER_FRACTION = 0.22F;
	private static final float MAX_ROW_SCALE = 0.62F;
	private static final float MIN_ROW_SCALE = 0.31F;
	private static final int HEADER_FILL = 0xFF2B6CB0;
	private static final int HEADER_TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFF9AA5AE;
	private static final int ROW_BAND = 0xFF20252B;

	private StationBoardLayout() {
	}

	/**
	 * @param notices the service messages that apply to the board's station, already in rotation order
	 *                ({@code ServiceMessages.applicable})
	 */
	public static PanelLayout.Panel layout(ResolvedWayfinding r, BoardView view, List<ServiceMessage> notices, float w, float h, int textColor,
										   ToIntFunction<String> measure) {
		if (w <= 2 * PAD || h <= 2 * PAD) {
			return PanelLayout.Panel.EMPTY;
		}
		final Canvas c = new Canvas(measure, w, h);
		switch (view) {
			case TRAINS_THIS_SIDE -> delegated(c, r, WayfindingPanelKind.WALL_DIRECTION, "board_view.trains", textColor);
			case PLATFORM_TRACK -> delegated(c, r, WayfindingPanelKind.PLATFORM, "board_view.platform", textColor);
			case SERVICE_CHANGE -> serviceChange(c, notices, textColor);
			case TRANSFER -> transfer(c, r, textColor);
			case EXITS -> exits(c, r, textColor);
		}
		return c.panel();
	}

	/** Heading band plus an existing wayfinding layout in the space below it. */
	private static void delegated(Canvas c, ResolvedWayfinding r, WayfindingPanelKind kind, String headingKey, int textColor) {
		final float headerH = c.header(Tr.t(headingKey));
		final float bodyH = c.h - headerH;
		final PanelLayout.Panel body = WayfindingLayout.layout(r, kind, c.w, bodyH, textColor, c.measure);
		final float shift = -headerH / 2;
		for (final PanelLayout.Rect rect : body.rects()) {
			c.rects.add(new PanelLayout.Rect(rect.cx(), rect.cy() + shift, rect.w(), rect.h(), rect.argb()));
		}
		for (final PanelLayout.Label label : body.labels()) {
			c.labels.add(new PanelLayout.Label(label.text(), label.cx(), label.cy() + shift, label.scale(), label.argb(), label.widthUnits()));
		}
	}

	private static void serviceChange(Canvas c, List<ServiceMessage> notices, int textColor) {
		final float headerH = c.header(Tr.t("board_view.service"));
		final Rows rows = new Rows(c, headerH);
		if (notices.isEmpty()) {
			c.text(Tr.t("board_no_changes"), 0, c.top() - headerH - (c.h - headerH) / 2, c.w - 2 * PAD, MAX_ROW_SCALE, DIM);
			return;
		}
		final int capacity = rows.capacity();
		final boolean overflow = notices.size() > capacity;
		final int shown = overflow ? capacity - 1 : notices.size();
		for (int i = 0; i < shown; i++) {
			final ServiceMessage notice = notices.get(i);
			final float cy = rows.centre(i);
			final float markerW = rows.rowH * 0.45F;
			c.rects.add(new PanelLayout.Rect(-c.w / 2 + PAD + markerW / 2, cy, markerW, rows.rowH * 0.62F, severityFill(notice.severity())));
			final float left = -c.w / 2 + PAD + markerW + 0.7F;
			c.textLeft(prefix(notice.severity()) + notice.text(), left, cy, c.w / 2 - PAD - left, rows.scale(), severityText(notice.severity(), textColor));
		}
		if (overflow) {
			c.textLeft(Tr.t("board_more", notices.size() - shown), -c.w / 2 + PAD, rows.centre(shown), c.w - 2 * PAD, rows.scale(), DIM);
		}
	}

	private static void transfer(Canvas c, ResolvedWayfinding r, int textColor) {
		final float headerH = c.header(Tr.t("board_view.transfer"));
		final Rows rows = new Rows(c, headerH);
		int row = 0;
		if (!r.lines().isEmpty()) {
			final float cy = rows.centre(row++);
			final float badgeH = rows.rowH * 0.8F;
			final float scale = badgeH / 9F * 0.9F;
			final List<float[]> widths = new ArrayList<>();
			float used = 0;
			for (final LineBadge badge : r.lines()) {
				final float bw = Math.max(badgeH, c.measure.applyAsInt(badge.label()) * scale + 1.4F);
				if (used + bw > c.w - 2 * PAD) {
					break;
				}
				widths.add(new float[] {used, bw});
				used += bw + 0.8F;
			}
			final float start = -(used - 0.8F) / 2;
			for (int i = 0; i < widths.size(); i++) {
				final LineBadge badge = r.lines().get(i);
				final float cx = start + widths.get(i)[0] + widths.get(i)[1] / 2;
				c.rects.add(new PanelLayout.Rect(cx, cy, widths.get(i)[1], badgeH, badge.argb()));
				c.labels.add(new PanelLayout.Label(badge.label(), cx, cy, scale, contrast(badge.rgb()), c.measure.applyAsInt(badge.label())));
			}
		}
		if (!r.transfers().isEmpty() && row < rows.capacity()) {
			c.textCentred(r.transfers(), 0, rows.centre(row++), c.w - 2 * PAD, rows.scale(), textColor);
		}
		if (!r.streetLabel().isEmpty() && row < rows.capacity()) {
			c.textCentred(r.streetLabel(), 0, rows.centre(row++), c.w - 2 * PAD, rows.scale(), DIM);
		}
		if (row == 0) {
			c.text(Tr.t("board_no_transfer"), 0, c.top() - headerH - (c.h - headerH) / 2, c.w - 2 * PAD, MAX_ROW_SCALE, DIM);
		}
	}

	private static void exits(Canvas c, ResolvedWayfinding r, int textColor) {
		final float headerH = c.header(Tr.t("board_view.exits"));
		final List<ExitInfo> exits = r.allExits();
		if (exits.isEmpty()) {
			c.text(Tr.t("board_no_exits"), 0, c.top() - headerH - (c.h - headerH) / 2, c.w - 2 * PAD, MAX_ROW_SCALE, DIM);
			return;
		}
		final Rows rows = new Rows(c, headerH);
		final int capacity = rows.capacity();
		final boolean overflow = exits.size() > capacity;
		final int shown = overflow ? capacity - 1 : exits.size();
		for (int i = 0; i < shown; i++) {
			final ExitInfo exit = exits.get(i);
			final float cy = rows.centre(i);
			final float chipW = Math.max(rows.rowH * 0.9F, c.measure.applyAsInt(exit.label()) * rows.scale() + 1.2F);
			final float chipCx = -c.w / 2 + PAD + chipW / 2;
			c.rects.add(new PanelLayout.Rect(chipCx, cy, chipW, rows.rowH * 0.86F, 0xFF1F7A3E));
			c.labels.add(new PanelLayout.Label(exit.label(), chipCx, cy, rows.scale(), 0xFFFFFFFF, c.measure.applyAsInt(exit.label())));
			final float left = -c.w / 2 + PAD + chipW + 0.8F;
			final String where = exit.destinations().isEmpty() ? "" : String.join(", ", exit.destinations());
			c.textLeft(where, left, cy, c.w / 2 - PAD - left, rows.scale(), textColor);
		}
		if (overflow) {
			c.textLeft(Tr.t("board_more", exits.size() - shown), -c.w / 2 + PAD, rows.centre(shown), c.w - 2 * PAD, rows.scale(), DIM);
		}
	}

	private static String prefix(MessageSeverity severity) {
		return switch (severity) {
			case INFO -> "";
			case WARNING, DISRUPTION -> "! ";
			case SEVERE -> "!! ";
		};
	}

	private static int severityFill(MessageSeverity severity) {
		return switch (severity) {
			case INFO -> 0xFF4C8DDB;
			case WARNING -> 0xFFE0A800;
			case DISRUPTION -> 0xFFD0342C;
			case SEVERE -> 0xFFFF2A20;
		};
	}

	private static int severityText(MessageSeverity severity, int textColor) {
		return switch (severity) {
			case INFO -> textColor;
			case WARNING -> 0xFFFFD25A;
			case DISRUPTION, SEVERE -> 0xFFFF8A80;
		};
	}

	private static int contrast(int rgb) {
		final float luminance = (0.2126F * ((rgb >> 16) & 0xFF) + 0.7152F * ((rgb >> 8) & 0xFF) + 0.0722F * (rgb & 0xFF)) / 255F;
		return luminance > 0.6F ? 0xFF111111 : 0xFFFFFFFF;
	}

	/** Rectangles, labels and text placement helpers of one board. */
	private static final class Canvas {
		final ToIntFunction<String> measure;
		final float w;
		final float h;
		final List<PanelLayout.Rect> rects = new ArrayList<>();
		final List<PanelLayout.Label> labels = new ArrayList<>();

		Canvas(ToIntFunction<String> measure, float w, float h) {
			this.measure = measure;
			this.w = w;
			this.h = h;
		}

		float top() {
			return h / 2;
		}

		PanelLayout.Panel panel() {
			return new PanelLayout.Panel(List.copyOf(rects), List.copyOf(labels));
		}

		/** The heading band across the top; returns its height. */
		float header(String text) {
			final float headerH = h * HEADER_FRACTION;
			rects.add(new PanelLayout.Rect(0, top() - headerH / 2, w, headerH, HEADER_FILL));
			text(text, 0, top() - headerH / 2, w - 2 * PAD, Math.min(MAX_ROW_SCALE, headerH / 9F * 0.9F), HEADER_TEXT);
			return headerH;
		}

		/** Text centred on (cx, cy), at most {@code maxScale}, shrunk to fit {@code avail}; never truncated. */
		void text(String text, float cx, float cy, float avail, float maxScale, int color) {
			final int units = Math.max(1, measure.applyAsInt(text));
			final float scale = Math.min(maxScale, avail / units);
			labels.add(new PanelLayout.Label(text, cx, cy, scale, color, units));
		}

		void textCentred(String text, float cx, float cy, float avail, float maxScale, int color) {
			text(text, cx, cy, avail, maxScale, color);
		}

		/**
		 * Left-aligned text starting at {@code left}. It keeps {@code maxScale} (so rows stay legible) and is cut with an
		 * ellipsis when it would not fit even at the smallest legible scale.
		 */
		void textLeft(String text, float left, float cy, float avail, float maxScale, int color) {
			if (text.isEmpty() || avail <= 0) {
				return;
			}
			String shown = text;
			int units = Math.max(1, measure.applyAsInt(shown));
			float scale = Math.min(maxScale, avail / units);
			if (scale < MIN_ROW_SCALE) {
				scale = MIN_ROW_SCALE;
				while (shown.length() > 1 && measure.applyAsInt(shown + "…") * scale > avail) {
					shown = shown.substring(0, shown.length() - 1);
				}
				shown = shown + "…";
				units = Math.max(1, measure.applyAsInt(shown));
				scale = Math.min(scale, avail / units); // only matters on a panel too small to be legible anyway
			}
			labels.add(new PanelLayout.Label(shown, left + units * scale / 2, cy, scale, color, units));
		}
	}

	/** Evenly spaced text rows under the heading band. */
	private static final class Rows {
		final float top;
		final float rowH;
		final int capacity;
		private final float scale;

		Rows(Canvas c, float headerH) {
			this.top = c.top() - headerH;
			final float avail = c.h - headerH - 0.6F;
			final int fit = Math.max(1, (int) Math.floor(avail / (9F * MIN_ROW_SCALE + 0.2F)));
			this.capacity = Math.min(4, fit);
			this.rowH = avail / capacity;
			this.scale = Math.min(MAX_ROW_SCALE, rowH / 9F * 0.92F);
		}

		int capacity() {
			return capacity;
		}

		float scale() {
			return scale;
		}

		float centre(int index) {
			return top - 0.3F - rowH * (index + 0.5F);
		}
	}
}
