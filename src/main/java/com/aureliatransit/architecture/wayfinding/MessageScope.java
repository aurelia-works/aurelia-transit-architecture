package com.aureliatransit.architecture.wayfinding;

/**
 * Where a service message applies. Declaration order is display priority: a display's own message first, then its
 * station's, then the network's.
 */
public enum MessageScope {
	/** The message typed into one display's own config; never stored on the server's list. */
	DISPLAY,
	STATION,
	NETWORK;

	public static MessageScope byOrdinal(int ordinal) {
		final MessageScope[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NETWORK;
	}
}
