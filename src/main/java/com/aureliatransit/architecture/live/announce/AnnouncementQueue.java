package com.aureliatransit.architecture.live.announce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded priority queue of pending announcements.
 *
 * <ul>
 *     <li><b>Ordering:</b> category priority (safety, delay, terminating, approaching, standing), then creation time.</li>
 *     <li><b>De-duplication:</b> an event whose {@link AnnouncementEvent#dedupKey()} was offered within
 *     {@code dedupWindowMillis} is rejected, whether it is still queued or already played.</li>
 *     <li><b>Station cooldown:</b> after an announcement of a station is played, non-safety events of that station wait
 *     {@code stationCooldownMillis}. Other stations are not blocked.</li>
 *     <li><b>Bounds:</b> at most {@code capacity} queued events (a full queue drops the least important), bounded recent-key
 *     and cooldown memory. Events past {@code expiresMillis} are discarded, never played late.</li>
 * </ul>
 */
public final class AnnouncementQueue {

	private final int capacity;
	private final long dedupWindowMillis;
	private final int recentCapacity;
	private final long stationCooldownMillis;
	private final List<AnnouncementEvent> queue = new ArrayList<>();
	private final Map<AnnouncementEvent.DedupKey, Long> recent;
	private final Map<Long, Long> cooldownUntil = new HashMap<>();
	private long dropped;

	public AnnouncementQueue(int capacity, long dedupWindowMillis, int recentCapacity, long stationCooldownMillis) {
		this.capacity = capacity;
		this.dedupWindowMillis = dedupWindowMillis;
		this.recentCapacity = recentCapacity;
		this.stationCooldownMillis = stationCooldownMillis;
		this.recent = new LinkedHashMap<>(64, 0.75F, false) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<AnnouncementEvent.DedupKey, Long> eldest) {
				return size() > AnnouncementQueue.this.recentCapacity;
			}
		};
	}

	/**
	 * @return true when the event was queued
	 */
	public boolean offer(AnnouncementEvent event, long now) {
		final Long seen = recent.get(event.dedupKey());
		if (seen != null && now - seen < dedupWindowMillis) {
			dropped++;
			return false;
		}
		int index = 0;
		while (index < queue.size() && compare(queue.get(index), event) <= 0) {
			index++;
		}
		if (queue.size() >= capacity) {
			if (index >= queue.size()) {
				dropped++;
				return false;
			}
			queue.remove(queue.size() - 1);
			dropped++;
		}
		queue.add(index, event);
		recent.put(event.dedupKey(), now);
		return true;
	}

	/**
	 * Removes and returns the most important playable event, or null.
	 */
	public AnnouncementEvent poll(long now) {
		final Iterator<AnnouncementEvent> it = queue.iterator();
		while (it.hasNext()) {
			final AnnouncementEvent event = it.next();
			if (now >= event.expiresMillis()) {
				it.remove();
				dropped++;
				continue;
			}
			if (event.category() != AnnouncementCategory.SAFETY && now < cooldownUntil.getOrDefault(event.stationKey(), Long.MIN_VALUE)) {
				continue;
			}
			it.remove();
			return event;
		}
		return null;
	}

	/**
	 * Records that an event was actually played, starting its station's cooldown.
	 */
	public void markPlayed(AnnouncementEvent event, long now) {
		if (cooldownUntil.size() > 64) {
			cooldownUntil.values().removeIf(until -> until <= now);
		}
		cooldownUntil.put(event.stationKey(), now + stationCooldownMillis);
	}

	public void clear() {
		queue.clear();
		cooldownUntil.clear();
		recent.clear();
	}

	public int size() {
		return queue.size();
	}

	public long droppedCount() {
		return dropped;
	}

	private static int compare(AnnouncementEvent a, AnnouncementEvent b) {
		final int byPriority = Integer.compare(a.category().priority(), b.category().priority());
		return byPriority != 0 ? byPriority : Long.compare(a.createdMillis(), b.createdMillis());
	}
}
