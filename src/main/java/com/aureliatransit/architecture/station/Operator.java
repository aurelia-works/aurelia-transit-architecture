package com.aureliatransit.architecture.station;

import java.util.Locale;

/** A railway operator whose mark a station's name signs carry (an ATA sign block with the mark painted at the left end of the row). */
public enum Operator {
	NONE("", "", true),
	NS("ns", "aurelia_transit_architecture:ns_station_sign", true),
	DB("db", "aurelia_transit_architecture:db_station_sign", true),
	GVB("gvb", "aurelia_transit_architecture:gvb_station_sign", true),
	UNDERGROUND("tfl", "aurelia_transit_architecture:underground_station_sign", true),
	NATIONAL_RAIL("nationalrail", "aurelia_transit_architecture:national_rail_station_sign", true),
	// "sbahn" alone is the German S-Bahn look; only "sbahn signs" (operator, logo) is the operator
	SBAHN("sbahn", "aurelia_transit_architecture:sbahn_station_sign", false),
	SBB("sbb", "aurelia_transit_architecture:sbb_station_sign", true),
	JR("jr", "aurelia_transit_architecture:jr_station_sign", true);

	private final String key;
	private final String signId;
	private final boolean bare;

	Operator(String key, String signId, boolean bare) {
		this.key = key;
		this.signId = signId;
		this.bare = bare;
	}

	/** The word the description uses ("ns operator"). */
	public String key() {
		return key;
	}

	/** The name sign block for this operator, or empty for none. */
	public String signId() {
		return signId;
	}

	/** True if the word alone names the operator; false if it needs "operator", "logo" or "signs" after it. */
	public boolean bare() {
		return bare;
	}

	/**
	 * The operator a word or phrase names (NS, Nederlandse Spoorwegen, DB, Deutsche Bahn, GVB, TfL or London Underground,
	 * National Rail, S-Bahn, SBB, JR or JR East), or null.
	 */
	public static Operator of(String word) {
		return switch (word == null ? "" : word.toLowerCase(Locale.ROOT).strip()) {
			case "ns", "nederlandse spoorwegen" -> NS;
			case "db", "deutsche bahn" -> DB;
			case "gvb" -> GVB;
			case "tfl", "roundel", "london underground" -> UNDERGROUND;
			case "nationalrail", "national rail" -> NATIONAL_RAIL;
			case "sbahn" -> SBAHN;
			case "sbb" -> SBB;
			case "jr", "jr east" -> JR;
			case "none", "auto", "" -> NONE;
			default -> null;
		};
	}
}
