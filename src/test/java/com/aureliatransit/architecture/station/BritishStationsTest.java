package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enviro, 10-20: British suburban stations, after Teddington, Surbiton and Coventry. */
class BritishStationsTest {

	private static final String ATA = "aurelia_transit_architecture:";

	private static StationPlan plan(String text) {
		return StationPlanner.plan(StationDescriptionParser.parse(text).spec());
	}

	private static Set<String> ids(StationPlan plan, Role role) {
		return plan.blocks().stream().filter(b -> b.role() == role).map(PlannedBlock::blockId).collect(Collectors.toSet());
	}

	@Test
	void wordsPickTheBritishLooks() {
		assertEquals(BuildingStyle.VICTORIAN, StationDescriptionParser.parse("british suburban station").spec().building());
		assertEquals(List.of(), StationDescriptionParser.parse("british suburban station").ignored());
		assertEquals(BuildingStyle.VICTORIAN, StationDescriptionParser.parse("2 track victorian station").spec().building());
		assertEquals(BuildingStyle.ART_DECO, StationDescriptionParser.parse("2 track art deco station").spec().building());
		assertEquals(BuildingStyle.MODERNIST, StationDescriptionParser.parse("2 track coventry station").spec().building());
		assertEquals(BuildingStyle.GOTHIC_BRICK, StationDescriptionParser.parse("2 track london station").spec().building());
		assertEquals(Operator.NATIONAL_RAIL, StationDescriptionParser.parse("2 track surbiton station").spec().operator());
		assertEquals(BuildingStyle.ART_DECO, StationDescriptionParser.parse("regional class, interwar era").spec().building());
	}

	/** Suburban stations stand under canopies; a ridge-and-furrow shed only when asked. */
	@Test
	void aVictorianStationHasCanopiesUnlessAShedIsAsked() {
		assertEquals(RoofType.CANOPY, StationDescriptionParser.parse("2 track teddington station").spec().roof());
		final StationSpec shed = StationDescriptionParser.parse("4 track teddington station, train shed").spec();
		assertEquals(RoofType.HALL, shed.roof());
		assertEquals(ShedShape.GABLED, shed.shed());
	}

	@Test
	void britishPlatformsAreTarmacWithCopingAndTheirStationsCanopy() {
		final String[][] looks = {{"teddington", "uk_timber_canopy", "uk_canopy_valance"}, {"surbiton", "uk_concrete_canopy", "uk_concrete_canopy_edge"},
				{"coventry", "uk_hardwood_canopy", "uk_hardwood_canopy_fascia"}};
		for (final String[] look : looks) {
			final StationPlan plan = plan("2 track " + look[0] + " station, 64 long");
			assertEquals(Set.of(ATA + "uk_platform_tarmac"), ids(plan, Role.PLATFORM_SURFACE), look[0]);
			assertTrue(ids(plan, Role.PLATFORM_EDGE).stream().allMatch(id -> id.startsWith(ATA + "uk_platform_coping")), look[0]);
			assertTrue(ids(plan, Role.ROOF).contains(ATA + look[1]), look[0] + " " + ids(plan, Role.ROOF));
			assertTrue(ids(plan, Role.ROOF_EDGE).contains(ATA + look[2]), look[0] + " " + ids(plan, Role.ROOF_EDGE));
		}
		// another look keeps its own platforms
		assertFalse(ids(plan("2 track hamburg station, 64 long"), Role.PLATFORM_SURFACE).contains(ATA + "uk_platform_tarmac"));
	}

	@Test
	void thePresetsAreClean() {
		for (final String key : List.of("teddington", "surbiton", "coventry")) {
			final StationPresets.Preset preset = StationPresets.ALL.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
			final ParseResult parse = StationDescriptionParser.parse(preset.description());
			assertEquals(preset.building(), parse.spec().building(), key);
			assertEquals(List.of(), parse.warnings(), key);
			assertEquals(List.of(), parse.ignored(), key);
			final StationPlan plan = StationPlanner.plan(parse.spec());
			assertEquals(List.of(), LayoutValidator.validate(plan), key);
			assertTrue(StationAudit.audit(plan).buildable(), key);
		}
		// Teddington's two side platforms face each other, Surbiton's are two islands, Coventry an island and a side platform
		assertEquals(PlatformLayout.SIDE, StationDescriptionParser.parse(StationPresets.ALL.stream().filter(p -> p.key().equals("teddington"))
				.findFirst().orElseThrow().description()).spec().platformLayout());
	}

	/** Four tracks pair off onto islands: "island and side" there would silently lose the side platform. */
	@Test
	void anIslandAndSidePlatformNeedsAnOddTrackCount() {
		assertTrue(StationDescriptionParser.parse("4 track station, island and side platforms").warnings().stream()
				.anyMatch(w -> w.contains("no track for a side platform")));
		assertEquals(List.of(), StationDescriptionParser.parse("3 track station, island and side platforms").warnings());
	}
}
