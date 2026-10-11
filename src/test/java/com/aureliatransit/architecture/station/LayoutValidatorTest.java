package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts from valid planner output and breaks it one rule at a time.
 */
class LayoutValidatorTest {

	private static final String ATA = "aurelia_transit_architecture:";

	// two side platforms 0..3 and 11..14, tracks at u = 5 and 9, canopy columns on u = 1 and 12
	private static StationPlan canopy() {
		return StationPlanner.plan(StationSpec.builder().roof(RoofType.CANOPY).furniture(true).build());
	}

	private static StationPlan hall() {
		return StationPlanner.plan(StationSpec.builder().roof(RoofType.HALL).build());
	}

	private static StationPlan with(StationPlan plan, List<PlannedBlock> blocks) {
		return new StationPlan(plan.spec(), plan.layout(), blocks, plan.trackEnvelopes(), plan.headroom(), plan.supportAreas(), plan.foundationId());
	}

	private static PlannedBlock block(int u, int y, int v, Role role, LocalDir front) {
		return new PlannedBlock(u, y, v, role, ATA + "test_" + role.name().toLowerCase(), front, "");
	}

	private static Set<Violation.Kind> kinds(StationPlan plan) {
		final Set<Violation.Kind> kinds = EnumSet.noneOf(Violation.Kind.class);
		for (final Violation violation : LayoutValidator.validate(plan)) {
			kinds.add(violation.kind());
		}
		return kinds;
	}

	private static int count(StationPlan plan, Violation.Kind kind) {
		int count = 0;
		for (final Violation violation : LayoutValidator.validate(plan)) {
			if (violation.kind() == kind) {
				count++;
			}
		}
		return count;
	}

	private static PlannedBlock find(StationPlan plan, int u, int y, int v) {
		for (final PlannedBlock block : plan.blocks()) {
			if (block.u() == u && block.y() == y && block.v() == v) {
				return block;
			}
		}
		throw new AssertionError("no block at " + u + "," + y + "," + v);
	}

	private static List<PlannedBlock> without(StationPlan plan, PlannedBlock removed) {
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		assertTrue(blocks.remove(removed));
		return blocks;
	}

	private static PlannedBlock moved(PlannedBlock block, int u, int y, int v) {
		return new PlannedBlock(u, y, v, block.role(), block.blockId(), block.front(), block.text());
	}

	private static PlannedBlock fronted(PlannedBlock block, LocalDir front) {
		return new PlannedBlock(block.u(), block.y(), block.v(), block.role(), block.blockId(), front, block.text());
	}

	@Test
	void validPlansHaveNoViolations() {
		assertEquals(List.of(), LayoutValidator.validate(canopy()));
		assertEquals(List.of(), LayoutValidator.validate(hall()));
		assertEquals(List.of(), LayoutValidator.validate(StationPlanner.plan(StationSpec.builder().roof(RoofType.NONE).trackCount(4)
				.platformLayout(PlatformLayout.ISLAND).name("Alpha").build())));
		assertEquals(List.of(), LayoutValidator.validate(StationPlanner.plan(StationSpec.builder().roof(RoofType.HALL).trackCount(3)
				.platformLayout(PlatformLayout.ISLAND).platformWidth(8).name("Alpha").build())));
	}

	@Test
	void columnInsideATrackEnvelopeIsAnIntrusion() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(5, 1, 20, Role.COLUMN, LocalDir.PLUS_V));
		final StationPlan broken = with(plan, blocks);
		assertTrue(kinds(broken).contains(Violation.Kind.ENVELOPE_INTRUSION));
		assertEquals(1, count(broken, Violation.Kind.ENVELOPE_INTRUSION));
	}

	@Test
	void railNodesAreAllowedInsideAnEnvelope() {
		final StationPlan plan = canopy();
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.RAIL_NODE));
		assertEquals(0, count(plan, Violation.Kind.ENVELOPE_INTRUSION));
	}

	@Test
	void hallRoofLoweredOverATrackIsAnIntrusion() {
		final StationPlan plan = hall();
		final List<PlannedBlock> blocks = new ArrayList<>();
		int lowered = 0;
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role().roofPart() && block.role() != Role.BEAM && Math.abs(block.u() - 5) <= 1) {
				blocks.add(moved(block, block.u(), 4, block.v()));
				lowered++;
			} else {
				blocks.add(block);
			}
		}
		final StationPlan broken = with(plan, blocks);
		assertTrue(lowered > 0);
		assertEquals(lowered, count(broken, Violation.Kind.ENVELOPE_INTRUSION));
	}

	@Test
	void canopyAtHeightThreeLeavesTooLittleHeadroom() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.COLUMN && block.y() == 2) {
				continue;
			}
			if (block.role() == Role.COLUMN_CAP) {
				blocks.add(moved(block, block.u(), 2, block.v()));
			} else if (block.role().roofPart()) {
				blocks.add(moved(block, block.u(), 3, block.v()));
			} else {
				blocks.add(block);
			}
		}
		final StationPlan broken = with(plan, blocks);
		assertEquals(EnumSet.of(Violation.Kind.HEADROOM), kinds(broken));
		assertEquals(2 * 48 * 4, count(broken, Violation.Kind.HEADROOM));
	}

	@Test
	void benchOnAnEdgeCellIsAnEdgeClearwayViolation() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(3, 1, 5, Role.BENCH, LocalDir.PLUS_U));
		final StationPlan broken = with(plan, blocks);
		assertEquals(EnumSet.of(Violation.Kind.EDGE_CLEARWAY), kinds(broken));
		assertEquals(1, count(broken, Violation.Kind.EDGE_CLEARWAY));
	}

	@Test
	void edgeAtTheWrongOffsetIsReported() {
		final StationPlan plan = canopy();
		final PlannedBlock edge = find(plan, 3, 0, 10);
		final PlannedBlock surface = find(plan, 2, 0, 10);

		// offset 3: the edge swaps places with the surface block behind it
		final List<PlannedBlock> far = new ArrayList<>(plan.blocks());
		far.set(far.indexOf(edge), moved(surface, 3, 0, 10));
		far.set(far.indexOf(surface), moved(edge, 2, 0, 10));
		final StationPlan tooFar = with(plan, far);
		assertEquals(EnumSet.of(Violation.Kind.PLATFORM_OFFSET), kinds(tooFar));
		assertEquals(2, count(tooFar, Violation.Kind.PLATFORM_OFFSET));

		// offset 1: the edge sits in the gap right beside the rail
		final List<PlannedBlock> near = new ArrayList<>(plan.blocks());
		near.set(near.indexOf(edge), moved(surface, 3, 0, 10));
		near.add(moved(edge, 4, 0, 10));
		final StationPlan tooNear = with(plan, near);
		assertTrue(kinds(tooNear).contains(Violation.Kind.PLATFORM_OFFSET));
		assertTrue(kinds(tooNear).contains(Violation.Kind.ENVELOPE_INTRUSION));
	}

	@Test
	void edgeFacingAwayFromItsTrackIsReported() {
		final StationPlan plan = canopy();
		final PlannedBlock edge = find(plan, 3, 0, 10);
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.set(blocks.indexOf(edge), fronted(edge, LocalDir.MINUS_U));
		final StationPlan broken = with(plan, blocks);
		assertEquals(EnumSet.of(Violation.Kind.PLATFORM_OFFSET), kinds(broken));

		final List<PlannedBlock> sideways = new ArrayList<>(plan.blocks());
		sideways.set(sideways.indexOf(edge), fronted(edge, LocalDir.PLUS_V));
		assertEquals(EnumSet.of(Violation.Kind.PLATFORM_OFFSET), kinds(with(plan, sideways)));
	}

	@Test
	void missingEdgeIsReportedOncePerTrackSideAtTheFirstBadCell() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		for (int v = 7; v < 20; v++) {
			final PlannedBlock edge = find(plan, 3, 0, v);
			blocks.set(blocks.indexOf(edge), moved(find(plan, 2, 0, v), 3, 0, v));
		}
		final List<Violation> offsets = new ArrayList<>();
		for (final Violation violation : LayoutValidator.validate(with(plan, blocks))) {
			if (violation.kind() == Violation.Kind.PLATFORM_OFFSET) {
				offsets.add(violation);
			}
		}
		assertEquals(1, offsets.size());
		assertEquals(3, offsets.get(0).u());
		assertEquals(7, offsets.get(0).v());
	}

	@Test
	void tracksCloserThanTheMinimumSpacingAreReported() {
		final StationPlan plan = canopy();
		final TrackLayout layout = new TrackLayout(7, 48, 0, List.of(new TrackLayout.Track(1, 1, false, false), new TrackLayout.Track(2, 4, false, false)),
				List.of());
		final List<PlannedBlock> blocks = List.of(block(1, 0, 0, Role.RAIL_NODE, LocalDir.PLUS_V), block(4, 0, 0, Role.RAIL_NODE, LocalDir.PLUS_V));
		final StationPlan close = new StationPlan(plan.spec(), layout, blocks, List.of(), List.of(), List.of(), plan.foundationId());
		assertEquals(EnumSet.of(Violation.Kind.TRACK_SPACING), kinds(close));
		assertEquals(1, count(close, Violation.Kind.TRACK_SPACING));

		final TrackLayout fine = new TrackLayout(8, 48, 0, List.of(new TrackLayout.Track(1, 1, false, false), new TrackLayout.Track(2, 5, false, false)),
				List.of());
		assertEquals(List.of(), LayoutValidator.validate(new StationPlan(plan.spec(), fine, List.of(), List.of(), List.of(), List.of(), plan.foundationId())));
	}

	@Test
	void columnWithAMissingMiddleCellIsUnsupported() {
		final StationPlan plan = canopy();
		final StationPlan broken = with(plan, without(plan, find(plan, 1, 2, 18)));
		assertEquals(EnumSet.of(Violation.Kind.UNSUPPORTED_COLUMN), kinds(broken));
		assertEquals(1, count(broken, Violation.Kind.UNSUPPORTED_COLUMN));
	}

	@Test
	void columnWithoutACapOrRoofOrFloorIsUnsupported() {
		final StationPlan plan = canopy();
		// no roof above the cap
		final PlannedBlock roof = find(plan, 1, 4, 18);
		assertTrue(kinds(with(plan, without(plan, roof))).contains(Violation.Kind.UNSUPPORTED_COLUMN));
		// a plain column where the cap should be
		final PlannedBlock cap = find(plan, 1, 3, 18);
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.set(blocks.indexOf(cap), new PlannedBlock(1, 3, 18, Role.COLUMN, cap.blockId(), cap.front(), ""));
		assertTrue(kinds(with(plan, blocks)).contains(Violation.Kind.UNSUPPORTED_COLUMN));
		// standing on nothing
		final List<PlannedBlock> floating = new ArrayList<>(plan.blocks());
		floating.remove(find(plan, 1, 0, 18));
		assertEquals(EnumSet.of(Violation.Kind.UNSUPPORTED_COLUMN), kinds(with(plan, floating)));
	}

	@Test
	void roofCellSixAwayFromAnySupportIsUnsupported() {
		// 47 long: the last column is at v = 42, so a roof cell at v = 48 (over the ramp) is 6 away from it
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().roof(RoofType.CANOPY).platformLength(47).build());
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(1, 4, 48, Role.ROOF, LocalDir.PLUS_V));
		final StationPlan broken = with(plan, blocks);
		assertEquals(EnumSet.of(Violation.Kind.ROOF_UNSUPPORTED), kinds(broken));
		assertEquals(1, count(broken, Violation.Kind.ROOF_UNSUPPORTED));
	}

	@Test
	void removingAWholeColumnLeavesTheRoofUnsupported() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (!((block.role() == Role.COLUMN || block.role() == Role.COLUMN_CAP) && block.v() == 26 && block.u() == 1)) {
				blocks.add(block);
			}
		}
		final StationPlan broken = with(plan, blocks);
		assertEquals(EnumSet.of(Violation.Kind.ROOF_UNSUPPORTED), kinds(broken));
	}

	@Test
	void roofAtOrBelowItsOnlySupportIsUnsupported() {
		// the canopy cell right above a cap is carried by the cap; a cell at the cap's own height is not
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(2, 3, 18, Role.ROOF, LocalDir.PLUS_V));
		assertEquals(EnumSet.of(Violation.Kind.ROOF_UNSUPPORTED, Violation.Kind.HEADROOM), kinds(with(plan, blocks)));
	}

	@Test
	void sideHallOverThreeTracksCannotBeSpanned() {
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().roof(RoofType.HALL).trackCount(3).build());
		assertEquals(EnumSet.of(Violation.Kind.ROOF_SPAN), kinds(plan));
		// one violation for each of the beam lines
		assertEquals(StationPlanner.columnPositions(48).size(), count(plan, Violation.Kind.ROOF_SPAN));
	}

	@Test
	void beamWithoutAColumnOrWithATooLongOverhangIsReported() {
		final StationPlan plan = hall();
		// drop both caps (and their columns) of one beam line
		final List<PlannedBlock> bare = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (!((block.role() == Role.COLUMN || block.role() == Role.COLUMN_CAP) && block.v() == 18)) {
				bare.add(block);
			}
		}
		assertEquals(EnumSet.of(Violation.Kind.ROOF_SPAN), kinds(with(plan, bare)));
		assertEquals(1, count(with(plan, bare), Violation.Kind.ROOF_SPAN));

		// drop only the left column of one beam line: the beam then overhangs 12 cells on the left
		final List<PlannedBlock> oneSided = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (!((block.role() == Role.COLUMN || block.role() == Role.COLUMN_CAP) && block.v() == 18 && block.u() < 5)) {
				oneSided.add(block);
			}
		}
		assertEquals(EnumSet.of(Violation.Kind.ROOF_SPAN), kinds(with(plan, oneSided)));
	}

	@Test
	void duplicateCellIsAnOverlap() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(find(plan, 1, 0, 10));
		final StationPlan broken = with(plan, blocks);
		assertEquals(EnumSet.of(Violation.Kind.OVERLAP), kinds(broken));
		assertEquals(1, count(broken, Violation.Kind.OVERLAP));
	}

	@Test
	void blockLeftOfTheFootprintIsOutOfBounds() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(-1, 0, 5, Role.PLATFORM_SURFACE, LocalDir.PLUS_V));
		blocks.add(block(15, 0, 5, Role.PLATFORM_SURFACE, LocalDir.PLUS_V));
		blocks.add(block(0, 0, 50, Role.PLATFORM_SURFACE, LocalDir.PLUS_V));
		blocks.add(block(0, 0, -3, Role.PLATFORM_SURFACE, LocalDir.PLUS_V));
		blocks.add(block(2, 65, 5, Role.ROOF, LocalDir.PLUS_V));
		blocks.add(block(2, -2, 5, Role.FOUNDATION, LocalDir.PLUS_V));
		final StationPlan broken = with(plan, blocks);
		assertTrue(kinds(broken).contains(Violation.Kind.OUT_OF_BOUNDS));
		assertEquals(6, count(broken, Violation.Kind.OUT_OF_BOUNDS));
	}

	@Test
	void lampOverTheGapBetweenTracksIsFloating() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(7, 1, 5, Role.LAMP, LocalDir.PLUS_U));
		final StationPlan broken = with(plan, blocks);
		assertEquals(EnumSet.of(Violation.Kind.FLOATING_FIXTURE), kinds(broken));
		assertEquals(1, count(broken, Violation.Kind.FLOATING_FIXTURE));

		final List<PlannedBlock> stacked = new ArrayList<>(plan.blocks());
		stacked.add(block(2, 2, 5, Role.BIN, LocalDir.PLUS_U));
		assertEquals(EnumSet.of(Violation.Kind.FLOATING_FIXTURE), kinds(with(plan, stacked)));
	}

	@Test
	void violationsComeInRuleOrderThenYVU() {
		final StationPlan plan = canopy();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(7, 1, 9, Role.LAMP, LocalDir.PLUS_U));
		blocks.add(block(7, 1, 3, Role.LAMP, LocalDir.PLUS_U));
		blocks.add(block(3, 1, 30, Role.BENCH, LocalDir.PLUS_U));
		blocks.add(find(plan, 1, 0, 10));
		blocks.add(block(-1, 0, 5, Role.PLATFORM_SURFACE, LocalDir.PLUS_V));
		blocks.add(block(5, 1, 20, Role.COLUMN, LocalDir.PLUS_V));
		final List<Violation> violations = LayoutValidator.validate(with(plan, blocks));

		final List<Violation.Kind> ruleOrder = List.of(Violation.Kind.OUT_OF_BOUNDS, Violation.Kind.OVERLAP, Violation.Kind.ENVELOPE_INTRUSION,
				Violation.Kind.TRACK_SPACING, Violation.Kind.PLATFORM_OFFSET, Violation.Kind.HEADROOM, Violation.Kind.EDGE_CLEARWAY,
				Violation.Kind.UNSUPPORTED_COLUMN, Violation.Kind.ROOF_UNSUPPORTED, Violation.Kind.ROOF_SPAN, Violation.Kind.FLOATING_FIXTURE);
		final Comparator<Violation> cellOrder = Comparator.comparingInt(Violation::y).thenComparingInt(Violation::v).thenComparingInt(Violation::u);
		assertTrue(violations.size() >= 6);
		for (int i = 0; i + 1 < violations.size(); i++) {
			final Violation a = violations.get(i);
			final Violation b = violations.get(i + 1);
			final int byRule = Integer.compare(ruleOrder.indexOf(a.kind()), ruleOrder.indexOf(b.kind()));
			assertTrue(byRule < 0 || byRule == 0 && cellOrder.compare(a, b) <= 0, a + " before " + b);
		}
	}

	@Test
	void resultDoesNotDependOnTheBlockOrderOrOnRepeatedCalls() {
		final StationPlan plan = hall();
		final List<PlannedBlock> blocks = new ArrayList<>(plan.blocks());
		blocks.add(block(7, 1, 9, Role.LAMP, LocalDir.PLUS_U));
		blocks.add(find(plan, 1, 0, 10));
		blocks.add(find(plan, 3, 0, 11));
		blocks.add(block(5, 1, 20, Role.COLUMN, LocalDir.PLUS_V));
		blocks.remove(find(plan, 2, 4, 18));
		final List<Violation> reference = LayoutValidator.validate(with(plan, blocks));
		assertFalse(reference.isEmpty());
		assertEquals(reference, LayoutValidator.validate(with(plan, blocks)));
		for (int seed = 0; seed < 5; seed++) {
			final List<PlannedBlock> shuffled = new ArrayList<>(blocks);
			Collections.shuffle(shuffled, new Random(seed));
			assertEquals(reference, LayoutValidator.validate(with(plan, shuffled)), "shuffle " + seed);
		}
	}

	@Test
	void fixturesStandOnFlatFloorsAndSkipWallMounted() {
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().building(BuildingStyle.MODERN).build());
		assertEquals(Set.of(), kinds(plan));
		// a bench on the building floor is fine
		final List<PlannedBlock> benches = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.BENCH && block.u() < plan.layout().buildingWidth()) {
				benches.add(block);
			}
		}
		assertFalse(benches.isEmpty());
		// a bench floating one above the floor is reported
		final List<PlannedBlock> floating = new ArrayList<>(plan.blocks());
		final PlannedBlock bench = benches.get(0);
		floating.add(new PlannedBlock(bench.u(), bench.y() + 1, bench.v(), Role.BENCH, bench.blockId(), bench.front(), ""));
		assertTrue(kinds(with(plan, floating)).contains(Violation.Kind.FLOATING_FIXTURE));
		// a wall-mounted fixture needs no floor (tested on its own, without the floating bench)
		final List<PlannedBlock> mounted = new ArrayList<>(plan.blocks());
		mounted.add(new PlannedBlock(bench.u(), bench.y() + 2, bench.v(), Role.FIXTURE, ATA + "test_fixture", LocalDir.PLUS_V, ""));
		assertFalse(kinds(with(plan, mounted)).contains(Violation.Kind.FLOATING_FIXTURE));
	}

	@Test
	void bridgePiersCarryCanopyRoofsAndDeckSpansNeedPiers() {
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().building(BuildingStyle.MODERN).platformLength(48).build());
		assertEquals(Set.of(), kinds(plan));
		// dropping a bridge pier leaves the deck span unsupported
		PlannedBlock pier = null;
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.BRIDGE_PIER) {
				pier = block;
				break;
			}
		}
		assertTrue(pier != null);
		final int pierU = pier.u();
		final int pierV = pier.v();
		final List<PlannedBlock> without = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (!(block.u() == pierU && block.v() == pierV)) {
				without.add(block);
			}
		}
		assertTrue(kinds(with(plan, without)).contains(Violation.Kind.ROOF_UNSUPPORTED)
				|| kinds(with(plan, without)).contains(Violation.Kind.ROOF_SPAN));
	}
}
