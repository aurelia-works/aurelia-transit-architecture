package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Painted letters for runway numbers and gate numbers: a 3 x 5 pixel font of the digits and the three runway letters, drawn as
 * cells of a grid. No Minecraft classes.
 */
public final class AirportGlyphs {

	public static final int GLYPH_WIDTH = 3;
	public static final int GLYPH_HEIGHT = 5;

	private static final Map<Character, String> FONT = Map.ofEntries(
			Map.entry('0', "111101101101111"), Map.entry('1', "010110010010111"), Map.entry('2', "111001111100111"),
			Map.entry('3', "111001111001111"), Map.entry('4', "101101111001001"), Map.entry('5', "111100111001111"),
			Map.entry('6', "111100111101111"), Map.entry('7', "111001001001001"), Map.entry('8', "111101111101111"),
			Map.entry('9', "111101111001111"), Map.entry('L', "100100100100111"), Map.entry('R', "110101110101101"),
			Map.entry('C', "111100100100111"));

	private AirportGlyphs() {
	}

	/** True when the font has this character. */
	public static boolean has(char c) {
		return FONT.containsKey(c);
	}

	/**
	 * The lit pixels of {@code text} in one line, as {@code {x, r}} with x from the left and r from the top row, scaled up by
	 * {@code scale} and one pixel apart. Characters the font lacks are left out.
	 */
	public static List<int[]> pixels(String text, int scale) {
		final List<int[]> out = new ArrayList<>();
		int x0 = 0;
		for (final char c : text.toCharArray()) {
			final String rows = FONT.get(c);
			if (rows == null) {
				continue;
			}
			for (int r = 0; r < GLYPH_HEIGHT; r++) {
				for (int x = 0; x < GLYPH_WIDTH; x++) {
					if (rows.charAt(r * GLYPH_WIDTH + x) == '1') {
						for (int dy = 0; dy < scale; dy++) {
							for (int dx = 0; dx < scale; dx++) {
								out.add(new int[]{x0 + x * scale + dx, r * scale + dy});
							}
						}
					}
				}
			}
			x0 += (GLYPH_WIDTH + 1) * scale;
		}
		return out;
	}

	/** Width in cells of {@code text} at {@code scale}, without the gap after the last letter. */
	public static int width(String text, int scale) {
		int n = 0;
		for (final char c : text.toCharArray()) {
			if (FONT.containsKey(c)) {
				n++;
			}
		}
		return n == 0 ? 0 : n * (GLYPH_WIDTH + 1) * scale - scale;
	}

	public static int height(int scale) {
		return GLYPH_HEIGHT * scale;
	}
}
