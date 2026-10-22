package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.text.SignStyle;
import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * Station name signs that carry an operator's mark at the left end of the row, the station name beside it. They are the
 * existing editable text sign (no new block entity, no ticking); the mark is painted on the model's face and the text keeps
 * {@link #MARK} model pixels clear of it. Registered via ModBlocks.register.
 */
public final class StationBlocksOperators {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_EDITABLE = TIP + "editable";
	private static final String TIP_JOINS = TIP + "joins";

	/** Width of the painted mark at the left end of a row, in model pixels. */
	public static final float MARK = 10F;

	private static final VoxelShape SHAPE = union(box(0, 7, 6.5, 16, 16, 9.5), box(1, 0, 7, 3, 7, 9), box(13, 0, 7, 15, 7, 9));

	public static final Block NS_STATION_SIGN = ModBlocks.register("ns_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_NS), SHAPE), TIP_EDITABLE, TIP_JOINS);
	public static final Block DB_STATION_SIGN = ModBlocks.register("db_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_DB), SHAPE), TIP_EDITABLE, TIP_JOINS);
	public static final Block GVB_STATION_SIGN = ModBlocks.register("gvb_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_GVB), SHAPE), TIP_EDITABLE, TIP_JOINS);

	public static final Block UNDERGROUND_STATION_SIGN = ModBlocks.register("underground_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_UNDERGROUND), SHAPE), TIP_EDITABLE, TIP_JOINS);
	public static final Block NATIONAL_RAIL_STATION_SIGN = ModBlocks.register("national_rail_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_NATIONAL_RAIL), SHAPE), TIP_EDITABLE, TIP_JOINS);
	public static final Block SBAHN_STATION_SIGN = ModBlocks.register("sbahn_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_SBAHN), SHAPE), TIP_EDITABLE, TIP_JOINS);
	public static final Block SBB_STATION_SIGN = ModBlocks.register("sbb_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_SBB), SHAPE), TIP_EDITABLE, TIP_JOINS);
	public static final Block JR_STATION_SIGN = ModBlocks.register("jr_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 6), layout(SignStyle.OPERATOR_JR), SHAPE), TIP_EDITABLE, TIP_JOINS);

	/**
	 * A station's name in letters on its facade (Jah, 10-22: the platform name board on the front looked out of place): a
	 * dark band fixed flat to the wall with white letters, joining along a row like the name signs. No legs, one face.
	 */
	public static final Block STATION_LETTERING = ModBlocks.register("station_lettering", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(ModBlocks.metal().luminance(state -> 8), new TextLayout(8F, 8, 16, 14.5F, -1F, true, SignStyle.STATION_LETTERING),
					box(0, 3, 14.5, 16, 13, 16)), TIP_EDITABLE, TIP_JOINS);

	private static TextLayout layout(SignStyle style) {
		return new TextLayout(11.5F, 8, 16, 6.5F, 9.5F, true, style, MARK);
	}

	private StationBlocksOperators() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
