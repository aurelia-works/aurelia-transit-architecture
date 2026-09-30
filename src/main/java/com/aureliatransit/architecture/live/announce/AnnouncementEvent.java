package com.aureliatransit.architecture.live.announce;

import java.util.List;

/**
 * One thing worth announcing, detected from snapshot changes for a logical station.
 *
 * @param trackId       stable id of the tracked train visit (survives small arrival-time changes)
 * @param expiresMillis after this instant the event is stale and must not be played
 * @param delayMinutes  only meaningful for DELAY
 */
public record AnnouncementEvent(
		AnnouncementCategory category,
		long stationKey,
		String stationName,
		long platformId,
		String platformName,
		long routeId,
		String routeName,
		String routeNumber,
		String destination,
		long trackId,
		long arrivalMillis,
		int delayMinutes,
		List<String> callingAt,
		long createdMillis,
		long expiresMillis
) {

	public AnnouncementEvent {
		callingAt = List.copyOf(callingAt);
	}

	/**
	 * Identity used for de-duplication: the same train visit and kind of announcement.
	 */
	public DedupKey dedupKey() {
		return new DedupKey(stationKey, platformId, routeId, trackId, category);
	}

	public record DedupKey(long stationKey, long platformId, long routeId, long trackId, AnnouncementCategory category) {
	}
}
