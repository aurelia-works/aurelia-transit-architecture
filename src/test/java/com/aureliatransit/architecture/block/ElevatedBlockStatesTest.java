package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.elevated.HandrailBlock;
import com.aureliatransit.architecture.block.elevated.PlatformFasciaBlock;
import com.aureliatransit.architecture.block.elevated.PlatformWindscreenBlock;
import com.aureliatransit.architecture.block.elevated.RailingBlock;
import com.aureliatransit.architecture.block.elevated.StairEnclosureBlock;
import com.aureliatransit.architecture.block.elevated.StationFenceBlock;
import com.aureliatransit.architecture.block.elevated.StationStairBlock;
import com.aureliatransit.architecture.block.elevated.TactileJunctionBlock;
import com.aureliatransit.architecture.block.elevated.UtilityRunBlock;
import com.aureliatransit.architecture.block.elevated.ViaductBeamBlock;
import com.aureliatransit.architecture.block.elevated.ViaductBraceBlock;
import com.aureliatransit.architecture.block.elevated.ViaductColumnBlock;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.Bootstrap;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.block.ShapeContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the real 1.3 block classes (unregistered) and checks them against the shipped blockstate JSON: every possible
 * state is covered by exactly the variants/parts the file declares, every referenced model file exists, and every state
 * has a sane, non-empty shape inside a one-block box (railings may be taller for collision).
 */
class ElevatedBlockStatesTest {

	private static final String ASSETS = "/assets/aurelia_transit_architecture/";

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	private static AbstractBlock.Settings settings() {
		return AbstractBlock.Settings.create().nonOpaque();
	}

	private static JsonObject json(String path) throws Exception {
		try (InputStream in = ElevatedBlockStatesTest.class.getResourceAsStream(ASSETS + path)) {
			assertNotNull(in, "missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static void assertModelExists(String model) throws Exception {
		assertTrue(model.startsWith("aurelia_transit_architecture:block/") || model.startsWith("minecraft:block/"), model);
		if (model.startsWith("aurelia_transit_architecture:")) {
			json("models/block/" + model.substring(model.indexOf('/') + 1) + ".json");
		}
	}

	private static String stateKey(BlockState state, List<String> names) {
		final StringBuilder out = new StringBuilder();
		for (final Property<?> property : state.getProperties()) {
			if (!names.contains(property.getName())) {
				continue;
			}
			if (out.length() > 0) {
				out.append(',');
			}
			out.append(property.getName()).append('=').append(valueName(state, property));
		}
		return out.toString();
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.name(state.get(property));
	}

	/** A variant key as Minecraft reads it: an order-independent property map. */
	private static Map<String, String> parse(String key) {
		final Map<String, String> out = new HashMap<>();
		for (final String pair : key.split(",")) {
			if (!pair.isEmpty()) {
				out.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
			}
		}
		return out;
	}

	private static void checkVariants(String id, Block block, List<String> keyProperties) throws Exception {
		final JsonObject variants = json("blockstates/" + id + ".json").getAsJsonObject("variants");
		final Map<Map<String, String>, String> byProperties = new HashMap<>();
		for (final String key : variants.keySet()) {
			assertTrue(byProperties.put(parse(key), key) == null, id + ": duplicate variant " + key);
		}
		final java.util.Set<String> covered = new java.util.HashSet<>();
		for (final BlockState state : block.getStateManager().getStates()) {
			final Map<String, String> wanted = parse(stateKey(state, keyProperties));
			final String key = byProperties.get(wanted);
			assertNotNull(key, id + ": no variant for " + wanted);
			covered.add(key);
			assertModelExists(variants.getAsJsonObject(key).get("model").getAsString());
		}
		assertEquals(variants.keySet(), covered, id + ": variants that match no state");
	}

	private static void checkShapes(String id, Block block, boolean tallCollisionAllowed) {
		for (final BlockState state : block.getStateManager().getStates()) {
			final VoxelShape outline = block.getOutlineShape(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
			assertTrue(!outline.isEmpty(), id + ": empty outline for " + state);
			assertTrue(outline.getMin(Direction.Axis.X) >= -0.0001 && outline.getMax(Direction.Axis.X) <= 1.0001
					&& outline.getMin(Direction.Axis.Y) >= -0.0001 && outline.getMax(Direction.Axis.Y) <= 1.0001
					&& outline.getMin(Direction.Axis.Z) >= -0.0001 && outline.getMax(Direction.Axis.Z) <= 1.0001, id + ": outline leaves the block for " + state);
			final VoxelShape collision = block.getCollisionShape(state, EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
			assertTrue(collision.getMax(Direction.Axis.Y) <= (tallCollisionAllowed ? 1.5001 : 1.0001), id + ": collision too tall for " + state);
		}
	}

	@Test
	void stateCountsAreTheDocumentedFamilySizes() {
		assertEquals(4, new ViaductColumnBlock(settings()).getStateManager().getStates().size());
		assertEquals(2 * 4 * 2, new ViaductBeamBlock(settings()).getStateManager().getStates().size());
		assertEquals(4 * 2, new ViaductBraceBlock(settings()).getStateManager().getStates().size());
		assertEquals(4 * 3, new StairEnclosureBlock(settings()).getStateManager().getStates().size());
		assertEquals(4 * 2, new PlatformWindscreenBlock(settings()).getStateManager().getStates().size());
		assertEquals(4 * 3, new PlatformFasciaBlock(settings()).getStateManager().getStates().size());
		assertEquals(2 * 16, new StationFenceBlock(settings()).getStateManager().getStates().size());
		assertEquals(3 * 2, new UtilityRunBlock(settings()).getStateManager().getStates().size());
		assertEquals(3 * 16, new HandrailBlock(settings()).getStateManager().getStates().size());
		assertEquals(4 * 3, new TactileJunctionBlock(settings()).getStateManager().getStates().size());
	}

	@Test
	void blockstateFilesCoverEveryStateAndReferenceExistingModels() throws Exception {
		checkVariants("viaduct_column", new ViaductColumnBlock(settings()), List.of("style"));
		checkVariants("viaduct_beam", new ViaductBeamBlock(settings()), List.of("axis", "kind", "concrete"));
		checkVariants("viaduct_brace", new ViaductBraceBlock(settings()), List.of("facing", "kind"));
		checkVariants("stair_enclosure", new StairEnclosureBlock(settings()), List.of("facing", "kind"));
		checkVariants("platform_windscreen", new PlatformWindscreenBlock(settings()), List.of("facing", "kind"));
		checkVariants("platform_fascia", new PlatformFasciaBlock(settings()), List.of("facing", "kind"));
		checkVariants("utility_run", new UtilityRunBlock(settings()), List.of("axis", "kind"));
		checkVariants("tactile_junction", new TactileJunctionBlock(settings()), List.of("facing", "kind"));
		checkVariants("station_stair", new StationStairBlock(settings()), List.of("facing", "half", "shape"));
		// changed or added in 1.3 outside the elevated family
		checkVariants("boarding_marker", new com.aureliatransit.architecture.block.wayfinding.BoardingMarkerBlock(settings(), net.minecraft.util.shape.VoxelShapes.fullCube()),
				List.of("facing", "marker"));
		checkVariants("station_info_board", new com.aureliatransit.architecture.block.wayfinding.WayfindingSignBlock(settings(), net.minecraft.util.shape.VoxelShapes.fullCube(),
				new com.aureliatransit.architecture.block.wayfinding.WayfindingPanelSpec(com.aureliatransit.architecture.wayfinding.WayfindingPanelKind.BOARD, 8, 8, 16, 12, 13, -1, true,
						-1, -1, com.aureliatransit.architecture.wayfinding.Pictogram.NONE)), List.of("facing", "left", "right"));
	}

	@Test
	void railingMultipartsReferenceExistingModelsAndOnlyRealProperties() throws Exception {
		for (final String id : List.of("station_fence", "handrail")) {
			final JsonArray parts = json("blockstates/" + id + ".json").getAsJsonArray("multipart");
			assertTrue(parts.size() >= 8, id);
			final Block block = id.equals("station_fence") ? new StationFenceBlock(settings()) : new HandrailBlock(settings());
			final List<String> names = block.getStateManager().getProperties().stream().map(Property::getName).toList();
			for (final JsonElement element : parts) {
				final JsonObject part = element.getAsJsonObject();
				assertModelExists(part.getAsJsonObject("apply").get("model").getAsString());
				final JsonObject when = part.getAsJsonObject("when");
				final JsonArray clauses = when.has("AND") ? when.getAsJsonArray("AND") : new JsonArray();
				if (!when.has("AND")) {
					clauses.add(when);
				}
				for (final JsonElement clause : clauses) {
					for (final String property : clause.getAsJsonObject().keySet()) {
						assertTrue(names.contains(property), id + ": unknown property " + property);
					}
				}
			}
		}
	}

	@Test
	void everyStateHasAShapeInsideTheBlock() {
		checkShapes("viaduct_column", new ViaductColumnBlock(settings()), false);
		checkShapes("viaduct_beam", new ViaductBeamBlock(settings()), false);
		checkShapes("viaduct_brace", new ViaductBraceBlock(settings()), false);
		checkShapes("stair_enclosure", new StairEnclosureBlock(settings()), false);
		checkShapes("platform_windscreen", new PlatformWindscreenBlock(settings()), false);
		checkShapes("platform_fascia", new PlatformFasciaBlock(settings()), false);
		checkShapes("utility_run", new UtilityRunBlock(settings()), false);
		checkShapes("tactile_junction", new TactileJunctionBlock(settings()), false);
		checkShapes("station_fence", new StationFenceBlock(settings()), true);
		checkShapes("handrail", new HandrailBlock(settings()), true);
	}

	@Test
	void railingsConnectOnlyWhereTheStateSays() {
		final StationFenceBlock fence = new StationFenceBlock(settings());
		final BlockState bare = fence.getDefaultState();
		final VoxelShape post = fence.getOutlineShape(bare, EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
		final VoxelShape joined = fence.getOutlineShape(bare.with(RailingBlock.NORTH, true).with(RailingBlock.EAST, true), EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
		assertTrue(joined.getMin(Direction.Axis.Z) < post.getMin(Direction.Axis.Z), "a north connection reaches the north edge");
		assertTrue(joined.getMax(Direction.Axis.X) > post.getMax(Direction.Axis.X), "an east connection reaches the east edge");
		assertEquals(post.getMax(Direction.Axis.Z), joined.getMax(Direction.Axis.Z), 0.0001, "no south connection");
		assertEquals(Direction.Type.HORIZONTAL.stream().count(), 4);
	}
}
