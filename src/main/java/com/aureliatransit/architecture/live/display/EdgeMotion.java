package com.aureliatransit.architecture.live.display;

import com.aureliatransit.architecture.transit.ServiceSnapshot;

import java.util.List;

/**
 * Motion of a train-keyed edge part (1.4): 0 = rest (bars up, step in), 1 = deployed (bars down, step out). Moves at
 * a constant rate toward the target, so a train arriving or leaving animates over {@link #TRAVEL_MILLIS}. Pure and
 * frame-rate independent; the clock going backwards snaps to the target.
 */
public final class EdgeMotion {

	public static final long TRAVEL_MILLIS = 1_000;
	/** How often a block asks whether a train is standing (the provider itself refreshes every 2 s). */
	public static final long CHECK_MILLIS = 1_000;

	private float progress;
	private long lastMillis = Long.MIN_VALUE;

	public float progress() {
		return progress;
	}

	/** Advances to {@code nowMillis} toward {@code deployed} and returns the new progress. */
	public float update(long nowMillis, boolean deployed) {
		final float target = deployed ? 1 : 0;
		if (lastMillis == Long.MIN_VALUE || nowMillis < lastMillis) {
			progress = lastMillis == Long.MIN_VALUE ? progress : target;
			lastMillis = nowMillis;
			return progress;
		}
		final float step = (nowMillis - lastMillis) / (float) TRAVEL_MILLIS;
		lastMillis = nowMillis;
		progress = progress < target ? Math.min(target, progress + step) : Math.max(target, progress - step);
		return progress;
	}

	/** True when a train is standing (arrived, not yet departed) at {@code platformId}; nothing nearby means false. */
	public static boolean trainStanding(List<ServiceSnapshot> services, long platformId, long nowMillis) {
		if (platformId == 0) {
			return false;
		}
		for (final ServiceSnapshot service : services) {
			if (service.platformId() == platformId && service.isStanding(nowMillis)) {
				return true;
			}
		}
		return false;
	}
}
