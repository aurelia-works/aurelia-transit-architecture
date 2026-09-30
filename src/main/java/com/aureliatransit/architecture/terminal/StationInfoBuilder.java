package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.wayfinding.ResolvedWayfinding;
import com.aureliatransit.architecture.wayfinding.StationFacts;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingResolver;

import java.util.List;

/**
 * Pure merge for the terminal's STATION and ACCESSIBILITY pages. The wayfinding rules apply (manual station name, code,
 * transfers and street label win; MTR fills the station name and lines when the data allows); exits are MTR's and
 * platforms come from the station snapshot. With no station, no platforms and no manual content the result equals
 * {@link StationInfo#EMPTY}.
 */
public final class StationInfoBuilder {

	private StationInfoBuilder() {
	}

	public static StationInfo build(StationFacts facts, List<PlatformReference> platforms, WayfindingData data, List<AccessibilityNote> accessibility) {
		final ResolvedWayfinding r = WayfindingResolver.merge(data, facts);
		return new StationInfo(r.stationName(), r.stationCode(), platforms, r.lines(), facts.exits(), r.transfers(), r.streetLabel(), accessibility);
	}
}
