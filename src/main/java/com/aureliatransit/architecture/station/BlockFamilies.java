package com.aureliatransit.architecture.station;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vanilla block families (a full block and its slab, stairs and wall), so a swap of one block takes its shapes with it: "use
 * stone bricks instead of sandstone" also builds stone brick slabs, stairs and walls where the plan had sandstone ones. A
 * shape the new block lacks keeps the old block (white concrete has no slab, so sandstone slabs stay). Taken from Minecraft
 * 1.20.1's block list.
 */
public final class BlockFamilies {

	/** A family: the full block, then its slab, stairs and wall (null where there is none), as ids without the namespace. */
	public record Family(String base, String slab, String stairs, String wall) {

		List<String> shapes() {
			return java.util.Arrays.asList(slab, stairs, wall);
		}
	}

	private static final String TABLE =
			"acacia_planks acacia_slab acacia_stairs -\n" +
			"andesite andesite_slab andesite_stairs andesite_wall\n" +
			"bamboo_planks bamboo_slab bamboo_stairs -\n" +
			"bamboo_mosaic bamboo_mosaic_slab bamboo_mosaic_stairs -\n" +
			"birch_planks birch_slab birch_stairs -\n" +
			"blackstone blackstone_slab blackstone_stairs blackstone_wall\n" +
			"bricks brick_slab brick_stairs brick_wall\n" +
			"cherry_planks cherry_slab cherry_stairs -\n" +
			"cobbled_deepslate cobbled_deepslate_slab cobbled_deepslate_stairs cobbled_deepslate_wall\n" +
			"cobblestone cobblestone_slab cobblestone_stairs cobblestone_wall\n" +
			"crimson_planks crimson_slab crimson_stairs -\n" +
			"cut_copper cut_copper_slab cut_copper_stairs -\n" +
			"cut_red_sandstone cut_red_sandstone_slab - -\n" +
			"cut_sandstone cut_sandstone_slab - -\n" +
			"dark_oak_planks dark_oak_slab dark_oak_stairs -\n" +
			"dark_prismarine dark_prismarine_slab dark_prismarine_stairs -\n" +
			"deepslate_bricks deepslate_brick_slab deepslate_brick_stairs deepslate_brick_wall\n" +
			"deepslate_tiles deepslate_tile_slab deepslate_tile_stairs deepslate_tile_wall\n" +
			"diorite diorite_slab diorite_stairs diorite_wall\n" +
			"end_stone_bricks end_stone_brick_slab end_stone_brick_stairs end_stone_brick_wall\n" +
			"exposed_cut_copper exposed_cut_copper_slab exposed_cut_copper_stairs -\n" +
			"granite granite_slab granite_stairs granite_wall\n" +
			"jungle_planks jungle_slab jungle_stairs -\n" +
			"mangrove_planks mangrove_slab mangrove_stairs -\n" +
			"mossy_cobblestone mossy_cobblestone_slab mossy_cobblestone_stairs mossy_cobblestone_wall\n" +
			"mossy_stone_bricks mossy_stone_brick_slab mossy_stone_brick_stairs mossy_stone_brick_wall\n" +
			"mud_bricks mud_brick_slab mud_brick_stairs mud_brick_wall\n" +
			"nether_bricks nether_brick_slab nether_brick_stairs nether_brick_wall\n" +
			"oak_planks oak_slab oak_stairs -\n" +
			"oxidized_cut_copper oxidized_cut_copper_slab oxidized_cut_copper_stairs -\n" +
			"polished_andesite polished_andesite_slab polished_andesite_stairs -\n" +
			"polished_blackstone polished_blackstone_slab polished_blackstone_stairs polished_blackstone_wall\n" +
			"polished_blackstone_bricks polished_blackstone_brick_slab polished_blackstone_brick_stairs polished_blackstone_brick_wall\n" +
			"polished_deepslate polished_deepslate_slab polished_deepslate_stairs polished_deepslate_wall\n" +
			"polished_diorite polished_diorite_slab polished_diorite_stairs -\n" +
			"polished_granite polished_granite_slab polished_granite_stairs -\n" +
			"prismarine prismarine_slab prismarine_stairs prismarine_wall\n" +
			"prismarine_bricks prismarine_brick_slab prismarine_brick_stairs -\n" +
			"purpur_block purpur_slab purpur_stairs -\n" +
			"quartz_block quartz_slab quartz_stairs -\n" +
			"red_nether_bricks red_nether_brick_slab red_nether_brick_stairs red_nether_brick_wall\n" +
			"red_sandstone red_sandstone_slab red_sandstone_stairs red_sandstone_wall\n" +
			"sandstone sandstone_slab sandstone_stairs sandstone_wall\n" +
			"smooth_quartz smooth_quartz_slab smooth_quartz_stairs -\n" +
			"smooth_red_sandstone smooth_red_sandstone_slab smooth_red_sandstone_stairs -\n" +
			"smooth_sandstone smooth_sandstone_slab smooth_sandstone_stairs -\n" +
			"smooth_stone smooth_stone_slab - -\n" +
			"spruce_planks spruce_slab spruce_stairs -\n" +
			"stone stone_slab stone_stairs -\n" +
			"stone_bricks stone_brick_slab stone_brick_stairs stone_brick_wall\n" +
			"warped_planks warped_slab warped_stairs -\n" +
			"waxed_cut_copper waxed_cut_copper_slab waxed_cut_copper_stairs -\n" +
			"waxed_exposed_cut_copper waxed_exposed_cut_copper_slab waxed_exposed_cut_copper_stairs -\n" +
			"waxed_oxidized_cut_copper waxed_oxidized_cut_copper_slab waxed_oxidized_cut_copper_stairs -\n" +
			"waxed_weathered_cut_copper waxed_weathered_cut_copper_slab waxed_weathered_cut_copper_stairs -\n" +
			"weathered_cut_copper weathered_cut_copper_slab weathered_cut_copper_stairs -\n";

	private static final Map<String, Family> BY_BASE = new LinkedHashMap<>();
	/** Every member id (base, slab, stairs, wall) to its family. */
	private static final Map<String, Family> BY_MEMBER = new HashMap<>();

	static {
		for (final String row : TABLE.split("\n")) {
			final String[] cell = row.split(" ");
			final Family family = new Family(cell[0], dash(cell[1]), dash(cell[2]), dash(cell[3]));
			BY_BASE.put(family.base(), family);
			for (final String member : new String[]{cell[0], cell[1], cell[2], cell[3]}) {
				if (!member.equals("-")) {
					BY_MEMBER.put(member, family);
				}
			}
		}
	}

	private BlockFamilies() {
	}

	private static String dash(String cell) {
		return cell.equals("-") ? null : cell;
	}

	/** The family a vanilla id (with or without "minecraft:") belongs to, or null. */
	public static Family of(String id) {
		return BY_MEMBER.get(strip(id));
	}

	/**
	 * The block id the player's words name: "stone bricks" is minecraft:stone_bricks, "oak" its planks, "quartz" the quartz
	 * block, "brick" the bricks; an id with a namespace ("mtr:..." or "minecraft:glass") is kept as it is. Null for nothing.
	 */
	public static String resolve(String words) {
		final String text = words == null ? "" : words.trim().toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			return null;
		}
		if (text.indexOf(':') > 0) {
			return text.replaceAll("\\s+", "_");
		}
		final String name = text.replaceAll("^(the|a|an)\\s+", "").replaceAll("\\s+(blocks?)$", "").replaceAll("[\\s-]+", "_");
		if (name.isEmpty()) {
			return null;
		}
		for (final String candidate : new String[]{name + "_planks", name, name + "s", name + "_block"}) {
			if (BY_MEMBER.containsKey(candidate)) {
				return "minecraft:" + candidate;
			}
		}
		return "minecraft:" + name;
	}

	/**
	 * The swaps one asked swap stands for: {@code from} built as {@code to}, and each shape of {@code from}'s family built as
	 * the same shape of {@code to}'s, where both have it. Asking to swap a slab (or stairs, a wall) swaps only that shape.
	 */
	public static Map<String, String> expand(String from, String to) {
		final Map<String, String> out = new LinkedHashMap<>();
		out.put(from, to);
		final Family a = of(from);
		final Family b = of(to);
		if (a != null && b != null && strip(from).equals(a.base()) && strip(to).equals(b.base())) {
			final List<String> as = a.shapes();
			final List<String> bs = b.shapes();
			for (int i = 0; i < as.size(); i++) {
				if (as.get(i) != null && bs.get(i) != null) {
					out.put("minecraft:" + as.get(i), "minecraft:" + bs.get(i));
				}
			}
		}
		return out;
	}

	/** Blocks the Studio offers to swap in: every family's full block and the plain building blocks, as ids. */
	public static List<String> suggestions() {
		final List<String> out = new java.util.ArrayList<>();
		for (final String base : BY_BASE.keySet()) {
			out.add("minecraft:" + base);
		}
		for (final String colour : new String[]{"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan",
				"light_blue", "blue", "purple", "magenta", "pink"}) {
			out.add("minecraft:" + colour + "_concrete");
			out.add("minecraft:" + colour + "_terracotta");
			out.add("minecraft:" + colour + "_stained_glass");
		}
		for (final String plain : new String[]{"glass", "tinted_glass", "terracotta", "calcite", "tuff", "dripstone_block", "packed_mud", "mud",
				"iron_block", "copper_block", "chiseled_stone_bricks", "chiseled_sandstone", "chiseled_quartz_block", "quartz_pillar", "quartz_bricks",
				"basalt", "polished_basalt", "smooth_basalt", "deepslate", "white_wool", "light_gray_wool", "gray_wool", "iron_bars", "glass_pane"}) {
			out.add("minecraft:" + plain);
		}
		java.util.Collections.sort(out);
		return out;
	}

	private static String strip(String id) {
		return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
	}
}
