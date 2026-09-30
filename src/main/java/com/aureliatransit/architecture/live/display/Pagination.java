package com.aureliatransit.architecture.live.display;

/**
 * Time-driven paging maths shared by the concourse board (departure pages) and the calling-at line (stop pages).
 */
public final class Pagination {

	private Pagination() {
	}

	public static int pageCount(int total, int perPage) {
		if (total <= 0 || perPage <= 0) {
			return 1;
		}
		return (total + perPage - 1) / perPage;
	}

	/**
	 * Which page to show at {@code nowMillis} when each page is shown for {@code pageMillis}.
	 */
	public static int currentPage(long nowMillis, long pageMillis, int pages) {
		if (pages <= 1 || pageMillis <= 0) {
			return 0;
		}
		return (int) Math.floorMod(nowMillis / pageMillis, (long) pages);
	}

	public static int firstIndex(int page, int perPage) {
		return page * perPage;
	}

	public static int endIndex(int page, int perPage, int total) {
		return Math.min(total, (page + 1) * perPage);
	}
}
