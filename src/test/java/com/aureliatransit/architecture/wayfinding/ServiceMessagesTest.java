package com.aureliatransit.architecture.wayfinding;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceMessagesTest {

	private static ServiceMessages sample() {
		return ServiceMessages.EMPTY
				.add(MessageScope.NETWORK, "", MessageSeverity.INFO, "Engineering work after 22:00")
				.add(MessageScope.STATION, "Market Street", MessageSeverity.WARNING, "Platform 2 closed")
				.add(MessageScope.STATION, "Market Street", MessageSeverity.SEVERE, "Severe delays")
				.add(MessageScope.STATION, "Stadium", MessageSeverity.DISRUPTION, "Closed after the game")
				.add(MessageScope.NETWORK, "", MessageSeverity.WARNING, "Bus 37 diversion");
	}

	private static List<String> texts(List<ServiceMessage> list) {
		return list.stream().map(ServiceMessage::text).toList();
	}

	@Test
	void severalMessagesAreActiveAtOnceWithStableIds() {
		final ServiceMessages set = sample();
		assertEquals(5, set.size());
		assertEquals(List.of(1, 2, 3, 4, 5), set.all().stream().map(ServiceMessage::id).toList());
		final ServiceMessages removed = set.remove(2);
		assertEquals(List.of(1, 3, 4, 5), removed.all().stream().map(ServiceMessage::id).toList());
		assertEquals(6, removed.add(MessageScope.NETWORK, "", MessageSeverity.INFO, "new").byId(6).id(), "ids are never reused");
		assertSame(removed, removed.remove(99), "unknown id changes nothing");
	}

	@Test
	void orderIsScopeThenSeverityThenIdAndDeterministic() {
		final ServiceMessages set = sample();
		assertEquals(List.of("Mine", "Severe delays", "Platform 2 closed", "Bus 37 diversion", "Engineering work after 22:00"),
				texts(set.applicable("Mine", "market street")));
		assertEquals(texts(set.applicable("", "Market Street")), texts(set.applicable("", "  MARKET   street ")));
		// same set built in another order lists the same way
		final ServiceMessages reversed = ServiceMessages.EMPTY
				.add(MessageScope.NETWORK, "", MessageSeverity.WARNING, "Bus 37 diversion")
				.add(MessageScope.STATION, "Market Street", MessageSeverity.SEVERE, "Severe delays")
				.add(MessageScope.STATION, "Market Street", MessageSeverity.WARNING, "Platform 2 closed")
				.add(MessageScope.NETWORK, "", MessageSeverity.INFO, "Engineering work after 22:00");
		assertEquals(texts(set.applicable("", "Market Street")), texts(reversed.applicable("", "Market Street")));
	}

	@Test
	void scopeFiltersStationMessages() {
		final ServiceMessages set = sample();
		assertEquals(List.of("Closed after the game", "Bus 37 diversion", "Engineering work after 22:00"), texts(set.applicable("", "Stadium")));
		assertEquals(List.of("Bus 37 diversion", "Engineering work after 22:00"), texts(set.applicable("", "Elsewhere")));
		assertEquals(List.of("Bus 37 diversion", "Engineering work after 22:00"), texts(set.applicable("", "")), "no station: network only");
		assertTrue(ServiceMessages.EMPTY.applicable("", "Market Street").isEmpty());
		assertEquals(List.of("Mine"), texts(ServiceMessages.EMPTY.applicable("Mine", "")));
	}

	@Test
	void clearByScope() {
		final ServiceMessages set = sample();
		assertEquals(3, set.clearNetwork().size());
		assertEquals(3, set.clearStation("MARKET STREET").size());
		assertTrue(set.clearAll().isEmpty());
		assertEquals(6, set.clearAll().add(MessageScope.NETWORK, "", MessageSeverity.INFO, "x").nextId() - 1, "ids continue after clear");
	}

	@Test
	void textIsSanitisedAndRefusedWhenInvalid() {
		final ServiceMessage m = new ServiceMessage("§cRed\u0007 " + "x".repeat(200), MessageSeverity.WARNING);
		assertEquals(ServiceMessage.MAX_TEXT, m.text().length());
		assertEquals(MessageSeverity.INFO, new ServiceMessage("a", null).severity());
		assertNull(ServiceMessages.EMPTY.add(MessageScope.NETWORK, "", MessageSeverity.INFO, "  "), "empty text");
		assertNull(ServiceMessages.EMPTY.add(MessageScope.STATION, "  ", MessageSeverity.INFO, "m"), "no station");
		assertNull(ServiceMessages.EMPTY.add(MessageScope.DISPLAY, "", MessageSeverity.INFO, "m"), "display messages live in the display config");
	}

	@Test
	void listIsBounded() {
		ServiceMessages set = ServiceMessages.EMPTY;
		for (int i = 0; i < ServiceMessages.MAX_MESSAGES; i++) {
			set = set.add(MessageScope.NETWORK, "", MessageSeverity.INFO, "m" + i);
			assertNotNull(set);
		}
		assertNull(set.add(MessageScope.NETWORK, "", MessageSeverity.INFO, "one too many"));
		assertNotNull(set.remove(1).add(MessageScope.NETWORK, "", MessageSeverity.INFO, "fits"), "removing frees a slot");
	}

	@Test
	void persistenceRoundTrip() {
		final ServiceMessages set = sample().remove(3);
		final NbtCompound nbt = set.toNbt();
		assertEquals(set, ServiceMessages.fromNbt(nbt));
		assertEquals(ServiceMessages.EMPTY, ServiceMessages.fromNbt(new NbtCompound()));
		assertEquals(set, ServiceMessages.fromNbt(ServiceMessages.fromNbt(nbt).toNbt()));
		assertEquals(6, ServiceMessages.fromNbt(nbt).add(MessageScope.NETWORK, "", MessageSeverity.INFO, "n").byId(6).id(), "next id survives the restart");
		final ServiceMessageState state = new ServiceMessageState();
		state.set(set);
		assertEquals(set, ServiceMessageState.fromNbt(state.writeNbt(new NbtCompound())).messages());
	}

	@Test
	void nbtFromDamagedDataKeepsOnlyValidMessages() {
		final NbtCompound tag = new NbtCompound();
		final NbtList list = new NbtList();
		list.add(new ServiceMessage(1, MessageScope.NETWORK, "", MessageSeverity.INFO, "ok").toNbt());
		list.add(new ServiceMessage(1, MessageScope.NETWORK, "", MessageSeverity.INFO, "duplicate id").toNbt());
		list.add(new ServiceMessage(0, MessageScope.NETWORK, "", MessageSeverity.INFO, "no id").toNbt());
		list.add(new ServiceMessage(2, MessageScope.NETWORK, "", MessageSeverity.INFO, "").toNbt());
		tag.put("Messages", list);
		assertEquals(List.of("ok"), texts(ServiceMessages.fromNbt(tag).all()));
		assertTrue(ServiceMessages.fromNbt(tag).nextId() > 1);
	}

	@Test
	void migratesTheOldSingleMessageLayout() {
		final NbtCompound legacy = new NbtCompound();
		legacy.put("Network", new ServiceMessage("Works tonight", MessageSeverity.WARNING).toNbt());
		final NbtList stations = new NbtList();
		final NbtCompound station = new NbtCompound();
		station.putString("Station", "Market Street");
		station.putString("Text", "Lift out");
		station.putInt("Severity", 0);
		stations.add(station);
		legacy.put("Stations", stations);
		final ServiceMessages migrated = ServiceMessages.fromNbt(legacy);
		assertEquals(List.of("Lift out", "Works tonight"), texts(migrated.applicable("", "market street")));
		assertEquals(List.of(1, 2), migrated.all().stream().map(ServiceMessage::id).toList());
	}

	@Test
	void packetRoundTripAndBounds() {
		final ServiceMessages set = sample();
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		set.write(buf);
		assertEquals(set, ServiceMessages.read(buf));

		final PacketByteBuf tooMany = new PacketByteBuf(Unpooled.buffer());
		tooMany.writeVarInt(1);
		tooMany.writeVarInt(ServiceMessages.MAX_MESSAGES + 1);
		assertThrows(IllegalArgumentException.class, () -> ServiceMessages.read(tooMany));
		final PacketByteBuf negative = new PacketByteBuf(Unpooled.buffer());
		negative.writeVarInt(1);
		negative.writeVarInt(-1);
		assertThrows(IllegalArgumentException.class, () -> ServiceMessages.read(negative));
		final PacketByteBuf longText = new PacketByteBuf(Unpooled.buffer());
		longText.writeVarInt(1);
		longText.writeVarInt(1);
		longText.writeVarInt(1);
		longText.writeVarInt(0);
		longText.writeVarInt(0);
		longText.writeString("", 10);
		longText.writeString("y".repeat(ServiceMessage.MAX_TEXT * 4 + 10), 32767);
		assertThrows(RuntimeException.class, () -> ServiceMessages.read(longText));
		final PacketByteBuf truncated = new PacketByteBuf(Unpooled.buffer());
		truncated.writeVarInt(1);
		truncated.writeVarInt(3);
		assertThrows(RuntimeException.class, () -> ServiceMessages.read(truncated));
	}

	@Test
	void severityIdsAndAliases() {
		assertEquals(MessageSeverity.WARNING, MessageSeverity.byId("notice"));
		assertEquals(MessageSeverity.WARNING, MessageSeverity.byId("Warning"));
		assertEquals(MessageSeverity.SEVERE, MessageSeverity.byId("SEVERE"));
		assertNull(MessageSeverity.byId("nope"));
		assertEquals(MessageSeverity.INFO, MessageSeverity.byOrdinal(99));
	}
}
