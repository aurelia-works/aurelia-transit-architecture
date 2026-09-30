package com.aureliatransit.architecture.client.interactive;

import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.InfoLayout;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.ConfigurableTextData;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.TextAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The one text editor for information case, information pillar and timetable case: heading, body rows, alignment and
 * accent, with a live preview drawn by the same layout code the in-world renderer uses. Sent on close.
 */
public class InfoTextEditScreen extends Screen {

	private static final int FIELD_WIDTH = 240;
	private static final int PREVIEW_SPACE = 130;

	private final BlockPos pos;
	private final ConfigurableTextData initial;
	private final InfoLayout layout;
	private final List<TextFieldWidget> bodyFields = new ArrayList<>();
	private TextFieldWidget headingField;
	private TextAlignment alignment;
	private AccentPalette accent;
	private PanelLayout.Panel preview = PanelLayout.Panel.EMPTY;
	private boolean previewDirty = true;
	private boolean sent;

	public InfoTextEditScreen(BlockPos pos, ConfigurableTextData initial, InfoLayout layout) {
		super(Text.translatable(EditorWidgets.KEY + "edit_info"));
		this.pos = pos;
		this.initial = initial;
		this.layout = layout;
		this.alignment = initial.alignment();
		this.accent = initial.accent();
	}

	@Override
	protected void init() {
		bodyFields.clear();
		final int rows = layout.maxBodyLines();
		final int rowHeight = Math.max(16, Math.min(22, (height - 88) / (rows + 1)));
		final int left = fieldsLeft();
		int y = 24;

		headingField = new TextFieldWidget(textRenderer, left, y, FIELD_WIDTH, 18, Text.translatable(EditorWidgets.KEY + "heading"));
		headingField.setMaxLength(ConfigurableTextData.MAX_HEADING);
		headingField.setText(initial.heading());
		headingField.setPlaceholder(Text.translatable(EditorWidgets.KEY + "heading"));
		headingField.setChangedListener(text -> previewDirty = true);
		addDrawableChild(headingField);
		y += rowHeight + 4;

		for (int i = 0; i < rows; i++) {
			final TextFieldWidget field = new TextFieldWidget(textRenderer, left, y, FIELD_WIDTH, 18, Text.translatable(EditorWidgets.KEY + "line", i + 1));
			field.setMaxLength(ConfigurableTextData.MAX_LINE);
			field.setText(i < initial.body().size() ? initial.body().get(i) : "");
			field.setPlaceholder(Text.translatable(EditorWidgets.KEY + "line", i + 1));
			field.setChangedListener(text -> previewDirty = true);
			bodyFields.add(addDrawableChild(field));
			y += rowHeight;
		}
		setInitialFocus(headingField);

		y += 4;
		final int half = (FIELD_WIDTH - 4) / 2;
		addDrawableChild(EditorWidgets.cycler(left, y, half, List.of(TextAlignment.values()), alignment, EditorWidgets::alignmentLabel, value -> {
			alignment = value;
			previewDirty = true;
		}));
		addDrawableChild(EditorWidgets.cycler(left + half + 4, y, half, List.of(AccentPalette.values()), accent, EditorWidgets::accentLabel, value -> {
			accent = value;
			previewDirty = true;
		}));
		y += 24;
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(left, y, FIELD_WIDTH, 20).build());
	}

	private boolean hasPreviewSpace() {
		return width >= FIELD_WIDTH + PREVIEW_SPACE + 40;
	}

	private int fieldsLeft() {
		return (width - FIELD_WIDTH - (hasPreviewSpace() ? PREVIEW_SPACE + 12 : 0)) / 2;
	}

	private ConfigurableTextData current() {
		final List<String> body = new ArrayList<>();
		for (final TextFieldWidget field : bodyFields) {
			body.add(field.getText());
		}
		return new ConfigurableTextData(headingField.getText(), body, alignment, accent);
	}

	@Override
	public void tick() {
		if (client != null && client.world != null && !(client.world.getBlockState(pos).getBlock() instanceof InfoDisplayBlock)) {
			sent = true;
			close();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
		super.render(context, mouseX, mouseY, delta);

		if (previewDirty) {
			previewDirty = false;
			preview = PanelLayout.info(current(), InfoDisplayRenderer.styleOf(layout), layout.width(), layout.height(), s -> textRenderer.getWidth(s));
		}
		if (hasPreviewSpace()) {
			final float scale = Math.min(PREVIEW_SPACE / layout.width(), (height - 60F) / layout.height());
			final boolean dark = layout.bodyColor() == 0xFFEDEDE8;
			EditorWidgets.drawPreview(context, textRenderer, preview, fieldsLeft() + FIELD_WIDTH + 12, 28, layout.width(), layout.height(), scale,
					dark ? 0xFF10141A : 0xFFF2F2EE);
		}
	}

	@Override
	public void removed() {
		if (sent) {
			return;
		}
		sent = true;
		final ConfigurableTextData data = current();
		if (!data.equals(initial)) {
			ClientPlayNetworking.send(ModPackets.UPDATE_INFO_TEXT, ModPackets.writeInfoText(pos, data));
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
