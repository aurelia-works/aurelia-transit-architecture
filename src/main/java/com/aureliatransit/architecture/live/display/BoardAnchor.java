package com.aureliatransit.architecture.live.display;

import com.aureliatransit.architecture.live.BoardAlignment;

/**
 * Pure vertical anchoring maths of a live board: where the content block starts inside the screen, in board units.
 * TOP keeps a small bezel margin above the first row and leaves all spare height at the bottom; CENTER splits the spare
 * height evenly. The result is never negative and never pushes content past the bottom when it fits.
 */
public final class BoardAnchor {

	private BoardAnchor() {
	}

	/**
	 * @param boardHeight   usable screen height in board units
	 * @param contentHeight height of the laid-out content block in board units
	 * @param topMargin     desired gap above the content for {@link BoardAlignment#TOP}
	 * @return y of the content's top edge, 0 or more
	 */
	public static float contentTop(BoardAlignment alignment, float boardHeight, float contentHeight, float topMargin) {
		final float spare = Math.max(0, boardHeight - contentHeight);
		if (alignment == BoardAlignment.CENTER) {
			return spare / 2;
		}
		return Math.min(Math.max(0, topMargin), spare);
	}
}
