package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** German and Swiss stations have an open concourse unless the words ask for gates; the rest keep their gate line (Jah 10-26). */
class GateLineTest {

	private static StationSpec spec(String words) {
		return StationDescriptionParser.parse(words).spec();
	}

	private static boolean gated(String words) {
		return StationPlanner.plan(spec(words)).blocks().stream().anyMatch(b -> b.role() == Role.GATE);
	}

	private static String preset(String key) {
		return StationPresets.ALL.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().description();
	}

	@Test
	void germanAndSwissTerminiAreOpen() {
		assertFalse(gated(preset("frankfurt")), "Frankfurt has no gate line");
		assertFalse(gated(preset("zurich")), "Zurich has no gate line");
	}

	@Test
	void anOpenConcourseHasNoGateStripes() {
		final boolean striped = StationPlanner.plan(spec(preset("frankfurt"))).blocks().stream()
				.anyMatch(b -> b.role() == Role.FLOOR && StationPlanner.PLATFORM_COLOURS.contains(b.blockId()));
		assertFalse(striped, "no platform colour stripes on Frankfurt's concourse floor");
	}

	@Test
	void gatesAskedForAreBuiltThere() {
		assertTrue(gated(preset("frankfurt") + ", gates"));
		assertTrue(gated(preset("frankfurt") + ", with ticket barriers"));
		assertTrue(gated(preset("frankfurt") + ", mtr gates"));
	}

	@Test
	void otherStationsKeepTheirGates() {
		assertTrue(gated(preset("amsterdam")), "Dutch stations have gates");
		assertTrue(gated(preset("st_pancras")), "British stations have gates");
	}

	@Test
	void theWordsSetTheOption() {
		assertTrue(spec("four track station, gate line").options().gatesAsked());
		assertTrue(spec("four track station, fare gates").options().gatesAsked());
		assertFalse(spec("four track station").options().gatesAsked());
	}
}
