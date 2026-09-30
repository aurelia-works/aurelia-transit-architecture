package com.aureliatransit.architecture.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelLayoutTest {

	/** Fake font: six units per character. */
	private static final ToIntFunction<String> M = s -> s.length() * 6;

	private static SignData sign(String primary, String secondary, SignArrow arrow, String platform, AccentPalette accent, TextAlignment align) {
		return new SignData(primary, secondary, align, accent, arrow, platform, false, List.of());
	}

	private static void assertInside(PanelLayout.Panel panel, float w, float h) {
		for (final PanelLayout.Label label : panel.labels()) {
			final float half = label.widthUnits() * label.scale() / 2;
			assertTrue(label.cx() - half >= -w / 2 - 0.01F && label.cx() + half <= w / 2 + 0.01F, "label '" + label.text() + "' overflows horizontally");
			final float halfH = TextFit.ROW * label.scale() / 2;
			assertTrue(label.cy() - halfH >= -h / 2 - 0.01F && label.cy() + halfH <= h / 2 + 0.01F, "label '" + label.text() + "' overflows vertically");
		}
		for (final PanelLayout.Rect rect : panel.rects()) {
			assertTrue(rect.cx() - rect.w() / 2 >= -w / 2 - 0.01F && rect.cx() + rect.w() / 2 <= w / 2 + 0.01F);
			assertTrue(rect.cy() - rect.h() / 2 >= -h / 2 - 0.01F && rect.cy() + rect.h() / 2 <= h / 2 + 0.01F);
		}
	}

	@Test
	void longerNamesGetSmallerText() {
		final PanelLayout.Panel shortName = PanelLayout.sign(sign("Ab", "", SignArrow.NONE, "", AccentPalette.NONE, TextAlignment.CENTER), "Ab", SignStyle.STATION, 13.5F, 8, M);
		final String longText = "Hauptbahnhof Nord Ausgang";
		final PanelLayout.Panel longName = PanelLayout.sign(sign(longText, "", SignArrow.NONE, "", AccentPalette.NONE, TextAlignment.CENTER), longText, SignStyle.STATION, 13.5F, 8, M);
		assertTrue(shortName.labels().get(0).scale() > longName.labels().get(0).scale());
		assertInside(shortName, 13.5F, 8);
		assertInside(longName, 13.5F, 8);
	}

	@Test
	void widerJoinedSignsAllowLargerText() {
		final String text = "Riverside Central";
		final SignData data = sign(text, "", SignArrow.NONE, "", AccentPalette.NONE, TextAlignment.CENTER);
		final float narrow = PanelLayout.sign(data, text, SignStyle.STATION, 13.5F, 8, M).labels().get(0).scale();
		final float wide = PanelLayout.sign(data, text, SignStyle.STATION, 61.5F, 8, M).labels().get(0).scale();
		assertTrue(wide > narrow);
	}

	@Test
	void secondaryLineIsSmallerThanPrimary() {
		final SignData data = sign("Central", "Hauptbahnhof", SignArrow.NONE, "", AccentPalette.NONE, TextAlignment.CENTER);
		final PanelLayout.Panel panel = PanelLayout.sign(data, "Central", SignStyle.STATION, 61.5F, 8, M);
		assertEquals(2, panel.labels().size());
		assertTrue(panel.labels().get(1).scale() < panel.labels().get(0).scale());
		assertTrue(panel.labels().get(1).cy() < panel.labels().get(0).cy());
		assertInside(panel, 61.5F, 8);
	}

	@Test
	void leftArrowsSitLeftAndRightArrowsSitRightOfTheText() {
		final PanelLayout.Panel left = PanelLayout.sign(sign("Exit", "", SignArrow.LEFT, "", AccentPalette.NONE, TextAlignment.CENTER), "Exit", SignStyle.DIRECTION, 29.5F, 6, M);
		final PanelLayout.Panel right = PanelLayout.sign(sign("Exit", "", SignArrow.RIGHT, "", AccentPalette.NONE, TextAlignment.CENTER), "Exit", SignStyle.DIRECTION, 29.5F, 6, M);
		assertTrue(left.labels().get(0).text().equals(SignArrow.LEFT.glyph()) && left.labels().get(0).cx() < left.labels().get(1).cx());
		assertTrue(right.labels().get(0).text().equals(SignArrow.RIGHT.glyph()) && right.labels().get(0).cx() > right.labels().get(1).cx());
		assertInside(left, 29.5F, 6);
		assertInside(right, 29.5F, 6);
	}

	@Test
	void accentDrawsAStripeAndPlatformDrawsABadge() {
		final SignData data = sign("Central", "", SignArrow.NONE, "12", AccentPalette.RED, TextAlignment.LEFT);
		final PanelLayout.Panel panel = PanelLayout.sign(data, "Central", SignStyle.STATION, 29.5F, 8, M);
		assertEquals(2, panel.rects().size());
		assertEquals(AccentPalette.RED.argb(), panel.rects().get(0).argb());
		assertEquals("12", panel.labels().get(0).text());
		assertEquals("Central", panel.labels().get(1).text());
		assertTrue(panel.labels().get(0).cx() < panel.labels().get(1).cx());
		assertInside(panel, 29.5F, 8);
	}

	@Test
	void leftAlignmentStartsAtTheLeftEdge() {
		final SignData data = sign("Hi", "", SignArrow.NONE, "", AccentPalette.NONE, TextAlignment.LEFT);
		final PanelLayout.Panel panel = PanelLayout.sign(data, "Hi", SignStyle.STATION, 61.5F, 8, M);
		final PanelLayout.Label label = panel.labels().get(0);
		assertTrue(label.cx() - label.widthUnits() * label.scale() / 2 < -61.5F / 2 + 1.5F);
	}

	@Test
	void platformNumberPlateShowsALargeNumber() {
		final SignData data = sign("Platform", "", SignArrow.NONE, "7", AccentPalette.NONE, TextAlignment.CENTER);
		final PanelLayout.Panel panel = PanelLayout.sign(data, "Platform", SignStyle.PLATFORM_NUMBER, 8, 7.5F, M);
		assertEquals("7", panel.labels().get(0).text());
		assertTrue(panel.labels().get(0).scale() > panel.labels().get(1).scale());
		assertInside(panel, 8, 7.5F);
	}

	@Test
	void busStopShowsNameAndRouteBadges() {
		final SignData data = new SignData("Market Square", "", TextAlignment.CENTER, AccentPalette.NONE, SignArrow.NONE, "", false,
				List.of(new RouteBadge("12", AccentPalette.ORANGE), new RouteBadge("N7", AccentPalette.PURPLE), new RouteBadge("101", AccentPalette.TEAL)));
		final PanelLayout.Panel panel = PanelLayout.sign(data, "Market Square", SignStyle.BUS_STOP, 13, 9, M);
		assertEquals(3, panel.rects().size());
		assertEquals(4, panel.labels().size());
		assertEquals("Market Square", panel.labels().get(0).text());
		assertTrue(panel.labels().get(0).cy() > panel.labels().get(1).cy());
		assertInside(panel, 13, 9);
	}

	@Test
	void emptySignProducesNothing() {
		assertTrue(PanelLayout.sign(SignData.EMPTY, "", SignStyle.STATION, 13.5F, 8, M).isEmpty());
	}

	@Test
	void infoPanelStacksHeadingAndRowsAndFits() {
		final ConfigurableTextData data = new ConfigurableTextData("Service notice", List.of("Line 1 runs every 10 min", "Closed on Sundays", "", "Ask at the desk"),
				TextAlignment.LEFT, AccentPalette.BLUE);
		final PanelLayout.InfoStyle style = new PanelLayout.InfoStyle(0xFF000000, 0xFF222222, 0.8F, 6);
		final PanelLayout.Panel panel = PanelLayout.info(data, style, 12, 11, M);
		assertEquals(1, panel.rects().size());
		assertEquals(4, panel.labels().size());
		assertEquals("Service notice", panel.labels().get(0).text());
		for (int i = 1; i < panel.labels().size() - 1; i++) {
			assertTrue(panel.labels().get(i).cy() > panel.labels().get(i + 1).cy());
		}
		assertInside(panel, 12, 11);
	}

	@Test
	void infoPanelWithoutAccentHasNoBandAndEmptyDataIsEmpty() {
		final PanelLayout.InfoStyle style = new PanelLayout.InfoStyle(0xFF000000, 0xFF222222, 0.8F, 6);
		final ConfigurableTextData plain = new ConfigurableTextData("Hello", List.of(), TextAlignment.CENTER, AccentPalette.NONE);
		assertTrue(PanelLayout.info(plain, style, 12, 11, M).rects().isEmpty());
		assertFalse(PanelLayout.info(plain, style, 12, 11, M).labels().isEmpty());
		assertTrue(PanelLayout.info(ConfigurableTextData.EMPTY, style, 12, 11, M).isEmpty());
	}

	@Test
	void infoBodyIsLimitedToTheBlocksRows() {
		final PanelLayout.InfoStyle style = new PanelLayout.InfoStyle(0xFF000000, 0xFF222222, 0.8F, 2);
		final ConfigurableTextData data = new ConfigurableTextData("", List.of("a", "b", "c", "d"), TextAlignment.CENTER, AccentPalette.NONE);
		assertEquals(2, PanelLayout.info(data, style, 12, 11, M).labels().size());
	}
}
