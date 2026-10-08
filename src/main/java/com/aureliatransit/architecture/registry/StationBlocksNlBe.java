package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.GlassFacingBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.ShapedBlock;
import com.aureliatransit.architecture.block.mtr.MtrPlatformContract;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.profile;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * 1.5 Dutch and Belgian station architecture (Utrecht, Amsterdam, Rotterdam, Antwerpen). Static decoration only: no block
 * entities, no ticking. Registered via ModBlocks.register.
 */
public final class StationBlocksNlBe {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_SLOPE = TIP + "slope";

	private static final VoxelShape WAVE_PLATE = box(0, 0, 0, 16, 3, 16);
	private static final VoxelShape WAVE_EDGE = union(box(0, 0, 2, 16, 3, 16), box(0, 0, 0, 16, 4, 2));
	private static final VoxelShape TREE_COLUMN = union(box(6.5, 0, 6.5, 9.5, 8, 9.5), box(5.5, 8, 5.5, 10.5, 11, 10.5),
			box(3.5, 11, 3.5, 12.5, 14, 12.5), box(1, 14, 1, 15, 16, 15));
	private static final VoxelShape CORNICE = union(box(0, 0, 10, 16, 5, 16), box(0, 5, 7, 16, 9, 16),
			box(0, 9, 3, 16, 12, 16), box(0, 12, 1, 16, 16, 16));
	private static final VoxelShape VAULT = union(box(0, 0, 0, 16, 7, 1.5), box(0, 0, 14.5, 16, 7, 16));
	private static final VoxelShape SHED_PLATE = box(0, 0, 7, 16, 16, 9);

	// ---- Utrecht Centraal (2016 hall) ------------------------------------------------------------------------------

	public static final Block WAVE_ROOF_PANEL = ModBlocks.register("wave_roof_panel", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), WAVE_PLATE));
	public static final Block WAVE_ROOF_RISE = ModBlocks.register("wave_roof_rise", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, profile(ModBlocks.WAVE_RISE, 2, false, 4)), TIP_SLOPE);
	public static final Block WAVE_ROOF_EDGE = ModBlocks.register("wave_roof_edge", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, WAVE_EDGE));
	public static final Block TREE_COLUMN_WHITE = ModBlocks.register("tree_column_white", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), TREE_COLUMN));
	public static final Block HALL_GLASS_FACADE = ModBlocks.register("hall_glass_facade", BlockFamily.STATIONS_NL_BE, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(ModBlocks.glass(), Placement.TOWARD_PLAYER, box(0, 0, 6.5, 16, 16, 9.5)));
	public static final Block LIGHT_GREY_FLOOR_TILE = ModBlocks.register("light_grey_floor_tile", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.LIGHT_GRAY)));

	// ---- Utrecht Leidsche Rijn -------------------------------------------------------------------------------------

	public static final Block TIMBER_SOFFIT_CANOPY = ModBlocks.register("timber_soffit_canopy", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), box(0, 0, 0, 16, 3, 16)));
	public static final Block PERFORATED_ANTHRACITE_CLADDING = ModBlocks.register("perforated_anthracite_cladding", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(solid(MapColor.GRAY, BlockSoundGroup.METAL)));
	public static final Block CONCRETE_PLATFORM_PAVER = ModBlocks.register("concrete_platform_paver", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			MtrPlatformContract.surface(ModBlocks.paving(MapColor.STONE_GRAY)));

	// ---- Amsterdam Centraal (1889) ---------------------------------------------------------------------------------

	public static final Block DARK_RED_BRICK = ModBlocks.register("dark_red_brick", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.RED)));
	public static final Block RED_BRICK_SANDSTONE_BAND = ModBlocks.register("red_brick_sandstone_band", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.RED)));
	public static final Block ORNAMENTAL_STONE_CORNICE = ModBlocks.register("ornamental_stone_cornice", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.paving(MapColor.PALE_YELLOW).nonOpaque(), Placement.TOWARD_PLAYER, CORNICE));
	public static final Block ARCHED_WINDOW_BRICK = ModBlocks.register("arched_window_brick", BlockFamily.STATIONS_NL_BE, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(ModBlocks.glass(), Placement.TOWARD_PLAYER, box(0, 0, 6, 16, 16, 10)));
	public static final Block CAST_IRON_SHED_LEG = ModBlocks.register("cast_iron_shed_leg", BlockFamily.STATIONS_NL_BE, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, SHED_PLATE));
	public static final Block CAST_IRON_SHED_ARCH = ModBlocks.register("cast_iron_shed_arch", BlockFamily.STATIONS_NL_BE, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, SHED_PLATE));
	public static final Block GILDED_CLOCK_FACE_PANEL = ModBlocks.register("gilded_clock_face_panel", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, box(1, 1, 13, 15, 15, 16)));

	// ---- Rotterdam Centraal (2014) ---------------------------------------------------------------------------------

	public static final Block STAINLESS_ROOF_PANEL = ModBlocks.register("stainless_roof_panel", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), box(0, 0, 0, 16, 2, 16)));
	public static final Block STAINLESS_ROOF_SLOPE = ModBlocks.register("stainless_roof_slope", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, profile(ModBlocks.SLOPE_LOWER, 2, false, 4)), TIP_SLOPE);
	public static final Block TIMBER_SLAT_CEILING = ModBlocks.register("timber_slat_ceiling", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(solid(MapColor.OAK_TAN, BlockSoundGroup.WOOD)));
	public static final Block BLACK_STONE_FLOOR = ModBlocks.register("black_stone_floor", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BLACK)));

	// ---- Antwerpen-Centraal (1905) ---------------------------------------------------------------------------------

	public static final Block ORNATE_LIMESTONE = ModBlocks.register("ornate_limestone", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block POLISHED_MARBLE_WALL = ModBlocks.register("polished_marble_wall", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE_GRAY)));
	public static final Block MARBLE_FLOOR_TILE = ModBlocks.register("marble_floor_tile", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE_GRAY)));
	public static final Block IRON_GLASS_VAULT = ModBlocks.register("iron_glass_vault", BlockFamily.STATIONS_NL_BE, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(ModBlocks.glass(), Placement.TOWARD_PLAYER, VAULT));
	public static final Block GILDED_ORNAMENT_TRIM = ModBlocks.register("gilded_ornament_trim", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, box(0, 2, 12, 16, 14, 16)));

	// ---- Dutch-style signage (colours only, no operator branding) --------------------------------------------------

	public static final Block YELLOW_STATION_SIGN_PANEL = ModBlocks.register("yellow_station_sign_panel", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, box(0, 4, 13, 16, 12, 16)));
	public static final Block YELLOW_BLUE_PLATFORM_SIGN = ModBlocks.register("yellow_blue_platform_sign", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, union(box(3, 2, 7, 13, 12, 9), box(7.5, 12, 7.5, 8.5, 16, 8.5))));
	public static final Block YELLOW_COLUMN_BAND = ModBlocks.register("yellow_column_band", BlockFamily.STATIONS_NL_BE, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), box(3.5, 5, 3.5, 12.5, 11, 12.5)));

	private StationBlocksNlBe() {
	}

	private static AbstractBlock.Settings solid(MapColor color, BlockSoundGroup sounds) {
		return AbstractBlock.Settings.create().mapColor(color).strength(2.0F, 6.0F).sounds(sounds);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
