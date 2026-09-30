package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.live.display.DepartureText;
import com.aureliatransit.architecture.terminal.TerminalDepartures;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * The departures the terminal shows (the shared {@link TerminalDepartures} rule, identical to a station-wide PIDS) and
 * their display rows.
 */
public final class TerminalFeed {

	/** A display-ready departure row. */
	public record Row(String route, int routeRgb, String destination, String platform, String status, boolean delayed) {
	}

	private TerminalFeed() {
	}

	/**
	 * The terminal's departures: exactly {@link TerminalDepartures#select}, the station-wide PIDS rule.
	 */
	public static List<ServiceSnapshot> select(StationSnapshot snapshot, long nowMillis, int max) {
		return TerminalDepartures.select(snapshot, nowMillis, max);
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
