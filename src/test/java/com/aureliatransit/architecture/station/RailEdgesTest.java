package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailEdgesTest {

	@Test
	void edgesAreKeptAgainstTheFrontAndNamedInTheWorld() {
		assertEquals(1, RailEdges.turns(LocalDir.PLUS_V, LocalDir.PLUS_U), "+U is clockwise from +V");
		assertEquals(3, RailEdges.turns(LocalDir.PLUS_V, LocalDir.MINUS_U));
		assertEquals("edges=0", RailEdges.planState(LocalDir.MINUS_U, List.of(LocalDir.MINUS_U)));
		assertEquals("edges=0+1", RailEdges.planState(LocalDir.PLUS_V, List.of(LocalDir.PLUS_V, LocalDir.PLUS_U)));
		assertEquals("kind=balustrade,edge_east=true,edge_south=true", RailEdges.worldState("kind=balustrade,edges=0+1", Heading.EAST));
		assertEquals("edge_west=true", RailEdges.worldState("edges=3", Heading.NORTH));
		assertEquals("kind=platform", RailEdges.worldState("kind=platform", Heading.NORTH));
		assertEquals("corner=0a", RailEdges.planCorner(LocalDir.PLUS_U, LocalDir.PLUS_U, false));
		assertEquals("edge_east=true,corner=east_ccw", RailEdges.worldState("edges=0,corner=0a", Heading.EAST));
		assertEquals("edge_south=true,corner=west_cw", RailEdges.worldState("edges=1,corner=2c", Heading.EAST));
	}

	@Test
	void outsideCornersGetOnePost() {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse("amsterdam hub, 10 tracks, 120 long, seed tmptnb").spec());
		final java.util.Map<String, PlannedBlock> at = new java.util.HashMap<>();
		for (final PlannedBlock b : plan.blocks()) {
			at.put(b.u() + "," + b.y() + "," + b.v(), b);
		}
		int posts = 0;
		for (final PlannedBlock a : plan.blocks()) {
			if (!a.state().contains("corner=")) {
				continue;
			}
			posts++;
			// the corner's other run: one step past the post's end and one over towards the drop, a railing on its edge
			final String corner = a.state().substring(a.state().indexOf("corner=") + 7);
			LocalDir side = a.front();
			for (int i = Integer.parseInt(corner.substring(0, corner.length() - 1)); i > 0; i--) {
				side = RailEdges.clockwise(side);
			}
			final LocalDir end = corner.endsWith("c") ? RailEdges.clockwise(side) : RailEdges.clockwise(side).opposite();
			final PlannedBlock beyond = at.get((a.u() + ConcourseFit.du(end)) + "," + a.y() + "," + (a.v() + ConcourseFit.dv(end)));
			final PlannedBlock other = at.get((a.u() + ConcourseFit.du(end) + ConcourseFit.du(side)) + "," + a.y() + ","
					+ (a.v() + ConcourseFit.dv(end) + ConcourseFit.dv(side)));
			assertEquals(null, beyond, "the corner's own cell stays open: " + a);
			assertTrue(other != null && other.role() == Role.RAILING && other.state().contains("edges="), "a run meets the post: " + a);
			assertTrue(!other.state().contains("corner="), "one post to a corner: " + a + " and " + other);
		}
		assertTrue(posts > 0, "the hub's track ends have outside corners");
	}

	@Test
	void railingsStandOnTheirDropAndTurnCorners() {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse("2 tracks island 8 wide 64 long canopy footbridge no ramps").spec());
		int edged = 0;
		int corners = 0;
		for (final PlannedBlock b : plan.blocks()) {
			if (!Planning.EDGE_RAILINGS.contains(b.blockId()) || !b.state().contains("edges=")) {
				continue;
			}
			edged++;
			final String edges = b.state().substring(b.state().indexOf("edges=") + 6);
			assertTrue(edges.startsWith("0"), "the front is a drop side: " + b);
			if (edges.contains("+")) {
				corners++;
			}
		}
		assertTrue(edged > 0, "some railing stands on its drop");
		assertTrue(corners > 0, "a platform end's railing turns the corner onto the track side");
	}
}
