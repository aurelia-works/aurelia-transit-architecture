package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.client.wayfinding.logic.ClientServiceMessages;
import com.aureliatransit.architecture.live.DisplayConfig;
import com.aureliatransit.architecture.live.DisplayKind;
import com.aureliatransit.architecture.live.DisplayStyle;
import com.aureliatransit.architecture.live.display.BoardAnchor;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientStationSuffixes;
import com.aureliatransit.architecture.live.display.BoardSummary;
import com.aureliatransit.architecture.live.display.CallingPages;
import com.aureliatransit.architecture.live.display.CallingTimes;
import com.aureliatransit.architecture.live.display.DepartureText;
import com.aureliatransit.architecture.live.display.Marquee;
import com.aureliatransit.architecture.live.display.Pagination;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.text.Tr;
import com.aureliatransit.architecture.wayfinding.MessageRotation;
import com.aureliatransit.architecture.wayfinding.ServiceMessage;
import com.aureliatransit.architecture.wayfinding.SuffixContext;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a display out into a {@link BoardModel}. Called at most about once a second per visible display (faster only
 * while a marquee is scrolling); the renderer replays the result every frame.
 */
final class BoardBuilder {

	static final float FRAME = 1F / 16F;
	private static final float MIN_SQUEEZE = 0.65F;
	private static final float MARGIN = 3F;
	/** Gap above the first row of a top-anchored board: half the side margin, so the bezel looks even. */
	private static final float TOP_MARGIN = MARGIN / 2;
	private static final long MARQUEE_STEP_MILLIS = 350;
	private static final long MARQUEE_HOLD_MILLIS = 1500;
	/** Approximate average glyph advance in font units, used to budget characters of calling-at pages. */
	private static final float AVG_CHAR = 5.2F;
	/** Height (virtual units) of the service-message strip at the bottom of a board, reserved only while a message exists. */
	private static final float MESSAGE_STRIP_H = 10F;
	private static final float COLUMN_HEADING_H = 7F;
	private static final float SUMMARY_H = 8F;
	private static final float STRIP_TEXT_SCALE = 0.85F;
	/** The message strip is squeezed at most this much before it scrolls instead, so text never gets unreadably narrow. */
	private static final float STRIP_MIN_SQUEEZE = 0.85F;

	private BoardBuilder() {
	}

	/**
	 * @param nearestPlatformId when non-zero, only this platform's services are shown (AUTO platform displays)
	 * @param topInsetPixels    pixels of the top block edge not usable by the screen (hanging rods)
	 */
	static void build(BoardModel m, TextRenderer tr, DisplayKind kind, DisplayConfig config, StationSnapshot snapshot, long nearestPlatformId,
					  int widthBlocks, int heightBlocks, float topInsetPixels, long now, String clock) {
		m.reset();
		final DisplayStyle style = config.style();
		final boolean header = config.clock() || kind == DisplayKind.CONCOURSE;
		final boolean sub = config.callingAt() && kind != DisplayKind.CONCOURSE;
		final float k = kind.textScale();
		final float mainH = 10 * k;
		final float subH = sub ? 7.5F : 0;
		final float rowH = mainH + subH;
		final float headerH = header ? 11 : 0;
		final int rows = config.rows();
		// service message (local > station > network): its strip height is part of the content so rows never overlap it
		final List<ServiceMessage> messages = ClientServiceMessages.applicable(config.message(), snapshot.station() == null ? "" : snapshot.station().displayName());
		final float stripH = messages.isEmpty() ? 0 : MESSAGE_STRIP_H;
		// concourse boards (1.4): a column heading row, and optionally a station summary line under the rows
		final boolean concourse = kind == DisplayKind.CONCOURSE;
		final boolean arrivals = concourse && config.arrivals();
		final float columnsH = concourse ? COLUMN_HEADING_H : 0;
		final float summaryH = concourse && config.summary() ? SUMMARY_H : 0;
		final float contentH = headerH + columnsH + rows * rowH + 1.5F + summaryH + stripH;

		final float boardW = widthBlocks - 2 * FRAME;
		final float boardH = heightBlocks - 2 * FRAME - topInsetPixels / 16F;
		final float minWidth = kind == DisplayKind.CIS ? 96 : 112;
		final float scale = Math.min(boardH / contentH, boardW / minWidth);
		final float vw = boardW / scale;
		final float vh = boardH / scale;
		m.scale = scale;
		m.width = vw;
		m.height = vh;
		m.rect(0, 0, vw, vh, style.background(), BoardModel.LAYER_BACKGROUND);

		final float y0 = BoardAnchor.contentTop(config.alignment(), vh, contentH, TOP_MARGIN);
		final String stationName = snapshot.station() == null ? "" : ClientStationSuffixes.apply(snapshot.station().displayName(), SuffixContext.DISPLAYS);

		final List<ServiceSnapshot> services = arrivals ? arrivals(snapshot, stationName(snapshot), now) : visible(kind, snapshot, nearestPlatformId, now);
		if (header) {
			m.rect(0, y0, vw, headerH, style.header(), BoardModel.LAYER_BAND);
			final String title = !concourse ? stationName : arrivals ? (stationName.isEmpty() ? Tr.t("board_arrivals") : Tr.t("board_arrivals_at", stationName))
					: (stationName.isEmpty() ? Tr.t("board_departures") : Tr.t("board_departures_at", stationName));
			float right = vw - MARGIN;
			if (config.clock()) {
				final float cw = tr.getWidth(clock) * 0.9F;
				m.text(clock, right - cw, y0 + (headerH - 7.2F) / 2, 0.9F, 1, style.accent());
				right -= cw + 4;
			}
			final int headerPages = kind == DisplayKind.CONCOURSE ? Pagination.pageCount(services.size(), rows) : 1;
			if (headerPages > 1) {
				final String indicator = (Pagination.currentPage(now, config.pageSeconds() * 1000L, headerPages) + 1) + "/" + headerPages;
				final float iw = tr.getWidth(indicator) * 0.9F;
				m.text(indicator, right - iw, y0 + (headerH - 7.2F) / 2, 0.9F, 1, style.dim());
				right -= iw + 4;
			}
			fit(m, tr, title, MARGIN, y0 + (headerH - 7.2F) / 2, 0.9F, right - MARGIN - 2, style.text(), now);
		}

		final float rowsTop = y0 + headerH + columnsH;
		messageStrip(m, tr, messages, vw, vh, style, now);
		if (summaryH > 0) {
			summary(m, tr, snapshot, nearestPlatformId, rowsTop + rows * rowH + 1, vw, style, now);
		}
		if (services.isEmpty()) {
			idle(m, tr, kind, snapshot, stationName, rowsTop, rows * rowH, vw, style, k, now);
			return;
		}

		final int pages = kind == DisplayKind.CONCOURSE ? Pagination.pageCount(services.size(), rows) : 1;
		final int page = Pagination.currentPage(now, config.pageSeconds() * 1000L, pages);
		final int from = Pagination.firstIndex(page, rows);
		final int to = Math.min(Pagination.endIndex(page, rows, services.size()), from + rows);

		// column geometry from the widest status text and platform name on this page
		float statusWidth = 0;
		float platformWidth = 0;
		boolean multiplePlatforms = kind == DisplayKind.CONCOURSE;
		long firstPlatform = services.get(from).platformId();
		for (int i = from; i < to; i++) {
			final ServiceSnapshot s = services.get(i);
			statusWidth = Math.max(statusWidth, tr.getWidth(DepartureText.status(s, now)) * k);
			platformWidth = Math.max(platformWidth, tr.getWidth(s.platformName()) * k * 0.9F);
			multiplePlatforms |= s.platformId() != firstPlatform;
		}
		final float left = MARGIN;
		final float right = vw - MARGIN;
		final float chipW = vw >= 100 ? 15 * k : 0;
		final float platW = multiplePlatforms ? Math.max(9 * k, platformWidth + 4) : 0;
		final float destX = left + (chipW > 0 ? chipW + 3 : 0);
		final float destEnd = right - statusWidth - 4 - (platW > 0 ? platW + 3 : 0);
		if (columnsH > 0) {
			// explicit column headings: Destination (or From), Platform, Time
			final float hk = 0.6F;
			final float hy = rowsTop - columnsH + (columnsH - 8 * hk) / 2;
			m.text(Tr.t(arrivals ? "board_col_from" : "board_col_destination"), destX, hy, hk, 1, style.dim());
			if (platW > 0) {
				final String plat = tr.trimToWidth(Tr.t("board_col_platform"), (int) ((platW + 3 + statusWidth) / hk));
				m.text(plat, right - statusWidth - 4 - platW, hy, hk, 1, style.dim());
			}
			final String time = Tr.t("board_col_time");
			m.text(time, right - tr.getWidth(time) * hk, hy, hk, 1, style.dim());
		}

		for (int i = from; i < to; i++) {
			final ServiceSnapshot s = services.get(i);
			final float top = rowsTop + (i - from) * rowH;
			if (rows > 1 && ((i - from) & 1) == 0) {
				m.rect(0, top, vw, rowH, style.rowBand(), BoardModel.LAYER_BAND);
			}
			final float textY = top + (mainH - 8 * k) / 2 + 0.3F;
			final int delay = DepartureText.delayMinutes(s);

			if (chipW > 0) {
				final String label = s.routeNumber().isBlank() ? s.routeName() : s.routeNumber();
				final int routeRgb = 0xFF000000 | s.routeColor();
				final float chipH = 9 * k;
				m.rect(left, top + (mainH - chipH) / 2, chipW, chipH, routeRgb, BoardModel.LAYER_CHIP);
				if (!label.isEmpty()) {
					final int textColor = luminance(routeRgb) > 0.6F ? 0xFF101010 : 0xFFFFFFFF;
					final float lk = k * 0.8F;
					String shown = label;
					float lw = tr.getWidth(shown) * lk;
					float sx = 1;
					if (lw > chipW - 2) {
						sx = Math.max(0.5F, (chipW - 2) / lw);
						if (lw * sx > chipW - 2) {
							shown = tr.trimToWidth(shown, (int) ((chipW - 2) / (lk * sx)));
							lw = tr.getWidth(shown) * lk;
						}
					}
					m.text(shown, left + (chipW - lw * sx) / 2, top + (mainH - 8 * lk) / 2 + 0.3F, lk, sx, textColor);
				}
			}

			// no origin from MTR: the From cell stays empty rather than showing something else under that heading
			final String place = arrivals ? s.origin() : s.destination();
			fit(m, tr, ClientStationSuffixes.apply(place, SuffixContext.DISPLAYS), destX, textY, k, destEnd - destX, style.text(), now);

			if (platW > 0) {
				final float chipH = 9 * k;
				final float px = right - statusWidth - 4 - platW;
				m.rect(px, top + (mainH - chipH) / 2, platW, chipH, style.chip(), BoardModel.LAYER_CHIP);
				final float pk = k * 0.9F;
				final float pw = tr.getWidth(s.platformName()) * pk;
				m.text(s.platformName(), px + (platW - pw) / 2, top + (mainH - 8 * pk) / 2 + 0.3F, pk, 1, style.text());
			}

			final String status = DepartureText.status(s, now);
			final int statusColor = delay > 0 ? style.delay() : style.accent();
			m.text(status, right - tr.getWidth(status) * k, textY, k, 1, statusColor);

			if (sub) {
				subLine(m, tr, s, delay, destX, top + mainH - 0.5F, destEnd + statusWidth + 4 - destX, style, config.pageSeconds(), now);
			}
		}
	}

	/**
	 * The service-message strip along the bottom edge of the board ({@code vw} x {@link #MESSAGE_STRIP_H}); nothing when
	 * there are no messages. The caller has already included {@link #MESSAGE_STRIP_H} in the content height, so rows end
	 * above the strip. One message is shown at a time; several rotate by wall-clock time ({@link MessageRotation}), a
	 * single message never rotates. Text is kept readable: at most a mild squeeze, otherwise it scrolls one character at
	 * a time inside its slot, restarting from the beginning for every message.
	 */
	private static void messageStrip(BoardModel m, TextRenderer tr, List<ServiceMessage> messages, float vw, float vh, DisplayStyle style, long now) {
		if (messages.isEmpty()) {
			return;
		}
		final float k = STRIP_TEXT_SCALE;
		final float avail = vw - 2 * MARGIN;
		final int count = messages.size();
		final String[] texts = new String[count];
		final int[] overflow = new int[count];
		final long[] slots = new long[count];
		for (int i = 0; i < count; i++) {
			texts[i] = stripText(messages.get(i));
			overflow[i] = stripOverflow(tr, texts[i], k, avail);
			slots[i] = MessageRotation.slotMillis(overflow[i]);
		}
		final int current = MessageRotation.index(now, slots);
		final ServiceMessage message = messages.get(current);
		final int band;
		final int color;
		switch (message.severity()) {
			case WARNING -> {
				band = 0xFF3A2A00;
				color = 0xFFFFC640;
			}
			case DISRUPTION -> {
				band = 0xFF8E1B16;
				color = 0xFFFFFFFF;
			}
			case SEVERE -> {
				band = 0xFFC2160E;
				color = 0xFFFFFFFF;
			}
			default -> {
				band = style.header();
				color = style.accent();
			}
		}
		final float top = vh - MESSAGE_STRIP_H;
		m.rect(0, top, vw, MESSAGE_STRIP_H, band, BoardModel.LAYER_BAND);
		final float y = top + (MESSAGE_STRIP_H - 8 * k) / 2 + 0.3F;
		final String text = texts[current];
		final float full = tr.getWidth(text) * k;
		if (full <= avail) {
			m.text(text, MARGIN, y, k, 1, color);
		} else if (overflow[current] == 0) {
			m.text(text, MARGIN, y, k, avail / full, color);
		} else {
			final float availUnits = avail / (k * STRIP_MIN_SQUEEZE);
			final int start = MessageRotation.scrollStart(MessageRotation.elapsedInSlot(now, slots), overflow[current]);
			final String window = tr.trimToWidth(text.substring(start), (int) availUnits);
			m.stripScroll = true;
			m.text(window, MARGIN, y, k, STRIP_MIN_SQUEEZE, color);
		}
	}

	private static String stripText(ServiceMessage message) {
		return switch (message.severity()) {
			case INFO, WARNING -> message.severity() == com.aureliatransit.architecture.wayfinding.MessageSeverity.INFO ? message.text() : "! " + message.text();
			case DISRUPTION -> "! " + message.text();
			case SEVERE -> "!! " + message.text();
		};
	}

	/** Characters by which the text overflows the strip even after the mild squeeze; 0 when it fits (squeezed or not). */
	private static int stripOverflow(TextRenderer tr, String text, float k, float avail) {
		final int width = tr.getWidth(text);
		if (width * k * STRIP_MIN_SQUEEZE <= avail) {
			return 0;
		}
		final float availUnits = avail / (k * STRIP_MIN_SQUEEZE);
		final int length = text.length();
		final int visibleChars = Math.max(1, (int) (length * availUnits / width));
		return Math.max(1, length - visibleChars);
	}

	/**
	 * Arrivals board: every train that has not left yet, except those starting here (they do not arrive), in arrival
	 * order. "From" is MTR's first stop of the route.
	 */
	private static List<ServiceSnapshot> arrivals(StationSnapshot snapshot, String plainStation, long now) {
		final List<ServiceSnapshot> out = new ArrayList<>(snapshot.services().size());
		for (final ServiceSnapshot s : snapshot.services()) {
			// trains starting here do not arrive; a circular route's train ending here does
			if (s.departureMillis() >= now && !(s.origin().equals(plainStation) && !plainStation.isEmpty() && !s.terminating())) {
				out.add(s);
			}
		}
		return out;
	}

	private static String stationName(StationSnapshot snapshot) {
		return snapshot.station() == null ? "" : snapshot.station().displayName();
	}

	/** Station summary (1.4): "N platforms", and "This is platform X" when a platform is beside the board. */
	private static void summary(BoardModel m, TextRenderer tr, StationSnapshot snapshot, long nearestPlatformId, float y, float vw, DisplayStyle style, long now) {
		final String text = BoardSummary.text(snapshot.platforms().size(), BoardSummary.platformName(snapshot.platforms(), nearestPlatformId));
		if (!text.isEmpty()) {
			fit(m, tr, text, MARGIN, y + (SUMMARY_H - 8 * 0.7F) / 2, 0.7F, vw - 2 * MARGIN, style.dim(), now);
		}
	}

	private static List<ServiceSnapshot> visible(DisplayKind kind, StationSnapshot snapshot, long nearestPlatformId, long now) {
		final List<ServiceSnapshot> out = new ArrayList<>(snapshot.services().size());
		for (final ServiceSnapshot s : snapshot.services()) {
			if (s.departureMillis() < now) {
				continue;
			}
			if (nearestPlatformId != 0 && !kind.stationWide() && s.platformId() != nearestPlatformId) {
				continue;
			}
			out.add(s);
		}
		return out;
	}

	private static void subLine(BoardModel m, TextRenderer tr, ServiceSnapshot s, int delayMinutes, float x, float y, float avail, DisplayStyle style, int pageSeconds, long now) {
		final float sk = 0.75F;
		final List<String> pages = new ArrayList<>(4);
		if (s.terminating()) {
			pages.add(Tr.t("board_terminates"));
		} else {
			if (delayMinutes > 0) {
				pages.add(Tr.t(delayMinutes == 1 ? "board_delayed_one" : "board_delayed_many", delayMinutes));
			}
			final List<String> stops = s.callingAt().stream().map(name -> ClientStationSuffixes.apply(name, SuffixContext.DISPLAYS)).toList();
			pages.addAll(CallingPages.paginate(s.callingAtMillis().isEmpty() ? stops : CallingTimes.labels(stops, s.callingAtMillis(), now),
					(int) (avail / (AVG_CHAR * sk))));
		}
		if (pages.isEmpty()) {
			return;
		}
		final int index = Pagination.currentPage(now, pageSeconds * 1000L, pages.size());
		final boolean delayPage = !s.terminating() && delayMinutes > 0 && index == 0;
		fit(m, tr, pages.get(index), x, y, sk, avail, delayPage ? style.delay() : style.dim(), now);
	}

	private static void idle(BoardModel m, TextRenderer tr, DisplayKind kind, StationSnapshot snapshot, String stationName, float top, float height, float vw, DisplayStyle style, float k, long now) {
		final String first;
		final String second;
		if (snapshot.station() == null) {
			first = Tr.t("term_no_station");
			second = Tr.t("board_configure");
		} else {
			first = Tr.t("board_welcome", stationName);
			second = Tr.t("term_no_departures");
		}
		final float fk = Math.max(1F, k);
		final float sk = Math.max(0.75F, k * 0.75F);
		final float blockH = 8 * fk + 2 + 8 * sk;
		final float y = top + Math.max(0, (height - blockH) / 2);
		centred(m, tr, first, vw, y, fk, style.text(), now);
		centred(m, tr, second, vw, y + 8 * fk + 2, sk, style.dim(), now);
	}

	private static void centred(BoardModel m, TextRenderer tr, String text, float vw, float y, float k, int color, long now) {
		final float avail = vw - 2 * MARGIN;
		final float w = tr.getWidth(text) * k;
		if (w <= avail) {
			m.text(text, (vw - w) / 2, y, k, 1, color);
		} else {
			fit(m, tr, text, MARGIN, y, k, avail, color, now);
		}
	}

	/**
	 * Left-aligned text in a column: squeezed horizontally down to {@link #MIN_SQUEEZE}, scrolled beyond that.
	 */
	private static void fit(BoardModel m, TextRenderer tr, String text, float x, float y, float k, float avail, int color, long now) {
		if (text.isEmpty() || avail <= 0) {
			return;
		}
		final int width = tr.getWidth(text);
		final float full = width * k;
		if (full <= avail) {
			m.text(text, x, y, k, 1, color);
			return;
		}
		final float squeeze = avail / full;
		if (squeeze >= MIN_SQUEEZE) {
			m.text(text, x, y, k, squeeze, color);
			return;
		}
		final float availUnits = avail / (k * MIN_SQUEEZE);
		final int length = text.length();
		final int visibleChars = Math.max(1, (int) (length * availUnits / width));
		final int start = Marquee.startIndex(now, length, visibleChars, MARQUEE_STEP_MILLIS, MARQUEE_HOLD_MILLIS);
		final String window = tr.trimToWidth(text.substring(start, Math.min(length, start + visibleChars + 2)), (int) availUnits);
		m.marquee = true;
		m.text(window, x, y, k, MIN_SQUEEZE, color);
	}

	private static float luminance(int argb) {
		final float r = ((argb >> 16) & 0xFF) / 255F;
		final float g = ((argb >> 8) & 0xFF) / 255F;
		final float b = (argb & 0xFF) / 255F;
		return 0.2126F * r + 0.7152F * g + 0.0722F * b;
	}
}
