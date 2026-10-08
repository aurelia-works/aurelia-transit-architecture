package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.GlassFacingBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.glass.ArchGlassBlock;
import com.aureliatransit.architecture.block.glass.ArchGlassPaneBlock;
import com.aureliatransit.architecture.block.glass.GlassFinBlock;
import com.aureliatransit.architecture.block.glass.GlassFloorBlock;
import net.minecraft.block.Block;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * 1.5 architectural glass: real-world glazing types as full blocks with matching connecting panes, plus a curtain wall
 * panel, a structural fin and a walkable floor panel. Registered through {@link ModBlocks#register} into the glass tab.
 * No block entities and no ticking; full blocks and panes cull like vanilla glass.
 */
public final class GlassBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";

	public static final Block CLEAR_FLOAT_GLASS = ModBlocks.register("clear_float_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_float");
	public static final Block CLEAR_FLOAT_GLASS_PANE = ModBlocks.register("clear_float_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block LOW_IRON_GLASS = ModBlocks.register("low_iron_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_low_iron");
	public static final Block LOW_IRON_GLASS_PANE = ModBlocks.register("low_iron_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block GREY_TINTED_GLASS = ModBlocks.register("grey_tinted_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_solar");
	public static final Block GREY_TINTED_GLASS_PANE = ModBlocks.register("grey_tinted_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block BRONZE_TINTED_GLASS = ModBlocks.register("bronze_tinted_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_solar");
	public static final Block BRONZE_TINTED_GLASS_PANE = ModBlocks.register("bronze_tinted_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block BLUE_TINTED_GLASS = ModBlocks.register("blue_tinted_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_solar");
	public static final Block BLUE_TINTED_GLASS_PANE = ModBlocks.register("blue_tinted_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block REFLECTIVE_GLASS = ModBlocks.register("reflective_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_reflective");
	public static final Block REFLECTIVE_GLASS_PANE = ModBlocks.register("reflective_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block FROSTED_GLASS = ModBlocks.register("frosted_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_frosted");
	public static final Block FROSTED_GLASS_PANE = ModBlocks.register("frosted_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block FRITTED_GLASS = ModBlocks.register("fritted_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_fritted");
	public static final Block FRITTED_GLASS_PANE = ModBlocks.register("fritted_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block WIRED_GLASS = ModBlocks.register("wired_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_wired");
	public static final Block WIRED_GLASS_PANE = ModBlocks.register("wired_glass_pane", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassPaneBlock(ModBlocks.glass()));

	public static final Block GLASS_BRICK = ModBlocks.register("glass_brick", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()), TIP + "glass_brick");

	public static final Block CURTAIN_WALL_GLASS = ModBlocks.register("curtain_wall_glass", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(ModBlocks.glass(), Placement.TOWARD_PLAYER, box(0, 0, 7, 16, 16, 9)), TIP + "curtain_wall_glass");
	public static final Block STRUCTURAL_GLASS_FIN = ModBlocks.register("structural_glass_fin", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new GlassFinBlock(ModBlocks.glass(), box(2, 0, 7, 14, 16, 9)), TIP + "structural_glass_fin");
	public static final Block GLASS_FLOOR_PANEL = ModBlocks.register("glass_floor_panel", BlockFamily.GLASS, RenderKind.TRANSLUCENT,
			new GlassFloorBlock(ModBlocks.glass(), box(0, 0, 0, 16, 8, 16)), TIP + "glass_floor_panel");

	private GlassBlocks() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
