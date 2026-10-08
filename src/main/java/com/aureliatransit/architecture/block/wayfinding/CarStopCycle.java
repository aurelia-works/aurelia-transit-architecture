package com.aureliatransit.architecture.block.wayfinding;

/**
 * The number on a car stop board, stepped one at a time and wrapping at either end.
 */
public final class CarStopCycle {

	private CarStopCycle() {
	}

	/**
	 * @param value     the current number, between {@code min} and {@code max}
	 * @param backwards true to count down (sneak-right-click)
	 */
	public static int step(int value, int min, int max, boolean backwards) {
		final int span = max - min + 1;
		final int offset = Math.floorMod(value - min + (backwards ? -1 : 1), span);
		return min + offset;
	}
}
