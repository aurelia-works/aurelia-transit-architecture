package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientServiceMessages;
import com.aureliatransit.architecture.live.DisplayStyle;
import com.aureliatransit.architecture.terminal.MapLine;
import com.aureliatransit.architecture.terminal.StationInfo;
import com.aureliatransit.architecture.terminal.SystemMap;
import com.aureliatransit.architecture.terminal.Terminals;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.text.Tr;
import com.aureliatransit.architecture.wayfinding.MessageRotation;
import com.aureliatransit.architecture.wayfinding.ServiceMessage;
import com.aureliatransit.architecture.wayfinding.ServiceMessages;
import com.aureliatransit.architecture.wayfinding.Wayfinding;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The passenger information terminal UI: a dark "information OS" with large tabs (home, departures, system map,
 * station, service info, accessibility). It consumes the shared model only: departures from the cached station data
 * provider, everything else from the {@link Terminals} source and the synced service messages. Departures refresh at
 * most once a second while open; map and station information are fetched on open and then at most every 10 seconds.
 * Drawing replays cached rows; nothing runs when the screen is closed and nothing is registered globally.
 */
public class PassengerTerminalScreen extends Screen {

	private static final DisplayStyle STYLE = DisplayStyle.EUROPEAN_MODERN;
	private static final long DEPARTURES_MILLIS = 1000;
	private static final long INFO_MILLIS = 10_000;
	private static final int SIDEBAR = 84;
	private static final int DEPARTURE_ROW = 16;
	private static final int TEXT_ROW = 10;
	private static final String KEY = "screen." + AureliaTransitArchitecture.MOD_ID + ".";

	private final BlockPos pos;
	private final WayfindingSignBlockEntity terminal;

	private TerminalPage page = TerminalPage.HOME;
	private int subPage;
	private StationSnapshot snapshot = StationSnapshot.EMPTY;
	private StationInfo info = StationInfo.EMPTY;
	private SystemMap map = SystemMap.EMPTY;
	private long nextDepartures;
	private long nextInfo;
	private boolean dirty = true;

	// geometry (set in init)
	private int left;
	private int top;
	private int panelW;
	private int panelH;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;

	// cached rows of the current page
	private List<TerminalFeed.Row> departureRows = List.of();
	private final List<Row> textRows = new ArrayList<>();
	private List<MapLayout.Strip> strips = List.of();
	private int pageCount = 1;
	private String clock = "";
	private List<ServiceMessage> homeMessages = List.of();
	private long[] homeSlots = new long[0];
	private ButtonWidget prevButton;
	private ButtonWidget nextButton;

	private record Row(OrderedText text, int argb) {
	}

	public PassengerTerminalScreen(BlockPos pos, WayfindingSignBlockEntity terminal) {
		super(Text.translatable(KEY + "term_title"));
		this.pos = pos;
		this.terminal = terminal;
	}

	@Override
	protected void init() {
		panelW = Math.min(width - 12, 360);
		panelH = Math.min(height - 12, 214);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		contentX = left + SIDEBAR + 8;
		contentY = top + 28;
		contentW = left + panelW - 8 - contentX;
		contentH = top + panelH - 28 - contentY;

		final TerminalPage[] pages = TerminalPage.values();
		final int buttonH = Math.max(16, Math.min(30, (panelH - 36) / pages.length - 2));
		for (final TerminalPage tab : pages) {
			addDrawableChild(new TabButton(left + 6, top + 28 + tab.ordinal() * (buttonH + 2), SIDEBAR - 4, buttonH, tab));
		}
		final int pagerY = top + panelH - 24;
		prevButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> turn(-1)).dimensions(contentX, pagerY, 40, 18).build());
		nextButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> turn(1)).dimensions(contentX + contentW - 40, pagerY, 40, 18).build());
		nextDepartures = 0;
		nextInfo = 0;
		dirty = true;
	}

	private void turn(int delta) {
		subPage = TerminalPaging.clamp(subPage + delta, pageCount);
		dirty = true;
	}

	private void select(TerminalPage tab) {
		page = tab;
		subPage = 0;
		dirty = true;
	}

	@Override
	public void tick() {
		if (terminal.isRemoved()) {
			close();
			return;
		}
		final long now = System.currentTimeMillis();
		if (now >= nextDepartures) {
			nextDepartures = now + DEPARTURES_MILLIS;
			snapshot = StationData.provider().resolve(pos, terminal.getWayfinding().autoStation() ? terminal.getWayfinding().association() : StationAssociation.AUTO, true);
			clock = clock();
			dirty = true;
		}
		if (now >= nextInfo) {
			nextInfo = now + INFO_MILLIS;
			final WayfindingData data = terminal.getWayfinding();
			info = Terminals.source().stationInfo(pos, data);
			final long stationId = snapshot.station() == null ? 0 : snapshot.station().id();
			map = Terminals.source().systemMap(stationId);
			dirty = true;
		}
	}

	private String clock() {
		final long ticks = Math.floorMod(terminal.getWorld() == null ? 0 : terminal.getWorld().getTimeOfDay(), 24000L);
		final int hour = (int) ((ticks / 1000 + 6) % 24);
		final int minute = (int) ((ticks % 1000) * 60 / 1000);
		return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
	}

	private String stationName() {
		if (!info.stationName().isEmpty()) {
			return info.stationName();
		}
		final String resolved = Wayfinding.resolve(pos, terminal.getWayfinding()).stationName();
		if (!resolved.isEmpty()) {
			return resolved;
		}
		return snapshot.station() == null ? "" : snapshot.station().displayName();
	}

	// ---- cached page content --------------------------------------------------------------------------------------

	private void rebuild() {
		dirty = false;
		final long now = System.currentTimeMillis();
		final String name = stationName();
		// messages are keyed by MTR's station name; a manual name override is only the fallback (B10)
		final List<ServiceMessage> notices = ClientServiceMessages.allFor(ServiceMessages.messageStation(snapshot.station() == null ? "" : snapshot.station().displayName(), name));
		textRows.clear();
		strips = List.of();
		departureRows = List.of();
		homeMessages = notices;
		homeSlots = new long[notices.size()];
		java.util.Arrays.fill(homeSlots, MessageRotation.DWELL_MILLIS);

		switch (page) {
			case HOME -> departureRows = TerminalFeed.rows(TerminalFeed.select(snapshot, now, Math.max(1, (contentH - 34) / DEPARTURE_ROW)), now);
			case DEPARTURES -> {
				final int perPage = Math.max(1, contentH / DEPARTURE_ROW);
				final List<TerminalFeed.Row> all = TerminalFeed.rows(TerminalFeed.select(snapshot, now, StationSnapshot.MAX_SERVICES), now);
				pageCount = TerminalPaging.pageCount(all.size(), perPage);
				subPage = TerminalPaging.clamp(subPage, pageCount);
				departureRows = all.subList(TerminalPaging.first(subPage, perPage), TerminalPaging.end(subPage, perPage, all.size()));
			}
			case MAP -> {
				final int perPage = MapLayout.linesPerPage(contentH);
				pageCount = TerminalPaging.pageCount(map.lines().size(), perPage);
				subPage = TerminalPaging.clamp(subPage, pageCount);
				strips = MapLayout.layout(map, subPage, contentW, contentH, s -> Math.round(textRenderer.getWidth(s) * 0.75F));
			}
			case STATION -> paginateText(TerminalText.stationLines(info));
			case SERVICE -> paginateText(TerminalText.serviceLines(notices));
			case ACCESSIBILITY -> paginateText(TerminalText.accessibilityLines(info));
		}
		if (page == TerminalPage.HOME) {
			pageCount = 1;
			subPage = 0;
		}
		prevButton.visible = pageCount > 1;
		nextButton.visible = pageCount > 1;
		prevButton.active = subPage > 0;
		nextButton.active = subPage < pageCount - 1;
	}

	private void paginateText(List<TerminalText.Line> lines) {
		final List<Row> all = new ArrayList<>();
		for (final TerminalText.Line line : lines) {
			for (final OrderedText wrapped : textRenderer.wrapLines(Text.literal(line.text()), contentW)) {
				all.add(new Row(wrapped, colour(line.kind())));
			}
		}
		final int perPage = Math.max(1, contentH / TEXT_ROW);
		pageCount = TerminalPaging.pageCount(all.size(), perPage);
		subPage = TerminalPaging.clamp(subPage, pageCount);
		textRows.addAll(all.subList(TerminalPaging.first(subPage, perPage), TerminalPaging.end(subPage, perPage, all.size())));
	}

	private static int colour(TerminalText.Kind kind) {
		return switch (kind) {
			case HEADING -> STYLE.accent();
			case BODY, INFO -> STYLE.text();
			case DIM -> STYLE.dim();
			case WARNING -> 0xFFFFD25A;
			case DISRUPTION -> STYLE.delay();
		};
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (dirty) {
			rebuild();
		}
		renderBackground(context);
		context.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, 0xFF000000);
		context.fill(left, top, left + panelW, top + panelH, STYLE.background());
		context.fill(left, top, left + panelW, top + 22, STYLE.header());
		context.fill(left + SIDEBAR + 2, top + 24, left + SIDEBAR + 3, top + panelH - 4, STYLE.chip());

		final String name = stationName();
		final String title = name.isEmpty() ? Text.translatable(KEY + "term_title").getString() : name;
		context.drawText(textRenderer, textRenderer.trimToWidth(title, panelW - 70), left + 8, top + 7, STYLE.text(), false);
		context.drawText(textRenderer, clock, left + panelW - 8 - textRenderer.getWidth(clock), top + 7, STYLE.accent(), false);

		switch (page) {
			case HOME -> drawHome(context, name);
			case DEPARTURES -> drawDepartures(context, contentY, name);
			case MAP -> drawMap(context);
			default -> {
				for (int i = 0; i < textRows.size(); i++) {
					context.drawText(textRenderer, textRows.get(i).text(), contentX, contentY + i * TEXT_ROW, textRows.get(i).argb(), false);
				}
			}
		}
		if (pageCount > 1) {
			final String counter = (subPage + 1) + "/" + pageCount;
			context.drawText(textRenderer, counter, contentX + contentW / 2 - textRenderer.getWidth(counter) / 2, top + panelH - 19, STYLE.dim(), false);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawHome(DrawContext context, String name) {
		int y = contentY;
		context.drawText(textRenderer, Text.translatable(KEY + "term_next"), contentX, y, STYLE.dim(), false);
		y += 12;
		drawDepartures(context, y, name);
		if (!homeMessages.isEmpty()) {
			// the current notice follows the wall clock; the list itself only changes when the server syncs it
			final int index = MessageRotation.index(System.currentTimeMillis(), homeSlots);
			final ServiceMessage homeMessage = homeMessages.get(index);
			final int color = switch (homeMessage.severity()) {
				case INFO -> STYLE.text();
				case WARNING -> 0xFFFFD25A;
				case DISRUPTION, SEVERE -> STYLE.delay();
			};
			final int stripY = contentY + contentH - 22;
			context.fill(contentX - 2, stripY, contentX + contentW + 2, stripY + 22, STYLE.rowBand());
			final String counter = homeMessages.size() > 1 ? (index + 1) + "/" + homeMessages.size() : "";
			final int textW = contentW - 4 - (counter.isEmpty() ? 0 : textRenderer.getWidth(counter) + 4);
			int line = 0;
			for (final OrderedText wrapped : textRenderer.wrapLines(Text.literal(TerminalText.severityPrefix(homeMessage.severity()) + homeMessage.text()), textW)) {
				if (line >= 2) {
					break;
				}
				context.drawText(textRenderer, wrapped, contentX + 2, stripY + 3 + line * TEXT_ROW, color, false);
				line++;
			}
			if (!counter.isEmpty()) {
				context.drawText(textRenderer, counter, contentX + contentW - textRenderer.getWidth(counter), stripY + 3, STYLE.dim(), false);
			}
		}
	}

	private void drawDepartures(DrawContext context, int startY, String name) {
		if (departureRows.isEmpty()) {
			final String empty = snapshot.station() == null ? Tr.t(TerminalText.NO_STATION_KEY) : Tr.t("term_no_departures");
			context.drawText(textRenderer, empty, contentX, startY + 2, STYLE.dim(), false);
			return;
		}
		for (int i = 0; i < departureRows.size(); i++) {
			final TerminalFeed.Row row = departureRows.get(i);
			final int y = startY + i * DEPARTURE_ROW;
			if ((i & 1) == 0) {
				context.fill(contentX - 2, y, contentX + contentW + 2, y + DEPARTURE_ROW - 1, STYLE.rowBand());
			}
			final int chipW = 30;
			final int rgb = 0xFF000000 | row.routeRgb();
			context.fill(contentX, y + 2, contentX + chipW, y + DEPARTURE_ROW - 3, rgb);
			context.drawText(textRenderer, row.route(), contentX + chipW / 2 - textRenderer.getWidth(row.route()) / 2, y + 4, TerminalFace.contrast(row.routeRgb()), false);
			final int statusW = textRenderer.getWidth(row.status());
			final int right = contentX + contentW;
			context.drawText(textRenderer, row.status(), right - statusW, y + 4, row.delayed() ? STYLE.delay() : STYLE.accent(), false);
			final int platW = row.platform().isEmpty() ? 0 : Math.max(14, textRenderer.getWidth(row.platform()) + 6);
			if (platW > 0) {
				final int px = right - statusW - 6 - platW;
				context.fill(px, y + 2, px + platW, y + DEPARTURE_ROW - 3, STYLE.chip());
				context.drawText(textRenderer, row.platform(), px + platW / 2 - textRenderer.getWidth(row.platform()) / 2, y + 4, STYLE.text(), false);
			}
			final int destX = contentX + chipW + 5;
			final int destEnd = right - statusW - 10 - (platW > 0 ? platW + 4 : 0);
			context.drawText(textRenderer, textRenderer.trimToWidth(row.destination(), Math.max(0, destEnd - destX)), destX, y + 4, STYLE.text(), false);
		}
	}

	private void drawMap(DrawContext context) {
		if (map.lines().isEmpty()) {
			context.drawText(textRenderer, Text.translatable(KEY + "term_no_map"), contentX, contentY + 2, STYLE.dim(), false);
			return;
		}
		final var matrices = context.getMatrices();
		for (final MapLayout.Strip strip : strips) {
			final MapLine line = map.lines().get(strip.lineIndex());
			final LineBadge badge = line.badge();
			final int x0 = contentX;
			final int bandY = contentY + Math.round(strip.bandTop());
			final int rgb = badge.argb();
			final int badgeW = Math.max(14, textRenderer.getWidth(badge.label()) + 6);
			context.fill(x0, bandY, x0 + badgeW, bandY + 10, rgb);
			context.drawText(textRenderer, badge.label(), x0 + badgeW / 2 - textRenderer.getWidth(badge.label()) / 2, bandY + 1, TerminalFace.contrast(badge.rgb()), false);
			context.drawText(textRenderer, textRenderer.trimToWidth(line.name(), contentW - badgeW - 8), x0 + badgeW + 5, bandY + 1, STYLE.dim(), false);
			final int sy = contentY + Math.round(strip.stripY());
			context.fill(x0 + Math.round(strip.left()), sy - 1, x0 + Math.round(strip.right()), sy + 2, rgb);
			for (final MapLayout.Dot dot : strip.dots()) {
				final int dx = x0 + Math.round(dot.x());
				if (dot.current()) {
					context.fill(dx - 4, sy - 4, dx + 4, sy + 5, 0xFFFFFFFF);
					context.fill(dx - 3, sy - 3, dx + 3, sy + 4, STYLE.accent());
				} else if (dot.transfer()) {
					context.fill(dx - 3, sy - 3, dx + 3, sy + 4, 0xFFFFFFFF);
					context.fill(dx - 2, sy - 2, dx + 2, sy + 3, STYLE.background());
				} else {
					context.fill(dx - 2, sy - 2, dx + 2, sy + 3, 0xFFDDDDDD);
				}
				if (dot.label() != null) {
					matrices.push();
					final float ly = dot.labelAbove() ? sy - 13 : sy + 6;
					matrices.translate(x0 + dot.labelLeft(), ly, 0);
					matrices.scale(0.75F, 0.75F, 1);
					context.drawText(textRenderer, dot.label(), 0, 0, dot.current() ? STYLE.accent() : STYLE.text(), false);
					matrices.pop();
				}
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (pageCount > 1) {
			turn(amount < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	/** A large dark tab button. */
	private final class TabButton extends ButtonWidget {

		private final TerminalPage tab;

		TabButton(int x, int y, int w, int h, TerminalPage tab) {
			super(x, y, w, h, Text.translatable(KEY + "term_tab." + tab.id()), button -> select(tab), DEFAULT_NARRATION_SUPPLIER);
			this.tab = tab;
		}

		@Override
		public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
			final boolean selected = page == tab;
			final int fill = selected ? STYLE.accent() : isHovered() ? STYLE.chip() : STYLE.rowBand();
			context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), fill);
			final int color = selected ? 0xFF0F1215 : STYLE.text();
			context.drawText(textRenderer, getMessage(), getX() + getWidth() / 2 - textRenderer.getWidth(getMessage()) / 2, getY() + (getHeight() - 8) / 2, color, false);
		}
	}
}
