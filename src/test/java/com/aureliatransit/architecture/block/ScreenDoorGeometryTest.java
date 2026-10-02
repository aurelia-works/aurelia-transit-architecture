package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.ScreenCurveKind;
import com.aureliatransit.architecture.block.elevated.ScreenDoorGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenDoorGeometryTest {

	@Test
	void everyBoxStaysInsideTheBlock() {
		for (final ScreenCurveKind kind : ScreenCurveKind.values()) {
			for (final boolean doorway : new boolean[]{false, true}) {
				final List<double[]> boxes = ScreenDoorGeometry.boxes(kind, doorway);
				assertTrue(!boxes.isEmpty(), kind + " " + doorway);
				for (final double[] b : boxes) {
					assertTrue(b[0] >= 0 && b[3] <= 16 && b[2] >= 0 && b[5] <= 16 && b[1] >= 0 && b[4] <= 16 && b[0] < b[3] && b[1] < b[4] && b[2] < b[5], kind + " box out of block");
				}
			}
		}
	}

	@Test
	void curvedScreensFollowTheEdgeLineWithoutGaps() {
		// 45 degree: consecutive strips overlap in x, so the screen has no see-through corners
		final List<double[]> strips = ScreenDoorGeometry.boxes(ScreenCurveKind.DIAGONAL, false);
		for (int i = 1; i < strips.size(); i++) {
			assertTrue(strips.get(i)[0] <= strips.get(i - 1)[3], "strip " + i + " leaves a gap");
			assertEquals(strips.get(i - 1)[5], strips.get(i)[2], 1e-9, "strips are contiguous in z");
		}
	}

	@Test
	void doorwayLeavesTheOpeningFree() {
		final List<double[]> straight = ScreenDoorGeometry.boxes(ScreenCurveKind.STRAIGHT, true);
		for (final double[] b : straight) {
			final boolean post = b[3] <= 1.5 || b[0] >= 14.5;
			assertTrue(post || b[1] >= ScreenDoorGeometry.HEADER_BOTTOM, "only posts reach the floor");
		}
		final List<double[]> curve = ScreenDoorGeometry.boxes(ScreenCurveKind.OUTER, true);
		long fullHeight = curve.stream().filter(b -> b[1] == 0).count();
		assertEquals(2, fullHeight, "two end posts");
	}
}
