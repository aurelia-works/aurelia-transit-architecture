package com.aureliatransit.architecture.client.wayfinding;

/**
 * Pure pagination maths of the terminal pages (rows of text, departures, map lines).
 */
public final class TerminalPaging {

	private TerminalPaging() {
	}

	/** At least 1, even for an empty list. */
	public static int pageCount(int total, int perPage) {
		return perPage <= 0 ? 1 : Math.max(1, (total + perPage - 1) / perPage);
	}

	public static int clamp(int page, int pageCount) {
		return Math.max(0, Math.min(page, Math.max(1, pageCount) - 1));
	}

	public static int first(int page, int perPage) {
		return Math.max(0, page) * Math.max(0, perPage);
	}

	public static int end(int page, int perPage, int total) {
		return Math.min(Math.max(0, total), first(page, perPage) + Math.max(0, perPage));
	}
}
