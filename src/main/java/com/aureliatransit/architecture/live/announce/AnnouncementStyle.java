package com.aureliatransit.architecture.live.announce;

/**
 * How a speaker words its announcements. STANDARD is the original wording; BRITISH follows the familiar UK platform
 * pattern ("The next train to arrive at platform 3 will be ..."), original English text with no operator wording.
 */
public enum AnnouncementStyle {
	STANDARD("standard"),
	BRITISH("british");

	private final String id;

	AnnouncementStyle(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "announce_style." + id;
	}

	public AnnouncementStyle next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/**
	 * The style with this id, or STANDARD for an unknown or missing one (older speakers keep their wording).
	 */
	public static AnnouncementStyle byId(String id) {
		for (final AnnouncementStyle style : values()) {
			if (style.id.equals(id)) {
				return style;
			}
		}
		return STANDARD;
	}

	public static AnnouncementStyle byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STANDARD;
	}
}
