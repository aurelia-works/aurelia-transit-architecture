package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.live.cache.ServiceOrder;
import com.aureliatransit.architecture.live.display.DepartureText;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * The departures the terminal shows. Same rule as a station-wide PIDS: services that have not left yet, in the one
 * {@link ServiceOrder}. PENDING INTEGRATION: replace {@link #select} by {@code TerminalDepartures.select} once it lands.
 */
public final class TerminalFeed {

	/** A display-ready departure row. */
	public record Row(String route, int routeRgb, String destination, String platform, String status, boolean delayed) {
	}

	private TerminalFeed() {
	}

	public static List<ServiceSnapshot> select(StationSnapshot snapshot, long nowMillis, int max) {
		final List<ServiceSnapshot> out = new ArrayList<>();
		for (final ServiceSnapshot s : snapshot.services()) {
			if (s.departureMillis() >= nowMillis) {
				out.add(s);
			}
		}
		out.sort(ServiceOrder.COMPARATOR);
		return out.size() > max ? new ArrayList<>(out.subList(0, Math.max(0, max))) : out;
	}

	public static List<Row> rows(List<ServiceSnapshot> services, long nowMillis) {
		final List<Row> rows = new ArrayList<>(services.size());
		for (final ServiceSnapshot s : services) {
			final String route = s.routeNumber().isBlank() ? s.routeName() : s.routeNumber();
			rows.add(new Row(route.length() > 4 ? route.substring(0, 4) : route, s.routeColor() & 0xFFFFFF, s.destination(), s.platformName(),
					DepartureText.status(s, nowMillis), DepartureText.delayMinutes(s) > 0));
		}
		return rows;
	}
}
