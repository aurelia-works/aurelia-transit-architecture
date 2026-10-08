package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.AxisShapedBlock;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.ShapedBlock;
import com.aureliatransit.architecture.block.mtr.MtrPlatformContract;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * ATA 1.5 European stations, German and Italian set (creative tab: European Stations): Frankfurt, Munich and Hamburg
 * main stations, a small S-Bahn station and Roma Termini. Plain blocks only: no block entities, no ticking.
 */
public final class StationBlocksDeIt {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";

	private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape TRUSS_SPAN = box(0, 0, 6.5, 16, 16, 9.5);
	private static final VoxelShape TRUSS_LEG = box(3, 0, 6.5, 13, 16, 9.5);
	private static final VoxelShape TRUSS_ARCH = box(6.5, 0, 0, 9.5, 16, 16);
	private static final VoxelShape CORNICE = union(box(0, 0, 3, 16, 8, 16), box(0, 8, 1.5, 16, 12, 16), box(0, 12, 0, 16, 16, 16));
	private static final VoxelShape SKYLIGHT = box(0, 0, 0, 16, 6.5, 16);
	private static final VoxelShape SHELTER = union(box(0, 14.5, 0, 16, 16, 16), box(0, 0, 13, 16, 14.5, 15.5));
	private static final VoxelShape STATION_SIGN = union(box(0, 7, 7, 16, 13, 9), box(1, 0, 7.5, 2.5, 7, 8.5), box(13.5, 0, 7.5, 15, 7, 8.5));
	private static final VoxelShape LAMP = union(box(7, 0, 7, 9, 13, 9), box(5.5, 13, 5.5, 10.5, 16, 10.5));
	private static final VoxelShape ROOF_DECK = box(0, 12, 0, 16, 16, 16);
	private static final VoxelShape GLASS_ROOF = box(0, 12.5, 0, 16, 14.5, 16);
	private static final VoxelShape RIBBON_WINDOW = box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape DINOSAUR = union(box(0, 14, 0, 16, 16, 16), box(0, 5, 0, 16, 14, 2));

	// ---- Frankfurt Hauptbahnhof (1888) -----------------------------------------------------------------------------

	public static final Block FRANKFURT_SANDSTONE_ASHLAR = ModBlocks.register("frankfurt_sandstone_ashlar", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.TERRACOTTA_ORANGE)));
	public static final Block FRANKFURT_SANDSTONE_RUSTICATED = ModBlocks.register("frankfurt_sandstone_rusticated", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.TERRACOTTA_ORANGE)));
	public static final Block FRANKFURT_SANDSTONE_CORNICE = ModBlocks.register("frankfurt_sandstone_cornice", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.paving(MapColor.TERRACOTTA_ORANGE).nonOpaque(), Placement.AWAY_FROM_PLAYER, CORNICE),
			TIP + "points_away");
	public static final Block FRANKFURT_HALL_TRUSS = ModBlocks.register("frankfurt_hall_truss", BlockFamily.STATIONS_DE_IT, RenderKind.CUTOUT,
			new AxisShapedBlock(ModBlocks.metal(), TRUSS_SPAN));
	public static final Block FRANKFURT_HALL_TRUSS_LEG = ModBlocks.register("frankfurt_hall_truss_leg", BlockFamily.STATIONS_DE_IT, RenderKind.CUTOUT,
			new ShapedBlock(ModBlocks.metal(), TRUSS_LEG));
	public static final Block FRANKFURT_HALL_TRUSS_ARCH = ModBlocks.register("frankfurt_hall_truss_arch", BlockFamily.STATIONS_DE_IT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, TRUSS_ARCH), TIP + "rises_ahead");
	public static final Block FRANKFURT_RIDGE_SKYLIGHT = ModBlocks.register("frankfurt_ridge_skylight", BlockFamily.STATIONS_DE_IT, RenderKind.TRANSLUCENT,
			new FacingShapedBlock(ModBlocks.glass(), Placement.AWAY_FROM_PLAYER, SKYLIGHT));

	// ---- Munchen Hauptbahnhof (1960s concourse) --------------------------------------------------------------------

	public static final Block MUNICH_EXPOSED_CONCRETE = ModBlocks.register("munich_exposed_concrete", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.STONE_GRAY)));
	public static final Block MUNICH_BOARD_FORMED_CONCRETE = ModBlocks.register("munich_board_formed_concrete", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.STONE_GRAY)));
	public static final Block MUNICH_TERRAZZO_FLOOR = ModBlocks.register("munich_terrazzo_floor", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE_GRAY)));
	public static final Block MUNICH_RIBBED_ROOF_DECK = ModBlocks.register("munich_ribbed_roof_deck", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), ROOF_DECK));

	// ---- Hamburg Hauptbahnhof (1906) -------------------------------------------------------------------------------

	public static final Block HAMBURG_CLINKER_BRICK = ModBlocks.register("hamburg_clinker_brick", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BROWN)));
	public static final Block HAMBURG_CLINKER_STONE_TRIM = ModBlocks.register("hamburg_clinker_stone_trim", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BROWN)));
	public static final Block HAMBURG_STONE_TRIM = ModBlocks.register("hamburg_stone_trim", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block HAMBURG_HALL_TRUSS = ModBlocks.register("hamburg_hall_truss", BlockFamily.STATIONS_DE_IT, RenderKind.CUTOUT,
			new AxisShapedBlock(ModBlocks.metal(), TRUSS_SPAN));
	public static final Block HAMBURG_GLASS_ROOF = ModBlocks.register("hamburg_glass_roof", BlockFamily.STATIONS_DE_IT, RenderKind.TRANSLUCENT,
			new ShapedBlock(ModBlocks.glass(), GLASS_ROOF));

	// ---- Small S-Bahn station --------------------------------------------------------------------------------------

	public static final Block SBAHN_CLINKER_BRICK = ModBlocks.register("sbahn_clinker_brick", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.RED)));
	public static final Block SBAHN_PLATFORM_SLAB = ModBlocks.register("sbahn_platform_slab", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			MtrPlatformContract.surface(ModBlocks.paving(MapColor.STONE_GRAY)));
	public static final Block SBAHN_PLATFORM_EDGE = ModBlocks.register("sbahn_platform_edge", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			MtrPlatformContract.edge(ModBlocks.paving(MapColor.STONE_GRAY), Placement.AWAY_FROM_PLAYER, FULL), TIP + "points_away");
	public static final Block SBAHN_PLATFORM_EDGE_TACTILE = ModBlocks.register("sbahn_platform_edge_tactile", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			MtrPlatformContract.edge(ModBlocks.paving(MapColor.STONE_GRAY), Placement.AWAY_FROM_PLAYER, FULL), TIP + "points_away");
	public static final Block SBAHN_SHELTER = ModBlocks.register("sbahn_shelter", BlockFamily.STATIONS_DE_IT, RenderKind.TRANSLUCENT,
			new FacingShapedBlock(ModBlocks.glass(), Placement.TOWARD_PLAYER, SHELTER), TIP + "faces_you");
	public static final Block SBAHN_STATION_SIGN = ModBlocks.register("sbahn_station_sign", BlockFamily.STATIONS_DE_IT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, STATION_SIGN), TIP + "faces_you");
	public static final Block SBAHN_PLATFORM_LIGHT = ModBlocks.register("sbahn_platform_light", BlockFamily.STATIONS_DE_IT, RenderKind.CUTOUT,
			new ShapedBlock(ModBlocks.metal().luminance(state -> 13), LAMP));

	// ---- Roma Termini (1950) ---------------------------------------------------------------------------------------

	public static final Block ROMA_TRAVERTINE = ModBlocks.register("roma_travertine", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block ROMA_POLISHED_FLOOR = ModBlocks.register("roma_polished_floor", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block ROMA_RIBBON_WINDOW = ModBlocks.register("roma_ribbon_window", BlockFamily.STATIONS_DE_IT, RenderKind.TRANSLUCENT,
			new FacingShapedBlock(ModBlocks.glass(), Placement.AWAY_FROM_PLAYER, RIBBON_WINDOW));
	public static final Block ROMA_CANOPY_EDGE = ModBlocks.register("roma_canopy_edge", BlockFamily.STATIONS_DE_IT, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.paving(MapColor.WHITE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER, DINOSAUR), TIP + "points_away");

	private StationBlocksDeIt() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
