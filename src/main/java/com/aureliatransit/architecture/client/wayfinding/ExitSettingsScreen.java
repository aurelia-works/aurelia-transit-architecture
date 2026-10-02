package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.client.interactive.EditorWidgets;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.wayfinding.ExitInfo;
import com.aureliatransit.architecture.wayfinding.ExitSetting;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Per-exit settings of a station information board's Exits view (A3): one row per exit with its own "leads to" text,
 * arrow and show/hide, independent of the others. Rows start with MTR's exits (their destinations as placeholder) and
 * the stored settings; empty rows add manual exits. Returns the result to the wayfinding editor, which sends it with
 * the rest of the sign; nothing is sent from here.
 */
public final class ExitSettingsScreen extends Screen {

	private static final int ROW = 22;
	private static final int WIDTH = 304;

	private final Screen parent;
	private final BooleanSupplier valid;
	private final Consumer<List<ExitSetting>> onDone;
	private final String[] label = new String[WayfindingData.MAX_EXIT_SETTINGS];
	private final String[] text = new String[WayfindingData.MAX_EXIT_SETTINGS];
	private final String[] hint = new String[WayfindingData.MAX_EXIT_SETTINGS];
	private final SignArrow[] arrow = new SignArrow[WayfindingData.MAX_EXIT_SETTINGS];
	private final boolean[] hidden = new boolean[WayfindingData.MAX_EXIT_SETTINGS];
	private final TextFieldWidget[] labelFields = new TextFieldWidget[WayfindingData.MAX_EXIT_SETTINGS];
	private final TextFieldWidget[] textFields = new TextFieldWidget[WayfindingData.MAX_EXIT_SETTINGS];

	public ExitSettingsScreen(Screen parent, List<ExitInfo> mtrExits, List<ExitSetting> settings, BooleanSupplier valid, Consumer<List<ExitSetting>> onDone) {
		super(Text.translatable(EditorWidgets.KEY + "wf_exits"));
		this.parent = parent;
		this.valid = valid;
		this.onDone = onDone;
		int row = 0;
		for (final ExitInfo exit : mtrExits) {
			if (row >= label.length) {
				break;
			}
			final ExitSetting setting = find(settings, exit.label());
			label[row] = exit.label();
			hint[row] = String.join(", ", exit.destinations());
			text[row] = setting == null ? "" : setting.text();
			arrow[row] = setting == null ? SignArrow.NONE : setting.arrow();
			hidden[row] = setting != null && setting.hidden();
			row++;
		}
		for (final ExitSetting setting : settings) {
			if (row < label.length && !contains(row, setting.label())) {
				label[row] = setting.label();
				hint[row] = "";
				text[row] = setting.text();
				arrow[row] = setting.arrow();
				hidden[row] = setting.hidden();
				row++;
			}
		}
		for (; row < label.length; row++) {
			label[row] = "";
			hint[row] = "";
			text[row] = "";
			arrow[row] = SignArrow.NONE;
		}
	}

	private static ExitSetting find(List<ExitSetting> settings, String exitLabel) {
		for (final ExitSetting setting : settings) {
			if (setting.label().equalsIgnoreCase(exitLabel.trim())) {
				return setting;
			}
		}
		return null;
	}

	private boolean contains(int rows, String exitLabel) {
		for (int i = 0; i < rows; i++) {
			if (label[i].equalsIgnoreCase(exitLabel.trim())) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void init() {
		final int left = (width - WIDTH) / 2;
		int y = 30;
		for (int i = 0; i < label.length; i++) {
			final int row = i;
			labelFields[i] = addDrawableChild(new TextFieldWidget(textRenderer, left, y, 34, 18, Text.translatable(EditorWidgets.KEY + "wf_exit")));
			labelFields[i].setMaxLength(ExitInfo.MAX_LABEL);
			labelFields[i].setText(label[i]);
			labelFields[i].setPlaceholder(Text.translatable(EditorWidgets.KEY + "wf_exit"));
			labelFields[i].setChangedListener(value -> label[row] = value);
			textFields[i] = addDrawableChild(new TextFieldWidget(textRenderer, left + 38, y, 150, 18, Text.translatable(EditorWidgets.KEY + "wf_exit_text")));
			textFields[i].setMaxLength(ExitSetting.MAX_TEXT);
			textFields[i].setText(text[i]);
			textFields[i].setPlaceholder(hint[i].isEmpty() ? Text.translatable(EditorWidgets.KEY + "wf_exit_text") : Text.literal(hint[i]));
			textFields[i].setChangedListener(value -> text[row] = value);
			addDrawableChild(EditorWidgets.cycler(left + 192, y - 1, 52, List.of(SignArrow.values()), arrow[i],
					value -> Text.literal(value == SignArrow.NONE ? "-" : value.glyph()), value -> arrow[row] = value));
			addDrawableChild(ButtonWidget.builder(shownText(hidden[i]), button -> {
				hidden[row] = !hidden[row];
				button.setMessage(shownText(hidden[row]));
			}).dimensions(left + 248, y - 1, 56, 20).build());
			y += ROW;
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(left, y + 6, WIDTH, 20).build());
	}

	private static Text shownText(boolean isHidden) {
		return Text.translatable(EditorWidgets.KEY + (isHidden ? "wf_exit_hidden" : "wf_exit_shown"));
	}

	@Override
	public void tick() {
		if (!valid.getAsBoolean()) {
			MinecraftClient.getInstance().setScreen(null);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		final List<ExitSetting> out = new ArrayList<>();
		for (int i = 0; i < label.length; i++) {
			final ExitSetting setting = new ExitSetting(label[i], text[i], arrow[i], hidden[i]);
			if (!setting.isEmpty() && !setting.isDefault()) {
				out.add(setting);
			}
		}
		onDone.accept(out);
		MinecraftClient.getInstance().setScreen(parent);
	}
}
