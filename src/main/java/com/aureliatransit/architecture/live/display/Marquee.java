package com.aureliatransit.architecture.live.display;

/**
 * Scrolling for text that is too long for its column, done by moving a character window (no clipping needed in the
 * world) at a capped step rate. The window holds at both ends so the text is readable.
 */
public final class Marquee {

	private Marquee() {
	}

	/**
	 * Index of the first visible character.
	 *
	 * @param textLength   characters in the full text
	 * @param visibleChars characters that fit the column
	 * @param stepMillis   time per one-character step (caps the update rate; must be positive)
	 * @param holdMillis   time to hold at the start and at the end
	 */
	public static int startIndex(long nowMillis, int textLength, int visibleChars, long stepMillis, long holdMillis) {
		final int overflow = textLength - visibleChars;
		if (overflow <= 0 || stepMillis <= 0) {
			return 0;
		}
		final long scrollMillis = overflow * stepMillis;
		final long period = 2 * holdMillis + 2 * scrollMillis;
		final long t = Math.floorMod(nowMillis, period);
		if (t < holdMillis) {
			return 0;
		}
		if (t < holdMillis + scrollMillis) {
			return (int) ((t - holdMillis) / stepMillis);
		}
		if (t < 2 * holdMillis + scrollMillis) {
			return overflow;
		}
		return overflow - (int) ((t - 2 * holdMillis - scrollMillis) / stepMillis);
	}
}
