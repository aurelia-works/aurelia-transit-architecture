package com.aureliatransit.architecture.transit;

import java.util.List;

/**
 * One upcoming stopping service at a platform, copied out of MTR's arrival data.
 * All times are epoch millis in the <b>local client clock</b> (MTR's server offset is already applied).
 *
 * @param deviationMillis positive when late, negative when early; only meaningful if {@code realtime}
 * @param callingAt       display names of the remaining stops after this platform, bounded to {@link #MAX_CALLING_AT}
 * @param departureIndex  MTR's index of this trip's departure (the same at every stop of the trip; repeats per cycle)
 * @param callingAtMillis local arrival time at each calling point where MTR supplied one (0 = unknown); empty when the
 *                        display did not ask for calling times
 * @param origin          display name of the first stop of the train's route (MTR's route data), or empty; used by
 *                        arrivals boards ("from ...")
 */
public record ServiceSnapshot(
		long routeId,
		String routeName,
		String routeNumber,
		int routeColor,
		String destination,
		long platformId,
		String platformName,
		long arrivalMillis,
		long departureMillis,
		long deviationMillis,
		boolean realtime,
		boolean terminating,
		List<String> callingAt,
		long departureIndex,
		List<Long> callingAtMillis,
		String origin
) {

	public static final int MAX_CALLING_AT = 16;

	public ServiceSnapshot {
		callingAt = List.copyOf(callingAt.size() > MAX_CALLING_AT ? callingAt.subList(0, MAX_CALLING_AT) : callingAt);
		origin = origin == null ? "" : origin;
		callingAtMillis = callingAtMillis == null ? List.of() : List.copyOf(callingAtMillis.size() > callingAt.size() ? callingAtMillis.subList(0, callingAt.size()) : callingAtMillis);
	}

	/** Without trip index and calling times (1.3 shape). */
	public ServiceSnapshot(long routeId, String routeName, String routeNumber, int routeColor, String destination, long platformId, String platformName,
						   long arrivalMillis, long departureMillis, long deviationMillis, boolean realtime, boolean terminating, List<String> callingAt) {
		this(routeId, routeName, routeNumber, routeColor, destination, platformId, platformName, arrivalMillis, departureMillis, deviationMillis, realtime,
				terminating, callingAt, 0, List.of(), "");
	}

	public long secondsUntilArrival(long nowMillis) {
		return Math.max(0, (arrivalMillis - nowMillis) / 1000);
	}

	public boolean isStanding(long nowMillis) {
		return arrivalMillis <= nowMillis && nowMillis < departureMillis;
	}
}
