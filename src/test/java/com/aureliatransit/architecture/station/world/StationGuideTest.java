package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.RealStation;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.world.GuidePages.Click;
import com.aureliatransit.architecture.station.world.GuidePages.Line;
import com.aureliatransit.architecture.station.world.GuidePages.Page;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

	private static JsonObject lang() throws IOException {
		try (InputStream in = StationGuideTest.class.getResourceAsStream("/assets/aurelia_transit_architecture/lang/en_us.json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	/** Every line of the book, the contents included. */
	private static List<Line> allLines() {
		return StationGuide.PAGES.stream().flatMap(page -> page.lines().stream()).toList();
	}

	@Test
	void everyPageFitsABookPage() throws IOException {
		final JsonObject lang = lang();
		assertTrue(StationGuide.TITLE.length() <= 32, "book titles are at most 32 characters");
		assertTrue(StationGuide.PAGES.size() <= 100);
		for (final Page page : StationGuide.PAGES) {
			int lines = lines(page.heading(), true) + 1; // the blank line after the heading
			for (final Line line : page.lines()) {
				assertTrue(!line.key() || lang.has(line.text()), "no English for " + line.text());
				final String shown = line.key() ? lang.get(line.text()).getAsString() : line.text();
				for (final String paragraph : shown.split("\n", -1)) {
					lines += lines(paragraph, false);
				}
			}
			final int room = page.blocks().isEmpty() ? PAGE_LINES : PAGE_LINES - GuidePages.SHOWCASE_LINES;
			assertTrue(lines <= room, "\"" + page.heading() + "\" needs " + lines + " lines");
		}
	}

	@Test
	void theGuideNamesTheRealCommands() {
		final String all = StationGuide.PAGES.stream().map(Page::body).reduce("", String::concat);
		for (final String command : new String[]{"/ata_station plan", "/ata_station confirm", "/ata_station cancel", "/ata_station undo",
				"/ata_station status", "/ata_guide"}) {
			assertTrue(all.contains(command), command);
		}
	}

	/** Every literal path under /ata_station ("confirm replace_terrain"), from the command tree the game registers. */
	private static void paths(CommandNode<ServerCommandSource> node, String prefix, List<String> out) {
		for (final CommandNode<ServerCommandSource> child : node.getChildren()) {
			if (child instanceof LiteralCommandNode<ServerCommandSource> literal) {
				final String path = prefix + " " + literal.getLiteral();
				out.add(path);
				paths(child, path, out);
			}
		}
	}

	@Test
	void theCommandPagesListEveryRegisteredCommand() {
		final CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
		StationCommand.register(dispatcher);
		final List<String> registered = new ArrayList<>();
		paths(dispatcher.getRoot().getChild("ata_station"), "/ata_station", registered);
		assertTrue(registered.size() >= 13, "found " + registered);
		final List<String> shown = allLines().stream().map(Line::text).toList();
		for (final String path : registered) {
			assertTrue(shown.stream().anyMatch(text -> text.equals(path) || text.startsWith(path + " <")), "the book leaves out " + path);
		}
	}

	@Test
	void everyPresetAndRealStationIsListed() {
		final List<Line> lines = allLines();
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			assertTrue(lines.stream().anyMatch(line -> line.key() && line.text().equals(StationGuide.PRESET_KEY + preset.key())
					&& line.value().equals(StationGuide.PLAN + preset.description())), "no preset " + preset.key());
		}
		for (final RealStation station : RealStation.values()) {
			if (station != RealStation.NONE) {
				assertTrue(lines.stream().anyMatch(line -> line.click() == Click.CHAT && line.value().equals(StationGuide.PLAN + station.phrase())),
						"no real station " + station);
			}
		}
	}

	@Test
	void everyClickLandsSomewhere() {
		final List<Page> pages = StationGuide.PAGES;
		int jumps = 0;
		for (final Line line : allLines()) {
			if (line.click() == Click.NONE) {
				continue;
			}
			assertNotNull(line.value(), line.text());
			assertNotNull(line.hover(), "no tooltip on " + line.text());
			switch (line.click()) {
				case PAGE -> {
					final int page = Integer.parseInt(line.value());
					assertTrue(page >= 2 && page <= pages.size(), line.text());
					jumps++;
				}
				// the chat box holds 256 characters
				case RUN, CHAT -> assertTrue(line.value().length() <= 256, line.value());
				default -> {
				}
			}
		}
		assertEquals(StationGuide.CHAPTERS.size(), jumps, "one contents line per chapter");
		final int[] first = GuidePages.firstPages(StationGuide.CHAPTERS);
		for (int i = 0; i < first.length; i++) {
			assertEquals(StationGuide.CHAPTERS.get(i).pages().get(0), pages.get(first[i] - 1), StationGuide.CHAPTERS.get(i).title());
		}
	}

	@Test
	void everyIconAndShowcasedBlockExists() throws IOException {
		final JsonObject lang = lang();
		final List<String> ids = new ArrayList<>();
		StationGuide.CHAPTERS.forEach(chapter -> ids.add(chapter.icon()));
		StationGuide.PAGES.forEach(page -> {
			assertTrue(page.blocks().size() <= 3, page.heading() + " shows more than 3 blocks");
			ids.addAll(page.blocks());
		});
		for (final String id : ids) {
			if (id.startsWith("aurelia_transit_architecture:")) {
				final String name = id.substring(id.indexOf(':') + 1);
				assertTrue(lang.has("block.aurelia_transit_architecture." + name) || lang.has("item.aurelia_transit_architecture." + name), id);
			} else {
				assertTrue(id.startsWith("minecraft:"), id);
			}
		}
	}
}
