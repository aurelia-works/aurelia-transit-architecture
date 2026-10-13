package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.studio.StudioApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Up escalators beside the footbridge stairs: only when asked for, only on platforms wide enough, level with the stair. */
class EscalatorTest {

	private static StationSpec spec(String text) {
		return StationDescriptionParser.parse(text).spec();
	}

	private static long steps(StationPlan plan) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(StylePalette.MTR_ESCALATOR_STEP)).count();
	}

	@Test
	void wordsAndStudioAskForEscalators() {
		assertFalse(spec("4 tracks island footbridge").escalators());
		assertTrue(spec("4 tracks island footbridge escalators").escalators());
		assertTrue(spec(StudioApi.typed(Map.of("text", "4 tracks island footbridge", "esc", "1"))).escalators());
	}

	@Test
	void narrowPlatformsKeepStairsOnly() {
		final StationSpec spec = spec("4 tracks island 8 wide 100 long footbridge escalators");
		final StationPlan plan = StationPlanner.plan(spec);
		assertEquals(0, steps(plan));
		assertTrue(StationAudit.audit(plan).buildable());
	}

	@Test
	void wideIslandsGetAnEscalatorBesideTheStair() {
		final StationSpec spec = spec("4 tracks island 12 wide 100 long footbridge escalators");
		final TrackLayout layout = StationGeometry.layout(spec);
		final StationLayout site = StationLayout.of(spec, layout);
		final StationPlan plan = StationPlanner.plan(spec);
		assertTrue(LayoutValidator.validate(plan).isEmpty(), LayoutValidator.validate(plan).toString());
		assertTrue(StationAudit.audit(plan).buildable(), StationAudit.audit(plan).problems().toString());
		int escalators = 0;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final List<Integer> stairs = site.stairs(band);
			final List<Integer> escalator = site.escalator(band);
			if (escalator.isEmpty()) {
				continue;
			}
			escalators++;
			// stair, a rail column, then the escalator
			assertEquals(stairs.get(1) + 2, escalator.get(0));
			for (int i = 2; i <= 5; i++) {
				// each slope step is level with the stair step in the same row
				final int y = StationLayout.escalatorY(i);
				assertEquals(site.stairV(y), site.escalatorV(i), "slope step " + i);
			}
		}
		assertTrue(escalators >= 2, "both islands are wide enough");
		assertEquals(16L * escalators, steps(plan));
	}
}
