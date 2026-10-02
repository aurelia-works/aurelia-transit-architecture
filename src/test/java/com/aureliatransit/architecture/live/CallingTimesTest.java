package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.display.CallingTimes;
import com.aureliatransit.architecture.live.display.CallingTimes.Arrival;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallingTimesTest {

	private static final long T = 1_000_000_000L;
	private static final long ROUTE = 719;

	@Test
	void sameTripIsMatchedByRouteAndDepartureIndex() {
		// the shape seen live: dep#1 reaches three platforms 20 s apart; dep#2 follows 160 s later
		final List<Arrival> arrivals = List.of(
				new Arrival(ROUTE, 1, 681, T + 32_000), new Arrival(ROUTE, 1, 951, T + 52_000), new Arrival(ROUTE, 1, 658, T + 72_000),
				new Arrival(ROUTE, 2, 681, T + 192_000), new Arrival(ROUTE, 2, 951, T + 212_000), new Arrival(ROUTE, 2, 658, T + 232_000));
		assertEquals(List.of(T + 52_000, T + 72_000), CallingTimes.match(ROUTE, 1, T + 40_000, List.of(951L, 658L), arrivals));
		assertEquals(List.of(T + 212_000, T + 232_000), CallingTimes.match(ROUTE, 2, T + 200_000, List.of(951L, 658L), arrivals));
	}

	@Test
	void noMatchMeansNoTimeAndNothingIsEstimated() {
		final List<Arrival> arrivals = List.of(new Arrival(ROUTE, 1, 951, T + 52_000));
		assertEquals(List.of(T + 52_000, 0L), CallingTimes.match(ROUTE, 1, T, List.of(951L, 658L), arrivals));
		assertEquals(List.of(0L, 0L), CallingTimes.match(ROUTE, 1, T, List.of(951L, 658L), List.of()));
		assertEquals(List.of(0L), CallingTimes.match(ROUTE + 1, 1, T, List.of(951L), arrivals), "another route's train is never used");
		assertEquals(List.of(0L), CallingTimes.match(ROUTE, 2, T, List.of(951L), arrivals), "another trip is never used");
	}

	@Test
	void wrappedIndexTakesTheEarliestArrivalAfterTheDeparture() {
		// departure indices repeat each cycle: the same index appears again much later, and an older arrival is gone
		final List<Arrival> arrivals = List.of(new Arrival(ROUTE, 1, 951, T + 1_272_000), new Arrival(ROUTE, 1, 951, T + 52_000),
				new Arrival(ROUTE, 1, 951, T - 5_000));
		assertEquals(List.of(T + 52_000), CallingTimes.match(ROUTE, 1, T, List.of(951L), arrivals));
		final List<Arrival> far = List.of(new Arrival(ROUTE, 1, 951, T + CallingTimes.MAX_AHEAD_MILLIS + 1));
		assertEquals(List.of(0L), CallingTimes.match(ROUTE, 1, T, List.of(951L), far), "a match beyond the horizon is another cycle");
	}

	@Test
	void timesNeverGoBackwardsAlongTheRoute() {
		// a platform visited twice (loop): the later stop takes the later arrival
		final List<Arrival> arrivals = List.of(new Arrival(ROUTE, 1, 951, T + 50_000), new Arrival(ROUTE, 1, 951, T + 150_000), new Arrival(ROUTE, 1, 658, T + 100_000));
		assertEquals(List.of(T + 50_000, T + 100_000, T + 150_000), CallingTimes.match(ROUTE, 1, T, List.of(951L, 658L, 951L), arrivals));
	}

	@Test
	void labelsShowMinutesOnlyWhereKnown() {
		assertEquals(List.of("Beta (1 min)", "Gamma (3 min)", "Delta"), CallingTimes.labels(List.of("Beta", "Gamma", "Delta"), List.of(T + 20_000, T + 180_000, 0L), T));
		assertEquals(List.of("Beta"), CallingTimes.labels(List.of("Beta"), List.of(T - 1), T), "a time already passed is not shown");
		assertEquals(List.of("Beta", "Gamma"), CallingTimes.labels(List.of("Beta", "Gamma"), List.of(), T));
	}
}
