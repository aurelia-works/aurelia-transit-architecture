package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The studio's models: properties the plan leaves unset take the game's defaults, even ones the files never name. */
class BlockModelsTest {

	private static final BlockModels.AssetSource OWN_ASSETS = (namespace, path) -> {
		final Path file = Path.of("src/main/resources/assets", namespace, path);
		return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
	};

	private static int parts(BlockModels models, String state) {
		return Json.list(Json.object(Json.parse(models.describe("aurelia_transit_architecture:station_fence", Heading.NORTH, state))).get("parts")).size();
	}

	@Test
	void anEdgeRailingWithoutACornerHasNoCornerPost() {
		final BlockModels models = new BlockModels(OWN_ASSETS);
		// the rail alone: corner is none in the game, which no blockstate part names
		assertEquals(1, parts(models, "kind=platform,edge_south=true,east=true,west=true"));
		assertEquals(2, parts(models, "kind=platform,edge_south=true,corner=south_ccw,west=true"));
		// a free-standing fence (joins come in with the state, as Connections gives them): its post, no corner post either
		assertEquals(1, parts(models, "kind=platform,north=false,east=false,south=false,west=false"));
	}
}
