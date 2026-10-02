package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.live.cache.ServiceOrder;
import com.aureliatransit.architecture.live.display.DepartureText;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.transit.ServiceSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure layout and refresh policy of the bus e-paper board. A monochrome, low-contrast "paper and ink" look: no colour,
 * no marquee, no pages. The board is deliberately slow: it re-lays itself out only every {@link #MIN_REFRESH_MILLIS} to
 * {@link #MAX_REFRESH_MILLIS} (per-board offset, so boards do not refresh in the same frame) or when its data changes.
 * Model pixels, origin at the panel centre, y up (same space as {@link PanelLayout}); no client classes, so it is testable.
 */
public final class EPaperLayout {

	public static final int PAPER = 0xFFCDD0C3;
	public static final int INK = 0xFF1E201A;
	public static final int INK_DIM = 0xFF5B5E52;
	public static final int RULE = 0xFFA9ACA0;

	public static final long MIN_REFRESH_MILLIS = 15_000;
	public static final long MAX_REFRESH_MILLIS = 30_000;

	public static final int MAX_ROWS = 4;
	public static final float HEADER_HEIGHT = 4.6F;
	public static final float HEADER_SCALE = 0.5F;
	public static final float ROW_SCALE = 0.4F;
	public static final float ROW_PITCH = 3.2F;
	public static final float PAD = 0.9F;
	/** Panel width (model px) at which text is drawn at full size; narrower boards scale it down (1.3.1). */
	public static final float FULL_SIZE_WIDTH = 46F;
	/** Smallest text factor, reached by a one-block board (about vanilla sign text size): route, a short destination and the time fit instead of "R…" / "Al…". */
	public static final float MIN_TEXT_FACTOR = 0.375F;

	/** One arrival line: route chip text, destination, and the pre-formatted time/status. */
	public record Row(String route, String destination, String status) {
	}

	private EPaperLayout() {
	}

	// ---- refresh policy ------------------------------------------------------------------------------------------------

	/**
	 * Refresh interval of one board: between {@link #MIN_REFRESH_MILLIS} and {@link #MAX_REFRESH_MILLIS}, fixed per board
	 * (derived from {@code seed}, e.g. the block position) so neighbouring boards stagger.
	 */
	public static long refreshIntervalMillis(long seed) {
		final long span = MAX_REFRESH_MILLIS - MIN_REFRESH_MILLIS;
		final long mixed = seed * 0x9E3779B97F4A7C15L;
		return MIN_REFRESH_MILLIS + Math.floorMod(mixed ^ (mixed >>> 29), span + 1);
	}

	public static boolean due(long nowMillis, long nextRefreshMillis) {
		// The clock going backwards (a huge gap) also forces a refresh.
		return nowMillis >= nextRefreshMillis || nextRefreshMillis - nowMillis > MAX_REFRESH_MILLIS * 2;
	}

	// ---- content ------------------------------------------------------------------------------------------------------

	/**
	 * The next departures as rows: already-departed services dropped, stable {@link ServiceOrder} order, capped.
	 * Nothing is invented: an empty list stays empty.
	 */
	public static List<Row> rows(List<ServiceSnapshot> services, long nowMillis, int max) {
		final List<ServiceSnapshot> upcoming = new ArrayList<>(services.size());
		for (final ServiceSnapshot service : services) {
			if (service.departureMillis() >= nowMillis) {
				upcoming.add(service);
			}
		}
		upcoming.sort(ServiceOrder.COMPARATOR);
		final List<Row> rows = new ArrayList<>();
		for (final ServiceSnapshot service : upcoming) {
			if (rows.size() >= max) {
				break;
			}
			final String route = service.routeNumber().isBlank() ? service.routeName() : service.routeNumber();
			rows.add(new Row(route.length() > 4 ? route.substring(0, 4) : route, service.destination(), DepartureText.status(service, nowMillis)));
		}
		return rows;
	}

	/**
	 * Text size factor for a panel width: 1 from {@link #FULL_SIZE_WIDTH} up (three blocks), scaling down linearly to
	 * {@link #MIN_TEXT_FACTOR} so one- and two-block boards fit a route, a destination and a time on one line.
	 */
	public static float textFactor(float panelWidth) {
		return Math.max(MIN_TEXT_FACTOR, Math.min(1F, panelWidth / FULL_SIZE_WIDTH));
	}

	/**
	 * How many arrival rows fit a panel of the given height at full text size.
	 */
	public static int maxRows(float panelHeight) {
		return maxRows(panelHeight, FULL_SIZE_WIDTH);
	}

	/**
	 * How many arrival rows fit a panel of the given size (text scales with {@link #textFactor}).
	 */
	public static int maxRows(float panelHeight, float panelWidth) {
		final float f = textFactor(panelWidth);
		return Math.max(0, Math.min(MAX_ROWS, (int) Math.floor((panelHeight - HEADER_HEIGHT * f - PAD * 0.5F) / (ROW_PITCH * f))));
	}

	// ---- layout -------------------------------------------------------------------------------------------------------

	/**
	 * @param header  stop name (already resolved); may be empty
	 * @param rows    arrival rows (only as many as fit are drawn)
	 * @param idle    text shown in place of rows when there are none ("No departures currently available" / "No stop linked")
	 */
	public static PanelLayout.Panel layout(String header, List<Row> rows, String idle, float w, float h, ToIntFunction<String> measure) {
		final List<PanelLayout.Rect> rects = new ArrayList<>();
		final List<PanelLayout.Label> labels = new ArrayList<>();
		final float f = textFactor(w);
		final float headerH = HEADER_HEIGHT * f;
		final float headerScale = HEADER_SCALE * f;
		final float rowScale = ROW_SCALE * f;
		final float pitch = ROW_PITCH * f;
		final float left = -w / 2 + PAD;
		final float right = w / 2 - PAD;
		final float top = h / 2;

		rects.add(new PanelLayout.Rect(0, top - headerH / 2, w, headerH, INK));
		final String title = fit(header, (right - left) / headerScale, measure);
		if (!title.isEmpty()) {
			labels.add(left(title, left, top - headerH / 2, headerScale, PAPER, measure));
		}

		final float bodyTop = top - headerH - PAD * 0.5F;
		final int fitting = maxRows(h, w);
		if (rows.isEmpty() || fitting == 0) {
			final float bodyH = bodyTop - (-h / 2);
			final int maxLines = (int) Math.floor(bodyH / pitch);
			if (maxLines >= 1) {
				final List<String> lines = wrap(idle, (right - left) / rowScale, maxLines, measure);
				final float firstCy = bodyTop - (bodyH - lines.size() * pitch) / 2 - pitch / 2;
				for (int i = 0; i < lines.size(); i++) {
					final String line = lines.get(i);
					labels.add(new PanelLayout.Label(line, 0, firstCy - i * pitch, rowScale, INK_DIM, measure.applyAsInt(line)));
				}
			}
			return new PanelLayout.Panel(rects, labels);
		}

		final int count = Math.min(fitting, rows.size());
		float chipW = 0;
		float statusW = 0;
		for (int i = 0; i < count; i++) {
			final Row row = rows.get(i);
			chipW = Math.max(chipW, measure.applyAsInt(row.route()) * rowScale + 1.4F * f);
			statusW = Math.max(statusW, measure.applyAsInt(row.status()) * rowScale);
		}
		chipW = Math.min(chipW, (right - left) * 0.3F);
		final float destLeft = left + chipW + 1.2F * f;
		final float destAvail = right - statusW - 1.2F * f - destLeft;

		for (int i = 0; i < count; i++) {
			final Row row = rows.get(i);
			final float cy = bodyTop - pitch * (i + 0.5F);
			final String route = fit(row.route(), (chipW - 1.4F * f) / rowScale + 0.01F, measure); // epsilon: float round trip of the widest chip
			rects.add(new PanelLayout.Rect(left + chipW / 2, cy, chipW, pitch - 0.8F * f, INK));
			if (!route.isEmpty()) {
				labels.add(new PanelLayout.Label(route, left + chipW / 2, cy, rowScale, PAPER, measure.applyAsInt(route)));
			}
			final String destination = fit(row.destination(), destAvail / rowScale, measure);
			if (!destination.isEmpty()) {
				labels.add(left(destination, destLeft, cy, rowScale, INK, measure));
			}
			final int statusUnits = measure.applyAsInt(row.status());
			labels.add(new PanelLayout.Label(row.status(), right - statusUnits * rowScale / 2, cy, rowScale, INK, statusUnits));
			if (i < count - 1) {
				rects.add(new PanelLayout.Rect(0, cy - pitch / 2, w - 2 * PAD, 0.2F, RULE));
			}
		}
		return new PanelLayout.Panel(rects, labels);
	}

	private static PanelLayout.Label left(String text, float leftX, float cy, float scale, int argb, ToIntFunction<String> measure) {
		final int units = measure.applyAsInt(text);
		return new PanelLayout.Label(text, leftX + units * scale / 2, cy, scale, argb, units);
	}

	/**
	 * Greedy word wrap into at most {@code maxLines} lines of at most {@code availUnits} font units; overflow is folded
	 * into the last line, which is then shortened with an ellipsis. Empty lines are dropped.
	 */
	static List<String> wrap(String text, float availUnits, int maxLines, ToIntFunction<String> measure) {
		final List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (final String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			if (line.length() > 0 && measure.applyAsInt(line + " " + word) > availUnits) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			line.append(line.length() > 0 ? " " : "").append(word);
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		while (lines.size() > Math.max(1, maxLines)) {
			final String last = lines.remove(lines.size() - 1);
			lines.set(lines.size() - 1, lines.get(lines.size() - 1) + " " + last);
		}
		final List<String> fitted = new ArrayList<>(lines.size());
		for (final String candidate : lines) {
			final String shown = fit(candidate, availUnits, measure);
			if (!shown.isEmpty()) {
				fitted.add(shown);
			}
		}
		return fitted;
	}

	/**
	 * Shortens {@code text} with an ellipsis until it is at most {@code availUnits} font units wide.
	 */
	static String fit(String text, float availUnits, ToIntFunction<String> measure) {
		if (text.isEmpty() || availUnits <= 0) {
			return "";
		}
		if (measure.applyAsInt(text) <= availUnits) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1) {
			cut = cut.substring(0, cut.length() - 1);
			final String candidate = cut.stripTrailing() + "…";
			if (measure.applyAsInt(candidate) <= availUnits) {
				return candidate;
			}
		}
		return "";
	}
}
