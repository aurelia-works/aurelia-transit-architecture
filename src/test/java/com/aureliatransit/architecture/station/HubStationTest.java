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
				// four to six tracks keep one through track a side; eight and ten get two, an outer island with both running through
				final int through = tracks >= 8 ? 2 : 1;
				assertEquals(through, layout.hubThrough(), label);
				for (final TrackLayout.Track track : layout.tracks()) {
					final boolean outer = track.number() <= through || track.number() > tracks - through;
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
	void theHeadBuildingSpansOnlyThePlatformsWithBayTracks() {
		for (final int tracks : new int[] {4, 6, 8, 10}) {
			final StationPlan plan = StationPlanner.plan(spec(tracks).build());
			final TrackLayout layout = plan.layout();
			final List<TrackLayout.PlatformBand> bays = layout.platforms().stream().filter(layout::bay).toList();
			assertEquals(tracks >= 8 ? layout.platforms().size() - 2 : layout.platforms().size(), bays.size(), tracks + " tracks");
			// the side walls stand just outside the outer bay tracks, so every buffer stop is indoors, and clear of the through tracks
			assertTrue(layout.headFrom() <= bays.get(0).uMin() && layout.headTo() >= bays.get(bays.size() - 1).uMax(), tracks + " tracks");
			for (final TrackLayout.Track track : layout.tracks()) {
				final boolean inside = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH > layout.headFrom()
						&& track.u() + StationGeometry.ENVELOPE_HALF_WIDTH < layout.headTo();
				final boolean outside = track.u() + StationGeometry.ENVELOPE_HALF_WIDTH < layout.headFrom()
						|| track.u() - StationGeometry.ENVELOPE_HALF_WIDTH > layout.headTo();
				assertTrue(layout.bay(track) ? inside : outside, tracks + " tracks: track " + track.number() + " at u " + track.u());
			}
			for (final PlannedBlock stop : plan.blocks()) {
				if (stop.role() == Role.BUFFER_STOP) {
					assertTrue(stop.u() > layout.headFrom() && stop.u() < layout.headTo(), tracks + " tracks: buffer stop outside " + stop);
				}
			}
			final boolean wider = plan.blocks().stream().anyMatch(b -> b.role() == Role.FLOOR && b.v() >= layout.platformLength()
					&& b.v() <= layout.headEnd() && (b.u() < layout.headFrom() || b.u() > layout.headTo()));
			assertFalse(wider, tracks + " tracks: head floor outside its platforms");
			// the outer islands of a big hub are reached by a footbridge with a lift on every platform
			final StationLayout site = StationLayout.of(plan.spec(), layout);
			assertEquals(tracks >= 8, site.bridge(), tracks + " tracks");
			if (site.bridge()) {
				for (final TrackLayout.PlatformBand band : layout.platforms()) {
					assertTrue(site.platformLift(band, layout) != null, tracks + " tracks: lift on platform " + band.number());
				}
			}
		}
	}

	@Test
	void narrowPlatformsKeepOneThroughTrackASide() {
		for (final int width : new int[] {3, 4, 5}) {
			final StationPlan plan = StationPlanner.plan(spec(10).platformWidth(width).build());
			assertEquals(1, plan.layout().hubThrough(), "width " + width);
			final StationAudit.Result audit = StationAudit.audit(plan);
			assertTrue(audit.buildable(), "width " + width + ": " + audit.problems());
			assertTrue(audit.access().stream().allMatch(a -> a == StationAudit.Access.STEP_FREE), "width " + width + ": " + audit.access());
		}
	}

	@Test
	void everyPlatformHasABigNumberAndAColourAtItsGates() {
		for (final int tracks : new int[] {4, 10}) {
			final StationPlan plan = StationPlanner.plan(spec(tracks).build());
			final TrackLayout layout = plan.layout();
			final java.util.Set<String> colours = new java.util.HashSet<>();
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final String number = Integer.toString(band.number());
				final boolean signed = plan.blocks().stream().anyMatch(b -> b.blockId().endsWith(":platform_number_sign") && b.text().equals(number)
						&& b.u() >= band.uMin() && b.u() <= band.uMax());
				assertTrue(signed, tracks + " tracks: number sign for platform " + number);
				if (layout.bay(band)) {
					final int mid = (band.uMin() + band.uMax()) / 2;
					final String stripe = plan.blocks().stream().filter(b -> b.u() == mid && b.y() == 0 && b.v() == layout.platformLength() + 1)
							.findFirst().orElseThrow().blockId();
					assertTrue(stripe.contains("metro_tile_"), tracks + " tracks platform " + number + ": " + stripe);
					colours.add(stripe);
				}
			}
			assertEquals(Math.min(4, (int) layout.platforms().stream().filter(layout::bay).count()), colours.size(), "neighbours differ");
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
