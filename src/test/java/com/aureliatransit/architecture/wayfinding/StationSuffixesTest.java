package com.aureliatransit.architecture.wayfinding;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationSuffixesTest {

	private static final StationSuffixes.Kind AIRPORT = StationSuffixes.Kind.AIRPORT;

	private static StationSuffixes sample() {
		return StationSuffixes.EMPTY
				.set("Aurelia", AIRPORT, "", SuffixContext.bit(SuffixContext.SIGNS))
				.set("Harbour Gate", StationSuffixes.Kind.CUSTOM, "Ferry", SuffixContext.DEFAULT_MASK);
	}

	@Test
	void appliesPerContextOnAndOff() {
		final StationSuffixes set = sample();
		assertEquals("Aurelia Airport", set.apply("Aurelia", SuffixContext.SIGNS));
		assertEquals("Aurelia", set.apply("Aurelia", SuffixContext.DISPLAYS));
		assertEquals("Aurelia", set.apply("Aurelia", SuffixContext.ANNOUNCEMENTS));
		assertEquals("Harbour Gate Ferry", set.apply("Harbour Gate", SuffixContext.TERMINALS));
		assertEquals("Elsewhere", set.apply("Elsewhere", SuffixContext.SIGNS));
		assertEquals("", set.apply(null, SuffixContext.SIGNS));
	}

	@Test
	void neverDoublesTheSuffix() {
		final StationSuffixes set = sample();
		assertEquals("Aurelia Airport", set.apply("Aurelia Airport", SuffixContext.SIGNS));
		assertEquals("Aurelia AIRPORT", set.apply("Aurelia AIRPORT", SuffixContext.SIGNS));
	}

	@Test
	void lookupIgnoresCaseAndWhitespace() {
		final StationSuffixes set = sample();
		assertNotNull(set.find("  AURELIA "));
		assertNotNull(set.find("harbour   gate"));
		assertNull(set.find("Nowhere"));
		assertNull(set.find(null));
		assertNull(set.find("  "));
		assertEquals("harbour gate Ferry", set.apply("harbour   gate", SuffixContext.SIGNS).replaceAll(" +", " "));
	}

	@Test
	void manualEntriesAreReplacedNotDuplicated() {
		final StationSuffixes set = sample().set("  aurelia ", StationSuffixes.Kind.PORT, "", SuffixContext.DEFAULT_MASK);
		assertEquals(2, set.all().size());
		assertEquals("Port", set.find("Aurelia").suffix());
		assertEquals("Aurelia Port", set.apply("Aurelia", SuffixContext.TERMINALS));
	}

	@Test
	void customKindIsSanitisedAndCapped() {
		final StationSuffixes set = StationSuffixes.EMPTY.set("Stadium", StationSuffixes.Kind.CUSTOM, "y".repeat(StationSuffixes.MAX_SUFFIX + 20), SuffixContext.DEFAULT_MASK);
		assertEquals(StationSuffixes.MAX_SUFFIX, set.find("Stadium").suffix().length());
		// built-in kinds ignore the custom text
		assertEquals("Terminal", StationSuffixes.EMPTY.set("Stadium", StationSuffixes.Kind.TERMINAL, "ignored", 0).find("Stadium").suffix());
	}

	@Test
	void invalidEntriesAreRejected() {
		assertNull(StationSuffixes.EMPTY.set("", AIRPORT, "", SuffixContext.DEFAULT_MASK));
		assertNull(StationSuffixes.EMPTY.set("   ", AIRPORT, "", SuffixContext.DEFAULT_MASK));
		assertNull(StationSuffixes.EMPTY.set(null, AIRPORT, "", SuffixContext.DEFAULT_MASK));
		assertNull(StationSuffixes.EMPTY.set("Aurelia", StationSuffixes.Kind.CUSTOM, "", SuffixContext.DEFAULT_MASK));
		assertNull(StationSuffixes.EMPTY.set("Aurelia", StationSuffixes.Kind.CUSTOM, "   ", SuffixContext.DEFAULT_MASK));
	}

	@Test
	void entryCountIsBounded() {
		StationSuffixes set = StationSuffixes.EMPTY;
		for (int i = 0; i < StationSuffixes.MAX_ENTRIES; i++) {
			set = set.set("Station " + i, AIRPORT, "", SuffixContext.DEFAULT_MASK);
			assertNotNull(set);
		}
		assertEquals(StationSuffixes.MAX_ENTRIES, set.all().size());
		assertNull(set.set("One too many", AIRPORT, "", SuffixContext.DEFAULT_MASK));
		assertNotNull(set.set("Station 5", StationSuffixes.Kind.PORT, "", SuffixContext.DEFAULT_MASK), "replacing still works when full");
	}

	@Test
	void withContextTogglesOneContext() {
		final StationSuffixes set = sample();
		final StationSuffixes on = set.withContext("aurelia", SuffixContext.DISPLAYS, true);
		assertEquals("Aurelia Airport", on.apply("Aurelia", SuffixContext.DISPLAYS));
		assertEquals("Aurelia Airport", on.apply("Aurelia", SuffixContext.SIGNS));
		final StationSuffixes off = on.withContext("Aurelia", SuffixContext.SIGNS, false);
		assertEquals("Aurelia", off.apply("Aurelia", SuffixContext.SIGNS));
		assertEquals(AIRPORT, off.find("Aurelia").kind());
		assertNull(set.withContext("Nowhere", SuffixContext.SIGNS, true));
	}

	@Test
	void removeDropsTheEntry() {
		final StationSuffixes set = sample();
		final StationSuffixes removed = set.remove(" AURELIA");
		assertNull(removed.find("Aurelia"));
		assertEquals(1, removed.all().size());
		assertSame(removed, removed.remove("Aurelia"), "unknown station changes nothing");
		assertTrue(removed.remove("Harbour Gate").isEmpty());
	}

	@Test
	void nbtRoundTrip() {
		final StationSuffixes set = sample();
		assertEquals(set, StationSuffixes.fromNbt(set.toNbt()));
		assertTrue(StationSuffixes.fromNbt(StationSuffixes.EMPTY.toNbt()).isEmpty());
	}

	@Test
	void packetRoundTripAndBounds() {
		final StationSuffixes set = sample();
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		set.write(buf);
		assertEquals(set, StationSuffixes.read(buf));

		final PacketByteBuf tooMany = new PacketByteBuf(Unpooled.buffer());
		tooMany.writeVarInt(StationSuffixes.MAX_ENTRIES + 1);
		assertThrows(IllegalArgumentException.class, () -> StationSuffixes.read(tooMany));
		final PacketByteBuf negative = new PacketByteBuf(Unpooled.buffer());
		negative.writeVarInt(-1);
		assertThrows(IllegalArgumentException.class, () -> StationSuffixes.read(negative));
	}
}
