package com.aureliatransit.architecture.station;

/**
 * A rule a plan breaks, at a cell in local coordinates.
 */
public record Violation(Kind kind, int u, int y, int v, String message) {

	public enum Kind {
		/** A planned block (other than a rail node) inside a track's clearance envelope. */
		ENVELOPE_INTRUSION,
		/** A platform edge not exactly {@link StationGeometry#EDGE_OFFSET} columns from its track, or missing along the platform. */
		PLATFORM_OFFSET,
		/** Two neighbouring tracks without a platform between them closer than {@link StationGeometry#TRACK_SPACING}. */
		TRACK_SPACING,
		/** A roof part over a platform lower than {@link StationGeometry#MIN_ROOF_Y}. */
		HEADROOM,
		/** Something standing on a platform edge cell, where passengers board. */
		EDGE_CLEARWAY,
		/** A column that does not stand on a platform floor cell or does not reach the roof unbroken. */
		UNSUPPORTED_COLUMN,
		/** A roof cell farther than {@link StationGeometry#MAX_CANTILEVER} from any column top or carried beam. */
		ROOF_UNSUPPORTED,
		/** A beam span between neighbouring columns longer than {@link StationGeometry#MAX_BEAM_SPAN}. */
		ROOF_SPAN,
		/** A lamp, bench, bin or sign not standing on a platform floor cell. */
		FLOATING_FIXTURE,
		/** Two planned blocks in one cell. */
		OVERLAP,
		/** A planned block outside the footprint. */
		OUT_OF_BOUNDS
	}
}
