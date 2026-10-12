package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioTest {

	/** The mod's own assets as the studio reads them outside the game. */
	private static final BlockModels.AssetSource OWN_ASSETS = (namespace, path) -> {
		final Path file = Path.of("src/main/resources/assets", namespace, path);
		return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
	};

	@Test
	void jsonReadsWhatModelFilesUse() {
		final Map<String, Object> model = Json.object(Json.parse("{\"parent\":\"block/cube\", // comment\n \"elements\":[{\"from\":[0,0,0],"
				+ "\"to\":[16,8.5,16],\"shade\":false}],\"x\":true,\"y\":null,\"s\":\"a\\\"b\\u0041\"}"));
		assertEquals("block/cube", model.get("parent"));
		assertEquals(8.5, Json.number(Json.list(Json.object(Json.list(model.get("elements")).get(0)).get("to")).get(1), 0));
		assertEquals(Boolean.TRUE, model.get("x"));
		assertEquals("a\"bA", model.get("s"));
		assertFalse(Json.quote("</script>").contains("<"), "quoted text never closes a script tag");
	}

	@Test
	void planListsEveryBlockWithItsModel() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		final Map<String, String> q = Map.of("look", "HAMBURG", "tracks", "3", "length", "96", "text", "named Altona", "building", "1");
		final Map<String, Object> plan = Json.object(Json.parse(api.plan(q)));
		final String text = (String) plan.get("text");
		final int expected = StationPlanner.plan(StationDescriptionParser.parse(text).spec()).blocks().size();
		assertEquals(expected * 5, Json.list(plan.get("cells")).size(), "five numbers per block");
		assertEquals(Boolean.TRUE, plan.get("buildable"), plan.get("checks").toString());
		final Map<String, Object> built = Json.object(plan.get("built"));
		assertEquals(3.0, built.get("tracks"));
		assertEquals(96.0, built.get("length"));
		assertEquals("Altona", built.get("name"));
		assertNotNull(Json.object(plan.get("passenger")).get("x"), "a clear spot for the passenger view");
		final List<Object> models = Json.list(plan.get("models"));
		long ownWithParts = models.stream().map(Json::object)
				.filter(m -> ((String) m.get("id")).startsWith("aurelia_transit_architecture:"))
				.filter(m -> !Json.list(Json.object(m.get("model")).get("parts")).isEmpty()).count();
		long own = models.stream().map(Json::object).filter(m -> ((String) m.get("id")).startsWith("aurelia_transit_architecture:")).count();
		assertEquals(own, ownWithParts, "every one of the mod's blocks resolves to a model");
	}

	@Test
	void controlsWinAndTypedWordsAreKept() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		final Map<String, Object> plan = Json.object(Json.parse(api.plan(Map.of("tracks", "2", "length", "40", "text", "4 tracks, 100 long, brick"))));
		assertEquals(2.0, Json.object(plan.get("built")).get("tracks"));
		assertEquals(40.0, Json.object(plan.get("built")).get("length"));
		assertTrue(Json.list(plan.get("warnings")).stream().anyMatch(w -> w.toString().contains("given twice")));
		assertEquals(StudioApi.MAX_TYPED, StudioApi.typed(Map.of("text", "x".repeat(5000))).length());
	}

	@Test
	void facingPicksTheVariantAndConnectionsPickParts() {
		final BlockModels models = new BlockModels(OWN_ASSETS);
		final String north = models.describe("aurelia_transit_architecture:fare_gate", Heading.NORTH, "");
		final String east = models.describe("aurelia_transit_architecture:fare_gate", Heading.EAST, "");
		assertFalse(north.equals(east), "a turned block uses a turned variant");
		final String alone = models.describe("aurelia_transit_architecture:handrail", Heading.NORTH, "north=false,east=false,south=false,west=false");
		final String joined = models.describe("aurelia_transit_architecture:handrail", Heading.NORTH, "north=true,east=false,south=true,west=false");
		assertTrue(alone.contains("\"from\""), "a lone handrail still has its post: " + alone);
		assertTrue(joined.split("\"from\"").length > alone.split("\"from\"").length, "a joined handrail adds its side pieces");
	}

	@Test
	void serverAnswersOnlyThisComputerAndGuardsTheGame() throws IOException, InterruptedException {
		final StudioServer server = StudioServer.start(OWN_ASSETS, (action, description, encoded) -> "ok " + action);
		try {
			final HttpClient http = HttpClient.newHttpClient();
			final String base = server.url();
			final HttpResponse<String> page = http.send(HttpRequest.newBuilder(URI.create(base)).build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(200, page.statusCode());
			final String token = page.body().replaceAll("(?s).*data-token=\"([0-9a-f]+)\".*", "$1");
			assertEquals(32, token.length());
			final HttpRequest.Builder send = HttpRequest.newBuilder(URI.create(base + "api/send")).POST(HttpRequest.BodyPublishers.ofString("action=plan"));
			assertEquals(403, http.send(send.build(), HttpResponse.BodyHandlers.ofString()).statusCode(), "no token, no action");
			final HttpResponse<String> ok = http.send(send.header("X-Studio-Token", token).build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(200, ok.statusCode());
			assertEquals("ok plan", ok.body());
			final HttpResponse<String> plan = http.send(HttpRequest.newBuilder(URI.create(base + "api/plan"))
					.POST(HttpRequest.BodyPublishers.ofString("tracks=1&length=20")).build(), HttpResponse.BodyHandlers.ofString());
			assertTrue(plan.body().contains("\"cells\""));
			assertEquals(404, http.send(HttpRequest.newBuilder(URI.create(base + "tex/../../secret.png")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
		} finally {
			server.stop();
		}
	}
}
