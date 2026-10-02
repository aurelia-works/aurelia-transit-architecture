package com.aureliatransit.architecture.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiftPanelLayoutTest {

	private static int measure(String s) {
		return s.length() * 6;
	}

	@Test
	void eachStatusHasItsOwnBarColour() {
		final int a = LiftPanelLayout.status(0).fill();
		final int b = LiftPanelLayout.status(1).fill();
		final int c = LiftPanelLayout.status(2).fill();
		assertNotEquals(a, b);
		assertNotEquals(b, c);
		assertNotEquals(a, c);
		assertEquals(a, LiftPanelLayout.status(99).fill(), "unknown status falls back to in service");
	}

	@Test
	void showsNameLevelsAndStatusInsideThePanel() {
		final float w = 12;
		final float h = 11;
		final PanelLayout.Panel panel = LiftPanelLayout.layout("Lift A", "Street - Concourse - Platforms 1-4", LiftPanelLayout.status(1), w, h, 0xFFFFFFFF,
				0xFFAAAAAA, LiftPanelLayoutTest::measure);
		final List<String> texts = panel.labels().stream().map(PanelLayout.Label::text).toList();
		assertTrue(texts.contains("Lift A") && texts.contains("Street - Concourse - Platforms 1-4"), texts.toString());
		assertEquals(3, panel.labels().size());
		assertEquals(LiftPanelLayout.OUT_OF_SERVICE, panel.rects().get(0).argb());
		for (final PanelLayout.Label label : panel.labels()) {
			final float half = label.widthUnits() * label.scale() / 2;
			assertTrue(label.cx() - half >= -w / 2 - 0.01F && label.cx() + half <= w / 2 + 0.01F, label.text() + " overflows");
			assertTrue(Math.abs(label.cy()) <= h / 2, label.text() + " outside");
		}
	}

	@Test
	void emptyNameReadsLiftAndTinyPanelsDrawNothing() {
		final PanelLayout.Panel panel = LiftPanelLayout.layout("", "", LiftPanelLayout.status(0), 12, 11, 0xFFFFFFFF, 0xFFAAAAAA, LiftPanelLayoutTest::measure);
		assertEquals(2, panel.labels().size(), "name and status");
		assertTrue(LiftPanelLayout.layout("Lift", "", LiftPanelLayout.status(0), 1, 1, 0, 0, LiftPanelLayoutTest::measure).labels().isEmpty());
	}

	@Test
	void layoutIsDeterministic() {
		assertEquals(LiftPanelLayout.layout("Lift", "Street", LiftPanelLayout.status(2), 12, 11, 1, 2, LiftPanelLayoutTest::measure),
				LiftPanelLayout.layout("Lift", "Street", LiftPanelLayout.status(2), 12, 11, 1, 2, LiftPanelLayoutTest::measure));
	}
}
