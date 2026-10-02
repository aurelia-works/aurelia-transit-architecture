package com.aureliatransit.architecture.fare;

/**
 * What {@code /card load <emeralds>} adds to a card. Uses MTR 4's ticket machine rates exactly, so loading by command
 * never beats the machine: tier {@code i} (0-9) costs {@code 2^i} emeralds and adds {@code ceil(2^i * (10 + i))}
 * (MTR {@code PacketAddBalance.getAddAmount}). A load is split into the largest tiers first, the same as pressing the
 * machine's buttons for those tiers one by one.
 */
public final class CardTopUp {

	/** MTR's ticket machine offers ten tiers (1 to 512 emeralds). */
	public static final int TIERS = 10;
	/** One load at most: a full inventory of emerald stacks. */
	public static final int MAX_EMERALDS = 36 * 64;

	private CardTopUp() {
	}

	/** Balance added by one machine tier. */
	public static int tierAmount(int tier) {
		return (int) Math.ceil(Math.pow(2, tier) * (10 + tier));
	}

	/** Balance added for {@code emeralds} emeralds (0 for 0 or fewer). */
	public static int amountFor(int emeralds) {
		int left = Math.min(emeralds, MAX_EMERALDS);
		int total = 0;
		for (int tier = TIERS - 1; tier >= 0 && left > 0; tier--) {
			final int cost = 1 << tier;
			while (left >= cost) {
				total += tierAmount(tier);
				left -= cost;
			}
		}
		return total;
	}
}
