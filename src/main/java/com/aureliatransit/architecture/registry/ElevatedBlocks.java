package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.AxisShapedBlock;
import com.aureliatransit.architecture.block.elevated.HandrailBlock;
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

	private ElevatedBlocks() {
	}

	public static void init() {
		// Class loading registers everything declared here.
	}
}
