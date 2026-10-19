package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every lift goes somewhere, every stair and escalator is walked from its foot to its top, and every platform is reached from
 * outside: across every preset and the common shapes of station (Jah's "lift to nowhere", 2026-10-19).
 */
class PassengerWalkTest {

	static final String[] WORDS = {"4 tracks island, 96 long, station building, footbridge", "2 tracks side platforms, 64 long, footbridge",
			"6 tracks island, 120 long, grand class, multi-level", "4 tracks island, 160 long, station building, mezzanine, escalators",
			"3 tracks, 80 long, underpass", "4 tracks, 100 long, terminus", "2 tracks, 60 long, step free",
			"6 tracks island, 200 long, footbridge, stairs both ways, escalators", "utrecht centraal, 4 tracks, 90 long"};

	@Test
	void everyPresetAndShapeLeadsSomewhere() {
		final List<String> all = new ArrayList<>(List.of(WORDS));
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			all.add(preset.description());
		}
		for (final String words : all) {
			RealStationTest.everyWayLeadsSomewhere(StationPlanner.plan(StationDescriptionParser.parse(words).spec()), words);
		}
	}

	@Test
	void aLiftWithNoFloorAtItsTopIsCaught() {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(WORDS[0]).spec());
		final List<PlannedBlock> noDeck = plan.blocks().stream().filter(b -> b.role() != Role.BRIDGE_DECK).toList();
		final StationPlan broken = new StationPlan(plan.spec(), plan.layout(), noDeck, plan.trackEnvelopes(), plan.headroom(), plan.supportAreas(),
				plan.foundationId());
		final PassengerWalk walk = new PassengerWalk(broken);
		assertFalse(walk.liftsToNowhere().isEmpty(), "the platform lifts open onto nothing at the top");
		assertFalse(walk.flightsToNowhere().isEmpty(), "the stairs end at nothing at the top");
	}
}
