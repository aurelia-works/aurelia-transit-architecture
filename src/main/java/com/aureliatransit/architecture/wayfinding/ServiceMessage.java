package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * One service message: sanitised text (at most {@value #MAX_TEXT} characters) and a severity. Empty text means "no
 * message".
 */
public record ServiceMessage(String text, MessageSeverity severity) {

	public static final int MAX_TEXT = 96;
	public static final ServiceMessage NONE = new ServiceMessage("", MessageSeverity.INFO);

	public ServiceMessage {
		text = TextSanitizer.sanitize(text, MAX_TEXT);
		severity = severity == null ? MessageSeverity.INFO : severity;
	}

	public boolean isEmpty() {
		return text.isEmpty();
	}

	public NbtCompound toNbt() {
		final NbtCompound tag = new NbtCompound();
		tag.putString("Text", text);
		tag.putInt("Severity", severity.ordinal());
		return tag;
	}

	public static ServiceMessage fromNbt(NbtCompound tag) {
		return new ServiceMessage(tag.getString("Text"), MessageSeverity.byOrdinal(tag.getInt("Severity")));
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(severity.ordinal());
		buf.writeString(text, MAX_TEXT * 4);
	}

	public static ServiceMessage read(PacketByteBuf buf) {
		final MessageSeverity severity = MessageSeverity.byOrdinal(buf.readVarInt());
		return new ServiceMessage(buf.readString(MAX_TEXT * 4), severity);
	}
}
