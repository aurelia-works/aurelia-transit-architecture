package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.client.interactive.EditorWidgets;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.function.Supplier;

/**
 * The station wrench screen. The simple page builds a station from buttons: a city look (and a second one to mix in), through
 * station or terminus, tracks, platforms, length, roof, building and name, then Place / Build it / Undo. The advanced page is
 * the typed description with the preset buttons and every generator action. Choices only change the screen; each action
 * button sends one packet (hand, action, description, choices) and closes the screen so the chat summary and the outline are
 * visible. Closing without an action saves changed choices. The server re-checks the operator level and bounds everything it
 * reads.
 */
public class StationWrenchScreen extends Screen {

	private static final int WIDTH = 320;
	private static final int HALF = (WIDTH - 4) / 2;
	private static final int COLUMNS = 4;
	private static final int ROW = 22;
	/** From the last choice row to the action buttons: the station's one-line summary and the next-step hint. */
	private static final int SUMMARY = 24;
	private static final String KEY = EditorWidgets.KEY + "station_wrench.";

	private final Hand hand;
	private final String initial;
	private final StationRecipe initialRecipe;
	private StationRecipe recipe;
	private boolean advanced;
	/** The advanced page's text; {@code custom} once the player typed or picked a preset, else it follows the choices. */
	private String typed;
	private boolean custom;
	private boolean sent;

	public StationWrenchScreen(Hand hand, String description, StationRecipe recipe) {
		super(Text.translatable(KEY + "title"));
		this.hand = hand;
		this.initialRecipe = recipe;
		this.recipe = recipe;
		this.typed = description;
		// a description typed on the advanced page (not the one the choices make) reopens there
		this.custom = !description.isEmpty() && !description.equals(recipe.description());
		this.advanced = custom;
		this.initial = description();
	}

	/** Opens the screen for the wrench in {@code hand} of the local player. */
	public static void open(Hand hand) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.player.getStackInHand(hand).getItem() instanceof StationWrenchItem) {
			final ItemStack stack = client.player.getStackInHand(hand);
			client.setScreen(new StationWrenchScreen(hand, StationWrenchItem.description(stack), StationWrenchItem.recipe(stack)));
		}
	}

	@Override
	protected void init() {
		if (advanced) {
			initAdvanced();
		} else {
			initSimple();
		}
	}

	// ---- simple page ----------------------------------------------------------------------------------------------

	private static int simpleHeight() {
		return 12 + 6 * ROW + SUMMARY + 26 + ROW + 20;
	}

	private void initSimple() {
		final int left = (width - WIDTH) / 2;
		int y = top(simpleHeight()) + 12;
		final int lookWidth = (WIDTH - (COLUMNS - 1) * 4) / COLUMNS;
		final StationRecipe.Look[] looks = StationRecipe.Look.values();
		for (int i = 0; i < looks.length; i++) {
			final StationRecipe.Look look = looks[i];
			final Text label = recipe.look() == look ? Text.literal("> " + look.label() + " <").formatted(Formatting.YELLOW) : Text.literal(look.label());
			addDrawableChild(ButtonWidget.builder(label, b -> change(recipe.withLook(look)))
					.dimensions(left + i % COLUMNS * (lookWidth + 4), y + i / COLUMNS * ROW, lookWidth, 20)
					.tooltip(Tooltip.of(Text.translatable(KEY + "look.tip"))).build());
		}
		y += 2 * ROW;
		final ButtonWidget mix = cycle(left, y, HALF, "mix",
				recipe.mix() == null ? Text.translatable(KEY + "none") : Text.literal(recipe.mix().label()), () -> recipe.withMix(nextMix(recipe.mix())));
		mix.setTooltip(Tooltip.of(Text.translatable(KEY + "mix.tip")));
		addDrawableChild(mix);
		addDrawableChild(cycle(left + HALF + 4, y, HALF, "type", Text.translatable(KEY + (recipe.terminus() ? "terminus" : "through")),
				() -> recipe.withTerminus(!recipe.terminus())));
		y += ROW;
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> change(recipe.withTracks(recipe.tracks() - 1))).dimensions(left, y, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "tracks", recipe.tracks()), b -> change(recipe.withTracks(
				recipe.tracks() % StationRecipe.MAX_TRACKS + 1))).dimensions(left + 22, y, HALF - 44, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> change(recipe.withTracks(recipe.tracks() + 1)))
				.dimensions(left + HALF - 20, y, 20, 20).build());
		addDrawableChild(cycle(left + HALF + 4, y, HALF, "platforms", Text.literal(recipe.platforms().label()),
				() -> recipe.withPlatforms(next(recipe.platforms()))));
		y += ROW;
		addDrawableChild(cycle(left, y, HALF, "length", Text.literal(Integer.toString(recipe.length())),
				() -> recipe.withLength(nextLength(recipe.length()))));
		final ButtonWidget roof = cycle(left + HALF + 4, y, HALF, "roof", Text.literal(recipe.roof().label()), () -> recipe.withRoof(next(recipe.roof())));
		roof.setTooltip(Tooltip.of(Text.translatable(KEY + "roof.tip")));
		addDrawableChild(roof);
		y += ROW;
		final Text buildingLabel = !recipe.building() ? ScreenTexts.NO
				: recipe.size() == StationRecipe.Size.AUTO || recipe.terminus() ? ScreenTexts.YES : Text.literal(recipe.size().label());
		final ButtonWidget building = cycle(left, y, HALF, "building", buildingLabel, this::nextBuilding);
		building.setTooltip(Tooltip.of(Text.translatable(KEY + "building.tip")));
		if (recipe.terminus()) {
			building.active = false;
			building.setTooltip(Tooltip.of(Text.translatable(KEY + "building.terminus")));
		}
		addDrawableChild(building);
		final TextFieldWidget name = new TextFieldWidget(textRenderer, left + HALF + 5, y + 1, HALF - 2, 18, Text.translatable(KEY + "name"));
		name.setMaxLength(32);
		name.setText(recipe.name());
		name.setPlaceholder(Text.translatable(KEY + "name").formatted(Formatting.DARK_GRAY));
		name.setChangedListener(text -> recipe = recipe.withName(text));
		addDrawableChild(name);
		y += ROW + SUMMARY;
		final ButtonWidget place = action(left, y, HALF, "place", StationWrenchItem.Action.PLAN);
		place.setTooltip(Tooltip.of(Text.translatable(KEY + "place.tip")));
		addDrawableChild(place);
		final ButtonWidget build = action(left + HALF + 4, y, HALF, "build_it", StationWrenchItem.Action.CONFIRM);
		build.setTooltip(Tooltip.of(Text.translatable(KEY + "build_it.tip")));
		addDrawableChild(build);
		y += 26;
		addDrawableChild(action(left, y, HALF, "undo", StationWrenchItem.Action.UNDO));
		addDrawableChild(action(left + HALF + 4, y, HALF, "cancel_plan", StationWrenchItem.Action.CANCEL));
		y += ROW;
		final ButtonWidget more = ButtonWidget.builder(Text.translatable(KEY + "advanced"), b -> {
			advanced = true;
			if (!custom) {
				typed = recipe.description();
			}
			clearAndInit();
		}).dimensions(left, y, HALF, 20).build();
		more.setTooltip(Tooltip.of(Text.translatable(KEY + "advanced.tip")));
		addDrawableChild(more);
		addDrawableChild(studioButton(left + HALF + 4, y, HALF));
	}

	/** A "Label: value" button that replaces the choices with {@code next} when pressed. */
	private ButtonWidget cycle(int x, int y, int w, String key, Text value, Supplier<StationRecipe> next) {
		final Text label = Text.translatable(KEY + key).append(": ").append(value.copy().formatted(Formatting.AQUA));
		return ButtonWidget.builder(label, b -> change(next.get())).dimensions(x, y, w, 20).build();
	}

	private void change(StationRecipe value) {
		recipe = value;
		clearAndInit();
	}

	/** None, then every look in order (the chosen look itself is skipped by the recipe), then none again. */
	private StationRecipe.Look nextMix(StationRecipe.Look mix) {
		final StationRecipe.Look[] all = StationRecipe.Look.values();
		int index = mix == null ? 0 : mix.ordinal() + 1;
		if (index < all.length && all[index] == recipe.look()) {
			index++;
		}
		return index < all.length ? all[index] : null;
	}

	/** Platform lengths the length button steps through, then back to the first. */
	private static final int[] LENGTHS = {32, 48, 64, 96, 128, 160, 200, 250};

	private static int nextLength(int length) {
		for (final int option : LENGTHS) {
			if (option > length) {
				return option;
			}
		}
		return LENGTHS[0];
	}

	/** No building, then a building sized by the station, then compact, city and grand, then no building again. */
	private StationRecipe nextBuilding() {
		if (!recipe.building()) {
			return recipe.withBuilding(true).withSize(StationRecipe.Size.AUTO);
		}
		if (recipe.size() == StationRecipe.Size.GRAND) {
			return recipe.withBuilding(false).withSize(StationRecipe.Size.AUTO);
		}
		return recipe.withSize(next(recipe.size()));
	}

	private static <E extends Enum<E>> E next(E value) {
		final E[] all = value.getDeclaringClass().getEnumConstants();
		return all[(value.ordinal() + 1) % all.length];
	}

	// ---- advanced page --------------------------------------------------------------------------------------------

	private static int advancedHeight() {
		return 46 + presetRows() * ROW + 6 + 24 + 24 + 28 + 20;
	}

	private void initAdvanced() {
		final int left = (width - WIDTH) / 2;
		int y = top(advancedHeight());
		final TextFieldWidget field = new TextFieldWidget(textRenderer, left, y, WIDTH, 18, Text.translatable(KEY + "description"));
		field.setMaxLength(StationDescriptionParser.MAX_INPUT);
		field.setText(typed);
		field.setPlaceholder(Text.translatable(KEY + "description").formatted(Formatting.DARK_GRAY));
		field.setChangedListener(text -> {
			typed = text;
			custom = true;
		});
		addDrawableChild(field);
		y += 46;
		final int presetWidth = (WIDTH - (COLUMNS - 1) * 4) / COLUMNS;
		for (int i = 0; i < StationPresets.ALL.size(); i++) {
			final StationPresets.Preset preset = StationPresets.ALL.get(i);
			addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "preset." + preset.key()), b -> {
				field.setText(preset.description());
				field.setCursorToStart();
			}).dimensions(left + i % COLUMNS * (presetWidth + 4), y + i / COLUMNS * ROW, presetWidth, 20)
					.tooltip(Tooltip.of(Text.literal(preset.description()))).build());
		}
		y += presetRows() * ROW + 6;
		addDrawableChild(action(left, y, HALF, "plan", StationWrenchItem.Action.PLAN));
		addDrawableChild(action(left + HALF + 4, y, HALF, "preview", StationWrenchItem.Action.PREVIEW));
		y += 24;
		addDrawableChild(action(left, y, HALF, "build", StationWrenchItem.Action.CONFIRM));
		final ButtonWidget replace = action(left + HALF + 4, y, HALF, "build_replace", StationWrenchItem.Action.CONFIRM_REPLACE_TERRAIN);
		replace.setTooltip(Tooltip.of(Text.translatable(KEY + "build_replace.tip")));
		addDrawableChild(replace);
		y += 24;
		addDrawableChild(action(left, y, HALF, "cancel", StationWrenchItem.Action.CANCEL));
		addDrawableChild(action(left + HALF + 4, y, HALF, "undo", StationWrenchItem.Action.UNDO));
		y += 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "simple"), b -> {
			advanced = false;
			clearAndInit();
		}).dimensions(left, y, HALF, 20).tooltip(Tooltip.of(Text.translatable(KEY + "simple.tip"))).build());
		addDrawableChild(studioButton(left + HALF + 4, y, HALF));
		setInitialFocus(field);
	}

	private static int presetRows() {
		return (StationPresets.ALL.size() + COLUMNS - 1) / COLUMNS;
	}

	/** Opens Station Studio in the browser (bigger description, 3D preview, checks); Escape closes this screen. */
	private ButtonWidget studioButton(int x, int y, int w) {
		return ButtonWidget.builder(Text.translatable(KEY + "studio"), b -> {
			close();
			StationStudioClient.open();
		}).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable(KEY + "studio.tip"))).build();
	}

	// ---- shared ---------------------------------------------------------------------------------------------------

	/** What the server plans: the typed text on the advanced page, the choices' description on the simple one. */
	private String description() {
		return advanced ? typed.strip() : recipe.description();
	}

	private ButtonWidget action(int x, int y, int buttonWidth, String key, StationWrenchItem.Action action) {
		return ButtonWidget.builder(Text.translatable(KEY + key), button -> send(action)).dimensions(x, y, buttonWidth, 20).build();
	}

	private void send(StationWrenchItem.Action action) {
		sent = true;
		ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, action, description(), recipe.encode()));
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		final int left = (width - WIDTH) / 2;
		if (advanced) {
			final int top = top(advancedHeight());
			context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 16, 0xFFFFFF);
			context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "example"), left, top + 22, 0x9AA0A6);
			context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "hint"), left, top + 32, 0x9AA0A6);
			return;
		}
		final int top = top(simpleHeight());
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 16, 0xFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "step_look"), left, top, 0xE0C060);
		final int summary = top + 12 + 6 * ROW + 1;
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(recipe.title()), width / 2, summary, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable(KEY + "step_place"), width / 2, summary + 11, 0x9AA0A6);
	}

	/** The first row's y; the whole page is centred vertically. */
	private int top(int total) {
		return Math.max(24, (height - total) / 2);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		if (!sent && (!description().equals(initial) || !recipe.equals(initialRecipe))) {
			sent = true;
			ClientPlayNetworking.send(ModPackets.STATION_WRENCH,
					ModPackets.writeStationWrench(hand, StationWrenchItem.Action.SAVE, description(), recipe.encode()));
		}
	}
}
