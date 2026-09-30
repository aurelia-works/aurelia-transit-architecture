package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * A line identity: short label ("L", "BSL", "15", "T1") on a coloured badge. Colours are plain 24-bit RGB because line
 * colours come from MTR routes (any colour) or must match a network's own palette; the value is masked, never trusted.
 */
public record LineBadge(String label, int rgb, BadgeShape shape) {

	public static final int MAX_LABEL = 4;

	public LineBadge {
		label = TextSanitizer.sanitize(label, MAX_LABEL);
		rgb &= 0xFFFFFF;
		shape = shape == null ? BadgeShape.ROUNDED : shape;
	}

	public boolean isEmpty() {
		return label.isEmpty();
	}

	public int argb() {
		return 0xFF000000 | rgb;
	}

	public NbtCompound toNbt() {
		final NbtCompound tag = new NbtCompound();
		tag.putString("Label", label);
		tag.putInt("Color", rgb);
		tag.putInt("Shape", shape.ordinal());
		return tag;
	}

	public static LineBadge fromNbt(NbtCompound tag) {
		return new LineBadge(tag.getString("Label"), tag.getInt("Color"), BadgeShape.byOrdinal(tag.getInt("Shape")));
	}

	public void write(PacketByteBuf buf) {
		buf.writeString(label, MAX_LABEL * 4);
		buf.writeInt(rgb);
		buf.writeVarInt(shape.ordinal());
	}

	public static LineBadge read(PacketByteBuf buf) {
		return new LineBadge(buf.readString(MAX_LABEL * 4), buf.readInt(), BadgeShape.byOrdinal(buf.readVarInt()));
	}
}
