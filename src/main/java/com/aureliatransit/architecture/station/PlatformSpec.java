package com.aureliatransit.architecture.station;

import java.util.Objects;

/**
 * One platform's own settings, for a station whose platforms are not all alike (Station Studio's advanced platform list).
 * Platforms are listed left to right; each brings its own tracks, so the list fixes the station's tracks too.
 *
 * @param kind  a side platform (one track) or an island (a track on each side)
 * @param width the platform's width in blocks, {@link StationSpec#MIN_WIDTH} to {@link StationSpec#MAX_WIDTH}, or 0 for the
 *              station's width
 * @param length the platform's length in blocks, {@link StationSpec#MIN_LENGTH} up to the station's length, or 0 for the
 *               station's length
 * @param roof  the platform's own roof, or {@link Roof#STATION} for the station's
 * @param bay   a bay platform of a through station: its tracks come in from the station's start and end at buffer stops just
 *              past its far end, instead of running on through
 */
public record PlatformSpec(Kind kind, int width, int length, Roof roof, boolean bay) {

	/** Most platforms one station can list. */
	public static final int MAX_PLATFORMS = 10;

	public enum Kind {
		SIDE, ISLAND;

		/** Tracks this kind of platform brings. */
		public int tracks() {
			return this == ISLAND ? 2 : 1;
		}
	}

	/** A platform's roof: the station's, its own canopy, or open to the sky. Under a train shed every platform is covered. */
	public enum Roof {
		STATION, CANOPY, OPEN
	}

	public PlatformSpec {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(roof, "roof");
		if (width != 0 && (width < StationSpec.MIN_WIDTH || width > StationSpec.MAX_WIDTH)) {
			throw new IllegalArgumentException("platform width " + width + " outside " + StationSpec.MIN_WIDTH + ".." + StationSpec.MAX_WIDTH);
		}
		if (length != 0 && (length < StationSpec.MIN_LENGTH || length > StationSpec.MAX_LENGTH)) {
			throw new IllegalArgumentException("platform length " + length + " outside " + StationSpec.MIN_LENGTH + ".." + StationSpec.MAX_LENGTH);
		}
	}

	/** A platform the trains run on past. */
	public PlatformSpec(Kind kind, int width, int length, Roof roof) {
		this(kind, width, length, roof, false);
	}

	public static PlatformSpec of(Kind kind) {
		return new PlatformSpec(kind, 0, 0, Roof.STATION);
	}

	/** The width this platform is built at, in a station whose platforms are {@code stationWidth} wide. */
	public int widthIn(int stationWidth) {
		return width == 0 ? stationWidth : width;
	}

	/** The length this platform is built at, in a station whose platforms are {@code stationLength} long. */
	public int lengthIn(int stationLength) {
		return length == 0 ? stationLength : Math.min(length, stationLength);
	}

	/** The words the parser reads back, e.g. "island bay 12 wide 120 long open". */
	public String words() {
		final StringBuilder out = new StringBuilder(kind == Kind.ISLAND ? "island" : "side");
		if (bay) {
			out.append(" bay");
		}
		if (width != 0) {
			out.append(' ').append(width).append(" wide");
		}
		if (length != 0) {
			out.append(' ').append(length).append(" long");
		}
		if (roof == Roof.CANOPY) {
			out.append(" canopy");
		} else if (roof == Roof.OPEN) {
			out.append(" open");
		}
		return out.toString();
	}
}
