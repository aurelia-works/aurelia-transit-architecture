package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.wayfinding.ExitInfo;
import com.aureliatransit.architecture.wayfinding.LineBadge;

import java.util.List;

/**
 * The STATION and ACCESSIBILITY pages' content: MTR facts (platforms, lines, exits) merged with the terminal's
 * wayfinding metadata (code, transfers, street/landmark) and nearby ATA accessibility metadata. Empty = not known.
 */
public record StationInfo(
		String stationName,
		String stationCode,
		List<PlatformReference> platforms,
		List<LineBadge> lines,
		List<ExitInfo> exits,
		String transfers,
		String streetLabel,
		List<AccessibilityNote> accessibility
) {

	public static final StationInfo EMPTY = new StationInfo("", "", List.of(), List.of(), List.of(), "", "", List.of());

	public StationInfo {
		platforms = List.copyOf(platforms);
		lines = List.copyOf(lines);
		exits = List.copyOf(exits);
		accessibility = List.copyOf(accessibility);
	}
}
