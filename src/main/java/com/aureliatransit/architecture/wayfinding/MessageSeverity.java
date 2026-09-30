package com.aureliatransit.architecture.wayfinding;

import java.util.Locale;

/**
 * How serious a service message is; drives the colour of the strip boards draw it in.
 */
public enum MessageSeverity {
	/** Plain information. */
	INFO,
	/** Something passengers should know (delays, works). */
	WARNING,
	/** Service is disrupted or suspended. */
	DISRUPTION;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static MessageSeverity byOrdinal(int ordinal) {
		final MessageSeverity[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INFO;
	}
}
