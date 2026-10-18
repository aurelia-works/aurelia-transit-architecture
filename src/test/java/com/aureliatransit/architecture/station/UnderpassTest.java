package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2: an underpass, a stairs-only tunnel under the tracks to every platform. */
class UnderpassTest {

	private static StationPlan plan(String words) {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan), words);
		assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		return plan;
	}

	@Test
	void theTunnelRunsUnderEveryTrackWithAStairToEachPlatform() {
		for (final String words : new String[]{"2 tracks side platforms, 64 long, underpass", "4 tracks island, 96 long, station building, underpass",
				"3 tracks, 80 long, city class, underpass", "4 tracks island, 120 long, footbridge, underpass, hall roof"}) {
			final StationPlan plan = plan(words);
			final TrackLayout layout = plan.layout();
			final List<PlannedBlock> below = plan.blocks().stream().filter(b -> b.y() < -1).toList();
			assertFalse(below.isEmpty(), words);
			// the floor runs under every track between the first platform and the last
			final int first = layout.platforms().get(0).uMin();
			final int last = layout.platforms().get(layout.platforms().size() - 1).uMax();
			for (final TrackLayout.Track track : layout.tracks()) {
				assertTrue(track.u() < first || track.u() > last || below.stream().anyMatch(b -> b.y() == StationGeometry.UNDERPASS_FLOOR_Y && b.u() == track.u() && b.role() == Role.FLOOR),
						words + ": floor under track " + track.number());
			}
			// a flight of six steps to every platform
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final long steps = plan.blocks().stream().filter(b -> b.role() == Role.STAIR && b.y() < 0 && b.u() >= band.uMin() && b.u() <= band.uMax()).count();
				assertEquals(2L * (StationGeometry.STAIR_STEPS - 1), steps, words + ": steps down from platform " + band.number());
			}
			// the corridor is dug: a headroom box below ground
			assertTrue(plan.headroom().stream().anyMatch(h -> h.yMax() < 0), words);
			// no support asked for where the wells cut through the support layer
			for (final PlannedBlock step : plan.blocks().stream().filter(b -> b.role() == Role.STAIR && b.y() == -2).toList()) {
				assertTrue(plan.supportAreas().stream().noneMatch(s -> s.contains(step.u(), -1, step.v())), words + ": support over a step at " + step);
			}
		}
	}

	@Test
	void aTerminusOrASinglePlatformGetsNone() {
		final ParseResult terminus = StationDescriptionParser.parse("4 tracks island, 96 long, terminus, underpass");
		assertFalse(terminus.spec().underpass());
		assertTrue(terminus.warnings().stream().anyMatch(w -> w.contains("underpass")), terminus.warnings().toString());
		assertTrue(plan("1 track, 48 long, underpass").blocks().stream().noneMatch(b -> b.y() < -1), "one platform: nothing to cross to");
		assertTrue(plan("2 tracks, 64 long").blocks().stream().noneMatch(b -> b.y() < -1), "only when asked for");
	}
}
