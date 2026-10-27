package com.aureliatransit.architecture.station;

import java.util.List;

/**
 * The ready-made airports of Station Studio's Airport Generator and the airport wrench's screen: plain descriptions, so the parser,
 * the design rules and the checks treat them like typed text. The tests keep every one free of warnings and ignored words.
 */
public final class AirportPresets {

	/**
	 * @param key         stable id; the wrench's button label is {@code screen.aurelia_transit_architecture.airport_wrench.preset.<key>}
	 * @param label       the Studio's chip
	 * @param description the words
	 */
	public record Preset(String key, String label, String description) {
	}

	public static final List<Preset> ALL = List.of(
			new Preset("regional", "Regional airport", "airport, one 1700 m runway, 4 gates, terminal, named Regional Airport"),
			new Preset("small", "Small airport", "small airport named Small Airfield"),
			new Preset("twin", "Two runways", "airport, 2 runways, 6 gates, named Twin Runway Airport"),
			new Preset("remote", "Airstrip", "airport, 1 runway, 2 gates, no terminal, named Remote Airstrip"),
			// a generic airport with its railway station under the terminal (Schiphol and Zurich have theirs there, with more tracks)
			new Preset("rail", "Airport with station", "airport, one 1700 m runway, 4 gates, terminal, train station, named Airport Central"),
			// London Southend (EGMC): 05/23 of 1,856 m, a terminal beside the apron; the runway is not turned to its compass heading. Its
			// railway station stands beside the terminal at street level, not under it, so this preset has none
			new Preset("southend", "London Southend", "airport, one 1856 m runway 05/23, 5 gates, terminal, named London Southend"));

	private AirportPresets() {
	}
}
