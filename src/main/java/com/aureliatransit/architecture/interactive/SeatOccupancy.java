package com.aureliatransit.architecture.interactive;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pure bookkeeping of which seat slot is held by which seat entity. Server thread only. Entries whose entity is no
 * longer alive are treated as free and cleaned up lazily, so a missed release can never lock a seat forever.
 *
 * @param <K> slot key (dimension, block position, slot index)
 */
public final class SeatOccupancy<K> {

	private final Map<K, UUID> holders = new HashMap<>();

	/**
	 * @param alive tells whether a seat entity with the given id still exists and is alive
	 */
	public boolean isOccupied(K key, Predicate<UUID> alive) {
		final UUID holder = holders.get(key);
		if (holder == null) {
			return false;
		}
		if (alive.test(holder)) {
			return true;
		}
		holders.remove(key);
		return false;
	}

	/**
	 * Claims a free slot for a seat entity. Returns false when another live entity holds it.
	 */
	public boolean claim(K key, UUID seat, Predicate<UUID> alive) {
		if (isOccupied(key, alive)) {
			return false;
		}
		holders.put(key, seat);
		return true;
	}

	/**
	 * Releases a slot, but only if the given entity is the one holding it.
	 */
	public void release(K key, UUID seat) {
		holders.remove(key, seat);
	}

	public void clear() {
		holders.clear();
	}

	public int size() {
		return holders.size();
	}
}
