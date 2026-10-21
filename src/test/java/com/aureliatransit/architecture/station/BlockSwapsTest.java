package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enviro, 10-20: "there has to be a way to replace blocks he doesn't like". */
class BlockSwapsTest {

	@Test
	void wordsNameBlocks() {
		assertEquals("minecraft:stone_bricks", BlockFamilies.resolve("Stone Bricks"));
		assertEquals("minecraft:oak_planks", BlockFamilies.resolve("oak"));
		assertEquals("minecraft:quartz_block", BlockFamilies.resolve("quartz"));
		assertEquals("minecraft:bricks", BlockFamilies.resolve("brick"));
		assertEquals("minecraft:white_concrete", BlockFamilies.resolve("white concrete"));
		assertEquals("mtr:ticket_barrier_entrance_1", BlockFamilies.resolve("mtr:ticket_barrier_entrance_1"));
		assertNull(BlockFamilies.resolve("  "));
	}

	@Test
	void aSwapTakesItsShapesWithIt() {
		assertEquals(Map.of("minecraft:sandstone", "minecraft:stone_bricks", "minecraft:sandstone_slab", "minecraft:stone_brick_slab",
				"minecraft:sandstone_stairs", "minecraft:stone_brick_stairs", "minecraft:sandstone_wall", "minecraft:stone_brick_wall"),
				BlockFamilies.expand("minecraft:sandstone", "minecraft:stone_bricks"));
		// concrete has no shapes: only the full block is swapped, sandstone slabs stay slabs
		assertEquals(Map.of("minecraft:sandstone", "minecraft:white_concrete"), BlockFamilies.expand("minecraft:sandstone", "minecraft:white_concrete"));
		// a shape asked for alone is swapped alone
		assertEquals(Map.of("minecraft:sandstone_slab", "minecraft:oak_slab"), BlockFamilies.expand("minecraft:sandstone_slab", "minecraft:oak_slab"));
		// planks have no wall
		assertFalse(BlockFamilies.expand("minecraft:stone_bricks", "minecraft:oak_planks").containsKey("minecraft:stone_brick_wall"));
	}

	@Test
	void everyWayOfAskingIsRead() {
		for (final String text : new String[]{"2 track station, use stone bricks instead of sandstone", "2 track station, replace sandstone with stone bricks",
				"2 track station, swap sandstone for stone bricks", "2 track station, sandstone -> stone bricks"}) {
			final ParseResult result = StationDescriptionParser.parse(text);
			assertEquals("minecraft:stone_bricks", result.spec().options().swapped("minecraft:sandstone"), text);
			assertEquals("minecraft:stone_brick_slab", result.spec().options().swapped("minecraft:sandstone_slab"), text);
			assertTrue(result.understood().stream().anyMatch(line -> line.contains("instead of minecraft:sandstone")), text + " " + result.understood());
			assertEquals(2, result.spec().trackCount(), text);
		}
		// the block words are not read as a look or material too
		final ParseResult two = StationDescriptionParser.parse("2 track station, use deepslate tiles instead of white concrete, and replace glass with tinted glass");
		assertEquals("minecraft:deepslate_tiles", two.spec().options().swapped("minecraft:white_concrete"));
		assertEquals("minecraft:tinted_glass", two.spec().options().swapped("minecraft:glass"));
		assertEquals(StationDescriptionParser.parse("2 track station").spec().style(), two.spec().style());
	}

	@Test
	void thePlanIsBuiltWithTheSwaps() {
		final String base = "2 track utrecht station, station building, 64 long";
		final StationPlan plain = StationPlanner.plan(StationDescriptionParser.parse(base).spec());
		final String most = plain.blocks().stream().map(PlannedBlock::blockId).filter(id -> !id.equals("minecraft:air"))
				.collect(java.util.stream.Collectors.groupingBy(id -> id, java.util.stream.Collectors.counting())).entrySet().stream()
				.max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		final StationPlan swapped = StationPlanner.plan(StationDescriptionParser.parse(base + ", replace " + most + " with minecraft:mud_bricks").spec());
		assertEquals(plain.blocks().size(), swapped.blocks().size());
		assertTrue(swapped.blocks().stream().noneMatch(b -> b.blockId().equals(most)), most);
		assertEquals(plain.blocks().stream().filter(b -> b.blockId().equals(most)).count(),
				swapped.blocks().stream().filter(b -> b.blockId().equals("minecraft:mud_bricks")).count()
						- plain.blocks().stream().filter(b -> b.blockId().equals("minecraft:mud_bricks")).count());
		assertTrue(StationAudit.audit(swapped).buildable());
	}
}
