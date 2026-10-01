package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.transit.StationReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WayfindingResolverTest {

	private static final LineBadge L = new LineBadge("L", 0x0066CC, BadgeShape.ROUNDED);
	private static final LineBadge B = new LineBadge("B", 0xFF8800, BadgeShape.CIRCLE);

	private static StationFacts facts() {
		return new StationFacts(new StationReference(7, "Market Street|Marktstrasse", 0x123456), List.of(L, B),
				List.of(new ExitInfo("A", List.of("City Hall", "Library")), new ExitInfo("B", List.of("Stadium"))));
	}

	private static WayfindingData data(boolean autoStation, String name, List<LineBadge> lines, boolean autoLines, String exit, ServiceType type, String serviceLabel,
									   LanguageLayout layout, String secondary) {
		return new WayfindingData(autoStation, name, secondary, "", lines, autoLines, SignArrow.LEFT, "To East", type, serviceLabel, "2", exit, "Market St", "Bus 12",
				layout, Pictogram.NONE, AccentPalette.NONE, com.aureliatransit.architecture.transit.StationAssociation.AUTO, com.aureliatransit.architecture.wayfinding.BoardView.TRAINS_THIS_SIDE);
	}

	@Test
	void manualNonEmptyValuesAlwaysWin() {
		final ResolvedWayfinding r = WayfindingResolver.merge(data(true, "My Stop", List.of(B), true, "", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), facts());
		assertEquals("My Stop", r.stationName());
		assertFalse(r.stationFromMtr());
		assertEquals(List.of(B), r.lines());
	}

	@Test
	void autoFlagsFillEmptyFields() {
		final ResolvedWayfinding r = WayfindingResolver.merge(data(true, "", List.of(), true, "", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), facts());
		assertEquals("Market Street", r.stationName());
		assertTrue(r.stationFromMtr());
		assertEquals(List.of(L, B), r.lines());
		assertEquals("", r.secondaryName(), "single-language layouts never take a secondary name");
	}

	@Test
	void autoOffShowsOnlyManualData() {
		final ResolvedWayfinding r = WayfindingResolver.merge(data(false, "", List.of(), false, "A", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), facts());
		assertEquals("", r.stationName());
		assertTrue(r.lines().isEmpty());
		assertEquals(List.of("City Hall", "Library"), r.exitDestinations(), "exit destinations come from the facts the source supplied");
		final ResolvedWayfinding none = WayfindingResolver.merge(data(false, "", List.of(), false, "A", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), StationFacts.EMPTY);
		assertTrue(none.exitDestinations().isEmpty());
	}

	@Test
	void mtrSecondaryLanguageComesFromTheMultilingualName() {
		final ResolvedWayfinding r = WayfindingResolver.merge(data(true, "", List.of(), true, "", ServiceType.NONE, "", LanguageLayout.SIDE_BY_SIDE, ""), facts());
		assertEquals("Marktstrasse", r.secondaryName());
		final ResolvedWayfinding manual = WayfindingResolver.merge(data(true, "", List.of(), true, "", ServiceType.NONE, "", LanguageLayout.SIDE_BY_SIDE, "Mercado"), facts());
		assertEquals("Mercado", manual.secondaryName());
		final StationFacts single = new StationFacts(new StationReference(1, "Central", 0), List.of(), List.of());
		assertEquals("", WayfindingResolver.merge(data(true, "", List.of(), true, "", ServiceType.NONE, "", LanguageLayout.STACKED, ""), single).secondaryName());
	}

	@Test
	void exitMatchingIsCaseInsensitiveAndTrimmed() {
		final ResolvedWayfinding lower = WayfindingResolver.merge(data(true, "", List.of(), true, "b", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), facts());
		assertEquals(List.of("Stadium"), lower.exitDestinations());
		assertEquals("b", lower.exitLabel());
		final ResolvedWayfinding unknown = WayfindingResolver.merge(data(true, "", List.of(), true, "Z", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), facts());
		assertTrue(unknown.exitDestinations().isEmpty(), "nothing is invented for an unknown exit");
		final ResolvedWayfinding unset = WayfindingResolver.merge(data(true, "", List.of(), true, "", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), facts());
		assertTrue(unset.exitDestinations().isEmpty());
	}

	@Test
	void serviceTexts() {
		assertEquals("", resolveService(ServiceType.NONE, "x"));
		assertEquals("Local", resolveService(ServiceType.LOCAL, "x"));
		assertEquals("Express", resolveService(ServiceType.EXPRESS, "x"));
		assertEquals("Limited", resolveService(ServiceType.LIMITED, "x"));
		assertEquals("Night owl", resolveService(ServiceType.CUSTOM, "Night owl"));
		assertEquals("", resolveService(ServiceType.CUSTOM, ""));
	}

	private static String resolveService(ServiceType type, String label) {
		return WayfindingResolver.merge(data(true, "", List.of(), true, "", type, label, LanguageLayout.SINGLE, ""), StationFacts.EMPTY).serviceText();
	}

	@Test
	void manualOnlyFieldsPassThroughAndResultIsDeterministic() {
		final WayfindingData d = data(true, "", List.of(), true, "A", ServiceType.EXPRESS, "", LanguageLayout.STACKED, "");
		final ResolvedWayfinding a = WayfindingResolver.merge(d, facts());
		assertEquals(a, WayfindingResolver.merge(d, facts()));
		assertEquals("To East", a.destination());
		assertEquals("2", a.platform());
		assertEquals("Market St", a.streetLabel());
		assertEquals("Bus 12", a.transfers());
		assertEquals(SignArrow.LEFT, a.arrow());
	}

	@Test
	void missingStationLeavesNameEmpty() {
		final ResolvedWayfinding r = WayfindingResolver.merge(data(true, "", List.of(), true, "", ServiceType.NONE, "", LanguageLayout.SINGLE, ""), StationFacts.EMPTY);
		assertEquals("", r.stationName());
		assertFalse(r.stationFromMtr());
	}
}
