package com.aureliatransit.architecture.presentation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wall terminals join by blockstate (left/right) and model alone. These tests run the same neighbour rule on a small
 * grid and check, from the shipped JSON, that the selected models form one continuous screen with the outer bezel
 * kept.
 */
class TerminalJoinTest {

	private static final String BASE = "/assets/aurelia_transit_architecture/";
	private static final Set<Direction> FACINGS = Set.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

	private static JsonObject json(String path) throws IOException {
		try (InputStream in = TerminalJoinTest.class.getResourceAsStream(BASE + path)) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	/** (left, right) of the terminal at offset {@code x} along its row; blocks sit at the offsets in {@code row}. */
	private static boolean[] connections(Set<Integer> row, int x) {
		return new boolean[] {row.contains(x - 1), row.contains(x + 1)};
	}

	private static JsonObject screen(boolean left, boolean right) throws IOException {
		final String suffix = (left ? "_l" : "") + (right ? "_r" : "");
		final JsonArray elements = json("models/block/passenger_info_terminal" + suffix + ".json").getAsJsonArray("elements");
		return elements.get(1).getAsJsonObject();
	}

	private static float[] range(JsonObject screen) {
		final JsonArray from = screen.getAsJsonArray("from");
		final JsonArray to = screen.getAsJsonArray("to");
		return new float[] {from.get(0).getAsFloat(), to.get(0).getAsFloat()};
	}

	@Test
	void neighbourRuleForOneTwoAndThreeWide() {
		assertTrue(java.util.Arrays.equals(new boolean[] {false, false}, connections(Set.of(0), 0)));
		assertTrue(java.util.Arrays.equals(new boolean[] {false, true}, connections(Set.of(0, 1), 0)));
		assertTrue(java.util.Arrays.equals(new boolean[] {true, false}, connections(Set.of(0, 1), 1)));
		assertTrue(java.util.Arrays.equals(new boolean[] {true, true}, connections(Set.of(0, 1, 2), 1)));
		assertTrue(java.util.Arrays.equals(new boolean[] {false, false}, connections(Set.of(0, 2), 0)), "a gap does not join");
	}

	@Test
	void growingAndShrinkingRestoresOuterEdges() throws IOException {
		// 1 -> 2 -> 1: placing then breaking the second block
		assertOuterEdges(Set.of(0));
		assertOuterEdges(Set.of(0, 1));
		assertOuterEdges(Set.of(0));
		assertOuterEdges(Set.of(0, 1, 2));
		assertOuterEdges(Set.of(0, 2));
	}

	/** In a contiguous run the first block keeps its left bezel, the last its right bezel, nothing between has one. */
	private void assertOuterEdges(Set<Integer> row) throws IOException {
		for (final int x : row) {
			final boolean[] c = connections(row, x);
			final float[] r = range(screen(c[0], c[1]));
			assertEquals(c[0] ? 0F : 0.75F, r[0], 0.0001F, "left bezel at " + row + " x=" + x);
			assertEquals(c[1] ? 16F : 15.25F, r[1], 0.0001F, "right bezel at " + row + " x=" + x);
		}
	}

	@Test
	void screenNeverSamplesTheTextureRimAtAJoin() throws IOException {
		for (final boolean left : new boolean[] {false, true}) {
			for (final boolean right : new boolean[] {false, true}) {
				final JsonArray uv = screen(left, right).getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
				assertTrue(uv.get(0).getAsFloat() >= 1F && uv.get(2).getAsFloat() <= 15F, "u stays inside the 1..15 interior: " + uv);
			}
		}
	}

	@Test
	void bodyIsOneContinuousBlockAcrossTheRow() throws IOException {
		for (final String suffix : new String[] {"", "_l", "_r", "_l_r"}) {
			final JsonObject body = json("models/block/passenger_info_terminal" + suffix + ".json").getAsJsonArray("elements").get(0).getAsJsonObject();
			assertEquals(0F, body.getAsJsonArray("from").get(0).getAsFloat());
			assertEquals(16F, body.getAsJsonArray("to").get(0).getAsFloat());
		}
	}

	@Test
	void blockstateSelectsTheMatchingModelForEveryFacing() throws IOException {
		final JsonObject variants = json("blockstates/passenger_info_terminal.json").getAsJsonObject("variants");
		final Map<Direction, Integer> rotation = new HashMap<>(Map.of(Direction.NORTH, 0, Direction.EAST, 90, Direction.SOUTH, 180, Direction.WEST, 270));
		assertEquals(16, variants.size());
		for (final Direction facing : FACINGS) {
			for (final boolean left : new boolean[] {false, true}) {
				for (final boolean right : new boolean[] {false, true}) {
					final JsonElement entry = variants.get("facing=" + facing.getName() + ",left=" + left + ",right=" + right);
					final String model = entry.getAsJsonObject().get("model").getAsString();
					assertTrue(model.endsWith("passenger_info_terminal" + (left ? "_l" : "") + (right ? "_r" : "")), model);
					final int y = entry.getAsJsonObject().has("y") ? entry.getAsJsonObject().get("y").getAsInt() : 0;
					assertEquals(rotation.get(facing), y, "rotation of " + facing);
				}
			}
		}
	}

	@Test
	void localLeftIsTheModelsWestSideAtNorth() {
		// TextSignBlock.localLeft/Right are rotateYCounterclockwise/rotateYClockwise (the block class itself needs the
		// game bootstrap); the model's "_l" extends its screen to x = 0, the west side of a north-facing block
		assertEquals(Direction.WEST, Direction.NORTH.rotateYCounterclockwise());
		assertEquals(Direction.EAST, Direction.NORTH.rotateYClockwise());
		for (final Direction facing : FACINGS) {
			assertEquals(facing.rotateYCounterclockwise().getOpposite(), facing.rotateYClockwise());
			assertFalse(facing.rotateYCounterclockwise().getAxis() == facing.getAxis());
		}
	}
}
