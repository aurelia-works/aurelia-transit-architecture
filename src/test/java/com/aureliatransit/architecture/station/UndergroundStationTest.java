package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enviro, 10-20: underground stations, a shallow box under the street after Berlin's Rosenthaler Platz. */
class UndergroundStationTest {

	private static final String ATA = "aurelia_transit_architecture:";
	private static final List<String> CLEAN = List.of("2 track underground station, side platforms, 80 long",
			"2 track underground station, island platform, 130 long, orange tiles, named Rosenthaler Platz", "4 track subway station, two island platforms, 120 long",
			"3 track tube station, island and side platforms, 100 long", "1 track underground station, 60 long");

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private static PlannedBlock at(StationPlan plan, int u, int y, int v) {
		return plan.blocks().stream().filter(b -> b.u() == u && b.y() == y && b.v() == v).findFirst().orElse(null);
	}

	@Test
	void wordsAskForAnUndergroundStation() {
		for (final String word : List.of("underground", "subway", "tube", "tunnel")) {
			final ParseResult parse = StationDescriptionParser.parse("2 track " + word + " station");
			assertTrue(parse.spec().options().underground(), word);
			assertEquals(List.of(), parse.ignored(), word);
			assertEquals(RoofType.NONE, parse.spec().roof(), word);
			assertTrue(parse.spec().footbridge(), word);
		}
		assertFalse(StationDescriptionParser.parse("2 track metro station").spec().options().underground());
		assertEquals(ATA + "metro_tile_orange", StationDescriptionParser.parse("2 track underground station, orange tiles").spec().options().tile());
		assertEquals(ATA + "metro_tile_orange", StationDescriptionParser.parse("2 track berlin underground station").spec().options().tile());
	}

	/** What an underground station cannot have is said, not silently dropped. */
	@Test
	void whatItCannotHaveIsSaid() {
		final ParseResult building = StationDescriptionParser.parse("2 track underground station with a station building");
		assertFalse(building.spec().hasBuilding());
		assertTrue(building.warnings().stream().anyMatch(w -> w.contains("no station building")), building.warnings().toString());
		final ParseResult terminus = StationDescriptionParser.parse("2 track underground terminus");
		assertFalse(terminus.spec().terminus());
		assertEquals(1, terminus.warnings().size(), terminus.warnings().toString());
	}

	@Test
	void everyLayoutIsCleanAndBuildable() {
		for (final String text : CLEAN) {
			final ParseResult parse = StationDescriptionParser.parse(text);
			assertEquals(List.of(), parse.warnings(), text);
			final StationPlan plan = StationPlanner.plan(parse.spec());
			assertEquals(List.of(), LayoutValidator.validate(plan), text);
			final StationAudit.Result report = StationAudit.audit(plan);
			assertTrue(report.buildable(), text);
			assertTrue(report.checks().stream().noneMatch(c -> c.topic() == StationAudit.Topic.ACCESS && c.status() != StationAudit.Status.PASS),
					text + " " + report.checks());
		}
	}

	/** The box: tiled side walls, a tunnel mouth lined round each track at both ends, and the street slab over it all. */
	@Test
	void theBoxHasWallsMouthsAndACeiling() {
		final StationPlan plan = plan("2 track underground station, island platform, 130 long, orange tiles");
		final TrackLayout layout = plan.layout();
		final int mid = (layout.vMin() + layout.vMax()) / 2;
		assertEquals(ATA + "metro_tile_orange", at(plan, 0, 3, mid).blockId());
		assertEquals(ATA + "metro_tile_orange", at(plan, layout.width() - 1, 3, mid).blockId());
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int v : new int[]{layout.vMin(), layout.vMax()}) {
				assertNull(at(plan, track.u(), 1, v), "mouth over track " + track.number());
				assertEquals(PlanUnderground.TUNNEL_LINING, at(plan, track.u(), PlanUnderground.MOUTH_HEIGHT, v).blockId());
			}
		}
		for (int u = 0; u < layout.width(); u++) {
			final PlannedBlock slab = at(plan, u, PlanUnderground.CEILING_Y, layout.vMin() + 2);
			assertTrue(slab != null && slab.role().covers(), "slab at u=" + u);
		}
		// lit from lights hung under the slab, none set in it to show in the street
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.LIGHT_PANEL && b.y() == PlanUnderground.CEILING_Y - 1));
		assertTrue(plan.blocks().stream().noneMatch(b -> b.role() == Role.LIGHT_PANEL && b.y() == PlanUnderground.CEILING_Y));
		// no ramps onto the track bed and nothing standing above the street but the pylons, rails and lift kiosks
		assertTrue(plan.blocks().stream().noneMatch(b -> b.role() == Role.RAMP_LOWER || b.role() == Role.RAMP_UPPER));
	}

	/** The stairs come up through the slab, with a rail round the opening and the station's name on a pylon beside it. */
	@Test
	void theStairsComeUpToTheStreet() {
		final StationPlan plan = plan("2 track underground station, island platform, 130 long, named Rosenthaler Platz");
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.STAIR && b.y() == PlanUnderground.CEILING_Y));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.BARRIER && b.y() == PlanUnderground.STREET_Y));
		final List<PlannedBlock> pylons = plan.blocks().stream().filter(b -> b.blockId().equals(PlanUnderground.PYLON)).toList();
		assertFalse(pylons.isEmpty());
		assertTrue(pylons.stream().anyMatch(b -> b.y() == PlanUnderground.STREET_Y && b.text().equals("NONE:Rosenthaler Platz")), pylons.toString());
		assertTrue(pylons.stream().anyMatch(b -> b.y() == PlanUnderground.STREET_Y + 1 && b.state().equals("half=upper")));
	}

	/** The player stands on the street; the box is built that far below their feet. */
	@Test
	void theFrameIsSunkUnderTheStreet() {
		final StationSpec spec = StationDescriptionParser.parse("2 track underground station").spec();
		assertEquals(StationGeometry.UPPER_WALK_Y, StationPlacement.depth(spec));
		final LocalFrame frame = new LocalFrame(10, 70, 20, Heading.NORTH);
		assertEquals(70 - StationGeometry.UPPER_WALK_Y, StationPlacement.sunk(spec, frame).oy());
		final StationSpec ground = StationDescriptionParser.parse("2 track station").spec();
		assertEquals(frame, StationPlacement.sunk(ground, frame));
	}
}
