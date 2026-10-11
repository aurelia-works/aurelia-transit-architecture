package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlatformLayout;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preview markers stay inside the station's footprint and within the particle budget, for every heading.
 */
class PreviewGeometryTest {

	@Test
	void markersStayInsideTheFootprintAndBudget() {
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().trackCount(4).platformLayout(PlatformLayout.ISLAND)
				.platformLength(StationSpec.MAX_LENGTH).build());
		for (final Heading heading : Heading.values()) {
			final LocalFrame frame = LocalFrame.inFrontOf(100, 64, -50, heading, plan.layout());
			final PreviewGeometry geometry = new PreviewGeometry(plan, frame, List.of());
			final int x1 = frame.x(0, plan.layout().vMin());
			final int x2 = frame.x(plan.layout().width() - 1, plan.layout().vMax());
			final int z1 = frame.z(0, plan.layout().vMin());
			final int z2 = frame.z(plan.layout().width() - 1, plan.layout().vMax());
			for (final PreviewGeometry.Layer layer : PreviewGeometry.Layer.values()) {
				for (final PreviewGeometry.Point point : geometry.all(layer)) {
					assertTrue(point.x() >= Math.min(x1, x2) && point.x() <= Math.max(x1, x2) + 1, heading + " " + layer + " x " + point);
					assertTrue(point.z() >= Math.min(z1, z2) && point.z() <= Math.max(z1, z2) + 1, heading + " " + layer + " z " + point);
				}
			}
			for (int refresh = 0; refresh < 4; refresh++) {
				int total = 0;
				for (final PreviewGeometry.Layer layer : PreviewGeometry.Layer.values()) {
					final int size = geometry.sample(layer, refresh).size();
					assertTrue(size <= layer.budget(), layer + " over budget: " + size);
					total += size;
				}
				assertTrue(total <= PreviewGeometry.MAX_PER_REFRESH, "refresh " + refresh + ": " + total);
			}
			assertFalse(geometry.all(PreviewGeometry.Layer.NODE).isEmpty());
			assertEquals(8, geometry.all(PreviewGeometry.Layer.NODE).size());
		}
	}
}
