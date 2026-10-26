package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enviro's play-test, 2026-10-20 (relayed by Jah): the fixes that are not platform numbering. */
class EnviroFixesTest {

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	/** Upstairs, seats stand only in a waiting room (glass partition, its board) or in rows on a mezzanine. */
	@Test
	void noLoneBenchesUpstairs() {
		final List<String> stray = new ArrayList<>();
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			final StationPlan plan = plan(preset.description());
			if (plan.spec().mezzanine()) {
				continue;
			}
			final List<PlannedBlock> boards = plan.blocks().stream()
					.filter(b -> b.blockId().endsWith(":concourse_board") && b.y() > StationGeometry.UPPER_FLOOR_Y).toList();
			final java.util.Set<String> benches = new java.util.HashSet<>();
			for (final PlannedBlock b : plan.blocks()) {
				if (b.role() == Role.BENCH) {
					benches.add(b.u() + "," + b.y() + "," + b.v());
				}
			}
			for (final PlannedBlock bench : plan.blocks()) {
				if (bench.role() != Role.BENCH || bench.y() < StationGeometry.UPPER_WALK_Y) {
					continue;
				}
				// a stacked station's upper platform is a platform, not a concourse upstairs (Jah 10-26)
				if (plan.layout().levels().contains(bench.y() - StationGeometry.WALK_Y)) {
					continue;
				}
				final boolean inRoom = boards.stream().anyMatch(board -> Math.abs(board.v() - bench.v()) <= StationGeometry.BAY);
				// or one of a concourse's islands of seats (ConcourseFit): a bench back to back with it and one beside it
				final boolean island = (benches.contains(bench.u() + "," + bench.y() + "," + (bench.v() - 1))
						|| benches.contains(bench.u() + "," + bench.y() + "," + (bench.v() + 1)))
						&& (benches.contains((bench.u() - 1) + "," + bench.y() + "," + bench.v()) || benches.contains((bench.u() + 1) + "," + bench.y() + "," + bench.v()));
				if (!inRoom && !island) {
					stray.add(preset.key() + " " + bench.u() + "," + bench.y() + "," + bench.v());
				}
			}
		}
		assertEquals(List.of(), stray);
	}

	/** By default a gate line is MTR's own barriers: entry lanes face the platforms, exit lanes the street, glass at the ends. */
	@Test
	void gateLinesAreMtrBarriersByDefault() {
		final StationPlan plan = plan("2 track station, station building, 48 long");
		final List<PlannedBlock> gates = plan.blocks().stream().filter(b -> b.role() == Role.GATE).toList();
		assertEquals(5, gates.size());
		final long in = gates.stream().filter(b -> b.blockId().equals(StylePalette.MTR_BARRIER_ENTRANCE)).count();
		final long out = gates.stream().filter(b -> b.blockId().equals(StylePalette.MTR_BARRIER_EXIT)).count();
		assertEquals(3, in);
		assertEquals(2, out);
		// people come in from the street (minus u) and walk towards the platforms; MTR's barrier faces the way they walk
		gates.forEach(g -> assertEquals(g.blockId().equals(StylePalette.MTR_BARRIER_EXIT) ? LocalDir.MINUS_U : LocalDir.PLUS_U, g.front(), g.toString()));
		final StationPlan ata = plan("2 track station, station building, 48 long, ata gates");
		assertEquals(7, ata.blocks().stream().filter(b -> b.role() == Role.GATE && b.blockId().endsWith(":fare_gate")).count());
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable());
	}

	/** Jah, 10-21: the end of an MTR bank is the line's own glass fence, not a mid-cell panel standing out in front of it. */
	@Test
	void mtrGateBanksEndInTheFenceLine() {
		final List<String> stray = new ArrayList<>();
		for (final String text : List.of("2 track station, station building, 48 long", "Utrecht terminus, 10 tracks, 200 long, seed tmptnb")) {
			final StationPlan plan = plan(text);
			final java.util.Map<String, PlannedBlock> at = new java.util.HashMap<>();
			plan.blocks().forEach(b -> at.put(b.u() + "," + b.y() + "," + b.v(), b));
			for (final PlannedBlock gate : plan.blocks()) {
				if (gate.role() != Role.GATE || gate.y() != StationGeometry.WALK_Y || !gate.blockId().startsWith("mtr:")) {
					continue;
				}
				for (final int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					final PlannedBlock end = at.get((gate.u() + d[0]) + "," + gate.y() + "," + (gate.v() + d[1]));
					if (end != null && end.role() == Role.BARRIER && !end.blockId().equals(PlanState.MTR_FENCE)
							&& !at.containsKey(end.u() + "," + (end.y() + 1) + "," + end.v())) {
						stray.add(text + ": " + end);
					}
				}
			}
		}
		assertEquals(List.of(), stray);
	}

	/** The lanes of the one gate bank of a small side-building station: a lane per door cell. */
	private static long lanes(String text) {
		return plan(text).blocks().stream().filter(b -> b.role() == Role.GATE).count();
	}

	/** Enviro: wider station entrance. Doors are 5 wide (7 at a grand station) or as asked, and the gate bank widens with them. */
	@Test
	void entrancesAreWiderAndCanBeAskedFor() {
		assertEquals(5, lanes("2 track station, station building, 48 long"));
		assertEquals(7, lanes("2 track station, station building, 48 long, entrance 7 blocks wide"));
		assertEquals(7, lanes("2 track station, 48 long, wide entrance"));
		assertEquals(3, lanes("2 track station, station building, 48 long, narrow entrance"));
		assertEquals(9, lanes("2 track station, station building, 48 long, 9 wide doorway"));
		final ParseResult asked = StationDescriptionParser.parse("2 track station, 48 long, entrance width 7");
		assertEquals(7, asked.spec().options().entranceWidth());
		assertTrue(asked.spec().hasBuilding(), "an entrance asks for the building it is in");
		// platform width words still mean the platforms
		assertEquals(StationOptions.DEFAULTS.entranceWidth(), StationDescriptionParser.parse("2 track station, 6 wide").spec().options().entranceWidth());
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			for (final String extra : new String[]{"", ", entrance 9 wide"}) {
				final StationPlan plan = plan(preset.description() + extra);
				assertEquals(List.of(), LayoutValidator.validate(plan), preset.key() + extra);
				assertTrue(StationAudit.audit(plan).buildable(), preset.key() + extra);
			}
		}
	}
}
