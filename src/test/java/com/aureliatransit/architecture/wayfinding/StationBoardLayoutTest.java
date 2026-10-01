package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.TextFit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationBoardLayoutTest {

	private static final ToIntFunction<String> FONT = s -> s.codePoints().map(c -> c > 0x2E80 ? 9 : 6).sum();
	private static final float[][] SIZES = {{14.5F, 12.5F}, {30.5F, 12.5F}, {46.5F, 12.5F}, {14.5F, 6}, {4, 4}, {2, 2}};

	private static ResolvedWayfinding resolved(boolean full) {
		final List<ExitInfo> exits = full
				? List.of(new ExitInfo("A", List.of("Market Street", "City Hall")), new ExitInfo("B", List.of("東京駅前", "Library")), new ExitInfo("C", List.of()),
				new ExitInfo("D", List.of("A very long destination list that cannot possibly fit in one row", "Second")), new ExitInfo("E", List.of("Park")),
				new ExitInfo("F", List.of("Pier")))
				: List.of();
		return new ResolvedWayfinding(full ? "Central" : "", "", "", full ? List.of(new LineBadge("B", 0x0066CC, BadgeShape.ROUNDED), new LineBadge("L", 0xFF8800, BadgeShape.CIRCLE)) : List.of(),
				SignArrow.UP_LEFT, full ? "To Frankford" : "", ServiceType.NONE, "", full ? "2" : "", "", List.of(), full ? "Market Street" : "", full ? "Change for Regional Rail" : "",
				LanguageLayout.SINGLE, Pictogram.NONE, AccentPalette.NONE, full, exits);
	}

	private static List<ServiceMessage> notices(int count) {
		final List<ServiceMessage> list = new ArrayList<>();
		final MessageSeverity[] severities = MessageSeverity.values();
		for (int i = 0; i < count; i++) {
			list.add(new ServiceMessage(i + 1, MessageScope.NETWORK, "", severities[i % severities.length],
					i == 1 ? "A long service message about severe delays on the B line towards Frankford because of a signal fault" : "Notice " + i));
		}
		return list;
	}

	private static void assertInside(PanelLayout.Panel panel, float w, float h, String context) {
		for (final PanelLayout.Label label : panel.labels()) {
			final float half = label.widthUnits() * label.scale() / 2;
			assertTrue(label.scale() > 0, context + ": '" + label.text() + "' has no size");
			assertTrue(label.cx() - half >= -w / 2 - 0.01F && label.cx() + half <= w / 2 + 0.01F, context + ": '" + label.text() + "' overflows horizontally");
			final float halfH = TextFit.ROW * label.scale() / 2;
			assertTrue(label.cy() - halfH >= -h / 2 - 0.01F && label.cy() + halfH <= h / 2 + 0.01F, context + ": '" + label.text() + "' overflows vertically");
		}
		for (final PanelLayout.Rect rect : panel.rects()) {
			assertTrue(rect.w() > 0 && rect.h() > 0, context + ": degenerate rect");
			assertTrue(rect.cx() - rect.w() / 2 >= -w / 2 - 0.01F && rect.cx() + rect.w() / 2 <= w / 2 + 0.01F, context + ": rect overflows horizontally " + rect);
			assertTrue(rect.cy() - rect.h() / 2 >= -h / 2 - 0.01F && rect.cy() + rect.h() / 2 <= h / 2 + 0.01F, context + ": rect overflows vertically " + rect);
		}
	}

	@Test
	void everyViewStaysInsideEverySizeWithZeroOneAndManyNotices() {
		for (final BoardView view : BoardView.values()) {
			for (final boolean full : new boolean[] {false, true}) {
				for (final int count : new int[] {0, 1, 2, 7}) {
					for (final float[] size : SIZES) {
						final PanelLayout.Panel panel = StationBoardLayout.layout(resolved(full), view, notices(count), size[0], size[1], 0xFFFFFFFF, FONT);
						assertInside(panel, size[0], size[1], view + " full=" + full + " notices=" + count + " size=" + size[0] + "x" + size[1]);
					}
				}
			}
		}
	}

	@Test
	void layoutIsDeterministic() {
		for (final BoardView view : BoardView.values()) {
			assertEquals(StationBoardLayout.layout(resolved(true), view, notices(3), 30.5F, 12.5F, 0xFFFFFFFF, FONT),
					StationBoardLayout.layout(resolved(true), view, notices(3), 30.5F, 12.5F, 0xFFFFFFFF, FONT));
		}
	}

	private static List<String> texts(PanelLayout.Panel panel) {
		return panel.labels().stream().map(PanelLayout.Label::text).toList();
	}

	@Test
	void serviceViewListsNoticesAndSaysWhenThereAreNone() {
		final PanelLayout.Panel none = StationBoardLayout.layout(resolved(true), BoardView.SERVICE_CHANGE, List.of(), 30.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(none).contains("No service changes"));
		final PanelLayout.Panel one = StationBoardLayout.layout(resolved(true), BoardView.SERVICE_CHANGE, notices(1), 30.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(one).contains("Notice 0"));
		assertFalse(texts(one).stream().anyMatch(t -> t.startsWith("+")), "one notice needs no overflow row");
		final PanelLayout.Panel many = StationBoardLayout.layout(resolved(true), BoardView.SERVICE_CHANGE, notices(7), 30.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(many).stream().anyMatch(t -> t.matches("\\+\\d+ more")), "more notices than rows end with a '+N more' row");
	}

	@Test
	void severityMarkersFollowTheMessages() {
		final PanelLayout.Panel panel = StationBoardLayout.layout(resolved(true), BoardView.SERVICE_CHANGE, notices(2), 30.5F, 12.5F, 0xFFFFFFFF, FONT);
		final long markers = panel.rects().stream().filter(r -> r.argb() == 0xFF4C8DDB || r.argb() == 0xFFE0A800).count();
		assertEquals(2, markers);
	}

	@Test
	void exitsViewShowsEveryExitUntilItRunsOutOfRows() {
		final PanelLayout.Panel panel = StationBoardLayout.layout(resolved(true), BoardView.EXITS, List.of(), 46.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(panel).contains("A"));
		assertTrue(texts(panel).stream().anyMatch(t -> t.contains("Market Street")));
		assertTrue(texts(panel).stream().anyMatch(t -> t.matches("\\+\\d+ more")));
		final PanelLayout.Panel empty = StationBoardLayout.layout(resolved(false), BoardView.EXITS, List.of(), 46.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(empty).contains("No exit information"));
	}

	@Test
	void transferViewShowsBadgesAndNote() {
		final PanelLayout.Panel panel = StationBoardLayout.layout(resolved(true), BoardView.TRANSFER, List.of(), 30.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(panel).containsAll(List.of("B", "L", "Change for Regional Rail")));
	}

	@Test
	void trainsAndPlatformViewsCarryTheirHeadingAndTheSharedFacts() {
		final PanelLayout.Panel trains = StationBoardLayout.layout(resolved(true), BoardView.TRAINS_THIS_SIDE, List.of(), 30.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(trains).contains("Trains this side"));
		assertTrue(texts(trains).stream().anyMatch(t -> t.contains("Frankford")));
		final PanelLayout.Panel platform = StationBoardLayout.layout(resolved(true), BoardView.PLATFORM_TRACK, List.of(), 30.5F, 12.5F, 0xFFFFFFFF, FONT);
		assertTrue(texts(platform).contains("Platform"));
		assertTrue(texts(platform).contains("2"));
	}
}
