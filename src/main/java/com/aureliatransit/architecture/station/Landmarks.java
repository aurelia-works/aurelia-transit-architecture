package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.Landmark.Cap;
import com.aureliatransit.architecture.station.Landmark.Facade;
import com.aureliatransit.architecture.station.Landmark.Roof;
import com.aureliatransit.architecture.station.Landmark.Shed;
import com.aureliatransit.architecture.station.Landmark.ShedProfile;
import com.aureliatransit.architecture.station.Landmark.Skin;
import com.aureliatransit.architecture.station.Landmark.Window;

import java.util.List;

/**
 * The sheets of the real stations the presets copy (Jah, 10-21), each from photos and published sizes (the reference
 * briefs list the sources). A station gets its sheet when it is named after it, as every preset is. One block is about
 * two thirds of a metre; heights are squeezed where the real building would pass the station's height limit.
 */
public final class Landmarks {

	static final String CLOCK_FACE = "aurelia_transit_architecture:amsterdam_clock_face_panel";

	// ---- materials ----

	/** Victorian red brick with pale stone dressings and a grey slate roof (St Pancras). */
	static final Skin GOTHIC_RED_BRICK = new Skin("minecraft:bricks", "minecraft:smooth_sandstone", "minecraft:cut_sandstone",
			"minecraft:gray_stained_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs",
			"minecraft:smooth_sandstone_stairs", "minecraft:smooth_sandstone");

	/** Antwerp's grey stone with pale bands, dark slate roofs and blue slate domes. */
	static final Skin ANTWERP_STONE = new Skin("minecraft:polished_andesite", "minecraft:polished_diorite", "minecraft:stone_bricks",
			"minecraft:light_gray_stained_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs",
			"minecraft:polished_diorite_stairs", null);

	/** Beaux-Arts limestone over a grey granite base, under green copper (Grand Central). */
	static final Skin BEAUX_ARTS_STONE = new Skin("aurelia_transit_architecture:roma_travertine", "minecraft:smooth_sandstone",
			"minecraft:polished_andesite", "minecraft:gray_stained_glass_pane", "minecraft:oxidized_cut_copper",
			"minecraft:oxidized_cut_copper_stairs", "minecraft:smooth_sandstone_stairs", null);

	/** Frankfurt's ochre sandstone, rusticated at the base, under blue-grey slate, with brown glass. */
	static final Skin FRANKFURT_SANDSTONE = new Skin("aurelia_transit_architecture:frankfurt_sandstone_ashlar", "minecraft:smooth_sandstone",
			"aurelia_transit_architecture:frankfurt_sandstone_rusticated", "minecraft:brown_stained_glass_pane", "minecraft:deepslate_tiles",
			"minecraft:deepslate_tile_stairs", "minecraft:smooth_sandstone_stairs", null);

	/** Pale grey-beige sandstone over grey stone, under dark slate (Zurich). */
	static final Skin ZURICH_SANDSTONE = new Skin("aurelia_transit_architecture:roma_travertine", "minecraft:smooth_stone",
			"minecraft:stone_bricks", "minecraft:gray_stained_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs",
			"minecraft:stone_brick_stairs", null);

	/** Cream Paris limestone under blue-grey slate mansards. */
	static final Skin PARIS_LIMESTONE = new Skin("aurelia_transit_architecture:roma_travertine", "minecraft:smooth_quartz",
			"minecraft:smooth_stone", "minecraft:gray_stained_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs",
			"minecraft:smooth_quartz_stairs", null);

	/** Milan's pale Botticino stone, a grey stone base and flat stone roofs. */
	static final Skin MILAN_STONE = new Skin("minecraft:calcite", "minecraft:polished_diorite", "minecraft:smooth_stone",
			"minecraft:gray_stained_glass_pane", "minecraft:smooth_stone", "minecraft:smooth_stone_slab", "minecraft:polished_diorite_stairs",
			null);

	/** Mumbai's honey sandstone with pale limestone dressings, blue-grey basalt and red sandstone bands, under dark slate. */
	static final Skin MUMBAI_SANDSTONE = new Skin("minecraft:smooth_sandstone", "minecraft:smooth_quartz", "minecraft:polished_basalt",
			"minecraft:gray_stained_glass_pane", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs", "minecraft:sandstone_stairs",
			"minecraft:smooth_red_sandstone");

	/** Bangkok's cream stucco with white trim, gold bands and red-brown tiles, and blue-green glass. */
	static final Skin BANGKOK_STUCCO = new Skin("minecraft:smooth_sandstone", "minecraft:smooth_quartz", "minecraft:smooth_sandstone",
			"minecraft:cyan_stained_glass_pane", "minecraft:bricks", "minecraft:brick_stairs", "minecraft:smooth_quartz_stairs",
			"minecraft:yellow_terracotta");

	/** A great arched window's look: sky-reflecting glass in dark iron glazing bars, so it reads against pale stone. */
	static Skin glazing(Skin s) {
		return s.withGlass("minecraft:light_blue_stained_glass_pane").withTrim("minecraft:polished_deepslate");
	}

	private Landmarks() {
	}

	/**
	 * London St Pancras (George Gilbert Scott's Midland Grand Hotel, 1868-76, and W. H. Barlow's train shed, 1868): the hotel
	 * across the head of the tracks in red Gretton brick with Ancaster stone bands, pointed Gothic windows in five storeys,
	 * steep slate roofs with dormers, a clock tower with a spire at the east end (on the right from Euston Road) and a lower
	 * spired tower at the west end, a gabled centre bay over pointed arches; behind it the single pointed span of the shed
	 * (74.8 m wide, 30.5 m high, springing from the platforms), its ribs pale sky blue, its roof slate below a glazed band.
	 */
	static final Landmark ST_PANCRAS = Landmark.head("st_pancras", "St Pancras", "St Pancras International", "London St Pancras")
			.shed(new Shed(ShedProfile.POINTED, 1, 0.42, StationGeometry.CANOPY_Y, "aurelia_transit_architecture:glass_wall",
					"minecraft:deepslate_tiles", 0.35, 1.0, "blue", "minecraft:bricks"))
			.skin(GOTHIC_RED_BRICK)
			.facade(new Facade(Window.POINTED, 5, 3, 6, 2, 3, 2, 0, true))
			// the clock tower and the west tower, then the gabled centre and its arches
			.tower(31, 2, 4, 1, 64, Cap.SPIRE, 26, true)
			.tower(-33, 3, 3, 1, 50, Cap.SPIRE, 18, false)
			.gable(0, 0, 7, 35, 10, false)
			.clock(0, 40, 0, 2)
			.arch(-8, 0, 2, 1, 6, true)
			.arch(8, 0, 2, 1, 6, true)
			// the hotel: five storeys of brick under a steep slate roof with dormers
			.volume(-36, 36, 0, 15, 34, Roof.GABLE_X, 2, true, true)
			.chimneys(-25, 25, 5, 46, 10)
			.chimneys(-30, 30, 10, 46, 12)
			// the booking hall behind it, to the shed's screen
			.facade(new Facade(Window.POINTED, 5, 3, 6, 2, 3, 2, 0, false))
			.hall(-36, 36, 15, 60, 12)
			.build();

	/**
	 * Antwerpen-Centraal (Louis Delacenserie, 1895-1905): a grey stone head building with a great dome (about 60 m) over the
	 * hall, a tall glazed bay under it, an arcade of round arches along the street, two corner towers with blue domes and
	 * lanterns and mansard-roofed wings; behind it Clement van Bogaert's iron and glass shed, 185 m long and 44 m high, its
	 * ribs springing from the platforms.
	 */
	static final Landmark ANTWERP = Landmark.head("antwerp", "Antwerpen Centraal", "Antwerp Central", "Antwerpen-Centraal")
			.shed(new Shed(ShedProfile.POINTED, 1, 0.5, StationGeometry.CANOPY_Y, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.15, 1.0, "steel", "minecraft:stone_bricks"))
			.skin(ANTWERP_STONE.withRoof("minecraft:light_blue_terracotta", "minecraft:deepslate_tile_stairs"))
			.facade(new Facade(Window.ROUND, 4, 2, 6, 2, 3, 6, 0, true))
			// the corner towers with their blue slate domes, between them the glazed bay over an arcade and the clock
			.tower(-17, 3, 4, 1, 40, Cap.DOME, 0, false)
			.tower(17, 3, 4, 1, 40, Cap.DOME, 0, false)
			.skin(ANTWERP_STONE.withGlass("minecraft:light_blue_stained_glass_pane").withTrim("minecraft:polished_deepslate"))
			.arch(0, 0, 9, 9, 25, false)
			.skin(ANTWERP_STONE.withGlass("minecraft:gray_stained_glass_pane"))
			.arcade(-10, 10, 0, 5, 2, 1, 6)
			.clock(0, 30, 0, 1)
			.volume(-13, 13, 0, 22, 32, Roof.FLAT, 1, true, false)
			// the great dome over the hall, dark slate with a gilded lantern
			.skin(ANTWERP_STONE.withRoof("minecraft:cyan_terracotta", "minecraft:deepslate_tile_stairs").withTrim("minecraft:gold_block"))
			.dome(0, 11, 11, 33, 3, false, true)
			// the mansard wings and the hall behind them
			.skin(ANTWERP_STONE.withGlass("minecraft:gray_stained_glass_pane"))
			.volume(-35, -22, 0, 16, 26, Roof.MANSARD, 3, true, true)
			.volume(22, 35, 0, 16, 26, Roof.MANSARD, 3, true, true)
			.hall(-35, 35, 17, 60, 14)
			.build();

	/**
	 * Grand Central Terminal, New York (Reed and Stem, Warren and Wetmore, 1913): the 42nd Street front of pale limestone on a
	 * granite base, three great arched windows between paired columns, an attic with the clock and the Glory of Commerce
	 * statues, a flat roof of green copper; inside, the Main Concourse under its teal sky with gold stars. Its tracks run
	 * under the city; here they stay at street level under the station's hall.
	 */
	static final Landmark GRAND_CENTRAL = Landmark.head("grand_central", "Grand Central", "Grand Central Terminal")
			.skin(BEAUX_ARTS_STONE)
			// the plain stone front: a wide bay leaves it bare between the windows and columns
			.facade(new Facade(Window.NONE, 64, 1, 10, 3, 5, 5, 0, false))
			.arch(-16, 0, 6, 6, 21, false)
			.arch(0, 0, 6, 6, 21, false)
			.arch(16, 0, 6, 6, 21, false)
			.colonnade(-25, -24, -1, 28, 1, "minecraft:smooth_quartz")
			.colonnade(-9, -8, -1, 28, 1, "minecraft:smooth_quartz")
			.colonnade(8, 9, -1, 28, 1, "minecraft:smooth_quartz")
			.colonnade(24, 25, -1, 28, 1, "minecraft:smooth_quartz")
			// the clock and its statue group over the cornice
			.skin(BEAUX_ARTS_STONE.withTrim("minecraft:gold_block"))
			.clock(0, 35, -1, 3)
			.skin(BEAUX_ARTS_STONE)
			.gable(0, 0, 9, 31, 9, false)
			.ceiling(-29, 29, 1, 59, 29, "minecraft:cyan_terracotta", "minecraft:ochre_froglight", 5)
			.volume(-30, 30, 0, 60, 30, Roof.FLAT, 1, true, false)
			.build();

	/**
	 * Frankfurt (Main) Hauptbahnhof (Hermann Eggert, 1888): a Neo-Renaissance front of ochre sandstone, its middle a giant
	 * arched window under the Atlas group and the clock, flanked by lower wings and two end pavilions with domed caps; behind
	 * it three barrel-vaulted halls side by side, grey-green iron with glazed tops.
	 */
	static final Landmark FRANKFURT = Landmark.head("frankfurt", "Frankfurt Hbf", "Frankfurt Hauptbahnhof", "Frankfurt (Main) Hbf")
			.shed(new Shed(ShedProfile.ROUND, 3, 0.4, 0, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.3, 1.0, "green", "aurelia_transit_architecture:frankfurt_sandstone_ashlar"))
			.skin(FRANKFURT_SANDSTONE)
			.facade(new Facade(Window.ROUND, 5, 2, 6, 2, 3, 4, 0, true))
			// the end pavilions with domed caps
			.tower(-38, 6, 6, 1, 30, Cap.DOME, 0, false)
			.tower(38, 6, 6, 1, 30, Cap.DOME, 0, false)
			// the giant arched window, the clock and the Atlas group over it
			.clock(0, 35, 0, 2)
			.skin(glazing(FRANKFURT_SANDSTONE))
			.arch(0, 0, 11, 5, 21, false)
			.skin(FRANKFURT_SANDSTONE)
			.arch(-6, 0, 2, 1, 4, false)
			.arch(6, 0, 2, 1, 4, false)
			.gable(0, 0, 6, 33, 8, false)
			.volume(-16, 16, 0, 16, 32, Roof.FLAT, 1, true, false)
			// the wings between, under slate with dormers, and the hall behind
			.volume(-31, -17, 0, 14, 24, Roof.GABLE_X, 1, true, true)
			.volume(17, 31, 0, 14, 24, Roof.GABLE_X, 1, true, true)
			.hall(-44, 44, 15, 60, 12)
			.build();

	/**
	 * Zurich Hauptbahnhof (Jakob Friedrich Wanner, 1871): a Neo-Renaissance front of pale sandstone round a triumphal-arch
	 * portal with a clock, long wings with half-round windows under slate mansards, the tall Haupthalle behind it with its
	 * arched windows; the platforms under barrel halls side by side.
	 */
	static final Landmark ZURICH = Landmark.head("zurich", "Zurich HB", "Zurich Hauptbahnhof", "Zuerich HB")
			.shed(new Shed(ShedProfile.ROUND, 3, 0.3, 0, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.25, 1.0, "steel", "minecraft:stone_bricks"))
			.skin(ZURICH_SANDSTONE)
			.facade(new Facade(Window.ROUND, 4, 2, 6, 2, 3, 3, 0, true))
			// the triumphal arch: a great middle arch between two smaller ones, the clock and an attic over them
			.clock(0, 21, 0, 2)
			.arch(0, 0, 4, 1, 13, false)
			.arch(-7, 0, 2, 1, 8, false)
			.arch(7, 0, 2, 1, 8, false)
			.colonnade(-10, 10, -1, 18, 5, "minecraft:smooth_stone")
			.volume(-10, 10, 0, 14, 26, Roof.FLAT, 1, true, false)
			.upper(-6, 6, 0, 4, 27, 30, Roof.FLAT, 1)
			// the wings, the Haupthalle behind them with its tall arched windows
			.volume(-35, -11, 2, 16, 20, Roof.MANSARD, 3, true, true)
			.volume(11, 35, 2, 16, 20, Roof.MANSARD, 3, true, true)
			.facade(new Facade(Window.ROUND, 5, 2, 24, 4, 14, 3, 0, true))
			.hall(-28, 28, 17, 60, 26, Roof.FLAT, 1)
			.build();

	/**
	 * Paris Gare du Nord (Jacques Ignace Hittorff, 1861-65): a cream limestone front, its middle pavilion one giant arched
	 * window under a pediment, statues of the cities served along the cornice, corner pavilions and wings under slate
	 * mansards; behind it the three-nave shed on tall dark green cast-iron columns, its roof timber brown with a glazed nave.
	 */
	static final Landmark PARIS = Landmark.head("paris", "Paris Nord", "Gare du Nord", "Paris Gare du Nord")
			.shed(new Shed(ShedProfile.GABLE, 1, 0.3, 0, "aurelia_transit_architecture:glass_wall", "minecraft:spruce_planks", 0.45, 1.0,
					"green", "aurelia_transit_architecture:roma_travertine"))
			.skin(PARIS_LIMESTONE)
			.facade(new Facade(Window.RECT, 4, 2, 6, 2, 3, 2, 0, true))
			// the middle pavilion: the giant window under its pediment
			.skin(glazing(PARIS_LIMESTONE))
			.arch(0, 0, 9, 4, 18, false)
			.skin(PARIS_LIMESTONE)
			.gable(0, 0, 13, 31, 7, false)
			.volume(-13, 13, 0, 14, 30, Roof.FLAT, 1, true, false)
			// the corner pavilions and the wings under mansards, the hall behind
			.volume(-35, -26, 0, 14, 30, Roof.MANSARD, 3, true, true)
			.volume(26, 35, 0, 14, 30, Roof.MANSARD, 3, true, true)
			.volume(-25, -14, 0, 12, 24, Roof.MANSARD, 3, true, true)
			.volume(14, 25, 0, 12, 24, Roof.MANSARD, 3, true, true)
			.hall(-35, 35, 15, 60, 12)
			.build();

	/**
	 * Milano Centrale (Ulisse Stacchini, 1931): a monumental pale stone front 200 m wide, its tall middle block cut by three
	 * great portals and a vast arch above them, sculpture at its corners, long wings with arcades; behind it Alberto Fava's
	 * five steel and glass barrel vaults over the platforms.
	 */
	static final Landmark MILAN = Landmark.head("milan", "Milano Centrale", "Milan Central", "Milano Centrale station")
			.shed(new Shed(ShedProfile.ROUND, 5, 0.45, 0, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.3, 1.0, "steel", "minecraft:calcite"))
			.skin(MILAN_STONE)
			.facade(new Facade(Window.RECT, 5, 2, 7, 2, 4, 4, 0, true))
			// the middle block: three portals, the great arch over them, the corner sculpture
			.skin(MILAN_STONE.withGlass("minecraft:gray_stained_glass_pane").withTrim("minecraft:polished_deepslate"))
			.arch(-14, 0, 5, 1, 15, false)
			.arch(0, 0, 5, 1, 15, false)
			.arch(14, 0, 5, 1, 15, false)
			.skin(glazing(MILAN_STONE))
			.arch(0, 0, 10, 23, 32, false)
			.skin(MILAN_STONE)
			.colonnade(-24, 24, -1, 22, 7, "minecraft:polished_diorite")
			.pinnacles(-24, -20, 0, 46, 4, "minecraft:polished_diorite", "minecraft:chiseled_quartz_block")
			.pinnacles(20, 24, 0, 46, 4, "minecraft:polished_diorite", "minecraft:chiseled_quartz_block")
			.volume(-24, 24, 0, 22, 45, Roof.FLAT, 1, true, false)
			// the wings with their ground arcades
			.arcade(-42, -27, 0, 5, 2, 1, 6)
			.arcade(27, 42, 0, 5, 2, 1, 6)
			.volume(-44, -25, 0, 18, 30, Roof.FLAT, 1, true, false)
			.volume(25, 44, 0, 18, 30, Roof.FLAT, 1, true, false)
			.hall(-44, 44, 23, 60, 14)
			.build();

	/**
	 * Chhatrapati Shivaji Maharaj Terminus, Mumbai (F. W. Stevens, 1878-87): Victorian Gothic in honey sandstone with pale
	 * limestone and blue-grey basalt, pointed windows over a round-arched arcade, the great ribbed octagonal dome in the middle
	 * with Progress on top, a clock gable on the front, turrets with domed caps at the corners; behind it a long ridge and
	 * furrow steel shed.
	 */
	static final Landmark MUMBAI = Landmark.head("mumbai", "Mumbai CSMT", "Chhatrapati Shivaji Maharaj Terminus", "CSMT", "Victoria Terminus")
			.shed(new Shed(ShedProfile.GABLE, 4, 0.25, 0, "aurelia_transit_architecture:glass_wall", "minecraft:deepslate_tiles", 0.55, 1.0,
					"steel", "minecraft:smooth_sandstone"))
			.skin(MUMBAI_SANDSTONE)
			.facade(new Facade(Window.POINTED, 4, 2, 6, 2, 3, 2, 5, true))
			// the corner turrets and the great dome
			.tower(-41, 2, 3, 1, 34, Cap.DOME, 0, false)
			.tower(41, 2, 3, 1, 34, Cap.DOME, 0, false)
			.tower(-17, 2, 2, 1, 32, Cap.DOME, 0, false)
			.tower(17, 2, 2, 1, 32, Cap.DOME, 0, false)
			.octTower(0, 12, 8, 1, 40, Cap.DOME, 0, false)
			// the clock gable and the arcade along the street
			.clock(0, 34, 0, 2)
			.gable(0, 0, 9, 30, 9, false)
			.arcade(-35, 35, 0, 5, 2, 1, 6)
			.volume(-14, 14, 0, 22, 29, Roof.FLAT, 1, true, false)
			.volume(-44, -15, 0, 14, 26, Roof.GABLE_X, 2, true, true)
			.volume(15, 44, 0, 14, 26, Roof.GABLE_X, 2, true, true)
			.hall(-44, 44, 23, 60, 12)
			.build();

	/**
	 * Bangkok (Hua Lamphong, Mario Tamagno, 1916): an Italian Neo-Renaissance front of cream stucco round the great half-round
	 * glazed end of the shed with a clock at its top, two end pavilions with pediments and long two-storey arcaded wings;
	 * behind it the barrel-vaulted shed of dark lattice steel with a skylight along its ridge.
	 */
	static final Landmark BANGKOK = Landmark.head("bangkok", "Krung Thep", "Bangkok", "Hua Lamphong", "Bangkok Hua Lamphong")
			.shed(new Shed(ShedProfile.ROUND, 1, 0.3, StationGeometry.CANOPY_Y, "aurelia_transit_architecture:glass_wall",
					"aurelia_transit_architecture:metro_steel_cladding", 0.75, 1.0, "steel", "minecraft:smooth_sandstone"))
			.skin(BANGKOK_STUCCO)
			.facade(new Facade(Window.ROUND, 4, 2, 7, 1, 5, 1, 0, true))
			// the shed's glazed end in its cream frame, the clock at its top
			.clock(0, 22, 0, 2)
			.arch(0, 0, 16, 2, 9, false)
			.volume(-20, 20, 0, 10, 27, Roof.FLAT, 1, true, false)
			// the end pavilions with pediments, the arcaded wings
			.gable(-39, 0, 5, 18, 4, false)
			.gable(39, 0, 5, 18, 4, false)
			.volume(-44, -34, 0, 14, 17, Roof.FLAT, 1, true, false)
			.volume(34, 44, 0, 14, 17, Roof.FLAT, 1, true, false)
			.volume(-33, -21, 0, 12, 13, Roof.GABLE_X, 1, true, false)
			.volume(21, 33, 0, 12, 13, Roof.GABLE_X, 1, true, false)
			.hall(-44, 44, 13, 60, 12)
			.build();

	static final List<Landmark> ALL = List.of(ST_PANCRAS, ANTWERP, GRAND_CENTRAL, FRANKFURT, ZURICH, PARIS, MILAN, MUMBAI, BANGKOK);

	/** The sheet of the real station {@code spec} is named after, or null. */
	public static Landmark of(StationSpec spec) {
		final String name = spec.name();
		for (final Landmark landmark : ALL) {
			if (landmark.named(name)) {
				return landmark;
			}
		}
		return null;
	}
}
