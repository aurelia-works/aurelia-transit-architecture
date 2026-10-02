package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity;
import com.aureliatransit.architecture.block.wayfinding.WayfindingPanelSpec;
import com.aureliatransit.architecture.block.wayfinding.WayfindingPlateBlock;
import com.aureliatransit.architecture.block.wayfinding.WayfindingSignBlock;
import com.aureliatransit.architecture.client.interactive.EditorWidgets;
import com.aureliatransit.architecture.client.live.StationPickerScreen;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientServiceMessages;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.wayfinding.BadgeShape;
import com.aureliatransit.architecture.wayfinding.BoardView;
import com.aureliatransit.architecture.wayfinding.StationBoardLayout;
import com.aureliatransit.architecture.wayfinding.LanguageLayout;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.ServiceType;
import com.aureliatransit.architecture.wayfinding.Wayfinding;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingLayout;
import com.aureliatransit.architecture.wayfinding.WayfindingPanelKind;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor of the shared {@link WayfindingData}, used by every wayfinding block. Only the fields the block's
 * {@link WayfindingPanelKind} actually shows appear (see {@link Fields}); fields that are hidden keep their stored value.
 * The preview runs the same {@link WayfindingLayout} as the in-world renderer. The data is sent once when the screen closes.
 */
public class WayfindingEditScreen extends Screen {

	private static final int COLUMN = 150;
	private static final int GAP = 4;
	private static final int WIDTH = COLUMN * 2 + GAP;
	private static final int PREVIEW_MAX_HEIGHT = 44;
	private static final int ROW = 22;

	/** Common transit line colours (RGB); the named accent colours are appended at class load. */
	private static final List<Integer> PALETTE = new ArrayList<>(List.of(
			0x0066B3, 0xE87722, 0x00A651, 0xD4202F, 0x7A2E8E, 0xFFC20E, 0x00A3AD, 0x8B5A2B, 0xE6509B, 0x8CC63E, 0x6E7378, 0x1B1B1B));

	static {
		for (final AccentPalette accent : AccentPalette.values()) {
			if (accent != AccentPalette.NONE && !PALETTE.contains(accent.rgb())) {
				PALETTE.add(accent.rgb());
			}
		}
	}

	/**
	 * Which controls a panel kind shows.
	 */
	record Fields(boolean station, boolean secondary, boolean code, boolean lines, boolean arrow, boolean destination, boolean service, boolean platform,
	              boolean exit, boolean street, boolean transfers, boolean pictogram, boolean accent, boolean view) {

		static Fields of(WayfindingPanelKind kind) {
			return switch (kind) {
				case ENTRANCE_PYLON -> new Fields(true, true, true, true, false, false, false, false, true, false, false, true, true, false);
				case WALL_DIRECTION, HANGING_DIRECTION -> new Fields(false, false, false, true, true, true, true, false, false, false, false, true, true, false);
				case STREET -> new Fields(false, false, false, false, true, false, false, false, false, true, true, true, true, false);
				case PICTOGRAM -> new Fields(false, false, false, false, false, true, false, false, false, false, false, true, true, false);
				case PLATFORM -> new Fields(true, false, false, true, true, true, true, true, false, false, false, false, true, false);
				case EXIT -> new Fields(true, false, false, false, true, true, false, false, true, false, false, true, true, false);
				case BUS_STOP -> new Fields(true, false, true, true, false, false, false, false, false, false, false, false, false, false);
				case TERMINAL -> new Fields(true, true, true, true, false, false, false, false, false, true, true, false, true, false);
				case BOARD -> new Fields(true, false, false, true, true, true, false, true, false, true, true, false, false, true);
			};
		}
	}

	private final BlockPos pos;
	private final WayfindingSignBlockEntity sign;
	private final WayfindingPanelSpec spec;
	private final WayfindingPanelKind kind;
	private final Fields fields;
	/** The data as stored when the editor opened; the screen only sends when the result differs from it. */
	private final WayfindingData initial;
	/** Values the widgets start from: {@link #initial}, or the edited state restored after the station picker. */
	private WayfindingData seed;
	private StationAssociation association;
	private boolean switching;
	private final int rowLength;

	private boolean autoStation;
	private boolean autoLines;
	private SignArrow arrow;
	private ServiceType service;
	private LanguageLayout languageLayout;
	private Pictogram pictogram;
	private AccentPalette accent;
	private BoardView view;
	private final String[] badgeLabel = new String[WayfindingData.MAX_LINES];
	private final int[] badgeColor = new int[WayfindingData.MAX_LINES];
	private final BadgeShape[] badgeShape = new BadgeShape[WayfindingData.MAX_LINES];

	private TextFieldWidget stationField;
	private TextFieldWidget secondaryField;
	private TextFieldWidget codeField;
	private TextFieldWidget destinationField;
	private TextFieldWidget serviceLabelField;
	private TextFieldWidget platformField;
	private TextFieldWidget exitField;
	private TextFieldWidget streetField;
	private TextFieldWidget transfersField;

	private PanelLayout.Panel preview = PanelLayout.Panel.EMPTY;
	private boolean previewDirty = true;
	private boolean sent;

	public WayfindingEditScreen(BlockPos pos, WayfindingSignBlockEntity sign) {
		super(Text.translatable(EditorWidgets.KEY + "wf_edit"));
		this.pos = pos;
		this.sign = sign;
		this.spec = sign.spec();
		this.kind = sign.panelKind();
		this.fields = Fields.of(kind);
		this.initial = sign.getWayfinding();
		this.seed = initial;
		this.association = initial.association();
		this.autoStation = initial.autoStation();
		this.autoLines = initial.autoLines();
		this.arrow = initial.arrow();
		this.service = initial.serviceType();
		this.languageLayout = initial.languageLayout();
		this.pictogram = initial.pictogram();
		this.accent = initial.accent();
		this.view = initial.view();
		for (int i = 0; i < badgeLabel.length; i++) {
			final LineBadge badge = i < initial.lines().size() ? initial.lines().get(i) : null;
			badgeLabel[i] = badge == null ? "" : badge.label();
			badgeColor[i] = badge == null ? PALETTE.get(i % PALETTE.size()) : badge.rgb();
			badgeShape[i] = badge == null ? BadgeShape.ROUNDED : badge.shape();
		}
		this.rowLength = sign.getWorld() != null && sign.getCachedState().getBlock() instanceof WayfindingSignBlock block
				? block.rowLength(sign.getWorld(), pos, sign.getCachedState()) : 1;
	}

	private float panelWidth() {
		return spec == null ? 16 : spec.panelWidth(rowLength);
	}

	private float panelHeight() {
		return spec == null ? 8 : spec.height();
	}

	private boolean tallPanel() {
		return panelHeight() > panelWidth() * 1.4F;
	}

	@Override
	protected void init() {
		final int left = (width - WIDTH) / 2;
		final int right = left + COLUMN + GAP;
		int y = tallPanel() ? 16 : 10 + Math.round(panelHeight() * previewScale()) + 10;

		if (fields.station()) {
			stationField = field(left, y, WIDTH, WayfindingData.MAX_NAME, seed.stationName(), kind == WayfindingPanelKind.BUS_STOP ? "wf_stop" : "wf_station");
			y += ROW;
			addDrawableChild(EditorWidgets.cycler(left, y, COLUMN, List.of(Boolean.TRUE, Boolean.FALSE), autoStation,
					value -> Text.translatable(EditorWidgets.KEY + (value ? "auto_on" : "auto_off")), value -> {
						autoStation = value;
						previewDirty = true;
					}));
			if (fields.lines()) {
				addDrawableChild(EditorWidgets.cycler(right, y, COLUMN, List.of(Boolean.TRUE, Boolean.FALSE), autoLines,
						value -> Text.translatable(EditorWidgets.KEY + (value ? "wf_lines_auto" : "wf_lines_manual")), value -> {
							autoLines = value;
							previewDirty = true;
						}));
			}
			y += ROW;
			addDrawableChild(ButtonWidget.builder(associationText(), button -> openStationPicker()).dimensions(left, y, WIDTH, 20).build());
			y += ROW;
		}
		if (fields.view()) {
			addDrawableChild(EditorWidgets.cycler(left, y, WIDTH, List.of(BoardView.values()), view, value -> Text.translatable(EditorWidgets.KEY + "wf_view",
					Text.translatable(EditorWidgets.KEY + "wf_view." + value.id())), value -> {
				view = value;
				previewDirty = true;
			}));
			y += ROW;
		}
		if (fields.secondary() || fields.code()) {
			if (fields.secondary()) {
				secondaryField = field(left, y, fields.code() ? COLUMN : WIDTH, WayfindingData.MAX_NAME, seed.secondaryName(), "wf_secondary");
			}
			if (fields.code()) {
				codeField = field(fields.secondary() ? right : left, y, fields.secondary() ? COLUMN : WIDTH, WayfindingData.MAX_CODE, seed.stationCode(),
						kind == WayfindingPanelKind.BUS_STOP ? "wf_stop_code" : "wf_code");
			}
			y += ROW;
		}
		if (fields.secondary()) {
			addDrawableChild(EditorWidgets.cycler(left, y, WIDTH, List.of(LanguageLayout.values()), languageLayout, value -> Text.translatable(EditorWidgets.KEY + "wf_layout",
					Text.translatable(EditorWidgets.KEY + "wf_layout." + value.name().toLowerCase(Locale.ROOT))), value -> {
				languageLayout = value;
				previewDirty = true;
			}));
			y += ROW;
		}
		if (fields.exit() || fields.platform() || fields.street()) {
			int column = 0;
			if (fields.exit()) {
				exitField = field(left, y, 56, WayfindingData.MAX_EXIT_LABEL, seed.exitLabel(), "wf_exit");
				column = 1;
			}
			if (fields.platform()) {
				platformField = field(left + column * 60, y, 56, WayfindingData.MAX_PLATFORM, seed.platform(), "wf_platform");
				column++;
			}
			if (fields.street()) {
				streetField = field(left + column * 60, y, WIDTH - column * 60, WayfindingData.MAX_STREET, seed.streetLabel(), "wf_street");
			} else if (fields.destination()) {
				destinationField = field(left + column * 60, y, WIDTH - column * 60, WayfindingData.MAX_DESTINATION, seed.destination(), destinationKey());
			}
			y += ROW;
			if (fields.street() && fields.destination()) {
				// the station information board has both: its "trains this side" view shows the destination (1.3.1)
				destinationField = field(left, y, WIDTH, WayfindingData.MAX_DESTINATION, seed.destination(), destinationKey());
				y += ROW;
			}
		} else if (fields.destination()) {
			destinationField = field(left, y, WIDTH, WayfindingData.MAX_DESTINATION, seed.destination(), destinationKey());
			y += ROW;
		}
		if (fields.transfers()) {
			transfersField = field(left, y, WIDTH, WayfindingData.MAX_TRANSFERS, seed.transfers(), "wf_transfers");
			y += ROW;
		}
		if (fields.arrow() || fields.service()) {
			if (fields.arrow()) {
				addDrawableChild(EditorWidgets.cycler(left, y, fields.service() ? COLUMN : WIDTH, List.of(SignArrow.values()), arrow, EditorWidgets::arrowLabel, value -> {
					arrow = value;
					previewDirty = true;
				}));
			}
			if (fields.service()) {
				addDrawableChild(EditorWidgets.cycler(fields.arrow() ? right : left, y, fields.arrow() ? COLUMN : WIDTH, List.of(ServiceType.values()), service,
						value -> Text.translatable(EditorWidgets.KEY + "wf_service", Text.translatable(EditorWidgets.KEY + "wf_service." + value.name().toLowerCase(Locale.ROOT))),
						value -> {
							service = value;
							previewDirty = true;
						}));
			}
			y += ROW;
			if (fields.service()) {
				serviceLabelField = field(left, y, WIDTH, WayfindingData.MAX_SERVICE_LABEL, seed.serviceLabel(), "wf_service_label");
				y += ROW;
			}
		}
		if (fields.pictogram() || fields.accent()) {
			if (fields.pictogram()) {
				addDrawableChild(EditorWidgets.cycler(left, y, fields.accent() ? COLUMN : WIDTH, List.of(Pictogram.values()), pictogram, value -> Text.translatable(
						EditorWidgets.KEY + "wf_symbol", Text.translatable(EditorWidgets.KEY + "wf_pictogram." + value.name().toLowerCase(Locale.ROOT))), value -> {
					pictogram = value;
					previewDirty = true;
				}));
			}
			if (fields.accent()) {
				addDrawableChild(EditorWidgets.cycler(fields.pictogram() ? right : left, y, fields.pictogram() ? COLUMN : WIDTH, List.of(AccentPalette.values()), accent,
						EditorWidgets::accentLabel, value -> {
							accent = value;
							previewDirty = true;
						}));
			}
			y += ROW;
		}
		if (fields.lines()) {
			for (int i = 0; i < WayfindingData.MAX_LINES; i++) {
				final int index = i;
				final int x = i % 2 == 0 ? left : right;
				final TextFieldWidget badge = field(x, y, 40, LineBadge.MAX_LABEL, badgeLabel[i], "wf_badge");
				badge.setChangedListener(text -> {
					badgeLabel[index] = text;
					previewDirty = true;
				});
				final List<Integer> colours = new ArrayList<>(PALETTE);
				if (!colours.contains(badgeColor[i])) {
					colours.add(0, badgeColor[i]);
				}
				addDrawableChild(EditorWidgets.cycler(x + 42, y, COLUMN - 42, colours, badgeColor[i], WayfindingEditScreen::colourLabel, value -> {
					badgeColor[index] = value;
					previewDirty = true;
				}));
				if (i % 2 == 1) {
					y += ROW;
				}
			}
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(left, y + 4, WIDTH, 20).build());
	}

	private Text associationText() {
		if (association.isAuto()) {
			return Text.translatable(EditorWidgets.KEY + "wf_station_source", Text.translatable(EditorWidgets.KEY + "wf_station_source.auto"));
		}
		final String name = com.aureliatransit.architecture.transit.StationData.provider().listStations(512).stream()
				.filter(station -> station.id() == association.stationId()).map(station -> station.displayName()).findFirst().orElse("#" + association.stationId());
		return Text.translatable(EditorWidgets.KEY + "wf_station_source", Text.translatable(EditorWidgets.KEY + "wf_station_source.manual", name));
	}

	private void openStationPicker() {
		final WayfindingData edited = current();
		switching = true;
		client.setScreen(new StationPickerScreen(this, pos, association, () -> !sign.isRemoved(), picked -> {
			association = picked;
			seed = edited.withAssociation(picked);
			previewDirty = true;
		}));
	}

	private String destinationKey() {
		return kind == WayfindingPanelKind.PICTOGRAM ? "wf_caption" : kind == WayfindingPanelKind.EXIT ? "wf_exit_text" : "wf_destination";
	}

	private static Text colourLabel(int rgb) {
		final MutableText swatch = Text.literal("■ ").styled(style -> style.withColor(rgb));
		return swatch.append(Text.literal(String.format(Locale.ROOT, "#%06X", rgb)).styled(style -> style.withColor(0xFFFFFF)));
	}

	private TextFieldWidget field(int x, int y, int fieldWidth, int maxLength, String value, String nameKey) {
		final TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, fieldWidth, 18, Text.translatable(EditorWidgets.KEY + nameKey));
		field.setMaxLength(maxLength);
		field.setText(value);
		field.setPlaceholder(Text.translatable(EditorWidgets.KEY + nameKey));
		field.setChangedListener(text -> previewDirty = true);
		return addDrawableChild(field);
	}

	private static String text(TextFieldWidget field, String fallback) {
		return field == null ? fallback : field.getText();
	}

	private WayfindingData current() {
		final List<LineBadge> lines = new ArrayList<>();
		if (fields.lines()) {
			for (int i = 0; i < badgeLabel.length; i++) {
				if (!badgeLabel[i].isBlank()) {
					lines.add(new LineBadge(badgeLabel[i], badgeColor[i], badgeShape[i]));
				}
			}
		} else {
			lines.addAll(seed.lines());
		}
		return new WayfindingData(fields.station() ? autoStation : seed.autoStation(), text(stationField, seed.stationName()),
				text(secondaryField, seed.secondaryName()), text(codeField, seed.stationCode()), lines, fields.lines() ? autoLines : seed.autoLines(),
				fields.arrow() ? arrow : seed.arrow(), text(destinationField, seed.destination()), fields.service() ? service : seed.serviceType(),
				text(serviceLabelField, seed.serviceLabel()), text(platformField, seed.platform()), text(exitField, seed.exitLabel()),
				text(streetField, seed.streetLabel()), text(transfersField, seed.transfers()), fields.secondary() ? languageLayout : seed.languageLayout(),
				fields.pictogram() ? pictogram : seed.pictogram(), fields.accent() ? accent : seed.accent(),
				fields.station() ? association : seed.association(), fields.view() ? view : seed.view());
	}

	private float previewScale() {
		if (tallPanel()) {
			final int available = Math.max(40, width - ((width - WIDTH) / 2 + WIDTH + 12));
			return Math.max(1, Math.min(3.5F, Math.min(110F / panelHeight(), available / panelWidth())));
		}
		return Math.min((WIDTH + 40F) / panelWidth(), PREVIEW_MAX_HEIGHT / panelHeight());
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
			final WayfindingData data = current();
			preview = kind == WayfindingPanelKind.BOARD
					? StationBoardLayout.layout(Wayfinding.resolve(pos, data), data.view(), ClientServiceMessages.allFor(Wayfinding.resolve(pos, data).messageStation()), panelWidth(),
					panelHeight(), spec == null ? 0xFFFFFFFF : spec.textColor(), s -> textRenderer.getWidth(s))
					: kind == WayfindingPanelKind.TERMINAL
					? TerminalFace.layout(Wayfinding.resolve(pos, data), panelWidth(), panelHeight(), spec == null ? 0xFFFFFFFF : spec.textColor(), s -> textRenderer.getWidth(s))
					: WayfindingLayout.layout(Wayfinding.resolve(pos, data), kind, panelWidth(), panelHeight(), spec == null ? 0xFFFFFFFF : spec.textColor(),
					s -> textRenderer.getWidth(s));
		}
		final float scale = previewScale();
		final int previewWidth = Math.round(panelWidth() * scale);
		final int background = spec == null ? 0xFF2C2F33 : spec.faceColor();
		if (tallPanel()) {
			final int x = (width - WIDTH) / 2 + WIDTH + 12;
			EditorWidgets.drawPreview(context, textRenderer, preview, x, 16, panelWidth(), panelHeight(), scale, background);
		} else {
			EditorWidgets.drawPreview(context, textRenderer, preview, (width - previewWidth) / 2, 10, panelWidth(), panelHeight(), scale, background);
		}
	}

	@Override
	public void removed() {
		if (sent || switching) {
			switching = false;
			return;
		}
		sent = true;
		final WayfindingData data = current();
		if (!data.equals(initial)) {
			ClientPlayNetworking.send(ModPackets.UPDATE_WAYFINDING, ModPackets.writeWayfinding(pos, data));
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/**
	 * Opens the editor for the wayfinding block entity at {@code pos}, if there is one.
	 */
	public static void open(BlockPos pos) {
		final net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client.world != null && client.world.getBlockEntity(pos) instanceof WayfindingSignBlockEntity sign && sign.getCachedState().getBlock() instanceof WayfindingPlateBlock) {
			client.setScreen(new WayfindingEditScreen(pos, sign));
		}
	}
}
