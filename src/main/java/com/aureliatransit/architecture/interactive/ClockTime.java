package com.aureliatransit.architecture.interactive;

/**
 * Pure conversion of Minecraft time of day to a 24-hour clock. Tick 0 of a day is 06:00 and one in-game hour is 1000
 * ticks, so one in-game minute is 1000/60 ticks.
 */
public final class ClockTime {

	public static final int MINUTES_PER_DAY = 24 * 60;

	private ClockTime() {
	}

	/**
	 * Minute of the day (0-1439) for a world time of day in ticks, shifted by an optional offset in minutes.
	 */
	public static int minuteOfDay(long timeOfDay, int offsetMinutes) {
		final long ticks = Math.floorMod(timeOfDay, 24000L);
		final long minutes = (ticks * 60) / 1000 + 6 * 60 + offsetMinutes;
		return (int) Math.floorMod(minutes, (long) MINUTES_PER_DAY);
	}

	public static int hour(int minuteOfDay) {
		return minuteOfDay / 60;
	}

	public static int minute(int minuteOfDay) {
		return minuteOfDay % 60;
	}

	/**
	 * "HH:MM".
	 */
	public static String format(int minuteOfDay) {
		final int h = hour(minuteOfDay);
		final int m = minute(minuteOfDay);
		return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
	}

	/**
	 * Hour-hand angle in degrees clockwise from 12 o'clock.
	 */
	public static float hourAngle(int minuteOfDay) {
		return (minuteOfDay % 720) * 0.5F;
	}

	/**
	 * Minute-hand angle in degrees clockwise from 12 o'clock.
	 */
	public static float minuteAngle(int minuteOfDay) {
		return minute(minuteOfDay) * 6.0F;
	}
}
