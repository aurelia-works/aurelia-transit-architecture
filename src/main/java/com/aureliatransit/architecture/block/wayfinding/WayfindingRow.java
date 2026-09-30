package com.aureliatransit.architecture.block.wayfinding;

import java.util.function.IntPredicate;

/**
 * Pure join maths of a row of side-by-side panels: the row is walked from its owner (leftmost) block.
 */
public final class WayfindingRow {

	private WayfindingRow() {
	}

	/**
	 * Number of blocks in the row, at least 1 and at most {@code maxRow}.
	 *
	 * @param connectsRight whether the block at the given index (0 = owner) is joined to a block on its right
	 */
	public static int length(int maxRow, IntPredicate connectsRight) {
		int length = 1;
		while (length < maxRow && connectsRight.test(length - 1)) {
			length++;
		}
		return length;
	}
}
