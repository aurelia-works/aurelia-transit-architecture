package com.aureliatransit.architecture.text;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared editable text model for information cases, pillars and timetable cases: a heading, body rows, an alignment
 * and a palette accent. Every constructor path sanitizes and bounds its input.
 */
public record ConfigurableTextData(String heading, List<String> body, TextAlignment alignment, AccentPalette accent) {

	public static final int MAX_HEADING = 40;
	public static final int MAX_LINE = 48;
	public static final int MAX_BODY_LINES = 8;
	public static final ConfigurableTextData EMPTY = new ConfigurableTextData("", List.of(), TextAlignment.LEFT, AccentPalette.BLUE);

	public ConfigurableTextData {
		heading = TextSanitizer.sanitize(heading, MAX_HEADING);
		final List<String> clean = new ArrayList<>();
		for (int i = 0; i < Math.min(MAX_BODY_LINES, body.size()); i++) {
			clean.add(TextSanitizer.sanitize(body.get(i), MAX_LINE));
		}
		// drop trailing empty rows so equal content compares equal
		while (!clean.isEmpty() && clean.get(clean.size() - 1).isEmpty()) {
			clean.remove(clean.size() - 1);
		}
		body = List.copyOf(clean);
		alignment = alignment == null ? TextAlignment.LEFT : alignment;
		accent = accent == null ? AccentPalette.NONE : accent;
	}

	public boolean isEmpty() {
		return heading.isEmpty() && body.isEmpty();
	}

	public void writeNbt(NbtCompound nbt, String key) {
		final NbtCompound tag = new NbtCompound();
		tag.putString("Heading", heading);
		final NbtList list = new NbtList();
		body.forEach(line -> list.add(NbtString.of(line)));
		tag.put("Body", list);
		tag.putInt("Align", alignment.ordinal());
		tag.putInt("Accent", accent.ordinal());
		nbt.put(key, tag);
	}

	public static ConfigurableTextData readNbt(NbtCompound nbt, String key) {
		if (!nbt.contains(key, NbtElement.COMPOUND_TYPE)) {
			return EMPTY;
		}
		final NbtCompound tag = nbt.getCompound(key);
		final NbtList list = tag.getList("Body", NbtElement.STRING_TYPE);
		final List<String> body = new ArrayList<>();
		for (int i = 0; i < Math.min(MAX_BODY_LINES, list.size()); i++) {
			body.add(list.getString(i));
		}
		return new ConfigurableTextData(tag.getString("Heading"), body, TextAlignment.byOrdinal(tag.getInt("Align")), AccentPalette.byOrdinal(tag.getInt("Accent")));
	}

	public void write(PacketByteBuf buf) {
		buf.writeString(heading, MAX_HEADING * 4);
		buf.writeVarInt(body.size());
		body.forEach(line -> buf.writeString(line, MAX_LINE * 4));
		buf.writeVarInt(alignment.ordinal());
		buf.writeVarInt(accent.ordinal());
	}

	/**
	 * Reads from an untrusted packet: string byte lengths and the row count are bounded before allocation.
	 */
	public static ConfigurableTextData read(PacketByteBuf buf) {
		final String heading = buf.readString(MAX_HEADING * 4);
		final int count = buf.readVarInt();
		if (count < 0 || count > MAX_BODY_LINES) {
			throw new IllegalArgumentException("Too many body lines: " + count);
		}
		final List<String> body = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			body.add(buf.readString(MAX_LINE * 4));
		}
		return new ConfigurableTextData(heading, body, TextAlignment.byOrdinal(buf.readVarInt()), AccentPalette.byOrdinal(buf.readVarInt()));
	}
}
