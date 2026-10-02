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
 * @param callingTimes show calling-point times where MTR supplies them (1.4, A14; off by default: it widens the arrivals
 *                     request by the calling points' platforms)
 * @param arrivals     concourse boards: list arrivals ("from ...") instead of departures (1.4)
 * @param summary      concourse boards: station summary line (platform count, the platform beside the board) (1.4)
 */
public record DisplayConfig(StationAssociation association, DisplayStyle style, int rows, boolean clock, boolean callingAt, int pageSeconds,
                            BoardAlignment alignment, String message, boolean callingTimes, boolean arrivals, boolean summary) {

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

	/** 1.3 shape: no calling times. */
	public DisplayConfig(StationAssociation association, DisplayStyle style, int rows, boolean clock, boolean callingAt, int pageSeconds,
						 BoardAlignment alignment, String message) {
		this(association, style, rows, clock, callingAt, pageSeconds, alignment, message, false, false, false);
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
		return clamped == rows ? this : new DisplayConfig(association, style, clamped, clock, callingAt, pageSeconds, alignment, message, callingTimes, arrivals, summary);
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
		nbt.putBoolean("CallingTimes", callingTimes);
		nbt.putBoolean("Arrivals", arrivals);
		nbt.putBoolean("Summary", summary);
	}

	public static DisplayConfig readNbt(NbtCompound nbt, DisplayKind kind) {
		if (!nbt.contains("Rows", NbtElement.INT_TYPE)) {
			return defaults(kind);
		}
		return new DisplayConfig(StationAssociation.readNbt(nbt, "Association"), DisplayStyle.byOrdinal(nbt.getInt("Style")), nbt.getInt("Rows"),
				nbt.getBoolean("Clock"), nbt.getBoolean("CallingAt"), nbt.getInt("PageSeconds"), BoardAlignment.byOrdinal(nbt.getInt("Align")),
				nbt.getString("Message"), nbt.getBoolean("CallingTimes"), nbt.getBoolean("Arrivals"), nbt.getBoolean("Summary")).forKind(kind);
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
		buf.writeBoolean(callingTimes);
		buf.writeBoolean(arrivals);
		buf.writeBoolean(summary);
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
				buf.readString(MAX_MESSAGE * 4), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
	}
}
