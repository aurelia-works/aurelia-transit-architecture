package com.aureliatransit.architecture.presentation;

import com.aureliatransit.architecture.terminal.NearbyBlocks;
import com.aureliatransit.architecture.text.Tr;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalPolishTest {

	@Test
	void helpPointSelectionIsBoundedAndNearestFirst() {
		final BlockPos centre = new BlockPos(100, 64, 100);
		final List<BlockPos> found = NearbyBlocks.nearest(List.of(
				new BlockPos(110, 64, 100), new BlockPos(103, 64, 100), new BlockPos(100, 64, 200), new BlockPos(100, 64, 104),
				new BlockPos(100, 80, 100), new BlockPos(100, 64, 100)), centre, 24, 4);
		assertEquals(List.of(new BlockPos(100, 64, 100), new BlockPos(103, 64, 100), new BlockPos(100, 64, 104), new BlockPos(110, 64, 100)), found,
				"the far one (100 blocks) is outside the radius; the count is capped; nearest first");
		assertTrue(NearbyBlocks.nearest(List.of(new BlockPos(0, 0, 0)), centre, 24, 4).isEmpty());
		assertTrue(NearbyBlocks.nearest(List.of(centre), centre, 24, 0).isEmpty());
		assertEquals(12, NearbyBlocks.distance(centre, new BlockPos(112, 64, 100)));
	}

	@Test
	void helpPointSelectionIsDeterministicForShuffledInput() {
		final BlockPos centre = BlockPos.ORIGIN;
		final List<BlockPos> a = List.of(new BlockPos(3, 0, 0), new BlockPos(0, 0, 3), new BlockPos(-3, 0, 0));
		final List<BlockPos> b = List.of(a.get(2), a.get(0), a.get(1));
		assertEquals(NearbyBlocks.nearest(a, centre, 10, 3), NearbyBlocks.nearest(b, centre, 10, 3));
	}

	@Test
	void translationFallsBackToBundledEnglishAndFormats() {
		assertEquals("No station linked", Tr.t("term_no_station"));
		assertEquals("Exit B2", Tr.t("term_exit", "B2"));
		assertEquals("missing.key", Tr.raw("missing.key"));
		Tr.install((key, args) -> "xx:" + key);
		try {
			assertEquals("xx:screen.aurelia_transit_architecture.term_exit", Tr.t("term_exit", "A"));
		} finally {
			Tr.install(null);
		}
		assertEquals("Exit A", Tr.t("term_exit", "A"));
	}

	@Test
	void pylonInventoryModelCoversBothHalves() throws Exception {
		try (var in = getClass().getResourceAsStream("/assets/aurelia_transit_architecture/models/item/entrance_pylon.json")) {
			final JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			final JsonArray elements = model.getAsJsonArray("elements");
			float min = 99;
			float max = -99;
			for (final var element : elements) {
				min = Math.min(min, element.getAsJsonObject().getAsJsonArray("from").get(1).getAsFloat());
				max = Math.max(max, element.getAsJsonObject().getAsJsonArray("to").get(1).getAsFloat());
			}
			assertEquals(0F, min);
			assertEquals(32F, max, "the item shows the full two-block pylon, not only the lower half");
			final float scale = model.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("scale").get(0).getAsFloat();
			assertFalse(scale >= 0.625F, "scaled down so two blocks fit the slot");
		}
	}
}
