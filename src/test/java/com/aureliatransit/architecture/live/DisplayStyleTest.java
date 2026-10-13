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
	void savedBoardsKeepTheirPlaceAndOldIdsStillResolve() {
		// styles are saved by ordinal: the three released in 1.4 and 1.5 keep their places, each now a real board
		assertEquals(DisplayStyle.UK_DOT_MATRIX, DisplayStyle.byOrdinal(0));
		assertEquals(DisplayStyle.METRO_COUNTDOWN, DisplayStyle.byOrdinal(1));
		assertEquals(DisplayStyle.DUTCH_NS, DisplayStyle.byOrdinal(2));
		assertEquals(DisplayStyle.GERMAN_DB, DisplayStyle.byOrdinal(3));
		assertEquals(DisplayStyle.METRO_COUNTDOWN, DisplayStyle.byOrdinal(99));
		assertEquals(4, DisplayStyle.values().length);
		final Set<String> ids = new HashSet<>();
		for (final DisplayStyle style : DisplayStyle.values()) {
			assertTrue(ids.add(style.id()), style.id());
			assertEquals(style, DisplayConfig.styleById(style.id()));
		}
		// former ids (saved in plans and build jobs) show the board that replaced them
		assertEquals(DisplayStyle.UK_DOT_MATRIX, DisplayConfig.styleById("european_amber"));
		assertEquals(DisplayStyle.METRO_COUNTDOWN, DisplayConfig.styleById("european_modern"));
		assertEquals(DisplayStyle.DUTCH_NS, DisplayConfig.styleById("dutch_modern"));
		assertEquals(DisplayStyle.GERMAN_DB, DisplayConfig.styleById("german_mainline"));
		assertEquals(null, DisplayConfig.styleById("nonsense"));
	}

	@Test
	void britishTrainsAreCountedInOrdinals() {
		assertEquals("1st 2nd 3rd 4th 11th 12th 13th 21st 22nd", String.join(" ", DisplayStyle.ordinal(1), DisplayStyle.ordinal(2),
				DisplayStyle.ordinal(3), DisplayStyle.ordinal(4), DisplayStyle.ordinal(11), DisplayStyle.ordinal(12), DisplayStyle.ordinal(13),
				DisplayStyle.ordinal(21), DisplayStyle.ordinal(22)));
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
		assertEquals(DisplayStyle.UK_DOT_MATRIX.id(), screenText("4 tracks st pancras station building 64 long"));
		assertEquals(DisplayStyle.GERMAN_DB.id(), screenText("4 tracks hamburg station building 64 long"));
		assertEquals(DisplayStyle.DUTCH_NS.id(), screenText("4 tracks utrecht station building 64 long"));
		assertEquals(DisplayStyle.METRO_COUNTDOWN.id(), screenText("2 tracks island 64 long footbridge"));
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
