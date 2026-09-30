package com.aureliatransit.architecture.presentation;

import com.aureliatransit.architecture.live.BoardAlignment;
import com.aureliatransit.architecture.live.display.BoardAnchor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoardAnchorTest {

	@Test
	void topAnchorsUnderTheMarginAndKeepsSpareHeightBelow() {
		assertEquals(1.5F, BoardAnchor.contentTop(BoardAlignment.TOP, 200, 60, 1.5F));
	}

	@Test
	void centerSplitsSpareHeight() {
		assertEquals(70F, BoardAnchor.contentTop(BoardAlignment.CENTER, 200, 60, 1.5F));
	}

	@Test
	void contentTallerThanBoardStartsAtZero() {
		assertEquals(0F, BoardAnchor.contentTop(BoardAlignment.TOP, 50, 60, 1.5F));
		assertEquals(0F, BoardAnchor.contentTop(BoardAlignment.CENTER, 50, 60, 1.5F));
	}

	@Test
	void marginIsNeverNegativeAndNeverExceedsSpare() {
		assertEquals(0F, BoardAnchor.contentTop(BoardAlignment.TOP, 200, 60, -4));
		assertEquals(0.5F, BoardAnchor.contentTop(BoardAlignment.TOP, 60.5F, 60, 1.5F));
	}
}
