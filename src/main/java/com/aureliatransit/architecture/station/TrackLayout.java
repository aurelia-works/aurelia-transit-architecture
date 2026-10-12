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
 *                       building beside the tracks); the railway starts at u = buildingWidth
 * @param headDepth      a terminus: rows v = platformLength.. taken by the head building across the track ends and its
 *                       entrance apron (0 for a through station, whose tracks run on past both platform ends)
 * @param hubThrough     a hub: how many tracks on each side run on through, past the head building, which then only spans
 *                       the platforms beside a bay track (0 for any other station; see {@link StationGeometry#hubThrough})
 */
public record TrackLayout(int width, int platformLength, int rampLength, List<Track> tracks, List<PlatformBand> platforms, int buildingWidth,
		int headDepth, int hubThrough) {

	public TrackLayout {
		tracks = List.copyOf(tracks);
		platforms = List.copyOf(platforms);
	}

	public TrackLayout(int width, int platformLength, int rampLength, List<Track> tracks, List<PlatformBand> platforms, int buildingWidth,
			int headDepth) {
		this(width, platformLength, rampLength, tracks, platforms, buildingWidth, headDepth, 0);
	}

	public TrackLayout(int width, int platformLength, int rampLength, List<Track> tracks, List<PlatformBand> platforms) {
		this(width, platformLength, rampLength, tracks, platforms, 0, 0);
	}

	/** A terminus whose outer tracks run on through, past both sides of the head building. */
	public boolean hub() {
		return hubThrough > 0;
	}

	/** The tracks end at buffer stops in front of a head building at v = platformLength. */
	public boolean terminus() {
		return headDepth > 0;
	}

	/** Last v of a terminus head building (its street wall). */
	public int headEnd() {
		return platformLength + StationGeometry.HEAD_DEPTH - 1;
	}

	/** Last v the platforms (and a terminus's bay tracks) run to: the platform end at a terminus, the footprint end otherwise. */
	public int trackEnd() {
		return terminus() ? platformLength - 1 : vMax();
	}

	/** The track ends at the head building's buffer stops: every track of a terminus except a hub's outer ones on each side. */
	public boolean bay(Track track) {
		return terminus() && track.number() > hubThrough && track.number() <= tracks.size() - hubThrough;
	}

	/** Last v this track (and its clearance) runs to: its buffer stop, or the footprint end for a track running through. */
	public int trackEnd(Track track) {
		return bay(track) ? platformLength - 1 : vMax();
	}

	/** A platform beside at least one bay track: it runs level into the head building's concourse. */
	public boolean bay(PlatformBand band) {
		for (final Track track : tracks) {
			if (bay(track) && (track.u() == band.uMin() - StationGeometry.EDGE_OFFSET && band.trackLeft()
					|| track.u() == band.uMax() + StationGeometry.EDGE_OFFSET && band.trackRight())) {
				return true;
			}
		}
		return false;
	}

	/** First column of a terminus head building: the whole width, or a hub's first platform beside a bay track. */
	public int headFrom() {
		if (hub()) {
			for (final PlatformBand band : platforms) {
				if (bay(band)) {
					return band.uMin();
				}
			}
		}
		return 0;
	}

	/** Last column of a terminus head building: the whole width, or a hub's last platform beside a bay track. */
	public int headTo() {
		if (hub()) {
			for (int i = platforms.size() - 1; i >= 0; i--) {
				if (bay(platforms.get(i))) {
					return platforms.get(i).uMax();
				}
			}
		}
		return width - 1;
	}

	/** Inside a station building: the side building's columns, or a terminus head building's rows. */
	public boolean indoor(int u, int v) {
		return u >= 0 && u < width && (u < buildingWidth || terminus() && u >= headFrom() && u <= headTo() && v >= platformLength && v <= headEnd());
	}

	/** First v of the footprint (start of the ramps, or 0). */
	public int vMin() {
		return -rampLength;
	}

	/** Last v of the footprint (the end of the ramps, or of a terminus head building's apron). */
	public int vMax() {
		return platformLength - 1 + (terminus() ? headDepth : rampLength);
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
