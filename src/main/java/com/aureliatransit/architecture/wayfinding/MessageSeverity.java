package com.aureliatransit.architecture.wayfinding;

import java.util.Locale;

/**
 * How serious a service message is; drives the colour of the strip boards draw it in. Ordinals are persisted, so new
 * levels are only ever appended.
 */
public enum MessageSeverity {
	/** Plain information. */
	INFO("info"),
	/** Something passengers should know (delays, works). Called "notice" in commands; "warning" is accepted as an alias. */
	WARNING("notice"),
	/** Service is disrupted or suspended. */
	DISRUPTION("disruption"),
	/** Severe disruption: shown ahead of everything else of the same scope. */
	SEVERE("severe");

	private final String id;

	MessageSeverity(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static MessageSeverity byOrdinal(int ordinal) {
		final MessageSeverity[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INFO;
	}

	/** Parses a command id (case-insensitive, "warning" accepted for "notice"); null when unknown. */
	public static MessageSeverity byId(String id) {
		final String lower = id.toLowerCase(Locale.ROOT);
		for (final MessageSeverity severity : values()) {
			if (severity.id.equals(lower)) {
				return severity;
			}
		}
		return lower.equals("warning") ? WARNING : null;
	}
}
