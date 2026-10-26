package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The chunks a survey or build holds loaded, least recently used first, never more than {@link #cap()}. Holding a chunk past
 * the cap lets go of the one used longest ago, so a site of any size keeps a bounded window loaded while it is walked. Chunks
 * are keys as {@code ChunkPos.toLong}.
 */
public final class ChunkHold {

	/** Default window: 32 × 32 chunks, more than any station's footprint, so a station never lets go of a chunk mid-build. */
	public static final int DEFAULT_CAP = 1024;

	private final int cap;
	private final LinkedHashSet<Long> held = new LinkedHashSet<>();

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
		return held.contains(chunk);
	}

	/**
	 * Marks the chunk as just used. Returns the chunk let go of to stay within the cap, or null when none was; a chunk already
	 * held only moves to the most recent end.
	 */
	public Long use(long chunk) {
		if (held.remove(chunk)) {
			held.add(chunk);
			return null;
		}
		held.add(chunk);
		if (held.size() <= cap) {
			return null;
		}
		final Iterator<Long> oldest = held.iterator();
		final Long gone = oldest.next();
		oldest.remove();
		return gone;
	}

	/** Lets go of every chunk and returns them, oldest first. */
	public List<Long> clear() {
		final List<Long> all = new ArrayList<>(held);
		held.clear();
		return all;
	}
}
