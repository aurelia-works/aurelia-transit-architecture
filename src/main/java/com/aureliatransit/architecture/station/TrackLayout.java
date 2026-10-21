package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * Railway geometry of a station, before any styling: where tracks and platforms are across the station (u) and how long
 * it is (v). Produced by {@link StationGeometry#layout}.
 *
 * @param width          number of columns, u = 0..width-1 (the outermost columns are track envelopes or platform cells)
 * @param platformLength the track zone, v = 0..platformLength-1: the platforms' full length, or at a terminus that plus each
 *                       track's overrun and buffer row ({@link StationGeometry#overrun})
 * @param rampLength     ramp cells beyond each platform end (0 without step-free access)
 * @param tracks         left to right
 * @param platforms      left to right
 * @param buildingWidth  columns u = 0..buildingWidth-1 taken by the station building and its entrance apron (0 without a
 *                       building beside the tracks); the railway starts at u = buildingWidth
 * @param headDepth      a terminus: rows v = platformLength.. taken by the head building across the track ends and its
 *                       entrance apron (0 for a through station, whose tracks run on past both platform ends)
 * @param hubThrough     a hub: how many tracks on each side run on through, past the head building, which then only spans
 *                       the platforms beside a bay track (0 for any other station; see {@link StationGeometry#hubThrough})
 * @param parkingWidth   columns at the low-u edge taken by a car park in front of the building ({@link StationGeometry#PARKING_DEPTH},
 *                       0 without one); they are outside the building, so not indoor
 * @param forecourtDepth rows v = headEnd + {@link StationGeometry#HEAD_APRON} + 1 .. past the head building's entrance ramps, taken by a
 *                       hub's forecourt with its bus interchange and taxi rank ({@link StationGeometry#FORECOURT_DEPTH}, 0 without one)
 */
public record TrackLayout(int width, int platformLength, int rampLength, List<Track> tracks, List<PlatformBand> platforms, int buildingWidth,
		int headDepth, int hubThrough, int parkingWidth, int forecourtDepth) {

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
			int headDepth, int hubThrough, int parkingWidth) {
		this(width, platformLength, rampLength, tracks, platforms, buildingWidth, headDepth, hubThrough, parkingWidth, 0);
	}

	public TrackLayout(int width, int platformLength, int rampLength, List<Track> tracks, List<PlatformBand> platforms, int buildingWidth,
			int headDepth, int hubThrough) {
		this(width, platformLength, rampLength, tracks, platforms, buildingWidth, headDepth, hubThrough, 0);
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
		return platformLength + headDepth - StationGeometry.HEAD_APRON - 1;
	}

	/** Last v the platforms (and a terminus's bay tracks) run to: the platform end at a terminus, the footprint end otherwise. */
	public int trackEnd() {
		return terminus() ? platformLength - 1 : vMax();
	}

	/** The track ends at the head building's buffer stops: every track of a terminus except a hub's outer ones on each side. */
	public boolean bay(Track track) {
		return terminus() && track.number() > hubThrough && track.number() <= tracks.size() - hubThrough;
	}

	/**
	 * Last v this track (and its clearance) runs to: its last rail node, {@link StationGeometry#overrun} rows past its
	 * platform's end, with the buffer stop in the row after; or the footprint end for a track running through.
	 */
	public int trackEnd(Track track) {
		return bay(track) ? platformLength - 2 : deadEnd(track) ? platformOf(track).vTo() + StationGeometry.overrun(platformLength) : vMax();
	}

	/** True for a track ending at a buffer stop: a terminus's bay track or a through station's dead end. */
	public boolean buffered(Track track) {
		return bay(track) || deadEnd(track);
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
		return u >= parkingWidth && u < width
				&& (u < buildingWidth || terminus() && u >= headFrom() && u <= headTo() && v >= platformLength && v <= headEnd());
	}

	/** First v of the footprint (start of the ramps, or 0). */
	public int vMin() {
		return -rampLength;
	}

	/** Last v of the footprint (the end of the ramps, or of a terminus head building's apron and the forecourt past it). */
	public int vMax() {
		return platformLength - 1 + (terminus() ? headDepth + forecourtDepth : rampLength);
	}

	/** First v of a hub's forecourt: the row past the head building's entrance ramps. Meaningful only with a forecourt. */
	public int forecourtFrom() {
		return headEnd() + StationGeometry.HEAD_APRON + 1;
	}

	/**
	 * The platform number passengers see for a track, and MTR's platform name: the tracks a platform runs beside, counted
	 * from the left (the station building's side), so an island's two faces have two numbers ("1" and "2") as at a real
	 * station. A track with no platform has none (0).
	 */
	public int platformNumber(Track track) {
		if (track.through()) {
			return 0;
		}
		int number = 0;
		for (final Track other : tracks) {
			if (!other.through()) {
				number++;
			}
			if (other == track || other.equals(track)) {
				return number;
			}
		}
		return 0;
	}

	/** The tracks along a platform's edges, left first: one for a side platform, two for an island. */
	public List<Track> faces(PlatformBand band) {
		final List<Track> faces = new ArrayList<>(2);
		for (final Track track : tracks) {
			if (band.trackLeft() && track.platformRight() && track.u() + StationGeometry.EDGE_OFFSET == band.uMin()) {
				faces.add(0, track);
			} else if (band.trackRight() && track.platformLeft() && track.u() - StationGeometry.EDGE_OFFSET == band.uMax()) {
				faces.add(track);
			}
		}
		return faces;
	}

	/** The platform numbers of a platform's faces, left first (empty for a platform with no track). */
	public List<Integer> platformNumbers(PlatformBand band) {
		final List<Integer> numbers = new ArrayList<>(2);
		for (final Track face : faces(band)) {
			numbers.add(platformNumber(face));
		}
		return numbers;
	}

	/** "Platform 3", or "Platforms 3–4" for an island or a run of them; empty with no numbers. */
	public static String platformsText(List<Integer> numbers) {
		if (numbers.isEmpty()) {
			return "";
		}
		final int lo = numbers.stream().mapToInt(Integer::intValue).min().orElseThrow();
		final int hi = numbers.stream().mapToInt(Integer::intValue).max().orElseThrow();
		return lo == hi ? "Platform " + lo : "Platforms " + lo + "–" + hi;
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
