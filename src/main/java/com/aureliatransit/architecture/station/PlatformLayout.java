package com.aureliatransit.architecture.station;

/**
 * How platforms sit relative to the tracks.
 */
public enum PlatformLayout {
	/** Platforms on the outer sides of the tracks; with three or four tracks the inner tracks are through tracks. */
	SIDE,
	/** Island platforms shared by pairs of tracks; an odd last track gets a side platform. */
	ISLAND
}
