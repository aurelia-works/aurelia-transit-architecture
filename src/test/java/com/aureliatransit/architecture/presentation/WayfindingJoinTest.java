package com.aureliatransit.architecture.presentation;

import com.aureliatransit.architecture.block.wayfinding.WayfindingPanelSpec;
import com.aureliatransit.architecture.block.wayfinding.WayfindingRow;
import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingPanelKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WayfindingJoinTest {

	private static WayfindingPanelSpec spec(boolean joins, Pictogram pictogram) {
		return new WayfindingPanelSpec(WayfindingPanelKind.WALL_DIRECTION, 8, 8, 9, 8, 13, -1, joins, 0xFFFFFFFF, 0xFF000000, pictogram);
	}

	@Test
	void rowLengthFollowsRightLinksAndIsCapped() {
		assertEquals(1, WayfindingRow.length(32, i -> false));
		assertEquals(3, WayfindingRow.length(32, i -> i < 2));
		assertEquals(32, WayfindingRow.length(32, i -> true));
		assertEquals(5, WayfindingRow.length(5, i -> true));
		assertEquals(1, WayfindingRow.length(1, i -> true));
	}

	@Test
	void joinedPanelsSpanTheRowMinusTheMargin() {
		final WayfindingPanelSpec joined = spec(true, Pictogram.NONE);
		assertEquals(16 - WayfindingPanelSpec.JOIN_MARGIN, joined.panelWidth(1), 0.001F);
		assertEquals(48 - WayfindingPanelSpec.JOIN_MARGIN, joined.panelWidth(3), 0.001F);
		assertEquals(24F, joined.panelCenterX(3), 0.001F);
		assertEquals(8F, joined.panelCenterX(0), 0.001F);
	}

	@Test
	void singleBlockPanelsIgnoreTheRowLength() {
		final WayfindingPanelSpec single = spec(false, Pictogram.NONE);
		assertEquals(9F, single.panelWidth(4), 0.001F);
		assertEquals(8F, single.panelCenterX(4), 0.001F);
	}

	@Test
	void defaultsCarryTheBlocksPictogram() {
		assertSame(WayfindingData.EMPTY, spec(false, Pictogram.NONE).defaults());
		assertEquals(Pictogram.ACCESSIBLE_ROUTE, spec(false, Pictogram.ACCESSIBLE_ROUTE).defaults().pictogram());
		assertEquals(true, spec(false, Pictogram.EXIT).defaults().autoStation());
	}
}
