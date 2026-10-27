package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The chunks a survey or build holds loaded, least recently used first, never more than {@link #cap()}. Holding a chunk past
 * the cap lets go of the one used longest ago, so a site of any size keeps a bounded window loaded while it is walked. Chunks
 * are keys as {@code ChunkPos.toLong}. Each held chunk remembers the tick its ticket was last added, so a chunk held for longer than
 * the ticket lasts can be renewed ({@link #due}).
 */
public final class ChunkHold {

	/** Default window: 32 × 32 chunks, more than any station's footprint, so a station never lets go of a chunk mid-build. */
	public static final int DEFAULT_CAP = 1024;

	private final int cap;
	/** Held chunks, least recently used first, each with the tick its ticket was last added. */
	private final LinkedHashMap<Long, Long> held = new LinkedHashMap<>();

	public ChunkHold(int cap) {
		if (cap < 1) {
			throw new IllegalArgumentException("cap must be at least 1");
		}
		this.cap = cap;
	}

	public int cap() {
		return cap;
	}

	public int size() {
		return held.size();
	}

	public boolean holds(long chunk) {
		return held.containsKey(chunk);
	}

	/** True when the chunk is not held, or its ticket was last added {@code every} ticks or more before {@code now}. */
	public boolean due(long chunk, long now, long every) {
		final Long at = held.get(chunk);
		// MIN_VALUE: held but never ticketed
		return at == null || at == Long.MIN_VALUE || now - at >= every;
	}

	/** Records that the chunk's ticket was added at {@code now}; the chunk must be held (call after {@link #use}). */
	public void ticketed(long chunk, long now) {
		held.replace(chunk, now);
	}

	/**
	 * Marks the chunk as just used. Returns the chunk let go of to stay within the cap, or null when none was; a chunk already
	 * held only moves to the most recent end.
	 */
	public Long use(long chunk) {
		final Long at = held.remove(chunk);
		if (at != null) {
			held.put(chunk, at);
			return null;
		}
		held.put(chunk, Long.MIN_VALUE);
		if (held.size() <= cap) {
			return null;
		}
		final Iterator<Long> oldest = held.keySet().iterator();
		final Long gone = oldest.next();
		oldest.remove();
		return gone;
	}

	/** Lets go of every chunk and returns them, oldest first. */
	public List<Long> clear() {
		final List<Long> all = new ArrayList<>(held.keySet());
		held.clear();
		return all;
	}
}
