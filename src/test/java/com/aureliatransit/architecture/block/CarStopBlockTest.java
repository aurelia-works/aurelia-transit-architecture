package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.wayfinding.CarStopBlock;
import com.aureliatransit.architecture.block.wayfinding.CarStopCycle;
import com.aureliatransit.architecture.block.wayfinding.CarStopMarkerBlock;
import com.aureliatransit.architecture.block.wayfinding.StopBoardBlock;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static com.aureliatransit.architecture.util.Shapes.box;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The car stop number cycle, and every state of the real block classes against the shipped blockstate files.
 */
class CarStopBlockTest {

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	void cycleCountsUpAndWraps() {
		assertEquals(2, CarStopCycle.step(1, 1, 12, false));
		assertEquals(1, CarStopCycle.step(12, 1, 12, false));
		assertEquals(0, CarStopCycle.step(12, 0, 12, false));
	}

	@Test
	void sneakCountsDownAndWraps() {
		assertEquals(3, CarStopCycle.step(4, 1, 12, true));
		assertEquals(12, CarStopCycle.step(1, 1, 12, true));
		assertEquals(12, CarStopCycle.step(0, 0, 12, true));
	}

	@Test
	void everyStepVisitsEveryNumberOnce() {
		int value = 0;
		final java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (int i = 0; i < 13; i++) {
			seen.add(value);
			value = CarStopCycle.step(value, 0, 12, false);
		}
		assertEquals(13, seen.size());
		assertEquals(0, value);
	}

	@Test
	void ukBoardsStartAtOneAndStopBoardsAtTheStopHereState() {
		final CarStopBlock uk = new CarStopMarkerBlock(AbstractBlock.Settings.create().nonOpaque(), box(4, 6, 7, 12, 13, 9), box(4, 5, 14.5, 12, 12, 16));
		final CarStopBlock stop = new StopBoardBlock(AbstractBlock.Settings.create().nonOpaque(), box(4, 6, 7, 12, 14, 9), box(4, 4, 14.5, 12, 12, 16));
		assertEquals(12, uk.getStateManager().getStates().size() / 8);
		assertEquals(13, stop.getStateManager().getStates().size() / 8);
		assertEquals(4, uk.getDefaultState().get(uk.cars()));
		assertFalse(uk.getDefaultState().get(CarStopBlock.WALL));
	}

	@Test
	void everyStateHasABlockstateVariant() {
		check("uk_car_stop_marker", new CarStopMarkerBlock(AbstractBlock.Settings.create().nonOpaque(), VoxelShapes.fullCube(), VoxelShapes.fullCube()));
		check("german_stop_board", new StopBoardBlock(AbstractBlock.Settings.create().nonOpaque(), VoxelShapes.fullCube(), VoxelShapes.fullCube()));
		check("dutch_stop_board", new StopBoardBlock(AbstractBlock.Settings.create().nonOpaque(), VoxelShapes.fullCube(), VoxelShapes.fullCube()));
	}

	private static void check(String id, CarStopBlock block) {
		final JsonObject variants = read("/assets/aurelia_transit_architecture/blockstates/" + id + ".json").getAsJsonObject("variants");
		assertEquals(block.getStateManager().getStates().size(), variants.size(), id);
		for (BlockState state : block.getStateManager().getStates()) {
			final String key = state.getEntries().entrySet().stream()
					.map(e -> e.getKey().getName() + "=" + name(e.getKey(), e.getValue()))
					.sorted().collect(Collectors.joining(","));
			assertTrue(variants.has(key), id + " lacks variant " + key);
			final String model = variants.getAsJsonObject(key).get("model").getAsString().replace("aurelia_transit_architecture:", "");
			assertTrue(CarStopBlockTest.class.getResource("/assets/aurelia_transit_architecture/models/" + model + ".json") != null, "missing model " + model);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String name(Property property, Comparable value) {
		return property.name(value);
	}

	private static JsonObject read(String path) {
		try (InputStream in = CarStopBlockTest.class.getResourceAsStream(path)) {
			assertTrue(in != null, "missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}
}
