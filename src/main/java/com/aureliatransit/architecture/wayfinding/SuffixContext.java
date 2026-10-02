package com.aureliatransit.architecture.wayfinding;

import java.util.Locale;

/**
 * Where a station suffix ({@link StationSuffixes}) may be shown. Each station turns contexts on or off separately, so
 * "Aurelia Airport" can read in full on entrance signs while departure boards keep the short "Aurelia".
 */
public enum SuffixContext {
	/** Wayfinding signs, pylons, station information boards. */
	SIGNS,
	/** PIDS, CIS and concourse boards: the station header and train destinations. */
	DISPLAYS,
	/** Passenger information terminals, kiosks and e-paper bus boards. */
	TERMINALS,
	/** Announcement text (and the voice-pack station fragment key is unaffected). */
	ANNOUNCEMENTS;

	public static final int DEFAULT_MASK = bit(SIGNS) | bit(TERMINALS);

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static int bit(SuffixContext context) {
		return 1 << context.ordinal();
	}

	public static SuffixContext byId(String id) {
		for (final SuffixContext context : values()) {
			if (context.id().equals(id)) {
				return context;
			}
		}
		return null;
	}
}
