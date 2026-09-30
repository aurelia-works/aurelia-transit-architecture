package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.block.ClockBlock;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.profile;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * Registers the blocks added by the interactive station content workstream (clocks and a few canopy pieces) via
 * ModBlocks.register.
 */
public final class InteractiveBlocks {

	private static final String TIP = "tooltip." + com.aureliatransit.architecture.AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_FACES_YOU = TIP + "faces_you";
	private static final String TIP_POINTS_AWAY = TIP + "points_away";
	private static final String TIP_WALL = TIP + "wall_mounted";
	private static final String TIP_SLOPE = TIP + "slope";
	private static final String TIP_CLOCK = TIP + "clock";

	private static final VoxelShape HANGING_CLOCK = union(box(0, 4, 6, 16, 12, 10), box(3.5, 12, 7.5, 4.5, 16, 8.5), box(11.5, 12, 7.5, 12.5, 16, 8.5));
	private static final VoxelShape WALL_CLOCK = box(1, 4, 13, 15, 12, 16);
	private static final VoxelShape ANALOG_CLOCK = box(1, 1, 13, 15, 15, 16);
	private static final VoxelShape END_CAP = union(box(0, 0, 0, 16, 3, 1.5), box(14.5, 0, 1.5, 16, 3, 16), box(0, 0, 1.5, 14.5, 2, 16));

	// ---- Passenger information: clocks -----------------------------------------------------------------------------

	public static final Block HANGING_DIGITAL_CLOCK = ModBlocks.register("hanging_digital_clock", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new ClockBlock(ModBlocks.metal().luminance(state -> 8), Placement.TOWARD_PLAYER, HANGING_CLOCK,
					new ClockBlock.Face(false, 8, 14, 6, 6, 10)), TIP_FACES_YOU, TIP_CLOCK);
	public static final Block WALL_DIGITAL_CLOCK = ModBlocks.register("wall_digital_clock", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new ClockBlock(ModBlocks.metal().luminance(state -> 8), Placement.TOWARD_PLAYER, WALL_CLOCK,
					new ClockBlock.Face(false, 8, 12, 6, 13, -1)), TIP_WALL, TIP_CLOCK);
	public static final Block STATION_ANALOG_CLOCK = ModBlocks.register("station_analog_clock", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new ClockBlock(ModBlocks.metal().luminance(state -> 4), Placement.TOWARD_PLAYER, ANALOG_CLOCK,
					new ClockBlock.Face(true, 8, 12, 12, 13, -1)), TIP_WALL, TIP_CLOCK);

	// ---- Architecture ----------------------------------------------------------------------------------------------

	public static final Block CANOPY_WAVE_FLAT = ModBlocks.register("canopy_wave_flat", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, profile(ModBlocks.WAVE_FLAT, 2, false, 2)), TIP_SLOPE);
	public static final Block CANOPY_END_CAP = ModBlocks.register("canopy_end_cap", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, END_CAP), TIP_POINTS_AWAY);

	private InteractiveBlocks() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
