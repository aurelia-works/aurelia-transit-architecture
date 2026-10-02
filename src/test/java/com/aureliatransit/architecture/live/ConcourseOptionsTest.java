package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.display.BoardSummary;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.StationAssociation;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcourseOptionsTest {

	@Test
	void summaryNamesThePlatformBesideTheBoardOnlyWhenThereIsOne() {
		final List<PlatformReference> platforms = List.of(new PlatformReference(11, "1", 7), new PlatformReference(12, "2", 7));
		assertEquals("2 platforms · This is platform 2", BoardSummary.text(platforms.size(), BoardSummary.platformName(platforms, 12)));
		assertEquals("2 platforms", BoardSummary.text(platforms.size(), BoardSummary.platformName(platforms, 0)), "no platform nearby: count only");
		assertEquals("2 platforms", BoardSummary.text(platforms.size(), BoardSummary.platformName(platforms, 99)), "another station's platform is not claimed");
		assertEquals("1 platform", BoardSummary.text(1, ""));
		assertEquals("", BoardSummary.text(0, "1"), "no station data: nothing");
	}

	@Test
	void arrivalsAndSummarySurviveNbtAndPackets() {
		final DisplayConfig config = new DisplayConfig(StationAssociation.AUTO, DisplayStyle.EUROPEAN_MODERN, 6, true, false, 6, BoardAlignment.TOP, "", false, true, true);
		final NbtCompound nbt = new NbtCompound();
		config.writeNbt(nbt);
		assertEquals(config, DisplayConfig.readNbt(nbt, DisplayKind.CONCOURSE));
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		config.write(buf);
		final DisplayConfig read = DisplayConfig.read(buf);
		assertTrue(read.arrivals() && read.summary());

		final NbtCompound old = new NbtCompound();
		DisplayConfig.defaults(DisplayKind.CONCOURSE).writeNbt(old);
		old.remove("Arrivals");
		old.remove("Summary");
		final DisplayConfig legacy = DisplayConfig.readNbt(old, DisplayKind.CONCOURSE);
		assertFalse(legacy.arrivals() || legacy.summary(), "1.3 boards stay departures without summary");
	}
}
