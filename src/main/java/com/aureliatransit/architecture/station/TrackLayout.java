package com.aureliatransit.architecture.station;

import java.util.List;

/**
 * Railway geometry of a station, before any styling: where tracks and platforms are across the station (u) and how long
 * it is (v). Produced by {@link StationGeometry#layout}.
 *
 * @param width          number of columns, u = 0..width-1 (the outermost columns are track envelopes or platform cells)
 * @param platformLength platforms run v = 0..platformLength-1
 * @param rampLength     ramp cells beyond each platform end (0 without step-free access)
 * @param tracks         left to right
 * @param platforms      left to right
 * @param buildingWidth  columns u = 0..buildingWidth-1 taken by the station building and its entrance apron (0 without a
 *                       building); the railway starts at u = buildingWidth
 */
public record TrackLayout(int width, int platformLength, int rampLength, List<Track> tracks, List<PlatformBand> platforms, int buildingWidth) {

	public TrackLayout {
		tracks = List.copyOf(tracks);
		platforms = List.copyOf(platforms);
	}

	public TrackLayout(int width, int platformLength, int rampLength, List<Track> tracks, List<PlatformBand> platforms) {
		this(width, platformLength, rampLength, tracks, platforms, 0);
	}

	/** First v of the footprint (start of the ramps, or 0). */
	public int vMin() {
		return -rampLength;
	}

	/** Last v of the footprint. */
	public int vMax() {
		return platformLength - 1 + rampLength;
	}

	/**
	 * A track. Its rail nodes and centre line are in column {@code u}; its clearance envelope is u-1..u+1.
	 *
	 * @param number        1-based, left to right
	 * @param platformLeft  a platform edge is at u - {@link StationGeometry#EDGE_OFFSET}
	 * @param platformRight a platform edge is at u + {@link StationGeometry#EDGE_OFFSET}
	 */
	public record Track(int number, int u, boolean platformLeft, boolean platformRight) {

		/** No platform on either side: trains run through. */
		public boolean through() {
			return !platformLeft && !platformRight;
		}
	}

	/**
	 * A platform occupying columns uMin..uMax.
	 *
	 * @param number     1-based, left to right
	 * @param trackLeft  a track runs along its uMin edge
	 * @param trackRight a track runs along its uMax edge
	 */
	public record PlatformBand(int number, int uMin, int uMax, boolean trackLeft, boolean trackRight) {

		public int width() {
			return uMax - uMin + 1;
		}

		public boolean island() {
			return trackLeft && trackRight;
		}
	}
}
