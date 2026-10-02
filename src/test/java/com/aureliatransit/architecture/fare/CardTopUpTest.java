package com.aureliatransit.architecture.fare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardTopUpTest {

	@Test
	void tiersMatchMtrTicketMachine() {
		assertEquals(10, CardTopUp.tierAmount(0));
		assertEquals(22, CardTopUp.tierAmount(1));
		assertEquals(48, CardTopUp.tierAmount(2));
		assertEquals(9728, CardTopUp.tierAmount(9));
	}

	@Test
	void splitsIntoLargestTiers() {
		assertEquals(0, CardTopUp.amountFor(0));
		assertEquals(0, CardTopUp.amountFor(-3));
		assertEquals(10, CardTopUp.amountFor(1));
		assertEquals(22, CardTopUp.amountFor(2));
		assertEquals(22 + 10, CardTopUp.amountFor(3));
		// 64 emeralds = tier 6: 64 * 16
		assertEquals(1024, CardTopUp.amountFor(64));
		// 1024 emeralds = two top tiers (the machine stops at 512)
		assertEquals(2 * 9728, CardTopUp.amountFor(1024));
	}

	@Test
	void neverBetterThanTheMachinePerEmerald() {
		for (int emeralds = 1; emeralds <= 600; emeralds++) {
			final int amount = CardTopUp.amountFor(emeralds);
			// Best machine rate is the 512 tier: 19 per emerald.
			assertEquals(true, amount <= emeralds * 19, "emeralds " + emeralds);
			assertEquals(true, amount >= emeralds * 10, "emeralds " + emeralds);
		}
	}

	@Test
	void capsOneLoad() {
		assertEquals(CardTopUp.amountFor(CardTopUp.MAX_EMERALDS), CardTopUp.amountFor(CardTopUp.MAX_EMERALDS + 500));
	}
}
