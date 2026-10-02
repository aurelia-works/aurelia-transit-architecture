package com.aureliatransit.architecture.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionLayoutTest {

	private static int measure(String s) {
		return s.length() * 6;
	}

	@Test
	void parsesCarsMarksAndUnitBreaks() {
		final List<CompositionLayout.Car> cars = CompositionLayout.parse(" 1+  2 | 3! 4+! ");
		assertEquals(List.of("1", "2", "3", "4"), cars.stream().map(CompositionLayout.Car::label).toList());
		assertTrue(cars.get(0).first() && !cars.get(0).accessible());
		assertTrue(cars.get(2).accessible() && cars.get(2).unitBreakBefore());
		assertTrue(cars.get(3).first() && cars.get(3).accessible());
		assertFalse(cars.get(1).unitBreakBefore());
	}

	@Test
	void oddInputDrawsNothingOrIsBounded() {
		assertTrue(CompositionLayout.parse("| | + !").isEmpty());
		assertTrue(CompositionLayout.layout("", "A B", 30, 10, 0, CompositionLayoutTest::measure).labels().isEmpty(), "no cars: nothing invented");
		assertEquals(CompositionLayout.MAX_CARS, CompositionLayout.parse("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18").size());
		assertTrue(CompositionLayout.layout("1 2", "", 0.5F, 10, 0, CompositionLayoutTest::measure).rects().isEmpty());
	}

	@Test
	void carsAndSectorsStayInsideThePanel() {
		final float w = 46.5F;
		final float h = 11;
		final PanelLayout.Panel panel = CompositionLayout.layout("1+ 2 3 | 4 5 6!", "A B C D", w, h, 0xFFAAAAAA, CompositionLayoutTest::measure);
		assertEquals(6 + 2, panel.rects().size(), "six cars and two class/access bands");
		assertEquals(6 + 4, panel.labels().size());
		for (final PanelLayout.Rect r : panel.rects()) {
			assertTrue(r.cx() - r.w() / 2 >= -w / 2 - 0.01F && r.cx() + r.w() / 2 <= w / 2 + 0.01F, "car outside");
		}
		for (final PanelLayout.Label l : panel.labels()) {
			assertTrue(Math.abs(l.cx()) + l.widthUnits() * l.scale() / 2 <= w / 2 + 0.01F, l.text() + " overflows");
			assertTrue(l.scale() > 0 && !Float.isNaN(l.scale()));
		}
	}

	@Test
	void warningSignFallsBackToItsDefaultsOnly() {
		final SignData empty = SignData.EMPTY;
		final PanelLayout.Panel panel = SignPanels.layout(empty, "", SignStyle.WARNING, -1, 16, 10, CompositionLayoutTest::measure);
		final List<String> texts = panel.labels().stream().map(PanelLayout.Label::text).toList();
		assertTrue(texts.contains("Stand back") && texts.contains("Non-stopping trains"), texts.toString());
		final SignData typed = new SignData("Keep clear", "Fast trains", TextAlignment.CENTER, AccentPalette.NONE, SignArrow.NONE, "", false, List.of());
		final List<String> typedTexts = SignPanels.layout(typed, "Keep clear", SignStyle.WARNING, -1, 16, 10, CompositionLayoutTest::measure).labels().stream()
				.map(PanelLayout.Label::text).toList();
		assertTrue(typedTexts.contains("Keep clear") && typedTexts.contains("Fast trains") && !typedTexts.contains("Stand back"), typedTexts.toString());
	}
}
