package com.aureliatransit.architecture.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSanitizerTest {

	@Test
	void stripsFormattingCodesAndControlCharacters() {
		assertEquals("Central", TextSanitizer.sanitize("§cCentral\u0007", 32));
		assertEquals("a b", TextSanitizer.sanitize("a§ b", 32).replace("  ", " "));
	}

	@Test
	void trimsAndCapsLength() {
		assertEquals("abc", TextSanitizer.sanitize("   abc  ", 10));
		assertEquals("abcd", TextSanitizer.sanitize("abcdefgh", 4));
		assertEquals("", TextSanitizer.sanitize(null, 4));
	}

	@Test
	void configurableTextBoundsEveryField() {
		final String huge = "x".repeat(500);
		final List<String> manyRows = java.util.Collections.nCopies(40, huge);
		final ConfigurableTextData data = new ConfigurableTextData(huge, manyRows, null, null);
		assertEquals(ConfigurableTextData.MAX_HEADING, data.heading().length());
		assertEquals(ConfigurableTextData.MAX_BODY_LINES, data.body().size());
		assertTrue(data.body().stream().allMatch(line -> line.length() == ConfigurableTextData.MAX_LINE));
		assertEquals(TextAlignment.LEFT, data.alignment());
	}

	@Test
	void trailingEmptyRowsAreDroppedSoEqualContentIsEqual() {
		final ConfigurableTextData a = new ConfigurableTextData("H", List.of("one", "", " "), TextAlignment.CENTER, AccentPalette.RED);
		final ConfigurableTextData b = new ConfigurableTextData("H", List.of("one"), TextAlignment.CENTER, AccentPalette.RED);
		assertEquals(a, b);
		assertFalse(a.isEmpty());
		assertTrue(new ConfigurableTextData("", List.of("", ""), TextAlignment.LEFT, AccentPalette.NONE).isEmpty());
	}

	@Test
	void enumOrdinalsFromUntrustedInputNeverThrow() {
		assertEquals(SignArrow.NONE, SignArrow.byOrdinal(-1));
		assertEquals(SignArrow.NONE, SignArrow.byOrdinal(999));
		assertEquals(AccentPalette.NONE, AccentPalette.byOrdinal(Integer.MAX_VALUE));
		assertEquals(TextAlignment.CENTER, TextAlignment.byOrdinal(-5));
	}
}
