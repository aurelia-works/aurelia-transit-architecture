package com.aureliatransit.architecture.station;

import java.util.List;

/**
 * Ready-made descriptions behind the wrench screen's preset buttons, one per building look: through stations with a building
 * and footbridge, and the termini (St Pancras, Antwerp, Frankfurt, Grand Central, Tokyo, Zurich, Paris, Milan, Berlin, Hong Kong,
 * Beijing) as they are. They are plain descriptions, so the parser, the design rules and the station checks treat them like
 * typed text; the tests keep every preset free of warnings, ignored words, rule breaks and failed checks.
 */
public final class StationPresets {

	/**
	 * @param key         stable id; the button label is {@code screen.aurelia_transit_architecture.station_wrench.preset.<key>}
	 * @param building    the building look the description must give
	 * @param description the text put in the wrench
	 */
	public record Preset(String key, BuildingStyle building, String description) {
	}

	public static final List<Preset> ALL = List.of(
			// the real station: its hall, platforms and operator (see RealStation), not a look after it
			new Preset("utrecht", BuildingStyle.NONE, "utrecht centraal"),
			new Preset("amsterdam", BuildingStyle.AMSTERDAM,
					"two track amsterdam station, side platforms, 64 long, station building with footbridge, named Amsterdam Centraal"),
			new Preset("st_pancras", BuildingStyle.GOTHIC_BRICK,
					"eight track st pancras terminus, four island platforms, 200 long, train shed, named St Pancras"),
			new Preset("antwerp", BuildingStyle.ANTWERP,
					"eight track antwerp terminus, four island platforms, 180 long, train shed, named Antwerpen Centraal"),
			new Preset("frankfurt", BuildingStyle.SANDSTONE,
					"ten track frankfurt terminus, five island platforms, 240 long, train shed, named Frankfurt Hbf"),
			new Preset("hamburg", BuildingStyle.CLINKER,
					"two track hamburg station, side platforms, 64 long, station building with footbridge, named Hamburg Dammtor"),
			new Preset("munich", BuildingStyle.CONCRETE,
					"two track munich station, island platform, 64 long, station building with footbridge, named Munchen Ost"),
			new Preset("grand_central", BuildingStyle.BEAUX_ARTS,
					"eight track grand central terminus, four island platforms, 200 long, named Grand Central"),
			new Preset("tokyo", BuildingStyle.TOKYO,
					"ten track tokyo terminus, five island platforms, 240 long, named Tokyo"),
			new Preset("zurich", BuildingStyle.ZURICH,
					"eight track zurich terminus, four island platforms, 200 long, train shed, named Zurich HB"),
			new Preset("paris", BuildingStyle.PARIS,
					"eight track paris terminus, four island platforms, 200 long, train shed, named Paris Nord"),
			new Preset("milan", BuildingStyle.MILAN,
					"ten track milan terminus, five island platforms, 240 long, train shed, named Milano Centrale"),
			new Preset("berlin", BuildingStyle.BERLIN,
					"six track berlin terminus, three island platforms, 160 long, train shed, named Berlin Hbf"),
			new Preset("hong_kong", BuildingStyle.HONG_KONG,
					"eight track hong kong terminus, four island platforms, 200 long, named West Kowloon"),
			new Preset("beijing", BuildingStyle.BEIJING,
					"eight track beijing terminus, four island platforms, 200 long, named Beijing South"),
			new Preset("bangkok", BuildingStyle.BANGKOK,
					"ten track bangkok terminus, five island platforms, 200 long, train shed, named Krung Thep"),
			new Preset("kuala_lumpur", BuildingStyle.KUALA_LUMPUR,
					"four track kuala lumpur station, two island platforms, 160 long, station building with footbridge, named Kuala Lumpur"),
			new Preset("mumbai", BuildingStyle.MUMBAI,
					"ten track mumbai terminus, five island platforms, 280 long, train shed, named Mumbai CSMT"),
			new Preset("modern", BuildingStyle.MODERN,
					"four track modern station, two island platforms, 96 long, glass canopy, station building with footbridge"));

	private StationPresets() {
	}
}
