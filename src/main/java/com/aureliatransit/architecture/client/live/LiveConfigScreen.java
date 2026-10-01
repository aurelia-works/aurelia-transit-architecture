package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationAssociationMode;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Base of the display and speaker configuration screens: the shared "which MTR station / platforms" picker (Auto or
 * Manual with a searchable station list and platform toggles). Subclasses add their own options in the left column.
 */
abstract class LiveConfigScreen extends Screen {

	private static final int STATION_ROWS = 4;
	protected static final int COLUMN_WIDTH = 150;

	protected final BlockPos pos;
	private StationAssociationMode mode;
	private long stationId;
	private final List<Long> platformIds = new ArrayList<>();
	private String filter = "";
	private int stationPage;
	private int pageCount = 1;
	private boolean platformsHint;
	private final List<ClickableWidget> listWidgets = new ArrayList<>();
	private ButtonWidget modeButton;
	private TextFieldWidget filterField;
	private int rightX;
	private int listY;
	private boolean sent;

	LiveConfigScreen(Text title, BlockPos pos, StationAssociation initial) {
		super(title);
		this.pos = pos;
		this.mode = initial.mode();
		this.stationId = initial.stationId();
		this.platformIds.addAll(initial.platformIds());
	}

	protected StationAssociation association() {
		return new StationAssociation(mode, mode == StationAssociationMode.MANUAL ? stationId : 0, mode == StationAssociationMode.MANUAL ? platformIds : List.of());
	}

	protected static Text tr(String key, Object... args) {
		return Text.translatable("screen." + AureliaTransitArchitecture.MOD_ID + "." + key, args);
	}

	protected <T extends ClickableWidget> T add(T widget) {
		return addDrawableChild(widget);
	}

	protected int leftX() {
		return width / 2 - COLUMN_WIDTH - 6;
	}

	protected int topY() {
		return Math.max(24, height / 2 - 100);
	}

	/**
	 * Builds the association picker in the right column. Call from {@link #init()}.
	 */
	protected void buildAssociationWidgets() {
		rightX = width / 2 + 6;
		final int y = topY();
		modeButton = add(ButtonWidget.builder(modeText(), button -> {
			mode = mode == StationAssociationMode.AUTO ? StationAssociationMode.MANUAL : StationAssociationMode.AUTO;
			button.setMessage(modeText());
			refreshLists();
		}).dimensions(rightX, y, COLUMN_WIDTH, 20).build());
		filterField = add(new TextFieldWidget(textRenderer, rightX, y + 24, COLUMN_WIDTH, 18, tr("live_filter")));
		filterField.setMaxLength(32);
		filterField.setText(filter);
		filterField.setChangedListener(text -> {
			filter = text.toLowerCase(Locale.ROOT).trim();
			stationPage = 0;
			refreshLists();
		});
		listY = y + 46;
		refreshLists();
	}

	private Text modeText() {
		return tr(mode == StationAssociationMode.AUTO ? "live_mode_auto" : "live_mode_manual");
	}

	private void refreshLists() {
		for (final ClickableWidget widget : listWidgets) {
			remove(widget);
		}
		listWidgets.clear();
		pageCount = 1;
		platformsHint = false;
		// the filter only applies to the manual list; in Automatic mode "Found: ..." is drawn in its place
		filterField.visible = mode == StationAssociationMode.MANUAL;
		if (mode != StationAssociationMode.MANUAL) {
			return;
		}
		final List<StationReference> stations = new ArrayList<>();
		for (final StationReference station : StationData.provider().listStations(512)) {
			if (filter.isEmpty() || station.displayName().toLowerCase(Locale.ROOT).contains(filter)) {
				stations.add(station);
			}
		}
		pageCount = Math.max(1, (stations.size() + STATION_ROWS - 1) / STATION_ROWS);
		stationPage = Math.max(0, Math.min(stationPage, pageCount - 1));
		for (int i = 0; i < STATION_ROWS; i++) {
			final int index = stationPage * STATION_ROWS + i;
			if (index >= stations.size()) {
				break;
			}
			final StationReference station = stations.get(index);
			final String label = (station.id() == stationId ? "> " : "") + station.displayName();
			track(ButtonWidget.builder(Text.literal(label), button -> {
				stationId = station.id();
				platformIds.clear();
				refreshLists();
			}).dimensions(rightX, listY + i * 20, COLUMN_WIDTH, 18).build());
		}
		final int pagerY = listY + STATION_ROWS * 20 + 2;
		track(ButtonWidget.builder(Text.literal("<"), button -> {
			stationPage--;
			refreshLists();
		}).dimensions(rightX, pagerY, 30, 18).build()).active = stationPage > 0;
		track(ButtonWidget.builder(Text.literal(">"), button -> {
			stationPage++;
			refreshLists();
		}).dimensions(rightX + COLUMN_WIDTH - 30, pagerY, 30, 18).build()).active = stationPage < pageCount - 1;

		if (stationId != 0) {
			final List<PlatformReference> platforms = StationData.provider().listPlatforms(stationId);
			platformsHint = true;
			final int platformsY = pagerY + 34;
			final int count = Math.min(StationAssociation.MAX_PLATFORMS, platforms.size());
			for (int i = 0; i < count; i++) {
				final PlatformReference platform = platforms.get(i);
				final boolean selected = platformIds.contains(platform.id());
				track(ButtonWidget.builder(Text.literal((selected ? "[x] " : "[ ] ") + platform.name()), button -> {
					if (platformIds.contains(platform.id())) {
						platformIds.remove(platform.id());
					} else if (platformIds.size() < StationAssociation.MAX_PLATFORMS) {
						platformIds.add(platform.id());
					}
					refreshLists();
				}).dimensions(rightX + (i % 2) * (COLUMN_WIDTH / 2), platformsY + (i / 2) * 20, COLUMN_WIDTH / 2 - 2, 18).build());
			}
		}
	}

	private <T extends ClickableWidget> T track(T widget) {
		listWidgets.add(widget);
		return addDrawableChild(widget);
	}

	protected void addDone() {
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(width / 2 - 100, height - 28, 200, 20).build());
	}

	/**
	 * Sends the edited configuration to the server. Called once when the screen closes.
	 */
	protected abstract void save();

	protected abstract boolean stillValid();

	@Override
	public void tick() {
		if (!stillValid()) {
			sent = true;
			close();
		}
	}

	@Override
	public void removed() {
		if (!sent) {
			sent = true;
			save();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, Math.max(6, topY() - 16), 0xFFFFFF);
		super.render(context, mouseX, mouseY, delta);
		if (mode == StationAssociationMode.AUTO) {
			final StationSnapshot snapshot = StationData.provider().resolve(pos, StationAssociation.AUTO, false);
			final Text found = snapshot.station() == null ? tr("live_auto_none") : tr("live_auto_found", snapshot.station().displayName());
			context.drawTextWithShadow(textRenderer, found, rightX, topY() + 28, 0xB0B0B0);
		} else {
			final int pagerY = listY + STATION_ROWS * 20 + 2;
			context.drawCenteredTextWithShadow(textRenderer, Text.literal((stationPage + 1) + "/" + pageCount), rightX + COLUMN_WIDTH / 2, pagerY + 5, 0xFFFFFF);
			if (platformsHint) {
				context.drawTextWithShadow(textRenderer, tr("live_platforms_hint"), rightX, pagerY + 22, 0xB0B0B0);
			}
		}
	}
}
