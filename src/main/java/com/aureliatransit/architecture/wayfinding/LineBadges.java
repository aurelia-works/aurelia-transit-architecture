package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;
import com.aureliatransit.architecture.transit.StationNames;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the routes MTR reports for a station into line badges. Pure (no MTR classes): the MTR-backed source feeds it
 * {@link RawLine}s.
 *
 * <h2>Label rule</h2>
 * <ol>
 *     <li>MTR's route number, when it has one (at most {@value LineBadge#MAX_LABEL} characters).</li>
 *     <li>Otherwise a short label <em>derived from the route name</em> (never invented): the first word that has a digit
 *     and is at most four characters ("Line 15" gives "15", "T1 Airport" gives "T1"); else the initials of the name's
 *     words after dropping generic words (line, route, service, lines), up to four ("Market Frankford" gives "MF");
 *     a single word of up to four characters is kept upper-cased; a longer single word contributes its first letter.</li>
 * </ol>
 * One badge per line: routes with the same label and colour collapse into one. Order: numeric-aware by label, then
 * colour, then route id. Bounded to {@link WayfindingData#MAX_LINES}.
 */
public final class LineBadges {

	private static final Set<String> GENERIC = Set.of("line", "lines", "route", "service");

	/**
	 * @param id     MTR route id (tie-breaker only)
	 * @param number MTR route number, may be null or blank
	 * @param hidden true when MTR marks the route hidden (only known when MTR's full route is available client-side)
	 */
	public record RawLine(long id, String name, String number, int rgb, boolean hidden) {
	}

	private record Labelled(String label, int rgb, long id) {
	}

	private LineBadges() {
	}

	public static List<LineBadge> build(List<RawLine> raw) {
		final List<Labelled> labelled = new ArrayList<>(raw.size());
		for (final RawLine line : raw) {
			if (line.hidden()) {
				continue;
			}
			final String label = label(line.number(), line.name());
			if (!label.isEmpty()) {
				labelled.add(new Labelled(label, line.rgb() & 0xFFFFFF, line.id()));
			}
		}
		labelled.sort((a, b) -> {
			final int byLabel = compareLabels(a.label(), b.label());
			if (byLabel != 0) {
				return byLabel;
			}
			final int byColour = Integer.compare(a.rgb(), b.rgb());
			return byColour != 0 ? byColour : Long.compare(a.id(), b.id());
		});
		final List<LineBadge> out = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		for (final Labelled line : labelled) {
			if (out.size() < WayfindingData.MAX_LINES && seen.add(line.label() + "#" + line.rgb())) {
				out.add(new LineBadge(line.label(), line.rgb(), BadgeShape.ROUNDED));
			}
		}
		return List.copyOf(out);
	}

	/**
	 * MTR's route number when it fits a badge, else the label derived from the route name.
	 */
	public static String label(String number, String name) {
		final String n = number == null ? "" : TextSanitizer.sanitize(number, 16);
		if (!n.isEmpty() && n.length() <= LineBadge.MAX_LABEL) {
			return n;
		}
		return derive(name == null || name.isBlank() ? n : name);
	}

	/**
	 * The derived-label rule, see the class comment.
	 */
	public static String derive(String routeName) {
		final String display = TextSanitizer.sanitize(StationNames.display(routeName), 48);
		if (display.isEmpty()) {
			return "";
		}
		final String[] words = display.split("\\s+");
		for (final String word : words) {
			if (word.length() <= LineBadge.MAX_LABEL && word.chars().anyMatch(Character::isDigit)) {
				return word;
			}
		}
		final List<String> kept = new ArrayList<>();
		for (final String word : words) {
			if (!GENERIC.contains(word.toLowerCase(Locale.ROOT))) {
				kept.add(word);
			}
		}
		final List<String> use = kept.isEmpty() ? List.of(words) : kept;
		if (use.size() == 1) {
			final String word = use.get(0);
			return word.length() <= LineBadge.MAX_LABEL ? word.toUpperCase(Locale.ROOT) : firstLetter(word);
		}
		final StringBuilder initials = new StringBuilder();
		for (final String word : use) {
			if (initials.length() < LineBadge.MAX_LABEL) {
				initials.append(firstLetter(word));
			}
		}
		return TextSanitizer.sanitize(initials.toString(), LineBadge.MAX_LABEL);
	}

	private static String firstLetter(String word) {
		return new String(Character.toChars(word.codePointAt(0))).toUpperCase(Locale.ROOT);
	}

	/**
	 * Numeric-aware: "2" before "10", "2" before "2a", otherwise case-insensitive.
	 */
	public static int compareLabels(String a, String b) {
		final int na = leadingNumber(a);
		final int nb = leadingNumber(b);
		if (na >= 0 && nb >= 0 && na != nb) {
			return Integer.compare(na, nb);
		}
		final int byText = String.CASE_INSENSITIVE_ORDER.compare(a, b);
		return byText != 0 ? byText : a.compareTo(b);
	}

	private static int leadingNumber(String s) {
		int end = 0;
		while (end < s.length() && end < 6 && Character.isDigit(s.charAt(end))) {
			end++;
		}
		return end == 0 ? -1 : Integer.parseInt(s.substring(0, end));
	}
}
