package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hub: a terminus whose two outer tracks run on past the head building while the inner ones end at its buffer stops. */
class HubStationTest {

	@Test
	void theWordHubMakesABigIslandHub() {
		final ParseResult result = StationDescriptionParser.parse("hub");
		final StationSpec spec = result.spec();
		assertTrue(spec.hub());
		assertTrue(spec.terminus());
		assertEquals(PlatformLayout.ISLAND, spec.platformLayout());
		assertEquals(0, spec.trackCount() % 2);
		assertTrue(spec.trackCount() >= 4);
		assertEquals(List.of(), result.warnings());
	}

	@Test
	void anOddOrSideHubIsFixedWithAWarning() {
		final ParseResult odd = StationDescriptionParser.parse("hub, 5 tracks");
		assertEquals(6, odd.spec().trackCount());
		assertFalse(odd.warnings().isEmpty());
		final ParseResult side = StationDescriptionParser.parse("interchange with side platforms, 4 tracks");
		assertEquals(PlatformLayout.ISLAND, side.spec().platformLayout());
		assertFalse(side.warnings().isEmpty());
	}

	@Test
	void aHubNeedsFourOrMoreEvenTracksOnIslands() {
		assertThrows(IllegalArgumentException.class, () -> spec(3).build());
		assertThrows(IllegalArgumentException.class, () -> spec(4).platformLayout(PlatformLayout.SIDE).build());
		assertThrows(IllegalArgumentException.class, () -> spec(4).terminus(false).build());
	}

	@Test
	void outerTracksRunThroughAndEveryPlatformIsStepFree() {
		for (final int tracks : new int[] {4, 6, 8, 10}) {
			for (final StationScale scale : StationScale.values()) {
				final StationPlan plan = StationPlanner.plan(spec(tracks).scale(scale).build());
				final TrackLayout layout = plan.layout();
				final String label = tracks + " tracks, " + scale;
				assertEquals(List.of(), LayoutValidator.validate(plan), label);
				final StationAudit.Result audit = StationAudit.audit(plan);
				assertTrue(audit.buildable(), label + ": " + audit.problems());
				assertTrue(audit.access().stream().allMatch(a -> a == StationAudit.Access.STEP_FREE), label + ": " + audit.access());
				for (final TrackLayout.Track track : layout.tracks()) {
					final boolean outer = track.number() == 1 || track.number() == tracks;
					assertEquals(!outer, layout.bay(track), label + " track " + track.number());
					final boolean buffer = plan.blocks().stream().anyMatch(b -> b.role() == Role.BUFFER_STOP && b.u() == track.u());
					assertEquals(!outer, buffer, label + ": buffer stop on track " + track.number());
					final boolean reachesEnd = plan.trackEnvelopes().stream()
							.anyMatch(box -> box.uMin() <= track.u() && track.u() <= box.uMax() && box.vMax() >= layout.vMax());
					assertEquals(outer, reachesEnd, label + ": track " + track.number() + " runs past the head");
				}
			}
		}
	}

	@Test
	void aRecipeHubKeepsItsTracksEven() {
		final StationRecipe hub = StationRecipe.defaults().withTracks(5).withHub(true);
		assertTrue(hub.terminus());
		assertEquals(6, hub.tracks());
		assertEquals(2, hub.trackStep());
		assertEquals(4, hub.withTracks(hub.tracks() - hub.trackStep()).withTracks(3).tracks());
		assertEquals(StationRecipe.MAX_TRACKS, hub.withTracks(99).tracks());
		assertTrue(StationDescriptionParser.parse(hub.description()).spec().hub());
		assertFalse(hub.withTerminus(false).hub());
	}

	private static StationSpec.Builder spec(int tracks) {
		return StationSpec.builder().trackCount(tracks).platformLayout(PlatformLayout.ISLAND).platformLength(96).platformWidth(6)
				.building(BuildingStyle.GOTHIC_BRICK).roof(RoofType.HALL).terminus(true).hub(true);
	}
}
