package com.aureliatransit.architecture.station;

import java.util.ArrayList;
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
		// a band made without its own run along the station runs the whole platform length
		final List<PlatformBand> bands = new ArrayList<>();
		for (final PlatformBand band : platforms) {
			bands.add(band.vTo() < 0 ? new PlatformBand(band.number(), band.uMin(), band.uMax(), band.trackLeft(), band.trackRight(), 0,
					platformLength - 1, band.deadEnd()) : band);
		}
		platforms = List.copyOf(bands);
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
		return bay(track) ? platformLength - 1 : deadEnd(track) ? platformOf(track).vTo() : vMax();
	}

	/** A through station's bay track: it ends at a buffer stop at row {@code trackEnd(track) + 1}, past its platform's end. */
	public boolean deadEnd(Track track) {
		final PlatformBand band = terminus() ? null : platformOf(track);
		return band != null && band.deadEnd();
	}

	/** The platform along this track (the longer, for a track between two), or null for a track between no platforms. */
	public PlatformBand platformOf(Track track) {
		PlatformBand best = null;
		for (final PlatformBand band : platforms) {
			final boolean beside = band.trackRight() && band.uMax() + StationGeometry.EDGE_OFFSET == track.u()
					|| band.trackLeft() && band.uMin() - StationGeometry.EDGE_OFFSET == track.u();
			if (beside && (best == null || band.length() > best.length())) {
				best = band;
			}
		}
		return best;
	}

	/** First v of the platform along this track (0 for a track between no platforms). */
	public int platformFrom(Track track) {
		final PlatformBand band = platformOf(track);
		return band == null ? 0 : band.vFrom();
	}

	/** Last v of the platform along this track ({@code platformLength - 1} for a track between no platforms). */
	public int platformTo(Track track) {
		final PlatformBand band = platformOf(track);
		return band == null ? platformLength - 1 : band.vTo();
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

	/**
	 * First column of a terminus head building: the whole width, or for a hub its side wall just outside the first bay track's
	 * clearance (so every buffer stop is indoors), never on a through track's clearance; the first bay platform otherwise.
	 */
	public int headFrom() {
		if (hub()) {
			for (final PlatformBand band : platforms) {
				if (bay(band)) {
					int from = band.uMin();
					for (final Track track : tracks) {
						if (bay(track)) {
							from = Math.min(from, track.u() - StationGeometry.ENVELOPE_HALF_WIDTH - 1);
							break;
						}
					}
					for (final Track track : tracks) {
						if (!bay(track) && track.u() < band.uMin() && from <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH) {
							return band.uMin();
						}
					}
					return from;
				}
			}
		}
		return 0;
	}

	/** Last column of a terminus head building: the whole width, or a hub's side wall just outside its last bay track. */
	public int headTo() {
		if (hub()) {
			for (int i = platforms.size() - 1; i >= 0; i--) {
				final PlatformBand band = platforms.get(i);
				if (bay(band)) {
					int to = band.uMax();
					for (int t = tracks.size() - 1; t >= 0; t--) {
						if (bay(tracks.get(t))) {
							to = Math.max(to, tracks.get(t).u() + StationGeometry.ENVELOPE_HALF_WIDTH + 1);
							break;
						}
					}
					for (final Track track : tracks) {
						if (!bay(track) && track.u() > band.uMax() && to >= track.u() - StationGeometry.ENVELOPE_HALF_WIDTH) {
							return band.uMax();
						}
					}
					return to;
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
	 * A platform occupying columns uMin..uMax and rows vFrom..vTo: the whole platform length, or less for a shorter platform
	 * of a station whose platforms are listed one by one.
	 *
	 * @param number     1-based, left to right
	 * @param trackLeft  a track runs along its uMin edge
	 * @param trackRight a track runs along its uMax edge
	 * @param deadEnd    a through station's bay platform: its tracks end at buffer stops just past vTo
	 */
	public record PlatformBand(int number, int uMin, int uMax, boolean trackLeft, boolean trackRight, int vFrom, int vTo, boolean deadEnd) {

		/** A platform the trains run on past. */
		public PlatformBand(int number, int uMin, int uMax, boolean trackLeft, boolean trackRight, int vFrom, int vTo) {
			this(number, uMin, uMax, trackLeft, trackRight, vFrom, vTo, false);
		}

		/** A platform the whole length of the station. */
		public PlatformBand(int number, int uMin, int uMax, boolean trackLeft, boolean trackRight) {
			this(number, uMin, uMax, trackLeft, trackRight, 0, -1);
		}

		public int width() {
			return uMax - uMin + 1;
		}

		/** Rows along the station. */
		public int length() {
			return vTo - vFrom + 1;
		}

		/** Row v is on this platform. */
		public boolean runs(int v) {
			return v >= vFrom && v <= vTo;
		}

		/** The cell (u, v) is on this platform. */
		public boolean contains(int u, int v) {
			return u >= uMin && u <= uMax && runs(v);
		}

		public boolean island() {
			return trackLeft && trackRight;
		}
	}
}
