package com.aureliatransit.architecture.station.world;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The "Station Generator Guide": a written book for operators, given once on the first join as an operator (a command tag
 * remembers it, like the transit card) and again with {@code /ata_guide}. Plain vanilla written book NBT, no new item.
 */
public final class StationGuide {

	public static final String TITLE = "Station Generator Guide";
	public static final String AUTHOR = "Aurelia Transit";
	static final String GIVEN_TAG = "ata_station_guide_given";

	/** A page: a bold heading line, then the body. Kept short enough for one book page (the tests check the fit). */
	public record Page(String heading, String body) {
	}

	public static final List<Page> PAGES = List.of(
			new Page("Station Generator", "Describe a railway station in plain words and ATA builds it from its own blocks.\n\n"
					+ "Operators only. This book covers the Station Wrench, the words it knows and the commands."),
			new Page("The Station Wrench", "Find it in the Passenger Equipment tab.\n\n"
					+ "Sneak + right-click the air to open it: type a description or pick a preset, then press Plan in front of me."),
			new Page("Wrench clicks", "Right-click a block: plan there, facing the way you look.\n"
					+ "Sneak + right-click a block: build.\n"
					+ "Left-click a block: cancel.\n"
					+ "Right-click the air: preview again."),
			new Page("Plan, check, build", "Planning places nothing. Chat lists what was understood, the size and the checks, and "
					+ "particles outline the station for 30 seconds.\n\nA plan that fails a check cannot be built."),
			new Page("Terrain", "A normal build never changes terrain. Grass, water or gaps on the site are listed in the plan.\n\n"
					+ "Build, replace terrain (on the screen) approves those changes."),
			new Page("Tracks", "1 to 4 tracks, island or side platforms, 16 to 160 long, 3 to 8 wide.\n\n"
					+ "Styles: modern, dutch, s-bahn, metro.\n"
					+ "Roof: canopy, hall or no roof."),
			new Page("Buildings", "Add \"station building\" for an entrance hall with gates, ticket office, machines and a kiosk.\n\n"
					+ "Add \"footbridge\" for stairs up to every platform."),
			new Page("Looks", "utrecht, amsterdam, st pancras, antwerp, frankfurt, hamburg, munich and grand central each pick a "
					+ "building look.\n\nA hall roof cannot go with a building yet."),
			new Page("Examples", "two track dutch station, island platform, 64 long, named Leidsche Rijn\n\n"
					+ "small single track s-bahn halt, no roof"),
			new Page("Rails and MTR", "Rail nodes go at both ends of every platform track, not joined.\n\n"
					+ "Join them with MTR's Platform Rail Connector, then extend the line."),
			new Page("Commands", "/ata_station plan <text>\n/ata_station confirm\n/ata_station cancel\n/ata_station undo\n"
					+ "/ata_station status\n\nUndo reverts your last build.\n/ata_guide gives this book again."));

	private StationGuide() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("ata_guide").requires(source -> source.hasPermissionLevel(StationActions.PERMISSION_LEVEL))
				.executes(context -> {
					context.getSource().getPlayerOrThrow().getInventory().offerOrDrop(book());
					context.getSource().sendFeedback(() -> Text.literal("Gave you the " + TITLE + "."), false);
					return 1;
				}));
	}

	/** On join: an operator who never had the book gets it once. Returns whether it was given. */
	public static boolean welcome(ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(StationActions.PERMISSION_LEVEL) || !player.addCommandTag(GIVEN_TAG)) {
			return false;
		}
		player.getInventory().offerOrDrop(book());
		player.sendMessage(Text.literal("[ATA] You have the " + TITLE + "; /ata_guide gives another copy.").formatted(Formatting.GOLD), false);
		return true;
	}

	public static ItemStack book() {
		final ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		final NbtCompound nbt = stack.getOrCreateNbt();
		nbt.putString("title", TITLE);
		nbt.putString("author", AUTHOR);
		nbt.putBoolean("resolved", true);
		final NbtList pages = new NbtList();
		for (final Page page : PAGES) {
			// siblings of an empty root, so the body does not inherit the heading's bold
			final Text text = Text.empty().append(Text.literal(page.heading()).formatted(Formatting.BOLD)).append(Text.literal("\n\n" + page.body()));
			pages.add(NbtString.of(Text.Serializer.toJson(text)));
		}
		nbt.put("pages", pages);
		return stack;
	}
}
