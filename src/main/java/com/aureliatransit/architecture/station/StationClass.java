package com.aureliatransit.architecture.station;

import java.util.Locale;

/**
 * How big a place the station serves, from a request-side halt to a main station. Station Studio's class control and the words
 * "halt class", "regional class", "town class", "city class" and "grand class" set it. A class fills in everything the player has
 * not said (tracks, length, platform layout, building and its size, roof, look); anything said, by a typed word or a pinned control,
 * wins. {@link #AUTO} is "not asked": the plan then reports the class its numbers amount to ({@link #of(StationSpec)}).
 *
 * <p>The words are separate from the older size words ("halt", "regional", "town", "city", "grand" on their own), which keep their
 * meaning as building-size words so saved descriptions plan as before.
 */
public enum StationClass {
	AUTO("Auto"), HALT("Halt"), REGIONAL("Regional"), TOWN("Town"), CITY("City"), GRAND("Grand");

	private final String label;

	StationClass(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/** The lower case name the studio sends and receives; "auto" for {@link #AUTO}. */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The class a request names; anything unknown, blank or null is {@link #AUTO}. */
	public static StationClass of(String text) {
		if (text == null) {
			return AUTO;
		}
		for (final StationClass value : values()) {
			if (value.key().equals(text.strip().toLowerCase(Locale.ROOT))) {
				return value;
			}
		}
		return AUTO;
	}

	/**
	 * The class the spec asks for, or, for {@link #AUTO}, the one its plan amounts to: no building and no more than two tracks is a
	 * halt; a compact building a regional station; a city-sized building on up to three tracks a town station, on more (or a terminus
	 * or hub) a city station; a grand building a grand station.
	 */
	public static StationClass of(StationSpec spec) {
		if (spec.stationClass() != AUTO) {
			return spec.stationClass();
		}
		if (!spec.hasBuilding()) {
			return spec.trackCount() <= 2 ? HALT : REGIONAL;
		}
		return switch (spec.buildingScale()) {
			case COMPACT -> REGIONAL;
			case CITY -> spec.trackCount() <= 3 && !spec.terminus() ? TOWN : CITY;
			case GRAND -> GRAND;
		};
	}
}
