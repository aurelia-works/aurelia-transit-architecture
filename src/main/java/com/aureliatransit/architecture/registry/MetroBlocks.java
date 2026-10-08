package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
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
 * ATA 1.5 metro (underground) station finishes: tiles, concrete, ceilings, floors, platform edges and tunnel pieces.
 * Plain blocks only: no block entities and no ticking; the lights glow through block luminance alone. Creative tab: ATA Metro.
 */
public final class MetroBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";

	private static final VoxelShape EDGE = union(box(0, 12, 0, 16, 16, 16), box(0, 0, 2, 16, 12, 16));
	private static final VoxelShape COLUMN = box(3, 0, 3, 13, 16, 13);
	/** Five stepped bands of the curve; the wall is on the south side of the model. */
	private static final VoxelShape SPRINGER = union(box(0, 15.5, 2, 16, 16, 5), box(0, 14.5, 5, 16, 16, 8), box(0, 13, 8, 16, 16, 11),
			box(0, 10, 11, 16, 16, 14), box(0, 5.5, 14, 16, 16, 16));
	private static final VoxelShape COVE = union(box(0, 8, 13, 16, 16, 16), box(0, 8, 9, 16, 12, 13));

	// ---- Munich: glossy line-colour tiles, aluminium ceiling, granite floor ----------------------------------------

	public static final Block METRO_TILE_ORANGE = ModBlocks.register("metro_tile_orange", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.ORANGE)));
	public static final Block METRO_TILE_BLUE = ModBlocks.register("metro_tile_blue", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BLUE)));
	public static final Block METRO_TILE_GREEN = ModBlocks.register("metro_tile_green", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.GREEN)));
	public static final Block METRO_TILE_YELLOW = ModBlocks.register("metro_tile_yellow", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.YELLOW)));
	public static final Block METRO_CEILING_STRIP = ModBlocks.register("metro_ceiling_strip", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.IRON_GRAY)));
	public static final Block METRO_GRANITE_FLOOR = ModBlocks.register("metro_granite_floor", BlockFamily.METRO, RenderKind.SOLID,
			MtrPlatformContract.surface(ModBlocks.paving(MapColor.STONE_GRAY)));

	// ---- Frankfurt -------------------------------------------------------------------------------------------------

	public static final Block METRO_RIBBED_CONCRETE = ModBlocks.register("metro_ribbed_concrete", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.STONE_GRAY)));
	public static final Block METRO_OCHRE_TILE = ModBlocks.register("metro_ochre_tile", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.TERRACOTTA_YELLOW)));

	// ---- Amsterdam -------------------------------------------------------------------------------------------------

	public static final Block METRO_PALE_CONCRETE = ModBlocks.register("metro_pale_concrete", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.LIGHT_GRAY)));
	public static final Block METRO_ACOUSTIC_CEILING = ModBlocks.register("metro_acoustic_ceiling", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE_GRAY)));
	public static final Block METRO_TERRAZZO_FLOOR = ModBlocks.register("metro_terrazzo_floor", BlockFamily.METRO, RenderKind.SOLID,
			MtrPlatformContract.surface(ModBlocks.paving(MapColor.DEEPSLATE_GRAY)));

	// ---- Rotterdam -------------------------------------------------------------------------------------------------

	public static final Block METRO_STEEL_CLADDING = ModBlocks.register("metro_steel_cladding", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.IRON_GRAY)));
	public static final Block METRO_BLUE_GREY_TILE = ModBlocks.register("metro_blue_grey_tile", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.LIGHT_BLUE_GRAY)));

	// ---- Lisbon ----------------------------------------------------------------------------------------------------

	public static final Block METRO_AZULEJO_ROSETTE = ModBlocks.register("metro_azulejo_rosette", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE)));
	public static final Block METRO_AZULEJO_LATTICE = ModBlocks.register("metro_azulejo_lattice", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE)));
	public static final Block METRO_AZULEJO_WAVE = ModBlocks.register("metro_azulejo_wave", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE)));

	// ---- Washington ------------------------------------------------------------------------------------------------

	public static final Block METRO_COFFER_CEILING = ModBlocks.register("metro_coffer_ceiling", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.STONE_GRAY)));
	public static final Block METRO_VAULT_SPRINGER = ModBlocks.register("metro_vault_springer", BlockFamily.METRO, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.paving(MapColor.STONE_GRAY).nonOpaque(), Placement.TOWARD_PLAYER, SPRINGER), TIP + "wall_mounted");
	public static final Block METRO_QUARRY_FLOOR = ModBlocks.register("metro_quarry_floor", BlockFamily.METRO, RenderKind.SOLID,
			MtrPlatformContract.surface(ModBlocks.paving(MapColor.BROWN)));
	public static final Block METRO_COVE_LIGHT = ModBlocks.register("metro_cove_light", BlockFamily.METRO, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal().luminance(state -> 12), Placement.TOWARD_PLAYER, COVE), TIP + "wall_mounted");
	public static final Block METRO_FLASHING_EDGE = ModBlocks.register("metro_flashing_edge", BlockFamily.METRO, RenderKind.SOLID,
			MtrPlatformContract.edge(ModBlocks.paving(MapColor.BROWN).nonOpaque().luminance(state -> 4), Placement.AWAY_FROM_PLAYER, EDGE), TIP + "points_away");

	// ---- Generic tunnel and platform pieces ------------------------------------------------------------------------

	public static final Block METRO_CABLE_WALL = ModBlocks.register("metro_cable_wall", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.DEEPSLATE_GRAY)));
	public static final Block METRO_TUNNEL_LINING = ModBlocks.register("metro_tunnel_lining", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.DEEPSLATE_GRAY)));
	public static final Block METRO_TACTILE_EDGE = ModBlocks.register("metro_tactile_edge", BlockFamily.METRO, RenderKind.SOLID,
			MtrPlatformContract.edge(ModBlocks.paving(MapColor.STONE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER, EDGE), TIP + "points_away");
	public static final Block METRO_RECESSED_LIGHT = ModBlocks.register("metro_recessed_light", BlockFamily.METRO, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.LIGHT_GRAY).luminance(state -> 15)));
	public static final Block METRO_COLUMN_TILED = ModBlocks.register("metro_column_tiled", BlockFamily.METRO, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.paving(MapColor.WHITE_GRAY).nonOpaque(), COLUMN));
	public static final Block METRO_COLUMN_STEEL = ModBlocks.register("metro_column_steel", BlockFamily.METRO, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), COLUMN));

	private MetroBlocks() {
	}

	public static void init() {
		// Class loading registers everything declared here.
	}
}
