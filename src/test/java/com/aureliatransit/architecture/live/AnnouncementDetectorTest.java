package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import com.aureliatransit.architecture.live.announce.AnnouncementDetector;
import com.aureliatransit.architecture.live.announce.AnnouncementEvent;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aureliatransit.architecture.live.TestData.NOW;
import static com.aureliatransit.architecture.live.TestData.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementDetectorTest {

	private static List<AnnouncementCategory> categories(List<AnnouncementEvent> events) {
		return events.stream().map(AnnouncementEvent::category).toList();
	}

	@Test
	void nothingFarAway() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		assertTrue(detector.update(1, "Central", List.of(service(1, 1, NOW + 300_000)), NOW).isEmpty());
	}

	@Test
	void approachingFiresOnceInsideTheWindow() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		final ServiceSnapshot train = service(1, 1, NOW + 35_000);
		assertEquals(List.of(AnnouncementCategory.APPROACHING), categories(detector.update(1, "Central", List.of(train), NOW)));
		assertTrue(detector.update(1, "Central", List.of(train), NOW + 1_000).isEmpty());
		assertTrue(detector.update(1, "Central", List.of(train), NOW + 2_000).isEmpty());
	}

	@Test
	void safetyFiresJustBeforeArrivalAfterApproaching() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		final ServiceSnapshot train = service(1, 1, NOW + 35_000);
		detector.update(1, "Central", List.of(train), NOW);
		assertEquals(List.of(AnnouncementCategory.SAFETY), categories(detector.update(1, "Central", List.of(train), NOW + 25_000)));
	}

	@Test
	void standingFiresOnceWhenTheTrainIsAtThePlatform() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		final ServiceSnapshot train = service(1, 1, NOW - 2_000, NOW + 28_000, false, 0);
		assertEquals(List.of(AnnouncementCategory.STANDING), categories(detector.update(1, "Central", List.of(train), NOW)));
		assertTrue(detector.update(1, "Central", List.of(train), NOW + 1_000).isEmpty());
	}

	@Test
	void terminatingReplacesApproachingAndStanding() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		final ServiceSnapshot coming = service(1, 1, NOW + 30_000, NOW + 60_000, true, 0);
		assertEquals(List.of(AnnouncementCategory.TERMINATING), categories(detector.update(1, "Central", List.of(coming), NOW)));
		final ServiceSnapshot arrived = service(1, 1, NOW + 30_000, NOW + 60_000, true, 0);
		assertTrue(detector.update(1, "Central", List.of(arrived), NOW + 31_000).stream().noneMatch(e -> e.category() == AnnouncementCategory.TERMINATING
				|| e.category() == AnnouncementCategory.STANDING));
	}

	@Test
	void delayFiresAboveThresholdAndRepeatsOnlyWhenWorse() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		final long arrival = NOW + 200_000;
		assertTrue(detector.update(1, "Central", List.of(service(1, 1, arrival, arrival + 30_000, false, 60_000)), NOW).isEmpty());
		final List<AnnouncementEvent> first = detector.update(1, "Central", List.of(service(1, 1, arrival, arrival + 30_000, false, 150_000)), NOW + 1_000);
		assertEquals(List.of(AnnouncementCategory.DELAY), categories(first));
		assertEquals(3, first.get(0).delayMinutes());
		assertTrue(detector.update(1, "Central", List.of(service(1, 1, arrival, arrival + 30_000, false, 200_000)), NOW + 2_000).isEmpty());
		assertEquals(List.of(AnnouncementCategory.DELAY), categories(detector.update(1, "Central", List.of(service(1, 1, arrival, arrival + 30_000, false, 400_000)), NOW + 3_000)));
	}

	@Test
	void realtimeShiftOfTheSameTrainIsNotANewTrain() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		assertEquals(1, detector.update(1, "Central", List.of(service(1, 1, NOW + 35_000)), NOW).size());
		// the arrival moves 40 seconds later: still the same tracked visit, so no second approach event
		assertTrue(detector.update(1, "Central", List.of(service(1, 1, NOW + 75_000)), NOW + 1_000).isEmpty());
		assertEquals(1, detector.trackedCount());
	}

	@Test
	void twoTrainsOnOnePlatformAreTrackedSeparately() {
		final AnnouncementDetector detector = new AnnouncementDetector();
		final List<AnnouncementEvent> events = detector.update(1, "Central",
				List.of(service(1, 1, NOW + 30_000), service(1, 1, NOW + 600_000)), NOW);
		assertEquals(1, events.size());
		assertEquals(2, detector.trackedCount());
	}

	@Test
	void trackingIsBoundedAndStaleVisitsArePurged() {
		final AnnouncementDetector detector = new AnnouncementDetector(new AnnouncementDetector.Config(40_000, 12_000, 120_000, 180_000, 120_000, 90_000, 8));
		for (int platform = 0; platform < 40; platform++) {
			detector.update(1, "Central", List.of(service(platform, 1, NOW + 500_000)), NOW + platform);
		}
		assertTrue(detector.trackedCount() <= 8);
		detector.update(1, "Central", List.of(), NOW + 200_000);
		assertEquals(0, detector.trackedCount());
	}
}
