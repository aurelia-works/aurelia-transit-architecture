package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.world.GuidePages.Chapter;
import com.aureliatransit.architecture.station.world.GuidePages.Click;
import com.aureliatransit.architecture.station.world.GuidePages.Line;
import com.aureliatransit.architecture.station.world.GuidePages.Page;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuidePagesTest {

	private static List<Line> entries(int count) {
		return IntStream.range(0, count).mapToObj(i -> Line.text("entry " + i)).toList();
	}

	@Test
	void aShortListIsOnePageWithoutANumber() {
		final List<Page> pages = GuidePages.list("Presets", "Click one.", entries(3), 10);
		assertEquals(1, pages.size());
		assertEquals("Presets", pages.get(0).heading());
		assertEquals(List.of("Click one.", "entry 0", "entry 1", "entry 2"), pages.get(0).lines().stream().map(Line::text).toList());
	}

	@Test
	void aLongListSplitsInOrderWithTheNoteOnEveryPage() {
		final List<Page> pages = GuidePages.list("Presets", "Click one.", entries(23), 10);
		assertEquals(List.of("Presets 1/3", "Presets 2/3", "Presets 3/3"), pages.stream().map(Page::heading).toList());
		assertEquals(List.of(11, 11, 4), pages.stream().map(page -> page.lines().size()).toList());
		assertEquals("Click one.", pages.get(2).lines().get(0).text());
		assertEquals("entry 20", pages.get(2).lines().get(1).text());
	}

	@Test
	void anEmptyListStillHasItsPageAndABadSizeIsRefused() {
		assertEquals(1, GuidePages.list("Presets", "None yet.", List.of(), 10).size());
		assertThrows(IllegalArgumentException.class, () -> GuidePages.list("Presets", "x", entries(2), 0));
	}

	@Test
	void theContentsTurnsToEachChapterAfterIt() {
		final Page a = Page.of("A", "a"), b1 = Page.of("B1", "b"), b2 = Page.of("B2", "b"), c = Page.of("C", "c");
		final List<Chapter> chapters = List.of(new Chapter("Alpha", List.of(a)), new Chapter("Beta", List.of(b1, b2)),
				new Chapter("Gamma", List.of(c)));
		assertArrayEquals(new int[]{2, 3, 5}, GuidePages.firstPages(chapters));
		final List<Page> book = GuidePages.book("Pick one.", chapters);
		assertEquals(List.of("Contents", "A", "B1", "B2", "C"), book.stream().map(Page::heading).toList());
		final List<Line> contents = book.get(0).lines();
		assertEquals("Pick one.", contents.get(0).text());
		assertEquals(Click.NONE, contents.get(0).click());
		assertEquals(List.of("2  Alpha", "3  Beta", "5  Gamma"), contents.subList(1, 4).stream().map(Line::text).toList());
		assertEquals(List.of("2", "3", "5"), contents.subList(1, 4).stream().map(Line::value).toList());
		contents.subList(1, 4).forEach(line -> assertEquals(Click.PAGE, line.click()));
	}

	@Test
	void bodyJoinsTheLinesForSearching() {
		assertEquals("one\n/ata_guide", Page.of("H", Line.text("one"), Line.run("/ata_guide", "again")).body());
	}

	@Test
	void chapterAtFindsTheChapterOfAPage() {
		final List<Chapter> chapters = List.of(new Chapter("Alpha", List.of(Page.of("A", "a"))),
				new Chapter("Beta", List.of(Page.of("B1", "b"), Page.of("B2", "b"))));
		assertEquals(-1, GuidePages.chapterAt(chapters, 0));
		assertEquals(0, GuidePages.chapterAt(chapters, 1));
		assertEquals(1, GuidePages.chapterAt(chapters, 2));
		assertEquals(1, GuidePages.chapterAt(chapters, 3));
		assertEquals(-1, GuidePages.chapterAt(chapters, 4));
	}

	@Test
	void searchNeedsEveryWordAndPutsHeadingsFirst() {
		final List<Page> pages = List.of(Page.of("Roofs", "canopy or train shed"), Page.of("Train shed", "arched"),
				Page.of("Presets", Line.chat("key.utrecht", true, "/ata_station plan utrecht centraal", "x")));
		assertEquals(List.of(1, 0), GuidePages.search(pages, "Train SHED", key -> key, 10));
		assertEquals(List.of(0), GuidePages.search(pages, "canopy shed", key -> key, 10));
		assertEquals(List.of(2), GuidePages.search(pages, "Utrecht Centraal", key -> "Utrecht", 10));
		assertEquals(List.of(1), GuidePages.search(pages, "shed", key -> key, 1));
		assertEquals(List.of(), GuidePages.search(pages, "  ", key -> key, 10));
		assertEquals(List.of(), GuidePages.search(pages, "tram", key -> key, 10));
	}

	@Test
	void aShowcasePageKeepsItsBlocks() {
		final Page page = Page.showcase("Platforms", "edges", "a:b", "a:c");
		assertEquals(List.of("a:b", "a:c"), page.blocks());
		assertEquals(List.of(), Page.of("Plain", "text").blocks());
	}
}
