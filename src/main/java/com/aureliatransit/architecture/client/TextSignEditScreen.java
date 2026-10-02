package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.client.interactive.EditorWidgets;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds;
import com.aureliatransit.architecture.block.elevated.LiftStatusPanelBlock;
import com.aureliatransit.architecture.text.LiftPanelLayout;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.RouteBadge;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.SignData;
import com.aureliatransit.architecture.text.SignStyle;
import com.aureliatransit.architecture.text.TextAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Editor for every sign kind. Which controls appear depends on the sign's {@link SignStyle}; a live preview uses the
 * same layout code as the in-world renderer. Sent on close.
 */
public class TextSignEditScreen extends Screen {

	private static final int WIDTH = 260;
	private static final int PREVIEW_MAX_HEIGHT = 52;

	private final BlockPos pos;
	private final TextSignBlockEntity sign;
	private final TextLayout layout;
	private final SignStyle style;
	private final SignData initial;
	private final int rowLength;

	private TextFieldWidget primaryField;
	private TextFieldWidget secondaryField;
	private TextFieldWidget platformField;
	private final List<TextFieldWidget> routeFields = new ArrayList<>();
	private final RouteBadge[] routeState = new RouteBadge[SignStyle.MAX_ROUTES];
	private TextAlignment alignment;
	private AccentPalette accent;
	private SignArrow arrow;
	private boolean auto;
	/** Lift status panel only (A8); null for every other sign. */
	private final ElevatedKinds.LiftStatus initialStatus;
	private ElevatedKinds.LiftStatus status;

	private PanelLayout.Panel preview = PanelLayout.Panel.EMPTY;
	private boolean previewDirty = true;
	private boolean sent;

	public TextSignEditScreen(BlockPos pos, TextSignBlockEntity sign) {
		super(Text.translatable(EditorWidgets.KEY + "edit_sign"));
		this.pos = pos;
		this.sign = sign;
		this.layout = sign.getLayout();
		this.style = layout == null ? SignStyle.STATION : layout.style();
		this.initial = sign.getData();
		this.alignment = initial.alignment();
		this.accent = initial.accent();
		this.arrow = initial.arrow();
		this.auto = initial.autoName();
		this.initialStatus = sign.getCachedState().getBlock() instanceof LiftStatusPanelBlock ? sign.getCachedState().get(LiftStatusPanelBlock.STATUS) : null;
		this.status = initialStatus;
		for (int i = 0; i < routeState.length; i++) {
			routeState[i] = i < initial.routes().size() ? initial.routes().get(i) : new RouteBadge("", AccentPalette.NONE);
		}
		this.rowLength = sign.getWorld() != null && sign.getCachedState().getBlock() instanceof TextSignBlock block && layout != null && layout.joins()
				? TextSignBlockEntityRenderer.rowLength(sign.getWorld(), pos, sign.getCachedState(), block) : 1;
	}

	private float panelWidth() {
		return layout != null && layout.joins() ? rowLength * 16 - 2.5F : layout == null ? 13.5F : layout.width();
	}

	private float panelHeight() {
		return layout == null ? 8 : layout.height();
	}

	@Override
	protected void init() {
		routeFields.clear();
		final float scale = previewScale();
		int y = 14 + Math.round(panelHeight() * scale) + 14;
		final int left = (width - WIDTH) / 2;

		primaryField = field(left, y, WIDTH, SignData.MAX_PRIMARY, initial.primary(), style.isNumberPlate() ? "caption" : "name");
		y += 22;
		if (style.hasSecondary()) {
			secondaryField = field(left, y, WIDTH, SignData.MAX_SECONDARY, initial.secondary(), "secondary");
			y += 22;
		}
		if (style.hasPlatform()) {
			platformField = field(left, y, 60, SignData.MAX_PLATFORM, initial.platform(), "platform");
			if (style.hasArrow()) {
				addDrawableChild(EditorWidgets.cycler(left + 64, y, 100, List.of(SignArrow.values()), arrow, EditorWidgets::arrowLabel, value -> {
					arrow = value;
					previewDirty = true;
				}));
				addDrawableChild(EditorWidgets.cycler(left + 168, y, WIDTH - 168, List.of(TextAlignment.values()), alignment, EditorWidgets::alignmentLabel, value -> {
					alignment = value;
					previewDirty = true;
				}));
			}
			y += 22;
		}
		if (style.hasRoutes()) {
			for (int i = 0; i < SignStyle.MAX_ROUTES; i++) {
				final int index = i;
				final int x = left + (i % 2) * (WIDTH / 2 + 2);
				final TextFieldWidget routeField = field(x, y, 44, RouteBadge.MAX_LABEL, routeState[i].label(), "route");
				routeField.setChangedListener(text -> {
					routeState[index] = new RouteBadge(text, routeState[index].color());
					previewDirty = true;
				});
				routeFields.add(routeField);
				addDrawableChild(EditorWidgets.cycler(x + 46, y, WIDTH / 2 - 48, List.of(AccentPalette.values()), routeState[i].color(), EditorWidgets::shortAccent, value -> {
					routeState[index] = new RouteBadge(routeState[index].label(), value);
					previewDirty = true;
				}));
				if (i % 2 == 1) {
					y += 22;
				}
			}
		}
		final int half = (WIDTH - 4) / 2;
		if (status != null) {
			addDrawableChild(EditorWidgets.cycler(left, y, WIDTH, List.of(ElevatedKinds.LiftStatus.values()), status,
					value -> Text.translatable("screen.aurelia_transit_architecture.lift_status", LiftPanelLayout.status(value.ordinal()).label()), value -> {
						status = value;
						previewDirty = true;
					}));
			y += 22;
		}
		if (!style.hasArrow()) {
			addDrawableChild(EditorWidgets.cycler(left, y, half, List.of(TextAlignment.values()), alignment, EditorWidgets::alignmentLabel, value -> {
				alignment = value;
				previewDirty = true;
			}));
			addDrawableChild(EditorWidgets.cycler(left + half + 4, y, half, List.of(AccentPalette.values()), accent, EditorWidgets::accentLabel, value -> {
				accent = value;
				previewDirty = true;
			}));
		} else {
			addDrawableChild(EditorWidgets.cycler(left, y, style.hasAutoName() ? half : WIDTH, List.of(AccentPalette.values()), accent, EditorWidgets::accentLabel, value -> {
				accent = value;
				previewDirty = true;
			}));
			if (style.hasAutoName()) {
				addDrawableChild(EditorWidgets.cycler(left + half + 4, y, half, List.of(Boolean.FALSE, Boolean.TRUE), auto,
						value -> Text.translatable(EditorWidgets.KEY + (value ? "auto_on" : "auto_off")), value -> {
							auto = value;
							previewDirty = true;
						}));
			}
		}
		y += 24;
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(left, y, WIDTH, 20).build());
		setInitialFocus(primaryField);
	}

	private TextFieldWidget field(int x, int y, int fieldWidth, int maxLength, String value, String nameKey) {
		final TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, fieldWidth, 18, Text.translatable(EditorWidgets.KEY + nameKey));
		field.setMaxLength(maxLength);
		field.setText(value);
		field.setPlaceholder(Text.translatable(EditorWidgets.KEY + nameKey));
		field.setChangedListener(text -> previewDirty = true);
		return addDrawableChild(field);
	}

	private float previewScale() {
		return Math.min((WIDTH + 40F) / panelWidth(), PREVIEW_MAX_HEIGHT / panelHeight());
	}

	private SignData current() {
		final List<RouteBadge> routes = new ArrayList<>(Arrays.asList(routeState));
		return new SignData(primaryField.getText(), secondaryField == null ? "" : secondaryField.getText(), alignment, accent, arrow,
				platformField == null ? "" : platformField.getText(), auto, routes).restrictedTo(style);
	}

	private int faceColor() {
		return switch (style) {
			case DIRECTION, LIFT -> 0xFF2C2F33;
			case BUS_STOP -> 0xFFF2F2EE;
			default -> 0xFF1D375A;
		};
	}

	@Override
	public void tick() {
		if (sign.isRemoved()) {
			sent = true;
			close();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		if (previewDirty) {
			previewDirty = false;
			final SignData data = current();
			preview = status != null
					? LiftPanelLayout.layout(data.primary(), data.secondary(), LiftPanelLayout.status(status.ordinal()), panelWidth(), panelHeight(), style.textColor(),
					style.secondaryColor(), s -> textRenderer.getWidth(s))
					: PanelLayout.sign(data, TextSignBlockEntityRenderer.resolvePrimary(data, style, pos), style, panelWidth(), panelHeight(), s -> textRenderer.getWidth(s));
		}
		final float scale = previewScale();
		final int previewWidth = Math.round(panelWidth() * scale);
		EditorWidgets.drawPreview(context, textRenderer, preview, (width - previewWidth) / 2, 14, panelWidth(), panelHeight(), scale, faceColor());
	}

	@Override
	public void removed() {
		if (sent) {
			return;
		}
		sent = true;
		final SignData data = current();
		if (!data.equals(initial)) {
			ClientPlayNetworking.send(ModPackets.UPDATE_SIGN, ModPackets.writeSign(pos, data));
		}
		if (status != null && status != initialStatus) {
			ClientPlayNetworking.send(ModPackets.UPDATE_LIFT_STATUS, ModPackets.writeLiftStatus(pos, status));
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
