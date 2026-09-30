package com.aureliatransit.architecture.interactive;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatOccupancyTest {

	private final Set<UUID> alive = new HashSet<>();

	private UUID newSeat() {
		final UUID id = UUID.randomUUID();
		alive.add(id);
		return id;
	}

	@Test
	void aSlotCanOnlyBeHeldByOneLiveSeat() {
		final SeatOccupancy<String> occupancy = new SeatOccupancy<>();
		final UUID first = newSeat();
		final UUID second = newSeat();
		assertTrue(occupancy.claim("bench:0", first, alive::contains));
		assertFalse(occupancy.claim("bench:0", second, alive::contains));
		assertTrue(occupancy.isOccupied("bench:0", alive::contains));
		assertTrue(occupancy.claim("bench:1", second, alive::contains));
	}

	@Test
	void releaseOnlyWorksForTheHolder() {
		final SeatOccupancy<String> occupancy = new SeatOccupancy<>();
		final UUID holder = newSeat();
		final UUID other = newSeat();
		occupancy.claim("k", holder, alive::contains);
		occupancy.release("k", other);
		assertTrue(occupancy.isOccupied("k", alive::contains));
		occupancy.release("k", holder);
		assertFalse(occupancy.isOccupied("k", alive::contains));
		assertEquals(0, occupancy.size());
	}

	@Test
	void staleHoldersNeverLockASeat() {
		final SeatOccupancy<String> occupancy = new SeatOccupancy<>();
		final UUID gone = newSeat();
		occupancy.claim("k", gone, alive::contains);
		alive.remove(gone);
		assertFalse(occupancy.isOccupied("k", alive::contains));
		assertEquals(0, occupancy.size());
		assertTrue(occupancy.claim("k", newSeat(), alive::contains));
	}

	@Test
	void clearDropsEverything() {
		final SeatOccupancy<String> occupancy = new SeatOccupancy<>();
		occupancy.claim("a", newSeat(), alive::contains);
		occupancy.claim("b", newSeat(), alive::contains);
		occupancy.clear();
		assertEquals(0, occupancy.size());
	}

	@Test
	void seatKeysDistinguishDimensionPositionAndSlot() {
		final Set<SeatManager.SeatKey> keys = new HashSet<>();
		keys.add(new SeatManager.SeatKey("minecraft:overworld", 5L, 0));
		keys.add(new SeatManager.SeatKey("minecraft:overworld", 5L, 1));
		keys.add(new SeatManager.SeatKey("minecraft:the_nether", 5L, 0));
		keys.add(new SeatManager.SeatKey("minecraft:overworld", 6L, 0));
		keys.add(new SeatManager.SeatKey("minecraft:overworld", 5L, 0));
		assertEquals(4, keys.size());
	}

	@Test
	void slotOrderTriesTheClickedSlotFirst() {
		assertArrayEquals(new int[]{1, 0}, SeatGeometry.slotOrder(1, 2));
		assertArrayEquals(new int[]{0, 1}, SeatGeometry.slotOrder(0, 2));
		assertArrayEquals(new int[]{0}, SeatGeometry.slotOrder(0, 1));
	}
}
