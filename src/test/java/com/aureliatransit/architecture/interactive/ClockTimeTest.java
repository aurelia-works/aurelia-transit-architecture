package com.aureliatransit.architecture.interactive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClockTimeTest {

	@Test
	void dayStartsAtSixInTheMorning() {
		assertEquals("06:00", ClockTime.format(ClockTime.minuteOfDay(0, 0)));
		assertEquals("12:00", ClockTime.format(ClockTime.minuteOfDay(6000, 0)));
		assertEquals("18:00", ClockTime.format(ClockTime.minuteOfDay(12000, 0)));
		assertEquals("00:00", ClockTime.format(ClockTime.minuteOfDay(18000, 0)));
		assertEquals("05:59", ClockTime.format(ClockTime.minuteOfDay(23999, 0)));
	}

	@Test
	void timeWrapsAcrossDaysAndNegativeValues() {
		assertEquals(ClockTime.minuteOfDay(1500, 0), ClockTime.minuteOfDay(1500 + 24000L * 7, 0));
		assertEquals("06:00", ClockTime.format(ClockTime.minuteOfDay(-24000, 0)));
	}

	@Test
	void offsetShiftsTheDisplay() {
		assertEquals("07:30", ClockTime.format(ClockTime.minuteOfDay(0, 90)));
		assertEquals("23:00", ClockTime.format(ClockTime.minuteOfDay(0, -7 * 60)));
	}

	@Test
	void stringOnlyChangesOncePerMinute() {
		// 1000 ticks per hour: one minute is ~16.67 ticks, so a minute-based cache changes at most every 16 ticks.
		int changes = 0;
		int last = ClockTime.minuteOfDay(0, 0);
		for (long t = 1; t < 24000; t++) {
			final int now = ClockTime.minuteOfDay(t, 0);
			if (now != last) {
				changes++;
				last = now;
			}
		}
		assertEquals(1439, changes);
	}

	@Test
	void handAngles() {
		assertEquals(0F, ClockTime.hourAngle(12 * 60), 1e-5);
		assertEquals(90F, ClockTime.hourAngle(3 * 60), 1e-5);
		assertEquals(180F, ClockTime.minuteAngle(30), 1e-5);
		assertEquals(195F, ClockTime.hourAngle(6 * 60 + 30), 1e-5);
	}
}
