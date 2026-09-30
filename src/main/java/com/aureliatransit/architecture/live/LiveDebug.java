package com.aureliatransit.architecture.live;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight debug counters for the live systems. Disabled by default (one boolean check per event); enable with
 * {@code -Daurelia.live.debug=true} or at runtime with {@code /aurelia_live debug true}.
 * Counters are only touched from the client thread.
 */
public final class LiveDebug {

	public enum Counter {
		PROVIDER_REFRESH,
		ARRIVAL_REQUESTS,
		BOARD_REBUILDS,
		BOARD_FRAMES,
		ENGINE_UPDATES,
		EVENTS_DETECTED,
		EVENTS_DROPPED,
		ANNOUNCEMENTS_PLAYED,
		ANNOUNCEMENTS_FALLBACK
	}

	private static boolean enabled = Boolean.getBoolean("aurelia.live.debug");
	private static final long[] COUNTS = new long[Counter.values().length];

	private LiveDebug() {
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static void count(Counter counter) {
		if (enabled) {
			COUNTS[counter.ordinal()]++;
		}
	}

	public static void reset() {
		java.util.Arrays.fill(COUNTS, 0);
	}

	public static Map<Counter, Long> snapshot() {
		final Map<Counter, Long> out = new LinkedHashMap<>();
		for (final Counter counter : Counter.values()) {
			out.put(counter, COUNTS[counter.ordinal()]);
		}
		return out;
	}
}
