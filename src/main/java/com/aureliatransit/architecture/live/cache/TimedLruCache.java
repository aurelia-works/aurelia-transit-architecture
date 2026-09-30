package com.aureliatransit.architecture.live.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Bounded, time-aware cache for values that are refreshed lazily and only while somebody keeps asking for them.
 *
 * <ul>
 *     <li>{@link #get} returns the cached value while it is younger than {@code refreshMillis}; otherwise it calls the
 *     refresher (which receives the previous value, or null) and stores the result.</li>
 *     <li>Least-recently-used entries are dropped beyond {@code maxEntries}; entries not requested for
 *     {@code evictAfterMillis} are swept out (amortised: at most once per quarter of that period).</li>
 * </ul>
 * Not thread-safe: use from the client thread only. Pure logic, no Minecraft classes.
 */
public final class TimedLruCache<K, V> {

	private static final class Entry<V> {
		V value;
		long refreshedAt;
		long lastUsed;
	}

	private final int maxEntries;
	private final long refreshMillis;
	private final long evictAfterMillis;
	private final LongSupplier clock;
	private final LinkedHashMap<K, Entry<V>> map = new LinkedHashMap<>(64, 0.75F, true);
	private long lastSweep;
	private long refreshes;
	private long hits;
	private long evictions;

	public TimedLruCache(int maxEntries, long refreshMillis, long evictAfterMillis, LongSupplier clock) {
		if (maxEntries < 1) {
			throw new IllegalArgumentException("maxEntries");
		}
		this.maxEntries = maxEntries;
		this.refreshMillis = refreshMillis;
		this.evictAfterMillis = evictAfterMillis;
		this.clock = clock;
		this.lastSweep = clock.getAsLong();
	}

	public V get(K key, BiFunction<K, V, V> refresher) {
		final long now = clock.getAsLong();
		sweepIfDue(now);
		Entry<V> entry = map.get(key);
		if (entry != null && now - entry.refreshedAt < refreshMillis) {
			entry.lastUsed = now;
			hits++;
			return entry.value;
		}
		final V previous = entry == null ? null : entry.value;
		final V fresh = refresher.apply(key, previous);
		refreshes++;
		if (entry == null) {
			entry = new Entry<>();
			map.put(key, entry);
			while (map.size() > maxEntries) {
				final Iterator<Map.Entry<K, Entry<V>>> it = map.entrySet().iterator();
				it.next();
				it.remove();
				evictions++;
			}
		}
		entry.value = fresh;
		entry.refreshedAt = now;
		entry.lastUsed = now;
		return fresh;
	}

	/**
	 * Removes entries not requested for {@code evictAfterMillis}. Called automatically from {@link #get}.
	 */
	public void sweep() {
		sweep(clock.getAsLong());
	}

	private void sweepIfDue(long now) {
		if (now - lastSweep >= Math.max(1, evictAfterMillis / 4)) {
			sweep(now);
		}
	}

	private void sweep(long now) {
		lastSweep = now;
		final Iterator<Map.Entry<K, Entry<V>>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			if (now - it.next().getValue().lastUsed >= evictAfterMillis) {
				it.remove();
				evictions++;
			}
		}
	}

	public void clear() {
		map.clear();
	}

	public int size() {
		return map.size();
	}

	public long refreshCount() {
		return refreshes;
	}

	public long hitCount() {
		return hits;
	}

	public long evictionCount() {
		return evictions;
	}
}
