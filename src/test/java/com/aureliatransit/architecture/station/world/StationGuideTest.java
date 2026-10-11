package com.aureliatransit.architecture.station.world;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guide's pages fit a written book page: 14 lines of 114 pixels in the vanilla font, wrapped at spaces. Glyph widths are the
 * vanilla ones (advance including the 1 px gap; bold adds 1), so a page that passes here is not cut off in game.
 */
class StationGuideTest {

	private static final int PAGE_WIDTH = 114;
	private static final int PAGE_LINES = 14;
	private static final Map<Character, Integer> NARROW = Map.ofEntries(Map.entry(' ', 4), Map.entry('i', 2), Map.entry('l', 3),
			Map.entry('t', 4), Map.entry('I', 4), Map.entry('f', 5), Map.entry('k', 5), Map.entry('.', 2), Map.entry(',', 2), Map.entry(':', 2),
			Map.entry(';', 2), Map.entry('!', 2), Map.entry('\'', 2), Map.entry('"', 4), Map.entry('(', 4), Map.entry(')', 4), Map.entry('<', 5),
			Map.entry('>', 5), Map.entry('[', 4), Map.entry(']', 4), Map.entry('`', 3), Map.entry('|', 2), Map.entry('@', 7), Map.entry('~', 7));

	private static int width(String text, boolean bold) {
		int width = 0;
		for (final char c : text.toCharArray()) {
			width += NARROW.getOrDefault(c, 6) + (bold ? 1 : 0);
		}
		return width;
	}

	/** Lines a paragraph takes when wrapped at spaces; a word wider than the page is split like vanilla does. */
	private static int lines(String paragraph, boolean bold) {
		if (paragraph.isEmpty()) {
			return 1;
		}
		int lines = 1;
		int line = 0;
		for (final String word : paragraph.split(" ")) {
			final int w = width(word, bold);
			final int needed = line == 0 ? w : line + width(" ", bold) + w;
			if (needed <= PAGE_WIDTH) {
				line = needed;
			} else {
				lines++;
				line = w;
				while (line > PAGE_WIDTH) {
					lines++;
					line -= PAGE_WIDTH;
				}
			}
		}
		return lines;
	}

	@Test
	void everyPageFitsABookPage() {
		assertTrue(StationGuide.TITLE.length() <= 32, "book titles are at most 32 characters");
		assertTrue(StationGuide.PAGES.size() <= 100);
		for (final StationGuide.Page page : StationGuide.PAGES) {
			int lines = lines(page.heading(), true) + 1; // the blank line after the heading
			for (final String paragraph : page.body().split("\n", -1)) {
				lines += lines(paragraph, false);
			}
			assertTrue(lines <= PAGE_LINES, "\"" + page.heading() + "\" needs " + lines + " lines");
		}
	}

	@Test
	void theGuideNamesTheRealCommands() {
		final String all = StationGuide.PAGES.stream().map(StationGuide.Page::body).reduce("", String::concat);
		for (final String command : new String[]{"/ata_station plan", "/ata_station confirm", "/ata_station cancel", "/ata_station undo",
				"/ata_station status", "/ata_guide"}) {
			assertTrue(all.contains(command), command);
		}
	}
}
