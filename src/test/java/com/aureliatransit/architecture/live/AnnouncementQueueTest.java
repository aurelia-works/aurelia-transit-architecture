package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import com.aureliatransit.architecture.live.announce.AnnouncementEvent;
import com.aureliatransit.architecture.live.announce.AnnouncementQueue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aureliatransit.architecture.live.TestData.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementQueueTest {

	private static AnnouncementEvent event(AnnouncementCategory category, long station, long track, long created, long expires) {
		return new AnnouncementEvent(category, station, "Station", 1, "1", 1, "Line", "L", "Terminus", track, NOW, 0, List.of(), created, expires);
	}

	private static AnnouncementQueue queue() {
		return new AnnouncementQueue(4, 60_000, 32, 5_000);
	}

	@Test
	void priorityOrderIsSafetyDelayTerminatingApproachingStanding() {
		final AnnouncementQueue q = queue();
		q.offer(event(AnnouncementCategory.STANDING, 1, 1, NOW, NOW + 60_000), NOW);
		q.offer(event(AnnouncementCategory.APPROACHING, 2, 2, NOW + 1, NOW + 60_000), NOW);
		q.offer(event(AnnouncementCategory.SAFETY, 3, 3, NOW + 2, NOW + 60_000), NOW);
		q.offer(event(AnnouncementCategory.DELAY, 4, 4, NOW + 3, NOW + 60_000), NOW);
		assertEquals(AnnouncementCategory.SAFETY, q.poll(NOW).category());
		assertEquals(AnnouncementCategory.DELAY, q.poll(NOW).category());
		assertEquals(AnnouncementCategory.APPROACHING, q.poll(NOW).category());
		assertEquals(AnnouncementCategory.STANDING, q.poll(NOW).category());
		assertNull(q.poll(NOW));
	}

	@Test
	void samePriorityIsFirstInFirstOut() {
		final AnnouncementQueue q = queue();
		q.offer(event(AnnouncementCategory.APPROACHING, 1, 1, NOW + 5, NOW + 60_000), NOW);
		q.offer(event(AnnouncementCategory.APPROACHING, 2, 2, NOW + 1, NOW + 60_000), NOW);
		assertEquals(2, q.poll(NOW).stationKey());
		assertEquals(1, q.poll(NOW).stationKey());
	}

	@Test
	void duplicatesAreRejectedWithinTheWindowAndAllowedAfterIt() {
		final AnnouncementQueue q = queue();
		final AnnouncementEvent e = event(AnnouncementCategory.APPROACHING, 1, 7, NOW, NOW + 600_000);
		assertTrue(q.offer(e, NOW));
		assertFalse(q.offer(e, NOW + 1_000));
		q.poll(NOW + 2_000);
		assertFalse(q.offer(e, NOW + 3_000), "already played recently");
		assertTrue(q.offer(e, NOW + 61_000));
	}

	@Test
	void differentTrainsOrCategoriesAreNotDuplicates() {
		final AnnouncementQueue q = queue();
		assertTrue(q.offer(event(AnnouncementCategory.APPROACHING, 1, 1, NOW, NOW + 60_000), NOW));
		assertTrue(q.offer(event(AnnouncementCategory.APPROACHING, 1, 2, NOW, NOW + 60_000), NOW));
		assertTrue(q.offer(event(AnnouncementCategory.STANDING, 1, 1, NOW, NOW + 60_000), NOW));
	}

	@Test
	void expiredEventsAreNeverPlayed() {
		final AnnouncementQueue q = queue();
		q.offer(event(AnnouncementCategory.APPROACHING, 1, 1, NOW, NOW + 5_000), NOW);
		assertNull(q.poll(NOW + 6_000));
		assertEquals(0, q.size());
	}

	@Test
	void stationCooldownDelaysNormalEventsButNotSafetyOrOtherStations() {
		final AnnouncementQueue q = queue();
		final AnnouncementEvent first = event(AnnouncementCategory.APPROACHING, 1, 1, NOW, NOW + 600_000);
		q.offer(first, NOW);
		q.markPlayed(q.poll(NOW), NOW);
		q.offer(event(AnnouncementCategory.APPROACHING, 1, 2, NOW, NOW + 600_000), NOW);
		q.offer(event(AnnouncementCategory.APPROACHING, 2, 3, NOW + 1, NOW + 600_000), NOW);
		assertEquals(2, q.poll(NOW + 1_000).stationKey(), "other station is not blocked");
		assertNull(q.poll(NOW + 1_000), "same station waits");
		q.offer(event(AnnouncementCategory.SAFETY, 1, 4, NOW, NOW + 600_000), NOW);
		assertEquals(AnnouncementCategory.SAFETY, q.poll(NOW + 1_500).category());
		assertEquals(1, q.poll(NOW + 6_000).stationKey());
	}

	@Test
	void fullQueueDropsTheLeastImportantAndStaysBounded() {
		final AnnouncementQueue q = queue();
		for (int i = 0; i < 4; i++) {
			assertTrue(q.offer(event(AnnouncementCategory.STANDING, i, i, NOW + i, NOW + 60_000), NOW));
		}
		assertFalse(q.offer(event(AnnouncementCategory.STANDING, 9, 9, NOW + 10, NOW + 60_000), NOW), "no room for equal priority");
		assertTrue(q.offer(event(AnnouncementCategory.SAFETY, 8, 8, NOW + 11, NOW + 60_000), NOW), "important event displaces a standing one");
		assertEquals(4, q.size());
		assertEquals(AnnouncementCategory.SAFETY, q.poll(NOW).category());
	}
}
