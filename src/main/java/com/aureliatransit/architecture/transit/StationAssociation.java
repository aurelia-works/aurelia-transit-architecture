package com.aureliatransit.architecture.transit;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-stored, client-resolved link between a block and MTR station data. Ids are MTR station/platform ids.
 * An empty {@code platformIds} list in MANUAL mode means "all platforms of the station".
 */
public record StationAssociation(StationAssociationMode mode, long stationId, List<Long> platformIds) {

	public static final int MAX_PLATFORMS = 8;
	public static final StationAssociation AUTO = new StationAssociation(StationAssociationMode.AUTO, 0, List.of());

	public StationAssociation {
		platformIds = List.copyOf(platformIds.size() > MAX_PLATFORMS ? platformIds.subList(0, MAX_PLATFORMS) : platformIds);
	}

	public boolean isAuto() {
		return mode == StationAssociationMode.AUTO;
	}

	public void writeNbt(NbtCompound nbt, String key) {
		final NbtCompound tag = new NbtCompound();
		tag.putInt("Mode", mode.ordinal());
		tag.putLong("Station", stationId);
		final NbtList list = new NbtList();
		platformIds.forEach(id -> list.add(NbtLong.of(id)));
		tag.put("Platforms", list);
		nbt.put(key, tag);
	}

	public static StationAssociation readNbt(NbtCompound nbt, String key) {
		if (!nbt.contains(key, NbtElement.COMPOUND_TYPE)) {
			return AUTO;
		}
		final NbtCompound tag = nbt.getCompound(key);
		final NbtList list = tag.getList("Platforms", NbtElement.LONG_TYPE);
		final List<Long> ids = new ArrayList<>();
		for (int i = 0; i < Math.min(MAX_PLATFORMS, list.size()); i++) {
			ids.add(((NbtLong) list.get(i)).longValue());
		}
		return new StationAssociation(StationAssociationMode.byOrdinal(tag.getInt("Mode")), tag.getLong("Station"), ids);
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(mode.ordinal());
		buf.writeLong(stationId);
		buf.writeVarInt(platformIds.size());
		platformIds.forEach(buf::writeLong);
	}

	/**
	 * Reads an association from an untrusted packet; counts are bounded before allocation.
	 */
	public static StationAssociation read(PacketByteBuf buf) {
		final StationAssociationMode mode = StationAssociationMode.byOrdinal(buf.readVarInt());
		final long stationId = buf.readLong();
		final int count = buf.readVarInt();
		if (count < 0 || count > MAX_PLATFORMS) {
			throw new IllegalArgumentException("Too many platform ids: " + count);
		}
		final List<Long> ids = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ids.add(buf.readLong());
		}
		return new StationAssociation(mode, stationId, ids);
	}
}
