package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationAssociationMode;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WayfindingDataAssociationTest {

	private static final StationAssociation MANUAL = new StationAssociation(StationAssociationMode.MANUAL, 42L, List.of(7L, 8L));

	@Test
	void autoIsTheDefault() {
		assertSame(StationAssociation.AUTO, WayfindingData.EMPTY.association());
		assertTrue(WayfindingData.EMPTY.association().isAuto());
		assertEquals(BoardView.TRAINS_THIS_SIDE, WayfindingData.EMPTY.view());
	}

	@Test
	void oldDataWithoutAssociationReadsAsAuto() {
		final NbtCompound root = new NbtCompound();
		WayfindingData.EMPTY.writeNbt(root);
		root.getCompound(WayfindingData.NBT_KEY).remove("Association");
		root.getCompound(WayfindingData.NBT_KEY).remove("View");
		final WayfindingData read = WayfindingData.readNbt(root, WayfindingData.EMPTY);
		assertTrue(read.association().isAuto());
		assertEquals(BoardView.TRAINS_THIS_SIDE, read.view());
	}

	@Test
	void manualAssociationAndViewSurviveNbtAndPackets() {
		final WayfindingData data = WayfindingData.EMPTY.withAssociation(MANUAL).withView(BoardView.EXITS);
		final NbtCompound root = new NbtCompound();
		data.writeNbt(root);
		assertEquals(data, WayfindingData.readNbt(root, WayfindingData.EMPTY));
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		data.write(buf);
		final WayfindingData read = WayfindingData.read(buf);
		assertEquals(data, read);
		assertEquals(42L, read.association().stationId());
		assertEquals(List.of(7L, 8L), read.association().platformIds());
		assertEquals(BoardView.EXITS, read.view());
	}

	@Test
	void switchingBackToAutoDropsThePinnedStation() {
		final WayfindingData manual = WayfindingData.EMPTY.withAssociation(MANUAL);
		assertEquals(WayfindingData.EMPTY, manual.withAssociation(StationAssociation.AUTO));
	}

	@Test
	void unknownViewOrdinalFallsBack() {
		assertEquals(BoardView.TRAINS_THIS_SIDE, BoardView.byOrdinal(99));
		assertEquals(BoardView.TRAINS_THIS_SIDE, BoardView.byOrdinal(-1));
	}

	@Test
	void sourceReceivesTheBlocksAssociation() {
		final StationAssociation[] seen = new StationAssociation[1];
		Wayfinding.install((pos, auto, association) -> {
			seen[0] = association;
			return StationFacts.EMPTY;
		});
		try {
			Wayfinding.resolve(net.minecraft.util.math.BlockPos.ORIGIN, WayfindingData.EMPTY.withAssociation(MANUAL));
			assertEquals(MANUAL, seen[0]);
		} finally {
			Wayfinding.install(null);
		}
	}
}
