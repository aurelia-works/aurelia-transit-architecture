package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The audit measures planner output: every plan the planner can make passes (island platforms with the known at-grade
 * warning), and each check fails when the plan is broken the way it looks for.
 */
class StationAuditTest {

	private static StationPlan plan(StationSpec.Builder builder) {
		return StationPlanner.plan(builder.build());
	}

	private static StationPlan with(StationPlan plan, List<PlannedBlock> blocks) {
		return new StationPlan(plan.spec(), plan.layout(), blocks, plan.trackEnvelopes(), plan.headroom(), plan.supportAreas(), plan.foundationId());
	}

	private static StationPlan without(StationPlan plan, Predicate<PlannedBlock> removed) {
		final List<PlannedBlock> blocks = new ArrayList<>();
		int dropped = 0;
		for (final PlannedBlock block : plan.blocks()) {
			if (removed.test(block)) {
				dropped++;
			} else {
				blocks.add(block);
			}
		}
		assertTrue(dropped > 0, "the test removed nothing");
		return with(plan, blocks);
	}

	private static StationAudit.Status status(StationAudit.Result result, StationAudit.Topic topic) {
		StationAudit.Status worst = StationAudit.Status.PASS;
		for (final StationAudit.Check check : result.checks(topic)) {
			if (check.status().ordinal() > worst.ordinal()) {
				worst = check.status();
			}
		}
		return worst;
	}

	private static String messages(StationAudit.Result result) {
		final StringBuilder text = new StringBuilder();
		for (final StationAudit.Check check : result.checks()) {
			text.append(check.status()).append(' ').append(check.topic()).append(": ").append(check.message()).append('\n');
		}
		return text.toString();
	}

	@Test
	void everyPlannablePlanPassesExceptIslandsReachedAtGrade() {
		int audited = 0;
		for (int tracks = StationSpec.MIN_TRACKS; tracks <= StationSpec.MAX_TRACKS; tracks++) {
			for (final PlatformLayout layout : PlatformLayout.values()) {
				if (layout == PlatformLayout.ISLAND && tracks < 2) {
					continue;
				}
				for (final RoofType roof : RoofType.values()) {
					for (final boolean stepFree : new boolean[]{true, false}) {
						for (final int length : new int[]{StationSpec.MIN_LENGTH, 48, 101, StationSpec.MAX_LENGTH}) {
							for (final String name : new String[]{"", "Leidsche Rijn"}) {
								final StationSpec.Builder builder = StationSpec.builder().trackCount(tracks).platformLayout(layout).roof(roof)
										.stepFree(stepFree).platformLength(length).name(name)
										.platformWidth(layout == PlatformLayout.ISLAND ? StationSpec.DEFAULT_ISLAND_WIDTH : StationSpec.DEFAULT_SIDE_WIDTH);
								final StationPlan plan = plan(builder);
								if (!LayoutValidator.validate(plan).isEmpty()) {
									continue; // e.g. side platforms with 3-4 tracks under a hall roof: refused before the audit
								}
								final StationAudit.Result result = StationAudit.audit(plan);
								final String context = builder.build() + "\n" + messages(result);
								assertTrue(result.buildable(), context);
								for (final StationAudit.Check check : result.problems()) {
									assertEquals(StationAudit.Topic.ACCESS, check.topic(), context);
									assertTrue(check.message().contains("walking across a track"), context);
								}
								for (int i = 0; i < plan.layout().platforms().size(); i++) {
									final TrackLayout.PlatformBand band = plan.layout().platforms().get(i);
									// only a platform on the outside of the station is reached without crossing a track
									final boolean outer = band.uMin() == 0 || band.uMax() == plan.layout().width() - 1;
									final StationAudit.Access expected = !outer ? StationAudit.Access.AT_GRADE
											: stepFree ? StationAudit.Access.STEP_FREE : StationAudit.Access.STEP;
									assertEquals(expected, result.access().get(i), "platform " + band.number() + "\n" + context);
								}
								assertEquals(messages(result), messages(StationAudit.audit(plan(builder))), "not deterministic");
								audited++;
							}
						}
					}
				}
			}
		}
		assertTrue(audited > 100, "only " + audited + " plans audited");
	}

	@Test
	void facesAreMeasuredFromTheEdgeBlocks() {
		final StationPlan plan = plan(StationSpec.builder().platformLength(64));
		final StationAudit.Result result = StationAudit.audit(plan);
		assertEquals(2, result.faces().size());
		for (final StationAudit.Face face : result.faces()) {
			assertEquals(0, face.vStart());
			assertEquals(65, face.length(), "64 picked: 65 blocks, 64 between the rail nodes");
		}
		assertEquals(1, result.faces().get(0).platform());
		assertEquals(2, result.faces().get(1).platform());
		assertTrue(result.checks(StationAudit.Topic.FACES).get(0).message().startsWith("2 boarding faces"));
	}

	@Test
	void sidePlatformsWithFourTracksReportTheThroughTracks() {
		final StationAudit.Result result = StationAudit.audit(plan(StationSpec.builder().trackCount(4).platformLayout(PlatformLayout.SIDE)));
		assertTrue(result.buildable(), messages(result));
		assertTrue(result.checks(StationAudit.Topic.FACES).get(0).message().contains("tracks 2 and 3 run through"), messages(result));
	}

	@Test
	void aShortFaceFails() {
		final StationPlan plan = plan(StationSpec.builder().platformLength(48));
		// trim the far end of the left platform: edge, surface and everything standing there
		final StationPlan broken = without(plan, b -> b.u() <= 3 && b.v() >= 46);
		final StationAudit.Result result = StationAudit.audit(broken);
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.FACES), messages(result));
		assertTrue(result.checks(StationAudit.Topic.FACES).get(0).message().contains("measures 46 blocks"), messages(result));
		assertFalse(result.buildable());
	}

	@Test
	void aGapInTheEdgeFails() {
		final StationPlan plan = plan(StationSpec.builder());
		final StationPlan broken = without(plan, b -> b.role() == Role.PLATFORM_EDGE && b.u() == 3 && b.v() == 20);
		final StationAudit.Result result = StationAudit.audit(broken);
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.FACES), messages(result));
		assertTrue(result.checks(StationAudit.Topic.FACES).get(0).message().contains("broken into 2 pieces"), messages(result));
	}

	@Test
	void aMissingRailNodeFails() {
		final StationPlan plan = plan(StationSpec.builder());
		final StationPlan broken = without(plan, b -> b.role() == Role.RAIL_NODE && b.v() == 0 && b.u() == plan.layout().tracks().get(1).u());
		final StationAudit.Result result = StationAudit.audit(broken);
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.RAIL_NODES), messages(result));
		assertTrue(result.checks(StationAudit.Topic.RAIL_NODES).get(0).message().startsWith("track 2"), messages(result));
	}

	@Test
	void railNodesAreOptional() {
		final StationAudit.Result result = StationAudit.audit(plan(StationSpec.builder().railNodes(false)));
		assertEquals(StationAudit.Status.PASS, status(result, StationAudit.Topic.RAIL_NODES));
	}

	@Test
	void aRoofWithoutColumnsFloatsAndFails() {
		final StationPlan plan = plan(StationSpec.builder().roof(RoofType.CANOPY));
		// every column of the left platform (u 0..3)
		final StationPlan broken = without(plan, b -> b.u() <= 3 && (b.role() == Role.COLUMN || b.role() == Role.COLUMN_CAP));
		final StationAudit.Result result = StationAudit.audit(broken);
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.GROUNDED), messages(result));
		assertTrue(result.checks(StationAudit.Topic.GROUNDED).get(0).message().contains("not joined to the ground"), messages(result));
	}

	@Test
	void partialRoofCoverageFails() {
		final StationPlan plan = plan(StationSpec.builder().roof(RoofType.CANOPY));
		final StationPlan broken = without(plan, b -> b.role().roofPart() && b.u() <= 3 && b.v() >= 40);
		final StationAudit.Result result = StationAudit.audit(broken);
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.ROOF), messages(result));
	}

	@Test
	void aRoofTooLowOverTheWalkwayFails() {
		final StationPlan plan = plan(StationSpec.builder().roof(RoofType.NONE).furniture(false));
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(new PlannedBlock(1, 2, 10, Role.WALL, "aurelia_transit_architecture:platform_edge", LocalDir.PLUS_U, ""));
		final StationAudit.Result result = StationAudit.audit(with(plan, blocks));
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.HEADROOM), messages(result));
	}

	@Test
	void wallMountedSignsDoNotCountForHeadroom() {
		final StationPlan plan = plan(StationSpec.builder().roof(RoofType.NONE).furniture(false));
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(new PlannedBlock(1, 2, 10, Role.NAME_SIGN, "aurelia_transit_architecture:station_name_sign", LocalDir.PLUS_U, ""));
		final StationAudit.Result result = StationAudit.audit(with(plan, blocks));
		assertEquals(StationAudit.Status.PASS, status(result, StationAudit.Topic.HEADROOM), messages(result));
	}

	@Test
	void stepFreeWithoutRampsFails() {
		final StationPlan plan = plan(StationSpec.builder().stepFree(true));
		final StationPlan broken = without(plan, b -> b.role() == Role.RAMP_LOWER || b.role() == Role.RAMP_UPPER);
		final StationAudit.Result result = StationAudit.audit(broken);
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.ACCESS), messages(result));
		assertEquals(StationAudit.Access.STEP, result.access().get(0));
	}

	@Test
	void aRampFacingTheWrongWayCannotBeClimbed() {
		final StationPlan plan = plan(StationSpec.builder().stepFree(true));
		final List<PlannedBlock> blocks = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			final boolean ramp = block.role() == Role.RAMP_LOWER || block.role() == Role.RAMP_UPPER;
			blocks.add(ramp ? new PlannedBlock(block.u(), block.y(), block.v(), block.role(), block.blockId(), LocalDir.PLUS_U, block.text()) : block);
		}
		final StationAudit.Result result = StationAudit.audit(with(plan, blocks));
		assertEquals(StationAudit.Access.STEP, result.access().get(0), messages(result));
	}

	@Test
	void aPlatformBoxedInByFixturesFails() {
		final StationPlan plan = plan(StationSpec.builder().roof(RoofType.NONE).furniture(false).stepFree(true));
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		// a wall of bins across the left platform (u 0..3) at v = 10 cuts off everything past it from the near ramps ...
		for (int u = 0; u <= 3; u++) {
			blocks.add(new PlannedBlock(u, 1, 10, Role.BIN, "aurelia_transit_architecture:waste_bin", LocalDir.PLUS_V, ""));
		}
		// ... and a second wall at v = 20 boxes in the cells between them
		for (int u = 0; u <= 3; u++) {
			blocks.add(new PlannedBlock(u, 1, 20, Role.BIN, "aurelia_transit_architecture:waste_bin", LocalDir.PLUS_V, ""));
		}
		final StationAudit.Result result = StationAudit.audit(with(plan, blocks));
		// the side approach still reaches the boxed-in cells by a step, so step-free access is what is lost
		assertEquals(StationAudit.Status.FAIL, status(result, StationAudit.Topic.ACCESS), messages(result));
	}

	@Test
	void nameSignsMatchTheName() {
		assertEquals(StationAudit.Status.PASS, status(StationAudit.audit(plan(StationSpec.builder().name("Utrecht Centraal"))), StationAudit.Topic.NAME_SIGNS));
		final StationPlan named = plan(StationSpec.builder().name("Utrecht Centraal"));
		final StationAudit.Result unsigned = StationAudit.audit(without(named, b -> b.role() == Role.NAME_SIGN && b.u() <= 3));
		assertEquals(StationAudit.Status.WARN, status(unsigned, StationAudit.Topic.NAME_SIGNS), messages(unsigned));
	}

	@Test
	void sizeIsMeasured() {
		final StationAudit.Result result = StationAudit.audit(plan(StationSpec.builder().platformLength(48).roof(RoofType.CANOPY)));
		assertEquals(StationGeometry.builtLength(48) + 2 * StationGeometry.RAMP_LENGTH, result.length());
		assertEquals(StationGeometry.CANOPY_Y + 1, result.height());
	}

	@Test
	void islandPlatformsWithFootbridgeAreReachedByStairs() {
		final StationPlan plan = plan(StationSpec.builder().trackCount(3).platformLayout(PlatformLayout.ISLAND).footbridge(true).platformLength(48).platformWidth(6));
		final StationAudit.Result result = StationAudit.audit(plan);
		assertTrue(result.buildable(), messages(result));
		assertTrue(result.access().contains(StationAudit.Access.STAIRS) || result.access().contains(StationAudit.Access.STEP_FREE), messages(result));
		for (final StationAudit.Check check : result.checks(StationAudit.Topic.ACCESS)) {
			if (check.status() == StationAudit.Status.WARN) {
				assertTrue(check.message().contains("stairs"), messages(result));
			}
		}
	}

	@Test
	void buildingStationsPassLightingAndFurnishing() {
		for (final BuildingStyle style : BuildingStyle.values()) {
			if (style == BuildingStyle.NONE) {
				continue;
			}
			final StationPlan plan = plan(StationSpec.builder().building(style).platformLength(48));
			final StationAudit.Result result = StationAudit.audit(plan);
			assertTrue(result.buildable(), style + "\n" + messages(result));
			assertEquals(StationAudit.Status.PASS, status(result, StationAudit.Topic.LIGHTING), style + "\n" + messages(result));
			assertEquals(StationAudit.Status.PASS, status(result, StationAudit.Topic.FURNISHING), style + "\n" + messages(result));
		}
	}

	@Test
	void strippedBuildingFailsLightingAndFurnishing() {
		final StationPlan plan = plan(StationSpec.builder().building(BuildingStyle.MODERN).platformLength(48));
		final StationPlan noGates = without(plan, b -> b.role() == Role.GATE);
		assertEquals(StationAudit.Status.FAIL, status(StationAudit.audit(noGates), StationAudit.Topic.FURNISHING));
		final StationPlan noLights = without(plan, b -> b.role() == Role.LIGHT_PANEL);
		assertEquals(StationAudit.Status.FAIL, status(StationAudit.audit(noLights), StationAudit.Topic.LIGHTING));
	}
}
