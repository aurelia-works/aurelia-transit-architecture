package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.text.TextSanitizer;
import com.aureliatransit.architecture.transit.StationAssociation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;

/**
 * Server-stored configuration of one live display (the owner block of a joined group).
 *
 * @param pageSeconds seconds each page (departures on a concourse board, calling-at stops) is shown
 * @param alignment   vertical anchoring of the content (1.2; boards saved by 1.1 load as {@link BoardAlignment#TOP})
 * @param message     local service message of this display (1.2; empty = none), shown by the live-information layer
 */
public record DisplayConfig(StationAssociation association, DisplayStyle style, int rows, boolean clock, boolean callingAt, int pageSeconds,
                            BoardAlignment alignment, String message) {

	public static final int MIN_PAGE_SECONDS = 3;
	public static final int MAX_PAGE_SECONDS = 20;
	public static final int DEFAULT_PAGE_SECONDS = 6;
	public static final int MAX_MESSAGE = 64;

	public DisplayConfig {
		association = association == null ? StationAssociation.AUTO : association;
		style = style == null ? DisplayStyle.EUROPEAN_MODERN : style;
		pageSeconds = Math.max(MIN_PAGE_SECONDS, Math.min(MAX_PAGE_SECONDS, pageSeconds));
		alignment = alignment == null ? BoardAlignment.TOP : alignment;
		message = TextSanitizer.sanitize(message, MAX_MESSAGE);
	}

	/**
	 * 1.1 shape: top-aligned, no message.
	 */
	public DisplayConfig(StationAssociation association, DisplayStyle style, int rows, boolean clock, boolean callingAt, int pageSeconds) {
		this(association, style, rows, clock, callingAt, pageSeconds, BoardAlignment.TOP, "");
	}

	public static DisplayConfig defaults(DisplayKind kind) {
		return new DisplayConfig(StationAssociation.AUTO, DisplayStyle.EUROPEAN_MODERN, kind.defaultRows(), kind == DisplayKind.CONCOURSE, kind == DisplayKind.CIS, DEFAULT_PAGE_SECONDS);
	}

	/**
	 * Clamps values that depend on the display kind (row count).
	 */
	public DisplayConfig forKind(DisplayKind kind) {
		final int clamped = kind.clampRows(rows);
		return clamped == rows ? this : new DisplayConfig(association, style, clamped, clock, callingAt, pageSeconds, alignment, message);
	}

	public void writeNbt(NbtCompound nbt) {
		association.writeNbt(nbt, "Association");
		nbt.putInt("Style", style.ordinal());
		nbt.putInt("Rows", rows);
		nbt.putBoolean("Clock", clock);
		nbt.putBoolean("CallingAt", callingAt);
		nbt.putInt("PageSeconds", pageSeconds);
		nbt.putInt("Align", alignment.ordinal());
		nbt.putString("Message", message);
	}

	public static DisplayConfig readNbt(NbtCompound nbt, DisplayKind kind) {
		if (!nbt.contains("Rows", NbtElement.INT_TYPE)) {
			return defaults(kind);
		}
		return new DisplayConfig(StationAssociation.readNbt(nbt, "Association"), DisplayStyle.byOrdinal(nbt.getInt("Style")), nbt.getInt("Rows"),
				nbt.getBoolean("Clock"), nbt.getBoolean("CallingAt"), nbt.getInt("PageSeconds"), BoardAlignment.byOrdinal(nbt.getInt("Align")),
				nbt.getString("Message")).forKind(kind);
	}

	public void write(PacketByteBuf buf) {
		association.write(buf);
		buf.writeVarInt(style.ordinal());
		buf.writeVarInt(rows);
		buf.writeBoolean(clock);
		buf.writeBoolean(callingAt);
		buf.writeVarInt(pageSeconds);
		buf.writeVarInt(alignment.ordinal());
		buf.writeString(message, MAX_MESSAGE * 4);
	}

	/**
	 * Reads from an untrusted packet. Every value is clamped or bounded; rows are finally clamped by {@link #forKind}.
	 */
	public static DisplayConfig read(PacketByteBuf buf) {
		final StationAssociation association = StationAssociation.read(buf);
		final DisplayStyle style = DisplayStyle.byOrdinal(buf.readVarInt());
		final int rows = buf.readVarInt();
		final boolean clock = buf.readBoolean();
		final boolean callingAt = buf.readBoolean();
		final int pageSeconds = buf.readVarInt();
		return new DisplayConfig(association, style, Math.max(0, Math.min(64, rows)), clock, callingAt, pageSeconds, BoardAlignment.byOrdinal(buf.readVarInt()),
				buf.readString(MAX_MESSAGE * 4));
	}
}
