package com.aureliatransit.architecture.station;

/**
 * Chooses a block id for every {@link Role}, from the spec's style and its material overrides. Ids are plain strings so this
 * stays testable without the game; the in-game adapter resolves them in the block registry and refuses to build if one is
 * missing.
 */
public final class StylePalette {

	/** MTR 4's rail node block. */
	public static final String MTR_RAIL_NODE = "mtr:rail";
	/** MTR lift parts: the track a car runs on, the track cell of a stop, a landing door half and a call button. */
	public static final String MTR_LIFT_TRACK = "mtr:lift_track_1";
	public static final String MTR_LIFT_FLOOR = "mtr:lift_track_floor_1";
	public static final String MTR_LIFT_DOOR = "mtr:lift_door_1";
	public static final String MTR_LIFT_BUTTONS = "mtr:lift_buttons_1";
	/** MTR's escalator: a step, with its handrail block in the cell above. */
	public static final String MTR_ESCALATOR_STEP = "mtr:escalator_step";
	public static final String MTR_ESCALATOR_SIDE = "mtr:escalator_side";

	private static final String FOUNDATION = "minecraft:stone_bricks";
	private static final String ATA = "aurelia_transit_architecture:";

	private final String surface;
	private final String edge;
	private final String column;
	private final String cap;
	private final String roof;
	private final String roofEdge;
	private final String roofLight;
	private final String lamp;
	private final String bench;
	private final String sign;
	private Building building = Building.of(BuildingStyle.MODERN);
	private BuildingStyle buildingStyle = BuildingStyle.NONE;
	private StationStyle style = StationStyle.MODERN;
	private RoofMaterial roofMaterial = RoofMaterial.STYLE_DEFAULT;

	/**
	 * Materials of a station building and its footbridge. Ids are ATA or vanilla blocks; {@code cornice} and {@code window}
	 * are facing blocks whose front points outwards.
	 */
	public record Building(String wall, String trim, String plinth, String cornice, String window, String floor, String upperFloor,
			String roof, String light, String stair, String deck, String bridgeWall, String bridgeRoof, String bridgeLight, String pier,
			String bench, String interiorWall) {

		public static Building of(BuildingStyle style) {
			return switch (style) {
				case NONE, MODERN -> new Building(ata("metro_pale_concrete"), ata("metro_steel_cladding"), ata("metro_steel_cladding"),
						ata("metro_steel_cladding"), ata("curtain_wall_glass"), ata("utrecht_light_grey_floor_tile"), ata("utrecht_light_grey_floor_tile"),
						ata("metro_pale_concrete"), ata("metro_recessed_light"), ata("station_stair"), ata("utrecht_light_grey_floor_tile"),
						ata("curtain_wall_glass"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_square"), ata("steel_bench"),
						ata("metro_pale_concrete"));
				case AMSTERDAM -> new Building(ata("amsterdam_red_brick"), ata("amsterdam_brick_sandstone_band"), ata("amsterdam_brick_sandstone_band"),
						ata("amsterdam_stone_cornice"), ata("amsterdam_arched_window"), ata("utrecht_light_grey_floor_tile"),
						ata("rotterdam_black_stone_floor"), ata("amsterdam_red_brick"), ata("metro_recessed_light"), ata("station_stair"),
						ata("rotterdam_black_stone_floor"), ata("curtain_wall_glass"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_round"),
						ata("wooden_bench"), ata("amsterdam_red_brick"));
				case GOTHIC_BRICK -> new Building(ata("hamburg_clinker_brick"), ata("hamburg_stone_trim"), ata("frankfurt_sandstone_rusticated"),
						ata("amsterdam_stone_cornice"), ata("amsterdam_arched_window"), ata("antwerp_marble_floor_tile"), ata("antwerp_marble_floor_tile"),
						ata("hamburg_clinker_brick"), ata("metro_recessed_light"), ata("station_stair"), ata("antwerp_marble_floor_tile"),
						ata("wired_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("amsterdam_cast_iron_shed_leg"), ata("wooden_bench"),
						ata("hamburg_stone_trim"));
				case ANTWERP -> new Building(ata("antwerp_ornate_limestone"), ata("roma_travertine"), ata("frankfurt_sandstone_rusticated"),
						ata("frankfurt_sandstone_cornice"), ata("clear_float_glass_pane"), ata("antwerp_marble_floor_tile"),
						ata("antwerp_marble_floor_tile"), ata("antwerp_ornate_limestone"), ata("metro_recessed_light"), ata("station_stair"),
						ata("antwerp_marble_floor_tile"), ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"),
						ata("amsterdam_cast_iron_shed_leg"), ata("wooden_bench"), ata("antwerp_polished_marble_wall"));
				case SANDSTONE -> new Building(ata("frankfurt_sandstone_ashlar"), ata("frankfurt_sandstone_rusticated"),
						ata("frankfurt_sandstone_rusticated"), ata("frankfurt_sandstone_cornice"), ata("clear_float_glass_pane"),
						ata("munich_terrazzo_floor"), ata("munich_terrazzo_floor"), ata("frankfurt_sandstone_ashlar"), ata("metro_recessed_light"),
						ata("station_stair"), ata("munich_terrazzo_floor"), ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"),
						ata("steel_column_round"), ata("steel_bench"), ata("frankfurt_sandstone_ashlar"));
				case CLINKER -> new Building(ata("hamburg_clinker_brick"), ata("hamburg_clinker_stone_trim"), ata("hamburg_clinker_stone_trim"),
						ata("frankfurt_sandstone_cornice"), ata("clear_float_glass_pane"), ata("munich_terrazzo_floor"), ata("munich_terrazzo_floor"),
						ata("hamburg_clinker_brick"), ata("metro_recessed_light"), ata("station_stair"), ata("munich_terrazzo_floor"),
						ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_round"), ata("steel_bench"),
						ata("hamburg_clinker_brick"));
				case CONCRETE -> new Building(ata("munich_exposed_concrete"), ata("munich_board_formed_concrete"), ata("munich_board_formed_concrete"),
						ata("metro_steel_cladding"), ata("curtain_wall_glass"), ata("metro_granite_floor"), ata("metro_granite_floor"),
						ata("munich_exposed_concrete"), ata("metro_recessed_light"), ata("station_stair"), ata("metro_granite_floor"),
						ata("curtain_wall_glass"), ata("canopy_flat"), ata("canopy_light"), ata("metro_column_steel"), ata("steel_bench"),
						ata("munich_exposed_concrete"));
				case BEAUX_ARTS -> new Building(ata("roma_travertine"), ata("antwerp_ornate_limestone"), ata("frankfurt_sandstone_rusticated"),
						ata("frankfurt_sandstone_cornice"), ata("clear_float_glass_pane"), ata("antwerp_marble_floor_tile"), ata("roma_polished_floor"),
						ata("roma_travertine"), ata("metro_recessed_light"), ata("station_stair"), ata("roma_polished_floor"),
						ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_round"), ata("wooden_bench"),
						ata("antwerp_polished_marble_wall"));
				case TOKYO -> new Building("minecraft:bricks", ata("amsterdam_brick_sandstone_band"), ata("frankfurt_sandstone_rusticated"),
						ata("amsterdam_stone_cornice"), ata("amsterdam_arched_window"), ata("antwerp_marble_floor_tile"), ata("antwerp_marble_floor_tile"),
						"minecraft:deepslate_tiles", ata("metro_recessed_light"), ata("station_stair"), ata("antwerp_marble_floor_tile"),
						ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("amsterdam_cast_iron_shed_leg"), ata("wooden_bench"),
						"minecraft:smooth_quartz");
				case ZURICH -> new Building("minecraft:smooth_sandstone", "minecraft:cut_sandstone", ata("frankfurt_sandstone_rusticated"),
						ata("frankfurt_sandstone_cornice"), ata("clear_float_glass_pane"), ata("munich_terrazzo_floor"), ata("munich_terrazzo_floor"),
						"minecraft:deepslate_tiles", ata("metro_recessed_light"), ata("station_stair"), ata("munich_terrazzo_floor"),
						ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_round"), ata("wooden_bench"),
						"minecraft:smooth_sandstone");
				case PARIS -> new Building(ata("roma_travertine"), ata("antwerp_ornate_limestone"), ata("frankfurt_sandstone_rusticated"),
						ata("frankfurt_sandstone_cornice"), ata("clear_float_glass_pane"), ata("antwerp_marble_floor_tile"), ata("roma_polished_floor"),
						ata("roma_travertine"), ata("metro_recessed_light"), ata("station_stair"), ata("roma_polished_floor"),
						ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("amsterdam_cast_iron_shed_leg"), ata("wooden_bench"),
						"minecraft:calcite");
				case MILAN -> new Building("minecraft:polished_diorite", ata("roma_travertine"), "minecraft:polished_andesite",
						ata("frankfurt_sandstone_cornice"), ata("clear_float_glass_pane"), ata("antwerp_marble_floor_tile"), ata("roma_polished_floor"),
						"minecraft:polished_diorite", ata("metro_recessed_light"), ata("station_stair"), ata("roma_polished_floor"),
						ata("clear_float_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_round"), ata("steel_bench"),
						ata("antwerp_polished_marble_wall"));
				case BERLIN -> new Building(ata("metro_steel_cladding"), ata("metro_steel_cladding"), ata("metro_steel_cladding"),
						ata("metro_steel_cladding"), ata("blue_tinted_glass"), ata("metro_granite_floor"), ata("metro_granite_floor"),
						ata("metro_steel_cladding"), ata("metro_recessed_light"), ata("station_stair"), ata("metro_granite_floor"),
						ata("blue_tinted_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_square"), ata("steel_bench"),
						ata("metro_pale_concrete"));
				case HONG_KONG -> new Building("minecraft:white_concrete", "minecraft:smooth_quartz", "minecraft:smooth_quartz",
						"minecraft:smooth_quartz", ata("low_iron_glass"), ata("metro_granite_floor"), ata("metro_granite_floor"),
						"minecraft:smooth_quartz", ata("metro_recessed_light"), ata("station_stair"), ata("metro_granite_floor"),
						ata("low_iron_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("utrecht_tree_column_white"), ata("steel_bench"),
						"minecraft:white_concrete");
				case BEIJING -> new Building(ata("metro_pale_concrete"), ata("metro_steel_cladding"), ata("metro_steel_cladding"),
						ata("metro_steel_cladding"), ata("grey_tinted_glass"), ata("metro_granite_floor"), ata("metro_granite_floor"),
						ata("metro_steel_cladding"), ata("metro_recessed_light"), ata("station_stair"), ata("metro_granite_floor"),
						ata("grey_tinted_glass_pane"), ata("canopy_flat"), ata("canopy_light"), ata("steel_column_square"), ata("steel_bench"),
						ata("metro_pale_concrete"));
			};
		}
	}

	private StylePalette(String surface, String edge, String column, String cap, String roof, String roofEdge, String roofLight, String lamp,
			String bench, String sign) {
		this.surface = surface;
		this.edge = edge;
		this.column = column;
		this.cap = cap;
		this.roof = roof;
		this.roofEdge = roofEdge;
		this.roofLight = roofLight;
		this.lamp = lamp;
		this.bench = bench;
		this.sign = sign;
	}

	public static StylePalette of(StationSpec spec) {
		final boolean hall = spec.roof() == RoofType.HALL;
		final boolean tactile = spec.tactile();
		final StylePalette base = switch (spec.style()) {
			case MODERN -> new StylePalette(ata("platform_paving_light"), ata(tactile ? "platform_edge_warning" : "platform_edge"),
					ata("steel_column_square"), ata("steel_column_square"), ata("canopy_flat"), ata("canopy_edge"), ata("canopy_light"),
					ata("platform_lamp"), ata("steel_bench"), ata("station_name_sign"));
			case DUTCH -> new StylePalette(ata("leidsche_rijn_concrete_platform_paver"),
					ata(tactile ? "platform_edge_warning" : "platform_edge"), ata("steel_column_round"), ata("utrecht_tree_column_white"),
					ata(hall ? "utrecht_wave_roof_panel" : "leidsche_rijn_timber_soffit_canopy"),
					ata(hall ? "utrecht_wave_roof_edge" : "canopy_edge"), ata("canopy_light"), ata("platform_lamp"), ata("wooden_bench"),
					ata("dutch_station_sign"));
			case GERMAN_SBAHN -> new StylePalette(ata("sbahn_platform_slab"),
					ata(tactile ? "sbahn_platform_edge_tactile" : "sbahn_platform_edge"), ata("steel_column_round"), ata("steel_column_round"),
					ata(hall ? "munich_ribbed_roof_deck" : "canopy_flat"), ata("canopy_edge"), ata("canopy_light"),
					ata("sbahn_platform_light"), ata("steel_bench"), ata("german_station_sign"));
			case METRO -> new StylePalette(ata("metro_terrazzo_floor"), ata(tactile ? "metro_tactile_edge" : "platform_edge"),
					ata("metro_column_tiled"), ata("metro_column_tiled"), ata("metro_acoustic_ceiling"), ata("metro_acoustic_ceiling"),
					ata("metro_recessed_light"), ata("platform_lamp"), ata("steel_bench"), ata("station_name_sign"));
		};
		final StylePalette palette = new StylePalette(surfaceOverride(spec.surface(), base.surface), base.edge, base.column, base.cap,
				roofOverride(spec.roofMaterial(), hall, base.roof), base.roofEdge, base.roofLight, base.lamp, base.bench, base.sign);
		palette.building = Building.of(spec.building());
		palette.buildingStyle = spec.building();
		palette.style = spec.style();
		palette.roofMaterial = spec.roofMaterial();
		return palette;
	}

	/** The {@code kind} state of the station girders: the colour of the roof steel, from the building look or the platform style. */
	public String girderKind() {
		return switch (buildingStyle) {
			case GOTHIC_BRICK -> "blue";
			case ANTWERP, BEAUX_ARTS, AMSTERDAM, PARIS -> "green";
			case SANDSTONE, CLINKER, CONCRETE, TOKYO, ZURICH, MILAN -> "steel";
			case MODERN, BERLIN, HONG_KONG, BEIJING -> "white";
			case NONE -> switch (style) {
				case MODERN, DUTCH -> "white";
				case GERMAN_SBAHN, METRO -> "steel";
			};
		};
	}

	/** The spec asked for a glass roof. */
	public boolean roofMaterialGlass() {
		return roofMaterial == RoofMaterial.GLASS;
	}

	/** The station building's materials (the modern set when the spec has no building). */
	public Building building() {
		return building;
	}

	/** The block id for a role; never null. */
	public String blockId(Role role) {
		return switch (role) {
			case PLATFORM_SURFACE -> surface;
			case PLATFORM_EDGE -> edge;
			case RAMP_LOWER -> ata("platform_ramp_lower");
			case RAMP_UPPER -> ata("platform_ramp_upper");
			case RAIL_NODE -> MTR_RAIL_NODE;
			case COLUMN -> column;
			case COLUMN_CAP -> cap;
			case BEAM, RIB -> ata("station_girder");
			case ROOF -> roof;
			case ROOF_EDGE -> roofEdge;
			case ROOF_LIGHT -> roofLight;
			case LAMP -> lamp;
			case BENCH -> bench;
			case BIN -> ata("waste_bin");
			case NAME_SIGN -> sign;
			case FOUNDATION -> FOUNDATION;
			case TRACK_BED -> "minecraft:gravel";
			case FLOOR -> building.floor();
			case LIGHT_PANEL -> building.light();
			case WALL -> building.wall();
			case WINDOW -> building.window();
			case STAIR -> building.stair();
			case BRIDGE_PIER -> building.pier();
			case BRIDGE_DECK -> building.deck();
			case BUILDING_ROOF -> building.roof();
			case GATE -> ata("fare_gate");
			case BARRIER -> ata("glass_barrier");
			case RAILING -> ata("handrail");
			case BUFFER_STOP -> ata("structural_beam");
			case FIXTURE -> ata("help_point");
			case LIFT -> MTR_LIFT_TRACK;
			case ESCALATOR -> MTR_ESCALATOR_STEP;
		};
	}

	private static String surfaceOverride(SurfaceMaterial material, String styleDefault) {
		return switch (material) {
			case STYLE_DEFAULT -> styleDefault;
			case LIGHT_PAVING -> ata("platform_paving_light");
			case DARK_PAVING -> ata("platform_paving_dark");
			case CONCRETE -> ata("leidsche_rijn_concrete_platform_paver");
			case TERRAZZO -> ata("metro_terrazzo_floor");
			case GRANITE -> ata("metro_granite_floor");
		};
	}

	private static String roofOverride(RoofMaterial material, boolean hall, String styleDefault) {
		return switch (material) {
			case STYLE_DEFAULT -> styleDefault;
			case STEEL -> ata("canopy_flat");
			case GLASS -> ata(hall ? "hamburg_glass_roof" : "canopy_skylight");
			case TIMBER -> ata("leidsche_rijn_timber_soffit_canopy");
		};
	}

	private static String ata(String id) {
		return ATA + id;
	}
}
