package com.aureliatransit.architecture.text;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignDataTest {

	private static SignData sample() {
		return new SignData("Central Station", "Hauptbahnhof", TextAlignment.LEFT, AccentPalette.GREEN, SignArrow.UP_RIGHT, "4", true,
				List.of(new RouteBadge("12", AccentPalette.ORANGE), new RouteBadge("N7", AccentPalette.PURPLE)));
	}

	@Test
	void nbtRoundTripKeepsEverything() {
		final NbtCompound nbt = new NbtCompound();
		sample().writeNbt(nbt);
		assertEquals(sample().restrictedTo(SignStyle.STATION), SignData.readNbt(nbt, SignStyle.STATION));
		assertEquals(sample().restrictedTo(SignStyle.BUS_STOP), SignData.readNbt(nbt, SignStyle.BUS_STOP));
		assertEquals(2, SignData.readNbt(nbt, SignStyle.BUS_STOP).routes().size());
	}

	@Test
	void v1LinesAreStillReadForEveryStyle() {
		final NbtCompound nbt = new NbtCompound();
		final NbtList lines = new NbtList();
		lines.add(NbtString.of("Old Town"));
		lines.add(NbtString.of("Altstadt"));
		lines.add(NbtString.of(""));
		nbt.put("Lines", lines);

		final SignData station = SignData.readNbt(nbt, SignStyle.STATION);
		assertEquals("Old Town", station.primary());
		assertEquals("Altstadt", station.secondary());

		final SignData bus = SignData.readNbt(nbt, SignStyle.BUS_STOP);
		assertEquals("Old Town", bus.primary());
		assertEquals("", bus.secondary());

		final NbtCompound plate = new NbtCompound();
		final NbtList number = new NbtList();
		number.add(NbtString.of("12"));
		plate.put("Lines", number);
		final SignData numberPlate = SignData.readNbt(plate, SignStyle.PLATFORM_NUMBER);
		assertEquals("12", numberPlate.platform());
		assertEquals("", numberPlate.primary());
	}

	@Test
	void newFormatWinsOverLegacyLines() {
		final NbtCompound nbt = new NbtCompound();
		sample().writeNbt(nbt);
		assertEquals("Central Station", SignData.readNbt(nbt, SignStyle.STATION).primary());
	}

	@Test
	void restrictedToDropsUnsupportedFields() {
		final SignData bus = sample().restrictedTo(SignStyle.BUS_STOP);
		assertEquals("", bus.secondary());
		assertEquals(SignArrow.NONE, bus.arrow());
		assertEquals("", bus.platform());
		assertEquals(false, bus.autoName());
		assertEquals(2, bus.routes().size());

		final SignData station = sample().restrictedTo(SignStyle.STATION);
		assertTrue(station.routes().isEmpty());
		assertEquals(SignArrow.UP_RIGHT, station.arrow());
		assertTrue(station.autoName());
	}

	@Test
	void constructorBoundsLengthsAndRoutes() {
		final SignData data = new SignData("p".repeat(300), "s".repeat(300), null, null, null, "12345678", false,
				java.util.Collections.nCopies(20, new RouteBadge("123456", AccentPalette.RED)));
		assertEquals(SignData.MAX_PRIMARY, data.primary().length());
		assertEquals(SignData.MAX_SECONDARY, data.secondary().length());
		assertEquals(SignData.MAX_PLATFORM, data.platform().length());
		assertEquals(SignStyle.MAX_ROUTES, data.routes().size());
		assertEquals(RouteBadge.MAX_LABEL, data.routes().get(0).label().length());
	}

	@Test
	void packetRoundTrip() {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		sample().write(buf);
		assertEquals(sample(), SignData.read(buf));
	}

	@Test
	void packetWithTooManyRoutesIsRejectedBeforeAllocation() {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeString("a");
		buf.writeString("b");
		buf.writeVarInt(0);
		buf.writeVarInt(0);
		buf.writeVarInt(0);
		buf.writeString("");
		buf.writeBoolean(false);
		buf.writeVarInt(1_000_000);
		assertThrows(IllegalArgumentException.class, () -> SignData.read(buf));
	}

	@Test
	void informationPacketWithTooManyRowsIsRejected() {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		buf.writeString("h");
		buf.writeVarInt(Integer.MAX_VALUE);
		assertThrows(IllegalArgumentException.class, () -> ConfigurableTextData.read(buf));
	}

	@Test
	void informationPacketRoundTrip() {
		final ConfigurableTextData data = new ConfigurableTextData("Timetable", List.of("Line 1", "Line 2"), TextAlignment.RIGHT, AccentPalette.TEAL);
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		data.write(buf);
		assertEquals(data, ConfigurableTextData.read(buf));
	}
}
