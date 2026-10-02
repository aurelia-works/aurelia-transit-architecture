package com.aureliatransit.architecture.live.display;

import java.util.ArrayList;
import java.util.List;

/**
 * Calling-point times (A14), only where MTR supplies them. MTR's client data has no route timetable (no durations,
 * no full routes), but every {@code ArrivalResponse} carries the route and a departure index that stays the same for
 * one trip along the whole route. A stop's time is therefore the arrival MTR reports at that stop's platform for the
 * same route and departure index; the index repeats after a full cycle, so the earliest arrival not before the
 * previous stop is the one taken. No match means no time: nothing is estimated.
 *
 * <p>Pure; times are local epoch millis, 0 means "unknown".
 */
public final class CallingTimes {

	/** A train further down the line can only be this far ahead of the service at our platform. */
	public static final long MAX_AHEAD_MILLIS = 2 * 60 * 60 * 1000L;
	/** Bound on the extra platforms a display with calling times adds to its arrivals request. */
	public static final int MAX_EXTRA_PLATFORMS = 16;

	/** One arrival as MTR reports it, any platform. */
	public record Arrival(long routeId, long departureIndex, long platformId, long arrivalMillis) {
	}

	private CallingTimes() {
	}

	/**
	 * @param fromMillis      departure of the service at the display's platform
	 * @param stopPlatformIds platform id of each calling point, in route order
	 * @return one time per stop (0 when MTR has no matching arrival); never longer than {@code stopPlatformIds}
	 */
	public static List<Long> match(long routeId, long departureIndex, long fromMillis, List<Long> stopPlatformIds, List<Arrival> arrivals) {
		final List<Long> out = new ArrayList<>(stopPlatformIds.size());
		long previous = fromMillis;
		for (final long platformId : stopPlatformIds) {
			long best = Long.MAX_VALUE;
			for (final Arrival arrival : arrivals) {
				if (arrival.routeId() == routeId && arrival.departureIndex() == departureIndex && arrival.platformId() == platformId
						&& arrival.arrivalMillis() >= previous && arrival.arrivalMillis() - fromMillis <= MAX_AHEAD_MILLIS && arrival.arrivalMillis() < best) {
					best = arrival.arrivalMillis();
				}
			}
			if (best == Long.MAX_VALUE) {
				out.add(0L);
			} else {
				out.add(best);
				previous = best;
			}
		}
		return out;
	}

	/**
	 * Calling-point labels: "Beta (3 min)" where a time is known, the plain name otherwise. A time already passed
	 * shows the plain name too.
	 */
	public static List<String> labels(List<String> stops, List<Long> millis, long nowMillis) {
		final List<String> out = new ArrayList<>(stops.size());
		for (int i = 0; i < stops.size(); i++) {
			final long at = i < millis.size() ? millis.get(i) : 0;
			final long minutes = at <= 0 || at < nowMillis ? -1 : Math.max(1, Math.round((at - nowMillis) / 60_000.0));
			out.add(minutes < 0 ? stops.get(i) : stops.get(i) + " (" + minutes + " min)");
		}
		return out;
	}
}
