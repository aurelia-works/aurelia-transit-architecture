package com.aureliatransit.architecture.text;

import com.aureliatransit.architecture.transit.StationNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the sign/info layout and name handling with Latin, CJK, Cyrillic and Arabic-script names. Widths model
 * Minecraft's font: roughly 6 units per Latin/Cyrillic/Arabic glyph, 9 per CJK glyph (unifont fallback).
 */
class InternationalTextTest {

	private static final ToIntFunction<String> FONT = s -> s.codePoints().map(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
			|| Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA
			|| Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL ? 9 : 6).sum();

	private static final List<String> NAMES = List.of(
			"Frankfurt (Main) Hauptbahnhof",
			"Łódź Fabryczna",
			"東京",
			"新宿三丁目",
			"서울역",
			"Москва Курская",
			"Санкт-Петербург-Главный",
			"القاهرة",
			"محطة مصر الرئيسية",
			"تهران",
			"𠮷野家前");

	private static void assertInside(PanelLayout.Panel panel, float w, float h) {
		for (final PanelLayout.Label label : panel.labels()) {
			final float half = label.widthUnits() * label.scale() / 2;
			assertTrue(label.cx() - half >= -w / 2 - 0.01F && label.cx() + half <= w / 2 + 0.01F, "label '" + label.text() + "' overflows horizontally");
			final float halfH = TextFit.ROW * label.scale() / 2;
			assertTrue(label.cy() - halfH >= -h / 2 - 0.01F && label.cy() + halfH <= h / 2 + 0.01F, "label '" + label.text() + "' overflows vertically");
			assertTrue(label.scale() > 0, "label '" + label.text() + "' has no size");
		}
	}

	@Test
	void everyScriptFitsEverySignStyleWithoutLosingText() {
		for (final SignStyle style : SignStyle.values()) {
			for (final String name : NAMES) {
				final SignData data = new SignData(name, "Ausgang | Выход | 出口 | مخرج", TextAlignment.CENTER, AccentPalette.BLUE, SignArrow.LEFT, "12",
						false, List.of(new RouteBadge("S8", AccentPalette.GREEN), new RouteBadge("線", AccentPalette.RED))).restrictedTo(style);
				for (final float width : new float[]{13.5F, 29.5F, 61.5F}) {
					final PanelLayout.Panel panel = PanelLayout.sign(data, data.primary(), style, width, 8, FONT);
					assertInside(panel, width, 8);
					assertEquals(panel, PanelLayout.sign(data, data.primary(), style, width, 8, FONT), "layout is deterministic");
					// A one-block sign that also carries a platform badge and an arrow has no width left for the name in any
					// script (existing layout rule, see the 1.1 certification report); from two joined blocks up the name must show.
					if (!data.primary().isEmpty() && width > 13.5F) {
						assertTrue(panel.labels().stream().anyMatch(l -> l.text().equals(data.primary())), style + " w=" + width + ": '" + name + "' was altered or dropped: " + panel.labels());
					}
				}
			}
		}
	}

	@Test
	void informationTextWithMixedScriptsStaysInside() {
		final ConfigurableTextData data = new ConfigurableTextData("Информация · 案内 · معلومات", List.of(NAMES.get(0), NAMES.get(3), NAMES.get(6), NAMES.get(8)),
				TextAlignment.LEFT, AccentPalette.TEAL);
		for (final PanelLayout.InfoStyle style : new PanelLayout.InfoStyle[]{new PanelLayout.InfoStyle(0xFFEDEDE8, 0xFF1B3A5E, 0.8F, 6), new PanelLayout.InfoStyle(0xFF23272B, 0xFFF2C25A, 0.7F, 8)}) {
			final PanelLayout.Panel panel = PanelLayout.info(data, style, 12, 11, FONT);
			assertInside(panel, 12, 11);
			assertFalse(panel.labels().isEmpty());
		}
	}

	@Test
	void sanitizerKeepsScriptsAndNeverSplitsSurrogatePairs() {
		for (final String name : NAMES) {
			assertEquals(name, TextSanitizer.sanitize(name, 64), "sanitizer altered '" + name + "'");
		}
		// "𠮷" is one code point stored as two UTF-16 units; a cap landing between them drops it whole.
		assertEquals("", TextSanitizer.sanitize("𠮷野家", 1));
		assertEquals("𠮷", TextSanitizer.sanitize("𠮷野家", 2));
		final String cut = TextSanitizer.sanitize("ab𠮷", 3);
		assertEquals("ab", cut);
		assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - 1)));
		// Arabic right-to-left marks are formatting (Cf), not control characters, and are kept.
		assertEquals("‏محطة", TextSanitizer.sanitize("‏محطة", 16));
	}

	@Test
	void multilingualMtrNamesPreferLatinThenNonCjk() {
		assertEquals("Central", StationNames.display("中環|Central"));
		assertEquals("Moscow", StationNames.display("Москва|Moscow"));
		assertEquals("Cairo", StationNames.display("القاهرة|Cairo"));
		assertEquals("Cairo", StationNames.display("Cairo|القاهرة"));
		assertEquals("Москва", StationNames.display("Москва"));
		assertEquals("Москва", StationNames.display("東京|Москва"));
		assertEquals("القاهرة", StationNames.display("القاهرة"));
		assertEquals("東京", StationNames.display("東京"));
		assertEquals("S-Bahn 1", StationNames.display("S-Bahn 1|エスバーン"));
		assertEquals("", StationNames.display("|"));
		assertEquals("", StationNames.display(""));
		assertEquals("", StationNames.display(null));
	}

	@Test
	void signDataWithInternationalTextRoundTripsUnchanged() {
		final SignData data = new SignData("Санкт-Петербург", "東京 · القاهرة", TextAlignment.RIGHT, AccentPalette.PURPLE, SignArrow.RIGHT, "٣",
				true, List.of(new RouteBadge("М1", AccentPalette.RED)));
		assertSame(data.primary(), data.primary());
		assertEquals(data, new SignData(data.primary(), data.secondary(), data.alignment(), data.accent(), data.arrow(), data.platform(), data.autoName(), data.routes()));
	}
}
