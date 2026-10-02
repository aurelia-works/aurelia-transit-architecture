package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Station suffix settings (A5): one entry per station name ("Aurelia" + "Airport"), each shown only in the
 * {@link SuffixContext}s the operator turned on. MTR stores no suffix, so MTR's own name stays short (route maps, MTR
 * screens) and ATA adds the suffix where wanted. Immutable and bounded; keyed like service messages
 * ({@link ServiceMessages#key}), so it never needs MTR types and the server can store it.
 *
 * <p>Applied to MTR station names only: a manual name typed on a sign is shown exactly as typed.
 */
public final class StationSuffixes {

	public static final int MAX_ENTRIES = 128;
	public static final int MAX_SUFFIX = 16;
	public static final StationSuffixes EMPTY = new StationSuffixes(List.of());

	/** Suffix kinds with their English text; CUSTOM uses the entry's own text. */
	public enum Kind {
		STATION("Station"), AIRPORT("Airport"), PORT("Port"), TERMINAL("Terminal"), CUSTOM("");

		private final String text;

		Kind(String text) {
			this.text = text;
		}

		public String text() {
			return text;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STATION;
		}
	}

	/**
	 * @param station  station name as typed (display), {@link #key} for lookups
	 * @param suffix   the text appended ("Airport"); for built-in kinds the kind's text
	 * @param contexts bit mask of {@link SuffixContext#bit}
	 */
	public record Entry(String station, Kind kind, String suffix, int contexts) {

		public Entry {
			station = TextSanitizer.sanitize(station, ServiceMessages.MAX_STATION_NAME);
			kind = kind == null ? Kind.STATION : kind;
			suffix = kind == Kind.CUSTOM ? TextSanitizer.sanitize(suffix, MAX_SUFFIX) : kind.text();
			contexts &= (1 << SuffixContext.values().length) - 1;
		}

		public String key() {
			return ServiceMessages.key(station);
		}

		public boolean shows(SuffixContext context) {
			return (contexts & SuffixContext.bit(context)) != 0;
		}

		boolean valid() {
			return !key().isEmpty() && !suffix.isEmpty();
		}
	}

	private final List<Entry> entries;

	private StationSuffixes(List<Entry> entries) {
		this.entries = entries;
	}

	public List<Entry> all() {
		return entries;
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public Entry find(String stationName) {
		final String key = stationName == null ? "" : ServiceMessages.key(stationName);
		if (key.isEmpty()) {
			return null;
		}
		for (final Entry entry : entries) {
			if (entry.key().equals(key)) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * The name to show in {@code context}: the name plus its suffix when the station has one for that context, else the
	 * name unchanged. A name that already ends with the suffix ("Aurelia Airport") is not doubled.
	 */
	public String apply(String stationName, SuffixContext context) {
		if (stationName == null || stationName.isEmpty() || entries.isEmpty()) {
			return stationName == null ? "" : stationName;
		}
		final Entry entry = find(stationName);
		if (entry == null || !entry.shows(context)) {
			return stationName;
		}
		final String trimmed = stationName.trim();
		if (trimmed.toLowerCase(Locale.ROOT).endsWith(" " + entry.suffix().toLowerCase(Locale.ROOT))) {
			return stationName;
		}
		return trimmed + " " + entry.suffix();
	}

	/** Adds or replaces the station's entry; null when invalid (empty name or suffix) or the list is full. */
	public StationSuffixes set(String station, Kind kind, String customText, int contexts) {
		final Entry entry = new Entry(station, kind, customText, contexts);
		if (!entry.valid()) {
			return null;
		}
		final List<Entry> next = new ArrayList<>(entries.size() + 1);
		boolean replaced = false;
		for (final Entry existing : entries) {
			if (existing.key().equals(entry.key())) {
				next.add(entry);
				replaced = true;
			} else {
				next.add(existing);
			}
		}
		if (!replaced) {
			if (entries.size() >= MAX_ENTRIES) {
				return null;
			}
			next.add(entry);
		}
		return new StationSuffixes(List.copyOf(next));
	}

	/** Changes one context of an existing entry; null when the station has no entry. */
	public StationSuffixes withContext(String station, SuffixContext context, boolean on) {
		final Entry entry = find(station);
		if (entry == null) {
			return null;
		}
		final int mask = on ? entry.contexts() | SuffixContext.bit(context) : entry.contexts() & ~SuffixContext.bit(context);
		return set(entry.station(), entry.kind(), entry.suffix(), mask);
	}

	public StationSuffixes remove(String station) {
		final String key = ServiceMessages.key(station);
		final List<Entry> next = new ArrayList<>(entries.size());
		for (final Entry entry : entries) {
			if (!entry.key().equals(key)) {
				next.add(entry);
			}
		}
		return next.size() == entries.size() ? this : new StationSuffixes(List.copyOf(next));
	}

	// ---- persistence and sync ----------------------------------------------------------------------------------------

	public NbtCompound toNbt() {
		final NbtCompound tag = new NbtCompound();
		final NbtList list = new NbtList();
		for (final Entry entry : entries) {
			final NbtCompound item = new NbtCompound();
			item.putString("Station", entry.station());
			item.putInt("Kind", entry.kind().ordinal());
			item.putString("Suffix", entry.suffix());
			item.putInt("Contexts", entry.contexts());
			list.add(item);
		}
		tag.put("Suffixes", list);
		return tag;
	}

	public static StationSuffixes fromNbt(NbtCompound tag) {
		StationSuffixes result = EMPTY;
		final NbtList list = tag.getList("Suffixes", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < Math.min(list.size(), MAX_ENTRIES); i++) {
			final NbtCompound item = list.getCompound(i);
			final StationSuffixes next = result.set(item.getString("Station"), Kind.byOrdinal(item.getInt("Kind")), item.getString("Suffix"), item.getInt("Contexts"));
			result = next == null ? result : next;
		}
		return result;
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (final Entry entry : entries) {
			buf.writeString(entry.station(), ServiceMessages.MAX_STATION_NAME * 4);
			buf.writeVarInt(entry.kind().ordinal());
			buf.writeString(entry.suffix(), MAX_SUFFIX * 4);
			buf.writeVarInt(entry.contexts());
		}
	}

	/** Reads from an untrusted packet: the count is checked first, strings are capped, invalid entries dropped. */
	public static StationSuffixes read(PacketByteBuf buf) {
		final int count = buf.readVarInt();
		if (count < 0 || count > MAX_ENTRIES) {
			throw new IllegalArgumentException("Too many station suffixes: " + count);
		}
		StationSuffixes result = EMPTY;
		for (int i = 0; i < count; i++) {
			final StationSuffixes next = result.set(buf.readString(ServiceMessages.MAX_STATION_NAME * 4), Kind.byOrdinal(buf.readVarInt()),
					buf.readString(MAX_SUFFIX * 4), buf.readVarInt());
			result = next == null ? result : next;
		}
		return result;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof StationSuffixes other && other.entries.equals(entries);
	}

	@Override
	public int hashCode() {
		return entries.hashCode();
	}
}
