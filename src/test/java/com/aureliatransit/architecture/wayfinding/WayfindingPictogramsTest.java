package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.wayfinding.WayfindingLayout.Box;
import com.aureliatransit.architecture.wayfinding.WayfindingLayout.Canvas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pictograms are drawn from rectangles in one colour and never leave the cell they are given. */
class WayfindingPictogramsTest {

	private static final int COLOR = 0xFF112233;
	private static final float EPS = 1e-3F;

	private static PanelLayout.Panel draw(Pictogram pictogram, Box cell) {
		final Canvas canvas = new Canvas(text -> text.length() * 6);
		WayfindingPictograms.draw(canvas, pictogram, cell, COLOR);
		return canvas.panel();
	}

	@Test
	void nonePaintsNothing() {
		final PanelLayout.Panel panel = draw(Pictogram.NONE, new Box(-5, 5, -5, 5));
		assertTrue(panel.rects().isEmpty());
		assertTrue(panel.labels().isEmpty());
	}

	@Test
	void everyPictogramPaintsSomething() {
		for (final Pictogram pictogram : Pictogram.values()) {
			if (pictogram == Pictogram.NONE) {
				continue;
			}
			final PanelLayout.Panel panel = draw(pictogram, new Box(-5, 5, -5, 5));
			assertFalse(panel.rects().isEmpty() && panel.labels().isEmpty(), pictogram + " drew nothing");
		}
	}

	@Test
	void strokesStayInsideTheCellAndShareOneColour() {
		final Box cell = new Box(10, 18, 2, 10);
		for (final Pictogram pictogram : Pictogram.values()) {
			for (final PanelLayout.Rect rect : draw(pictogram, cell).rects()) {
				assertTrue(rect.cx() - rect.w() / 2 >= cell.l() - EPS && rect.cx() + rect.w() / 2 <= cell.r() + EPS, pictogram + " leaves the cell sideways");
				assertTrue(rect.cy() - rect.h() / 2 >= cell.b() - EPS && rect.cy() + rect.h() / 2 <= cell.t() + EPS, pictogram + " leaves the cell vertically");
				assertEquals(COLOR, rect.argb(), pictogram + " must use the text colour only");
			}
		}
	}

	@Test
	void aCellTooSmallToReadPaintsNothing() {
		final PanelLayout.Panel panel = draw(Pictogram.TRAIN, new Box(0, 0.4F, 0, 0.4F));
		assertTrue(panel.rects().isEmpty());
		assertTrue(panel.labels().isEmpty());
	}

	@Test
	void aWideCellDrawsInTheLargestCentredSquare() {
		final Box cell = new Box(0, 40, 0, 8);
		for (final PanelLayout.Rect rect : draw(Pictogram.STAIRS, cell).rects()) {
			assertTrue(rect.cx() - rect.w() / 2 >= 16 - EPS && rect.cx() + rect.w() / 2 <= 24 + EPS, "stairs stay in the central square");
		}
	}
}
