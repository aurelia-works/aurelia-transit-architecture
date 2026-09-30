package com.aureliatransit.architecture.live.display;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the calling points of a service into "Calling at: ..." pages that each fit a character budget, so the
 * sub-line can rotate through them instead of scrolling.
 */
public final class CallingPages {

	public static final String PREFIX = "Calling at ";
	public static final int MAX_PAGES = 6;

	private CallingPages() {
	}

	/**
	 * @param maxChars character budget for one page including the prefix (clamped to a sensible minimum)
	 */
	public static List<String> paginate(List<String> stops, int maxChars) {
		final List<String> pages = new ArrayList<>();
		if (stops.isEmpty()) {
			return pages;
		}
		final int budget = Math.max(PREFIX.length() + 8, maxChars);
		StringBuilder page = new StringBuilder(PREFIX);
		boolean first = true;
		for (int i = 0; i < stops.size() && pages.size() < MAX_PAGES; i++) {
			final String stop = stops.get(i);
			final boolean last = i == stops.size() - 1;
			final String separator = first ? "" : (last ? " and " : ", ");
			if (!first && page.length() + separator.length() + stop.length() > budget) {
				pages.add(page.append(i < stops.size() ? "..." : "").toString());
				page = new StringBuilder(PREFIX);
				first = true;
				i--;
				continue;
			}
			page.append(first ? "" : separator).append(stop);
			first = false;
		}
		if (!first && pages.size() < MAX_PAGES) {
			pages.add(page.toString());
		}
		return pages;
	}
}
