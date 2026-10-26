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
			new Preset("remote", "Airstrip", "airport, 1 runway, 2 gates, no terminal, named Remote Airstrip"));

	private AirportPresets() {
	}
}
