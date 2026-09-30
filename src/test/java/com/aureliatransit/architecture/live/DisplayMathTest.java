package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.display.CallingPages;
import com.aureliatransit.architecture.live.display.DepartureText;
import com.aureliatransit.architecture.live.display.Marquee;
import com.aureliatransit.architecture.live.display.Pagination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aureliatransit.architecture.live.TestData.NOW;
import static com.aureliatransit.architecture.live.TestData.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayMathTest {

	@Test
	void pageCountAndRotation() {
		assertEquals(1, Pagination.pageCount(0, 8));
		assertEquals(1, Pagination.pageCount(8, 8));
		assertEquals(2, Pagination.pageCount(9, 8));
		assertEquals(3, Pagination.pageCount(17, 8));
		assertEquals(0, Pagination.currentPage(5_999, 6_000, 3));
		assertEquals(1, Pagination.currentPage(6_000, 6_000, 3));
		assertEquals(2, Pagination.currentPage(12_000, 6_000, 3));
		assertEquals(0, Pagination.currentPage(18_000, 6_000, 3));
		assertEquals(0, Pagination.currentPage(123_456, 6_000, 1));
		assertEquals(8, Pagination.firstIndex(1, 8));
		assertEquals(9, Pagination.endIndex(1, 8, 9));
	}

	@Test
	void marqueeHoldsScrollsAndReturns() {
		// 20 chars in a 12 char window: overflow 8, 100ms per step, 500ms hold
		assertEquals(0, Marquee.startIndex(0, 20, 12, 100, 500));
		assertEquals(0, Marquee.startIndex(499, 20, 12, 100, 500));
		assertEquals(1, Marquee.startIndex(600, 20, 12, 100, 500));
		assertEquals(7, Marquee.startIndex(1_299, 20, 12, 100, 500));
		assertEquals(8, Marquee.startIndex(1_300, 20, 12, 100, 500));
		assertEquals(8, Marquee.startIndex(1_799, 20, 12, 100, 500));
		assertEquals(8, Marquee.startIndex(1_800, 20, 12, 100, 500));
		assertEquals(7, Marquee.startIndex(1_900, 20, 12, 100, 500));
		assertEquals(0, Marquee.startIndex(2_600, 20, 12, 100, 500));
		for (long t = 0; t < 10_000; t += 37) {
			final int index = Marquee.startIndex(t, 20, 12, 100, 500);
			assertTrue(index >= 0 && index <= 8);
		}
		assertEquals(0, Marquee.startIndex(12345, 10, 12, 100, 500), "text that fits never scrolls");
	}

	@Test
	void statusFollowsTheArrivalPhase() {
		assertEquals("Due", DepartureText.status(service(1, 1, NOW + 30_000), NOW));
		assertEquals("2 min", DepartureText.status(service(1, 1, NOW + 61_000), NOW));
		assertEquals("1 min", DepartureText.status(service(1, 1, NOW + 46_000), NOW));
		assertEquals("Boarding", DepartureText.status(service(1, 1, NOW - 1_000, NOW + 20_000, false, 0), NOW));
		assertEquals("Arrived", DepartureText.status(service(1, 1, NOW - 1_000, NOW + 20_000, true, 0), NOW));
		assertEquals("99+ min", DepartureText.status(service(1, 1, NOW + 7_200_000), NOW));
	}

	@Test
	void delayIsFlaggedOnlyForRealtimeLateTrains() {
		assertEquals(0, DepartureText.delayMinutes(service(1, 1, NOW + 60_000, NOW + 90_000, false, 30_000)));
		assertEquals(2, DepartureText.delayMinutes(service(1, 1, NOW + 60_000, NOW + 90_000, false, 110_000)));
		assertEquals(0, DepartureText.delayMinutes(service(1, 1, NOW + 60_000, NOW + 90_000, false, -90_000)));
	}

	@Test
	void callingPagesFitTheBudgetAndJoinWithAnd() {
		assertEquals(List.of(), CallingPages.paginate(List.of(), 40));
		assertEquals(List.of("Calling at Alpha"), CallingPages.paginate(List.of("Alpha"), 40));
		assertEquals(List.of("Calling at Alpha, Beta and Gamma"), CallingPages.paginate(List.of("Alpha", "Beta", "Gamma"), 60));
		final List<String> stops = List.of("Alpha Street", "Beta Park", "Gamma Junction", "Delta Central", "Epsilon Hill", "Zeta Lake");
		final List<String> pages = CallingPages.paginate(stops, 34);
		assertTrue(pages.size() > 1);
		assertTrue(pages.size() <= CallingPages.MAX_PAGES);
		pages.forEach(page -> assertTrue(page.length() <= 34 + 3, page));
		final String all = String.join(" ", pages);
		stops.forEach(stop -> assertTrue(all.contains(stop), stop));
	}
}
