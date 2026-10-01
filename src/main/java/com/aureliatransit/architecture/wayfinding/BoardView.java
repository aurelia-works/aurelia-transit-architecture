package com.aureliatransit.architecture.wayfinding;

/**
 * What a station information board shows. One block, one shared wayfinding model: the view only picks which of the
 * already-resolved facts (station, lines, exits, platform, transfers, service messages) the board lays out.
 */
public enum BoardView {
	/** "Trains this side": direction arrow, destination and the lines that stop here. */
	TRAINS_THIS_SIDE,
	/** Large platform/track number with direction and lines. */
	PLATFORM_TRACK,
	/** The active service notices that apply to the station. */
	SERVICE_CHANGE,
	/** Mezzanine transfer board: lines to change to plus the transfer note. */
	TRANSFER,
	/** Street/exit summary: each exit with where it leads. */
	EXITS;

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static BoardView byOrdinal(int ordinal) {
		final BoardView[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TRAINS_THIS_SIDE;
	}
}
