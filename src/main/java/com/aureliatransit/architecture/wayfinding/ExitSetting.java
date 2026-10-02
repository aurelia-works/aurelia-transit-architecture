package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Per-exit configuration of a multi-exit sign (A3): one exit, by label, set independently of the others. Matched to
 * MTR's exit of the same label (case-insensitive); a label MTR does not have adds a manual exit.
 *
 * @param label  exit label ("A", "B2")
 * @param text   "leads to" text shown instead of MTR's destinations; empty keeps MTR's
 * @param arrow  direction arrow drawn beside this exit only
 * @param hidden leave this exit off the sign
 */
public record ExitSetting(String label, String text, SignArrow arrow, boolean hidden) {

	public static final int MAX_TEXT = 32;

	public ExitSetting {
		label = TextSanitizer.sanitize(label, ExitInfo.MAX_LABEL);
		text = TextSanitizer.sanitize(text, MAX_TEXT);
		arrow = arrow == null ? SignArrow.NONE : arrow;
	}

	public boolean isEmpty() {
		return label.isEmpty();
	}

	/** True when it changes nothing (no text, no arrow, shown): such rows are not stored. */
	public boolean isDefault() {
		return text.isEmpty() && arrow == SignArrow.NONE && !hidden;
	}

	public NbtCompound toNbt() {
		final NbtCompound tag = new NbtCompound();
		tag.putString("Label", label);
		tag.putString("Text", text);
		tag.putInt("Arrow", arrow.ordinal());
		tag.putBoolean("Hidden", hidden);
		return tag;
	}

	public static ExitSetting fromNbt(NbtCompound tag) {
		return new ExitSetting(tag.getString("Label"), tag.getString("Text"), SignArrow.byOrdinal(tag.getInt("Arrow")), tag.getBoolean("Hidden"));
	}

	public void write(PacketByteBuf buf) {
		buf.writeString(label, ExitInfo.MAX_LABEL * 4);
		buf.writeString(text, MAX_TEXT * 4);
		buf.writeVarInt(arrow.ordinal());
		buf.writeBoolean(hidden);
	}

	public static ExitSetting read(PacketByteBuf buf) {
		return new ExitSetting(buf.readString(ExitInfo.MAX_LABEL * 4), buf.readString(MAX_TEXT * 4), SignArrow.byOrdinal(buf.readVarInt()), buf.readBoolean());
	}
}
