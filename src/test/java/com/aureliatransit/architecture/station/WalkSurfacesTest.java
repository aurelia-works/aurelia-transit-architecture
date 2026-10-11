package com.aureliatransit.architecture.station;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every block a station plans as something to walk on (platform paving and edges, building floors, footbridge decks, the light
 * panels set into upper floors) has a full top face, so no walkway has a pit or a step where a thin model sits. Checked through
 * the real blockstate and model files, parents included.
 */
class WalkSurfacesTest {

	private static final String ATA = "aurelia_transit_architecture:";
	private static final String ASSETS = "/assets/aurelia_transit_architecture/";
	private static final Set<Role> WALKED_ON = Set.of(Role.PLATFORM_SURFACE, Role.PLATFORM_EDGE, Role.FLOOR, Role.BRIDGE_DECK, Role.LIGHT_PANEL);

	@Test
	void everyPlannedWalkSurfaceHasAFullTopFace() throws IOException {
		final Map<String, String> usedBy = new TreeMap<>();
		final List<String> descriptions = new ArrayList<>();
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			descriptions.add(preset.description());
		}
		for (final StationStyle style : StationStyle.values()) {
			descriptions.add("two track " + style.name().toLowerCase() + " station, island platform, 48 long, station building with footbridge");
			descriptions.add("two track " + style.name().toLowerCase() + " station, side platforms, 48 long, hall roof");
		}
		for (final String description : descriptions) {
			for (final PlannedBlock block : StationPlanner.plan(StationDescriptionParser.parse(description).spec()).blocks()) {
				if (WALKED_ON.contains(block.role()) && block.blockId().startsWith(ATA)) {
					usedBy.putIfAbsent(block.blockId(), block.role() + " in \"" + description + "\"");
				}
			}
		}
		assertTrue(usedBy.size() > 10, "expected many walk surface blocks, got " + usedBy.keySet());
		final List<String> thin = new ArrayList<>();
		for (final Map.Entry<String, String> entry : usedBy.entrySet()) {
			for (final String model : models(entry.getKey().substring(ATA.length()))) {
				if (!fullTop(model, new HashSet<>())) {
					thin.add(entry.getKey() + " (model " + model + ", " + entry.getValue() + ")");
				}
			}
		}
		assertTrue(thin.isEmpty(), "walk surfaces without a full top face:\n" + String.join("\n", thin));
	}

	/** The models a block's blockstate uses, as resource ids. */
	private static Set<String> models(String block) throws IOException {
		final JsonObject state = json(ASSETS + "blockstates/" + block + ".json");
		final Set<String> models = new HashSet<>();
		if (state.has("variants")) {
			for (final Map.Entry<String, JsonElement> variant : state.getAsJsonObject("variants").entrySet()) {
				collectModels(variant.getValue(), models);
			}
		}
		if (state.has("multipart")) {
			for (final JsonElement part : state.getAsJsonArray("multipart")) {
				collectModels(part.getAsJsonObject().get("apply"), models);
			}
		}
		assertTrue(!models.isEmpty(), "no models in blockstate " + block);
		return models;
	}

	private static void collectModels(JsonElement entry, Set<String> models) {
		if (entry.isJsonArray()) {
			for (final JsonElement each : entry.getAsJsonArray()) {
				models.add(each.getAsJsonObject().get("model").getAsString());
			}
		} else {
			models.add(entry.getAsJsonObject().get("model").getAsString());
		}
	}

	/** Whether the model's top is a whole face at height 16: a vanilla cube parent, or an element covering the full top. */
	private static boolean fullTop(String model, Set<String> seen) throws IOException {
		if (!seen.add(model)) {
			return false;
		}
		final String path = model.contains(":") ? model.substring(model.indexOf(':') + 1) : model;
		if (!model.startsWith(ATA)) {
			// vanilla parents: the cube family has a full top, anything else (slabs, panes, stairs) does not count
			return path.startsWith("block/cube") || path.equals("block/orientable") || path.equals("block/leaves");
		}
		final JsonObject json = json(ASSETS + "models/" + path + ".json");
		if (json.has("elements")) {
			for (final JsonElement element : json.getAsJsonArray("elements")) {
				final JsonArray from = element.getAsJsonObject().getAsJsonArray("from");
				final JsonArray to = element.getAsJsonObject().getAsJsonArray("to");
				if (from.get(0).getAsDouble() <= 0 && from.get(2).getAsDouble() <= 0 && to.get(0).getAsDouble() >= 16 && to.get(2).getAsDouble() >= 16
						&& to.get(1).getAsDouble() >= 16) {
					return true;
				}
			}
			return false;
		}
		return json.has("parent") && fullTop(json.get("parent").getAsString(), seen);
	}

	private static JsonObject json(String path) throws IOException {
		try (InputStream in = WalkSurfacesTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
