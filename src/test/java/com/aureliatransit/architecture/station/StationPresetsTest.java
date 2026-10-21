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
			assertTrue(parse.spec().footbridge() || parse.spec().terminus(), preset.key() + " has neither a footbridge nor a concourse");
			assertTrue(parse.warnings().isEmpty(), preset.key() + " warnings: " + parse.warnings());
			assertTrue(parse.ignored().isEmpty(), preset.key() + " ignored: " + parse.ignored());
			final StationPlan plan = StationPlanner.plan(parse.spec());
			assertTrue(LayoutValidator.validate(plan).isEmpty(), preset.key() + " violations: " + LayoutValidator.validate(plan));
			final StationAudit.Result audit = StationAudit.audit(plan);
			// every platform is step-free, by a lift where the footbridge serves it; nothing may warn
			assertTrue(audit.buildable() && audit.problems().isEmpty(), preset.key() + " checks: " + audit.problems());
		}
		for (final BuildingStyle look : BuildingStyle.values()) {
			// clinker lost its preset with Hamburg Dammtor (Jah, 10-21: Hamburg is the Hbf); it is still asked for by name
			assertTrue(look == BuildingStyle.NONE || look == BuildingStyle.CLINKER || looks.contains(look), "no preset for " + look);
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
}
