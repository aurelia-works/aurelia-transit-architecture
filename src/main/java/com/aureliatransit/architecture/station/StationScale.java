package com.aureliatransit.architecture.station;

/**
 * How grand the station building is. A compact building is the two-storey block beside the entrance; a city station adds
 * a storey and wings along the platform; a grand station adds two storeys, wings along almost the whole platform, a pitched
 * roof on masonry looks, a clock tower over the entrance and a taller train shed. A side building grows deeper and taller
 * ({@link StationGeometry#buildingDepth}, {@link StationGeometry#headDepth}); platforms, tracks and footbridges are the
 * same at every scale.
 */
public enum StationScale {
	/** The two-storey building block only. */
	COMPACT("compact", 0, 0),
	/** Three storeys, with two-storey wings along about 70% of the platform, at most {@link #CITY_SPAN} long in all. */
	CITY("city", 1, 70),
	/** Four storeys, wings along about 92% of the platform, a clock tower and a taller shed. */
	GRAND("grand", 2, 92);

	/**
	 * Longest a city building and its wings run along the platform: 96 blocks, 64 m. A town's station building is a block
	 * with short wings and the platforms run on past it under their canopies; at 70% of a 250-long platform it was a 117 m
	 * wall (Surbiton, Coventry). A grand station's building stays the platform's share (Amsterdam Centraal's is longer still).
	 */
	public static final int CITY_SPAN = 96;

	private final String label;
	private final int extraStoreys;
	private final int wingPercent;

	StationScale(String label, int extraStoreys, int wingPercent) {
		this.label = label;
		this.extraStoreys = extraStoreys;
		this.wingPercent = wingPercent;
	}

	/** Plain-words name for the summary. */
	public String label() {
		return label;
	}

	/** Storeys added on top of the two-storey building block. */
	public int extraStoreys() {
		return extraStoreys;
	}

	/** The share of the platform length the building and its wings cover, in percent; 0 for no wings. */
	public int wingPercent() {
		return wingPercent;
	}

	/** Blocks along the platform the building and its wings cover on platforms {@code length} long; 0 for no wings. */
	public int wingSpan(int length) {
		final int span = length * wingPercent / 100;
		return this == CITY ? Math.min(span, CITY_SPAN) : span;
	}

	/**
	 * The scale a description gets when it names none: a grand station for a building with three or more tracks or a
	 * platform of 100 or more, a city station for a building with two tracks and 48 or more, otherwise compact.
	 */
	public static StationScale forSize(boolean building, int tracks, int length) {
		if (!building) {
			return COMPACT;
		}
		if (tracks >= 3 || length >= 100) {
			return GRAND;
		}
		return tracks >= 2 && length >= 48 ? CITY : COMPACT;
	}
}
