package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain editor for sign text, with buttons that insert wayfinding arrows.
 */
public class TextSignEditScreen extends Screen {

	private static final String[] ARROWS = {"←", "→", "↑", "↓", "↖", "↗"};
	private static final int FIELD_WIDTH = 260;

	private final BlockPos pos;
	private final TextSignBlockEntity sign;
	private final TextLayout layout;
	private final List<TextFieldWidget> fields = new ArrayList<>();
	private TextFieldWidget lastField;
	private boolean sent;

	public TextSignEditScreen(BlockPos pos, TextSignBlockEntity sign) {
		super(Text.translatable("screen." + AureliaTransitArchitecture.MOD_ID + ".edit_sign"));
		this.pos = pos;
		this.sign = sign;
		this.layout = sign.getLayout();
	}

	@Override
	protected void init() {
		fields.clear();
		final int lineCount = layout == null ? TextSignBlockEntity.MAX_LINES : layout.maxLines();
		final int maxLength = layout == null ? TextSignBlockEntity.MAX_LENGTH : layout.maxLength();
		final int left = (width - FIELD_WIDTH) / 2;
		int y = height / 2 - 50;
		final List<String> current = sign.getLines();
		for (int i = 0; i < lineCount; i++) {
			final TextFieldWidget field = new TextFieldWidget(textRenderer, left, y, FIELD_WIDTH, 20,
					Text.translatable("screen." + AureliaTransitArchitecture.MOD_ID + ".line", i + 1));
			field.setMaxLength(maxLength);
			field.setText(current.get(i));
			fields.add(addDrawableChild(field));
			y += 24;
		}
		lastField = fields.get(0);
		setInitialFocus(lastField);

		final int arrowWidth = 24;
		int x = (width - ARROWS.length * (arrowWidth + 4) + 4) / 2;
		for (final String arrow : ARROWS) {
			addDrawableChild(ButtonWidget.builder(Text.literal(arrow), button -> insert(arrow)).dimensions(x, y + 4, arrowWidth, 20).build());
			x += arrowWidth + 4;
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(width / 2 - 100, y + 34, 200, 20).build());
	}

	private void insert(String text) {
		lastField.write(text);
		setFocused(lastField);
	}

	@Override
	public void tick() {
		for (final TextFieldWidget field : fields) {
			field.tick();
			if (field.isFocused()) {
				lastField = field;
			}
		}
		if (sign.isRemoved()) {
			close();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 72, 0xFFFFFF);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void removed() {
		if (!sent && !sign.isRemoved()) {
			sent = true;
			final List<String> lines = fields.stream().map(TextFieldWidget::getText).toList();
			ClientPlayNetworking.send(ModPackets.UPDATE_SIGN_TEXT, ModPackets.writeSignText(pos, lines));
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
