package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.client.interactive.EditorWidgets;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/**
 * The station wrench screen: edit the remembered description, fill it from a preset, and run a generator action. Preset buttons
 * only change the text field; every action button sends one packet
 * (hand, action, description) and closes the screen so the chat summary and the preview are visible; closing without a button
 * only saves a changed description. The server re-checks the operator level and bounds everything it reads.
 */
public class StationWrenchScreen extends Screen {

	private static final int WIDTH = 300;
	private static final int PRESET_COLUMNS = 4;
	/** From the top of the description field to the first action row, before the preset rows. */
	private static final int TEXT_HEIGHT = 46;
	private static final String KEY = EditorWidgets.KEY + "station_wrench.";

	private final Hand hand;
	private final String initial;
	private TextFieldWidget descriptionField;
	private boolean sent;

	public StationWrenchScreen(Hand hand, String description) {
		super(Text.translatable(KEY + "title"));
		this.hand = hand;
		this.initial = description;
	}

	/** Opens the screen for the wrench in {@code hand} of the local player. */
	public static void open(Hand hand) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.player.getStackInHand(hand).getItem() instanceof StationWrenchItem) {
			client.setScreen(new StationWrenchScreen(hand, StationWrenchItem.description(client.player.getStackInHand(hand))));
		}
	}

	@Override
	protected void init() {
		final int left = (width - WIDTH) / 2;
		final int half = (WIDTH - 4) / 2;
		int y = top();
		final String text = descriptionField == null ? initial : descriptionField.getText();
		descriptionField = new TextFieldWidget(textRenderer, left, y, WIDTH, 18, Text.translatable(KEY + "description"));
		descriptionField.setMaxLength(StationDescriptionParser.MAX_INPUT);
		descriptionField.setText(text);
		descriptionField.setPlaceholder(Text.translatable(KEY + "description").formatted(Formatting.DARK_GRAY));
		addDrawableChild(descriptionField);
		y += TEXT_HEIGHT;
		final int presetWidth = (WIDTH - (PRESET_COLUMNS - 1) * 4) / PRESET_COLUMNS;
		for (int i = 0; i < StationPresets.ALL.size(); i++) {
			final StationPresets.Preset preset = StationPresets.ALL.get(i);
			final int column = i % PRESET_COLUMNS;
			final ButtonWidget button = ButtonWidget.builder(Text.translatable(KEY + "preset." + preset.key()), b -> {
				descriptionField.setText(preset.description());
				descriptionField.setCursorToStart();
			}).dimensions(left + column * (presetWidth + 4), y + i / PRESET_COLUMNS * 22, presetWidth, 20).build();
			button.setTooltip(Tooltip.of(Text.literal(preset.description())));
			addDrawableChild(button);
		}
		y += presetRows() * 22 + 6;
		addDrawableChild(action(left, y, half, "plan", StationWrenchItem.Action.PLAN));
		addDrawableChild(action(left + half + 4, y, half, "preview", StationWrenchItem.Action.PREVIEW));
		y += 24;
		addDrawableChild(action(left, y, half, "build", StationWrenchItem.Action.CONFIRM));
		final ButtonWidget replace = action(left + half + 4, y, half, "build_replace", StationWrenchItem.Action.CONFIRM_REPLACE_TERRAIN);
		replace.setTooltip(Tooltip.of(Text.translatable(KEY + "build_replace.tip")));
		addDrawableChild(replace);
		y += 24;
		addDrawableChild(action(left, y, half, "cancel", StationWrenchItem.Action.CANCEL));
		addDrawableChild(action(left + half + 4, y, half, "undo", StationWrenchItem.Action.UNDO));
		y += 28;
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(left, y, WIDTH, 20).build());
		setInitialFocus(descriptionField);
	}

	private ButtonWidget action(int x, int y, int buttonWidth, String key, StationWrenchItem.Action action) {
		return ButtonWidget.builder(Text.translatable(KEY + key), button -> send(action)).dimensions(x, y, buttonWidth, 20).build();
	}

	private void send(StationWrenchItem.Action action) {
		sent = true;
		ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, action, descriptionField.getText()));
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		final int left = (width - WIDTH) / 2;
		final int top = top();
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top - 16, 0xFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "example"), left, top + 22, 0x9AA0A6);
		context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "hint"), left, top + 32, 0x9AA0A6);
	}

	private static int presetRows() {
		return (StationPresets.ALL.size() + PRESET_COLUMNS - 1) / PRESET_COLUMNS;
	}

	/** The description field's y; the whole screen is centred vertically. */
	private int top() {
		final int total = TEXT_HEIGHT + presetRows() * 22 + 6 + 24 + 24 + 28 + 20;
		return Math.max(20, (height - total) / 2);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		if (!sent && !descriptionField.getText().strip().equals(initial)) {
			sent = true;
			ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, StationWrenchItem.Action.SAVE, descriptionField.getText()));
		}
	}
}
