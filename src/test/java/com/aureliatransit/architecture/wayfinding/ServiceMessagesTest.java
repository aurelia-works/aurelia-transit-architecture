package com.aureliatransit.architecture.wayfinding;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceMessagesTest {

	private static ServiceMessage msg(String text, MessageSeverity severity) {
		return new ServiceMessage(text, severity);
	}

	private static ServiceMessages sample() {
		return ServiceMessages.EMPTY
				.withNetwork(msg("Network works tonight", MessageSeverity.INFO))
				.withStation("Market Street", msg("Escalator out of service", MessageSeverity.WARNING))
				.withStation("Stadium", msg("Closed after the game", MessageSeverity.DISRUPTION));
	}

	@Test
	void priorityIsLocalThenStationThenNetwork() {
		final ServiceMessages set = sample();
		assertEquals("Mine", set.select("Mine", "Market Street").text());
		assertEquals(MessageSeverity.INFO, set.select("Mine", "Market Street").severity());
		assertEquals("Escalator out of service", set.select("", "Market Street").text());
		assertEquals(MessageSeverity.WARNING, set.select("", "Market Street").severity());
		assertEquals("Network works tonight", set.select("", "Elsewhere").text());
		assertEquals("Network works tonight", set.select("  ", "").text(), "a blank local message is no message");
		assertTrue(ServiceMessages.EMPTY.select("", "Market Street").isEmpty());
	}

	@Test
	void stationMatchIsCaseInsensitiveAndWhitespaceTolerant() {
		final ServiceMessages set = sample();
		assertEquals("Closed after the game", set.forStation("  STADIUM ").text());
		assertEquals("Escalator out of service", set.forStation("market   street").text());
		assertTrue(set.forStation("").isEmpty());
		assertTrue(set.forStation(null).isEmpty());
	}

	@Test
	void textIsSanitisedAndBounded() {
		final ServiceMessage m = msg("§cRed\u0007 " + "x".repeat(200), MessageSeverity.WARNING);
		assertEquals(ServiceMessage.MAX_TEXT, m.text().length());
		assertTrue(m.text().startsWith("cRed") || m.text().startsWith("Red"));
		assertTrue(m.text().chars().noneMatch(Character::isISOControl));
		assertTrue(msg(null, null).isEmpty());
		assertEquals(MessageSeverity.INFO, msg("a", null).severity());
	}

	@Test
	void clearingAndLimit() {
		ServiceMessages set = ServiceMessages.EMPTY;
		for (int i = 0; i < ServiceMessages.MAX_STATIONS; i++) {
			set = set.withStation("Station " + i, msg("m", MessageSeverity.INFO));
			assertNotNull(set);
		}
		assertNull(set.withStation("One too many", msg("m", MessageSeverity.INFO)), "17th station is refused");
		assertNotNull(set.withStation("station 3", msg("changed", MessageSeverity.WARNING)), "an existing station can be updated");
		final ServiceMessages cleared = set.withStation("STATION 3", ServiceMessage.NONE);
		assertEquals(ServiceMessages.MAX_STATIONS - 1, cleared.stations().size());
		assertNotNull(cleared.withStation("One more", msg("m", MessageSeverity.INFO)), "clearing frees a slot");
		assertNull(ServiceMessages.EMPTY.withStation("  ", msg("m", MessageSeverity.INFO)));
		assertTrue(sample().withNetwork(ServiceMessage.NONE).network().isEmpty());
	}

	@Test
	void nbtRoundTrip() {
		final ServiceMessages set = sample();
		final NbtCompound nbt = set.toNbt();
		assertEquals(set, ServiceMessages.fromNbt(nbt));
		assertEquals(ServiceMessages.EMPTY, ServiceMessages.fromNbt(new NbtCompound()));
		assertEquals(set, ServiceMessages.fromNbt(ServiceMessages.fromNbt(nbt).toNbt()));
	}

	@Test
	void persistentStateNbtRoundTrip() {
		final ServiceMessageState state = new ServiceMessageState();
		state.set(sample());
		final NbtCompound nbt = state.writeNbt(new NbtCompound());
		assertEquals(sample(), ServiceMessageState.fromNbt(nbt).messages());
	}

	@Test
	void packetRoundTrip() {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		sample().write(buf);
		assertEquals(sample(), ServiceMessages.read(buf));
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void packetReadIsBounded() {
		final PacketByteBuf tooMany = new PacketByteBuf(Unpooled.buffer());
		ServiceMessage.NONE.write(tooMany);
		tooMany.writeVarInt(ServiceMessages.MAX_STATIONS + 1);
		assertThrows(IllegalArgumentException.class, () -> ServiceMessages.read(tooMany));

		final PacketByteBuf negative = new PacketByteBuf(Unpooled.buffer());
		ServiceMessage.NONE.write(negative);
		negative.writeVarInt(-1);
		assertThrows(IllegalArgumentException.class, () -> ServiceMessages.read(negative));

		final PacketByteBuf longText = new PacketByteBuf(Unpooled.buffer());
		longText.writeVarInt(0);
		longText.writeString("y".repeat(ServiceMessage.MAX_TEXT * 4 + 10), 32767);
		assertThrows(RuntimeException.class, () -> ServiceMessages.read(longText));

		final PacketByteBuf truncated = new PacketByteBuf(Unpooled.buffer());
		truncated.writeVarInt(0);
		assertThrows(RuntimeException.class, () -> ServiceMessages.read(truncated));
	}

	@Test
	void hostileStationNamesAreBoundedOnRead() {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		ServiceMessage.NONE.write(buf);
		buf.writeVarInt(1);
		buf.writeString("z".repeat(ServiceMessages.MAX_STATION_NAME * 4), 32767);
		msg("m", MessageSeverity.INFO).write(buf);
		final ServiceMessages read = ServiceMessages.read(buf);
		assertEquals(ServiceMessages.MAX_STATION_NAME, read.stations().get(0).station().length());
	}
}
