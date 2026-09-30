package com.aureliatransit.architecture.wayfinding;

/**
 * Stopping pattern of a service. MTR has no such concept, so this is always manual configuration; NONE shows nothing.
 */
public enum ServiceType {
	/** Not shown. */
	NONE,
	/** All stops. */
	LOCAL,
	/** Skips stops. */
	EXPRESS,
	/** Limited stops. */
	LIMITED,
	/** The data's custom service label. */
	CUSTOM;

	public static ServiceType byOrdinal(int ordinal) {
		final ServiceType[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
