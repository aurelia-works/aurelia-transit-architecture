package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.AxisShapedBlock;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.FramedGlassBlock;
import com.aureliatransit.architecture.block.GlassFacingBlock;
import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.InfoLayout;
import com.aureliatransit.architecture.block.SeatBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.ShapedBlock;
import com.aureliatransit.architecture.block.SignPoleBlock;
import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.mtr.MtrPlatformContract;
import com.aureliatransit.architecture.block.wayfinding.CarStopMarkerBlock;
import com.aureliatransit.architecture.block.wayfinding.StopBoardBlock;
import com.aureliatransit.architecture.text.SignStyle;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.Instrument;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.profile;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * Every Aurelia block, in creative-tab order. Shapes are in pixels for the north-facing model.
 */
public final class ModBlocks {

	public record Entry(String id, Block block, BlockFamily family, RenderKind renderKind) {
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_EDITABLE = TIP + "editable";
	private static final String TIP_JOINS = TIP + "joins";
	private static final String TIP_FACES_YOU = TIP + "faces_you";
	private static final String TIP_POINTS_AWAY = TIP + "points_away";
	private static final String TIP_WALL = TIP + "wall_mounted";
	private static final String TIP_SLOPE = TIP + "slope";
	private static final String TIP_SIT = TIP + "sit";
	private static final String TIP_CAR_STOP = TIP + "car_stop_cycle";

	// Profiles shared by models (tools/generate_assets.py) and collision: underside height in pixels as a function of z (0 = north).
	public static final DoubleUnaryOperator SLOPE_LOWER = z -> z / 2;
	public static final DoubleUnaryOperator SLOPE_UPPER = z -> 8 + z / 2;
	public static final DoubleUnaryOperator WAVE_RISE = z -> 8 * (z / 16) * (z / 16);
	public static final DoubleUnaryOperator WAVE_CREST = z -> 8 + z - z * z / 16;
	/** Eases from the height where {@link #WAVE_RISE} ends (8) into the next block's flat canopy (16) with a horizontal tangent. */
	public static final DoubleUnaryOperator WAVE_FLAT = z -> 8 + z - z * z / 32;

	private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape EDGE = union(box(0, 12, 0, 16, 16, 16), box(0, 0, 2, 16, 12, 16));
	private static final VoxelShape PLATE = box(0, 0, 0, 16, 2, 16);
	private static final VoxelShape POLE = box(7, 0, 7, 9, 16, 9);
	private static final VoxelShape BENCH = union(box(0, 0, 4, 16, 7.5, 11), box(0, 7.5, 11, 16, 14, 12.5));
	private static final VoxelShape BENCH_COLLISION = union(box(0, 0, 4, 16, 7.5, 12.5), box(0, 7.5, 11, 16, 14, 12.5));
	private static final VoxelShape SHELTER_SEAT = box(0, 5, 0, 16, 8, 7.5);
	private static final VoxelShape SHELTER_SEAT_COLLISION = box(0, 6.5, 0, 16, 8, 7.5);

	// ---- Platforms -------------------------------------------------------------------------------------------------
	// Flat walkable surfaces and edges carry MTR's platform marker so train doors open beside them (ramps do not).

	public static final Block PLATFORM_PAVING_LIGHT = register("platform_paving_light", BlockFamily.PLATFORMS, RenderKind.SOLID,
			MtrPlatformContract.surface(paving(MapColor.STONE_GRAY)));
	public static final Block PLATFORM_PAVING_DARK = register("platform_paving_dark", BlockFamily.PLATFORMS, RenderKind.SOLID,
			MtrPlatformContract.surface(paving(MapColor.DEEPSLATE_GRAY)));
	public static final Block TACTILE_WARNING_PAVING = register("tactile_warning_paving", BlockFamily.PLATFORMS, RenderKind.SOLID,
			MtrPlatformContract.surface(paving(MapColor.YELLOW)));
	public static final Block PLATFORM_EDGE = register("platform_edge", BlockFamily.PLATFORMS, RenderKind.SOLID,
			MtrPlatformContract.edge(paving(MapColor.STONE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER, EDGE), TIP_POINTS_AWAY);
	public static final Block PLATFORM_EDGE_WARNING = register("platform_edge_warning", BlockFamily.PLATFORMS, RenderKind.SOLID,
			MtrPlatformContract.edge(paving(MapColor.STONE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER, EDGE), TIP_POINTS_AWAY);
	public static final Block PLATFORM_EDGE_CURVE = register("platform_edge_curve", BlockFamily.PLATFORMS, RenderKind.SOLID,
			MtrPlatformContract.curve(paving(MapColor.STONE_GRAY).nonOpaque()), TIP + "style_cycle", TIP_POINTS_AWAY);
	public static final Block PLATFORM_RAMP_LOWER = register("platform_ramp_lower", BlockFamily.PLATFORMS, RenderKind.SOLID,
			new FacingShapedBlock(paving(MapColor.STONE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER,
					profile(SLOPE_LOWER, 0, true, 4)), TIP_SLOPE);
	public static final Block PLATFORM_RAMP_UPPER = register("platform_ramp_upper", BlockFamily.PLATFORMS, RenderKind.SOLID,
			new FacingShapedBlock(paving(MapColor.STONE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER,
					profile(SLOPE_UPPER, 0, true, 4)), TIP_SLOPE);

	// ---- Signage ---------------------------------------------------------------------------------------------------

	public static final Block STATION_NAME_SIGN = register("station_name_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(metal().luminance(state -> 6),
					new TextLayout(11.5F, 8, 16, 6.5F, 9.5F, true, SignStyle.STATION),
					union(box(0, 7, 6.5, 16, 16, 9.5), box(1, 0, 7, 3, 7, 9), box(13, 0, 7, 15, 7, 9))),
			TIP_EDITABLE, TIP_JOINS);
	public static final Block HANGING_STATION_SIGN = register("hanging_station_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(metal().luminance(state -> 6),
					new TextLayout(7, 7, 16, 6.5F, 9.5F, true, SignStyle.HANGING),
					union(box(0, 3, 6.5, 16, 11, 9.5), box(3, 11, 7, 5, 16, 9), box(11, 11, 7, 13, 16, 9))),
			TIP_EDITABLE, TIP_JOINS);
	public static final Block PLATFORM_NUMBER_SIGN = register("platform_number_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(metal().luminance(state -> 6),
					new TextLayout(7, 7.5F, 8, 7, 9, false, SignStyle.PLATFORM_NUMBER),
					union(box(3, 2, 7, 13, 12, 9), box(7.5, 12, 7.5, 8.5, 16, 8.5))),
			TIP_EDITABLE);
	// 1.5 car stop boards: the number is a block state; thin post or wall mount, cycled with an empty hand
	public static final Block UK_CAR_STOP_MARKER = register("uk_car_stop_marker", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new CarStopMarkerBlock(metal(), union(box(4, 6, 7, 12, 13, 9), box(7.5, 0, 7.5, 8.5, 6, 8.5)), box(4, 5, 14.5, 12, 12, 16)),
			TIP_CAR_STOP, TIP_WALL);
	public static final Block GERMAN_STOP_BOARD = register("german_stop_board", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new StopBoardBlock(metal(), union(box(4, 6, 7, 12, 14, 9), box(7.5, 0, 7.5, 8.5, 6, 8.5)), box(4, 4, 14.5, 12, 12, 16)),
			TIP_CAR_STOP, TIP_WALL);
	public static final Block DUTCH_STOP_BOARD = register("dutch_stop_board", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new StopBoardBlock(metal(), union(box(4, 6, 7, 12, 14, 9), box(7.5, 0, 7.5, 8.5, 6, 8.5)), box(4, 4, 14.5, 12, 12, 16)),
			TIP_CAR_STOP, TIP_WALL);
	public static final Block DIRECTION_SIGN = register("direction_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(metal().luminance(state -> 4),
					new TextLayout(7.5F, 6, 16, 7, 9, true, SignStyle.DIRECTION),
					union(box(0, 4, 7, 16, 11, 9), box(3, 11, 7.5, 5, 16, 8.5), box(11, 11, 7.5, 13, 16, 8.5))),
			TIP_EDITABLE, TIP_JOINS);
	public static final Block INFORMATION_CASE = register("information_case", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new InfoDisplayBlock(metal().luminance(state -> 6), Placement.TOWARD_PLAYER, box(1, 2, 14, 15, 15, 16),
					new InfoLayout(8, 8.5F, 12, 11, 14, -1, 0xFF23272B, 0xFF1B3A5E, 6, 0.8F)), TIP_WALL, TIP_EDITABLE);
	public static final Block SIGN_POLE = register("sign_pole", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new SignPoleBlock(metal()), TIP + "sign_pole");
	// 1.4 (A17, A16, A18): built as far as MTR allows, all text set by hand
	public static final Block PSD_TEXT_PANEL = register("psd_text_panel", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(metal().luminance(state -> 6),
					new TextLayout(8, 7, 16, 12.9F, -1, true, SignStyle.PSD),
					box(0, 4, 13, 16, 12, 16)),
			TIP_EDITABLE, TIP_JOINS, TIP + "psd_text_panel");
	public static final Block STAND_BACK_SIGN = register("stand_back_sign", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(metal(),
					new TextLayout(8, 10, 12.5F, 13.9F, -1, false, SignStyle.WARNING),
					box(1, 2, 14, 15, 14, 16)),
			TIP_EDITABLE, TIP + "stand_back_sign");
	public static final Block COMPOSITION_BOARD = register("composition_board", BlockFamily.SIGNAGE, RenderKind.CUTOUT,
			new TextSignBlock(metal().luminance(state -> 6),
					new TextLayout(7, 7.5F, 16, 6.4F, 9.6F, true, SignStyle.COMPOSITION),
					union(box(0, 3, 6.5, 16, 11, 9.5), box(3, 11, 7.5, 5, 16, 8.5), box(11, 11, 7.5, 13, 16, 8.5))),
			TIP_EDITABLE, TIP_JOINS, TIP + "composition_board");

	// ---- Furniture -------------------------------------------------------------------------------------------------

	public static final Block STEEL_BENCH = register("steel_bench", BlockFamily.FURNITURE, RenderKind.CUTOUT,
			new SeatBlock(metal(), Placement.TOWARD_PLAYER, BENCH, BENCH_COLLISION, 2, 7.5, 7.5, true), TIP_FACES_YOU, TIP_SIT);
	public static final Block WOODEN_BENCH = register("wooden_bench", BlockFamily.FURNITURE, RenderKind.CUTOUT,
			new SeatBlock(metal(), Placement.TOWARD_PLAYER, BENCH, BENCH_COLLISION, 2, 7.5, 7.5, true), TIP_FACES_YOU, TIP_SIT);
	public static final Block WASTE_BIN = register("waste_bin", BlockFamily.FURNITURE, RenderKind.CUTOUT,
			new FacingShapedBlock(metal(), Placement.TOWARD_PLAYER, box(3.5, 0, 3.5, 12.5, 14.5, 12.5)));
	public static final Block BOLLARD = register("bollard", BlockFamily.FURNITURE, RenderKind.CUTOUT,
			new ShapedBlock(metal(), box(5.5, 0, 5.5, 10.5, 14, 10.5)));
	public static final Block PLATFORM_LAMP = register("platform_lamp", BlockFamily.FURNITURE, RenderKind.CUTOUT,
			new FacingShapedBlock(metal().luminance(state -> 15), Placement.TOWARD_PLAYER,
					union(box(7, 0, 7, 9, 13, 9), box(5, 13, 3, 11, 15, 11))));
	public static final Block INFORMATION_PILLAR = register("information_pillar", BlockFamily.FURNITURE, RenderKind.CUTOUT,
			new InfoDisplayBlock(metal().luminance(state -> 7), Placement.TOWARD_PLAYER, box(3, 0, 6, 13, 16, 10),
					new InfoLayout(8, 8, 8, 13, 6, 10, 0xFFEDEDE8, 0xFFF2C25A, 6, 0.7F)), TIP_FACES_YOU, TIP_EDITABLE);

	// ---- Architecture ----------------------------------------------------------------------------------------------

	public static final Block STEEL_COLUMN_SQUARE = register("steel_column_square", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new ShapedBlock(metal(), box(4, 0, 4, 12, 16, 12)));
	public static final Block STEEL_COLUMN_ROUND = register("steel_column_round", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new ShapedBlock(metal(), box(4.5, 0, 4.5, 11.5, 16, 11.5)));
	public static final Block STRUCTURAL_BEAM = register("structural_beam", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new AxisShapedBlock(metal(), box(0, 11, 5, 16, 16, 11)));
	public static final Block ROOF_SUPPORT = register("roof_support", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new AxisShapedBlock(metal(), union(box(5, 0, 5, 11, 6, 11), box(4, 6, 6, 12, 10, 10), box(0, 10, 6, 16, 16, 10))));
	public static final Block GLASS_WALL = register("glass_wall", BlockFamily.ARCHITECTURE, RenderKind.TRANSLUCENT,
			new FramedGlassBlock(glass()));
	public static final Block GLASS_PANEL = register("glass_panel", BlockFamily.ARCHITECTURE, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(glass(), Placement.TOWARD_PLAYER, box(0, 0, 7, 16, 16, 9)));
	public static final Block GLASS_BARRIER = register("glass_barrier", BlockFamily.ARCHITECTURE, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(glass(), Placement.TOWARD_PLAYER,
					union(box(0, 0, 7.25, 16, 13, 8.75), box(0, 13, 6.5, 16, 15, 9.5)), box(0, 0, 7, 16, 24, 9)));
	public static final Block CANOPY_FLAT = register("canopy_flat", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new ShapedBlock(metal(), PLATE));
	public static final Block CANOPY_EDGE = register("canopy_edge", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(metal(), Placement.AWAY_FROM_PLAYER, union(PLATE, box(0, 0, 0, 16, 3, 1.5))), TIP_POINTS_AWAY);
	public static final Block CANOPY_SLOPE_LOWER = register("canopy_slope_lower", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(metal(), Placement.AWAY_FROM_PLAYER, profile(SLOPE_LOWER, 2, false, 2)), TIP_SLOPE);
	public static final Block CANOPY_SLOPE_UPPER = register("canopy_slope_upper", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(metal(), Placement.AWAY_FROM_PLAYER, profile(SLOPE_UPPER, 2, false, 2)), TIP_SLOPE);
	public static final Block CANOPY_WAVE_RISE = register("canopy_wave_rise", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(metal(), Placement.AWAY_FROM_PLAYER, profile(WAVE_RISE, 2, false, 2)), TIP_SLOPE);
	public static final Block CANOPY_WAVE_CREST = register("canopy_wave_crest", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(metal(), Placement.AWAY_FROM_PLAYER, profile(WAVE_CREST, 2, false, 2)));
	public static final Block CANOPY_SKYLIGHT = register("canopy_skylight", BlockFamily.ARCHITECTURE, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(glass(), Placement.TOWARD_PLAYER, PLATE));
	public static final Block CANOPY_LIGHT = register("canopy_light", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new FacingShapedBlock(metal().luminance(state -> 15), Placement.TOWARD_PLAYER, PLATE));

	// ---- Catenary --------------------------------------------------------------------------------------------------

	public static final Block CATENARY_MAST = register("catenary_mast", BlockFamily.CATENARY, RenderKind.SOLID,
			new AxisShapedBlock(metal(), box(5, 0, 5, 11, 16, 11)));
	public static final Block CATENARY_CANTILEVER = register("catenary_cantilever", BlockFamily.CATENARY, RenderKind.SOLID,
			new FacingShapedBlock(metal(), Placement.AWAY_FROM_PLAYER, box(6, 4, 0, 10, 15, 16)), TIP_POINTS_AWAY);
	public static final Block CATENARY_GANTRY = register("catenary_gantry", BlockFamily.CATENARY, RenderKind.CUTOUT,
			new AxisShapedBlock(metal(), box(0, 10, 4, 16, 16, 12)));
	public static final Block CATENARY_INSULATOR = register("catenary_insulator", BlockFamily.CATENARY, RenderKind.SOLID,
			new ShapedBlock(metal(), box(6, 3, 6, 10, 16, 10)));

	// ---- Bus -------------------------------------------------------------------------------------------------------

	public static final Block BUS_STOP_SIGN = register("bus_stop_sign", BlockFamily.BUS, RenderKind.CUTOUT,
			new TextSignBlock(metal(),
					new TextLayout(7.5F, 9, 13, 6.75F, 9.25F, false, SignStyle.BUS_STOP),
					union(POLE, box(1, 3, 6.75, 15, 12, 9.25), box(3, 12, 7, 13, 16, 9))),
			TIP_EDITABLE);
	public static final Block BUS_TIMETABLE_CASE = register("bus_timetable_case", BlockFamily.BUS, RenderKind.CUTOUT,
			new InfoDisplayBlock(metal().luminance(state -> 5), Placement.TOWARD_PLAYER, union(POLE, box(1, 3, 4, 15, 15, 7)),
					new InfoLayout(8, 9, 12, 10, 4, -1, 0xFF23272B, 0xFF1F5E3C, 8, 0.7F)), TIP_FACES_YOU, TIP_EDITABLE);
	public static final Block BUS_SHELTER_GLASS = register("bus_shelter_glass", BlockFamily.BUS, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(glass(), Placement.AWAY_FROM_PLAYER, box(0, 0, 0, 16, 16, 1.5)), TIP_WALL);
	public static final Block BUS_SHELTER_ROOF = register("bus_shelter_roof", BlockFamily.BUS, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(glass(), Placement.AWAY_FROM_PLAYER, union(PLATE, box(0, 0, 0, 16, 4, 1.5))), TIP_POINTS_AWAY);
	public static final Block BUS_SHELTER_SEAT = register("bus_shelter_seat", BlockFamily.BUS, RenderKind.CUTOUT,
			new SeatBlock(metal(), Placement.AWAY_FROM_PLAYER, SHELTER_SEAT, SHELTER_SEAT_COLLISION, 2, 8, 4, false), TIP_WALL, TIP_SIT);
	public static final Block BUS_CURB = register("bus_curb", BlockFamily.BUS, RenderKind.SOLID,
			new FacingShapedBlock(paving(MapColor.STONE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER,
					union(box(0, 0, 0, 16, 2, 16), box(0, 2, 0.75, 16, 5, 16), box(0, 5, 1.5, 16, 16, 16))), TIP_POINTS_AWAY);
	public static final Block BUS_CURB_LOW = register("bus_curb_low", BlockFamily.BUS, RenderKind.SOLID,
			new FacingShapedBlock(paving(MapColor.STONE_GRAY).nonOpaque(), Placement.AWAY_FROM_PLAYER,
					union(box(0, 0, 0, 16, 2, 16), box(0, 2, 0.75, 16, 4, 16), box(0, 4, 1.5, 16, 8, 16))), TIP_POINTS_AWAY);

	private ModBlocks() {
	}

	static AbstractBlock.Settings paving(MapColor color) {
		return AbstractBlock.Settings.create().mapColor(color).instrument(Instrument.BASEDRUM).strength(1.5F, 6.0F).sounds(BlockSoundGroup.STONE);
	}

	static AbstractBlock.Settings metal() {
		return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(2.0F, 6.0F).sounds(BlockSoundGroup.METAL).nonOpaque();
	}

	static AbstractBlock.Settings glass() {
		return AbstractBlock.Settings.create().mapColor(MapColor.CLEAR).strength(0.8F, 3.0F).sounds(BlockSoundGroup.GLASS).nonOpaque()
				.allowsSpawning((state, world, pos, type) -> false)
				.solidBlock((state, world, pos) -> false)
				.suffocates((state, world, pos) -> false)
				.blockVision((state, world, pos) -> false);
	}

	/**
	 * Registers a block and its item. Also used by {@link LiveBlocks} and {@link InteractiveBlocks}.
	 */
	static Block register(String id, BlockFamily family, RenderKind renderKind, Block block, String... tooltipKeys) {
		Registry.register(Registries.BLOCK, AureliaTransitArchitecture.id(id), block);
		Registry.register(Registries.ITEM, AureliaTransitArchitecture.id(id), new DescribedBlockItem(block, new Item.Settings(), List.of(tooltipKeys)));
		ENTRIES.add(new Entry(id, block, family, renderKind));
		return block;
	}

	public static List<Entry> entries() {
		return Collections.unmodifiableList(ENTRIES);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
