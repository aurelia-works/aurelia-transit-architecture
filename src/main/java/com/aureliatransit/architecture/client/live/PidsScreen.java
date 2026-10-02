package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.live.BoardAlignment;
import com.aureliatransit.architecture.live.DisplayConfig;
import com.aureliatransit.architecture.live.DisplayKind;
import com.aureliatransit.architecture.live.DisplayStyle;
import com.aureliatransit.architecture.live.LiveSystems;
import com.aureliatransit.architecture.live.PidsBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Configuration screen of a live display: style, rows, clock, calling-at, page time, and the station picker.
 */
final class PidsScreen extends LiveConfigScreen {

	private static final int[] PAGE_SECONDS = {3, 4, 5, 6, 8, 10, 15, 20};

	private final PidsBlockEntity display;
	private final DisplayKind kind;
	private DisplayStyle style;
	private int rows;
	private boolean clock;
	private boolean callingAt;
	private boolean callingTimes;
	private boolean arrivals;
	private boolean summary;
	private int pageSeconds;
	private BoardAlignment alignment;
	private String message;

	PidsScreen(BlockPos pos, PidsBlockEntity display) {
		super(tr("live_display_title"), pos, display.config().association());
		this.display = display;
		this.kind = display.kind();
		final DisplayConfig config = display.config();
		this.style = config.style();
		this.rows = config.rows();
		this.clock = config.clock();
		this.callingAt = config.callingAt();
		this.callingTimes = config.callingTimes();
		this.arrivals = config.arrivals();
		this.summary = config.summary();
		this.pageSeconds = config.pageSeconds();
		this.alignment = config.alignment();
		this.message = config.message();
	}

	@Override
	protected void init() {
		final int x = leftX();
		int y = topY();
		add(ButtonWidget.builder(styleText(), button -> {
			style = style.next();
			button.setMessage(styleText());
		}).dimensions(x, y, COLUMN_WIDTH, 20).build());
		y += 24;
		if (kind.hasRowChoice()) {
			add(ButtonWidget.builder(rowsText(), button -> {
				rows = rows >= kind.maxRows() ? kind.minRows() : rows + 1;
				button.setMessage(rowsText());
			}).dimensions(x, y, COLUMN_WIDTH, 20).build());
			y += 24;
		}
		add(ButtonWidget.builder(toggleText("live_clock", clock), button -> {
			clock = !clock;
			button.setMessage(toggleText("live_clock", clock));
		}).dimensions(x, y, COLUMN_WIDTH, 20).build());
		y += 24;
		if (kind != DisplayKind.CONCOURSE) {
			final int half = (COLUMN_WIDTH - 4) / 2;
			add(ButtonWidget.builder(toggleText("live_calling_at", callingAt), button -> {
				callingAt = !callingAt;
				button.setMessage(toggleText("live_calling_at", callingAt));
			}).dimensions(x, y, half, 20).build());
			// calling-point times (A14): off by default, it widens the arrivals request by the calling points' platforms
			add(ButtonWidget.builder(toggleText("live_calling_times", callingTimes), button -> {
				callingTimes = !callingTimes;
				button.setMessage(toggleText("live_calling_times", callingTimes));
			}).dimensions(x + half + 4, y, COLUMN_WIDTH - half - 4, 20).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(tr("live_calling_times.tip"))).build());
			y += 24;
		} else {
			// concourse board (1.4): departures or arrivals, and the station summary line
			final int half = (COLUMN_WIDTH - 4) / 2;
			add(ButtonWidget.builder(modeText(), button -> {
				arrivals = !arrivals;
				button.setMessage(modeText());
			}).dimensions(x, y, half, 20).build());
			add(ButtonWidget.builder(toggleText("live_summary", summary), button -> {
				summary = !summary;
				button.setMessage(toggleText("live_summary", summary));
			}).dimensions(x + half + 4, y, COLUMN_WIDTH - half - 4, 20).build());
			y += 24;
		}
		add(ButtonWidget.builder(pageText(), button -> {
			int index = 0;
			for (int i = 0; i < PAGE_SECONDS.length; i++) {
				if (PAGE_SECONDS[i] == pageSeconds) {
					index = i;
				}
			}
			pageSeconds = PAGE_SECONDS[(index + 1) % PAGE_SECONDS.length];
			button.setMessage(pageText());
		}).dimensions(x, y, COLUMN_WIDTH, 20).build());
		y += 24;
		buildAssociationWidgets();
		// local service message (1.2): shown in a strip on this display, overriding station and network messages
		final TextFieldWidget messageField = add(new TextFieldWidget(textRenderer, x, y + 24, COLUMN_WIDTH, 18, Text.literal("Message")));
		messageField.setMaxLength(DisplayConfig.MAX_MESSAGE);
		messageField.setPlaceholder(Text.literal("Service message (optional)"));
		messageField.setText(message);
		messageField.setChangedListener(text -> message = text);
		addDone();
		add(ButtonWidget.builder(alignmentText(), button -> {
			alignment = alignment == BoardAlignment.TOP ? BoardAlignment.CENTER : BoardAlignment.TOP;
			button.setMessage(alignmentText());
		}).dimensions(x, y, COLUMN_WIDTH, 20).build());
	}

	private Text alignmentText() {
		return tr("live_alignment", tr("live_alignment." + alignment.name().toLowerCase(java.util.Locale.ROOT)));
	}

	private Text styleText() {
		return tr("live_style", Text.translatable("screen.aurelia_transit_architecture." + style.translationKey()));
	}

	private Text modeText() {
		return tr(arrivals ? "live_mode_arrivals" : "live_mode_departures");
	}

	private Text rowsText() {
		return tr("live_rows", rows);
	}

	private Text pageText() {
		return tr("live_page_seconds", pageSeconds);
	}

	private static Text toggleText(String key, boolean on) {
		return tr(key, on ? Text.translatable("options.on") : Text.translatable("options.off"));
	}

	@Override
	protected boolean stillValid() {
		return !display.isRemoved();
	}

	@Override
	protected void save() {
		final DisplayConfig config = new DisplayConfig(association(), style, rows, clock, callingAt, pageSeconds, alignment, message, callingTimes, arrivals, summary);
		if (!config.equals(display.config())) {
			ClientPlayNetworking.send(LiveSystems.UPDATE_DISPLAY, LiveSystems.writeDisplayUpdate(pos, config));
		}
	}
}
