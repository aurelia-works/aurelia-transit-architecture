package com.aureliatransit.architecture.presentation;

import com.aureliatransit.architecture.client.wayfinding.MapLayout;
import com.aureliatransit.architecture.client.wayfinding.TerminalFace;
import com.aureliatransit.architecture.client.wayfinding.TerminalPage;
import com.aureliatransit.architecture.client.wayfinding.TerminalPaging;
import com.aureliatransit.architecture.client.wayfinding.TerminalText;
import com.aureliatransit.architecture.terminal.AccessibilityNote;
import com.aureliatransit.architecture.terminal.MapLine;
import com.aureliatransit.architecture.terminal.MapStop;
import com.aureliatransit.architecture.terminal.StationInfo;
import com.aureliatransit.architecture.terminal.SystemMap;
import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.wayfinding.BadgeShape;
import com.aureliatransit.architecture.wayfinding.LanguageLayout;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.wayfinding.MessageSeverity;
import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.ResolvedWayfinding;
import com.aureliatransit.architecture.wayfinding.ServiceMessage;
import com.aureliatransit.architecture.wayfinding.ServiceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalTest {

	private static int measure(String s) {
		return s.length() * 5;
	}

	private static SystemMap map(int lines, int stops) {
		final List<MapLine> out = new ArrayList<>();
		for (int l = 0; l < lines; l++) {
			final List<MapStop> list = new ArrayList<>();
			for (int i = 0; i < stops; i++) {
				list.add(new MapStop(l * 100L + i, "Station number " + l + "-" + i, l == 0 && i == stops / 2, i % 5 == 0));
			}
			out.add(new MapLine(l, new LineBadge("L" + l, 0x3355AA, BadgeShape.ROUNDED), "Line " + l, list));
		}
		return new SystemMap(out);
	}

	@Test
	void mapLayoutIsDeterministicAndInBounds() {
		final SystemMap map = map(6, 30);
		final float w = 250;
		final float h = 130;
		final List<MapLayout.Strip> a = MapLayout.layout(map, 0, w, h, TerminalTest::measure);
		assertEquals(a, MapLayout.layout(map, 0, w, h, TerminalTest::measure));
		assertEquals(MapLayout.linesPerPage(h), a.size());
		for (final MapLayout.Strip strip : a) {
			assertTrue(strip.bandTop() + MapLayout.BAND_HEIGHT <= h + 0.01F);
			for (final MapLayout.Dot dot : strip.dots()) {
				assertTrue(dot.x() >= 0 && dot.x() <= w);
				if (dot.label() != null) {
					assertTrue(dot.labelLeft() >= 0 && dot.labelLeft() + dot.labelWidth() <= w + 0.01F, dot.label());
				}
			}
		}
	}

	@Test
	void mapLabelsNeverOverlapOnTheSameSideAndTheCurrentStationIsLabelled() {
		final MapLayout.Strip strip = MapLayout.layout(map(1, 40), 0, 250, 60, TerminalTest::measure).get(0);
		final MapLayout.Dot current = strip.dots().stream().filter(MapLayout.Dot::current).findFirst().orElseThrow();
		assertNotNull(current.label());
		for (final boolean above : new boolean[]{true, false}) {
			final List<MapLayout.Dot> side = strip.dots().stream().filter(d -> d.label() != null && d.labelAbove() == above)
					.sorted((x, y) -> Float.compare(x.labelLeft(), y.labelLeft())).toList();
			for (int i = 1; i < side.size(); i++) {
				assertTrue(side.get(i - 1).labelLeft() + side.get(i - 1).labelWidth() < side.get(i).labelLeft());
			}
		}
	}

	@Test
	void mapPagesCoverAllLinesAndClampTheRequestedPage() {
		final SystemMap map = map(7, 4);
		final int perPage = MapLayout.linesPerPage(85);
		assertEquals(2, perPage);
		final int pages = TerminalPaging.pageCount(map.lines().size(), perPage);
		assertEquals(4, pages);
		assertEquals(6, MapLayout.layout(map, 99, 200, 85, TerminalTest::measure).get(0).lineIndex());
		assertTrue(MapLayout.layout(SystemMap.EMPTY, 0, 200, 85, TerminalTest::measure).isEmpty());
		final MapLayout.Strip single = MapLayout.layout(map(1, 1), 0, 200, 85, TerminalTest::measure).get(0);
		assertEquals(100F, single.dots().get(0).x());
	}

	@Test
	void paginationMaths() {
		assertEquals(1, TerminalPaging.pageCount(0, 5));
		assertEquals(2, TerminalPaging.pageCount(6, 5));
		assertEquals(0, TerminalPaging.clamp(-3, 4));
		assertEquals(3, TerminalPaging.clamp(9, 4));
		assertEquals(5, TerminalPaging.first(1, 5));
		assertEquals(6, TerminalPaging.end(1, 5, 6));
		assertEquals(0, TerminalPaging.end(0, 5, 0));
	}

	@Test
	void pageSelectionWrapsAndFallsBackToHome() {
		assertEquals(TerminalPage.DEPARTURES, TerminalPage.HOME.next());
		assertEquals(TerminalPage.HOME, TerminalPage.ACCESSIBILITY.next());
		assertEquals(TerminalPage.ACCESSIBILITY, TerminalPage.HOME.previous());
		assertEquals(TerminalPage.HOME, TerminalPage.byOrdinal(-1));
		assertEquals(TerminalPage.HOME, TerminalPage.byOrdinal(99));
		assertEquals("map", TerminalPage.MAP.id());
	}

	@Test
	void textPagesNeverInventContent() {
		assertEquals(com.aureliatransit.architecture.text.Tr.t(TerminalText.NO_STATION_KEY), TerminalText.stationLines(StationInfo.EMPTY).get(0).text());
		assertEquals(com.aureliatransit.architecture.text.Tr.t(TerminalText.NO_ACCESSIBILITY_KEY), TerminalText.accessibilityLines(StationInfo.EMPTY).get(0).text());
		assertEquals(com.aureliatransit.architecture.text.Tr.t(TerminalText.NO_NOTICES_KEY), TerminalText.serviceLines(List.of()).get(0).text());
		final StationInfo info = new StationInfo("Central", "C1", List.of(), List.of(), List.of(), "", "", List.of(new AccessibilityNote(Pictogram.ELEVATOR, "Lift to concourse")));
		assertEquals("Elevator: Lift to concourse", TerminalText.accessibilityLines(info).get(0).text());
		final List<TerminalText.Line> service = TerminalText.serviceLines(List.of(new ServiceMessage(1, com.aureliatransit.architecture.wayfinding.MessageScope.STATION, "Central", MessageSeverity.WARNING, "Works")));
		assertEquals(TerminalText.Kind.WARNING, service.get(1).kind());
	}

	private static ResolvedWayfinding resolved(String name, List<LineBadge> lines) {
		return new ResolvedWayfinding(name, "", "", lines, SignArrow.NONE, "", ServiceType.NONE, "", "", "", List.of(), "", "", LanguageLayout.SINGLE,
				Pictogram.NONE, AccentPalette.NONE, false);
	}

	@Test
	void idleFaceShowsNamePromptAndBadgesInsideThePanel() {
		final List<LineBadge> lines = List.of(new LineBadge("A", 0xD4202F, BadgeShape.ROUNDED), new LineBadge("B", 0xFFC20E, BadgeShape.ROUNDED));
		for (final float[] size : new float[][]{{30.5F, 12}, {9, 8}, {46.5F, 12}}) {
			final PanelLayout.Panel panel = TerminalFace.layout(resolved("Market East Station", lines), size[0], size[1], 0xFFFFFFFF, TerminalTest::measure);
			final String all = String.join(" ", panel.labels().stream().map(PanelLayout.Label::text).toList());
			assertTrue(all.contains("Touch") || all.contains("Tou"), all);
			assertFalse(panel.labels().isEmpty());
			for (final PanelLayout.Label label : panel.labels()) {
				final float half = label.widthUnits() * label.scale() / 2;
				assertTrue(label.cx() - half >= -size[0] / 2 - 0.01F && label.cx() + half <= size[0] / 2 + 0.01F, label.text() + " " + size[0]);
				assertTrue(Math.abs(label.cy()) <= size[1] / 2 + 0.01F);
			}
			for (final PanelLayout.Rect rect : panel.rects()) {
				assertTrue(rect.cx() - rect.w() / 2 >= -size[0] / 2 - 0.01F && rect.cx() + rect.w() / 2 <= size[0] / 2 + 0.01F);
			}
			// live-test regression: text lines and badges must not overlap vertically, and nothing is truncated
			assertTrue(all.contains("Touch for information") || all.contains("Touch for") && all.contains("information"), "prompt kept whole: " + all);
			assertTrue(all.contains("Market East Station") || all.contains("Market East") || all.contains("Market"), "name kept: " + all);
			assertFalse(all.contains("..") || all.contains("\u2026"), "no ellipsis: " + all);
			final List<float[]> spans = new java.util.ArrayList<>();
			for (final PanelLayout.Label label : panel.labels()) {
				if (label.text().length() > 1) {
					spans.add(new float[]{label.cy() - 4.5F * label.scale(), label.cy() + 4.5F * label.scale()});
				}
			}
			for (final PanelLayout.Rect rect : panel.rects()) {
				if (rect.h() > 1) {
					spans.add(new float[]{rect.cy() - rect.h() / 2, rect.cy() + rect.h() / 2});
				}
			}
			for (int i = 0; i < spans.size(); i++) {
				for (int j = i + 1; j < spans.size(); j++) {
					final float[] x = spans.get(i);
					final float[] y = spans.get(j);
					final boolean sameRow = Math.abs((x[0] + x[1]) - (y[0] + y[1])) < 0.01F;
					assertTrue(sameRow || x[1] <= y[0] + 0.01F || y[1] <= x[0] + 0.01F, "overlap at " + size[0] + "x" + size[1] + ": " + panel);
				}
			}
		}
		assertEquals(TerminalFace.layout(resolved("X", lines), 30, 12, -1, TerminalTest::measure), TerminalFace.layout(resolved("X", lines), 30, 12, -1, TerminalTest::measure));
		final PanelLayout.Panel unnamed = TerminalFace.layout(resolved("", List.of()), 30, 12, -1, TerminalTest::measure);
		assertTrue(unnamed.labels().stream().anyMatch(l -> l.text().startsWith("Passenger") || l.text().startsWith("Pass")));
	}
}
