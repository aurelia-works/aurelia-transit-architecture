package com.aureliatransit.architecture.client.live;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers text widths per font so a board rebuild does not build a {@code Text} literal for every probe. Bounded: each
 * font keeps at most {@link #MAX_STRINGS} strings (least recently used go first) and a table for the first
 * {@link #CHAR_TABLE} characters. Owned by one renderer, which Minecraft recreates on a resource reload, so a changed
 * font never leaves stale widths behind. Render thread only.
 */
final class WidthCache {

	static final int MAX_STRINGS = 512;
	static final int CHAR_TABLE = 256;

	private final Map<String, Float>[] strings;
	private final float[][] chars;

	@SuppressWarnings("unchecked")
	WidthCache(int fonts) {
		strings = new Map[fonts];
		chars = new float[fonts][CHAR_TABLE];
		for (int i = 0; i < fonts; i++) {
			strings[i] = new LinkedHashMap<>(64, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
					return size() > MAX_STRINGS;
				}
			};
			Arrays.fill(chars[i], Float.NaN);
		}
	}

	/** The remembered width of {@code text}, or null. */
	Float string(int font, String text) {
		return strings[font].get(text);
	}

	void putString(int font, String text, float width) {
		strings[font].put(text, width);
	}

	/** The remembered width of one character, or NaN when unknown or beyond the table. */
	float character(int font, char c) {
		return c < CHAR_TABLE ? chars[font][c] : Float.NaN;
	}

	void putCharacter(int font, char c, float width) {
		if (c < CHAR_TABLE) {
			chars[font][c] = width;
		}
	}
}
