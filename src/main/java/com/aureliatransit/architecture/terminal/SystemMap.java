package com.aureliatransit.architecture.terminal;

import java.util.List;

/**
 * Lightweight schematic network map for the passenger terminal: lines as ordered station strips, the current station
 * flagged. Immutable and deterministic for equal MTR data, so screens cache their drawing keyed by it.
 *
 * @param lines lines serving the current station first, then the others; each bounded ({@link #MAX_LINES}, {@link #MAX_STOPS})
 */
public record SystemMap(List<MapLine> lines) {

	public static final int MAX_LINES = 24;
	public static final int MAX_STOPS = 48;
	public static final SystemMap EMPTY = new SystemMap(List.of());

	public SystemMap {
		lines = List.copyOf(lines);
	}
}
