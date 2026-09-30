package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.SignArrow;

import java.util.List;

/**
 * Display-ready merge of {@link WayfindingData} (manual) and {@link StationFacts} (MTR). Immutable and value-equal, so
 * renderers cache their layout keyed by it and rebuild only when it changes. Empty strings mean "show nothing".
 *
 * @param serviceText      resolved service label ("Local", "Express", custom) or empty
 * @param exitDestinations destinations of the configured exit (MTR when available, otherwise empty)
 * @param stationFromMtr   true when the station name came from MTR (consumers may mark auto content)
 */
public record ResolvedWayfinding(
		String stationName,
		String secondaryName,
		String stationCode,
		List<LineBadge> lines,
		SignArrow arrow,
		String destination,
		ServiceType serviceType,
		String serviceText,
		String platform,
		String exitLabel,
		List<String> exitDestinations,
		String streetLabel,
		String transfers,
		LanguageLayout languageLayout,
		Pictogram pictogram,
		AccentPalette accent,
		boolean stationFromMtr
) {

	public ResolvedWayfinding {
		lines = List.copyOf(lines);
		exitDestinations = List.copyOf(exitDestinations);
	}
}
