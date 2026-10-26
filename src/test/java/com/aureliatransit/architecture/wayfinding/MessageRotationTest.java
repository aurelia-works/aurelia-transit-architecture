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

	@Test
	void aLongMessageIsReadToItsEndBeforeTheNextOne() {
		final float speed = 24;
		final float width = 400;
		final float window = 150;
		final long slot = MessageRotation.readSlotMillis(width, speed, 6000);
		assertEquals(0, MessageRotation.readOffset(0, width - window, speed), "starts at the left edge, not entering from the right");
		assertEquals(0, MessageRotation.readOffset(MessageRotation.HOLD_MILLIS, width - window, speed), "holds before it moves");
		float previous = 0;
		for (long t = 0; t < slot; t += 20) {
			final float offset = MessageRotation.readOffset(t, width - window, speed);
			assertTrue(offset >= previous && offset <= width - window, "one way, never past its end");
			previous = offset;
		}
		assertEquals(width - window, MessageRotation.readOffset(slot - MessageRotation.HOLD_MILLIS, width - window, speed), 1e-3F,
				"the end has been reached with a hold left before the next message");
		assertEquals(0, MessageRotation.readOffset(5000, -10, speed), "text that fits never moves");
	}

	@Test
	void readSlotsDependOnTheTextNotTheBoard() {
		assertEquals(6000, MessageRotation.readSlotMillis(50, 24, 6000), "short text keeps the dwell");
		assertEquals(MessageRotation.readSlotMillis(400, 24, 6000), MessageRotation.readSlotMillis(400, 24, 6000));
		assertTrue(MessageRotation.readSlotMillis(800, 24, 6000) > MessageRotation.readSlotMillis(400, 24, 6000));
	}
}
