package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.cache.HeldServices;
import com.aureliatransit.architecture.live.cache.ServerClockOffset;
import com.aureliatransit.architecture.live.cache.ServiceOrder;
import com.aureliatransit.architecture.live.cache.SnapshotVersions;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.aureliatransit.architecture.live.TestData.NOW;
import static com.aureliatransit.architecture.live.TestData.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression cover for PIDS flicker: identical timetable data must produce an identical snapshot (same instance, same
 * version, same row order) across refreshes, even though MTR returns arrivals in varying order and re-measures its clock
 * offset with jitter on every response.
 */
class BoardStabilityTest {

	private static final StationReference STATION = new StationReference(1, "Central", 0);
	private static final List<PlatformReference> PLATFORMS = List.of(new PlatformReference(10, "1", 1), new PlatformReference(11, "2", 1));

	/** Server clock is 5 s ahead of the client. */
	private static final long TRUE_OFFSET = 5_000;

	/** One MTR arrival as the server reports it: absolute server-clock millis. */
	private record Raw(long platformId, long routeId, String destination, long serverArrival, long serverDeparture) {
	}

	/**
	 * Two routes on two platforms due in the same second (synchronised timetable), one due 0.2 s before a second
	 * boundary, plus ordinary later services.
	 */
	private static List<Raw> timetable() {
		final long base = NOW + TRUE_OFFSET + 120_000;
		return List.of(
				new Raw(10, 1, "North", base, base + 30_000),
				new Raw(11, 2, "South", base, base + 30_000),
				new Raw(10, 3, "Airport", base + 60_800, base + 90_800),
				new Raw(11, 4, "Harbour", base + 61_000, base + 91_000),
				new Raw(10, 1, "North", base + 300_000, base + 330_000));
	}

	private static ServiceSnapshot toService(Raw raw, long arrival, long departure) {
		return new ServiceSnapshot(raw.routeId, "Line " + raw.routeId, "L" + raw.routeId, 0x3355AA, raw.destination, raw.platformId,
				String.valueOf(raw.platformId), arrival, departure, 0, false, false, List.of());
	}

	/** The 1.1.0 conversion: raw jittering offset, truncating division, sort by arrival only (stable, so MTR order wins ties). */
	private static List<ServiceSnapshot> oldPipeline(List<Raw> response, long offsetSample) {
		final List<ServiceSnapshot> out = new ArrayList<>();
		for (final Raw raw : response) {
			out.add(toService(raw, (raw.serverArrival - offsetSample) / 1000 * 1000, (raw.serverDeparture - offsetSample) / 1000 * 1000));
		}
		out.sort(Comparator.comparingLong(ServiceSnapshot::arrivalMillis));
		return out;
	}

	private static List<ServiceSnapshot> newPipeline(List<Raw> response, ServerClockOffset offset, long offsetSample) {
		final long held = offset.stabilize(offsetSample);
		final List<ServiceSnapshot> out = new ArrayList<>();
		for (final Raw raw : response) {
			out.add(toService(raw, ServerClockOffset.toLocalSecond(raw.serverArrival, held), ServerClockOffset.toLocalSecond(raw.serverDeparture, held)));
		}
		out.sort(ServiceOrder.COMPARATOR);
		return out;
	}

	/** Offset samples as MTR measures them: true offset plus up to +-300 ms of network/tick jitter. */
	private static long jitteredOffset(Random random) {
		return TRUE_OFFSET + random.nextInt(601) - 300;
	}

	@Test
	void oldPipelineChangesContentWithoutAnyTimetableChange() {
		// Reproduces the defect: same timetable, 50 refreshes, content keeps changing.
		final Random random = new Random(42);
		final SnapshotVersions versions = new SnapshotVersions();
		final Set<Long> seenVersions = new HashSet<>();
		final Set<List<Long>> seenRouteOrders = new HashSet<>();
		StationSnapshot previous = null;
		for (int i = 0; i < 50; i++) {
			final List<Raw> response = new ArrayList<>(timetable());
			Collections.shuffle(response, random);
			previous = versions.stabilize(previous, STATION, PLATFORMS, oldPipeline(response, jitteredOffset(random)));
			seenVersions.add(previous.version());
			seenRouteOrders.add(previous.services().stream().map(ServiceSnapshot::routeId).toList());
		}
		assertTrue(seenVersions.size() > 1, "old pipeline was expected to churn versions");
		assertTrue(seenRouteOrders.size() > 1, "old pipeline was expected to swap rows");
	}

	@Test
	void identicalTimetableGivesTheSameSnapshotInstanceAcrossRefreshes() {
		final Random random = new Random(42);
		final SnapshotVersions versions = new SnapshotVersions();
		final ServerClockOffset offset = new ServerClockOffset();
		StationSnapshot first = null;
		StationSnapshot previous = null;
		for (int i = 0; i < 500; i++) {
			final List<Raw> response = new ArrayList<>(timetable());
			Collections.shuffle(response, random);
			previous = versions.stabilize(previous, STATION, PLATFORMS, newPipeline(response, offset, jitteredOffset(random)));
			if (first == null) {
				first = previous;
			}
			assertSame(first, previous, "refresh " + i + " changed the snapshot");
		}
		assertEquals(List.of(1L, 2L, 3L, 4L, 1L), first.services().stream().map(ServiceSnapshot::routeId).toList());
	}

	@Test
	void realTimetableChangesStillComeThrough() {
		final SnapshotVersions versions = new SnapshotVersions();
		final ServerClockOffset offset = new ServerClockOffset();
		final StationSnapshot before = versions.stabilize(null, STATION, PLATFORMS, newPipeline(timetable(), offset, TRUE_OFFSET));
		final List<Raw> delayed = new ArrayList<>(timetable());
		final Raw late = delayed.remove(0);
		delayed.add(new Raw(late.platformId, late.routeId, late.destination, late.serverArrival + 45_000, late.serverDeparture + 45_000));
		final StationSnapshot after = versions.stabilize(before, STATION, PLATFORMS, newPipeline(delayed, offset, TRUE_OFFSET + 150));
		assertTrue(after.version() != before.version());
		assertEquals(2L, after.services().get(0).routeId());
	}

	@Test
	void orderIsTotalForServicesDueTogether() {
		final List<ServiceSnapshot> services = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			services.add(service(10 + i % 3, i, NOW + 60_000));
		}
		final List<ServiceSnapshot> reference = new ArrayList<>(services);
		reference.sort(ServiceOrder.COMPARATOR);
		final Random random = new Random(7);
		for (int i = 0; i < 100; i++) {
			Collections.shuffle(services, random);
			services.sort(ServiceOrder.COMPARATOR);
			assertEquals(reference, services);
		}
	}

	@Test
	void clockOffsetIgnoresJitterButFollowsRealChanges() {
		final ServerClockOffset offset = new ServerClockOffset();
		assertEquals(5_000, offset.stabilize(5_000));
		assertEquals(5_000, offset.stabilize(5_900));
		assertEquals(5_000, offset.stabilize(4_100));
		assertEquals(7_500, offset.stabilize(7_500), "a real clock step is adopted");
		assertEquals(-2_000, ServerClockOffset.toLocalSecond(-1_500, 0), "rounds down for negative values too");
	}

	@Test
	void briefEmptyRefreshKeepsValidServices() {
		final List<ServiceSnapshot> live = List.of(service(10, 1, NOW + 60_000), service(10, 2, NOW + 5_000, NOW + 8_000, false, 0));
		final HeldServices full = HeldServices.next(null, live, NOW);
		assertEquals(live, full.services());
		final HeldServices gap = HeldServices.next(full, List.of(), NOW + 3_000);
		assertSame(full, gap, "nothing departed yet: identical instance, no rebuild");
		final HeldServices later = HeldServices.next(gap, List.of(), NOW + 9_000);
		assertEquals(List.of(live.get(0)), later.services(), "a departed service is not held");
		assertTrue(HeldServices.next(later, List.of(), NOW + HeldServices.GRACE_MILLIS + 1).services().isEmpty(), "grace expires");
		final List<ServiceSnapshot> fresh = List.of(service(11, 3, NOW + 70_000));
		assertEquals(fresh, HeldServices.next(later, fresh, NOW + 9_500).services(), "new data replaces held data");
	}

	@Test
	void emptyWithoutHistoryStaysEmpty() {
		assertTrue(HeldServices.next(null, List.of(), NOW).services().isEmpty());
		final HeldServices empty = HeldServices.next(null, List.of(), NOW);
		assertTrue(HeldServices.next(empty, List.of(), NOW + 1_000).services().isEmpty());
	}
}
