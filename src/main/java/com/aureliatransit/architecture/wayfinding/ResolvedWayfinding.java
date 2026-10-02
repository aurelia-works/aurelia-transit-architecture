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
 * @param allExits         every exit of the station as MTR defines it (empty without MTR); used by the exit summary board
 * @param mtrStationName   display name of the MTR station the block resolves (even when a manual name is shown), or empty
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
		boolean stationFromMtr,
		List<ExitInfo> allExits,
		String mtrStationName
) {

	public ResolvedWayfinding {
		lines = List.copyOf(lines);
		exitDestinations = List.copyOf(exitDestinations);
		allExits = List.copyOf(allExits);
		mtrStationName = mtrStationName == null ? "" : mtrStationName;
	}

	/** Without the MTR station name (no MTR station resolved). */
	public ResolvedWayfinding(String stationName, String secondaryName, String stationCode, List<LineBadge> lines, SignArrow arrow, String destination,
							  ServiceType serviceType, String serviceText, String platform, String exitLabel, List<String> exitDestinations, String streetLabel,
							  String transfers, LanguageLayout languageLayout, Pictogram pictogram, AccentPalette accent, boolean stationFromMtr,
							  List<ExitInfo> allExits) {
		this(stationName, secondaryName, stationCode, lines, arrow, destination, serviceType, serviceText, platform, exitLabel, exitDestinations, streetLabel,
				transfers, languageLayout, pictogram, accent, stationFromMtr, allExits, "");
	}

	/** The station name service messages are looked up by ({@link ServiceMessages#messageStation}). */
	public String messageStation() {
		return ServiceMessages.messageStation(mtrStationName, stationName);
	}

	/** A resolution without the station's full exit list. */
	public ResolvedWayfinding(String stationName, String secondaryName, String stationCode, List<LineBadge> lines, SignArrow arrow, String destination,
							  ServiceType serviceType, String serviceText, String platform, String exitLabel, List<String> exitDestinations, String streetLabel,
							  String transfers, LanguageLayout languageLayout, Pictogram pictogram, AccentPalette accent, boolean stationFromMtr) {
		this(stationName, secondaryName, stationCode, lines, arrow, destination, serviceType, serviceText, platform, exitLabel, exitDestinations, streetLabel,
				transfers, languageLayout, pictogram, accent, stationFromMtr, List.of());
	}
}
