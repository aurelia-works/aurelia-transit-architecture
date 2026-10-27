package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.AirportPresets;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/**
 * The airport wrench screen: the airport choices (Regional, Small, Two runways, Airstrip) write their words into the text box, which
 * can be edited freely ("airport, 2 runways, 6 gates, no terminal"); then Plan in front of me, Build, Cancel or Undo. Each button
 * sends one packet (hand, action, words) like the station wrench screen and closes the screen, so the outline and the chat summary
 * are visible. The server re-checks the operator level and bounds the words, and plans an airport whatever they say.
 */
public class AirportWrenchScreen extends Screen {

	private static final String KEY = "screen." + AureliaTransitArchitecture.MOD_ID + ".airport_wrench.";
	private static final int WIDTH = 260;
	private static final int ROW = 22;

	private final Hand hand;
	private String words;
	private TextFieldWidget field;

	public AirportWrenchScreen(Hand hand, String words) {
		super(Text.translatable(KEY + "title"));
		this.hand = hand;
		this.words = words;
	}

	public static void open(Hand hand) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.setScreen(new AirportWrenchScreen(hand, StationWrenchItem.description(client.player.getStackInHand(hand))));
		}
	}

	@Override
	protected void init() {
		final int left = (width - WIDTH) / 2;
		int y = Math.max(20, height / 2 - 80);
		final int half = (WIDTH - 4) / 2;
		for (int i = 0; i < AirportPresets.ALL.size(); i++) {
			final AirportPresets.Preset preset = AirportPresets.ALL.get(i);
			addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "preset." + preset.key()), b -> field.setText(preset.description()))
					.dimensions(left + (i % 2) * (half + 4), y + (i / 2) * ROW, half, 20).build());
		}
		y += ((AirportPresets.ALL.size() + 1) / 2) * ROW + 12;
		field = new TextFieldWidget(textRenderer, left, y, WIDTH, 18, Text.translatable(KEY + "description"));
		field.setMaxLength(StationDescriptionParser.MAX_INPUT);
		field.setText(words);
		field.setPlaceholder(Text.translatable(KEY + "example"));
		addDrawableChild(field);
		y += 30;
		final int third = (WIDTH - 8) / 3;
		action(StationWrenchItem.Action.PLAN, "plan", left, y, WIDTH);
		y += ROW;
		action(StationWrenchItem.Action.CONFIRM, "build", left, y, third);
		action(StationWrenchItem.Action.CANCEL, "cancel", left + third + 4, y, third);
		action(StationWrenchItem.Action.UNDO, "undo", left + 2 * (third + 4), y, third);
	}

	private void action(StationWrenchItem.Action action, String key, int x, int y, int w) {
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + key), b -> send(action)).dimensions(x, y, w, 20).build());
	}

	private void send(StationWrenchItem.Action action) {
		words = field.getText();
		ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, action, words, ""));
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, Math.max(6, height / 2 - 100), 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable(KEY + "hint").formatted(Formatting.GRAY), width / 2, Math.max(6, height / 2 - 100) + 12, 0xAAAAAA);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		// closing without an action saves the words on the wrench, so its next click on the ground plans them
		if (field != null && !field.getText().equals(StationWrenchItem.description(client.player.getStackInHand(hand)))) {
			ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, StationWrenchItem.Action.SAVE, field.getText(), ""));
		}
		super.close();
	}
}
