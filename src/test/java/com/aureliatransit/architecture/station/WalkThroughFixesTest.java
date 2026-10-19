package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixes from the Station Studio walk-through: nothing falls, the rails clear the boarding edge, signs and boards hang on something. */
class WalkThroughFixesTest {

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private static StationAudit.Status falls(StationPlan plan) {
		return StationAudit.audit(plan).checks(StationAudit.Topic.FALLS).get(0).status();
	}

	private static boolean onEdge(StationPlan plan, PlannedBlock block) {
		return plan.blocks().stream().anyMatch(b -> b.role() == Role.PLATFORM_EDGE && b.u() == block.u() && b.v() == block.v()
				&& b.y() == StationGeometry.PLATFORM_Y);
	}

	@Test
	void aStairBesideTheBoardingEdgeIsRailedAboveTheClearway() {
		final StationPlan plan = plan("2 tracks side, 64 long, 3 wide, bangkok, butterfly canopy, andesite bed, terrazzo floors");
		assertEquals(StationAudit.Status.PASS, falls(plan), StationAudit.audit(plan).problems().toString());
		assertEquals(List.of(), LayoutValidator.validate(plan));
		final List<PlannedBlock> edgeRails = plan.blocks().stream().filter(b -> b.role() == Role.RAILING && onEdge(plan, b)).toList();
		assertFalse(edgeRails.isEmpty(), "the high steps beside the edge have a rail");
		for (final PlannedBlock rail : edgeRails) {
			assertTrue(rail.y() >= StationGeometry.WALK_Y + StationGeometry.HEADROOM, "rail " + rail + " is over the clearway");
		}
		// without those rails the audit sees the drop
		final List<PlannedBlock> bare = plan.blocks().stream().filter(b -> !edgeRails.contains(b)).toList();
		final StationPlan open = new StationPlan(plan.spec(), plan.layout(), bare, plan.trackEnvelopes(), plan.headroom(), plan.supportAreas(),
				plan.foundationId());
		assertEquals(StationAudit.Status.FAIL, falls(open));
	}

	@Test
	void aWideCorridorLeavesTheLiftShaftItsSideWall() {
		final StationPlan plan = plan("4 tracks island, 96 long, station building, mezzanine");
		assertEquals(StationAudit.Status.PASS, falls(plan), StationAudit.audit(plan).problems().toString());
	}

	@Test
	void aSecondFootbridgesStairsAreNotRunThroughByATravelator() {
		final StationPlan plan = plan("8 tracks, 96 long, grand central, pointed shed, travelators, gravel bed");
		final Set<String> stairs = plan.blocks().stream().filter(b -> b.role() == Role.STAIR).map(b -> b.u() + "," + b.v())
				.collect(Collectors.toSet());
		final Set<String> walkways = new HashSet<>();
		plan.blocks().stream().filter(b -> b.role() == Role.ESCALATOR).forEach(b -> walkways.add(b.u() + "," + b.v()));
		walkways.retainAll(stairs);
		assertEquals(Set.of(), walkways, "a walkway shares a column and row with a stair");
		assertEquals(StationAudit.Status.PASS, falls(plan), StationAudit.audit(plan).problems().toString());
	}

	@Test
	void theHallsEndBoardsHangOnlyOnAWall() {
		for (final String text : List.of("4 tracks island, 96 long, station building, wings", "2 tracks side station building city class",
				"2 tracks side station building grand class")) {
			final StationPlan plan = plan(text);
			assertEquals(StationAudit.Status.PASS, StationAudit.audit(plan).checks(StationAudit.Topic.GROUNDED).get(0).status(), text);
		}
	}

	@Test
	void theApproachToAStairFootIsClearOfScreensSignsAndFurniture() {
		for (final String text : List.of("4 tracks island 12 wide 100 long footbridge escalators enclosed stairs, named Halt",
				"2 tracks island 11 wide 120 long footbridge escalators stairs both ways hong kong, named hi")) {
			final StationSpec spec = StationDescriptionParser.parse(text).spec();
			final TrackLayout layout = StationGeometry.layout(spec);
			final StationLayout site = StationLayout.of(spec, layout);
			final StationPlan plan = StationPlanner.plan(spec);
			int zones = 0;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final StationLayout.Flight flight : site.flights(band)) {
					zones++;
					final int from = Math.min(flight.wallV(), flight.landingV()) - (flight.side() < 0 ? StationPlanner.STAIR_APPROACH : 0);
					final int to = Math.max(flight.wallV(), flight.landingV()) + (flight.side() > 0 ? StationPlanner.STAIR_APPROACH : 0);
					for (final PlannedBlock b : plan.blocks()) {
						final boolean movable = b.role() == Role.FIXTURE || b.role() == Role.BENCH || b.role() == Role.BIN || b.role() == Role.LAMP
								|| b.role() == Role.NAME_SIGN;
						if (movable && b.y() >= StationGeometry.WALK_Y && b.y() <= StationGeometry.WALK_Y + StationGeometry.HEADROOM
								&& b.v() >= from && b.v() <= to && flight.columns().contains(b.u())) {
							throw new AssertionError(text + ": " + b.role() + " " + b.blockId() + " stands in the approach to a stair at u=" + b.u() + " v=" + b.v());
						}
					}
				}
			}
			assertTrue(zones > 0, text);
			assertTrue(plan.blocks().stream().noneMatch(b -> b.blockId().equals(StylePalette.MTR_ROUTE_SIGN)), text + ": a route map stands on an enclosure post");
			assertEquals(StationAudit.Status.PASS, StationAudit.audit(plan).checks(StationAudit.Topic.GROUNDED).get(0).status(), text);
		}
	}

	@Test
	void aSecondFlightFromTheOtherSideCanBeAnEscalator() {
		final StationSpec spec = StationDescriptionParser.parse("4 tracks island 12 wide 100 long footbridge escalators stairs both ways").spec();
		final TrackLayout layout = StationGeometry.layout(spec);
		final StationLayout site = StationLayout.of(spec, layout);
		long flights = 0;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			flights += (site.hasEscalator(band) ? 1 : 0) + (site.reverseEscalator(band) != null ? 1 : 0);
		}
		assertTrue(flights >= 2, "an island this wide has an escalator each way");
		final StationPlan plan = StationPlanner.plan(spec);
		final long steps = plan.blocks().stream().filter(b -> b.blockId().equals(StylePalette.MTR_ESCALATOR_STEP)).count();
		assertEquals(2L * (StationLayout.ESCALATOR_TOP + 1) * flights, steps);
		assertTrue(StationAudit.audit(plan).buildable(), StationAudit.audit(plan).problems().toString());
	}

	@Test
	void aRaisedRoofOverAColumnLineStandsOnAPostNotAStackOfGirders() {
		final String girder = "aurelia_transit_architecture:station_girder";
		int posts = 0;
		for (final String text : List.of("2 tracks island 8 wide 64 long gable canopy footbridge", "2 tracks side 8 wide 64 long gable canopy footbridge",
				"2 tracks side 8 wide 64 long butterfly canopy footbridge")) {
			final StationPlan plan = plan(text);
			for (final PlannedBlock rib : plan.blocks()) {
				if (rib.role() != Role.RIB) {
					continue;
				}
				final PlannedBlock below = plan.blocks().stream().filter(o -> o.u() == rib.u() && o.y() == rib.y() - 1 && o.v() == rib.v()).findFirst()
						.orElse(null);
				if (below != null && below.role() == Role.BEAM) {
					assertFalse(rib.blockId().equals(girder), text + ": a girder stands on the girder at " + rib);
					posts++;
				}
			}
			assertEquals(StationAudit.Status.PASS, StationAudit.audit(plan).checks(StationAudit.Topic.GROUNDED).get(0).status(), text);
		}
		assertTrue(posts > 0, "a raised roof stands on a column line's girder somewhere");
	}

	@Test
	void aDepartureScreenIsFixedToAColumnWhereThereIsOne() {
		int fixed = 0;
		for (final String text : List.of("2 tracks side 4 wide 64 long canopy footbridge", "2 tracks island 8 wide 80 long canopy footbridge",
				"4 tracks island 8 wide 96 long station hall footbridge")) {
			final StationPlan plan = plan(text);
			for (final PlannedBlock b : plan.blocks()) {
				if (!b.blockId().equals("aurelia_transit_architecture:platform_pids")) {
					continue;
				}
				// beside a column block, in the same row, or beside the screen's own other half
				final boolean touchesColumn = plan.blocks().stream().anyMatch(o -> o.role() == Role.COLUMN && o.y() == b.y()
						&& Math.abs(o.u() - b.u()) + Math.abs(o.v() - b.v()) <= 2);
				if (touchesColumn) {
					fixed++;
				}
			}
		}
		assertTrue(fixed > 0, "some screen is fixed to a column");
	}

	@Test
	void aPlatformWallHasDoorwaysOnlyWhereAPlatformStandsBesideIt() {
		int windows = 0;
		for (final String text : List.of("4 tracks island, 96 long, station building", "2 tracks island 8 wide 64 long station building footbridge")) {
			final StationSpec spec = StationDescriptionParser.parse(text).spec();
			final TrackLayout layout = StationGeometry.layout(spec);
			final StationLayout site = StationLayout.of(spec, layout);
			final StationPlan plan = StationPlanner.plan(spec);
			final boolean platformBeside = layout.platforms().stream().anyMatch(band -> band.uMin() == layout.buildingWidth());
			final int uP = site.platformWall(layout);
			for (int bay = 0; bay < site.bays(); bay++) {
				final int centre = site.bayCentre(bay);
				final boolean closed = plan.blocks().stream().anyMatch(b -> b.u() == uP && b.y() == 2 && b.v() == centre);
				if (!platformBeside && closed) {
					windows++;
				}
				if (!platformBeside) {
					assertTrue(closed, text + ": a doorway onto a track at bay " + bay);
				}
			}
		}
		assertTrue(windows > 0, "a wall along a track has its windows");
	}

	@Test
	void everyPlatformOfAListGetsTravelatorsWhenItsWidthAllows() {
		final String text = "4 track, platform 1: side; platform 2: island; platform 3: island; platform 4: island; travelators footbridge 120 blocks long";
		final StationPlan plan = plan(text);
		for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
			final long steps = plan.blocks().stream().filter(b -> b.blockId().equals(StylePalette.MTR_ESCALATOR_STEP) && b.y() == StationGeometry.PLATFORM_Y
					&& b.u() >= band.uMin() && b.u() <= band.uMax()).count();
			assertTrue(steps > 0, "platform " + band.number() + " (" + (band.island() ? "island" : "side") + ", " + band.width() + " wide) has no travelator");
		}
	}

	@Test
	void travelatorsOnPlatformsTooNarrowAreWarnedAbout() {
		final ParseResult parse = StationDescriptionParser.parse("2 tracks island 7 wide 120 long travelators footbridge");
		assertTrue(parse.warnings().stream().anyMatch(w -> w.contains("travelators") && w.contains("wide")), parse.warnings().toString());
	}

	@Test
	void travelatorsRunBothWaysAndKeepTheSameHandAlongThePlatform() {
		for (final String text : List.of("2 tracks island 11 wide, 120 long, footbridge, travelators",
				"2 tracks island 11 wide, 201 long, station building, footbridge, travelators")) {
			final StationPlan plan = plan(text);
			for (final TrackLayout.PlatformBand band : plan.layout().platforms()) {
				final List<PlannedBlock> steps = plan.blocks().stream().filter(b -> b.blockId().equals(StylePalette.MTR_ESCALATOR_STEP)
						&& b.y() == StationGeometry.PLATFORM_Y && b.u() >= band.uMin() && b.u() <= band.uMax()).toList();
				assertFalse(steps.isEmpty(), text);
				final int low = steps.stream().mapToInt(PlannedBlock::u).min().getAsInt();
				final int high = steps.stream().mapToInt(PlannedBlock::u).max().getAsInt();
				assertTrue(high > low, text + ": one lane only");
				for (final PlannedBlock step : steps) {
					final int other = step.u() == low ? high : low;
					assertTrue(steps.stream().anyMatch(o -> o.u() == other && o.v() == step.v()), text + ": a lane runs alone at v=" + step.v());
				}
				assertTrue(steps.stream().filter(b -> b.u() == low).allMatch(b -> b.front() == LocalDir.PLUS_V), text + ": the lower lane changes hand");
				assertTrue(steps.stream().filter(b -> b.u() == high).allMatch(b -> b.front() == LocalDir.MINUS_V), text + ": the upper lane changes hand");
			}
		}
	}

	@Test
	void aBuildingsEndWallsGoDownToTheFloorExceptAtADoor() {
		for (final String text : List.of("4 tracks island, 96 long, station building, wings", "named hi, 2 tracks, 201 long, grand station building, footbridge, hong kong")) {
			final StationPlan plan = plan(text);
			final java.util.Map<Integer, Set<Integer>> upper = new java.util.HashMap<>();
			final java.util.Map<Integer, Set<Integer>> low = new java.util.HashMap<>();
			for (final PlannedBlock b : plan.blocks()) {
				final boolean wall = b.role() == Role.WALL || b.role() == Role.WINDOW;
				if (wall && b.y() == 5) {
					upper.computeIfAbsent(b.v(), k -> new HashSet<>()).add(b.u());
				}
				if (b.y() == 2) {
					low.computeIfAbsent(b.v(), k -> new HashSet<>()).add(b.u());
				}
			}
			for (final java.util.Map.Entry<Integer, Set<Integer>> row : upper.entrySet()) {
				final Set<Integer> missing = new HashSet<>(row.getValue());
				missing.removeAll(low.getOrDefault(row.getKey(), Set.of()));
				assertTrue(missing.size() <= 3, text + ": the wall at v=" + row.getKey() + " is open to the floor over " + missing.size() + " cells");
			}
		}
	}
}
