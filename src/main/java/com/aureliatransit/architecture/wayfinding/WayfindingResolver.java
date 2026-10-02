package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;

import java.util.List;
import java.util.Locale;

/**
 * Merges manual configuration with MTR facts. Pure and deterministic: equal input gives an equal
 * {@link ResolvedWayfinding}, so renderers can cache layouts keyed by it.
 *
 * <p>Rules (nothing is ever invented):
 * <ul>
 *     <li>A non-empty manual value always wins.</li>
 *     <li>{@code autoStation} lets MTR fill an empty station name; with a second-language layout selected and no manual
 *     secondary name, the second "|" segment of MTR's own multilingual station name fills the secondary name (it is
 *     MTR data, not a guess; single-language names leave it empty).</li>
 *     <li>{@code autoLines} lets MTR fill an empty line list.</li>
 *     <li>The exit destinations are MTR's destinations of the exit whose label equals the configured exit label
 *     (case-insensitive, surrounding whitespace ignored). No configured exit, or no matching exit, gives none.</li>
 *     <li>Service text: "Local" / "Express" / "Limited" for those types, the custom label for CUSTOM (empty label, empty
 *     text), nothing for NONE. MTR has no stopping-pattern data, so the service type is manual only.</li>
 *     <li>The MTR station's own name is kept beside a manual name, because service messages are keyed by it.</li>
 *     <li>Station code, platform, street label, transfers, destination, arrow, pictogram, accent and language layout are
 *     manual only.</li>
 * </ul>
 */
public final class WayfindingResolver {

	public static final int MAX_EXITS = 8;

	private WayfindingResolver() {
	}

	public static ResolvedWayfinding merge(WayfindingData data, StationFacts facts) {
		final boolean mtrName = data.stationName().isEmpty() && data.autoStation() && facts.station() != null && !facts.station().displayName().isEmpty();
		final String name = mtrName ? TextSanitizer.sanitize(facts.station().displayName(), WayfindingData.MAX_NAME) : data.stationName();
		String secondary = data.secondaryName();
		if (secondary.isEmpty() && mtrName && data.languageLayout() != LanguageLayout.SINGLE) {
			secondary = mtrSecondary(facts.station().rawName(), facts.station().displayName());
		}
		final List<LineBadge> lines = data.lines().isEmpty() && data.autoLines() ? bounded(facts.lines()) : data.lines();
		return new ResolvedWayfinding(name, secondary, data.stationCode(), lines, data.arrow(), data.destination(), data.serviceType(),
				serviceText(data), data.platform(), data.exitLabel(), exitDestinations(data.exitLabel(), facts.exits()), data.streetLabel(), data.transfers(),
				data.languageLayout(), data.pictogram(), data.accent(), mtrName, data.autoStation() ? bounded(facts.exits(), MAX_EXITS) : List.of(),
				data.autoStation() && facts.station() != null ? facts.station().displayName() : "");
	}

	static String serviceText(WayfindingData data) {
		return switch (data.serviceType()) {
			case NONE -> "";
			case LOCAL -> "Local";
			case EXPRESS -> "Express";
			case LIMITED -> "Limited";
			case CUSTOM -> data.serviceLabel();
		};
	}

	static List<String> exitDestinations(String exitLabel, List<ExitInfo> exits) {
		final String wanted = exitLabel.trim().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return List.of();
		}
		for (final ExitInfo exit : exits) {
			if (exit.label().trim().toLowerCase(Locale.ROOT).equals(wanted)) {
				return exit.destinations();
			}
		}
		return List.of();
	}

	/**
	 * The first non-blank segment of a "a|b|c" multilingual MTR name that differs from the displayed one.
	 */
	static String mtrSecondary(String rawName, String display) {
		if (rawName == null || rawName.indexOf('|') < 0) {
			return "";
		}
		for (final String segment : rawName.split("\\|")) {
			final String text = TextSanitizer.sanitize(segment, WayfindingData.MAX_NAME);
			if (!text.isEmpty() && !text.equals(display)) {
				return text;
			}
		}
		return "";
	}

	private static <T> List<T> bounded(List<T> list, int max) {
		return list.size() > max ? list.subList(0, max) : list;
	}

	private static List<LineBadge> bounded(List<LineBadge> lines) {
		return lines.size() > WayfindingData.MAX_LINES ? lines.subList(0, WayfindingData.MAX_LINES) : lines;
	}
}
