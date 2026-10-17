package com.aureliatransit.architecture.station;

/** The cross-section of a hall roof (train shed); canopies ignore it. */
public enum ShedShape {
	/** A flat roof on a grid of girders. */
	FLAT("flat train shed"),
	/** One shallow barrel vault over the whole railway (Hamburg, Amsterdam, Antwerp, Paris, Berlin, Bangkok). */
	ARCH("arched train shed"),
	/** One tall pointed (Gothic) arch over the whole railway (St Pancras). */
	POINTED("pointed-arch train shed"),
	/** A row of barrel vaults side by side, one over each group of tracks (Frankfurt, Leipzig, Milan). */
	MULTI_ARCH("train shed of several arches"),
	/** A row of pitched roofs side by side, ridge and furrow, one over each group of tracks (Mumbai, Kuala Lumpur, Zurich). */
	GABLED("train shed of pitched roofs");

	private final String label;

	ShedShape(String label) {
		this.label = label;
	}

	/** The shed that suits a building look (or, without a building, a platform style). */
	public static ShedShape forLook(BuildingStyle building, StationStyle style) {
		return switch (building) {
			case CLINKER, AMSTERDAM, ANTWERP, PARIS, BERLIN, BANGKOK -> ARCH;
			case GOTHIC_BRICK -> POINTED;
			case SANDSTONE, MILAN -> MULTI_ARCH;
			case MUMBAI, KUALA_LUMPUR, ZURICH -> GABLED;
			case MODERN, CONCRETE, BEAUX_ARTS, TOKYO, HONG_KONG, BEIJING -> FLAT;
			case NONE -> style == StationStyle.GERMAN_SBAHN ? ARCH : FLAT;
		};
	}

	/** How the roof reads in a summary, e.g. "arched train shed". */
	public String label() {
		return label;
	}

	public boolean arched() {
		return this != FLAT;
	}
}
