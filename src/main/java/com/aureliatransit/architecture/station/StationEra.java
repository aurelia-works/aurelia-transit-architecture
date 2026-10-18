package com.aureliatransit.architecture.station;

import java.util.Locale;

/**
 * When the station was built, which decides its look when the player named none. Station Studio's era control and the words
 * "historic era", "interwar era", "postwar era" and "modern era" set it. Historic is the masonry stations of the railway age (sandstone,
 * brick, gabled canopies, arched sheds); interwar the brick and stone of the 1920s and 30s; postwar the concrete of the 1950s to 70s;
 * modern glass and steel. {@link #AUTO} is "not asked": the plan then reports the era its building look belongs to ({@link #of}).
 *
 * <p>There is no look drawn from the 1920s and 30s yet, so interwar uses the nearest existing ones: Hamburg's clinker brick for
 * smaller stations and Milan's monumental stone for a grand one.
 */
public enum StationEra {
	AUTO("Auto"), HISTORIC("Historic"), INTERWAR("Interwar"), POSTWAR("Postwar"), MODERN("Modern");

	private final String label;

	StationEra(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The era a request names; anything unknown, blank or null is {@link #AUTO}. */
	public static StationEra of(String text) {
		if (text == null) {
			return AUTO;
		}
		for (final StationEra value : values()) {
			if (value.key().equals(text.strip().toLowerCase(Locale.ROOT))) {
				return value;
			}
		}
		return AUTO;
	}

	/** The era the spec asks for, or, for {@link #AUTO}, the one its building look belongs to (the platform style without a building). */
	public static StationEra of(StationSpec spec) {
		if (spec.era() != AUTO) {
			return spec.era();
		}
		return switch (spec.building()) {
			case GOTHIC_BRICK, AMSTERDAM, ANTWERP, SANDSTONE, BEAUX_ARTS, TOKYO, ZURICH, PARIS, BANGKOK, KUALA_LUMPUR, MUMBAI -> HISTORIC;
			case CLINKER, MILAN -> INTERWAR;
			case CONCRETE -> POSTWAR;
			case MODERN, BERLIN, HONG_KONG, BEIJING -> MODERN;
			case NONE -> switch (spec.style()) {
				case GERMAN_SBAHN -> POSTWAR;
				case DUTCH, MODERN, METRO -> MODERN;
			};
		};
	}
}
