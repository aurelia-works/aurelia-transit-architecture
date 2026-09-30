package com.aureliatransit.architecture.live.announce;

/**
 * What an announcement is about. Lower {@link #priority()} plays first: safety > delay > terminating > normal.
 * Each category is one bit of a speaker's enabled-categories mask.
 *
 * <p>There is deliberately no "through service" category: MTR does not report trains that pass without stopping in its
 * arrival data, so they cannot be detected reliably (see docs/MTR_INTEGRATION.md).
 */
public enum AnnouncementCategory {
	SAFETY(0, "safety", "alert"),
	DELAY(1, "delay", "delay"),
	TERMINATING(2, "terminating", "info"),
	APPROACHING(3, "approaching", "info"),
	STANDING(4, "standing", "info");

	public static final int ALL_MASK = (1 << values().length) - 1;

	private final int priority;
	private final String id;
	private final String chime;

	AnnouncementCategory(int priority, String id, String chime) {
		this.priority = priority;
		this.id = id;
		this.chime = chime;
	}

	public int priority() {
		return priority;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "live.category." + id;
	}

	/**
	 * Which chime ("info", "alert" or "delay") precedes announcements of this category.
	 */
	public String chime() {
		return chime;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public boolean enabledIn(int mask) {
		return (mask & bit()) != 0;
	}

	public static AnnouncementCategory byId(String id) {
		for (final AnnouncementCategory category : values()) {
			if (category.id.equals(id)) {
				return category;
			}
		}
		return null;
	}
}
