package com.aureliatransit.architecture.station.studio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The airport generator page: its own route, an airport whatever the words, its choices, and the close views. */
class AirportStudioTest {

	private static final BlockModels.AssetSource OWN_ASSETS = (namespace, path) -> {
		final Path file = Path.of("src/main/resources/assets", namespace, path);
		return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
	};

	@Test
	void theAirportPageIsItsOwnRouteOfTheSamePage() throws IOException, InterruptedException {
		final StudioServer server = StudioServer.start(OWN_ASSETS, (action, description, encoded) -> "ok", null, 25830);
		try {
			final HttpClient http = HttpClient.newHttpClient();
			final String station = http.send(HttpRequest.newBuilder(URI.create(server.url())).build(), HttpResponse.BodyHandlers.ofString()).body();
			final String airport = http.send(HttpRequest.newBuilder(URI.create(server.url() + "airport")).build(), HttpResponse.BodyHandlers.ofString()).body();
			assertTrue(station.contains("data-page=\"station\""));
			assertTrue(airport.contains("data-page=\"airport\""));
			assertTrue(station.contains("studio.js") && airport.contains("studio.js"), "one script serves both");
			assertTrue(airport.contains("Airport Generator") && airport.contains("Station Generator"), "both cards are in the sidebar");
		} finally {
			server.stop();
		}
	}

	@Test
	void theAirportPageAlwaysPlansAnAirport() {
		assertEquals("airport", StudioApi.description(Map.of("page", "airport")));
		assertEquals("airport, 3 gates", StudioApi.description(Map.of("page", "airport", "text", "3 gates")));
		assertEquals("airport, 2 runways, seed abc123", StudioApi.description(Map.of("page", "airport", "text", "airport, 2 runways", "seed", "abc123")));
		// the station controls the page still carries say nothing about an airport
		assertEquals("small airport", StudioApi.description(Map.of("page", "airport", "text", "small airport", "tracks", "8", "look", "TOKYO", "esc", "1")));
		// the station page keeps planning stations from its controls
		assertFalse(StudioApi.description(Map.of("tracks", "4", "length", "96")).contains("airport"));
	}

	@Test
	void anAirportAnswerHasItsViewsAndItsChoices() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		final String plan = api.plan(Map.of("page", "airport", "text", "small airport named Test"));
		assertTrue(plan.contains("\"views\":{\"terminal\":"), "views");
		assertTrue(plan.contains("\"apron\":") && plan.contains("\"threshold\":"));
		assertTrue(plan.contains("\"airport\":{\"runways\":1"));
		assertTrue(plan.contains("\"buildable\":true"));
		final String remote = api.plan(Map.of("page", "airport", "text", "airport, no terminal, 2 gates"));
		assertFalse(remote.contains("\"terminal\":{\"x\""), "no terminal view without a terminal");
		assertTrue(remote.contains("\"threshold\":"));
		final String station = api.plan(Map.of("text", "2 track station"));
		assertTrue(station.contains("\"views\":null"));
		assertTrue(api.options().contains("\"airports\":[{\"key\":\"regional\""));
	}
}
