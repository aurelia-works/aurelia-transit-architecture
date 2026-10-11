package com.aureliatransit.architecture.station;

/**
 * Chooses a block id for every {@link Role}, from the spec's style and its material overrides. Ids are plain strings so this
 * stays testable without the game; the in-game adapter resolves them in the block registry and refuses to build if one is
 * missing.
 */
public final class StylePalette {

	/** MTR 4's rail node block. */
	public static final String MTR_RAIL_NODE = "mtr:rail";

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
		return new StylePalette(surfaceOverride(spec.surface(), base.surface), base.edge, base.column, base.cap,
				roofOverride(spec.roofMaterial(), hall, base.roof), base.roofEdge, base.roofLight, base.lamp, base.bench, base.sign);
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
			case BEAM -> ata("structural_beam");
			case ROOF -> roof;
			case ROOF_EDGE -> roofEdge;
			case ROOF_LIGHT -> roofLight;
			case LAMP -> lamp;
			case BENCH -> bench;
			case BIN -> ata("waste_bin");
			case NAME_SIGN -> sign;
			case FOUNDATION -> FOUNDATION;
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
