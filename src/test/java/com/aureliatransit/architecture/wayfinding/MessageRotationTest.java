package com.aureliatransit.architecture.wayfinding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRotationTest {

	private static final long D = MessageRotation.DWELL_MILLIS;

	@Test
	void dwellIsAboutFiveSeconds() {
		assertTrue(D >= 4000 && D <= 6000);
		assertEquals(D, MessageRotation.slotMillis(0));
	}

	@Test
	void zeroOrOneMessageNeverRotates() {
		assertEquals(0, MessageRotation.index(123456789L, new long[0]));
		for (long t = 0; t < 60_000; t += 1000) {
			assertEquals(0, MessageRotation.index(t, new long[] {D}));
		}
	}

	@Test
	void manyMessagesRotateByTimeNotFrames() {
		final long[] slots = {D, D, D};
		assertEquals(0, MessageRotation.index(0, slots));
		assertEquals(0, MessageRotation.index(D - 1, slots));
		assertEquals(1, MessageRotation.index(D, slots));
		assertEquals(2, MessageRotation.index(2 * D, slots));
		assertEquals(0, MessageRotation.index(3 * D, slots), "wraps");
		// repeated evaluation at the same instant (frames) never changes the answer
		for (int frame = 0; frame < 100; frame++) {
			assertEquals(1, MessageRotation.index(D + 10, slots));
		}
		assertEquals(0, MessageRotation.elapsedInSlot(D, slots), "a new message starts at the beginning of its slot");
		assertEquals(77, MessageRotation.elapsedInSlot(D + 77, slots));
	}

	@Test
	void longTextGetsALongerSlotAndScrollsOneWay() {
		final int overflow = 40;
		final long slot = MessageRotation.slotMillis(overflow);
		assertTrue(slot > D);
		assertEquals(0, MessageRotation.scrollStart(0, overflow), "holds at the start");
		assertEquals(0, MessageRotation.scrollStart(MessageRotation.HOLD_MILLIS, overflow));
		assertEquals(1, MessageRotation.scrollStart(MessageRotation.HOLD_MILLIS + MessageRotation.STEP_MILLIS, overflow));
		int previous = 0;
		for (long t = 0; t < slot; t += 20) {
			final int start = MessageRotation.scrollStart(t, overflow);
			assertTrue(start >= previous && start - previous <= 1, "monotonic, one character at a time");
			previous = start;
		}
		assertEquals(overflow, MessageRotation.scrollStart(slot - 1, overflow), "reaches the end of the text inside its slot");
		assertEquals(0, MessageRotation.scrollStart(5000, 0), "text that fits never scrolls");
	}

	@Test
	void scrollRestartsWhenTheNextMessageStarts() {
		final long[] slots = {MessageRotation.slotMillis(30), D};
		final long boundary = slots[0];
		assertEquals(0, MessageRotation.index(boundary - 1, slots));
		assertEquals(1, MessageRotation.index(boundary, slots));
		assertEquals(0, MessageRotation.scrollStart(MessageRotation.elapsedInSlot(boundary, slots), 0));
		assertEquals(0, MessageRotation.index(boundary + D, slots));
		assertEquals(0, MessageRotation.scrollStart(MessageRotation.elapsedInSlot(boundary + D, slots), 30), "scrolling message restarts at its beginning");
	}
}
