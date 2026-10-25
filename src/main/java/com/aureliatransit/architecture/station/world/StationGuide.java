package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.registry.ModItems;
import com.aureliatransit.architecture.station.RealStation;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.world.GuidePages.Chapter;
import com.aureliatransit.architecture.station.world.GuidePages.Line;
import com.aureliatransit.architecture.station.world.GuidePages.Page;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The Station Codex: the station generator's guide for operators, given once on the first join as an operator (a command tag
 * remembers it, like the transit card) and again with {@code /ata_guide}. The item ({@link StationCodexItem}) opens the client's
 * Codex screen, which draws the pages below.
 *
 * <p>It opens on a contents page that turns to each chapter; commands in it run on a click, or go to the chat box when they
 * build or undo, and the real stations and presets are listed from the code ({@link GuidePages} lays the pages out).
 */
public final class StationGuide {

	public static final String TITLE = "Station Codex";
	static final String GIVEN_TAG = "ata_station_guide_given";
	/** The wrench screen's preset labels, which the preset list shows translated. */
	static final String PRESET_KEY = "screen.aurelia_transit_architecture.station_wrench.preset.";

	static final String PLAN = "/ata_station plan ";
	/** Presets on one page of the preset list, under its heading and note. */
	static final int PRESETS_PER_PAGE = 10;
	private static final String ATA = "aurelia_transit_architecture:";

	public static final List<Chapter> CHAPTERS = List.of(
			new Chapter("Getting started", ATA + "station_wrench", List.of(
					Page.of("Station Generator", "Describe a railway station in plain words and ATA builds it from its own blocks.\n\n"
							+ "Operators only. This Codex covers the Station Wrench, Station Studio, the words and the commands."),
					Page.of("The Station Wrench", "Find it in the Passenger Equipment tab.\n\n"
							+ "Right-click the air to open it. Pick a city, the tracks, length and roof, then press Place in front of me and Build it!"),
					Page.of("Wrench clicks", "Right-click the air: open the menu.\n"
							+ "Right-click a block: place there, facing the way you look.\n"
							+ "Sneak + right-click a block: build.\n"
							+ "Left-click: cancel.\n"
							+ "Sneak + right-click the air: outline again."),
					Page.of("Plan, check, build", "Placing builds nothing yet. Chat shows the station with Build it, Cancel and Details buttons, and "
							+ "particles outline it for 30 seconds.\n\nA plan that fails a check cannot be built."),
					Page.of("Terrain", "A normal build never changes terrain. Grass, water or gaps on the site are listed in the plan.\n\n"
							+ "Clear and build (in chat) approves those changes."),
					Page.of("Station Studio", Line.text("A web page on this computer with the wrench's controls and a 3D preview. Its buttons "
							+ "plan, build, cancel and undo in the world as you.\n\nOpen it from the wrench screen or with:"),
							Line.run("/ata_studio", "Opens Station Studio in your browser")))),
			new Chapter("Describing a station", ATA + "platform_edge", List.of(
					Page.of("Tracks", "1 to 10 tracks, island or side platforms, 16 to 300 long, 3 to 20 wide.\n\n"
							+ "Styles: modern, dutch, s-bahn, metro."),
					Page.showcase("Platforms", "Every platform gets an edge with its warning line, lamps and canopies.",
							ATA + "platform_edge", ATA + "platform_edge_warning", ATA + "canopy_flat"),
					Page.of("Roofs", "canopy (flat, butterfly or gable), glass canopy, no roof, or a train shed (flat, arched, pointed or "
							+ "multi-arch)."),
					Page.of("Buildings", "Add \"station building\" for an entrance hall with gates, ticket office, machines and a kiosk.\n\n"
							+ "Add \"footbridge\" for stairs up to every platform, with lifts where a platform has room."),
					Page.showcase("Underground", "Add \"underground\", \"subway\", \"tube\" or \"tunnel\" for a tiled box under the street. "
							+ "Pick the tiles: \"orange tiles\".", ATA + "metro_tile_orange", ATA + "metro_column_tiled", ATA + "metro_tactile_edge"),
					Page.of("Swapping blocks", "Don't like a block? Say so:\n\nuse stone bricks instead of sandstone\n"
							+ "replace sandstone with stone bricks\nsandstone -> stone bricks\n\nSlabs, stairs and walls follow."),
					Page.of("Examples", Line.text("Click one to put it in chat.\n"),
							example("two track dutch station, island platform, 64 long, named Leidsche Rijn"), Line.text(""),
							example("small single track s-bahn halt, no roof")))),
			new Chapter("Looks and real stations", ATA + "amsterdam_clock_face_panel", looks()),
			new Chapter("Termini and hubs", ATA + "buffer_stop", List.of(
					Page.of("Terminus", "Add \"terminus\" and the tracks end at buffer stops in front of a concourse building.\n\n"
							+ "Every platform is reached step-free through the gates."),
					Page.showcase("Buffer stops", "Modern and heritage buffer stops end the tracks; gates stand between the platforms and the hall.",
							ATA + "buffer_stop", ATA + "heritage_buffer_stop", ATA + "fare_gate"),
					Page.of("Hub", "Add \"hub\" for a bit of both: the middle tracks end at the station hall and the outer ones "
							+ "run on past it.\n\nIsland platforms, 4, 6, 8 or 10 tracks. With 8 or 10, two run through each side and a "
							+ "footbridge takes you out to them."))),
			new Chapter("Finding your way", ATA + "platform_pids", List.of(
					Page.of("Finding your train", "Each platform has its own colour at the gates: follow the stripe on the floor.\n\n"
							+ "A big number hangs over the gates and under the footbridge, so you always know which platform you are on."),
					Page.showcase("On the platform", "Screens show the next trains, numbers say where you are, help points say the station's name.",
							ATA + "platform_pids", ATA + "platform_number_sign", ATA + "help_point"),
					Page.of("Moving screens", "Sneak while placing a screen, sign or clock.\n\n"
							+ "Screens and clocks turn to face you. Click near an edge to sit it flush there, right at the edge to move it "
							+ "half a block."))),
			new Chapter("Rails and MTR", "minecraft:rail", List.of(
					Page.of("Rails and MTR", "Rail nodes go at both ends of every platform track. The build joins them as MTR "
							+ "platforms, numbered like the signs.\n\nExtend the line and add routes on the dashboard."))),
			new Chapter("Commands", "minecraft:command_block", List.of(
					Page.of("Commands", Line.text("Click to run, or to put in chat:"),
							Line.run("/ata_station help", "Lists the commands in chat"),
							Line.chat(PLAN + "<words>", false, PLAN, "Plans a station in front of you"),
							Line.run("/ata_station preview", "Outlines the plan again"),
							Line.run("/ata_station details", "The plan in detail"),
							Line.run("/ata_station status", "What you have planned or building"),
							Line.chat("/ata_station confirm", "Builds the plan"),
							Line.chat("/ata_station confirm replace_terrain", "Builds it and approves the terrain changes")),
					Page.of("More commands",
							Line.chat("/ata_station cancel", "Drops the plan or stops the build"),
							Line.chat("/ata_station undo", "Reverts your last build, until a restart"),
							Line.chat("/ata_station rebuild", "Builds the plan where your last station stands, changing only what differs"),
							Line.chat("/ata_station rebuild replace_terrain", "Rebuilds and approves the terrain changes"),
							Line.run("/ata_station verify", "Reads the world back against the plan"),
							Line.chat("/ata_station verify paste", "Checks a schematic pasted at your feet"),
							Line.run("/ata_guide", "Gives you the Codex again"),
							Line.run("/ata_studio", "Opens Station Studio in your browser")))));

	/** Every page of the Codex, the contents first. */
	public static final List<Page> PAGES = GuidePages.book("Click a chapter to turn to it.", CHAPTERS);

	private StationGuide() {
	}

	private static Line example(String words) {
		return Line.chat(words, false, PLAN + words, "Puts " + PLAN + words + " in chat");
	}

	/** Looks, the real stations ({@link RealStation}) and every wrench preset ({@link StationPresets#ALL}), so none is left out. */
	private static List<Page> looks() {
		final List<Page> pages = new ArrayList<>();
		pages.add(Page.of("Looks", "A city picks its look and roof: amsterdam, st pancras, antwerp, frankfurt, hamburg, munich, "
				+ "grand central, tokyo, zurich, paris, milan, berlin, hong kong, beijing, bangkok, mumbai, kuala lumpur."));
		pages.add(Page.of("British looks", "victorian, art deco or modernist, after Teddington, Surbiton and Coventry: tarmac platforms "
				+ "with white-nosed coping."));
		pages.add(Page.of("Mixing looks", "Join cities with \"and\" or \"combined\":\n\namerican station, utrecht and hamburg combined\n\n"
				+ "The first gives the building, then the platforms, then the train shed."));
		final List<Line> real = new ArrayList<>();
		real.add(Line.text("These words build the real station from published facts: its hall, platforms and operator. Words "
				+ "against it are dropped, with a warning.\n"));
		for (final RealStation station : RealStation.values()) {
			if (station != RealStation.NONE) {
				real.add(Line.chat(station.phrase(), false, PLAN + station.phrase(), "Puts " + PLAN + station.phrase() + " in chat"));
			}
		}
		pages.add(new Page("Real stations", List.copyOf(real)));
		final List<Line> presets = new ArrayList<>();
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			presets.add(Line.chat(PRESET_KEY + preset.key(), true, PLAN + preset.description(), preset.description()));
		}
		pages.addAll(GuidePages.list("Presets", "Click one to plan it.", presets, PRESETS_PER_PAGE));
		return List.copyOf(pages);
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("ata_guide").requires(source -> source.hasPermissionLevel(StationActions.PERMISSION_LEVEL))
				.executes(context -> {
					context.getSource().getPlayerOrThrow().getInventory().offerOrDrop(codex());
					context.getSource().sendFeedback(() -> Text.literal("Gave you the " + TITLE + "."), false);
					return 1;
				}));
	}

	/** On join: an operator who never had the Codex gets it once. Returns whether it was given. */
	public static boolean welcome(ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(StationActions.PERMISSION_LEVEL) || !player.addCommandTag(GIVEN_TAG)) {
			return false;
		}
		player.getInventory().offerOrDrop(codex());
		player.sendMessage(Text.literal("[ATA] You have the " + TITLE + "; /ata_guide gives another copy.").formatted(Formatting.GOLD), false);
		return true;
	}

	public static ItemStack codex() {
		return new ItemStack(ModItems.STATION_CODEX);
	}
}
