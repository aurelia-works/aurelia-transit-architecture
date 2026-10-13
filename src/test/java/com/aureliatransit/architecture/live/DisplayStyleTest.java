package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Display style presets, row numbering and the 1.6 defaults of a new display. */
class DisplayStyleTest {

	@Test
	void savedBoardsKeepTheirStyle() {
		// styles are saved by ordinal: the first three never move
		assertEquals(DisplayStyle.EUROPEAN_AMBER, DisplayStyle.byOrdinal(0));
		assertEquals(DisplayStyle.EUROPEAN_MODERN, DisplayStyle.byOrdinal(1));
		assertEquals(DisplayStyle.DUTCH_MODERN, DisplayStyle.byOrdinal(2));
		final Set<String> ids = new HashSet<>();
		for (final DisplayStyle style : DisplayStyle.values()) {
			assertTrue(ids.add(style.id()), style.id());
			assertEquals(style, DisplayConfig.styleById(style.id()));
		}
		assertTrue(DisplayStyle.values().length >= 11);
	}

	@Test
	void rowsAreNumberedTheBoardsWay() {
		assertEquals("", DisplayStyle.Numbering.NONE.prefix(1));
		assertEquals("1 ", DisplayStyle.Numbering.NUMBER.prefix(1));
		assertEquals("1st 2nd 3rd 4th 11th 12th 13th 21st 22nd ", DisplayStyle.Numbering.ORDINAL.prefix(1) + DisplayStyle.Numbering.ORDINAL.prefix(2)
				+ DisplayStyle.Numbering.ORDINAL.prefix(3) + DisplayStyle.Numbering.ORDINAL.prefix(4) + DisplayStyle.Numbering.ORDINAL.prefix(11)
				+ DisplayStyle.Numbering.ORDINAL.prefix(12) + DisplayStyle.Numbering.ORDINAL.prefix(13) + DisplayStyle.Numbering.ORDINAL.prefix(21)
				+ DisplayStyle.Numbering.ORDINAL.prefix(22));
		assertEquals("LONDON", DisplayStyle.NEW_YORK_COUNTDOWN.cased("London"));
		assertEquals("London", DisplayStyle.UK_LCD.cased("London"));
	}

	@Test
	void newDisplaysShowTheClockAndCallingPointsWithTimes() {
		for (final DisplayKind kind : DisplayKind.values()) {
			final DisplayConfig config = DisplayConfig.defaults(kind);
			assertTrue(config.clock(), kind.name());
			final boolean platform = kind != DisplayKind.CONCOURSE;
			assertEquals(platform, config.callingAt(), kind.name());
			assertEquals(platform, config.callingTimes(), kind.name());
		}
	}

	@Test
	void plannedScreensCarryTheLookOfTheirStation() {
		assertEquals(DisplayStyle.UK_LCD.id(), screenText("4 tracks st pancras station building 64 long"));
		assertEquals(DisplayStyle.HAMBURG_METRO.id(), screenText("4 tracks hamburg station building 64 long"));
		assertEquals(DisplayStyle.EUROPEAN_MODERN.id(), screenText("2 tracks island 64 long footbridge"));
	}

	private static String screenText(String description) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(description).spec());
		final Set<String> texts = new HashSet<>();
		plan.blocks().stream().filter(b -> b.blockId().endsWith("platform_pids") || b.blockId().endsWith("concourse_board")).forEach(b -> texts.add(b.text()));
		assertEquals(1, texts.size(), description + " " + texts);
		final String text = texts.iterator().next();
		assertFalse(text.isEmpty());
		return text;
	}
}
