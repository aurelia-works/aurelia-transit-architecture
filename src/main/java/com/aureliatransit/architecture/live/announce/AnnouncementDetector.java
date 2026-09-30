package com.aureliatransit.architecture.live.announce;

import com.aureliatransit.architecture.transit.ServiceSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns successive service lists of a logical station into announcement events.
 *
 * <p>Train visits are tracked per (station, platform, route): a service in a new update is matched to the closest
 * tracked visit whose arrival differs by at most {@link Config#matchMillis}, so realtime corrections do not look like
 * new trains. Each visit fires each event kind at most once (delay again only if it worsened by
 * {@link Config#delayRepeatMillis}). Tracking is bounded and stale visits are dropped.
 * Pure logic; called from the client thread only.
 */
public final class AnnouncementDetector {

	public record Config(long approachMillis, long safetyMillis, long delayThresholdMillis, long delayRepeatMillis, long matchMillis, long staleMillis, int maxTracked) {
		public static final Config DEFAULT = new Config(40_000, 12_000, 120_000, 180_000, 120_000, 90_000, 256);
	}

	private static final class Tracked {
		final long id;
		long arrival;
		long departure;
		long lastSeen;
		boolean safety;
		boolean approach;
		boolean standing;
		boolean terminating;
		long lastDelayAnnounced = Long.MIN_VALUE;

		Tracked(long id) {
			this.id = id;
		}
	}

	private record GroupKey(long stationKey, long platformId, long routeId) {
	}

	private final Config config;
	private final Map<GroupKey, List<Tracked>> groups = new HashMap<>();
	private long nextId = 1;
	private int trackedCount;

	public AnnouncementDetector() {
		this(Config.DEFAULT);
	}

	public AnnouncementDetector(Config config) {
		this.config = config;
	}

	public int trackedCount() {
		return trackedCount;
	}

	public void clear() {
		groups.clear();
		trackedCount = 0;
	}

	/**
	 * @param services the upcoming services for this station (any order; duplicates of the same train are merged)
	 */
	public List<AnnouncementEvent> update(long stationKey, String stationName, Collection<ServiceSnapshot> services, long now) {
		final List<AnnouncementEvent> events = new ArrayList<>(2);
		final Set<Tracked> matchedThisUpdate = new HashSet<>();
		for (final ServiceSnapshot service : services) {
			if (service.departureMillis() < now - 2_000) {
				continue;
			}
			final List<Tracked> list = groups.computeIfAbsent(new GroupKey(stationKey, service.platformId(), service.routeId()), k -> new ArrayList<>(2));
			Tracked tracked = null;
			long best = config.matchMillis() + 1;
			for (final Tracked candidate : list) {
				final long diff = Math.abs(candidate.arrival - service.arrivalMillis());
				if (diff < best && !matchedThisUpdate.contains(candidate)) {
					best = diff;
					tracked = candidate;
				}
			}
			if (tracked == null) {
				if (trackedCount >= config.maxTracked()) {
					dropOldest();
				}
				tracked = new Tracked(nextId++);
				list.add(tracked);
				trackedCount++;
			}
			matchedThisUpdate.add(tracked);
			tracked.arrival = service.arrivalMillis();
			tracked.departure = service.departureMillis();
			tracked.lastSeen = now;
			detect(stationKey, stationName, service, tracked, now, events);
		}
		purge(now);
		return events;
	}

	private void detect(long stationKey, String stationName, ServiceSnapshot service, Tracked tracked, long now, List<AnnouncementEvent> out) {
		final long until = service.arrivalMillis() - now;
		final boolean notArrived = until > 0;
		final boolean standing = !notArrived && now < service.departureMillis();

		if (notArrived && until <= config.safetyMillis() && !tracked.safety) {
			tracked.safety = true;
			out.add(event(AnnouncementCategory.SAFETY, stationKey, stationName, service, tracked, now, service.arrivalMillis() + 2_000, 0));
		}
		if (notArrived && until <= config.approachMillis() && !tracked.approach) {
			tracked.approach = true;
			final AnnouncementCategory category = service.terminating() ? AnnouncementCategory.TERMINATING : AnnouncementCategory.APPROACHING;
			tracked.terminating |= service.terminating();
			out.add(event(category, stationKey, stationName, service, tracked, now, service.arrivalMillis(), 0));
		}
		if (standing && !tracked.standing) {
			tracked.standing = true;
			if (service.terminating()) {
				if (!tracked.terminating) {
					tracked.terminating = true;
					out.add(event(AnnouncementCategory.TERMINATING, stationKey, stationName, service, tracked, now, service.departureMillis(), 0));
				}
			} else {
				out.add(event(AnnouncementCategory.STANDING, stationKey, stationName, service, tracked, now, service.departureMillis(), 0));
			}
		}
		if ((notArrived || standing) && service.realtime() && service.deviationMillis() >= config.delayThresholdMillis()
				&& (tracked.lastDelayAnnounced == Long.MIN_VALUE || service.deviationMillis() - tracked.lastDelayAnnounced >= config.delayRepeatMillis())) {
			tracked.lastDelayAnnounced = service.deviationMillis();
			final int minutes = (int) Math.max(1, (service.deviationMillis() + 30_000) / 60_000);
			out.add(event(AnnouncementCategory.DELAY, stationKey, stationName, service, tracked, now, service.departureMillis(), minutes));
		}
	}

	private static AnnouncementEvent event(AnnouncementCategory category, long stationKey, String stationName, ServiceSnapshot s, Tracked tracked, long now, long expires, int delayMinutes) {
		return new AnnouncementEvent(category, stationKey, stationName, s.platformId(), s.platformName(), s.routeId(), s.routeName(), s.routeNumber(),
				s.destination(), tracked.id, s.arrivalMillis(), delayMinutes, s.callingAt(), now, Math.max(expires, now + 5_000));
	}

	private void purge(long now) {
		final Iterator<Map.Entry<GroupKey, List<Tracked>>> groupIt = groups.entrySet().iterator();
		while (groupIt.hasNext()) {
			final List<Tracked> list = groupIt.next().getValue();
			final int before = list.size();
			list.removeIf(t -> now - t.lastSeen > config.staleMillis());
			trackedCount -= before - list.size();
			if (list.isEmpty()) {
				groupIt.remove();
			}
		}
	}

	private void dropOldest() {
		List<Tracked> oldestList = null;
		Tracked oldest = null;
		for (final List<Tracked> list : groups.values()) {
			for (final Tracked t : list) {
				if (oldest == null || t.lastSeen < oldest.lastSeen) {
					oldest = t;
					oldestList = list;
				}
			}
		}
		if (oldest != null) {
			oldestList.remove(oldest);
			trackedCount--;
		}
	}
}
