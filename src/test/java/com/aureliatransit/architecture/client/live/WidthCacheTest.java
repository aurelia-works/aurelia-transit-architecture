package com.aureliatransit.architecture.client.live;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The width memo is per font, bounded, and never answers for a string it has not seen. */
class WidthCacheTest {

	@Test
	void remembersAStringPerFont() {
		final WidthCache cache = new WidthCache(2);
		assertNull(cache.string(0, "12:34"));
		cache.putString(0, "12:34", 25F);
		assertEquals(25F, cache.string(0, "12:34"));
		assertNull(cache.string(1, "12:34"));
	}

	@Test
	void forgetsTheLeastRecentlyUsedStringPastTheBound() {
		final WidthCache cache = new WidthCache(1);
		for (int i = 0; i < WidthCache.MAX_STRINGS; i++) {
			cache.putString(0, "s" + i, i);
		}
		assertNotNull(cache.string(0, "s0"));
		cache.putString(0, "overflow", 1F);
		assertNotNull(cache.string(0, "s0"), "s0 was used last, so it stays");
		assertNull(cache.string(0, "s1"), "s1 was the oldest");
		assertNotNull(cache.string(0, "overflow"));
	}

	@Test
	void remembersCharactersInTheTableOnly() {
		final WidthCache cache = new WidthCache(1);
		assertTrue(Float.isNaN(cache.character(0, 'a')));
		cache.putCharacter(0, 'a', 6F);
		assertEquals(6F, cache.character(0, 'a'));
		cache.putCharacter(0, '中', 9F);
		assertTrue(Float.isNaN(cache.character(0, '中')), "characters past the table are measured each time");
	}

	@Test
	void aZeroWidthCharacterIsRemembered() {
		final WidthCache cache = new WidthCache(1);
		cache.putCharacter(0, '\u0001', 0F);
		assertEquals(0F, cache.character(0, '\u0001'));
	}
}
