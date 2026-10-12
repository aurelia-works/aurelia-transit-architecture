package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every style and material combination resolves every role to an existing ATA block (or the two allowed outside blocks), and
 * floors and edges only ever use blocks that count as MTR platforms.
 */
class StylePaletteTest {

	private static final String ATA = "aurelia_transit_architecture:";

	private static final Set<String> EDGES = Set.of(ATA + "platform_edge_warning", ATA + "platform_edge", ATA + "sbahn_platform_edge_tactile",
			ATA + "sbahn_platform_edge", ATA + "metro_tactile_edge");
	private static final Set<String> SURFACES = Set.of(ATA + "platform_paving_light", ATA + "platform_paving_dark",
			ATA + "leidsche_rijn_concrete_platform_paver", ATA + "sbahn_platform_slab", ATA + "metro_terrazzo_floor", ATA + "metro_granite_floor");

	private static StationSpec spec(StationStyle style, SurfaceMaterial surface, RoofMaterial roofMaterial, RoofType roof, boolean tactile) {
		return StationSpec.builder().style(style).surface(surface).roofMaterial(roofMaterial).roof(roof).tactile(tactile).build();
	}

	private static void forEachCombination(Consumer<StationSpec> check) {
		for (final StationStyle style : StationStyle.values()) {
			for (final SurfaceMaterial surface : SurfaceMaterial.values()) {
				for (final RoofMaterial roofMaterial : RoofMaterial.values()) {
					for (final RoofType roof : RoofType.values()) {
						for (final boolean tactile : new boolean[] {true, false}) {
							check.accept(spec(style, surface, roofMaterial, roof, tactile));
						}
					}
				}
			}
		}
	}

	private static String label(StationSpec spec) {
		return spec.style() + "/" + spec.surface() + "/" + spec.roofMaterial() + "/" + spec.roof() + "/tactile=" + spec.tactile();
	}

	@Test
	void everyRoleHasAnIdThatExists() {
		final Set<String> checked = new HashSet<>();
		forEachCombination(spec -> {
			final StylePalette palette = StylePalette.of(spec);
			for (final Role role : Role.values()) {
				final String id = palette.blockId(role);
				assertNotNull(id, label(spec) + " " + role);
				if (id.startsWith(ATA)) {
					if (checked.add(id)) {
						assertTrue(hasBlockstate(id.substring(ATA.length())), "no blockstate for " + id + " (" + label(spec) + " " + role + ")");
					}
				} else {
					assertTrue(id.equals(StylePalette.MTR_RAIL_NODE) || id.equals(StylePalette.MTR_LIFT_TRACK) || id.equals("minecraft:stone_bricks"),
							"unexpected id " + id + " for " + role);
				}
			}
		});
		assertTrue(checked.size() > 30, "expected many distinct ATA blocks, got " + checked.size());
	}

	@Test
	void railNodeAndFoundationNeverChange() {
		forEachCombination(spec -> {
			final StylePalette palette = StylePalette.of(spec);
			assertEquals("mtr:rail", palette.blockId(Role.RAIL_NODE), label(spec));
			assertEquals("minecraft:stone_bricks", palette.blockId(Role.FOUNDATION), label(spec));
		});
	}

	@Test
	void edgesAndSurfacesAreMtrPlatformBlocks() {
		forEachCombination(spec -> {
			final StylePalette palette = StylePalette.of(spec);
			assertTrue(EDGES.contains(palette.blockId(Role.PLATFORM_EDGE)), label(spec) + " edge " + palette.blockId(Role.PLATFORM_EDGE));
			assertTrue(SURFACES.contains(palette.blockId(Role.PLATFORM_SURFACE)), label(spec) + " surface " + palette.blockId(Role.PLATFORM_SURFACE));
		});
	}

	@Test
	void tactileChoosesTheTactileEdgeOfTheStyle() {
		assertEdge(StationStyle.MODERN, true, "platform_edge_warning");
		assertEdge(StationStyle.MODERN, false, "platform_edge");
		assertEdge(StationStyle.DUTCH, true, "platform_edge_warning");
		assertEdge(StationStyle.DUTCH, false, "platform_edge");
		assertEdge(StationStyle.GERMAN_SBAHN, true, "sbahn_platform_edge_tactile");
		assertEdge(StationStyle.GERMAN_SBAHN, false, "sbahn_platform_edge");
		assertEdge(StationStyle.METRO, true, "metro_tactile_edge");
		assertEdge(StationStyle.METRO, false, "platform_edge");
	}

	private static void assertEdge(StationStyle style, boolean tactile, String expected) {
		final StylePalette palette = StylePalette.of(spec(style, SurfaceMaterial.STYLE_DEFAULT, RoofMaterial.STYLE_DEFAULT, RoofType.CANOPY, tactile));
		assertEquals(ATA + expected, palette.blockId(Role.PLATFORM_EDGE), style + " tactile=" + tactile);
	}

	@Test
	void styleDefaultsMatchTheStyleTable() {
		assertStyle(StationStyle.MODERN, RoofType.CANOPY, "platform_paving_light", "steel_column_square", "steel_column_square", "canopy_flat", "canopy_edge",
				"canopy_light", "platform_lamp", "steel_bench", "station_name_sign");
		assertStyle(StationStyle.MODERN, RoofType.HALL, "platform_paving_light", "steel_column_square", "steel_column_square", "canopy_flat", "canopy_edge",
				"canopy_light", "platform_lamp", "steel_bench", "station_name_sign");
		assertStyle(StationStyle.DUTCH, RoofType.CANOPY, "leidsche_rijn_concrete_platform_paver", "steel_column_round", "utrecht_tree_column_white",
				"leidsche_rijn_timber_soffit_canopy", "canopy_edge", "canopy_light", "platform_lamp", "wooden_bench", "dutch_station_sign");
		assertStyle(StationStyle.DUTCH, RoofType.HALL, "leidsche_rijn_concrete_platform_paver", "steel_column_round", "utrecht_tree_column_white",
				"utrecht_wave_roof_panel", "utrecht_wave_roof_edge", "canopy_light", "platform_lamp", "wooden_bench", "dutch_station_sign");
		assertStyle(StationStyle.GERMAN_SBAHN, RoofType.CANOPY, "sbahn_platform_slab", "steel_column_round", "steel_column_round", "canopy_flat",
				"canopy_edge", "canopy_light", "sbahn_platform_light", "steel_bench", "german_station_sign");
		assertStyle(StationStyle.GERMAN_SBAHN, RoofType.HALL, "sbahn_platform_slab", "steel_column_round", "steel_column_round", "munich_ribbed_roof_deck",
				"canopy_edge", "canopy_light", "sbahn_platform_light", "steel_bench", "german_station_sign");
		assertStyle(StationStyle.METRO, RoofType.CANOPY, "metro_terrazzo_floor", "metro_column_tiled", "metro_column_tiled", "metro_acoustic_ceiling",
				"metro_acoustic_ceiling", "metro_recessed_light", "platform_lamp", "steel_bench", "station_name_sign");
		assertStyle(StationStyle.METRO, RoofType.NONE, "metro_terrazzo_floor", "metro_column_tiled", "metro_column_tiled", "metro_acoustic_ceiling",
				"metro_acoustic_ceiling", "metro_recessed_light", "platform_lamp", "steel_bench", "station_name_sign");
	}

	private static void assertStyle(StationStyle style, RoofType roof, String surface, String column, String cap, String roofId, String roofEdge,
			String light, String lamp, String bench, String sign) {
		final StylePalette palette = StylePalette.of(spec(style, SurfaceMaterial.STYLE_DEFAULT, RoofMaterial.STYLE_DEFAULT, roof, true));
		final String label = style + "/" + roof;
		assertEquals(ATA + surface, palette.blockId(Role.PLATFORM_SURFACE), label);
		assertEquals(ATA + column, palette.blockId(Role.COLUMN), label);
		assertEquals(ATA + cap, palette.blockId(Role.COLUMN_CAP), label);
		assertEquals(ATA + "station_girder", palette.blockId(Role.BEAM), label);
		assertEquals(ATA + roofId, palette.blockId(Role.ROOF), label);
		assertEquals(ATA + roofEdge, palette.blockId(Role.ROOF_EDGE), label);
		assertEquals(ATA + light, palette.blockId(Role.ROOF_LIGHT), label);
		assertEquals(ATA + lamp, palette.blockId(Role.LAMP), label);
		assertEquals(ATA + bench, palette.blockId(Role.BENCH), label);
		assertEquals(ATA + "waste_bin", palette.blockId(Role.BIN), label);
		assertEquals(ATA + sign, palette.blockId(Role.NAME_SIGN), label);
		assertEquals(ATA + "platform_ramp_lower", palette.blockId(Role.RAMP_LOWER), label);
		assertEquals(ATA + "platform_ramp_upper", palette.blockId(Role.RAMP_UPPER), label);
	}

	@Test
	void surfaceOverridesApplyToTheFloorOnly() {
		final String[][] expected = {{"LIGHT_PAVING", "platform_paving_light"}, {"DARK_PAVING", "platform_paving_dark"},
				{"CONCRETE", "leidsche_rijn_concrete_platform_paver"}, {"TERRAZZO", "metro_terrazzo_floor"}, {"GRANITE", "metro_granite_floor"}};
		for (final StationStyle style : StationStyle.values()) {
			final StylePalette plain = StylePalette.of(spec(style, SurfaceMaterial.STYLE_DEFAULT, RoofMaterial.STYLE_DEFAULT, RoofType.CANOPY, true));
			for (final String[] pair : expected) {
				final StylePalette palette = StylePalette.of(spec(style, SurfaceMaterial.valueOf(pair[0]), RoofMaterial.STYLE_DEFAULT, RoofType.CANOPY, true));
				assertEquals(ATA + pair[1], palette.blockId(Role.PLATFORM_SURFACE), style + " " + pair[0]);
				for (final Role role : Role.values()) {
					if (role != Role.PLATFORM_SURFACE) {
						assertEquals(plain.blockId(role), palette.blockId(role), style + " " + pair[0] + " " + role);
					}
				}
			}
		}
	}

	@Test
	void roofMaterialOverridesApplyToTheRoofDeckOnly() {
		for (final StationStyle style : StationStyle.values()) {
			for (final RoofType roof : RoofType.values()) {
				final StylePalette plain = StylePalette.of(spec(style, SurfaceMaterial.STYLE_DEFAULT, RoofMaterial.STYLE_DEFAULT, roof, true));
				final boolean hall = roof == RoofType.HALL;
				assertRoof(style, roof, RoofMaterial.STEEL, "canopy_flat", plain);
				assertRoof(style, roof, RoofMaterial.GLASS, hall ? "hamburg_glass_roof" : "canopy_skylight", plain);
				assertRoof(style, roof, RoofMaterial.TIMBER, "leidsche_rijn_timber_soffit_canopy", plain);
			}
		}
	}

	private static void assertRoof(StationStyle style, RoofType roof, RoofMaterial material, String expected, StylePalette plain) {
		final StylePalette palette = StylePalette.of(spec(style, SurfaceMaterial.STYLE_DEFAULT, material, roof, true));
		final String label = style + "/" + roof + "/" + material;
		assertEquals(ATA + expected, palette.blockId(Role.ROOF), label);
		for (final Role role : Role.values()) {
			if (role != Role.ROOF) {
				assertEquals(plain.blockId(role), palette.blockId(role), label + " " + role);
			}
		}
	}

	private static boolean hasBlockstate(String id) {
		try (InputStream in = StylePaletteTest.class.getResourceAsStream("/assets/aurelia_transit_architecture/blockstates/" + id + ".json")) {
			return in != null;
		} catch (IOException e) {
			return false;
		}
	}

	@Test
	void buildingMaterialsExistForEveryStyle() {
		final Set<String> checked = new HashSet<>();
		for (final BuildingStyle style : BuildingStyle.values()) {
			if (style == BuildingStyle.NONE) {
				continue;
			}
			final StylePalette.Building building = StylePalette.Building.of(style);
			for (final String id : new String[]{building.wall(), building.trim(), building.plinth(), building.cornice(), building.window(), building.floor(),
					building.upperFloor(), building.roof(), building.light(), building.stair(), building.deck(), building.bridgeWall(), building.bridgeRoof(),
					building.bridgeLight(), building.pier(), building.bench(), building.interiorWall()}) {
				assertTrue(id.startsWith(ATA) || id.startsWith("minecraft:"), style + " " + id);
				if (id.startsWith(ATA) && checked.add(id)) {
					assertTrue(hasBlockstate(id.substring(ATA.length())), "no blockstate for " + id + " (" + style + ")");
				}
			}
		}
		assertTrue(checked.size() > 20, "expected many building blocks, got " + checked.size());
	}

	/**
	 * A building's solid surfaces (walls, trim bands, plinth, floors, roof, interior walls) are full cubes, so a facade reads
	 * as one flush wall: no thin decorative panel in the wall line that leaves gaps you can see through.
	 */
	@Test
	void buildingSurfacesAreFullCubes() {
		for (final BuildingStyle style : BuildingStyle.values()) {
			final StylePalette.Building building = StylePalette.Building.of(style);
			for (final String id : new String[]{building.wall(), building.trim(), building.plinth(), building.floor(), building.upperFloor(),
					building.roof(), building.interiorWall()}) {
				if (id.startsWith(ATA)) {
					assertTrue(fullCube(blockModel(id.substring(ATA.length()))), style + ": " + id + " is not a full cube");
				}
			}
		}
	}

	private static String read(String path) {
		try (InputStream in = StylePaletteTest.class.getResourceAsStream(path)) {
			return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	/** The first model a block's blockstate names, without its namespace. */
	private static String blockModel(String id) {
		final String state = read("/assets/aurelia_transit_architecture/blockstates/" + id + ".json");
		assertNotNull(state, id);
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"model\"\\s*:\\s*\"[^\"]*block/([^\"]+)\"").matcher(state);
		assertTrue(m.find(), id);
		return m.group(1);
	}

	/** True for a cube parent, or an element from 0,0,0 to 16,16,16. */
	private static boolean fullCube(String model) {
		final String json = read("/assets/aurelia_transit_architecture/models/block/" + model + ".json");
		assertNotNull(json, model);
		final com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		if (!root.has("elements")) {
			final String parent = root.has("parent") ? root.get("parent").getAsString() : "";
			if (parent.startsWith("aurelia_transit_architecture:block/")) {
				return fullCube(parent.substring("aurelia_transit_architecture:block/".length()));
			}
			return parent.contains("cube");
		}
		for (final com.google.gson.JsonElement element : root.getAsJsonArray("elements")) {
			final String from = element.getAsJsonObject().get("from").toString();
			final String to = element.getAsJsonObject().get("to").toString();
			if (from.equals("[0,0,0]") && to.equals("[16,16,16]")) {
				return true;
			}
		}
		return false;
	}
}
