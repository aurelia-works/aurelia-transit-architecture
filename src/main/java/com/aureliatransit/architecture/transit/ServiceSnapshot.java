package com.aureliatransit.architecture.transit;

import java.util.List;

/**
 * One upcoming stopping service at a platform, copied out of MTR's arrival data.
 * All times are epoch millis in the <b>local client clock</b> (MTR's server offset is already applied).
 *
 * @param deviationMillis positive when late, negative when early; only meaningful if {@code realtime}
 * @param callingAt       display names of the remaining stops after this platform, bounded to {@link #MAX_CALLING_AT}
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
		List<String> callingAt
) {

	public static final int MAX_CALLING_AT = 16;

	public ServiceSnapshot {
		callingAt = List.copyOf(callingAt.size() > MAX_CALLING_AT ? callingAt.subList(0, MAX_CALLING_AT) : callingAt);
	}

	public long secondsUntilArrival(long nowMillis) {
		return Math.max(0, (arrivalMillis - nowMillis) / 1000);
	}

	public boolean isStanding(long nowMillis) {
		return arrivalMillis <= nowMillis && nowMillis < departureMillis;
	}
}
