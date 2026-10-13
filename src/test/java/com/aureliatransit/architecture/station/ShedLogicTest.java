package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Train sheds join up, carry every span and stay within bounds across the station types. */
class ShedLogicTest {

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private static boolean girder(PlannedBlock block) {
		return block != null && block.blockId().endsWith(":station_girder");
	}

	@Test
	void everyGirderJoinsAnotherOnTwoSidesOrMore() {
		for (final String text : List.of("flat train shed island 4 tracks", "several arches train shed 6 tracks island", "arched train shed 4 tracks",
				"pointed train shed with a footbridge 3 tracks island", "terminus 8 tracks several arches train shed")) {
			final StationPlan plan = plan(text);
			final Map<String, PlannedBlock> at = new HashMap<>();
			for (final PlannedBlock block : plan.blocks()) {
				at.put(block.u() + "," + block.y() + "," + block.v(), block);
			}
			final List<String> ends = new ArrayList<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (!girder(block)) {
					continue;
				}
				int joined = 0;
				for (final int[] o : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
					if (girder(at.get((block.u() + o[0]) + "," + (block.y() + o[1]) + "," + (block.v() + o[2])))) {
						joined++;
					}
				}
				if (joined < 2) {
					ends.add(block.u() + "," + block.y() + "," + block.v());
				}
			}
			assertEquals(List.of(), ends, text + ": girders stopping short");
		}
	}

	@Test
	void tracksSideBySideGetColumnsBetweenThem() {
		final StationPlan plan = plan("4 tracks flat train shed side");
		assertEquals(List.of(), LayoutValidator.validate(plan));
		final TrackLayout layout = plan.layout();
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.COLUMN && layout.platforms().stream().noneMatch(p -> b.u() >= p.uMin() && b.u() <= p.uMax())),
				"a column between the tracks");
		for (final TrackLayout.Track track : layout.tracks()) {
			assertTrue(plan.blocks().stream().noneMatch(b -> b.role() == Role.COLUMN && Math.abs(b.u() - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH),
					"no column in track " + track.number() + "'s clearance");
		}
	}

	@Test
	void longFootbridgesAndVaultTiesStayWithinTheirSpan() {
		for (final String text : List.of("6 tracks side with a footbridge", "8 tracks side with a footbridge", "city building 4 tracks arched train shed side 12 wide",
				"hamburg 6 tracks 80 long several arches train shed island")) {
			assertEquals(List.of(), LayoutValidator.validate(plan(text)), text);
		}
	}

	@Test
	void sampledStationsPlanCleanly() {
		final String[] kinds = {"", "with a station building", "city building", "grand building", "terminus", "hub"};
		final String[] roofs = {"canopies", "flat train shed", "arched train shed", "pointed train shed", "several arches train shed", "no roof"};
		final int[] tracks = {1, 2, 3, 4, 5, 6, 8, 10};
		final Random random = new Random(4440);
		final List<String> problems = new ArrayList<>();
		for (int round = 0; round < 250; round++) {
			final String kind = kinds[random.nextInt(kinds.length)];
			final boolean hub = kind.equals("hub");
			final int count = hub ? 4 + 2 * random.nextInt(4) : tracks[random.nextInt(tracks.length)];
			final String text = kind + " " + roofs[random.nextInt(roofs.length)] + " " + (hub || random.nextBoolean() ? "island" : "side") + " " + count
					+ " tracks " + (random.nextBoolean() ? (StationSpec.MIN_WIDTH + 1 + random.nextInt(17)) + " wide " : "")
					+ (!kind.equals("terminus") && !hub && random.nextBoolean() ? "with a footbridge " : "") + (16 + random.nextInt(120)) + " long";
			final StationPlan plan = plan(text);
			final List<Violation> violations = LayoutValidator.validate(plan);
			if (!violations.isEmpty()) {
				problems.add(text + " -> " + violations.get(0));
			} else if (!StationAudit.audit(plan).buildable()) {
				problems.add(text + " -> audit " + StationAudit.audit(plan).problems());
			}
		}
		assertEquals(List.of(), problems);
	}
}
