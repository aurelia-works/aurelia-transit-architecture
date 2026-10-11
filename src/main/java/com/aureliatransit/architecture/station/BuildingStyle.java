package com.aureliatransit.architecture.station;

/**
 * The look of a station building (entrance hall, upper floor and footbridge), each inspired by real European or American
 * stations and built only from ATA and vanilla blocks: colours, materials and shapes, no operator logos or wordmarks.
 */
public enum BuildingStyle {
	/** No station building. */
	NONE("no building"),
	/** Light concrete and full-height glass, after Utrecht Centraal and Rotterdam Centraal. */
	MODERN("modern glass hall (after Utrecht Centraal)"),
	/** Red brick with sandstone bands and arched windows, after Amsterdam Centraal. */
	AMSTERDAM("red brick hall (after Amsterdam Centraal)"),
	/** Darker brick with pale stone trim and tall arched windows, after London St Pancras. */
	GOTHIC_BRICK("Gothic brick hall (after London St Pancras)"),
	/** Ornate limestone, marble floors and gilded trim, after Antwerpen-Centraal. */
	ANTWERP("limestone and marble hall (after Antwerpen-Centraal)"),
	/** Sandstone ashlar on a rusticated plinth with a deep cornice, after Frankfurt Hauptbahnhof. */
	SANDSTONE("sandstone hall (after Frankfurt Hauptbahnhof)"),
	/** Clinker brick with stone trim, after Hamburg's brick stations. */
	CLINKER("clinker brick hall (after Hamburg)"),
	/** Exposed concrete and steel, after Munich's post-war stations. */
	CONCRETE("concrete hall (after Munich)"),
	/** Travertine and limestone with marble floors and tall windows, after Grand Central and Washington Union Station. */
	BEAUX_ARTS("Beaux-Arts hall (after Grand Central and Washington Union Station)");

	private final String label;

	BuildingStyle(String label) {
		this.label = label;
	}

	/** Plain-words name for the summary. */
	public String label() {
		return label;
	}

	/** The building a station style gets when a building is asked for without a city or landmark. */
	public static BuildingStyle forStyle(StationStyle style) {
		return switch (style) {
			case MODERN -> MODERN;
			case DUTCH -> AMSTERDAM;
			case GERMAN_SBAHN -> SANDSTONE;
			case METRO -> CONCRETE;
		};
	}
}
