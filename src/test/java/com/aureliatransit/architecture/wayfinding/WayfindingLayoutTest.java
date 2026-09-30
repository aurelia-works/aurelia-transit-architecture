package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.TextFit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every panel kind at several sizes with Latin, CJK, Cyrillic and Arabic-script text: nothing leaves the panel, nothing
 * overlaps, the result is deterministic.
 */
class WayfindingLayoutTest {

	private static final ToIntFunction<String> FONT = s -> s.codePoints().map(c -> {
		final Character.UnicodeScript script = Character.UnicodeScript.of(c);
		return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
				|| script == Character.UnicodeScript.HANGUL ? 9 : 6;
	}).sum();

	private static final List<String> NAMES = List.of(
			"Frankfurt (Main) Hauptbahnhof",
			"City Hall",
			"東京",
			"新宿三丁目",
			"Москва Курская",
			"القاهرة",
			"محطة مصر الرئيسية",
			"");

	private static final float[][] SIZES = {{13.5F, 8}, {29.5F, 8}, {61.5F, 8}, {16, 16}, {29.5F, 16}, {16, 48}, {16, 32}, {45, 24}, {4, 4}, {2, 2}};

	private static ResolvedWayfinding sample(String name, LanguageLayout layout, Pictogram pictogram, AccentPalette accent) {
		return new ResolvedWayfinding(name, name.isEmpty() ? "" : "Zweiter Name 次", "MFL 15",
				List.of(new LineBadge("L", 0x0066CC, BadgeShape.ROUNDED), new LineBadge("BSL", 0xFF8800, BadgeShape.CIRCLE), new LineBadge("15", 0x777777, BadgeShape.SQUARE),
						new LineBadge("線", 0xCC0000, BadgeShape.ROUNDED)),
				SignArrow.UP_LEFT, name.isEmpty() ? "To Frankford" : name, ServiceType.EXPRESS, "Express", "2", "B", List.of("City Hall", name, "Library"), "Market Street",
				"Transfer to Regional Rail", layout, pictogram, accent, true);
	}

	private static void assertInside(PanelLayout.Panel panel, float w, float h, String context) {
		for (final PanelLayout.Label label : panel.labels()) {
			final float half = label.widthUnits() * label.scale() / 2;
			assertTrue(label.scale() > 0, context + ": label '" + label.text() + "' has no size");
			assertTrue(label.cx() - half >= -w / 2 - 0.01F && label.cx() + half <= w / 2 + 0.01F, context + ": label '" + label.text() + "' overflows horizontally");
			final float halfH = TextFit.ROW * label.scale() / 2;
			assertTrue(label.cy() - halfH >= -h / 2 - 0.01F && label.cy() + halfH <= h / 2 + 0.01F, context + ": label '" + label.text() + "' overflows vertically");
		}
		for (final PanelLayout.Rect rect : panel.rects()) {
			assertTrue(rect.w() > 0 && rect.h() > 0, context + ": degenerate rect");
			assertTrue(rect.cx() - rect.w() / 2 >= -w / 2 - 0.01F && rect.cx() + rect.w() / 2 <= w / 2 + 0.01F, context + ": rect overflows horizontally " + rect);
			assertTrue(rect.cy() - rect.h() / 2 >= -h / 2 - 0.01F && rect.cy() + rect.h() / 2 <= h / 2 + 0.01F, context + ": rect overflows vertically " + rect);
		}
	}

	/**
	 * Rectangles of different colours must not intersect (the drawer draws them in one plane: they would z-fight).
	 */
	private static void assertNoMixedOverlap(PanelLayout.Panel panel, String context) {
		final List<PanelLayout.Rect> rects = panel.rects();
		for (int i = 0; i < rects.size(); i++) {
			for (int j = i + 1; j < rects.size(); j++) {
				final PanelLayout.Rect a = rects.get(i);
				final PanelLayout.Rect b = rects.get(j);
				if (a.argb() == b.argb()) {
					continue;
				}
				final float overlapX = Math.min(a.cx() + a.w() / 2, b.cx() + b.w() / 2) - Math.max(a.cx() - a.w() / 2, b.cx() - b.w() / 2);
				final float overlapY = Math.min(a.cy() + a.h() / 2, b.cy() + b.h() / 2) - Math.max(a.cy() - a.h() / 2, b.cy() - b.h() / 2);
				assertFalse(overlapX > 0.02F && overlapY > 0.02F, context + ": differently coloured rects overlap " + a + " / " + b);
			}
		}
	}

	@Test
	void everyKindFitsEverySizeAndScript() {
		for (final WayfindingPanelKind kind : WayfindingPanelKind.values()) {
			for (final String name : NAMES) {
				for (final LanguageLayout layout : LanguageLayout.values()) {
					for (final Pictogram pictogram : new Pictogram[]{Pictogram.NONE, Pictogram.EXIT, Pictogram.ELEVATOR}) {
						for (final AccentPalette accent : new AccentPalette[]{AccentPalette.NONE, AccentPalette.BLUE}) {
							final ResolvedWayfinding r = sample(name, layout, pictogram, accent);
							for (final float[] size : SIZES) {
								final String context = kind + " '" + name + "' " + layout + " " + pictogram + " " + size[0] + "x" + size[1];
								final PanelLayout.Panel panel = WayfindingLayout.layout(r, kind, size[0], size[1], 0xFFFFFFFF, FONT);
								assertInside(panel, size[0], size[1], context);
								assertNoMixedOverlap(panel, context);
								assertEquals(panel, WayfindingLayout.layout(r, kind, size[0], size[1], 0xFFFFFFFF, FONT), context + ": deterministic");
							}
						}
					}
				}
			}
		}
	}

	@Test
	void everyPictogramFitsItsCell() {
		for (final Pictogram pictogram : Pictogram.values()) {
			final ResolvedWayfinding r = new ResolvedWayfinding("", "", "", List.of(), SignArrow.NONE, "", ServiceType.NONE, "", "", "", List.of(), "", "",
					LanguageLayout.SINGLE, pictogram, AccentPalette.NONE, false);
			for (final float[] size : new float[][]{{8, 8}, {16, 16}, {30, 12}}) {
				final PanelLayout.Panel panel = WayfindingLayout.layout(r, WayfindingPanelKind.PICTOGRAM, size[0], size[1], 0xFFFFFFFF, FONT);
				assertInside(panel, size[0], size[1], pictogram + " " + size[0] + "x" + size[1]);
				assertEquals(pictogram != Pictogram.NONE, !panel.isEmpty(), pictogram + " drawn only when set");
			}
		}
	}

	@Test
	void emptyResolvedDrawsNothing() {
		final ResolvedWayfinding empty = new ResolvedWayfinding("", "", "", List.of(), SignArrow.NONE, "", ServiceType.NONE, "", "", "", List.of(), "", "",
				LanguageLayout.SINGLE, Pictogram.NONE, AccentPalette.NONE, false);
		for (final WayfindingPanelKind kind : WayfindingPanelKind.values()) {
			assertTrue(WayfindingLayout.layout(empty, kind, 30, 16, 0xFFFFFFFF, FONT).isEmpty(), kind + " with no content");
		}
	}

	@Test
	void sideBySideSplitsTheNameAreaWithADivider() {
		final ResolvedWayfinding r = new ResolvedWayfinding("City Hall", "Rathaus", "", List.of(), SignArrow.NONE, "", ServiceType.NONE, "", "", "", List.of(), "", "",
				LanguageLayout.SIDE_BY_SIDE, Pictogram.NONE, AccentPalette.NONE, true);
		final PanelLayout.Panel panel = WayfindingLayout.layout(r, WayfindingPanelKind.WALL_DIRECTION, 61.5F, 12, 0xFFFFFFFF, FONT);
		final PanelLayout.Label primary = label(panel, "City Hall");
		final PanelLayout.Label secondary = label(panel, "Rathaus");
		assertTrue(primary.cx() < 0 && secondary.cx() > 0, "primary left, secondary right");
		assertEquals(primary.cy(), secondary.cy(), 0.001F, "same baseline");
		assertEquals(1, panel.rects().size(), "one thin divider");
		assertTrue(panel.rects().get(0).w() < 1F);
	}

	@Test
	void stackedPutsTheSecondaryBelowAndSmaller() {
		final ResolvedWayfinding r = new ResolvedWayfinding("Central", "Zentral", "", List.of(), SignArrow.NONE, "", ServiceType.NONE, "", "", "", List.of(), "", "",
				LanguageLayout.STACKED, Pictogram.NONE, AccentPalette.NONE, true);
		final PanelLayout.Panel panel = WayfindingLayout.layout(r, WayfindingPanelKind.ENTRANCE_PYLON, 16, 32, 0xFFFFFFFF, FONT);
		final PanelLayout.Label primary = label(panel, "Central");
		final PanelLayout.Label secondary = label(panel, "Zentral");
		assertTrue(secondary.cy() < primary.cy());
		assertTrue(secondary.scale() < primary.scale());
	}

	@Test
	void singleLayoutIgnoresTheSecondaryName() {
		final ResolvedWayfinding r = new ResolvedWayfinding("City Hall", "Rathaus", "", List.of(), SignArrow.NONE, "", ServiceType.NONE, "", "", "", List.of(), "", "",
				LanguageLayout.SINGLE, Pictogram.NONE, AccentPalette.NONE, true);
		final PanelLayout.Panel panel = WayfindingLayout.layout(r, WayfindingPanelKind.WALL_DIRECTION, 30, 8, 0xFFFFFFFF, FONT);
		assertTrue(panel.labels().stream().noneMatch(l -> l.text().equals("Rathaus")));
	}

	@Test
	void longNamesOnTallPylonsWrapOnWordsInsteadOfShrinkingToNothing() {
		final ResolvedWayfinding r = new ResolvedWayfinding("Frankford Transportation Center", "", "", List.of(), SignArrow.NONE, "", ServiceType.NONE, "", "", "",
				List.of(), "", "", LanguageLayout.SINGLE, Pictogram.NONE, AccentPalette.NONE, true);
		final PanelLayout.Panel tall = WayfindingLayout.layout(r, WayfindingPanelKind.ENTRANCE_PYLON, 16, 48, 0xFFFFFFFF, FONT);
		assertTrue(tall.labels().size() >= 2, "wrapped: " + tall.labels());
		assertEquals("Frankford Transportation Center", String.join(" ", tall.labels().stream().map(PanelLayout.Label::text).toList()), "text is not altered");
	}

	@Test
	void contentKindsShowTheirFields() {
		final ResolvedWayfinding r = sample("City Hall", LanguageLayout.SINGLE, Pictogram.NONE, AccentPalette.NONE);
		assertTrue(texts(WayfindingLayout.layout(r, WayfindingPanelKind.PLATFORM, 61.5F, 16, 0xFFFFFFFF, FONT)).contains("2"), "platform badge");
		assertTrue(texts(WayfindingLayout.layout(r, WayfindingPanelKind.EXIT, 61.5F, 16, 0xFFFFFFFF, FONT)).contains("B"), "exit badge");
		assertTrue(texts(WayfindingLayout.layout(r, WayfindingPanelKind.EXIT, 61.5F, 16, 0xFFFFFFFF, FONT)).contains("Library") || texts(
				WayfindingLayout.layout(r, WayfindingPanelKind.EXIT, 61.5F, 16, 0xFFFFFFFF, FONT)).stream().anyMatch(t -> t.contains("Library")), "exit destinations");
		assertTrue(texts(WayfindingLayout.layout(r, WayfindingPanelKind.WALL_DIRECTION, 61.5F, 16, 0xFFFFFFFF, FONT)).stream().anyMatch("express"::equalsIgnoreCase), "service tag");
		assertTrue(texts(WayfindingLayout.layout(r, WayfindingPanelKind.WALL_DIRECTION, 93.5F, 16, 0xFFFFFFFF, FONT)).contains("EXPRESS"), "service tag on a wide sign");
		assertTrue(texts(WayfindingLayout.layout(r, WayfindingPanelKind.ENTRANCE_PYLON, 16, 48, 0xFFFFFFFF, FONT)).contains("L"), "line badge");
		assertTrue(texts(WayfindingLayout.layout(r, WayfindingPanelKind.STREET, 61.5F, 16, 0xFFFFFFFF, FONT)).contains("Market Street"), "street");
	}

	private static List<String> texts(PanelLayout.Panel panel) {
		return panel.labels().stream().map(PanelLayout.Label::text).toList();
	}

	private static PanelLayout.Label label(PanelLayout.Panel panel, String text) {
		return panel.labels().stream().filter(l -> l.text().equals(text)).findFirst().orElseThrow(() -> new AssertionError("no label '" + text + "' in " + panel.labels()));
	}
}
