package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity;
import com.aureliatransit.architecture.block.mtr.MtrPlatformContract;
import com.aureliatransit.architecture.block.wayfinding.BoardingMarkerBlock;
import com.aureliatransit.architecture.block.wayfinding.EntrancePylonBlock;
import com.aureliatransit.architecture.block.wayfinding.HelpPointBlock;
import com.aureliatransit.architecture.block.wayfinding.WayfindingPanelSpec;
import com.aureliatransit.architecture.block.wayfinding.WayfindingPlateBlock;
import com.aureliatransit.architecture.block.wayfinding.WayfindingSignBlock;
import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.WayfindingPanelKind;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * The 1.2 wayfinding, accessibility and street/bus presentation blocks. One configurable block per use case: the
 * variants (accessible route, lift, help, exit ... pictograms; exit letters; destinations) come from the shared
 * {@code WayfindingData} edited in one screen, not from duplicate blocks. All data blocks share one block entity type.
 */
public final class WayfindingBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_CONFIGURE = TIP + "wf_configure";
	private static final String TIP_JOINS = TIP + "joins";
	private static final String TIP_TALL = TIP + "wf_tall";
	private static final String TIP_HANGING = TIP + "live_hanging";
	private static final String TIP_GUIDANCE = TIP + "wf_guidance";
	private static final String TIP_MARKER = TIP + "wf_marker";
	private static final String TIP_HELP = TIP + "wf_help";
	private static final String TIP_BOARD = TIP + "wf_board";
	private static final String TIP_EPAPER = TIP + "wf_epaper";
	private static final String TIP_TERMINAL = TIP + "wf_terminal";
	private static final String TIP_TERMINAL_EDIT = TIP + "wf_terminal_edit";
	private static final String TIP_FACES_YOU = TIP + "faces_you";

	private static final int WHITE = 0xFFFFFFFF;
	private static final int CHARCOAL = 0xFF2C2F33;
	private static final int NAVY = 0xFF1D375A;
	private static final int EXIT_GREEN = 0xFF1E6B45;
	private static final int PAPER = 0xFFCDD0C3;
	private static final int INK = 0xFF1E201A;

	// Panel placement per block: kind, centre x/y, size, front z, back z (-1 = single sided), joins, text, face, default pictogram.
	private static final WayfindingPanelSpec PYLON_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.ENTRANCE_PYLON, 8, 16, 9, 26, 6, 10, false, WHITE, CHARCOAL, Pictogram.NONE);
	private static final WayfindingPanelSpec WALL_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.WALL_DIRECTION, 8, 8, 16, 8, 13, -1, true, WHITE, CHARCOAL, Pictogram.NONE);
	private static final WayfindingPanelSpec HANGING_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.HANGING_DIRECTION, 8, 8, 16, 7, 7, 9, true, WHITE, CHARCOAL, Pictogram.NONE);
	private static final WayfindingPanelSpec EXIT_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.EXIT, 8, 9, 16, 5, 7, 9, true, WHITE, EXIT_GREEN, Pictogram.EXIT);
	private static final WayfindingPanelSpec STREET_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.STREET, 9.5F, 10, 12.5F, 4.5F, 7.25F, 8.75F, false, WHITE, NAVY, Pictogram.NONE);
	private static final WayfindingPanelSpec PICTOGRAM_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.PICTOGRAM, 8, 7, 9, 9, 7, 9, false, WHITE, NAVY, Pictogram.ACCESSIBLE_ROUTE);
	private static final WayfindingPanelSpec BUS_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.BUS_STOP, 8, 8, 16, 14.7F, 13, -1, true, INK, PAPER, Pictogram.NONE);

	private static final int DARK = 0xFF16191D;
	private static final WayfindingPanelSpec BOARD_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.BOARD, 8, 8, 16, 12.5F, 13, -1, true, WHITE, CHARCOAL, Pictogram.NONE);
	private static final WayfindingPanelSpec TERMINAL_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.TERMINAL, 8, 8, 16, 12, 11.9F, -1, true, WHITE, DARK, Pictogram.NONE);
	private static final WayfindingPanelSpec KIOSK_SPEC = new WayfindingPanelSpec(WayfindingPanelKind.TERMINAL, 8, 10, 9, 8, 5.9F, -1, false, WHITE, DARK, Pictogram.NONE);

	// ---- Wayfinding (creative tab: Wayfinding) ---------------------------------------------------------------------

	public static final Block ENTRANCE_PYLON = ModBlocks.register("entrance_pylon", BlockFamily.WAYFINDING, RenderKind.CUTOUT,
			new EntrancePylonBlock(ModBlocks.metal().luminance(state -> 6), box(2, 0, 5.5, 14, 16, 10.5), PYLON_SPEC),
			TIP_CONFIGURE, TIP_TALL);
	public static final Block WALL_WAYFINDING_SIGN = ModBlocks.register("wall_wayfinding_sign", BlockFamily.WAYFINDING, RenderKind.CUTOUT,
			new WayfindingSignBlock(ModBlocks.metal().luminance(state -> 4), box(0, 3, 13, 16, 13, 16), WALL_SPEC),
			TIP_CONFIGURE, TIP_JOINS);
	public static final Block STATION_INFO_BOARD = ModBlocks.register("station_info_board", BlockFamily.WAYFINDING, RenderKind.CUTOUT,
			new WayfindingSignBlock(ModBlocks.metal().luminance(state -> 7), box(0, 1, 12.75, 16, 15, 16), BOARD_SPEC),
			TIP_CONFIGURE, TIP_BOARD, TIP_JOINS);
	public static final Block HANGING_WAYFINDING_SIGN = ModBlocks.register("hanging_wayfinding_sign", BlockFamily.WAYFINDING, RenderKind.CUTOUT,
			new WayfindingSignBlock(ModBlocks.metal().luminance(state -> 4),
					union(box(0, 4, 7, 16, 12, 9), box(3, 12, 7.5, 5, 16, 8.5), box(11, 12, 7.5, 13, 16, 8.5)), HANGING_SPEC),
			TIP_CONFIGURE, TIP_JOINS, TIP_HANGING);
	public static final Block EXIT_SIGN = ModBlocks.register("exit_sign", BlockFamily.WAYFINDING, RenderKind.CUTOUT,
			new WayfindingSignBlock(ModBlocks.metal().luminance(state -> 7),
					union(box(0, 6, 7, 16, 12, 9), box(3, 12, 7.5, 5, 16, 8.5), box(11, 12, 7.5, 13, 16, 8.5)), EXIT_SPEC),
			TIP_CONFIGURE, TIP_JOINS, TIP_HANGING);

	// ---- Street (creative tab: Bus / Street) -----------------------------------------------------------------------

	public static final Block STREET_SIGN = ModBlocks.register("street_sign", BlockFamily.STREET, RenderKind.CUTOUT,
			new WayfindingPlateBlock(ModBlocks.metal().luminance(state -> 3), union(box(1, 0, 7, 3, 16, 9), box(3, 7, 7, 16, 13, 9)), STREET_SPEC),
			TIP_CONFIGURE);

	// ---- Accessibility (creative tab: Passenger equipment) ---------------------------------------------------------

	public static final Block PICTOGRAM_SIGN = ModBlocks.register("pictogram_sign", BlockFamily.ACCESSIBILITY, RenderKind.CUTOUT,
			new WayfindingPlateBlock(ModBlocks.metal().luminance(state -> 5), union(box(3, 2, 7, 13, 12, 9), box(7.5, 12, 7.5, 8.5, 16, 8.5)), PICTOGRAM_SPEC),
			TIP_CONFIGURE, TIP_HANGING);
	public static final Block TACTILE_GUIDANCE_PAVING = ModBlocks.register("tactile_guidance_paving", BlockFamily.ACCESSIBILITY, RenderKind.SOLID,
			MtrPlatformContract.edge(ModBlocks.paving(MapColor.YELLOW), Placement.AWAY_FROM_PLAYER, box(0, 0, 0, 16, 16, 16)),
			TIP_GUIDANCE);
	public static final Block HELP_POINT = ModBlocks.register("help_point", BlockFamily.ACCESSIBILITY, RenderKind.CUTOUT,
			new HelpPointBlock(ModBlocks.metal().luminance(state -> 6), box(4, 3, 12, 12, 14, 16)),
			TIP_FACES_YOU, TIP_HELP);
	public static final Block BOARDING_MARKER = ModBlocks.register("boarding_marker", BlockFamily.ACCESSIBILITY, RenderKind.CUTOUT,
			new BoardingMarkerBlock(ModBlocks.paving(MapColor.IRON_GRAY).nonOpaque().strength(0.5F), box(0, 0, 0, 16, 0.5, 16)),
			TIP_MARKER);

	// ---- Bus ---------------------------------------------------------------------------------------------------------

	public static final Block BUS_EPAPER_BOARD = ModBlocks.register("bus_epaper_board", BlockFamily.BUS, RenderKind.CUTOUT,
			new WayfindingSignBlock(ModBlocks.metal(), box(0, 0, 13, 16, 16, 16), BUS_SPEC),
			TIP_CONFIGURE, TIP_EPAPER, TIP_JOINS);

	// ---- Passenger information terminals (creative tab: Passenger equipment) ---------------------------------------

	public static final Block PASSENGER_INFO_TERMINAL = ModBlocks.register("passenger_info_terminal", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new WayfindingSignBlock(ModBlocks.metal().luminance(state -> 8), box(0, 1, 12, 16, 15, 16), TERMINAL_SPEC),
			TIP_TERMINAL, TIP_TERMINAL_EDIT, TIP_JOINS);
	public static final Block PASSENGER_INFO_KIOSK = ModBlocks.register("passenger_info_kiosk", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new WayfindingPlateBlock(ModBlocks.metal().luminance(state -> 8), union(box(2, 0, 5, 14, 2, 12), box(3, 2, 6, 13, 16, 11)), KIOSK_SPEC),
			TIP_TERMINAL, TIP_TERMINAL_EDIT);

	public static final BlockEntityType<WayfindingSignBlockEntity> SIGN_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("wayfinding_sign"),
			BlockEntityType.Builder.create(WayfindingSignBlockEntity::new, ENTRANCE_PYLON, WALL_WAYFINDING_SIGN, STATION_INFO_BOARD, HANGING_WAYFINDING_SIGN, EXIT_SIGN, STREET_SIGN,
					PICTOGRAM_SIGN, BUS_EPAPER_BOARD, PASSENGER_INFO_TERMINAL, PASSENGER_INFO_KIOSK).build(null)
	);

	private WayfindingBlocks() {
	}

	public static void init() {
		// Class loading registers everything declared here.
	}
}
