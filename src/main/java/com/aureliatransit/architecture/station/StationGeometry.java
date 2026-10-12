package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * Railway geometry: the fixed clearances of an MTR station and the cross-section of tracks and platforms for a spec.
 * Knows nothing about styles or blocks.
 */
public final class StationGeometry {

	/**
	 * Columns from a track's rail node column to the nearest platform cell (the edge), at the same y as the rail node.
	 * Live-checked in this repo's release-check world (tools/release_check/ata_test place.mcfunction: rail z=0, platform edge
	 * z=2 facing the track, same Y), where MTR train doors were verified to open.
	 */
	public static final int EDGE_OFFSET = 2;
	/** Platform blocks (and rail nodes) are at y = 0; passengers walk at y = 1. */
	public static final int PLATFORM_Y = 0;
	public static final int WALK_Y = 1;
	/** Track clearance envelope: columns u-1..u+1 ... */
	public static final int ENVELOPE_HALF_WIDTH = 1;
	/** ... and y = 0..ENVELOPE_HEIGHT-1. */
	public static final int ENVELOPE_HEIGHT = 5;
	/** Smallest centre-to-centre distance of two neighbouring tracks with no platform between them. */
	public static final int TRACK_SPACING = 4;
	/** Clear cells above the walking surface under any roof (y = 1..3). */
	public static final int HEADROOM = 3;
	/** Platform canopies sit directly above the headroom. */
	public static final int CANOPY_Y = WALK_Y + HEADROOM;
	/** Hall beams sit directly above the track envelopes ... */
	public static final int HALL_BEAM_Y = ENVELOPE_HEIGHT;
	/** ... and the hall roof on top of them. */
	public static final int HALL_ROOF_Y = HALL_BEAM_Y + 1;
	/** The frame of a wide vaulted shed, so the vault springs well above the trains. */
	public static final int HIGH_HALL_BEAM_Y = 8;
	/** The frame of a shed over a footbridge or beside a station building: above the bridge roof and the building's cornice. */
	public static final int TALL_HALL_BEAM_Y = 11;
	/** Columns stand every this many cells along a platform. */
	public static final int COLUMN_SPACING = 8;
	/** A roof cell must be within this many cells (along u and along v) of a column top or a beam carried by columns. */
	public static final int MAX_CANTILEVER = 4;
	/** Longest beam between two neighbouring columns on one beam line. */
	public static final int MAX_BEAM_SPAN = 12;
	/** Ramp cells beyond each platform end for step-free access (lower half, upper half). */
	public static final int RAMP_LENGTH = 2;

	/** Station building: entrance apron (the entrance ramp's two cells) on the street side ... */
	public static final int APRON = 2;
	/** ... then the building across u, walls included (street wall, 7 interior columns, platform wall). */
	public static final int BUILDING_DEPTH = 9;
	/** Ground storey: floor at y = 0, rooms y = 1..4, upper slab (and footbridge deck) at y = UPPER_FLOOR_Y ... */
	public static final int UPPER_FLOOR_Y = ENVELOPE_HEIGHT;
	/** ... upper storey walking level ... */
	public static final int UPPER_WALK_Y = UPPER_FLOOR_Y + 1;
	/** ... and the flat roof (footbridge roof too) above three clear blocks. */
	public static final int BUILDING_ROOF_Y = UPPER_WALK_Y + HEADROOM;
	/** Facade bay width along the tracks, pilaster to pilaster. */
	public static final int BAY = 6;
	/** Footbridge corridor: clear width inside its walls. */
	public static final int BRIDGE_WIDTH = 3;
	/** Terminus head building along v past the platform ends: buffer row, paid row, gate line, concourse, street wall. */
	public static final int HEAD_DEPTH = 12;
	/** Entrance ramps outside a terminus head building's street wall. */
	public static final int HEAD_APRON = RAMP_LENGTH;
	/** Narrowest island with a two-column stair and room for a lift beside the walkway. */
	public static final int MIN_LIFT_ISLAND_WIDTH = 6;
	/** Stairs from a walking level to the footbridge or upper floor: one step per block of rise. */
	public static final int STAIR_STEPS = UPPER_WALK_Y - WALK_Y;

	private StationGeometry() {
	}

	/** The cross-section and length for a spec. */
	public static TrackLayout layout(StationSpec spec) {
		final String bands = bandPattern(spec);
		final List<TrackLayout.Track> tracks = new ArrayList<>();
		final List<TrackLayout.PlatformBand> platforms = new ArrayList<>();
		// u of the previous band's last occupied column (track centre or platform uMax), and whether it was a track
		int previousU = 0;
		boolean previousTrack = false;
		for (int i = 0; i < bands.length(); i++) {
			if (bands.charAt(i) == 'W') {
				// a gap behind a side platform, so its back never faces the next track
				previousU++;
				previousTrack = false;
				continue;
			}
			final boolean track = bands.charAt(i) == 'T';
			final boolean leftIsTrack = i > 0 && bands.charAt(i - 1) == 'T';
			final boolean leftIsPlatform = i > 0 && bands.charAt(i - 1) == 'P';
			final boolean rightIsTrack = i + 1 < bands.length() && bands.charAt(i + 1) == 'T';
			final boolean rightIsPlatform = i + 1 < bands.length() && bands.charAt(i + 1) == 'P';
			if (track) {
				final int u;
				if (i == 0) {
					u = ENVELOPE_HALF_WIDTH;
				} else if (previousTrack) {
					u = previousU + TRACK_SPACING;
				} else {
					u = previousU + EDGE_OFFSET;
				}
				tracks.add(new TrackLayout.Track(tracks.size() + 1, u, leftIsPlatform, rightIsPlatform));
				previousU = u;
			} else {
				// a side platform back to back with the one before touches it
				final int uMin = i == 0 ? 0 : leftIsPlatform ? previousU + 1 : previousU + EDGE_OFFSET;
				final int uMax = uMin + platformWidth(spec, platforms.size()) - 1;
				final int[] run = platformRun(spec, platforms.size());
				platforms.add(new TrackLayout.PlatformBand(platforms.size() + 1, uMin, uMax, leftIsTrack, rightIsTrack, run[0], run[1]));
				previousU = uMax;
			}
			previousTrack = track;
		}
		final int railWidth = previousTrack ? previousU + ENVELOPE_HALF_WIDTH + 1 : previousU + 1;
		final int shift = spec.sideBuilding() ? APRON + BUILDING_DEPTH : 0;
		if (shift == 0) {
			return new TrackLayout(railWidth, spec.platformLength(), spec.stepFree() ? RAMP_LENGTH : 0, tracks, platforms, 0,
					spec.terminus() ? HEAD_DEPTH + HEAD_APRON : 0, hubThrough(spec));
		}
		final List<TrackLayout.Track> shiftedTracks = new ArrayList<>();
		for (final TrackLayout.Track track : tracks) {
			shiftedTracks.add(new TrackLayout.Track(track.number(), track.u() + shift, track.platformLeft(), track.platformRight()));
		}
		final List<TrackLayout.PlatformBand> shiftedPlatforms = new ArrayList<>();
		for (final TrackLayout.PlatformBand band : platforms) {
			shiftedPlatforms.add(new TrackLayout.PlatformBand(band.number(), band.uMin() + shift, band.uMax() + shift, band.trackLeft(), band.trackRight(),
					band.vFrom(), band.vTo()));
		}
		return new TrackLayout(railWidth + shift, spec.platformLength(), spec.stepFree() ? RAMP_LENGTH : 0, shiftedTracks, shiftedPlatforms, shift, 0);
	}

	/**
	 * How many tracks on each side of a hub run on through past the head building: one up to six tracks, two from eight (an
	 * outer island whose tracks both run through, reached by a footbridge), when the platforms are wide enough for its lifts;
	 * 0 for any other station.
	 */
	public static int hubThrough(StationSpec spec) {
		if (!spec.hub()) {
			return 0;
		}
		final int outer = Math.min(platformWidth(spec, 0), platformWidth(spec, platformCount(spec) - 1));
		return spec.trackCount() >= 8 && outer >= MIN_LIFT_ISLAND_WIDTH ? 2 : 1;
	}

	/** Rows a footbridge's stairs, landings and lifts take on either side of its line, which every platform must reach. */
	static final int BRIDGE_REACH = 12;

	/**
	 * First and last v of the platform at {@code index} (0-based): the whole length, or a listed platform's own length. A
	 * terminus's shorter platform ends at the head building; a through station's is centred on the footbridge line. Any
	 * platform a footbridge may serve (a through station's, a hub's) still reaches {@link #BRIDGE_REACH} rows past that line.
	 */
	public static int[] platformRun(StationSpec spec, int index) {
		final int length = spec.platformLength();
		final int own = spec.listed() ? spec.platforms().get(index).lengthIn(length) : length;
		if (own >= length) {
			return new int[]{0, length - 1};
		}
		int from;
		int to;
		if (spec.terminus()) {
			from = length - own;
			to = length - 1;
		} else {
			from = StationLayout.entranceV(spec.sideBuilding(), length) - own / 2;
			to = from + own - 1;
		}
		if (!spec.terminus() || spec.hub()) {
			final int line = StationLayout.entranceV(spec.sideBuilding(), length);
			from = Math.min(from, line - BRIDGE_REACH);
			to = Math.max(to, line + BRIDGE_REACH);
		}
		return new int[]{Math.max(0, from), Math.min(length - 1, to)};
	}

	/** Width of the platform at {@code index} (0-based, left to right): its own when the platforms are listed. */
	public static int platformWidth(StationSpec spec, int index) {
		return spec.listed() ? spec.platforms().get(index).widthIn(spec.platformWidth()) : spec.platformWidth();
	}

	/**
	 * The roof over platform {@code number} (1-based): the station's, or under canopies or none the platform's own canopy or
	 * open sky when the platforms are listed. A train shed covers every platform.
	 */
	public static RoofType roof(StationSpec spec, int number) {
		if (!spec.listed() || spec.roof() == RoofType.HALL) {
			return spec.roof();
		}
		return switch (spec.platforms().get(number - 1).roof()) {
			case STATION -> spec.roof();
			case CANOPY -> RoofType.CANOPY;
			case OPEN -> RoofType.NONE;
		};
	}

	/** Some platform has a canopy. */
	public static boolean anyCanopy(StationSpec spec) {
		for (int i = 1; i <= platformCount(spec); i++) {
			if (roof(spec, i) == RoofType.CANOPY) {
				return true;
			}
		}
		return false;
	}

	/** How many platforms the station has. */
	public static int platformCount(StationSpec spec) {
		return spec.listed() ? spec.platforms().size() : (int) bandPattern(spec).chars().filter(c -> c == 'P').count();
	}

	/** Bands left to right: 'T' for a track, 'P' for a platform, 'W' for a one-column gap. */
	private static String bandPattern(StationSpec spec) {
		final int tracks = spec.trackCount();
		final StringBuilder bands = new StringBuilder();
		if (spec.listed()) {
			// each platform brings its tracks: an island one on each side, a side platform one. A side platform's back is the
			// station's edge, or the back of the side platform after it; a side platform with an island after it gets a gap
			// ('W') behind it, so no track is shared and every side platform keeps one boarding edge
			boolean openBack = false;
			for (int i = 0; i < spec.platforms().size(); i++) {
				if (spec.platforms().get(i).kind() == PlatformSpec.Kind.ISLAND) {
					bands.append(openBack ? "WTPT" : "TPT");
					openBack = false;
				} else if (i == 0 || openBack) {
					bands.append("PT");
					openBack = false;
				} else {
					bands.append("TP");
					openBack = true;
				}
			}
		} else if (spec.platformLayout() == PlatformLayout.SIDE) {
			bands.append('P');
			for (int i = 0; i < tracks; i++) {
				bands.append('T');
			}
			if (tracks >= 2) {
				bands.append('P');
			}
		} else {
			for (int i = 0; i + 1 < tracks; i += 2) {
				bands.append("TPT");
			}
			if ((tracks & 1) == 1) {
				bands.append("TP");
			}
		}
		return bands.toString();
	}
}
