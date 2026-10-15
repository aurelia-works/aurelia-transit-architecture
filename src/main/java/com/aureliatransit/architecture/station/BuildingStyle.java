package com.aureliatransit.architecture.station;

/**
 * The look of a station building (entrance hall, upper floor and footbridge), each inspired by real European, American or Asian
 * stations and built only from ATA and vanilla blocks: colours, materials and shapes, no operator logos or wordmarks.
 */
public enum BuildingStyle {
	/** No station building. */
	NONE("no building", Facade.GLASS_MODERN),
	/** Light concrete and full-height glass, after Utrecht Centraal and Rotterdam Centraal. */
	MODERN("modern glass hall (after Utrecht Centraal)", Facade.GLASS_MODERN),
	/** Red brick with sandstone bands and arched windows, after Amsterdam Centraal. */
	AMSTERDAM("red brick hall (after Amsterdam Centraal)", Facade.BRICK_TOWERS),
	/** Darker brick with pale stone trim and tall arched windows, after London St Pancras. */
	GOTHIC_BRICK("Gothic brick hall (after London St Pancras)", Facade.BRICK_TOWERS),
	/** Ornate limestone, marble floors and gilded trim, after Antwerpen-Centraal. */
	ANTWERP("limestone and marble hall (after Antwerpen-Centraal)", Facade.ARCHED_GLASS),
	/** Sandstone ashlar on a rusticated plinth with a deep cornice, after Frankfurt Hauptbahnhof. */
	SANDSTONE("sandstone hall (after Frankfurt Hauptbahnhof)", Facade.ARCHED_GLASS),
	/** Clinker brick with stone trim, after Hamburg's brick stations. */
	CLINKER("clinker brick hall (after Hamburg)", Facade.ARCHED_GLASS),
	/** Exposed concrete and steel, after Munich's post-war stations. */
	CONCRETE("concrete hall (after Munich)", Facade.GLASS_MODERN),
	/** Travertine and limestone with marble floors and tall windows, after Grand Central and Washington Union Station. */
	BEAUX_ARTS("Beaux-Arts hall (after Grand Central and Washington Union Station)", Facade.SCULPTED_ATTIC),
	/** Red brick with cream bands, a slate roof and copper domes, after Tokyo Station's Marunouchi front. */
	TOKYO("red brick hall with domes (after Tokyo Station)", Facade.BRICK_TOWERS),
	/** Pale sandstone round one giant arched gateway, after Zürich Hauptbahnhof. */
	ZURICH("sandstone hall with a giant arch (after Zurich HB)", Facade.ARCHED_GLASS),
	/** Pale limestone with one great arched window and statues on the parapet, after Paris Gare du Nord. */
	PARIS("limestone hall with statues (after Paris Gare du Nord)", Facade.SCULPTED_ATTIC),
	/** Pale stone, a stepped tall centre and sculpture on the attic, after Milano Centrale. */
	MILAN("monumental stone hall (after Milano Centrale)", Facade.SCULPTED_ATTIC),
	/** Steel and blue glass with a tall glass corner tower, after Berlin Hauptbahnhof. */
	BERLIN("glass and steel hall (after Berlin Hbf)", Facade.GLASS_MODERN),
	/** White walls and glass under one sweeping wave roof, after Hong Kong West Kowloon. */
	HONG_KONG("white hall with a wave roof (after Hong Kong West Kowloon)", Facade.GLASS_MODERN),
	/** Pale cladding and a glass band under a deep roof turned up at the edge, after Beijing South. */
	BEIJING("glass hall with a deep roof (after Beijing South)", Facade.GLASS_MODERN),
	/** Cream stucco round one great arched window with a clock, between colonnaded wings, after Bangkok's Hua Lamphong. */
	BANGKOK("cream hall with a great arched window (after Bangkok Hua Lamphong)", Facade.ARCHED_GLASS),
	/** White walls, arcades and rows of small domed kiosks and corner minarets, after Kuala Lumpur's old railway station. */
	KUALA_LUMPUR("white hall with domed kiosks (after Kuala Lumpur's old station)", Facade.SCULPTED_ATTIC),
	/** Sandstone with pale stone bands, a great central dome and corner turrets, after Mumbai's Chhatrapati Shivaji Terminus. */
	MUMBAI("Gothic stone hall with a great dome (after Mumbai CSMT)", Facade.BRICK_TOWERS);

	/**
	 * The front of a city or grand terminus head building, one for each kind of real terminal in the photo study: red brick
	 * with clock towers, masonry round giant arched windows, Beaux-Arts stone with a sculpted attic, or modern glass.
	 */
	public enum Facade {
		/** Red brick or banded stone with clock towers, gables, domed pavilions or a great dome and a hipped roof (Amsterdam, St Pancras, Tokyo, Mumbai). */
		BRICK_TOWERS,
		/** Masonry round one, two or three giant arched windows (Hamburg, Antwerp, Zurich, Frankfurt, Bangkok). */
		ARCHED_GLASS,
		/** Stone with tall round-headed windows, a flat roof and a raised attic with a clock and statues (Grand Central, Paris, Milan), or an arcade under domed kiosks (Kuala Lumpur). */
		SCULPTED_ATTIC,
		/** A glass curtain wall with a street canopy, a glass tower, a wave roof or a deep overhang (Utrecht, Munich, Berlin, Hong Kong, Beijing). */
		GLASS_MODERN
	}

	private final String label;
	private final Facade facade;

	BuildingStyle(String label, Facade facade) {
		this.label = label;
		this.facade = facade;
	}

	/** The head building front a terminus in this look gets at city and grand size. */
	public Facade facade() {
		return facade;
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
