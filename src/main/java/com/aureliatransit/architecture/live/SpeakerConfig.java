package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import com.aureliatransit.architecture.transit.StationAssociation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;

/**
 * Server-stored configuration of a speaker.
 *
 * @param radius     hearing radius in blocks
 * @param volume     percent, 10..100
 * @param categories bit mask of enabled {@link AnnouncementCategory}s
 */
public record SpeakerConfig(StationAssociation association, int radius, int volume, int categories) {

	public static final int MIN_RADIUS = 4;
	public static final int MAX_RADIUS = 32;
	public static final int MIN_VOLUME = 10;
	public static final int MAX_VOLUME = 100;
	public static final SpeakerConfig DEFAULT = new SpeakerConfig(StationAssociation.AUTO, 16, 80, AnnouncementCategory.ALL_MASK);

	public SpeakerConfig {
		association = association == null ? StationAssociation.AUTO : association;
		radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
		volume = Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, volume));
		categories &= AnnouncementCategory.ALL_MASK;
	}

	public SpeakerConfig withCategoryToggled(AnnouncementCategory category) {
		return new SpeakerConfig(association, radius, volume, categories ^ category.bit());
	}

	public void writeNbt(NbtCompound nbt) {
		association.writeNbt(nbt, "Association");
		nbt.putInt("Radius", radius);
		nbt.putInt("Volume", volume);
		nbt.putInt("Categories", categories);
	}

	public static SpeakerConfig readNbt(NbtCompound nbt) {
		if (!nbt.contains("Radius", NbtElement.INT_TYPE)) {
			return DEFAULT;
		}
		return new SpeakerConfig(StationAssociation.readNbt(nbt, "Association"), nbt.getInt("Radius"), nbt.getInt("Volume"), nbt.getInt("Categories"));
	}

	public void write(PacketByteBuf buf) {
		association.write(buf);
		buf.writeVarInt(radius);
		buf.writeVarInt(volume);
		buf.writeVarInt(categories);
	}

	/**
	 * Reads from an untrusted packet; all values are clamped by the constructor.
	 */
	public static SpeakerConfig read(PacketByteBuf buf) {
		return new SpeakerConfig(StationAssociation.read(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}
}
