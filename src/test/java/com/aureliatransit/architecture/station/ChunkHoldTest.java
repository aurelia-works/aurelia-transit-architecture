package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkHoldTest {

	@Test
	void holdsUpToTheCapWithoutLettingGo() {
		final ChunkHold hold = new ChunkHold(3);
		assertNull(hold.use(1));
		assertNull(hold.use(2));
		assertNull(hold.use(3));
		assertEquals(3, hold.size());
	}

	@Test
	void pastTheCapLetsGoOfTheLeastRecentlyUsed() {
		final ChunkHold hold = new ChunkHold(3);
		hold.use(1);
		hold.use(2);
		hold.use(3);
		// 1 is used again, so 2 is now the oldest
		assertNull(hold.use(1));
		assertEquals(2L, hold.use(4));
		assertFalse(hold.holds(2));
		assertTrue(hold.holds(1));
		assertEquals(3, hold.size());
	}

	@Test
	void aLongWalkStaysWithinTheCap() {
		final ChunkHold hold = new ChunkHold(16);
		int letGo = 0;
		for (long chunk = 0; chunk < 10_000; chunk++) {
			if (hold.use(chunk) != null) {
				letGo++;
			}
			assertTrue(hold.size() <= 16);
		}
		assertEquals(10_000 - 16, letGo);
	}

	@Test
	void clearReturnsEveryChunkOldestFirst() {
		final ChunkHold hold = new ChunkHold(4);
		hold.use(5);
		hold.use(6);
		hold.use(5);
		assertEquals(List.of(6L, 5L), hold.clear());
		assertEquals(0, hold.size());
	}

	@Test
	void capMustBePositive() {
		assertThrows(IllegalArgumentException.class, () -> new ChunkHold(0));
	}
}
