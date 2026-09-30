package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.cache.SnapshotVersions;
import com.aureliatransit.architecture.live.cache.TimedLruCache;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.aureliatransit.architecture.live.TestData.NOW;
import static com.aureliatransit.architecture.live.TestData.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCacheTest {

	@Test
	void valueIsReusedUntilTheRefreshIntervalPasses() {
		final AtomicLong clock = new AtomicLong(0);
		final AtomicInteger loads = new AtomicInteger();
		final TimedLruCache<String, Integer> cache = new TimedLruCache<>(8, 2_000, 30_000, clock::get);
		assertEquals(1, cache.get("a", (k, old) -> loads.incrementAndGet()));
		clock.set(1_999);
		assertEquals(1, cache.get("a", (k, old) -> loads.incrementAndGet()));
		clock.set(2_000);
		assertEquals(2, cache.get("a", (k, old) -> loads.incrementAndGet()));
		assertEquals(2, loads.get());
	}

	@Test
	void refresherReceivesThePreviousValue() {
		final AtomicLong clock = new AtomicLong(0);
		final TimedLruCache<String, Integer> cache = new TimedLruCache<>(8, 1_000, 30_000, clock::get);
		assertNull(seen(cache, "a"));
		clock.set(1_000);
		assertEquals(10, seen(cache, "a"));
	}

	private static Integer seen(TimedLruCache<String, Integer> cache, String key) {
		final Integer[] previous = new Integer[1];
		cache.get(key, (k, old) -> {
			previous[0] = old;
			return 10;
		});
		return previous[0];
	}

	@Test
	void sizeIsBoundedAndLeastRecentlyUsedGoesFirst() {
		final AtomicLong clock = new AtomicLong(0);
		final TimedLruCache<Integer, Integer> cache = new TimedLruCache<>(3, 10_000, 100_000, clock::get);
		for (int i = 0; i < 3; i++) {
			cache.get(i, (k, old) -> k);
		}
		cache.get(0, (k, old) -> -1); // touch 0 so 1 is the eldest
		cache.get(3, (k, old) -> 3);
		assertEquals(3, cache.size());
		assertEquals(1, cache.evictionCount());
		final AtomicInteger loads = new AtomicInteger();
		cache.get(0, (k, old) -> loads.incrementAndGet());
		assertEquals(0, loads.get(), "0 survived");
		cache.get(1, (k, old) -> loads.incrementAndGet());
		assertEquals(1, loads.get(), "1 was evicted");
		for (int i = 0; i < 1000; i++) {
			cache.get(100 + i, (k, old) -> k);
		}
		assertTrue(cache.size() <= 3);
	}

	@Test
	void unusedEntriesAreEvictedAfterTheIdleTime() {
		final AtomicLong clock = new AtomicLong(0);
		final TimedLruCache<String, Integer> cache = new TimedLruCache<>(8, 1_000, 10_000, clock::get);
		cache.get("a", (k, old) -> 1);
		cache.get("b", (k, old) -> 2);
		clock.set(9_000);
		cache.get("b", (k, old) -> 2);
		clock.set(11_000);
		cache.sweep();
		assertEquals(1, cache.size());
	}

	@Test
	void versionChangesOnlyWhenContentChanges() {
		final SnapshotVersions versions = new SnapshotVersions();
		final StationReference station = new StationReference(1, "Central", 0);
		final List<PlatformReference> platforms = List.of(new PlatformReference(10, "1", 1));
		final StationSnapshot first = versions.stabilize(null, station, platforms, List.of(service(10, 1, NOW + 60_000)));
		assertNotEquals(0, first.version());
		final StationSnapshot same = versions.stabilize(first, station, platforms, List.of(service(10, 1, NOW + 60_000)));
		assertSame(first, same);
		final StationSnapshot changed = versions.stabilize(same, station, platforms, List.of(service(10, 1, NOW + 90_000)));
		assertNotEquals(first.version(), changed.version());
		final StationSnapshot empty = versions.stabilize(changed, null, List.of(), List.<ServiceSnapshot>of());
		assertSame(StationSnapshot.EMPTY, empty);
	}

	@Test
	void servicesAreBoundedBySnapshot() {
		final SnapshotVersions versions = new SnapshotVersions();
		final List<ServiceSnapshot> many = new java.util.ArrayList<>();
		for (int i = 0; i < 50; i++) {
			many.add(service(10, i, NOW + i * 1000L));
		}
		assertEquals(StationSnapshot.MAX_SERVICES, versions.stabilize(null, new StationReference(1, "C", 0), List.of(), many).services().size());
	}
}
