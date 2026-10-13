package com.aureliatransit.architecture.station;

/** The profile of a platform canopy's roof; a hall roof ignores it. */
public enum CanopyShape {
	/** A level roof (plain and modern looks, and the Dutch ones: Utrecht's wave is its hall roof, its platform canopies are flat). */
	FLAT,
	/** A V across the platform: low over the column line, rising to both edges (the German S-Bahn, Berlin, Munich). */
	BUTTERFLY,
	/** A pitched roof with its ridge over the column line, falling to both edges (heritage stations). */
	GABLE;

	/** The canopy that suits a building look (or, without a building, a platform style). */
	public static CanopyShape forLook(BuildingStyle building, StationStyle style) {
		return switch (building) {
			case BERLIN, CONCRETE, SANDSTONE, CLINKER -> BUTTERFLY;
			case GOTHIC_BRICK, AMSTERDAM, ANTWERP, PARIS, BEAUX_ARTS, TOKYO, ZURICH, MILAN -> GABLE;
			case MODERN, HONG_KONG, BEIJING -> FLAT;
			case NONE -> switch (style) {
				case GERMAN_SBAHN -> BUTTERFLY;
				case DUTCH, MODERN, METRO -> FLAT;
			};
		};
	}

	/** The words that describe it in a station's headline. */
	public String label() {
		return switch (this) {
			case FLAT -> "flat";
			case BUTTERFLY -> "butterfly";
			case GABLE -> "gable";
		};
	}
}
