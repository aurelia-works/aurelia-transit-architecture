package com.aureliatransit.architecture.wayfinding;

/**
 * Pure timing model of a rotating message strip. Wall-clock based (never frame based): the same instant always gives
 * the same message and scroll position, so it is safe to evaluate whenever a board is rebuilt.
 *
 * <p>Each message owns a slot of {@link #slotMillis}: the dwell time, extended when the text has to scroll so the whole
 * text is read. Inside a slot the text holds at its start, scrolls one way to its end, then holds again; the next
 * message starts from its beginning.
 */
public final class MessageRotation {

	public static final long DWELL_MILLIS = 5000;
	public static final long HOLD_MILLIS = 1200;
	/** Time per one-character scroll step; short enough to read as continuous movement. */
	public static final long STEP_MILLIS = 140;

	private MessageRotation() {
	}

	/** Slot length for a message whose text overflows its strip by {@code overflowChars} characters (0 = fits). */
	public static long slotMillis(int overflowChars) {
		if (overflowChars <= 0) {
			return DWELL_MILLIS;
		}
		return Math.max(DWELL_MILLIS, 2 * HOLD_MILLIS + overflowChars * STEP_MILLIS);
	}

	/** Index of the message shown at {@code now}: always 0 for zero or one message. */
	public static int index(long now, long[] slots) {
		if (slots.length <= 1) {
			return 0;
		}
		long cycle = 0;
		for (final long slot : slots) {
			cycle += slot;
		}
		long t = Math.floorMod(now, cycle);
		for (int i = 0; i < slots.length; i++) {
			if (t < slots[i]) {
				return i;
			}
			t -= slots[i];
		}
		return slots.length - 1;
	}

	/** Milliseconds elapsed inside the current slot at {@code now}; 0 for zero or one message (no rotation to reset). */
	public static long elapsedInSlot(long now, long[] slots) {
		if (slots.length <= 1) {
			return Math.floorMod(now, Math.max(1, slots.length == 1 ? slots[0] : 1));
		}
		long cycle = 0;
		for (final long slot : slots) {
			cycle += slot;
		}
		long t = Math.floorMod(now, cycle);
		for (final long slot : slots) {
			if (t < slot) {
				return t;
			}
			t -= slot;
		}
		return 0;
	}

	/**
	 * Index of the first visible character for a one-way scroll: hold, advance one character per {@link #STEP_MILLIS},
	 * hold at the end.
	 */
	public static int scrollStart(long elapsed, int overflowChars) {
		if (overflowChars <= 0 || elapsed < HOLD_MILLIS) {
			return 0;
		}
		return (int) Math.min(overflowChars, (elapsed - HOLD_MILLIS) / STEP_MILLIS);
	}

	/**
	 * Slot for a message {@code width} units wide that scrolls at {@code speed} units a second: long enough to hold, scroll
	 * the whole text past and hold again, at least {@code dwell}. From the text's width alone, so boards of every size
	 * change message together.
	 */
	public static long readSlotMillis(float width, float speed, long dwell) {
		return Math.max(dwell, 2 * HOLD_MILLIS + (long) Math.ceil(width / speed * 1000));
	}

	/**
	 * How far a text {@code overflow} units wider than its strip has moved {@code elapsed} ms into its slot: held at its
	 * start, then {@code speed} units a second, held at its end. 0 when it fits.
	 */
	public static float readOffset(long elapsed, float overflow, float speed) {
		if (overflow <= 0 || elapsed <= HOLD_MILLIS) {
			return 0;
		}
		return Math.min(overflow, (elapsed - HOLD_MILLIS) * speed / 1000F);
	}
}
