package com.aureliatransit.architecture.fare;

/**
 * An incomplete journey's fare: what a passenger is charged when they do not tap out properly — slipping through an open
 * fare gate behind the passenger it opened for, or otherwise passing without paying. Real systems charge a "maximum
 * fare" in this case; ours is deliberately modest and never takes the balance below zero. It is charged through MTR's
 * own balance, like every other ATA fare.
 */
public final class MaximumFare {

	/** The most one incomplete journey costs. Kept generous on purpose. */
	public static final int MAXIMUM = 20;

	private MaximumFare() {
	}

	/** The amount to take from a balance of {@code balance}: the maximum fare, or everything left if that is less. */
	public static int charge(int balance) {
		return Math.max(0, Math.min(MAXIMUM, balance));
	}
}
