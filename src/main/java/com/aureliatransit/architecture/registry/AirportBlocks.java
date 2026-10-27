package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.ShapedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * 1.7 airport blocks: the airfield's ground lights and apron floodlight, and the terminal's check-in desk, baggage belt,
 * departures board, windsock and terrazzo floor. Static decoration: no block entities, nothing ticks. The airport generator
 * ({@code PlanAirport}) builds with them; they are in their own creative tab.
 */
public final class AirportBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_GROUND_LIGHT = TIP + "airport_ground_light";

	/** A check-in counter (front, the passengers' side, facing north) with a screen on a post at its back. */
	private static final VoxelShape DESK = union(box(0, 0, 4, 16, 10, 16), box(7, 10, 13, 9, 14, 15), box(3, 14, 12, 13, 20, 16));
	private static final VoxelShape DESK_COLLISION = box(0, 0, 4, 16, 16, 16);
	private static final VoxelShape BELT = box(0, 0, 0, 16, 7, 16);
	/** A board fixed to a wall or column behind it (south), its face to the north. */
	private static final VoxelShape BOARD = box(0, 2, 13, 16, 14, 16);
	/** A windsock on top of its post: the post's head, the swivel and the cone blowing north. */
	private static final VoxelShape SOCK = union(box(7, 0, 7, 9, 12, 9), box(5, 9, 0, 11, 15, 9));
	private static final VoxelShape FLOODLIGHT = union(box(7, 0, 7, 9, 8, 9), box(2, 8, 2, 14, 16, 14));

	public static final Block RUNWAY_EDGE_LIGHT = ModBlocks.register("runway_edge_light", BlockFamily.AIRPORT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BLACK).luminance(state -> 15)), TIP_GROUND_LIGHT);
	public static final Block RUNWAY_THRESHOLD_LIGHT = ModBlocks.register("runway_threshold_light", BlockFamily.AIRPORT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BLACK).luminance(state -> 15)), TIP_GROUND_LIGHT);
	public static final Block TAXIWAY_EDGE_LIGHT = ModBlocks.register("taxiway_edge_light", BlockFamily.AIRPORT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.GRAY).luminance(state -> 12)), TIP_GROUND_LIGHT);
	public static final Block APRON_FLOODLIGHT = ModBlocks.register("apron_floodlight", BlockFamily.AIRPORT, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal().luminance(state -> 15), FLOODLIGHT));
	public static final Block CHECK_IN_DESK = ModBlocks.register("check_in_desk", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal().luminance(state -> 4), Placement.TOWARD_PLAYER, DESK, DESK_COLLISION));
	public static final Block BAGGAGE_BELT = ModBlocks.register("baggage_belt", BlockFamily.AIRPORT, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), BELT));
	public static final Block DEPARTURES_BOARD = ModBlocks.register("departures_board", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal().luminance(state -> 10), Placement.TOWARD_PLAYER, BOARD));
	public static final Block WINDSOCK = ModBlocks.register("windsock", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, SOCK));
	public static final Block TERMINAL_TERRAZZO = ModBlocks.register("terminal_terrazzo", BlockFamily.AIRPORT, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.OFF_WHITE)));

	private AirportBlocks() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
