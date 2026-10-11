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
	/** Columns stand every this many cells along a platform. */
	public static final int COLUMN_SPACING = 8;
	/** A roof cell must be within this many cells (along u and along v) of a column top or a beam carried by columns. */
	public static final int MAX_CANTILEVER = 4;
	/** Longest beam between two neighbouring columns on one beam line. */
	public static final int MAX_BEAM_SPAN = 12;
	/** Ramp cells beyond each platform end for step-free access (lower half, upper half). */
	public static final int RAMP_LENGTH = 2;

	private StationGeometry() {
	}

	/** The cross-section and length for a spec. */
	public static TrackLayout layout(StationSpec spec) {
		final String bands = bandPattern(spec);
		final int platformWidth = spec.platformWidth();
		final List<TrackLayout.Track> tracks = new ArrayList<>();
		final List<TrackLayout.PlatformBand> platforms = new ArrayList<>();
		// u of the previous band's last occupied column (track centre or platform uMax), and whether it was a track
		int previousU = 0;
		boolean previousTrack = false;
		for (int i = 0; i < bands.length(); i++) {
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
				final int uMin = i == 0 ? 0 : previousU + EDGE_OFFSET;
				final int uMax = uMin + platformWidth - 1;
				platforms.add(new TrackLayout.PlatformBand(platforms.size() + 1, uMin, uMax, leftIsTrack, rightIsTrack));
				previousU = uMax;
			}
			previousTrack = track;
		}
		final int width = previousTrack ? previousU + ENVELOPE_HALF_WIDTH + 1 : previousU + 1;
		return new TrackLayout(width, spec.platformLength(), spec.stepFree() ? RAMP_LENGTH : 0, tracks, platforms);
	}

	/** Bands left to right: 'T' for a track, 'P' for a platform. */
	private static String bandPattern(StationSpec spec) {
		final int tracks = spec.trackCount();
		final StringBuilder bands = new StringBuilder();
		if (spec.platformLayout() == PlatformLayout.SIDE) {
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
