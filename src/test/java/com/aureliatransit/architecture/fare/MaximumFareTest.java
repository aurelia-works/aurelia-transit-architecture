package com.aureliatransit.architecture.fare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An incomplete journey takes the maximum fare, but never more than the balance holds. */
class MaximumFareTest {

	@Test
	void chargesTheMaximumWhenThereIsEnough() {
		assertEquals(MaximumFare.MAXIMUM, MaximumFare.charge(MaximumFare.MAXIMUM));
		assertEquals(MaximumFare.MAXIMUM, MaximumFare.charge(1000));
	}

	@Test
	void takesOnlyWhatIsLeftWhenTheBalanceIsLow() {
		assertEquals(0, MaximumFare.charge(0));
		assertEquals(0, MaximumFare.charge(-5));
		assertEquals(7, MaximumFare.charge(7));
	}

	@Test
	void theMaximumIsModest() {
		assertEquals(20, MaximumFare.MAXIMUM);
	}
}
