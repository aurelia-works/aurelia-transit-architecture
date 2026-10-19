package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.client.live.BoardModel.Font;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientServiceMessages;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientStationSuffixes;
import com.aureliatransit.architecture.live.DisplayConfig;
import com.aureliatransit.architecture.live.DisplayKind;
import com.aureliatransit.architecture.live.DisplayStyle;
import com.aureliatransit.architecture.live.display.BoardAnchor;
import com.aureliatransit.architecture.live.display.BoardSummary;
import com.aureliatransit.architecture.live.display.DepartureText;
import com.aureliatransit.architecture.live.display.Marquee;
import com.aureliatransit.architecture.live.display.Pagination;
import com.aureliatransit.architecture.text.Tr;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.wayfinding.ServiceMessage;
import com.aureliatransit.architecture.wayfinding.SuffixContext;
import net.minecraft.client.font.TextRenderer;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays a display out into a {@link BoardModel}. Called at most about once a second per visible display (faster only
 * while a marquee is scrolling or a seconds clock ticks); the renderer replays the result every frame.
 * <p>
 * Each {@link DisplayStyle} is its own board, laid out the way that country's boards are:
 * <ul>
 * <li>{@link DisplayStyle#UK_DOT_MATRIX}: amber LED lettering. Platform: "1st 10:42 London Euston ... On time", a
 * "Calling at:" line scrolling column by column, the 2nd and 3rd trains in turn, a clock with small seconds. Concourse:
 * Time, Destination, Plat, Expected.</li>
 * <li>{@link DisplayStyle#DUTCH_NS}: blue, white sans. Platform: time with a red "+5", train type, the destination large,
 * "via" below, the platform number in a white square, the next train along the bottom. Concourse: Departure, To / Via,
 * Platform (white squares), Train.</li>
 * <li>{@link DisplayStyle#GERMAN_DB}: blue, white sans. Platform: time, train and a white delay box on the left, the
 * stops above a large destination, the platform number on the right, following trains below and a white band scrolling
 * notices. Concourse: Time, Train, Via, Destination, Platform.</li>
 * <li>{@link DisplayStyle#METRO_COUNTDOWN}: black, line-coloured route badges, destinations and minutes to go.</li>
 * <li>{@link DisplayStyle#JAPAN_COMMUTER}: a dark panel, the platform number and an arrow at one end, the clock at the other, a black window of
 * train-type badge, destination and time between them.</li>
 * </ul>
 * Times are clock times of the real world, like MTR's own timetable; the clocks on the boards show the same time.
 */
final class BoardBuilder {

	static final float FRAME = 1F / 16F;
	private static final float MIN_SQUEEZE = 0.7F;
	private static final long MARQUEE_STEP_MILLIS = 350;
	private static final long MARQUEE_HOLD_MILLIS = 1500;
	private static final ZoneId ZONE = ZoneId.systemDefault();

	// UK dot-matrix: one LED is one virtual unit; a line is 9 LEDs (7 above the baseline) and 2 dark rows
	private static final float LED_LINE = 11;
	private static final float LED_MARGIN = 5;
	/** LED columns per second of the scrolling lines. */
	private static final float LED_SCROLL = 34;
	private static final char SMALL_DIGIT_ZERO = '';
	private static final char SMALL_COLON = '';

	// colour screens
	private static final float MARGIN = 4;
	private static final int NS_RULE = 0xFF2A4884;
	private static final int DB_RULE = 0xFF3A5DA6;
	private static final int METRO_RULE = 0xFF262626;

	private BoardBuilder() {
	}

	/**
	 * @param nearestPlatformId when non-zero, only this platform's services are shown (AUTO platform displays)
	 * @param topInsetPixels    pixels of the top block edge not usable by the screen (hanging rods)
	 */
	static void build(BoardModel m, TextRenderer tr, DisplayKind kind, DisplayConfig config, StationSnapshot snapshot, long nearestPlatformId,
					  int widthBlocks, int heightBlocks, float topInsetPixels, long now) {
		m.reset();
		final Board b = new Board(m, tr, kind, config, snapshot, nearestPlatformId, widthBlocks - 2 * FRAME,
				heightBlocks - 2 * FRAME - topInsetPixels / 16F, now);
		switch (config.style()) {
			case UK_DOT_MATRIX -> {
				if (b.concourse) {
					ukConcourse(b);
				} else {
					ukPlatform(b);
				}
			}
			case DUTCH_NS -> {
				if (b.concourse) {
					nsConcourse(b);
				} else {
					nsPlatform(b);
				}
			}
			case GERMAN_DB -> {
				if (b.concourse) {
					dbConcourse(b);
				} else {
					dbPlatform(b);
				}
			}
			case METRO_COUNTDOWN -> {
				if (b.concourse) {
					metroConcourse(b);
				} else {
					metroPlatform(b);
				}
			}
			case JAPAN_COMMUTER -> {
				if (b.concourse) {
					jpConcourse(b);
				} else {
					jpPlatform(b);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------- Japan

	/**
	 * Hanging platform board: the platform number with an arrow at the left, the clock at the right, and between them a black
	 * window of rows (train-type badge, destination, departure time).
	 */
	private static void jpPlatform(Board b) {
		final DisplayStyle st = b.style;
		final List<ServiceSnapshot> list = b.services;
		final int rows = Math.max(1, Math.min(b.config.rows(), 4));
		final float rowH = 13;
		final float bodyH = rows * rowH + 6;
		final float stripH = b.messages.isEmpty() ? 0 : 11;
		final float sideL = 26;
		final float sideR = 36;
		final float y0 = b.frame(bodyH + stripH, 180, 0);
		final float windowL = sideL;
		final float windowR = b.vw - sideR;
		// platform number and arrow
		final String here = BoardSummary.platformName(b.snapshot.platforms(), b.nearest);
		b.put("\u2190", Font.DEFAULT, MARGIN + 2, y0 + 3, 1.4F, st.text());
		if (!here.isEmpty()) {
			final float k = Math.min(2.4F, (sideL - MARGIN) / Math.max(7, b.w(here, Font.DEFAULT, 1)));
			b.put(here, Font.DEFAULT, MARGIN + (sideL - MARGIN - b.w(here, Font.DEFAULT, k)) / 2 - 1, y0 + bodyH - 7 * k - 3, k, st.text());
		}
		// the clock and the station
		final float cx = windowR + 3;
		b.m.rect(cx, y0 + 3, sideR - 6, 14, st.rowBand(), BoardModel.LAYER_BAND);
		final String clock = clock();
		b.put(clock, Font.DEFAULT, cx + (sideR - 6 - b.w(clock, Font.DEFAULT, 0.9F)) / 2, y0 + 7, 0.9F, st.text());
		if (!b.station.isEmpty()) {
			b.fit(b.station, Font.DEFAULT, cx, y0 + 20, 0.6F, sideR - 6, st.dim());
		}
		// the window
		b.m.rect(windowL, y0 + 2, windowR - windowL, bodyH - 4, st.rowBand(), BoardModel.LAYER_BAND);
		if (list.isEmpty()) {
			b.idle(Font.DEFAULT, Font.DEFAULT, y0 + 2, bodyH - 4);
			messageStrip(b, Font.DEFAULT);
			return;
		}
		final int shown = Math.min(rows, list.size());
		final float badgeW = jpBadgeColumn(b, list.subList(0, shown));
		for (int i = 0; i < shown; i++) {
			jpRow(b, list.get(i), y0 + 3 + i * rowH, rowH, windowL + 4, windowR - 4, badgeW, false);
		}
		messageStrip(b, Font.DEFAULT);
	}

	/** Departures list: time, train-type badge, destination, platform. */
	private static void jpConcourse(Board b) {
		final DisplayStyle st = b.style;
		final int rows = b.config.rows();
		final float headerH = 13;
		final float rowH = 13;
		final String summary = b.summary();
		final float summaryH = summary.isEmpty() ? 0 : 9;
		final float stripH = b.messages.isEmpty() ? 0 : 11;
		final float y0 = b.frame(headerH + rows * rowH + summaryH + stripH, 170, 0);
		final Page page = b.services.isEmpty() ? null : b.page(rows);
		b.header(y0, headerH, Font.DEFAULT, Font.DEFAULT, page);
		final float rowsTop = y0 + headerH;
		b.m.rect(0, rowsTop, b.vw, rows * rowH, st.rowBand(), BoardModel.LAYER_BAND);
		if (page == null) {
			b.idle(Font.DEFAULT, Font.DEFAULT, rowsTop, rows * rowH);
		} else {
			final float badgeW = jpBadgeColumn(b, b.services.subList(page.from, page.to));
			for (int i = page.from; i < page.to; i++) {
				jpRow(b, b.services.get(i), rowsTop + (i - page.from) * rowH, rowH, MARGIN, b.vw - MARGIN, badgeW, true);
			}
		}
		if (!summary.isEmpty()) {
			b.fit(summary, Font.DEFAULT, MARGIN, rowsTop + rows * rowH + 1.5F, 0.7F, b.vw - 2 * MARGIN, st.dim());
		}
		messageStrip(b, Font.DEFAULT);
	}

	/**
	 * One row: the badge, the destination, and the time at the right (a platform board puts it last; a concourse board first
	 * and the platform last).
	 */
	private static void jpRow(Board b, ServiceSnapshot s, float y, float h, float left, float right, float badgeW, boolean concourse) {
		final DisplayStyle st = b.style;
		final float textY = y + (h - 7) / 2;
		final String time = hhmm(scheduled(s));
		final boolean late = s.realtime() && s.deviationMillis() >= 60_000;
		final float timeW = b.w("00:00", Font.DEFAULT, 1);
		float x = left;
		float end = right;
		if (concourse) {
			b.put(time, Font.DEFAULT, x, textY, 1, late ? st.delay() : st.accent());
			x += timeW + 5;
			final String platform = s.platformName();
			if (!platform.isEmpty()) {
				final float pw = Math.max(12, b.w(platform, Font.DEFAULT, 1) + 4);
				b.m.rect(end - pw, y + (h - 10) / 2, pw, 10, st.chip(), BoardModel.LAYER_CHIP);
				b.put(platform, Font.DEFAULT, end - pw + (pw - b.w(platform, Font.DEFAULT, 1)) / 2, textY, 1, st.text());
				end -= pw + 4;
			}
		} else {
			b.put(time, Font.DEFAULT, end - timeW, textY, 1, late ? st.delay() : st.accent());
			end -= timeW + 5;
			if (late) {
				final String behind = "+" + Math.max(1, s.deviationMillis() / 60_000);
				final float lw = b.w(behind, Font.DEFAULT, 0.8F);
				b.put(behind, Font.DEFAULT, end - lw, textY + 1, 0.8F, st.delay());
				end -= lw + 4;
			}
		}
		jpBadge(b, s, x, y + (h - 10) / 2, badgeW, 10);
		b.fit(b.place(b.arrivals ? s.origin() : s.destination()), Font.DEFAULT, x + badgeW + 5, textY, 1, end - x - badgeW - 5, st.text());
	}

	private static float jpBadgeColumn(Board b, List<ServiceSnapshot> shown) {
		float widest = 14;
		for (final ServiceSnapshot s : shown) {
			widest = Math.max(widest, b.w(b.trainType(s), Font.DEFAULT, 0.9F) + 6);
		}
		return Math.min(widest, 40);
	}

	/** A rounded badge in the route's colour carrying its number or name (the train type on a Japanese board). */
	private static void jpBadge(Board b, ServiceSnapshot s, float x, float y, float w, float h) {
		final int color = s.routeColor() == 0 ? b.style.chip() : 0xFF000000 | s.routeColor();
		b.m.rect(x + 1, y, w - 2, h, color, BoardModel.LAYER_CHIP);
		b.m.rect(x, y + 1, w, h - 2, color, BoardModel.LAYER_CHIP);
		final String label = b.trainType(s);
		if (label.isEmpty()) {
			return;
		}
		final int textColor = luminance(color) > 0.6F ? 0xFF101010 : 0xFFFFFFFF;
		final String shown = b.trim(label, Font.DEFAULT, 0.9F, w - 2);
		b.put(shown, Font.DEFAULT, x + (w - b.w(shown, Font.DEFAULT, 0.9F)) / 2, y + (h - 6.3F) / 2, 0.9F, textColor);
	}

	// ---------------------------------------------------------------------------------------------------------- UK

	private static void ukPlatform(Board b) {
		final DisplayStyle st = b.style;
		final List<ServiceSnapshot> list = b.services;
		final boolean calling = b.config.callingAt();
		final int following = list.size() <= 1 ? 0 : Math.min(b.config.rows(), list.size()) - 1;
		final boolean message = !b.messages.isEmpty();
		final int lines = 1 + (calling || list.isEmpty() ? 1 : 0) + (following > 0 ? 1 : 0) + (message ? 1 : 0) + (b.config.clock() ? 1 : 0);
		float y = b.frame(Math.max(2, lines) * LED_LINE, 150, LED_LINE / 2 - 1) + 1;
		final float left = LED_MARGIN;
		final float right = b.vw - LED_MARGIN;
		if (list.isEmpty()) {
			b.centre(b.idleFirst(), Font.LED, b.vw / 2, y, 1, st.text());
			b.centre(b.idleSecond(), Font.LED, b.vw / 2, y + LED_LINE, 1, st.text());
			y += 2 * LED_LINE;
		} else {
			final float timeX = left + b.w("3rd", Font.LED, 1) + 5;
			final float destX = timeX + b.w("00:00", Font.LED, 1) + 5;
			ukTrain(b, list.get(0), 1, y, left, timeX, destX, right);
			y += LED_LINE;
			if (calling) {
				ukCalling(b, list.get(0), y, left, right);
				y += LED_LINE;
			}
			if (following > 0) {
				final int n = 1 + Pagination.currentPage(b.now, b.config.pageSeconds() * 1000L, following);
				ukTrain(b, list.get(n), n + 1, y, left, timeX, destX, right);
				y += LED_LINE;
			}
		}
		if (message) {
			ukScrollLine(b, "", joinedMessages(b.messages), y, left, right);
			y += LED_LINE;
		}
		if (b.config.clock()) {
			ukClock(b, y);
		}
	}

	/** "1st  10:42  London Euston ... On time". */
	private static void ukTrain(Board b, ServiceSnapshot s, int n, float y, float left, float timeX, float destX, float right) {
		final int c = b.style.text();
		b.put(DisplayStyle.ordinal(n), Font.LED, left, y, 1, c);
		b.put(hhmm(scheduled(s)), Font.LED, timeX, y, 1, c);
		final String status = ukStatus(s);
		final float statusW = b.w(status, Font.LED, 1);
		b.put(status, Font.LED, right - statusW, y, 1, c);
		b.put(b.trim(b.place(s.destination()), Font.LED, 1, right - statusW - 6 - destX), Font.LED, destX, y, 1, c);
	}

	private static String ukStatus(ServiceSnapshot s) {
		final int delay = DepartureText.delayMinutes(s);
		return delay > 0 ? Tr.t("board_expected", hhmm(expected(s))) : Tr.t("board_on_time");
	}

	/** "Calling at:" standing still, the stops scrolling through the rest of the line. */
	private static void ukCalling(Board b, ServiceSnapshot s, float y, float left, float right) {
		if (s.terminating()) {
			b.put(Tr.t("board_terminates"), Font.LED, left, y, 1, b.style.text());
			return;
		}
		final List<String> stops = b.stops(s);
		if (stops.isEmpty()) {
			return;
		}
		final List<String> named = new ArrayList<>(stops.size());
		for (int i = 0; i < stops.size(); i++) {
			final boolean timed = b.config.callingTimes() && i < s.callingAtMillis().size() && s.callingAtMillis().get(i) > 0;
			named.add(timed ? stops.get(i) + " (" + hhmm(s.callingAtMillis().get(i)) + ")" : stops.get(i));
		}
		final String text = named.size() == 1 ? Tr.t("board_calling_only", named.get(0)) : listed(named) + ".";
		ukScrollLine(b, Tr.t("board_calling_at"), text, y, left, right);
	}

	/** A label standing at the left and text scrolling through the rest of the line, LED column by LED column. */
	private static void ukScrollLine(Board b, String label, String text, float y, float left, float right) {
		final int c = b.style.text();
		final float x0 = label.isEmpty() ? left : left + b.w(label, Font.LED, 1) + 4;
		if (!label.isEmpty()) {
			b.m.text(label, Font.LED, left, y, 1, 1, c, BoardModel.TEXT_TOP);
		}
		// the strips either side of the window hide the letters entering and leaving it
		b.m.rect(0, y - 1, x0, LED_LINE, b.style.background(), BoardModel.LAYER_MASK);
		b.m.rect(right, y - 1, b.vw - right, LED_LINE, b.style.background(), BoardModel.LAYER_MASK);
		b.m.scroll(text, Font.LED, b.advances(text, Font.LED), x0, right, y, 1, c, LED_SCROLL, true);
	}

	/** "10:41:23", the seconds in small digits, centred along the bottom. */
	private static void ukClock(Board b, float y) {
		final LocalTime t = LocalTime.now(ZONE);
		final String main = two(t.getHour()) + ":" + two(t.getMinute());
		final String seconds = "" + SMALL_COLON + (char) (SMALL_DIGIT_ZERO + t.getSecond() / 10) + (char) (SMALL_DIGIT_ZERO + t.getSecond() % 10);
		final float w = b.w(main, Font.LED, 1) + b.w(seconds, Font.LED, 1);
		final float x = (b.vw - w) / 2;
		b.put(main, Font.LED, x, y, 1, b.style.text());
		b.put(seconds, Font.LED, x + b.w(main, Font.LED, 1), y, 1, b.style.text());
		b.m.seconds = true;
	}

	private static void ukConcourse(Board b) {
		final DisplayStyle st = b.style;
		final int c = st.text();
		final int rows = b.config.rows();
		final boolean message = !b.messages.isEmpty();
		final String summary = b.summary();
		final int lines = 1 + rows + (message ? 1 : 0) + (summary.isEmpty() ? 0 : 1) + (b.config.clock() ? 1 : 0);
		float y = b.frame(lines * LED_LINE, 190, LED_LINE / 2 - 1) + 1;
		final float left = LED_MARGIN;
		final float right = b.vw - LED_MARGIN;
		final float destX = left + b.w("00:00", Font.LED, 1) + 6;
		final String expectedHead = Tr.t("board_col_expected");
		final float expectedW = Math.max(b.w(expectedHead, Font.LED, 1), b.w(Tr.t("board_expected", "00:00"), Font.LED, 1));
		final String platHead = Tr.t("board_col_plat");
		final float platW = Math.max(b.w(platHead, Font.LED, 1), b.w("00", Font.LED, 1));
		final float platX = right - expectedW - 6 - platW;
		b.put(Tr.t("board_col_time"), Font.LED, left, y, 1, c);
		b.put(Tr.t(b.arrivals ? "board_col_from" : "board_col_destination"), Font.LED, destX, y, 1, c);
		b.put(platHead, Font.LED, platX, y, 1, c);
		b.put(expectedHead, Font.LED, right - b.w(expectedHead, Font.LED, 1), y, 1, c);
		y += LED_LINE;
		final float rowsTop = y;
		if (b.services.isEmpty()) {
			b.centre(b.idleSecond(), Font.LED, b.vw / 2, rowsTop + Math.max(0, (rows - 1) / 2) * LED_LINE, 1, c);
		} else {
			final Page page = b.page(rows);
			for (int i = page.from; i < page.to; i++) {
				final ServiceSnapshot s = b.services.get(i);
				b.put(hhmm(scheduled(s)), Font.LED, left, y, 1, c);
				b.put(b.trim(b.place(b.arrivals ? s.origin() : s.destination()), Font.LED, 1, platX - 6 - destX), Font.LED, destX, y, 1, c);
				b.put(b.trim(s.platformName(), Font.LED, 1, platW + 4), Font.LED, platX, y, 1, c);
				final String status = ukStatus(s);
				b.put(status, Font.LED, right - b.w(status, Font.LED, 1), y, 1, c);
				y += LED_LINE;
			}
			if (page.pages > 1) {
				final String indicator = Tr.t("board_page", page.page + 1, page.pages);
				b.put(indicator, Font.LED, right - b.w(indicator, Font.LED, 1), rowsTop + rows * LED_LINE + (message ? LED_LINE : 0)
						+ (summary.isEmpty() ? 0 : LED_LINE), 1, c);
			}
		}
		y = rowsTop + rows * LED_LINE;
		if (message) {
			ukScrollLine(b, "", joinedMessages(b.messages), y, left, right);
			y += LED_LINE;
		}
		if (!summary.isEmpty()) {
			b.put(b.trim(summary, Font.LED, 1, right - left), Font.LED, left, y, 1, c);
			y += LED_LINE;
		}
		if (b.config.clock()) {
			ukClock(b, y);
		}
	}

	// ---------------------------------------------------------------------------------------------------------- NS

	private static void nsPlatform(Board b) {
		final DisplayStyle st = b.style;
		final List<ServiceSnapshot> list = b.services;
		final boolean next = list.size() > 1 && b.config.rows() > 1;
		final float stripH = b.messages.isEmpty() ? 0 : 11;
		final float mainH = 46;
		final float nextH = next ? 13 : 0;
		final float y0 = b.frame(mainH + nextH + stripH, 150, 0);
		if (list.isEmpty()) {
			b.idle(Font.SANS_BOLD, Font.SANS, y0, mainH + nextH);
			messageStrip(b, Font.SANS);
			return;
		}
		final ServiceSnapshot s = list.get(0);
		final float left = MARGIN + 1;
		final float right = b.vw - MARGIN - 1;
		// platform number in a white square, top right
		final float box = 30;
		final float boxX = right - box;
		nsPlatformSquare(b, s.platformName(), boxX, y0 + 4, box, 2.2F);
		final float textRight = boxX - 5;
		// departure time, the delay in red beside it, then the train type
		float x = left;
		final String time = hhmm(scheduled(s));
		b.put(time, Font.SANS_BOLD, x, y0 + 4, 1.3F, st.text());
		x += b.w(time, Font.SANS_BOLD, 1.3F) + 3;
		final int delay = DepartureText.delayMinutes(s);
		if (delay > 0) {
			final String plus = "+" + delay;
			b.put(plus, Font.SANS_BOLD, x, y0 + 4, 1.3F, st.delay());
			x += b.w(plus, Font.SANS_BOLD, 1.3F) + 3;
		}
		b.fit(b.trainType(s), Font.SANS, x + 3, y0 + 5.5F, 0.95F, textRight - x - 3, st.dim());
		// the destination, large
		b.fit(b.place(s.destination()), Font.SANS_BOLD, left, y0 + 17, 2.0F, textRight - left, st.text());
		// via the first few stops (or what happens here)
		final String via = s.terminating() ? Tr.t("board_terminates") : b.via(s, 4, ", ", "board_via", Font.SANS, 0.95F, textRight - left);
		b.fit(via, Font.SANS, left, y0 + 36, 0.95F, textRight - left, st.dim());
		if (next) {
			final float y = y0 + mainH;
			b.m.rect(MARGIN, y, b.vw - 2 * MARGIN, 0.6F, NS_RULE, BoardModel.LAYER_BAND);
			final ServiceSnapshot n = list.get(1);
			float nx = left;
			final String label = Tr.t("board_next");
			b.put(label, Font.SANS, nx, y + 3.5F, 0.85F, st.dim());
			nx += b.w(label, Font.SANS, 0.85F) + 4;
			final String nTime = hhmm(scheduled(n));
			b.put(nTime, Font.SANS_BOLD, nx, y + 3.5F, 0.85F, st.text());
			nx += b.w(nTime, Font.SANS_BOLD, 0.85F) + 3;
			final int nDelay = DepartureText.delayMinutes(n);
			if (nDelay > 0) {
				b.put("+" + nDelay, Font.SANS_BOLD, nx, y + 3.5F, 0.85F, st.delay());
				nx += b.w("+" + nDelay, Font.SANS_BOLD, 0.85F) + 3;
			}
			float clockLeft = right;
			if (b.config.clock()) {
				final String clock = clock();
				clockLeft = right - b.w(clock, Font.SANS, 0.85F);
				b.put(clock, Font.SANS, clockLeft, y + 3.5F, 0.85F, st.dim());
			}
			b.fit(b.trainType(n) + "  " + b.place(n.destination()), Font.SANS, nx + 2, y + 3.5F, 0.85F, clockLeft - 6 - nx, st.text());
		}
		messageStrip(b, Font.SANS);
	}

	/** A white square with the platform number in the board's blue, as on Dutch platform screens. */
	private static void nsPlatformSquare(Board b, String platform, float x, float y, float size, float k) {
		if (platform.isEmpty()) {
			return;
		}
		b.m.rect(x, y, size, size, b.style.chip(), BoardModel.LAYER_CHIP);
		float pk = k;
		while (pk > 0.5F && b.w(platform, Font.SANS_BOLD, pk) > size - 3) {
			pk -= 0.1F;
		}
		final float pw = b.w(platform, Font.SANS_BOLD, pk);
		b.put(platform, Font.SANS_BOLD, x + (size - pw) / 2, y + (size - 7 * pk) / 2, pk, b.style.background());
	}

	private static void nsConcourse(Board b) {
		final DisplayStyle st = b.style;
		final int rows = b.config.rows();
		final float headerH = 13;
		final float headsH = 8;
		final float rowH = 15;
		final String summary = b.summary();
		final float summaryH = summary.isEmpty() ? 0 : 9;
		final float stripH = b.messages.isEmpty() ? 0 : 11;
		final float y0 = b.frame(headerH + headsH + rows * rowH + summaryH + stripH, 170, 0);
		final float left = MARGIN + 1;
		final float right = b.vw - MARGIN - 1;
		final Page page = b.services.isEmpty() ? null : b.page(rows);
		b.header(y0, headerH, Font.SANS_BOLD, Font.SANS, page);
		// column headings
		final float hy = y0 + headerH + 1.5F;
		final float destX = left + b.w("00:00 +00", Font.SANS_BOLD, 0.95F) + 6;
		final float trainW = Math.min(46, b.vw * 0.2F);
		final float trainX = right - trainW;
		final float platSize = 11;
		final float platX = trainX - 8 - Math.max(platSize, b.w(Tr.t("board_col_platform"), Font.SANS, 0.6F));
		b.put(Tr.t("board_col_departure"), Font.SANS, left, hy, 0.6F, st.dim());
		b.put(Tr.t(b.arrivals ? "board_col_from" : "board_col_to_via"), Font.SANS, destX, hy, 0.6F, st.dim());
		b.put(Tr.t("board_col_platform"), Font.SANS, platX, hy, 0.6F, st.dim());
		b.put(Tr.t("board_col_train"), Font.SANS, trainX, hy, 0.6F, st.dim());
		final float rowsTop = y0 + headerH + headsH;
		if (page == null) {
			b.idle(Font.SANS_BOLD, Font.SANS, rowsTop, rows * rowH);
		} else {
			for (int i = page.from; i < page.to; i++) {
				final ServiceSnapshot s = b.services.get(i);
				final float y = rowsTop + (i - page.from) * rowH;
				b.m.rect(MARGIN, y, b.vw - 2 * MARGIN, 0.5F, NS_RULE, BoardModel.LAYER_BAND);
				final String time = hhmm(scheduled(s));
				b.put(time, Font.SANS_BOLD, left, y + 3, 0.95F, st.text());
				final int delay = DepartureText.delayMinutes(s);
				if (delay > 0) {
					b.put("+" + delay, Font.SANS_BOLD, left + b.w(time, Font.SANS_BOLD, 0.95F) + 2, y + 3, 0.95F, st.delay());
				}
				final String place = b.place(b.arrivals ? s.origin() : s.destination());
				final float destW = platX - 6 - destX;
				final String via = b.arrivals || s.terminating() ? "" : b.via(s, 3, ", ", "board_via", Font.SANS, 0.6F, destW);
				b.fit(place, Font.SANS_BOLD, destX, via.isEmpty() ? y + 4 : y + 2, 0.95F, destW, st.text());
				if (!via.isEmpty()) {
					b.fit(via, Font.SANS, destX, y + 9.5F, 0.6F, destW, st.dim());
				}
				nsPlatformSquare(b, s.platformName(), platX, y + 2, platSize, 0.9F);
				b.fit(b.trainType(s), Font.SANS, trainX, y + 4.5F, 0.75F, right - trainX, st.text());
			}
		}
		if (!summary.isEmpty()) {
			b.fit(summary, Font.SANS, left, rowsTop + rows * rowH + 1.5F, 0.7F, right - left, st.dim());
		}
		messageStrip(b, Font.SANS);
	}

	// ---------------------------------------------------------------------------------------------------------- DB

	private static void dbPlatform(Board b) {
		final DisplayStyle st = b.style;
		final List<ServiceSnapshot> list = b.services;
		final int following = list.size() <= 1 ? 0 : Math.min(Math.min(b.config.rows(), list.size()) - 1, 2);
		final ServiceSnapshot s = list.isEmpty() ? null : list.get(0);
		final int delay = s == null ? 0 : DepartureText.delayMinutes(s);
		// the white band: notices, and the delay in words
		final List<String> notices = new ArrayList<>();
		if (delay > 0) {
			notices.add(Tr.t(delay == 1 ? "board_late_about_one" : "board_late_about", delay));
		}
		for (final ServiceMessage message : b.messages) {
			notices.add(message.text());
		}
		final float bandH = notices.isEmpty() ? 0 : 10;
		final float mainH = 44;
		final float followH = 10;
		final float y0 = b.frame(bandH + mainH + following * followH, 160, 0);
		final float left = MARGIN + 1;
		final float right = b.vw - MARGIN - 1;
		if (bandH > 0) {
			final float bandLeft = s == null ? MARGIN : left + 44;
			b.m.rect(bandLeft, y0 + 1, b.vw - MARGIN - bandLeft, bandH - 2, 0xFFFFFFFF, BoardModel.LAYER_BAND);
			final String text = String.join("  +++  ", notices);
			final float x0 = bandLeft + 2;
			final float x1 = b.vw - MARGIN - 2;
			b.m.rect(MARGIN - 1, y0 + 1, x0 - MARGIN + 1, bandH - 2, bandLeft > MARGIN ? st.background() : 0xFFFFFFFF, BoardModel.LAYER_MASK);
			b.m.rect(x1, y0 + 1, b.vw - x1, bandH - 2, 0xFFFFFFFF, BoardModel.LAYER_MASK);
			b.m.scroll(text, Font.SANS, b.advances(text, Font.SANS), x0, x1, y0 + 2.2F, 0.8F, st.background(), 22, false);
		}
		final float top = y0 + bandH;
		if (s == null) {
			b.idle(Font.SANS_BOLD, Font.SANS, top, mainH);
			return;
		}
		// left column: time, train, delay box
		final String time = hhmm(scheduled(s));
		b.put(time, Font.SANS_BOLD, left, top + 3, 1.35F, st.text());
		b.fit(b.trainType(s), Font.SANS, left, top + 17, 0.9F, 40, st.text());
		if (delay > 0) {
			final String plus = "+" + delay;
			final float pw = b.w(plus, Font.SANS_BOLD, 0.9F) + 4;
			b.m.rect(left, top + 28, pw, 10, 0xFFFFFFFF, BoardModel.LAYER_CHIP);
			b.put(plus, Font.SANS_BOLD, left + 2, top + 29.5F, 0.9F, st.background());
		}
		// right: the platform number
		final String platform = s.platformName();
		float mainRight = right;
		if (!platform.isEmpty() && b.vw >= 150) {
			final float pk = 2.6F;
			final float pw = Math.min(b.w(platform, Font.SANS_BOLD, pk), 34);
			b.fit(platform, Font.SANS_BOLD, right - pw, top + 9, pk, pw, st.text());
			mainRight = right - pw - 6;
		}
		// middle: the stops, then the destination large
		final float mx = left + 44;
		final String via = s.terminating() ? Tr.t("board_terminates") : b.via(s, 4, " - ", "", Font.SANS, 0.85F, mainRight - mx);
		b.fit(via, Font.SANS, mx, top + 4, 0.85F, mainRight - mx, st.text());
		b.fit(b.place(s.destination()), Font.SANS_BOLD, mx, top + 17, 2.1F, mainRight - mx, st.text());
		// following trains
		for (int i = 1; i <= following; i++) {
			final ServiceSnapshot f = list.get(i);
			final float y = top + mainH + (i - 1) * followH;
			b.m.rect(MARGIN, y, b.vw - 2 * MARGIN, 0.5F, DB_RULE, BoardModel.LAYER_BAND);
			float x = left;
			final String fTime = hhmm(scheduled(f));
			b.put(fTime, Font.SANS_BOLD, x, y + 2.2F, 0.8F, st.text());
			x += b.w("00:00", Font.SANS_BOLD, 0.8F) + 4;
			final int fDelay = DepartureText.delayMinutes(f);
			if (fDelay > 0) {
				final String plus = "+" + fDelay;
				final float pw = b.w(plus, Font.SANS_BOLD, 0.7F) + 3;
				b.m.rect(x, y + 1.6F, pw, 7.2F, 0xFFFFFFFF, BoardModel.LAYER_CHIP);
				b.put(plus, Font.SANS_BOLD, x + 1.5F, y + 2.6F, 0.7F, st.background());
				x += pw + 3;
			}
			b.fit(b.trainType(f), Font.SANS, x, y + 2.2F, 0.8F, 34, st.dim());
			x += Math.min(34, b.w(b.trainType(f), Font.SANS, 0.8F)) + 5;
			b.fit(b.place(f.destination()), Font.SANS_BOLD, x, y + 2.2F, 0.8F, right - x, st.text());
		}
	}

	private static void dbConcourse(Board b) {
		final DisplayStyle st = b.style;
		final int rows = b.config.rows();
		final float headerH = 13;
		final float headsH = 8;
		final float rowH = 12;
		final String summary = b.summary();
		final float summaryH = summary.isEmpty() ? 0 : 9;
		final float stripH = b.messages.isEmpty() ? 0 : 11;
		final float y0 = b.frame(headerH + headsH + rows * rowH + summaryH + stripH, 180, 0);
		final float left = MARGIN + 1;
		final float right = b.vw - MARGIN - 1;
		final Page page = b.services.isEmpty() ? null : b.page(rows);
		b.header(y0, headerH, Font.SANS_BOLD, Font.SANS, page);
		final float timeW = b.w("00:00", Font.SANS_BOLD, 0.95F) + 16;
		final float trainX = left + timeW;
		final float trainW = Math.min(34, b.vw * 0.16F);
		final float viaX = trainX + trainW + 4;
		final float platW = Math.max(b.w(Tr.t("board_col_platform"), Font.SANS, 0.6F), b.w("00", Font.SANS_BOLD, 0.95F));
		final float platX = right - platW;
		final float span = platX - 6 - viaX;
		final float viaW = b.arrivals ? 0 : span * 0.42F;
		final float destX = viaX + (b.arrivals ? 0 : viaW + 4);
		final float hy = y0 + headerH + 1.5F;
		b.put(Tr.t("board_col_time"), Font.SANS, left, hy, 0.6F, st.dim());
		b.put(Tr.t("board_col_train"), Font.SANS, trainX, hy, 0.6F, st.dim());
		if (!b.arrivals) {
			b.put(Tr.t("board_col_via"), Font.SANS, viaX, hy, 0.6F, st.dim());
		}
		b.put(Tr.t(b.arrivals ? "board_col_from" : "board_col_destination"), Font.SANS, destX, hy, 0.6F, st.dim());
		final String platHead = Tr.t("board_col_platform");
		b.put(platHead, Font.SANS, right - b.w(platHead, Font.SANS, 0.6F), hy, 0.6F, st.dim());
		final float rowsTop = y0 + headerH + headsH;
		if (page == null) {
			b.idle(Font.SANS_BOLD, Font.SANS, rowsTop, rows * rowH);
		} else {
			for (int i = page.from; i < page.to; i++) {
				final ServiceSnapshot s = b.services.get(i);
				final float y = rowsTop + (i - page.from) * rowH;
				b.m.rect(MARGIN, y, b.vw - 2 * MARGIN, 0.5F, DB_RULE, BoardModel.LAYER_BAND);
				final String time = hhmm(scheduled(s));
				b.put(time, Font.SANS_BOLD, left, y + 3, 0.95F, st.text());
				final int delay = DepartureText.delayMinutes(s);
				if (delay > 0) {
					final String plus = "+" + delay;
					final float px = left + b.w(time, Font.SANS_BOLD, 0.95F) + 2;
					final float pw = b.w(plus, Font.SANS_BOLD, 0.6F) + 2;
					b.m.rect(px, y + 3, pw, 6.5F, 0xFFFFFFFF, BoardModel.LAYER_CHIP);
					b.put(plus, Font.SANS_BOLD, px + 1, y + 4, 0.6F, st.background());
				}
				b.fit(b.trainType(s), Font.SANS, trainX, y + 3.5F, 0.8F, trainW, st.text());
				if (!b.arrivals && !s.terminating()) {
					b.fit(b.via(s, 3, " - ", "", Font.SANS, 0.65F, viaW), Font.SANS, viaX, y + 4, 0.65F, viaW, st.dim());
				}
				b.fit(b.place(b.arrivals ? s.origin() : s.destination()), Font.SANS_BOLD, destX, y + 3, 0.95F, platX - 6 - destX, st.text());
				final String platform = s.platformName();
				b.fit(platform, Font.SANS_BOLD, right - Math.min(platW, b.w(platform, Font.SANS_BOLD, 0.95F)), y + 3, 0.95F, platW, st.text());
			}
		}
		if (!summary.isEmpty()) {
			b.fit(summary, Font.SANS, left, rowsTop + rows * rowH + 1.5F, 0.7F, right - left, st.dim());
		}
		messageStrip(b, Font.SANS);
	}

	// ------------------------------------------------------------------------------------------------------- metro

	private static void metroPlatform(Board b) {
		final DisplayStyle st = b.style;
		final List<ServiceSnapshot> list = b.services;
		final int rows = b.config.rows();
		final boolean header = b.config.clock();
		final boolean calling = b.config.callingAt() && !list.isEmpty();
		final float headerH = header ? 12 : 0;
		final float firstH = 17;
		final float rowH = 13;
		final float callingH = calling ? 8 : 0;
		final float stripH = b.messages.isEmpty() ? 0 : 11;
		final float y0 = b.frame(headerH + firstH + callingH + (rows - 1) * rowH + stripH + 1, 130, 0);
		final float left = MARGIN;
		final float right = b.vw - MARGIN;
		if (header) {
			b.m.rect(0, y0, b.vw, headerH, st.header(), BoardModel.LAYER_BAND);
			final String clock = clock();
			final float cw = b.w(clock, Font.SANS_BOLD, 0.85F);
			b.put(clock, Font.SANS_BOLD, right - cw, y0 + 3, 0.85F, st.text());
			final String here = BoardSummary.platformName(b.snapshot.platforms(), b.nearest);
			final String title = here.isEmpty() ? b.station : Tr.t("board_platform", here);
			b.fit(title, Font.SANS_BOLD, left, y0 + 3, 0.85F, right - cw - 6 - left, st.text());
		}
		float y = y0 + headerH;
		if (list.isEmpty()) {
			b.idle(Font.SANS_BOLD, Font.SANS, y, firstH + (rows - 1) * rowH);
			messageStrip(b, Font.SANS);
			return;
		}
		final float badgeW = badgeColumn(b, list.subList(0, Math.min(rows, list.size())));
		for (int i = 0; i < Math.min(rows, list.size()); i++) {
			final float h = i == 0 ? firstH : rowH;
			final float k = i == 0 ? 1.25F : 1.0F;
			metroRow(b, list.get(i), y, h, k, left, right, false, badgeW * k);
			y += h;
			if (i == 0 && calling) {
				final ServiceSnapshot s = list.get(0);
				final String text = s.terminating() ? Tr.t("board_terminates") : listedCalling(b, s);
				b.fit(text, Font.SANS, left + badgeW * k + 4, y - 1, 0.7F, right - left - badgeW * k - 4, st.dim());
				y += callingH;
			}
			if (i < Math.min(rows, list.size()) - 1) {
				b.m.rect(left, y - 0.5F, right - left, 0.5F, METRO_RULE, BoardModel.LAYER_BAND);
			}
		}
		messageStrip(b, Font.SANS);
	}

	/** Route badge, destination, (platform), minutes to go. */
	private static void metroRow(Board b, ServiceSnapshot s, float y, float h, float k, float left, float right, boolean platform, float badge) {
		final DisplayStyle st = b.style;
		final float badgeH = 9.6F * k;
		badge(b, s, left, y + (h - badgeH) / 2, badge, badgeH, k * 0.8F);
		final float textY = y + (h - 7 * k) / 2;
		// minutes: the number large, "min" small
		final DepartureText.Phase phase = DepartureText.phase(s, b.now);
		float statusLeft;
		if (phase == DepartureText.Phase.MINUTES) {
			final int minutes = Math.min(99, DepartureText.minutesUntil(s, b.now));
			final String unit = Tr.t("board_min");
			final float uw = b.w(unit, Font.SANS, 0.7F * k);
			b.put(unit, Font.SANS, right - uw, textY + 2 * k, 0.7F * k, st.dim());
			final String number = String.valueOf(minutes);
			final float nw = b.w(number, Font.SANS_BOLD, k);
			b.put(number, Font.SANS_BOLD, right - uw - 2 - nw, textY, k, st.accent());
			statusLeft = right - uw - 2 - b.w("00", Font.SANS_BOLD, k);
		} else {
			final String status = DepartureText.status(s, b.now);
			final float sw = b.w(status, Font.SANS_BOLD, k);
			b.put(status, Font.SANS_BOLD, right - sw, textY, k, st.accent());
			statusLeft = right - sw;
		}
		float destRight = statusLeft - 5;
		if (platform) {
			final String name = s.platformName();
			final float pw = Math.max(12, b.w(name, Font.SANS_BOLD, 0.85F) + 4);
			final float px = destRight - pw - 2;
			b.m.rect(px, y + (h - 10) / 2, pw, 10, st.chip(), BoardModel.LAYER_CHIP);
			b.put(name, Font.SANS_BOLD, px + (pw - b.w(name, Font.SANS_BOLD, 0.85F)) / 2, y + (h - 6) / 2, 0.85F, st.text());
			destRight = px - 4;
		}
		final String place = b.place(b.arrivals ? s.origin() : s.destination());
		b.fit(place, Font.SANS_BOLD, left + badge + 4, textY, k, destRight - left - badge - 4, st.text());
	}

	/** Width (at text scale 1) of the badge column: the longest route label shown, within sensible bounds. */
	private static float badgeColumn(Board b, List<ServiceSnapshot> shown) {
		float widest = 12;
		for (final ServiceSnapshot s : shown) {
			final String label = s.routeNumber().isBlank() ? s.routeName() : s.routeNumber();
			widest = Math.max(widest, b.w(label, Font.SANS_BOLD, 0.8F) + 5);
		}
		return Math.min(widest, 30);
	}

	/** A rounded badge in the route's colour with its number. */
	private static void badge(Board b, ServiceSnapshot s, float x, float y, float w, float h, float k) {
		final int color = 0xFF000000 | s.routeColor();
		final float r = Math.min(w, h) * 0.12F;
		b.m.rect(x + r, y, w - 2 * r, h, color, BoardModel.LAYER_CHIP);
		b.m.rect(x, y + r, w, h - 2 * r, color, BoardModel.LAYER_CHIP);
		final String label = s.routeNumber().isBlank() ? s.routeName() : s.routeNumber();
		if (label.isEmpty()) {
			return;
		}
		final int textColor = luminance(color) > 0.6F ? 0xFF101010 : 0xFFFFFFFF;
		float lk = k;
		while (lk > 0.4F && b.w(label, Font.SANS_BOLD, lk) > w - 2) {
			lk -= 0.05F;
		}
		final String shown = b.trim(label, Font.SANS_BOLD, lk, w - 2);
		b.put(shown, Font.SANS_BOLD, x + (w - b.w(shown, Font.SANS_BOLD, lk)) / 2, y + (h - 7 * lk) / 2, lk, textColor);
	}

	private static void metroConcourse(Board b) {
		final DisplayStyle st = b.style;
		final int rows = b.config.rows();
		final float headerH = 13;
		final float headsH = 8;
		final float rowH = 13;
		final String summary = b.summary();
		final float summaryH = summary.isEmpty() ? 0 : 9;
		final float stripH = b.messages.isEmpty() ? 0 : 11;
		final float y0 = b.frame(headerH + headsH + rows * rowH + summaryH + stripH, 150, 0);
		final float left = MARGIN;
		final float right = b.vw - MARGIN;
		final Page page = b.services.isEmpty() ? null : b.page(rows);
		b.header(y0, headerH, Font.SANS_BOLD, Font.SANS, page);
		final float hy = y0 + headerH + 1.5F;
		b.put(Tr.t("board_col_line"), Font.SANS, left, hy, 0.6F, st.dim());
		final float badgeW = page == null ? 12 : badgeColumn(b, b.services.subList(page.from, page.to));
		b.put(Tr.t(b.arrivals ? "board_col_from" : "board_col_destination"), Font.SANS, left + badgeW + 4, hy, 0.6F, st.dim());
		final String minHead = Tr.t("board_col_min");
		b.put(minHead, Font.SANS, right - b.w(minHead, Font.SANS, 0.6F), hy, 0.6F, st.dim());
		final float rowsTop = y0 + headerH + headsH;
		if (page == null) {
			b.idle(Font.SANS_BOLD, Font.SANS, rowsTop, rows * rowH);
		} else {
			for (int i = page.from; i < page.to; i++) {
				final float y = rowsTop + (i - page.from) * rowH;
				if (i > page.from) {
					b.m.rect(left, y - 0.5F, right - left, 0.5F, METRO_RULE, BoardModel.LAYER_BAND);
				}
				metroRow(b, b.services.get(i), y, rowH, 1, left, right, true, badgeW);
			}
		}
		if (!summary.isEmpty()) {
			b.fit(summary, Font.SANS, left, rowsTop + rows * rowH + 1.5F, 0.7F, right - left, st.dim());
		}
		messageStrip(b, Font.SANS);
	}

	// ------------------------------------------------------------------------------------------------------ shared

	/**
	 * The service-message band along the bottom of a colour screen; nothing without messages. One message at a time,
	 * several in turn; a long one scrolls through the band.
	 */
	private static void messageStrip(Board b, Font font) {
		if (b.messages.isEmpty()) {
			return;
		}
		final long slot = 6000;
		final ServiceMessage message = b.messages.get((int) ((b.now / slot) % b.messages.size()));
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
				band = b.style.header();
				color = b.style.text();
			}
		}
		final float h = 11;
		final float top = b.vh - h;
		b.m.rect(0, top, b.vw, h, band, BoardModel.LAYER_BAND);
		final String text = stripText(message);
		final float k = 0.8F;
		final float x0 = MARGIN;
		final float x1 = b.vw - MARGIN;
		if (b.w(text, font, k) <= x1 - x0) {
			b.put(text, font, x0, top + (h - 7 * k) / 2, k, color);
		} else {
			b.m.rect(0, top, x0, h, band, BoardModel.LAYER_MASK);
			b.m.rect(x1, top, b.vw - x1, h, band, BoardModel.LAYER_MASK);
			b.m.scroll(text, font, b.advances(text, font), x0, x1, top + (h - 7 * k) / 2, k, color, 24, false);
		}
	}

	private static String stripText(ServiceMessage message) {
		return switch (message.severity()) {
			case INFO -> message.text();
			case WARNING, DISRUPTION -> "! " + message.text();
			case SEVERE -> "!! " + message.text();
		};
	}

	private static String joinedMessages(List<ServiceMessage> messages) {
		final StringBuilder out = new StringBuilder();
		for (final ServiceMessage message : messages) {
			if (!out.isEmpty()) {
				out.append("   ");
			}
			out.append(stripText(message));
		}
		return out.toString();
	}

	/** "A, B, C and D". */
	private static String listed(List<String> names) {
		if (names.size() <= 1) {
			return names.isEmpty() ? "" : names.get(0);
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + Tr.t("board_and") + names.get(names.size() - 1);
	}

	private static String listedCalling(Board b, ServiceSnapshot s) {
		final List<String> stops = b.stops(s);
		return stops.isEmpty() ? "" : Tr.t("board_calling_at") + " " + listed(stops);
	}

	/** When the train is timetabled: its departure, or its arrival when it ends here, less any lateness. */
	static long scheduled(ServiceSnapshot s) {
		return expected(s) - (s.realtime() ? Math.max(0, s.deviationMillis()) : 0);
	}

	static long expected(ServiceSnapshot s) {
		return s.terminating() ? s.arrivalMillis() : s.departureMillis();
	}

	static String hhmm(long millis) {
		final LocalTime t = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalTime();
		return two(t.getHour()) + ":" + two(t.getMinute());
	}

	private static String clock() {
		final LocalTime t = LocalTime.now(ZONE);
		return two(t.getHour()) + ":" + two(t.getMinute());
	}

	private static String two(int n) {
		return n < 10 ? "0" + n : String.valueOf(n);
	}

	private static float luminance(int argb) {
		final float r = ((argb >> 16) & 0xFF) / 255F;
		final float g = ((argb >> 8) & 0xFF) / 255F;
		final float b = (argb & 0xFF) / 255F;
		return 0.2126F * r + 0.7152F * g + 0.0722F * b;
	}

	private record Page(int page, int pages, int from, int to) {
	}

	/** One build: the inputs, the board's virtual size once {@link #frame} has set it, and the drawing helpers. */
	private static final class Board {

		final BoardModel m;
		final TextRenderer tr;
		final DisplayConfig config;
		final DisplayStyle style;
		final StationSnapshot snapshot;
		final long nearest;
		final long now;
		final String station;
		final List<ServiceMessage> messages;
		final boolean concourse;
		final boolean arrivals;
		final List<ServiceSnapshot> services;
		final float boardW;
		final float boardH;
		float vw;
		float vh;

		Board(BoardModel m, TextRenderer tr, DisplayKind kind, DisplayConfig config, StationSnapshot snapshot, long nearest, float boardW, float boardH,
			  long now) {
			this.m = m;
			this.tr = tr;
			this.config = config;
			this.style = config.style();
			this.snapshot = snapshot;
			this.nearest = nearest;
			this.now = now;
			this.boardW = boardW;
			this.boardH = boardH;
			final String plain = snapshot.station() == null ? "" : snapshot.station().displayName();
			this.station = snapshot.station() == null ? "" : ClientStationSuffixes.apply(plain, SuffixContext.DISPLAYS);
			this.messages = ClientServiceMessages.applicable(config.message(), plain);
			this.concourse = kind == DisplayKind.CONCOURSE;
			this.arrivals = concourse && config.arrivals();
			this.services = arrivals ? arrivals(snapshot, plain, now) : visible(kind, snapshot, nearest, now);
		}

		/**
		 * Sizes the virtual board so {@code contentH} fits its height and at least {@code minWidth} its width, paints the
		 * background and returns the top of the content.
		 */
		float frame(float contentH, float minWidth, float topMargin) {
			final float scale = Math.min(boardH / contentH, boardW / minWidth);
			vw = boardW / scale;
			vh = boardH / scale;
			m.scale = scale;
			m.width = vw;
			m.height = vh;
			m.rect(0, 0, vw, vh, style.background(), BoardModel.LAYER_BACKGROUND);
			return BoardAnchor.contentTop(config.alignment(), vh, contentH, topMargin);
		}

		/** Title band of a concourse board: "Departures" (or "Arrivals"), the page, the clock. */
		void header(float y, float h, Font bold, Font regular, Page page) {
			m.rect(0, y, vw, h, style.header(), BoardModel.LAYER_BAND);
			final float k = 0.95F;
			final float ty = y + (h - 7 * k) / 2;
			float right = vw - MARGIN - 1;
			if (config.clock()) {
				final String clock = clock();
				final float cw = w(clock, bold, k);
				put(clock, bold, right - cw, ty, k, style.text());
				right -= cw + 6;
			}
			if (page != null && page.pages > 1) {
				final String indicator = (page.page + 1) + "/" + page.pages;
				final float iw = w(indicator, regular, 0.8F);
				put(indicator, regular, right - iw, ty + 1, 0.8F, style.dim());
				right -= iw + 6;
			}
			final String title = arrivals ? Tr.t("board_arrivals") : Tr.t("board_departures");
			final float left = MARGIN + 1;
			put(title, bold, left, ty, k, style.text());
			final float after = left + w(title, bold, k) + 5;
			if (!station.isEmpty()) {
				fit(station, regular, after, ty, k, right - after, style.dim());
			}
		}

		Page page(int rows) {
			final int pages = Pagination.pageCount(services.size(), rows);
			final int page = Pagination.currentPage(now, config.pageSeconds() * 1000L, pages);
			final int from = Pagination.firstIndex(page, rows);
			return new Page(page, pages, from, Math.min(Pagination.endIndex(page, rows, services.size()), from + rows));
		}

		/** "N platforms" / "This is platform X" when the board asks for the summary line, else empty. */
		String summary() {
			if (!concourse || !config.summary()) {
				return "";
			}
			return BoardSummary.text(snapshot.platforms().size(), BoardSummary.platformName(snapshot.platforms(), nearest));
		}

		String idleFirst() {
			return snapshot.station() == null ? Tr.t("term_no_station") : Tr.t("board_welcome", station);
		}

		String idleSecond() {
			return snapshot.station() == null ? Tr.t("board_configure") : Tr.t("term_no_departures");
		}

		/** The two idle lines, centred in the given band. */
		void idle(Font bold, Font regular, float top, float height) {
			final float y = top + Math.max(0, (height - 18) / 2);
			centre(idleFirst(), bold, vw / 2, y, 1, style.text());
			centre(idleSecond(), regular, vw / 2, y + 10, 0.8F, style.dim());
		}

		/** A station name as the boards show it (suffixes applied). */
		String place(String name) {
			return ClientStationSuffixes.apply(name, SuffixContext.DISPLAYS);
		}

		List<String> stops(ServiceSnapshot s) {
			return s.callingAt().stream().map(this::place).toList();
		}

		/**
		 * The first stops before the destination, joined by {@code separator}: as many whole names as fit {@code avail} (at
		 * most {@code max}), never a name cut in half; at least the first. In {@code key}'s wording ("via %s") unless empty.
		 */
		String via(ServiceSnapshot s, int max, String separator, String key, Font font, float k, float avail) {
			final List<String> stops = new ArrayList<>(stops(s));
			if (!stops.isEmpty() && stops.get(stops.size() - 1).equals(place(s.destination()))) {
				stops.remove(stops.size() - 1);
			}
			if (stops.isEmpty()) {
				return "";
			}
			String text = "";
			for (int n = Math.min(max, stops.size()); n >= 1; n--) {
				final String joined = String.join(separator, stops.subList(0, n));
				text = key.isEmpty() ? joined : Tr.t(key, joined);
				if (w(text, font, k) <= avail) {
					break;
				}
			}
			return text;
		}

		/** What a colour screen calls the train: the route number, or the route's name. */
		String trainType(ServiceSnapshot s) {
			return s.routeNumber().isBlank() ? s.routeName() : s.routeNumber();
		}

		float w(String text, Font font, float k) {
			return tr.getWidth(font.ordered(text)) * k;
		}

		/** Each character's left edge in unscaled font units, plus the end. */
		float[] advances(String text, Font font) {
			final float[] at = new float[text.length() + 1];
			for (int i = 0; i < text.length(); i++) {
				at[i + 1] = at[i] + tr.getWidth(font.ordered(String.valueOf(text.charAt(i))));
			}
			return at;
		}

		void put(String text, Font font, float x, float y, float k, int color) {
			m.text(text, font, x, y, k, 1, color, BoardModel.TEXT_NORMAL);
		}

		void centre(String text, Font font, float cx, float y, float k, int color) {
			final float avail = vw - 2 * MARGIN;
			final float width = w(text, font, k);
			if (width <= avail) {
				put(text, font, cx - width / 2, y, k, color);
			} else {
				fit(text, font, MARGIN, y, k, avail, color);
			}
		}

		/** {@code text} cut to fit {@code avail}. */
		String trim(String text, Font font, float k, float avail) {
			if (avail <= 0) {
				return "";
			}
			return w(text, font, k) <= avail ? text : tr.getTextHandler().trimToWidth(text, (int) (avail / k), font.style);
		}

		/**
		 * Left-aligned text in a column. LED lettering is cut to fit (its LEDs have a fixed pitch); a colour screen's is
		 * squeezed down to {@link #MIN_SQUEEZE}, scrolled beyond that.
		 */
		void fit(String text, Font font, float x, float y, float k, float avail, int color) {
			if (text.isEmpty() || avail <= 0) {
				return;
			}
			if (font == Font.LED) {
				put(trim(text, font, k, avail), font, x, y, k, color);
				return;
			}
			final float full = w(text, font, k);
			if (full <= avail) {
				put(text, font, x, y, k, color);
				return;
			}
			final float squeeze = avail / full;
			if (squeeze >= MIN_SQUEEZE) {
				m.text(text, font, x, y, k, squeeze, color, BoardModel.TEXT_NORMAL);
				return;
			}
			final float availUnits = avail / (k * MIN_SQUEEZE);
			final int length = text.length();
			final int visibleChars = Math.max(1, (int) (length * availUnits / (full / k)));
			final int start = Marquee.startIndex(now, length, visibleChars, MARQUEE_STEP_MILLIS, MARQUEE_HOLD_MILLIS);
			final String window = tr.getTextHandler().trimToWidth(text.substring(start, Math.min(length, start + visibleChars + 2)), (int) availUnits, font.style);
			m.marquee = true;
			m.text(window, font, x, y, k, MIN_SQUEEZE, color, BoardModel.TEXT_NORMAL);
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
	}
}
