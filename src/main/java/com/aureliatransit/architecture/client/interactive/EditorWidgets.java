package com.aureliatransit.architecture.client.interactive;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.TextAlignment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Widgets and the panel preview shared by the information editor and the sign editor.
 */
public final class EditorWidgets {

	public static final String KEY = "screen." + AureliaTransitArchitecture.MOD_ID + ".";

	private EditorWidgets() {
	}

	/**
	 * A button that steps through {@code values} on each click.
	 */
	public static <T> ButtonWidget cycler(int x, int y, int width, List<T> values, T initial, Function<T, Text> label, Consumer<T> onChange) {
		final int[] index = {Math.max(0, values.indexOf(initial))};
		return ButtonWidget.builder(label.apply(values.get(index[0])), button -> {
			index[0] = (index[0] + 1) % values.size();
			final T value = values.get(index[0]);
			button.setMessage(label.apply(value));
			onChange.accept(value);
		}).dimensions(x, y, width, 20).build();
	}

	public static Text alignmentLabel(TextAlignment alignment) {
		return Text.translatable(KEY + "align", Text.translatable(KEY + "align." + alignment.name().toLowerCase(Locale.ROOT)));
	}

	public static Text accentLabel(AccentPalette accent) {
		final MutableText name = Text.translatable(KEY + "accent." + accent.name().toLowerCase(Locale.ROOT));
		if (accent != AccentPalette.NONE) {
			name.styled(style -> style.withColor(accent.rgb()));
		}
		return Text.translatable(KEY + "accent", name);
	}

	public static Text arrowLabel(SignArrow arrow) {
		return Text.translatable(KEY + "arrow", arrow == SignArrow.NONE ? Text.translatable(KEY + "arrow.none") : Text.literal(arrow.glyph()));
	}

	public static Text shortAccent(AccentPalette accent) {
		final MutableText name = Text.translatable(KEY + "accent." + accent.name().toLowerCase(Locale.ROOT));
		if (accent != AccentPalette.NONE) {
			name.styled(style -> style.withColor(accent.rgb()));
		}
		return name;
	}

	/**
	 * Draws a panel layout scaled into a GUI rectangle: {@code scale} GUI pixels per model pixel.
	 */
	public static void drawPreview(DrawContext context, TextRenderer textRenderer, PanelLayout.Panel panel, int left, int top, float panelW, float panelH,
	                               float scale, int background) {
		final int width = Math.round(panelW * scale);
		final int height = Math.round(panelH * scale);
		context.fill(left - 1, top - 1, left + width + 1, top + height + 1, 0xFF000000);
		context.fill(left, top, left + width, top + height, background);
		for (final PanelLayout.Rect rect : panel.rects()) {
			final int x1 = left + Math.round((panelW / 2 + rect.cx() - rect.w() / 2) * scale);
			final int x2 = left + Math.round((panelW / 2 + rect.cx() + rect.w() / 2) * scale);
			final int y1 = top + Math.round((panelH / 2 - rect.cy() - rect.h() / 2) * scale);
			final int y2 = top + Math.round((panelH / 2 - rect.cy() + rect.h() / 2) * scale);
			context.fill(x1, y1, x2, y2, rect.argb());
		}
		for (final PanelLayout.Label label : panel.labels()) {
			context.getMatrices().push();
			context.getMatrices().translate(left + (panelW / 2 + label.cx()) * scale, top + (panelH / 2 - label.cy()) * scale, 0);
			final float s = label.scale() * scale;
			context.getMatrices().scale(s, s, 1);
			context.drawText(textRenderer, label.text(), Math.round(-label.widthUnits() / 2F), -4, label.argb(), false);
			context.getMatrices().pop();
		}
	}
}
