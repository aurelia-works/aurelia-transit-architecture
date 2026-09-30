package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.transit.ServiceSnapshot;

import java.util.List;

final class TestData {

	static final long NOW = 1_000_000_000L;

	private TestData() {
	}

	static ServiceSnapshot service(long platformId, long routeId, long arrival, long departure, boolean terminating, long deviation) {
		return new ServiceSnapshot(routeId, "Line " + routeId, "L" + routeId, 0x3355AA, "Terminus", platformId, String.valueOf(platformId), arrival, departure, deviation,
				deviation != 0, terminating, List.of("Alpha", "Beta", "Terminus"));
	}

	static ServiceSnapshot service(long platformId, long routeId, long arrival) {
		return service(platformId, routeId, arrival, arrival + 30_000, false, 0);
	}
}
