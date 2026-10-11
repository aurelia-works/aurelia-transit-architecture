package com.aureliatransit.architecture.station;

import java.util.List;

/**
 * Result of {@link StationDescriptionParser#parse}: the resolved spec plus feedback for the player.
 *
 * @param spec       always a valid spec (defaults fill whatever the text did not say)
 * @param understood one short line per recognised phrase, in text order, e.g. "3 tracks", "island platforms", "no roof"
 * @param defaulted  one short line per field taken from defaults, e.g. "length 48 (default)"
 * @param warnings   clamped values, contradictions and unsupported requests, e.g. "length 400 is too long; using 160"
 * @param ignored    words that were not understood (stop words excluded), in text order, without duplicates
 */
public record ParseResult(StationSpec spec, List<String> understood, List<String> defaulted, List<String> warnings, List<String> ignored) {

	public ParseResult {
		understood = List.copyOf(understood);
		defaulted = List.copyOf(defaulted);
		warnings = List.copyOf(warnings);
		ignored = List.copyOf(ignored);
	}
}
