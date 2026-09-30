package com.aureliatransit.architecture.live.cache;

/**
 * Holds MTR's server-to-client clock offset steady.
 *
 * <p>MTR re-measures the offset on every arrivals response as "server time when sent minus client time when received",
 * so it moves by network and tick jitter each time. Arrival times converted with a jittering offset and rounded to whole
 * seconds flip between neighbouring seconds, which changed snapshot content (and could swap rows or minute texts) with
 * no change in the actual timetable. The held offset only follows a new sample once it differs by more than
 * {@link #TOLERANCE_MILLIS}. Pure logic; client thread only.
 */
public final class ServerClockOffset {

	public static final long TOLERANCE_MILLIS = 1_000;

	private long held;
	private boolean hasValue;

	public long stabilize(long sample) {
		if (!hasValue || Math.abs(sample - held) > TOLERANCE_MILLIS) {
			held = sample;
			hasValue = true;
		}
		return held;
	}

	/**
	 * A server epoch time as local client millis, rounded down to a whole second.
	 */
	public static long toLocalSecond(long serverMillis, long offset) {
		return Math.floorDiv(serverMillis - offset, 1000) * 1000;
	}

	public void reset() {
		hasValue = false;
		held = 0;
	}
}
