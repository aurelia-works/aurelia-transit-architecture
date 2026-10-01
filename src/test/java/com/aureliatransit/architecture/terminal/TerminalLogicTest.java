package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.client.wayfinding.logic.ClientServiceMessages;
import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.wayfinding.BadgeShape;
import com.aureliatransit.architecture.wayfinding.ExitInfo;
import com.aureliatransit.architecture.wayfinding.LanguageLayout;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.wayfinding.MessageScope;
import com.aureliatransit.architecture.wayfinding.MessageSeverity;
import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.ServiceMessage;
import com.aureliatransit.architecture.wayfinding.ServiceMessages;
import com.aureliatransit.architecture.wayfinding.ServiceType;
import com.aureliatransit.architecture.wayfinding.StationFacts;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.terminal.SystemMapBuilder.RawRoute;
import com.aureliatransit.architecture.terminal.SystemMapBuilder.RawStop;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalLogicTest {

	private static LineBadge badge(String label, int rgb) {
		return new LineBadge(label, rgb, BadgeShape.ROUNDED);
	}

	private static RawRoute route(long id, String label, int rgb, long... stations) {
		final List<RawStop> stops = new ArrayList<>();
		for (final long s : stations) {
			stops.add(new RawStop(s, "S" + s));
		}
		return new RawRoute(id, badge(label, rgb), label + " line", stops);
	}

	private static List<RawRoute> sample() {
		return List.of(
				route(1, "A", 0xFF0000, 1, 2, 2, 3, 4),
				route(2, "A", 0xFF0000, 4, 3, 2),
				route(3, "B", 0x00FF00, 10, 3, 11),
				route(4, "C", 0x0000FF, 20, 21, 22),
				route(5, "2", 0x123456, 30, 31));
	}

	@Test
	void mapDedupesLinesKeepsMostCompleteAndFlagsStops() {
		final SystemMap map = SystemMapBuilder.build(sample(), 3);
		assertEquals(4, map.lines().size());
		final MapLine a = map.lines().stream().filter(l -> l.badge().label().equals("A")).findFirst().orElseThrow();
		assertEquals(1, a.id(), "longest variant wins");
		assertEquals(List.of(1L, 2L, 3L, 4L), a.stops().stream().map(MapStop::stationId).toList(), "consecutive duplicate removed");
		assertTrue(a.stops().get(2).current());
		assertTrue(a.stops().get(2).transfer(), "station 3 is on A and B");
		assertFalse(a.stops().get(0).transfer());
		assertFalse(a.stops().get(0).current());
	}

	@Test
	void linesServingTheCurrentStationComeFirstThenDeterministicOrder() {
		final SystemMap map = SystemMapBuilder.build(sample(), 3);
		assertEquals(List.of("A", "B", "2", "C"), map.lines().stream().map(l -> l.badge().label()).toList());
		final SystemMap other = SystemMapBuilder.build(sample(), 31);
		assertEquals("2", other.lines().get(0).badge().label());
		assertEquals(List.of("2", "A", "B", "C"), other.lines().stream().map(l -> l.badge().label()).toList());
	}

	@Test
	void shuffledInputGivesTheIdenticalMap() {
		final SystemMap expected = SystemMapBuilder.build(sample(), 3);
		final List<RawRoute> shuffled = new ArrayList<>(sample());
		for (int i = 0; i < 10; i++) {
			Collections.shuffle(shuffled, new Random(i));
			assertEquals(expected, SystemMapBuilder.build(shuffled, 3));
		}
	}

	@Test
	void invalidStopsAreDroppedAndNothingIsInvented() {
		final SystemMap map = SystemMapBuilder.build(List.of(new RawRoute(1, badge("X", 1), "x", List.of(new RawStop(0, "Zero"), new RawStop(-4, "Neg"), new RawStop(5, " "),
				new RawStop(6, "Six")))), 0);
		assertEquals(1, map.lines().get(0).stops().size());
		assertEquals("Six", map.lines().get(0).stops().get(0).name());
		assertFalse(map.lines().get(0).stops().get(0).current(), "no current station");
		assertTrue(SystemMapBuilder.build(List.of(), 5).lines().isEmpty());
		assertEquals(SystemMap.EMPTY, SystemMapBuilder.build(List.of(), 0));
	}

	@Test
	void mapIsBounded() {
		final List<RawRoute> many = new ArrayList<>();
		for (int i = 0; i < SystemMap.MAX_LINES + 10; i++) {
			final long[] stations = new long[SystemMap.MAX_STOPS + 20];
			for (int j = 0; j < stations.length; j++) {
				stations[j] = 100L * (i + 1) + j + 1;
			}
			many.add(route(i, String.valueOf(i + 1), i + 1, stations));
		}
		final SystemMap map = SystemMapBuilder.build(many, 0);
		assertEquals(SystemMap.MAX_LINES, map.lines().size());
		assertTrue(map.lines().stream().allMatch(l -> l.stops().size() == SystemMap.MAX_STOPS));
	}

	private static WayfindingData data(boolean auto, String name, String code, String transfers, String street) {
		return new WayfindingData(auto, name, "", code, List.of(), auto, SignArrow.NONE, "", ServiceType.NONE, "", "", "", street, transfers, LanguageLayout.SINGLE, Pictogram.NONE,
				AccentPalette.NONE, com.aureliatransit.architecture.transit.StationAssociation.AUTO, com.aureliatransit.architecture.wayfinding.BoardView.TRAINS_THIS_SIDE);
	}

	@Test
	void stationInfoWithoutDataIsEmpty() {
		assertEquals(StationInfo.EMPTY, StationInfoBuilder.build(StationFacts.EMPTY, List.of(), data(true, "", "", "", ""), List.of()));
		assertEquals(StationInfo.EMPTY, StationInfoBuilder.build(StationFacts.EMPTY, List.of(), WayfindingData.EMPTY, List.of()));
	}

	@Test
	void stationInfoMergesMtrFactsWithManualMetadata() {
		final LineBadge l = badge("L", 0x0066CC);
		final StationFacts facts = new StationFacts(new StationReference(7, "Market Street", 0), List.of(l), List.of(new ExitInfo("A", List.of("City Hall"))));
		final List<PlatformReference> platforms = List.of(new PlatformReference(1, "1", 7), new PlatformReference(2, "2", 7));
		final AccessibilityNote lift = new AccessibilityNote(Pictogram.ELEVATOR, "Lift to street");
		final StationInfo auto = StationInfoBuilder.build(facts, platforms, data(true, "", "MKT", "Bus 12", "Market St"), List.of(lift));
		assertEquals("Market Street", auto.stationName());
		assertEquals("MKT", auto.stationCode());
		assertEquals("Bus 12", auto.transfers());
		assertEquals("Market St", auto.streetLabel());
		assertEquals(List.of(l), auto.lines());
		assertEquals(platforms, auto.platforms());
		assertEquals(List.of("City Hall"), auto.exits().get(0).destinations());
		assertEquals(List.of(lift), auto.accessibility());
		assertEquals("My Stop", StationInfoBuilder.build(facts, platforms, data(true, "My Stop", "", "", ""), List.of()).stationName(), "manual name wins");
	}

	private static ServiceSnapshot service(long departure) {
		return new ServiceSnapshot(1, "R", "1", 0, "Dest", 1, "1", departure - 30_000, departure, 0, false, false, List.of());
	}

	@Test
	void departuresFilterDepartedAndKeepOrderAndBound() {
		final long now = 1_000_000;
		final StationSnapshot snapshot = new StationSnapshot(null, List.of(), List.of(service(now - 1), service(now), service(now + 5), service(now + 9), service(now + 12)), 1);
		final List<ServiceSnapshot> shown = TerminalDepartures.select(snapshot, now, 3);
		assertEquals(List.of(now, now + 5, now + 9), shown.stream().map(ServiceSnapshot::departureMillis).toList(), "departure == now still shown, like the PIDS");
		assertTrue(TerminalDepartures.select(StationSnapshot.EMPTY, now, 5).isEmpty());
		assertTrue(TerminalDepartures.select(snapshot, now, 0).isEmpty());
	}

	@Test
	void serviceMessageListPriorityIsStationThenNetwork() {
		ClientServiceMessages.set(ServiceMessages.EMPTY.add(MessageScope.NETWORK, "", MessageSeverity.INFO, "net")
				.add(MessageScope.STATION, "Market Street", MessageSeverity.WARNING, "stn"));
		try {
			assertEquals(List.of("stn", "net"), ClientServiceMessages.allFor("market street").stream().map(ServiceMessage::text).toList());
			assertEquals(List.of("net"), ClientServiceMessages.allFor("Elsewhere").stream().map(ServiceMessage::text).toList());
			assertEquals(List.of("mine", "stn", "net"), ClientServiceMessages.applicable("mine", "Market Street").stream().map(ServiceMessage::text).toList());
			ClientServiceMessages.clear();
			assertTrue(ClientServiceMessages.allFor("Market Street").isEmpty());
		} finally {
			ClientServiceMessages.clear();
		}
	}
}
