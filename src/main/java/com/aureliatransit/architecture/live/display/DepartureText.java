package com.aureliatransit.architecture.live.display;

import com.aureliatransit.architecture.transit.ServiceSnapshot;

/**
 * Pure formatting rules for the status column of a departure row.
 */
public final class DepartureText {

	/**
	 * A train this close to arriving is shown as "Due".
	 */
	public static final long DUE_MILLIS = 45_000;
	/**
	 * Realtime lateness below this is not flagged.
	 */
	public static final long DELAY_FLAG_MILLIS = 60_000;

	public enum Phase {
		/** Standing at the platform, will depart. */
		BOARDING,
		/** Standing at the platform, terminating: passengers leave. */
		ARRIVED,
		/** About to arrive. */
		DUE,
		/** Further away: show minutes. */
		MINUTES
	}

	private DepartureText() {
	}

	public static Phase phase(ServiceSnapshot service, long nowMillis) {
		if (service.isStanding(nowMillis) || service.arrivalMillis() <= nowMillis) {
			return service.terminating() ? Phase.ARRIVED : Phase.BOARDING;
		}
		return service.arrivalMillis() - nowMillis <= DUE_MILLIS ? Phase.DUE : Phase.MINUTES;
	}

	/**
	 * Whole minutes until arrival, rounded up (so "1 min" means within the next minute).
	 */
	public static int minutesUntil(ServiceSnapshot service, long nowMillis) {
		final long seconds = Math.max(0, (service.arrivalMillis() - nowMillis + 999) / 1000);
		return (int) Math.min(Integer.MAX_VALUE, (seconds + 59) / 60);
	}

	/**
	 * Lateness in whole minutes when the train runs late by a flaggable amount according to realtime data, else 0.
	 */
	public static int delayMinutes(ServiceSnapshot service) {
		if (!service.realtime() || service.deviationMillis() < DELAY_FLAG_MILLIS) {
			return 0;
		}
		return (int) Math.max(1, (service.deviationMillis() + 30_000) / 60_000);
	}

	public static String status(ServiceSnapshot service, long nowMillis) {
		return switch (phase(service, nowMillis)) {
			case BOARDING -> "Boarding";
			case ARRIVED -> "Arrived";
			case DUE -> "Due";
			case MINUTES -> {
				final int minutes = minutesUntil(service, nowMillis);
				yield minutes > 99 ? "99+ min" : minutes + " min";
			}
		};
	}
}
