package com.aureliatransit.architecture.client.wayfinding.logic;

import com.aureliatransit.architecture.wayfinding.StationSuffixes;
import com.aureliatransit.architecture.wayfinding.SuffixContext;

/**
 * The latest server-synced {@link StationSuffixes} on this client. Written from the network handler, read by display
 * and sign builders, so it is a single volatile reference to an immutable value.
 */
public final class ClientStationSuffixes {

	private static volatile StationSuffixes current = StationSuffixes.EMPTY;

	private ClientStationSuffixes() {
	}

	public static void set(StationSuffixes suffixes) {
		current = suffixes == null ? StationSuffixes.EMPTY : suffixes;
	}

	public static void clear() {
		current = StationSuffixes.EMPTY;
	}

	public static StationSuffixes get() {
		return current;
	}

	/** The station name as it should read in {@code context} ({@link StationSuffixes#apply}). */
	public static String apply(String name, SuffixContext context) {
		return current.apply(name, context);
	}
}
