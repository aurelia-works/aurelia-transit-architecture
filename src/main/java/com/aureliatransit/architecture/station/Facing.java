package com.aureliatransit.architecture.station;

import java.util.Locale;

/**
 * Which compass direction a station's entrance, its street side, faces. Station Studio's facing control and the words
 * "facing north" (east, south, west) set it; {@link #AUTO} leaves it to the way the player looks when building in the game, and to
 * Station Studio's own drawing (the street to the west of a through station, to the north of a terminus) in the preview and export.
 */
public enum Facing {
	AUTO(null), NORTH(Heading.NORTH), EAST(Heading.EAST), SOUTH(Heading.SOUTH), WEST(Heading.WEST);

	private final Heading heading;

	Facing(Heading heading) {
		this.heading = heading;
	}

	/** The compass direction, or null for {@link #AUTO}. */
	public Heading direction() {
		return heading;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The facing a request names; anything unknown, blank or null is {@link #AUTO}. */
	public static Facing of(String text) {
		if (text == null) {
			return AUTO;
		}
		for (final Facing value : values()) {
			if (value.key().equals(text.strip().toLowerCase(Locale.ROOT))) {
				return value;
			}
		}
		return AUTO;
	}
}
