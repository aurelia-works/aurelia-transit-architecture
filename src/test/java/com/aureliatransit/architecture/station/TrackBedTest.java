package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.station.studio.StudioApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ballast under the tracks with a light strip at the foot of each platform wall, only when asked for. */
class TrackBedTest {

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	@Test
	void wordsPickTheBed() {
		assertEquals(TrackBed.NONE, StationDescriptionParser.parse("2 tracks").spec().trackBed());
		assertEquals(TrackBed.GRAVEL, StationDescriptionParser.parse("gravel track bed").spec().trackBed());
		assertEquals(TrackBed.GRAVEL, StationDescriptionParser.parse("ballast").spec().trackBed());
		assertEquals(TrackBed.ANDESITE, StationDescriptionParser.parse("andesite terminus").spec().trackBed());
		assertEquals(TrackBed.ANDESITE, StationDescriptionParser.parse(StudioApi.typed(Map.of("bed", "andesite"))).spec().trackBed());
	}

	@Test
	void noBedLeavesTheGroundAlone() {
		assertTrue(plan("2 tracks island").blocks().stream().noneMatch(b -> b.role() == Role.TRACK_BED || b.y() < 0));
	}

	@Test
	void bedFillsEveryTrackRunAndLightsEachPlatformWall() {
		final StationPlan plan = plan("gravel 3 tracks side 30 long");
		final TrackLayout layout = plan.layout();
		for (final TrackLayout.Track track : layout.tracks()) {
			for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
				for (int u = track.u() - 1; u <= track.u() + 1; u++) {
					final int uu = u;
					final int vv = v;
					final boolean wall = (u == track.u() - 1 && track.platformLeft() || u == track.u() + 1 && track.platformRight())
							&& v >= layout.platformFrom(track) && v <= layout.platformTo(track);
					final String id = plan.blocks().stream().filter(b -> b.u() == uu && b.y() == -1 && b.v() == vv).findFirst()
							.map(PlannedBlock::blockId).orElse("none");
					assertEquals(wall ? TrackBed.LIGHT : TrackBed.GRAVEL.blockId(), id, "track " + track.number() + " u=" + u + " v=" + v);
				}
			}
		}
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable());
		// gravel falls, so the ground under it is a support
		assertTrue(plan.supportAreas().stream().anyMatch(box -> box.yMin() == -2));
		assertTrue(plan("andesite 2 tracks").supportAreas().stream().noneMatch(box -> box.yMin() == -2));
	}
}
