package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.JoiningSeatBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.QueuePostBlock;
import com.aureliatransit.architecture.block.ShapedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * 1.7 airport blocks: the airfield's ground lights and apron floodlight, and the terminal's check-in desk, baggage belt,
 * departures board, windsock and terrazzo floor; and the terminal's security and gate blocks (Null, 10-26). Static decoration:
 * no block entities, nothing ticks (the seats are sat on like the benches). The airport generator ({@code PlanAirport}) builds
 * with them; they are in their own creative tab.
 */
public final class AirportBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_GROUND_LIGHT = TIP + "airport_ground_light";
	private static final String TIP_FACES_YOU = TIP + "faces_you";
	private static final String TIP_POINTS_AWAY = TIP + "points_away";
	private static final String TIP_SIT = TIP + "sit";

	/** A check-in counter (front, the passengers' side, facing north) with a screen on a post at its back. */
	private static final VoxelShape DESK = union(box(0, 0, 4, 16, 10, 16), box(7, 10, 13, 9, 14, 15), box(3, 14, 12, 13, 20, 16));
	private static final VoxelShape DESK_COLLISION = box(0, 0, 4, 16, 16, 16);
	private static final VoxelShape BELT = box(0, 0, 0, 16, 7, 16);
	/** A board fixed to a wall or column behind it (south), its face to the north. */
	private static final VoxelShape BOARD = box(0, 2, 13, 16, 14, 16);
	/** A windsock on top of its post: the post's head, the swivel and the cone blowing north. */
	private static final VoxelShape SOCK = union(box(7, 0, 7, 9, 12, 9), box(5, 9, 0, 11, 15, 9));
	private static final VoxelShape FLOODLIGHT = union(box(7, 0, 7, 9, 8, 9), box(2, 8, 2, 14, 16, 14));
	/** A walk-through arch, two blocks high: its posts block, the way through between them is open. */
	private static final VoxelShape ARCH = union(box(0, 0, 5, 3, 16, 11), box(13, 0, 5, 16, 16, 11));
	/** The scanner's tunnel along its facing, a belt through it; solid to walk into. */
	private static final VoxelShape SCANNER = box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape TABLE = union(box(0, 13, 1, 16, 14.5, 15), box(1, 0, 3, 15, 13, 13));
	private static final VoxelShape KIOSK = union(box(5, 0, 6, 11, 12, 11), box(2, 12, 4, 14, 16, 12));
	private static final VoxelShape KIOSK_COLLISION = box(2, 0, 4, 14, 16, 12);
	private static final VoxelShape DROP = union(box(0, 0, 0, 16, 7, 11), box(3, 0, 10, 13, 16, 15));
	private static final VoxelShape CAROUSEL = box(0, 0, 0, 16, 7, 16);
	private static final VoxelShape PODIUM = union(box(1, 0, 4, 15, 15, 14), box(4, 15, 11, 12, 16, 13));
	private static final VoxelShape TROLLEY = union(box(3, 0, 1, 13, 2.5, 15), box(3, 2.5, 13, 13, 9, 14), box(2, 9, 1.5, 14, 15, 2.5));
	private static final VoxelShape SEAT = union(box(0, 4.5, 3.5, 16, 8, 12), box(1, 8, 10.5, 15, 15, 12), box(1, 0, 6.5, 15, 4.5, 8.5));
	private static final VoxelShape SEAT_COLLISION = box(0, 0, 3.5, 16, 8, 12);

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
	public static final Block SECURITY_ARCH = ModBlocks.register("security_arch", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal().nonOpaque(), Placement.AWAY_FROM_PLAYER, ARCH), TIP_POINTS_AWAY);
	public static final Block XRAY_SCANNER = ModBlocks.register("xray_scanner", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, SCANNER), TIP_POINTS_AWAY);
	public static final Block SECURITY_DESK = ModBlocks.register("security_desk", BlockFamily.AIRPORT, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), TABLE));
	public static final Block QUEUE_POST = ModBlocks.register("queue_post", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new QueuePostBlock(ModBlocks.metal().nonOpaque()));
	public static final Block SELF_CHECK_IN_KIOSK = ModBlocks.register("self_check_in_kiosk", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal().luminance(state -> 6), Placement.TOWARD_PLAYER, KIOSK, KIOSK_COLLISION), TIP_FACES_YOU);
	public static final Block BAG_DROP = ModBlocks.register("bag_drop", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal().luminance(state -> 4), Placement.TOWARD_PLAYER, DROP), TIP_FACES_YOU);
	public static final Block BAGGAGE_CAROUSEL = ModBlocks.register("baggage_carousel", BlockFamily.AIRPORT, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, CAROUSEL), TIP_FACES_YOU);
	public static final Block BAGGAGE_CAROUSEL_CORNER = ModBlocks.register("baggage_carousel_corner", BlockFamily.AIRPORT, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.TOWARD_PLAYER, CAROUSEL), TIP_FACES_YOU);
	public static final Block GATE_DESK = ModBlocks.register("gate_desk", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal().luminance(state -> 4), Placement.TOWARD_PLAYER, PODIUM), TIP_FACES_YOU);
	public static final Block LUGGAGE_TROLLEY = ModBlocks.register("luggage_trolley", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal().nonOpaque(), Placement.TOWARD_PLAYER, TROLLEY), TIP_FACES_YOU);
	public static final Block AIRPORT_SEAT = ModBlocks.register("airport_seat", BlockFamily.AIRPORT, RenderKind.CUTOUT,
			new JoiningSeatBlock(ModBlocks.metal().nonOpaque(), Placement.TOWARD_PLAYER, SEAT, SEAT_COLLISION, 1, 8, 7, true), TIP_FACES_YOU, TIP_SIT);

	private AirportBlocks() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
