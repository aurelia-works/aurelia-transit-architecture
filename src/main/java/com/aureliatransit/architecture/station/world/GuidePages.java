package com.aureliatransit.architecture.station.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * The Station Codex's pages: chapters of pages, a contents page whose lines turn to each chapter, long lists split over pages,
 * and search. Pure (no game classes), so the tests can check every page fits and every link lands; the client's
 * {@code CodexScreen} draws them.
 */
public final class GuidePages {

	/** Text lines a page's block showcase takes at its foot. */
	public static final int SHOWCASE_LINES = 6;

	/** What clicking a line does. Commands that change the world go to the chat box, so a stray click never builds. */
	public enum Click {
		NONE,
		RUN,
		CHAT,
		PAGE
	}

	/**
	 * A paragraph, or one clickable line.
	 *
	 * @param text  what the page shows; may hold line breaks
	 * @param key   true when {@code text} is a lang key, shown translated
	 * @param click what a click does
	 * @param value the command to run or put in chat, or (for {@link Click#PAGE}) the page number
	 * @param hover the tooltip, or null
	 */
	public record Line(String text, boolean key, Click click, String value, String hover) {

		public static Line text(String text) {
			return new Line(text, false, Click.NONE, null, null);
		}

		/** A command shown as itself; clicking runs it. */
		public static Line run(String command, String hover) {
			return new Line(command, false, Click.RUN, command, hover);
		}

		/** A command shown as itself; clicking puts it in the chat box. */
		public static Line chat(String command, String hover) {
			return new Line(command, false, Click.CHAT, command, hover);
		}

		/** Shows {@code label} (translated when {@code key}); clicking puts {@code value} in the chat box. */
		public static Line chat(String label, boolean key, String value, String hover) {
			return new Line(label, key, Click.CHAT, value, hover);
		}
	}

	/**
	 * A page: a bold heading, a blank line, then its lines one under the other.
	 *
	 * @param blocks block ids shown turning at the page's foot (at most 3), or none
	 */
	public record Page(String heading, List<Line> lines, List<String> blocks) {

		public Page(String heading, List<Line> lines) {
			this(heading, lines, List.of());
		}

		public static Page of(String heading, String body) {
			return new Page(heading, List.of(Line.text(body)));
		}

		public static Page of(String heading, Line... lines) {
			return new Page(heading, List.of(lines));
		}

		/** A page that shows {@code blocks} under a short text. */
		public static Page showcase(String heading, String body, String... blocks) {
			return new Page(heading, List.of(Line.text(body)), List.of(blocks));
		}

		/** The page's lines as plain text, one per line (lang keys left as keys), for searching. */
		public String body() {
			return String.join("\n", lines.stream().map(Line::text).toList());
		}
	}

	/** @param icon the item id on the chapter's tab */
	public record Chapter(String title, String icon, List<Page> pages) {

		public Chapter(String title, List<Page> pages) {
			this(title, "minecraft:book", pages);
		}
	}

	private GuidePages() {
	}

	/**
	 * Splits {@code entries} over pages of at most {@code perPage}, each headed {@code heading} ("heading 2/3" once there are
	 * several) with {@code note} as its first line.
	 */
	public static List<Page> list(String heading, String note, List<Line> entries, int perPage) {
		if (perPage < 1) {
			throw new IllegalArgumentException("perPage " + perPage);
		}
		final int count = Math.max(1, (entries.size() + perPage - 1) / perPage);
		final List<Page> pages = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			final List<Line> lines = new ArrayList<>();
			lines.add(Line.text(note));
			lines.addAll(entries.subList(i * perPage, Math.min(entries.size(), (i + 1) * perPage)));
			pages.add(new Page(count == 1 ? heading : heading + " " + (i + 1) + "/" + count, List.copyOf(lines)));
		}
		return List.copyOf(pages);
	}

	/** The 1-based page each chapter starts on, after the one contents page. */
	public static int[] firstPages(List<Chapter> chapters) {
		final int[] first = new int[chapters.size()];
		int page = 2;
		for (int i = 0; i < chapters.size(); i++) {
			first[i] = page;
			page += chapters.get(i).pages().size();
		}
		return first;
	}

	/** The chapter the 0-based {@code page} is in, or -1 for the contents page (or a page past the end). */
	public static int chapterAt(List<Chapter> chapters, int page) {
		final int[] first = firstPages(chapters);
		for (int i = chapters.size() - 1; i >= 0; i--) {
			if (page + 1 >= first[i]) {
				return page + 1 < first[i] + chapters.get(i).pages().size() ? i : -1;
			}
		}
		return -1;
	}

	/** The whole Codex: a contents page with {@code note} and one line per chapter that turns to it, then the chapters. */
	public static List<Page> book(String note, List<Chapter> chapters) {
		final int[] first = firstPages(chapters);
		final List<Line> contents = new ArrayList<>();
		contents.add(Line.text(note));
		for (int i = 0; i < chapters.size(); i++) {
			contents.add(new Line(first[i] + "  " + chapters.get(i).title(), false, Click.PAGE, Integer.toString(first[i]),
					"Page " + first[i]));
		}
		final List<Page> pages = new ArrayList<>();
		pages.add(new Page("Contents", List.copyOf(contents)));
		for (final Chapter chapter : chapters) {
			pages.addAll(chapter.pages());
		}
		return List.copyOf(pages);
	}

	/**
	 * The 0-based pages whose heading or lines hold every word of {@code query}, ignoring case, headings first; at most
	 * {@code limit}. Lang-key lines are searched through {@code translate}, and a line's command counts as its text.
	 */
	public static List<Integer> search(List<Page> pages, String query, UnaryOperator<String> translate, int limit) {
		final String[] words = query.toLowerCase(Locale.ROOT).strip().split("\\s+");
		if (words.length == 0 || words[0].isEmpty()) {
			return List.of();
		}
		final List<Integer> headings = new ArrayList<>();
		final List<Integer> bodies = new ArrayList<>();
		for (int i = 0; i < pages.size(); i++) {
			final Page page = pages.get(i);
			final String heading = page.heading().toLowerCase(Locale.ROOT);
			final StringBuilder all = new StringBuilder(heading);
			for (final Line line : page.lines()) {
				all.append('\n').append(line.key() ? translate.apply(line.text()) : line.text());
				if (line.value() != null && line.click() != Click.PAGE) {
					all.append('\n').append(line.value());
				}
			}
			final String text = all.toString().toLowerCase(Locale.ROOT);
			boolean inHeading = true;
			boolean found = true;
			for (final String word : words) {
				inHeading &= heading.contains(word);
				found &= text.contains(word);
			}
			if (inHeading) {
				headings.add(i);
			} else if (found) {
				bodies.add(i);
			}
		}
		headings.addAll(bodies);
		return List.copyOf(headings.subList(0, Math.min(limit, headings.size())));
	}
}
