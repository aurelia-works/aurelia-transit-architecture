package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * The departures a terminal shows: exactly what a station-wide PIDS shows. Services that have already departed are
 * dropped (same test as {@code BoardBuilder.visible}: {@code departureMillis < now}); the snapshot's order
 * ({@code ServiceOrder}) is preserved.
 */
public final class TerminalDepartures {

	private TerminalDepartures() {
	}

	public static List<ServiceSnapshot> select(StationSnapshot snapshot, long nowMillis, int max) {
		final List<ServiceSnapshot> out = new ArrayList<>(Math.min(Math.max(0, max), snapshot.services().size()));
		for (final ServiceSnapshot service : snapshot.services()) {
			if (out.size() >= max) {
				break;
			}
			if (service.departureMillis() >= nowMillis) {
				out.add(service);
			}
		}
		return List.copyOf(out);
	}
}
