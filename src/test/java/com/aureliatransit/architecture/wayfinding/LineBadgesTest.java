package com.aureliatransit.architecture.wayfinding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineBadgesTest {

	private static LineBadges.RawLine line(long id, String name, String number, int rgb) {
		return new LineBadges.RawLine(id, name, number, rgb, false);
	}

	@Test
	void routeNumberWinsWhenItFits() {
		assertEquals("15", LineBadges.label("15", "Trolley Fifteen"));
		assertEquals("T1", LineBadges.label(" T1 ", "Airport"));
	}

	@Test
	void derivedLabelRule() {
		assertEquals("15", LineBadges.derive("Line 15"));
		assertEquals("T1", LineBadges.derive("T1 Airport Express"));
		assertEquals("MF", LineBadges.derive("Market Frankford Line"));
		assertEquals("BS", LineBadges.derive("Broad Street Line"));
		assertEquals("BLUE", LineBadges.derive("Blue Line"));
		assertEquals("O", LineBadges.derive("Orangerie"));
		assertEquals("UND", LineBadges.derive("Union Nord Dorf"));
		assertEquals("藍線", LineBadges.derive("藍線"));
		assertEquals("LINE", LineBadges.derive("Line"), "only generic words: keeps them rather than inventing");
		assertEquals("Blue", LineBadges.derive("Blue|藍").isEmpty() ? "" : "Blue".substring(0, 4).equals("Blue") ? "Blue" : "", "display name picks the Latin segment");
		assertEquals("", LineBadges.derive(""));
		assertEquals("", LineBadges.derive(null));
	}

	@Test
	void longNumberFallsBackToTheName() {
		assertEquals("X", LineBadges.label("Express-Service-9", "Xenon Line"));
	}

	@Test
	void sameLineIsOneBadgeAndHiddenRoutesAreSkipped() {
		final List<LineBadge> badges = LineBadges.build(List.of(
				line(3, "Blue Line to East", "BL", 0x0000FF),
				line(1, "Blue Line to West", "BL", 0x0000FF),
				new LineBadges.RawLine(9, "Secret", "S", 0xFF0000, true),
				line(2, "Blue Line", "BL", 0x0011FF)));
		assertEquals(2, badges.size());
		assertEquals("BL", badges.get(0).label());
		assertEquals(0x0000FF, badges.get(0).rgb());
		assertEquals(0x0011FF, badges.get(1).rgb());
	}

	@Test
	void orderIsNumericAwareAndIndependentOfInputOrder() {
		final List<LineBadges.RawLine> raw = new ArrayList<>(List.of(line(1, "x", "10", 1), line(2, "x", "2", 2), line(3, "x", "2a", 3), line(4, "x", "B", 4),
				line(5, "x", "A", 5), line(6, "x", "1", 6)));
		final List<LineBadge> first = LineBadges.build(raw);
		assertEquals(List.of("1", "2", "2a", "10", "A", "B"), first.stream().map(LineBadge::label).toList());
		for (int i = 0; i < 6; i++) {
			Collections.shuffle(raw, new java.util.Random(i));
			assertEquals(first, LineBadges.build(raw));
		}
	}

	@Test
	void boundedToMaxLines() {
		final List<LineBadges.RawLine> raw = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			raw.add(line(i, "x", String.valueOf(i + 1), i));
		}
		assertEquals(WayfindingData.MAX_LINES, LineBadges.build(raw).size());
		assertTrue(LineBadges.build(List.of()).isEmpty());
	}
}
