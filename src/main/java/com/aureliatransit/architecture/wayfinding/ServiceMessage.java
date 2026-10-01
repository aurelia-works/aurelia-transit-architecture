package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * One service message: a stable id (assigned by {@link ServiceMessages}; 0 for display-local or unassigned messages), a
 * scope, the station it targets (STATION scope only), a severity and sanitised text of at most {@value #MAX_TEXT}
 * characters. Empty text means "no message".
 */
public record ServiceMessage(int id, MessageScope scope, String station, MessageSeverity severity, String text) {

	public static final int MAX_TEXT = 96;
	public static final int MAX_STATION_NAME = 48;
	public static final ServiceMessage NONE = new ServiceMessage("", MessageSeverity.INFO);

	public ServiceMessage {
		scope = scope == null ? MessageScope.NETWORK : scope;
		station = scope == MessageScope.STATION ? TextSanitizer.sanitize(station, MAX_STATION_NAME) : "";
		text = TextSanitizer.sanitize(text, MAX_TEXT);
		severity = severity == null ? MessageSeverity.INFO : severity;
	}

	/** An unassigned network-scope message. */
	public ServiceMessage(String text, MessageSeverity severity) {
		this(0, MessageScope.NETWORK, "", severity, text);
	}

	/** A display's own message (DISPLAY scope, INFO). */
	public static ServiceMessage display(String text) {
		return new ServiceMessage(0, MessageScope.DISPLAY, "", MessageSeverity.INFO, text);
	}

	public boolean isEmpty() {
		return text.isEmpty();
	}

	/** Normalised lookup key of a station name: trimmed, whitespace collapsed, lower case. */
	public String stationKey() {
		return ServiceMessages.key(station);
	}

	ServiceMessage withId(int newId) {
		return new ServiceMessage(newId, scope, station, severity, text);
	}

	public NbtCompound toNbt() {
		final NbtCompound tag = new NbtCompound();
		tag.putInt("Id", id);
		tag.putInt("Scope", scope.ordinal());
		tag.putString("Station", station);
		tag.putString("Text", text);
		tag.putInt("Severity", severity.ordinal());
		return tag;
	}

	public static ServiceMessage fromNbt(NbtCompound tag) {
		return new ServiceMessage(tag.getInt("Id"), MessageScope.byOrdinal(tag.getInt("Scope")), tag.getString("Station"),
				MessageSeverity.byOrdinal(tag.getInt("Severity")), tag.getString("Text"));
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(id);
		buf.writeVarInt(scope.ordinal());
		buf.writeVarInt(severity.ordinal());
		buf.writeString(station, MAX_STATION_NAME * 4);
		buf.writeString(text, MAX_TEXT * 4);
	}

	public static ServiceMessage read(PacketByteBuf buf) {
		final int id = buf.readVarInt();
		final MessageScope scope = MessageScope.byOrdinal(buf.readVarInt());
		final MessageSeverity severity = MessageSeverity.byOrdinal(buf.readVarInt());
		final String station = buf.readString(MAX_STATION_NAME * 4);
		return new ServiceMessage(id, scope, station, severity, buf.readString(MAX_TEXT * 4));
	}
}
