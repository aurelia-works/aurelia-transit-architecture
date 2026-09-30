package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.terminal.MapLine;
import com.aureliatransit.architecture.terminal.MapStop;
import com.aureliatransit.architecture.terminal.SystemMap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Pure layout of the schematic system map page: each line is a horizontal band with a header row, a strip with one dot
 * per station, and station names above/below the strip where they fit. Deterministic for equal input, and every dot and
 * label stays inside {@code [0, width]}. Units are GUI pixels relative to the content area's top-left.
 */
public final class MapLayout {

	public static final float BAND_HEIGHT = 40;
	public static final float HEADER_HEIGHT = 10;
	/** Offset of the strip centre from the band top. */
	public static final float STRIP_OFFSET = 24;
	public static final float SIDE_MARGIN = 6;
	public static final float MAX_LABEL = 56;
	private static final float LABEL_GAP = 3;

	/** A station dot; {@code label} is null when its name did not fit. */
	public record Dot(int index, float x, boolean current, boolean transfer, String label, boolean labelAbove, float labelLeft, float labelWidth) {
	}

	public record Strip(int lineIndex, float bandTop, float stripY, float left, float right, List<Dot> dots) {
	}

	private MapLayout() {
	}

	public static int linesPerPage(float height) {
		return Math.max(1, (int) Math.floor(height / BAND_HEIGHT));
	}

	/**
	 * @param measure pixel width of a string at the label text size
	 */
	public static List<Strip> layout(SystemMap map, int page, float width, float height, ToIntFunction<String> measure) {
		final int perPage = linesPerPage(height);
		final int from = TerminalPaging.first(TerminalPaging.clamp(page, TerminalPaging.pageCount(map.lines().size(), perPage)), perPage);
		final int to = Math.min(map.lines().size(), from + perPage);
		final List<Strip> strips = new ArrayList<>();
		for (int i = from; i < to; i++) {
			strips.add(strip(map.lines().get(i), i, (i - from) * BAND_HEIGHT, width, measure));
		}
		return strips;
	}

	private static Strip strip(MapLine line, int lineIndex, float bandTop, float width, ToIntFunction<String> measure) {
		final List<MapStop> stops = line.stops();
		final int n = stops.size();
		final float left = SIDE_MARGIN;
		final float right = Math.max(left, width - SIDE_MARGIN);
		final float[] xs = new float[n];
		for (int i = 0; i < n; i++) {
			xs[i] = n == 1 ? width / 2 : left + i * (right - left) / (n - 1);
		}

		// Label priority: the current station, the termini, transfers, then the rest in route order.
		final List<Integer> order = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			if (stops.get(i).current()) {
				order.add(i);
			}
		}
		addIf(order, stops, 0);
		addIf(order, stops, n - 1);
		for (int i = 0; i < n; i++) {
			if (stops.get(i).transfer()) {
				addIf(order, stops, i);
			}
		}
		for (int i = 0; i < n; i++) {
			addIf(order, stops, i);
		}

		final List<float[]> above = new ArrayList<>();
		final List<float[]> below = new ArrayList<>();
		final String[] labels = new String[n];
		final boolean[] isAbove = new boolean[n];
		final float[] labelLeft = new float[n];
		final float[] labelWidth = new float[n];
		for (final int i : order) {
			final String text = EPaperLayout.fit(stops.get(i).name(), MAX_LABEL, measure);
			if (text.isEmpty()) {
				continue;
			}
			final float w = Math.min(width, measure.applyAsInt(text));
			final float x0 = Math.max(0, Math.min(width - w, xs[i] - w / 2));
			final boolean preferAbove = (i & 1) == 0;
			if (free(preferAbove ? above : below, x0, w)) {
				(preferAbove ? above : below).add(new float[]{x0, x0 + w});
				isAbove[i] = preferAbove;
			} else if (free(preferAbove ? below : above, x0, w)) {
				(preferAbove ? below : above).add(new float[]{x0, x0 + w});
				isAbove[i] = !preferAbove;
			} else {
				continue;
			}
			labels[i] = text;
			labelLeft[i] = x0;
			labelWidth[i] = w;
		}

		final List<Dot> dots = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			final MapStop stop = stops.get(i);
			dots.add(new Dot(i, xs[i], stop.current(), stop.transfer(), labels[i], isAbove[i], labelLeft[i], labelWidth[i]));
		}
		return new Strip(lineIndex, bandTop, bandTop + STRIP_OFFSET, left, right, dots);
	}

	private static void addIf(List<Integer> order, List<MapStop> stops, int index) {
		if (index >= 0 && index < stops.size() && !order.contains(index)) {
			order.add(index);
		}
	}

	private static boolean free(List<float[]> used, float x0, float w) {
		for (final float[] interval : used) {
			if (x0 < interval[1] + LABEL_GAP && x0 + w > interval[0] - LABEL_GAP) {
				return false;
			}
		}
		return true;
	}
}
