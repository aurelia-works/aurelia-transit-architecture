package com.aureliatransit.architecture.station;

/** The cross-section of a hall roof (train shed); canopies ignore it. */
public enum ShedShape {
	/** A flat roof on a grid of girders. */
	FLAT,
	/** One shallow barrel vault over the whole railway (Hamburg, Amsterdam, Antwerp). */
	ARCH,
	/** One tall pointed (Gothic) arch over the whole railway (St Pancras). */
	POINTED,
	/** A row of barrel vaults side by side, one over each group of tracks (Frankfurt, Leipzig). */
	MULTI_ARCH;

	/** The shed that suits a building look (or, without a building, a platform style). */
	public static ShedShape forLook(BuildingStyle building, StationStyle style) {
		return switch (building) {
			case CLINKER, AMSTERDAM, ANTWERP -> ARCH;
			case GOTHIC_BRICK -> POINTED;
			case SANDSTONE -> MULTI_ARCH;
			case MODERN, CONCRETE, BEAUX_ARTS -> FLAT;
			case NONE -> style == StationStyle.GERMAN_SBAHN ? ARCH : FLAT;
		};
	}

	public boolean arched() {
		return this != FLAT;
	}
}
