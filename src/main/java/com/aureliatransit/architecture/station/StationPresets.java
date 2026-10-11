package com.aureliatransit.architecture.station;

import java.util.List;

/**
 * Ready-made descriptions behind the wrench screen's preset buttons and the guide book, one per building look. They are plain
 * descriptions, so the parser, the design rules and the station checks treat them like typed text; the tests keep every preset
 * free of warnings, ignored words, rule breaks and failed checks.
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
			new Preset("utrecht", BuildingStyle.MODERN,
					"four track utrecht station, two island platforms, 96 long, glass canopy, station building with footbridge, named Utrecht Centraal"),
			new Preset("amsterdam", BuildingStyle.AMSTERDAM,
					"two track amsterdam station, side platforms, 64 long, station building with footbridge, named Amsterdam Centraal"),
			new Preset("st_pancras", BuildingStyle.GOTHIC_BRICK,
					"four track st pancras station, two island platforms, 96 long, station building with footbridge, named St Pancras"),
			new Preset("antwerp", BuildingStyle.ANTWERP,
					"four track antwerp station, two island platforms, 96 long, station building with footbridge, named Antwerpen"),
			new Preset("frankfurt", BuildingStyle.SANDSTONE,
					"three track frankfurt station, island and side platforms, 80 long, station building with footbridge, named Frankfurt Hbf"),
			new Preset("hamburg", BuildingStyle.CLINKER,
					"two track hamburg station, side platforms, 64 long, station building with footbridge, named Hamburg Dammtor"),
			new Preset("munich", BuildingStyle.CONCRETE,
					"two track munich station, island platform, 64 long, station building with footbridge, named Munchen Ost"),
			new Preset("grand_central", BuildingStyle.BEAUX_ARTS,
					"four track grand central station building, two island platforms, 96 long, with footbridge, named Grand Central"));

	private StationPresets() {
	}
}
