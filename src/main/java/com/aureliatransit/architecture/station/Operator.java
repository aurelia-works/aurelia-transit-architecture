package com.aureliatransit.architecture.station;

import java.util.Locale;

/** A railway operator whose mark a station's name signs carry (an ATA sign block with the mark painted at the left end of the row). */
public enum Operator {
	NONE("", ""),
	NS("ns", "aurelia_transit_architecture:ns_station_sign"),
	DB("db", "aurelia_transit_architecture:db_station_sign"),
	GVB("gvb", "aurelia_transit_architecture:gvb_station_sign");

	private final String key;
	private final String signId;

	Operator(String key, String signId) {
		this.key = key;
		this.signId = signId;
	}

	/** The word the description uses ("ns operator"). */
	public String key() {
		return key;
	}

	/** The name sign block for this operator, or empty for none. */
	public String signId() {
		return signId;
	}

	/** The operator a word or phrase names (NS, Nederlandse Spoorwegen, DB, Deutsche Bahn, GVB), or null. */
	public static Operator of(String word) {
		return switch (word == null ? "" : word.toLowerCase(Locale.ROOT).strip()) {
			case "ns", "nederlandse spoorwegen" -> NS;
			case "db", "deutsche bahn" -> DB;
			case "gvb" -> GVB;
			case "none", "auto", "" -> NONE;
			default -> null;
		};
	}
}
