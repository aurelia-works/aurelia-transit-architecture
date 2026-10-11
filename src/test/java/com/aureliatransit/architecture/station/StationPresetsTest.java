package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every preset is a clean description: understood in full, the look it promises, no rule breaks, every check passes. */
class StationPresetsTest {

	@Test
	void everyPresetPlansCleanly() {
		final Set<String> keys = new HashSet<>();
		final Set<BuildingStyle> looks = new HashSet<>();
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			assertTrue(keys.add(preset.key()), "duplicate key " + preset.key());
			looks.add(preset.building());
			assertTrue(preset.description().length() <= StationDescriptionParser.MAX_INPUT, preset.key() + " is too long for the wrench");
			final ParseResult parse = StationDescriptionParser.parse(preset.description());
			assertEquals(preset.building(), parse.spec().building(), preset.key());
			assertTrue(parse.spec().footbridge(), preset.key() + " has no footbridge");
			assertTrue(parse.warnings().isEmpty(), preset.key() + " warnings: " + parse.warnings());
			assertTrue(parse.ignored().isEmpty(), preset.key() + " ignored: " + parse.ignored());
			final StationPlan plan = StationPlanner.plan(parse.spec());
			assertTrue(LayoutValidator.validate(plan).isEmpty(), preset.key() + " violations: " + LayoutValidator.validate(plan));
			final StationAudit.Result audit = StationAudit.audit(plan);
			// platforms reached over the footbridge warn that stairs are not step-free (no lifts yet); nothing else may warn
			assertTrue(audit.buildable() && audit.problems().stream().allMatch(StationPresetsTest::stairsOnly), preset.key() + " checks: " + audit.problems());
		}
		for (final BuildingStyle look : BuildingStyle.values()) {
			assertTrue(look == BuildingStyle.NONE || looks.contains(look), "no preset for " + look);
		}
	}

	@Test
	void everyPresetHasAButtonLabel() throws IOException {
		final String lang;
		try (InputStream in = StationPresetsTest.class.getResourceAsStream("/assets/aurelia_transit_architecture/lang/en_us.json")) {
			lang = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			assertTrue(lang.contains("\"screen.aurelia_transit_architecture.station_wrench.preset." + preset.key() + "\""), "no label for " + preset.key());
		}
	}

	private static boolean stairsOnly(StationAudit.Check check) {
		return check.topic() == StationAudit.Topic.ACCESS && check.status() == StationAudit.Status.WARN && check.message().contains("only reached by stairs");
	}
}
