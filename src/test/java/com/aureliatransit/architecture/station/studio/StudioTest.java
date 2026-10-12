package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.StationSpec;
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

	/**
	 * Picking a preset sets the page's controls, which are always sent with the description: the station planned from them is
	 * the preset's own (its tracks, length, type, platforms and name), with no clashing or ignored words.
	 */
	@Test
	void presetsPlanAsThemselvesThroughTheControls() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			final Map<String, Object> p = Json.object(Json.parse(StudioApi.preset(preset)));
			final Map<String, String> q = new java.util.HashMap<>();
			for (final String field : List.of("text", "mode", "tracks", "length", "layout", "look", "name")) {
				final Object value = p.get(field);
				q.put(field, value instanceof Double d ? Integer.toString(d.intValue()) : (String) value);
			}
			q.put("building", "1");
			final Map<String, Object> plan = Json.object(Json.parse(api.plan(q)));
			final StationSpec want = StationDescriptionParser.parse(preset.description()).spec();
			final Map<String, Object> built = Json.object(plan.get("built"));
			assertEquals((double) want.trackCount(), built.get("tracks"), preset.key());
			assertEquals((double) want.platformLength(), built.get("length"), preset.key());
			assertEquals(want.terminus() ? "Terminus" : "Through station", built.get("mode"), preset.key());
			assertEquals(want.name(), built.get("name"), preset.key());
			assertTrue(Json.list(plan.get("warnings")).isEmpty(), preset.key() + " warnings: " + plan.get("warnings"));
			assertTrue(Json.list(plan.get("ignored")).isEmpty(), preset.key() + " ignored: " + plan.get("ignored"));
			assertEquals(Boolean.TRUE, plan.get("buildable"), preset.key());
		}
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

	/**
	 * Every boarding face has a view from its platform: the passenger stands on a platform floor with three clear blocks above,
	 * within a few blocks of the face's edge, looking at a point further along.
	 */
	@Test
	void everyFaceHasAViewFromItsPlatform() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		for (final Map<String, String> q : List.of(Map.of("look", "ST_PANCRAS", "mode", "terminus", "tracks", "8", "building", "1"),
				Map.of("tracks", "2", "length", "40"), Map.of("look", "HAMBURG", "tracks", "5", "layout", "SIDE", "roof", "HALL"))) {
			final Map<String, Object> plan = Json.object(Json.parse(api.plan(q)));
			final List<Object> cells = Json.list(plan.get("cells"));
			final java.util.Set<Long> taken = new java.util.HashSet<>();
			for (int i = 0; i < cells.size(); i += 5) {
				taken.add(StudioApi.cell(((Double) cells.get(i)).intValue(), ((Double) cells.get(i + 1)).intValue(), ((Double) cells.get(i + 2)).intValue()));
			}
			final List<Object> faces = Json.list(plan.get("faceLabels"));
			assertFalse(faces.isEmpty(), q.toString());
			for (final Object o : faces) {
				final Map<String, Object> face = Json.object(o);
				final Map<String, Object> view = Json.object(face.get("view"));
				assertNotNull(view, q + " face " + face.get("n"));
				final int x = ((Double) view.get("x")).intValue();
				final int z = ((Double) view.get("z")).intValue();
				assertTrue(taken.contains(StudioApi.cell(x, 0, z)), q + " face " + face.get("n") + " stands on a floor");
				for (int y = 1; y <= 3; y++) {
					assertFalse(taken.contains(StudioApi.cell(x, y, z)), q + " face " + face.get("n") + " has headroom at y " + y);
				}
				final double across = Math.min(Math.abs(x - (Double) face.get("x")), Math.abs(z - (Double) face.get("z")));
				assertTrue(across <= 3, q + " face " + face.get("n") + " stands beside its edge");
				assertTrue(!view.get("tx").equals(view.get("x")) || !view.get("tz").equals(view.get("z")), "looks somewhere else");
			}
		}
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
