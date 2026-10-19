package com.aureliatransit.architecture.station;

import java.util.Locale;

/**
 * A real station the generator copies: its layout, dimensions, materials and operator from published facts (the sources
 * are listed in docs/REAL_STATIONS.md), not a look "after" it. Where a fact is unknown or does not fit the game's limits
 * the generator builds the nearest thing and says so.
 *
 * @param phrase       the words that ask for it ("utrecht centraal")
 * @param operator     who runs it, for the name signs
 * @param islands      island platforms over the tracks (two tracks each)
 * @param hallDepth    the concourse hall's depth along the tracks, in metres
 * @param hallHeight   the hall roof's highest point over the tracks, in metres
 * @param waves        the hall roof's waves across the tracks
 */
public enum RealStation {
	NONE("", Operator.NONE, 0, 0, 0, 0),
	/**
	 * Utrecht Centraal (Benthem Crouwel, 2016): one closed hall over all the tracks, between the raised Stationsplein (east)
	 * and the Jaarbeursplein (west). Roof about 235 x 85 m and 18 m high, steel, with three waves (the highest over the
	 * trains); glass walls hung from it; gate lines across the hall; stairs, escalators and lifts down to 8 island platforms
	 * (16 tracks); separate canopies on the platforms outside the hall.
	 */
	UTRECHT_CENTRAAL("utrecht centraal", Operator.NS, 8, 85, 18, 3);

	/** Rows a hall leaves free at each end of the platforms, so trains stop under the canopies as well as the hall. */
	static final int HALL_MARGIN = 24;
	/** The shallowest hall the generator builds: a gate line, a way down and a lift either side of the middle. */
	public static final int MIN_HALL_DEPTH = 31;

	private final String phrase;
	private final Operator operator;
	private final int islands;
	private final int hallDepth;
	private final int hallHeight;
	private final int waves;

	RealStation(String phrase, Operator operator, int islands, int hallDepth, int hallHeight, int waves) {
		this.phrase = phrase;
		this.operator = operator;
		this.islands = islands;
		this.hallDepth = hallDepth;
		this.hallHeight = hallHeight;
		this.waves = waves;
	}

	public String phrase() {
		return phrase;
	}

	/** The station's own name, for its name signs: "Utrecht Centraal". */
	public String label() {
		final StringBuilder label = new StringBuilder();
		for (final String word : phrase.split(" ")) {
			if (!word.isEmpty()) {
				label.append(label.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return label.toString();
	}

	public Operator operator() {
		return operator;
	}

	public int islands() {
		return islands;
	}

	/** Platforms the real station has; the plan builds them all. */
	public int tracks() {
		return 2 * islands;
	}

	public int waves() {
		return waves;
	}

	/** The real hall depth in blocks. */
	public int hallDepthBlocks() {
		return StationGeometry.metres(hallDepth);
	}

	/** y of the hall roof's highest point: its real height over the rails. */
	public int roofTopY() {
		return StationGeometry.metres(hallHeight);
	}

	/**
	 * The hall's depth along the tracks on platforms {@code length} blocks long: the real depth, or less on shorter platforms
	 * so they still run {@link #HALL_MARGIN} past each end. Always odd, so the hall has a centre row; 0 when even
	 * {@link #MIN_HALL_DEPTH} does not fit.
	 */
	public int hallDepth(int length) {
		int depth = Math.min(hallDepthBlocks(), length - 2 * HALL_MARGIN);
		if (depth % 2 == 0) {
			depth--;
		}
		return this == NONE || depth < MIN_HALL_DEPTH ? 0 : depth;
	}

	/** The share of the hall's width under its middle wave; the side waves share the rest equally. */
	static final double MIDDLE_WAVE = 0.56;
	/** A side wave rises this share of the middle one (Utrecht: about 4 m of the middle wave's 10 m over the eaves). */
	static final double SIDE_WAVE = 0.4;

	/**
	 * The roof's top y across the hall, from one long wall ({@code i = 0}) to the other ({@code i = span}): the waves side by
	 * side, each rising from {@code eaves}, the middle one to {@link #roofTopY()} and the others {@link #SIDE_WAVE} as high.
	 * An odd number of waves has a middle one; with one wave it spans the hall.
	 */
	public int[] roofProfile(int span, int eaves) {
		final int[] surface = new int[span + 1];
		final int rise = Math.max(0, roofTopY() - eaves);
		final int sides = (waves - 1) / 2;
		final double side = waves <= 1 ? 0 : (1 - MIDDLE_WAVE) / (2 * sides);
		final double midStart = sides * side;
		final double midEnd = 1 - midStart;
		for (int i = 0; i <= span; i++) {
			final double f = span == 0 ? 0.5 : (double) i / span;
			final double h;
			if (f < midStart) {
				h = SIDE_WAVE * rise * Math.sin(Math.PI * (f % side) / side);
			} else if (f > midEnd) {
				h = SIDE_WAVE * rise * Math.sin(Math.PI * ((f - midEnd) % side) / side);
			} else {
				h = rise * Math.sin(Math.PI * (f - midStart) / (midEnd - midStart));
			}
			surface[i] = eaves + (int) Math.round(h);
		}
		return surface;
	}

	/**
	 * True when every platform is an island with a track each side, so the outermost bands are tracks and the hall's streets
	 * meet them: the only platforms a real station's hall is built over.
	 */
	static boolean islandsOnly(PlatformLayout layout, int trackCount, java.util.List<PlatformSpec> platforms) {
		if (!platforms.isEmpty()) {
			return platforms.stream().allMatch(p -> p.kind() == PlatformSpec.Kind.ISLAND);
		}
		return layout == PlatformLayout.ISLAND && trackCount >= 2 && trackCount % 2 == 0;
	}

	static boolean islandsOnly(StationSpec spec) {
		return islandsOnly(spec.platformLayout(), spec.trackCount(), spec.platforms());
	}

	/** Shortest platforms that take the real hall at its real depth. */
	public int fullLength() {
		return hallDepthBlocks() + 2 * HALL_MARGIN;
	}

	/** The real station a phrase names, or null. */
	public static RealStation of(String phrase) {
		final String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT).strip();
		for (final RealStation station : values()) {
			if (station != NONE && station.phrase.equals(p)) {
				return station;
			}
		}
		return null;
	}
}
