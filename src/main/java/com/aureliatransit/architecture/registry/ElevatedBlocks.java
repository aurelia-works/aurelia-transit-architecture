package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.AxisShapedBlock;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.elevated.CardReaderBlock;
import com.aureliatransit.architecture.block.elevated.CctvCameraBlock;
import com.aureliatransit.architecture.block.elevated.CurvedScreenDoorBlock;
import com.aureliatransit.architecture.block.elevated.TrainEdgeBlock;
import com.aureliatransit.architecture.block.mtr.MtrPlatformContract;
import com.aureliatransit.architecture.block.elevated.FareGateBlock;
import com.aureliatransit.architecture.block.elevated.HandrailBlock;
import com.aureliatransit.architecture.block.elevated.LiftStatusPanelBlock;
import com.aureliatransit.architecture.block.elevated.NoiseBarrierBlock;
import com.aureliatransit.architecture.block.elevated.PlatformFasciaBlock;
import com.aureliatransit.architecture.block.elevated.PlatformWindscreenBlock;
import com.aureliatransit.architecture.block.elevated.StairEnclosureBlock;
import com.aureliatransit.architecture.block.elevated.StationFenceBlock;
import com.aureliatransit.architecture.block.elevated.StationStairBlock;
import com.aureliatransit.architecture.block.elevated.TactileJunctionBlock;
import com.aureliatransit.architecture.block.elevated.UtilityRunBlock;
import com.aureliatransit.architecture.block.elevated.ViaductBeamBlock;
import com.aureliatransit.architecture.block.elevated.ViaductBraceBlock;
import com.aureliatransit.architecture.block.elevated.ViaductColumnBlock;
import net.minecraft.block.Block;
import com.aureliatransit.architecture.text.SignStyle;
import net.minecraft.block.MapColor;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * ATA 1.3 urban infrastructure: the elevated viaduct and station family (creative tab: Architecture) and the two
 * accessibility additions (tab: Passenger equipment). Each registration is one inventory item whose looks are block
 * states, so twelve items cover what would otherwise be dozens of near-identical blocks.
 */
public final class ElevatedBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_STYLE = TIP + "style_cycle";
	private static final String TIP_BEAM = TIP + "style_cycle_beam";
	private static final String TIP_JOINS_RAIL = TIP + "railing_joins";
	private static final String TIP_BRACE = TIP + "brace_facing";

	// ---- Viaduct ---------------------------------------------------------------------------------------------------

	public static final Block VIADUCT_COLUMN = ModBlocks.register("viaduct_column", BlockFamily.ELEVATED, RenderKind.SOLID,
			new ViaductColumnBlock(ModBlocks.metal()), TIP_STYLE);
	public static final Block VIADUCT_BEAM = ModBlocks.register("viaduct_beam", BlockFamily.ELEVATED, RenderKind.SOLID,
			new ViaductBeamBlock(ModBlocks.metal()), TIP_BEAM);
	public static final Block VIADUCT_BRACE = ModBlocks.register("viaduct_brace", BlockFamily.ELEVATED, RenderKind.SOLID,
			new ViaductBraceBlock(ModBlocks.metal()), TIP_STYLE, TIP_BRACE);

	// ---- Elevated station ------------------------------------------------------------------------------------------

	public static final Block STATION_STAIR = ModBlocks.register("station_stair", BlockFamily.ELEVATED, RenderKind.SOLID,
			new StationStairBlock(ModBlocks.metal()));
	public static final Block STAIR_ENCLOSURE = ModBlocks.register("stair_enclosure", BlockFamily.ELEVATED, RenderKind.TRANSLUCENT,
			new StairEnclosureBlock(ModBlocks.glass().sounds(net.minecraft.sound.BlockSoundGroup.METAL)), TIP_STYLE);
	public static final Block PLATFORM_WINDSCREEN = ModBlocks.register("platform_windscreen", BlockFamily.ELEVATED, RenderKind.TRANSLUCENT,
			new PlatformWindscreenBlock(ModBlocks.glass()), TIP_STYLE);
	public static final Block PLATFORM_FASCIA = ModBlocks.register("platform_fascia", BlockFamily.ELEVATED, RenderKind.SOLID,
			new PlatformFasciaBlock(ModBlocks.paving(MapColor.STONE_GRAY).nonOpaque()), TIP_STYLE);
	public static final Block STATION_FENCE = ModBlocks.register("station_fence", BlockFamily.ELEVATED, RenderKind.CUTOUT,
			new StationFenceBlock(ModBlocks.metal()), TIP_STYLE, TIP_JOINS_RAIL);
	public static final Block UTILITY_RUN = ModBlocks.register("utility_run", BlockFamily.ELEVATED, RenderKind.CUTOUT,
			new UtilityRunBlock(ModBlocks.metal()), TIP_STYLE);
	public static final Block DECK_LIGHT = ModBlocks.register("deck_light", BlockFamily.ELEVATED, RenderKind.SOLID,
			new AxisShapedBlock(ModBlocks.metal().luminance(state -> 15), box(1, 13, 6, 15, 16, 10)));

	// ---- Accessibility additions (creative tab: Passenger equipment) -----------------------------------------------

	public static final Block HANDRAIL = ModBlocks.register("handrail", BlockFamily.ACCESSIBILITY, RenderKind.TRANSLUCENT,
			new HandrailBlock(ModBlocks.metal()), TIP_STYLE, TIP_JOINS_RAIL);
	public static final Block TACTILE_JUNCTION = ModBlocks.register("tactile_junction", BlockFamily.ACCESSIBILITY, RenderKind.SOLID,
			new TactileJunctionBlock(ModBlocks.paving(MapColor.YELLOW)), TIP_STYLE);

	// ---- 1.4: noise barriers (A12), station equipment props (A10), lift status panel (A8) -------------------------

	public static final Block NOISE_BARRIER = ModBlocks.register("noise_barrier", BlockFamily.ELEVATED, RenderKind.TRANSLUCENT,
			new NoiseBarrierBlock(ModBlocks.glass().sounds(net.minecraft.sound.BlockSoundGroup.METAL)), TIP_STYLE, TIP + "noise_barrier");
	public static final Block FARE_GATE = ModBlocks.register("fare_gate", BlockFamily.STATION_EQUIPMENT, RenderKind.TRANSLUCENT,
			new FareGateBlock(ModBlocks.metal().nonOpaque()), TIP_STYLE, TIP + "fare_gate");
	public static final Block CARD_READER = ModBlocks.register("card_reader", BlockFamily.STATION_EQUIPMENT, RenderKind.CUTOUT,
			new CardReaderBlock(ModBlocks.metal().nonOpaque().luminance(state -> 4)), TIP_STYLE, TIP + "prop_only");
	public static final Block BOOTH_WINDOW = ModBlocks.register("booth_window", BlockFamily.STATION_EQUIPMENT, RenderKind.TRANSLUCENT,
			new FacingShapedBlock(ModBlocks.glass().sounds(net.minecraft.sound.BlockSoundGroup.METAL), Placement.TOWARD_PLAYER,
					union(box(0, 0, 12, 16, 16, 16), box(0, 6, 8, 16, 7, 12))), TIP + "prop_only");
	public static final Block CCTV_CAMERA = ModBlocks.register("cctv_camera", BlockFamily.STATION_EQUIPMENT, RenderKind.CUTOUT,
			new CctvCameraBlock(ModBlocks.metal().nonOpaque().noCollision()), TIP_STYLE, TIP + "prop_only");
	public static final Block LIFT_STATUS_PANEL = ModBlocks.register("lift_status_panel", BlockFamily.ACCESSIBILITY, RenderKind.CUTOUT,
			new LiftStatusPanelBlock(ModBlocks.metal().luminance(state -> 6), new TextLayout(8.5F, 11, 12, 13.4F, -1, false, SignStyle.LIFT),
					box(2, 3, 13.5, 14, 14, 16)), TIP + "lift_status");

	// ---- 1.4: platform screen doors and train-keyed edges (DESIGN_1.4 b-d) ----------------------------------------

	private static final net.minecraft.util.shape.VoxelShape EDGE = union(box(0, 12, 0, 16, 16, 16), box(0, 0, 2, 16, 12, 16));

	public static final Block SCREEN_DOOR_PANEL = ModBlocks.register("screen_door_panel", BlockFamily.PLATFORMS, RenderKind.TRANSLUCENT,
			new CurvedScreenDoorBlock(ModBlocks.glass().sounds(net.minecraft.sound.BlockSoundGroup.METAL), false), TIP_STYLE, TIP + "screen_door_panel");
	public static final Block SCREEN_DOOR_DOORWAY = ModBlocks.register("screen_door_doorway", BlockFamily.PLATFORMS, RenderKind.CUTOUT,
			MtrPlatformContract.screenDoorway(ModBlocks.metal().nonOpaque()), TIP_STYLE, TIP + "screen_door_doorway");
	public static final Block DROP_BARRIER_EDGE = ModBlocks.register("drop_barrier_edge", BlockFamily.PLATFORMS, RenderKind.CUTOUT,
			MtrPlatformContract.trainEdge(ModBlocks.paving(MapColor.STONE_GRAY).nonOpaque(), EDGE, TrainEdgeBlock.Mode.BARRIER), TIP + "drop_barrier_edge");
	public static final Block BOARDING_STEP_EDGE = ModBlocks.register("boarding_step_edge", BlockFamily.PLATFORMS, RenderKind.CUTOUT,
			MtrPlatformContract.trainEdge(ModBlocks.paving(MapColor.STONE_GRAY).nonOpaque(), EDGE, TrainEdgeBlock.Mode.STEP), TIP + "boarding_step_edge");

	private ElevatedBlocks() {
	}

	public static void init() {
		// Class loading registers everything declared here.
	}
}
