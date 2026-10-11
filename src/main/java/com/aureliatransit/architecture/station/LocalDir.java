package com.aureliatransit.architecture.station;

/**
 * A horizontal direction in the station's local frame: +V runs along the tracks (away from the player who planned it),
 * +U is to the right of +V.
 */
public enum LocalDir {
	PLUS_U,
	MINUS_U,
	PLUS_V,
	MINUS_V;

	public LocalDir opposite() {
		return switch (this) {
			case PLUS_U -> MINUS_U;
			case MINUS_U -> PLUS_U;
			case PLUS_V -> MINUS_V;
			case MINUS_V -> PLUS_V;
		};
	}

	public boolean alongTrack() {
		return this == PLUS_V || this == MINUS_V;
	}
}
