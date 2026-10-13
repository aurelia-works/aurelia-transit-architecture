package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One name sign per place: the signs are double-sided, so two rows side by side (back-to-back platforms) read as a pair stacked. */
class NameSignsTest {

	private static final String[] TEXTS = {
			"2 tracks side, named Halt",
			"4 tracks island, footbridge, named Halt",
			"6 tracks island 120 long, station building, named Halt",
			"8 track terminus, island, 160 long, named Halt",
			"hub 8 tracks, named Halt",
			"platform 1: side; platform 2: side; platform 3: side; platform 4: side; named Halt",
			"platform 1: side; platform 2: side; platform 3: side; platform 4: side; platform 5: side; platform 6: side; platform 7: side; "
					+ "platform 8: side; platform 9: side; platform 10: side; terminus, named Halt",
			"platform 1: side; platform 2: island; platform 3: side; platform 4: side; footbridge, named Halt",
			"platform 1: island; platform 2: side bay; platform 3: side bay; platform 4: island; terminus, named Halt",
	};

	@Test
	void noTwoSignRowsSideBySide() {
		final List<String> problems = new ArrayList<>();
		for (final String text : TEXTS) {
			final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(text).spec());
			final List<PlannedBlock> signs = plan.blocks().stream().filter(b -> b.role() == Role.NAME_SIGN).toList();
			assertTrue(!signs.isEmpty(), text + " has name signs");
			for (final PlannedBlock a : signs) {
				for (final PlannedBlock b : signs) {
					if (a.u() != b.u() && Math.abs(a.u() - b.u()) <= 2 && Math.abs(a.v() - b.v()) <= 2) {
						problems.add(text + ": signs at u=" + a.u() + " and u=" + b.u() + " near v=" + a.v());
					}
				}
			}
		}
		assertEquals(List.of(), problems.stream().distinct().limit(20).toList());
	}
}
