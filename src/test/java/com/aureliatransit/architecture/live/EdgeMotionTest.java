package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.display.EdgeMotion;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeMotionTest {

	private static final long T = 1_000_000_000L;

	private static ServiceSnapshot at(long platform, long arrival, long departure) {
		return new ServiceSnapshot(1, "R", "1", 0, "Beta", platform, "1", arrival, departure, 0, false, false, List.of());
	}

	@Test
	void deploysOverOneSecondAndRetracts() {
		final EdgeMotion motion = new EdgeMotion();
		assertEquals(0F, motion.update(T, true), "first frame starts at rest");
		assertEquals(0.5F, motion.update(T + 500, true), 1e-4);
		assertEquals(1F, motion.update(T + 1_500, true), "never overshoots");
		assertEquals(0.75F, motion.update(T + 1_750, false), 1e-4);
		assertEquals(0F, motion.update(T + 5_000, false));
	}

	@Test
	void clockGoingBackwardsSnapsToTheTarget() {
		final EdgeMotion motion = new EdgeMotion();
		motion.update(T, true);
		motion.update(T + 300, true);
		assertEquals(1F, motion.update(T - 10_000, true));
	}

	@Test
	void onlyAStandingTrainAtThisPlatformDeploys() {
		final List<ServiceSnapshot> services = List.of(at(5, T - 10_000, T + 20_000), at(6, T + 60_000, T + 90_000));
		assertTrue(EdgeMotion.trainStanding(services, 5, T));
		assertFalse(EdgeMotion.trainStanding(services, 6, T), "approaching, not standing");
		assertFalse(EdgeMotion.trainStanding(services, 7, T), "another platform");
		assertFalse(EdgeMotion.trainStanding(services, 0, T), "no platform nearby");
		assertFalse(EdgeMotion.trainStanding(services, 5, T + 25_000), "departed");
		assertFalse(EdgeMotion.trainStanding(List.of(), 5, T), "no data: stays at rest");
	}
}
