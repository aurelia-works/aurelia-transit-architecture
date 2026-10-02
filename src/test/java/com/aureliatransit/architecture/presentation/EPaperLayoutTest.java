package com.aureliatransit.architecture.presentation;

import com.aureliatransit.architecture.client.wayfinding.EPaperLayout;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EPaperLayoutTest {

	private static final long NOW = 1_000_000_000L;

	private static int measure(String s) {
		return s.length() * 6;
	}

	private static ServiceSnapshot service(long route, long arrival, String destination) {
		return new ServiceSnapshot(route, "Route " + route, "R" + route, 0xFF0000, destination, 1, "1", arrival, arrival + 30_000, 0, false, false, List.of());
	}

	@Test
	void refreshIntervalStaysInTheEpaperWindowAndStaggers() {
		final java.util.Set<Long> seen = new java.util.HashSet<>();
		for (long seed = -500; seed < 500; seed++) {
			final long interval = EPaperLayout.refreshIntervalMillis(seed);
			assertTrue(interval >= EPaperLayout.MIN_REFRESH_MILLIS && interval <= EPaperLayout.MAX_REFRESH_MILLIS, "interval " + interval);
			seen.add(interval);
		}
		assertTrue(seen.size() > 50, "boards should not all refresh together");
		assertEquals(EPaperLayout.refreshIntervalMillis(42), EPaperLayout.refreshIntervalMillis(42));
	}

	@Test
	void dueOnlyAfterTheDeadlineOrWhenTheClockJumpsBack() {
		assertFalse(EPaperLayout.due(1_000, 20_000));
		assertTrue(EPaperLayout.due(20_000, 20_000));
		assertTrue(EPaperLayout.due(0, 10_000_000));
	}

	@Test
	void rowsDropDepartedServicesSortAndCap() {
		final List<ServiceSnapshot> services = new ArrayList<>(List.of(
				service(3, NOW + 300_000, "Gamma"),
				service(1, NOW - 120_000, "Gone"),
				service(2, NOW + 60_000, "Beta"),
				service(4, NOW + 600_000, "Delta")));
		final List<EPaperLayout.Row> rows = EPaperLayout.rows(services, NOW, 2);
		assertEquals(2, rows.size());
		assertEquals("Beta", rows.get(0).destination());
		assertEquals("Gamma", rows.get(1).destination());
		assertEquals("R2", rows.get(0).route());
		assertEquals("1 min", rows.get(0).status());
		assertTrue(EPaperLayout.rows(List.of(), NOW, 4).isEmpty());
	}

	@Test
	void rowCapacityFollowsPanelHeight() {
		assertEquals(0, EPaperLayout.maxRows(EPaperLayout.HEADER_HEIGHT));
		assertEquals(2, EPaperLayout.maxRows(12));
		assertEquals(3, EPaperLayout.maxRows(14.7F));
		assertEquals(EPaperLayout.MAX_ROWS, EPaperLayout.maxRows(200));
	}

	@Test
	void idleStatesShowTheMessageAndNoRows() {
		final PanelLayout.Panel none = EPaperLayout.layout("Market", List.of(), "No departures currently available", 46.5F, 14.7F, EPaperLayoutTest::measure);
		final String shown = String.join(" ", none.labels().stream().skip(1).map(PanelLayout.Label::text).toList());
		assertTrue(shown.contains("No departures") && shown.contains("available"), shown);
		final PanelLayout.Panel unlinked = EPaperLayout.layout("", List.of(), "No stop linked", 46.5F, 14.7F, EPaperLayoutTest::measure);
		assertTrue(unlinked.labels().stream().anyMatch(l -> l.text().equals("No stop linked")));
	}

	@Test
	void everythingStaysInsideThePanelEvenWithLongText() {
		final float w = 30;
		final float h = 14.7F;
		final List<EPaperLayout.Row> rows = List.of(
				new EPaperLayout.Row("R15", "A very long destination name that cannot possibly fit", "99+ min"),
				new EPaperLayout.Row("R2", "Short", "Due"),
				new EPaperLayout.Row("X", "Third", "Boarding"),
				new EPaperLayout.Row("Y", "Fourth stop", "5 min"));
		final PanelLayout.Panel panel = EPaperLayout.layout("An extremely long stop name for a narrow board", rows, "", w, h, EPaperLayoutTest::measure);
		for (final PanelLayout.Label label : panel.labels()) {
			final float half = label.widthUnits() * label.scale() / 2;
			assertTrue(label.cx() - half >= -w / 2 - 0.01F && label.cx() + half <= w / 2 + 0.01F, "label '" + label.text() + "' overflows horizontally");
			assertTrue(label.cy() <= h / 2 && label.cy() >= -h / 2, "label '" + label.text() + "' outside vertically");
		}
		assertEquals(EPaperLayout.maxRows(h, w), panel.labels().stream().filter(l -> l.argb() == EPaperLayout.INK && l.cx() > 0 && l.text().matches("Due|Boarding|5 min|99\\+ min")).count());
	}

	@Test
	void textScalesWithPanelWidth() {
		// 1.3.1: panel widths of 1-, 2- and 3-wide boards (16n - join margin)
		final float one = 14.5F;
		final float two = 30.5F;
		final float three = 46.5F;
		assertEquals(EPaperLayout.MIN_TEXT_FACTOR, EPaperLayout.textFactor(one));
		assertTrue(EPaperLayout.textFactor(two) > EPaperLayout.textFactor(one) && EPaperLayout.textFactor(two) < 1F);
		assertEquals(1F, EPaperLayout.textFactor(three), "three blocks and wider keep the 1.3.0 size");
		assertEquals(EPaperLayout.maxRows(14.7F), EPaperLayout.maxRows(14.7F, three));
		assertTrue(EPaperLayout.maxRows(14.7F, one) >= EPaperLayout.maxRows(14.7F, three), "smaller text never shows fewer rows");

		for (final float w : new float[]{one, two, three}) {
			// a one-block board fits a short destination beside "3 min"; from two blocks also beside the long "Arrived"
			final List<EPaperLayout.Row> rows = w == one ? List.of(new EPaperLayout.Row("12", "Alpha", "3 min"))
					: List.of(new EPaperLayout.Row("12", "Alpha", "Arrived"));
			final PanelLayout.Panel panel = EPaperLayout.layout("Alpha", rows, "", w, 14.7F, EPaperLayoutTest::measure);
			assertTrue(panel.labels().stream().anyMatch(l -> l.text().equals("12")), w + ": route shown in full");
			assertTrue(panel.labels().stream().anyMatch(l -> l.text().equals("Alpha") && l.argb() == EPaperLayout.INK), w + ": destination shown in full");
			assertTrue(panel.labels().stream().anyMatch(l -> l.text().equals(rows.get(0).status())), w + ": time shown");
			for (final PanelLayout.Label label : panel.labels()) {
				final float half = label.widthUnits() * label.scale() / 2;
				assertTrue(label.cx() - half >= -w / 2 - 0.01F && label.cx() + half <= w / 2 + 0.01F, w + ": '" + label.text() + "' overflows");
			}
		}
	}

	@Test
	void layoutIsDeterministic() {
		final List<EPaperLayout.Row> rows = List.of(new EPaperLayout.Row("R1", "Beta", "3 min"));
		assertEquals(EPaperLayout.layout("Stop", rows, "", 30, 14.7F, EPaperLayoutTest::measure),
				EPaperLayout.layout("Stop", rows, "", 30, 14.7F, EPaperLayoutTest::measure));
	}
}
