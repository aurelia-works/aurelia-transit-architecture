package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.wayfinding.LineBadge;

import java.util.List;

/**
 * One line of the schematic map: its badge and its stations in route order (from MTR route data; never invented).
 */
public record MapLine(long id, LineBadge badge, String name, List<MapStop> stops) {

	public MapLine {
		stops = List.copyOf(stops);
	}
}
