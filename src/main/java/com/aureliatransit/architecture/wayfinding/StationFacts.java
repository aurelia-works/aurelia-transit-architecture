package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.transit.StationReference;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What MTR actually knows about the station at a position: the <b>automatic</b> half of the model. Only fields MTR
 * provides are here (station, the lines serving it, its exits). Station codes, service types, street labels and
 * second-language names do not exist in MTR and are never guessed.
 *
 * @param station the MTR station, or null when none resolves
 * @param lines   one badge per MTR route (line) serving the station, deterministic order
 * @param exits   MTR station exits, sorted by label
 */
public record StationFacts(@Nullable StationReference station, List<LineBadge> lines, List<ExitInfo> exits) {

	public static final StationFacts EMPTY = new StationFacts(null, List.of(), List.of());

	public StationFacts {
		lines = List.copyOf(lines);
		exits = List.copyOf(exits);
	}
}
