package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.SignArrow;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitPlanTest {

	private static final List<ExitInfo> MTR = List.of(new ExitInfo("A", List.of("City Hall", "Library")), new ExitInfo("B", List.of("Stadium")),
			new ExitInfo("C", List.of("Harbour")));

	private static List<String> labels(List<ExitPlan.Shown> shown) {
		return shown.stream().map(ExitPlan.Shown::label).toList();
	}

	@Test
	void withoutSettingsMtrExitsShowAsMtrDefinesThem() {
		final List<ExitPlan.Shown> shown = ExitPlan.merge(MTR, List.of(), 8);
		assertEquals(List.of("A", "B", "C"), labels(shown));
		assertEquals(List.of("City Hall", "Library"), shown.get(0).destinations());
		assertTrue(shown.stream().allMatch(s -> s.arrow() == SignArrow.NONE && !s.manual()));
	}

	@Test
	void eachExitIsConfiguredIndependently() {
		final List<ExitSetting> settings = List.of(new ExitSetting("b", "Football ground", SignArrow.LEFT, false), new ExitSetting("C", "", SignArrow.NONE, true),
				new ExitSetting("A", "", SignArrow.UP, false));
		final List<ExitPlan.Shown> shown = ExitPlan.merge(MTR, settings, 8);
		assertEquals(List.of("A", "B"), labels(shown), "C hidden, MTR order kept");
		assertEquals(List.of("City Hall", "Library"), shown.get(0).destinations(), "no text: MTR's destinations stay");
		assertEquals(SignArrow.UP, shown.get(0).arrow());
		assertEquals(List.of("Football ground"), shown.get(1).destinations(), "label match is case-insensitive");
		assertEquals(SignArrow.LEFT, shown.get(1).arrow());
	}

	@Test
	void manualExitsFollowMtrsAndNeedText() {
		final List<ExitSetting> settings = List.of(new ExitSetting("D", "Bus station", SignArrow.RIGHT, false), new ExitSetting("E", "", SignArrow.LEFT, false),
				new ExitSetting("F", "Pier", SignArrow.NONE, true), new ExitSetting("d", "Duplicate", SignArrow.NONE, false));
		final List<ExitPlan.Shown> shown = ExitPlan.merge(MTR, settings, 8);
		assertEquals(List.of("A", "B", "C", "D"), labels(shown));
		assertTrue(shown.get(3).manual());
		assertEquals(List.of("Bus station"), shown.get(3).destinations());
		assertEquals(List.of("D"), labels(ExitPlan.merge(List.of(), settings, 8)), "manual exits work without an MTR station");
	}

	@Test
	void boundedByMax() {
		assertEquals(List.of("A", "B"), labels(ExitPlan.merge(MTR, List.of(new ExitSetting("D", "Bus", SignArrow.NONE, false)), 2)));
	}

	@Test
	void wayfindingDataStoresSettingsBoundedAndDropsDefaults() {
		final List<ExitSetting> many = Collections.nCopies(12, new ExitSetting("X", "Text", SignArrow.LEFT, false));
		final WayfindingData data = WayfindingData.EMPTY.withExitSettings(many);
		assertEquals(WayfindingData.MAX_EXIT_SETTINGS, data.exitSettings().size());
		assertTrue(WayfindingData.EMPTY.withExitSettings(List.of(new ExitSetting("A", "", SignArrow.NONE, false), new ExitSetting("", "x", SignArrow.LEFT, false)))
				.exitSettings().isEmpty(), "default and unlabelled rows are not stored");

		final WayfindingData one = WayfindingData.EMPTY.withExitSettings(List.of(new ExitSetting("B", "Stadium", SignArrow.UP_RIGHT, true)));
		final NbtCompound nbt = new NbtCompound();
		one.writeNbt(nbt);
		assertEquals(one, WayfindingData.readNbt(nbt, WayfindingData.EMPTY));
		final NbtCompound old = new NbtCompound();
		WayfindingData.EMPTY.writeNbt(old);
		assertTrue(WayfindingData.readNbt(old, WayfindingData.EMPTY).exitSettings().isEmpty(), "1.3 data loads with no settings");

		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		one.write(buf);
		assertEquals(one, WayfindingData.read(buf));
	}

	@Test
	void oversizedPacketIsRejected() {
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		WayfindingData.EMPTY.write(buf);
		buf.writerIndex(buf.writerIndex() - 1);
		buf.writeVarInt(WayfindingData.MAX_EXIT_SETTINGS + 1);
		assertThrows(IllegalArgumentException.class, () -> WayfindingData.read(buf));
	}
}
