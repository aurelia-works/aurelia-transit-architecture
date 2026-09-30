package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The complete, immutable set of service messages: one network-wide message and up to {@value #MAX_STATIONS}
 * per-station messages keyed by station display name (case-insensitive). Holds no MTR types, so the server can store
 * it; boards match by the station name they already display.
 *
 * <p>Display priority on a board: the display's own local message, then the message for its station, then the network
 * message ({@link #select}).
 */
public final class ServiceMessages {

	public static final int MAX_STATIONS = 16;
	public static final int MAX_STATION_NAME = 48;
	public static final ServiceMessages EMPTY = new ServiceMessages(ServiceMessage.NONE, Map.of());

	/** A station message together with the station name as typed by the operator. */
	public record StationMessage(String station, ServiceMessage message) {

		public StationMessage {
			station = TextSanitizer.sanitize(station, MAX_STATION_NAME);
		}
	}

	private final ServiceMessage network;
	private final Map<String, StationMessage> stations;

	private ServiceMessages(ServiceMessage network, Map<String, StationMessage> stations) {
		this.network = network;
		this.stations = stations;
	}

	/** Normalised lookup key of a station name: trimmed, whitespace collapsed, lower case. */
	public static String key(String stationName) {
		return TextSanitizer.sanitize(stationName, MAX_STATION_NAME).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	public ServiceMessage network() {
		return network;
	}

	/** Station messages sorted by key, at most {@value #MAX_STATIONS}. */
	public List<StationMessage> stations() {
		return List.copyOf(stations.values());
	}

	public ServiceMessage forStation(String stationName) {
		if (stations.isEmpty() || stationName == null) {
			return ServiceMessage.NONE;
		}
		final StationMessage found = stations.get(key(stationName));
		return found == null ? ServiceMessage.NONE : found.message();
	}

	public boolean isEmpty() {
		return network.isEmpty() && stations.isEmpty();
	}

	public ServiceMessages withNetwork(ServiceMessage message) {
		return new ServiceMessages(message == null || message.isEmpty() ? ServiceMessage.NONE : message, stations);
	}

	/**
	 * Sets (or, with an empty message, clears) a station message. Returns null when the station is new and the limit of
	 * {@value #MAX_STATIONS} stations is reached, or the name is empty.
	 */
	public ServiceMessages withStation(String stationName, ServiceMessage message) {
		final String key = key(stationName);
		if (key.isEmpty()) {
			return null;
		}
		final Map<String, StationMessage> next = new TreeMap<>(stations);
		if (message == null || message.isEmpty()) {
			next.remove(key);
		} else if (next.containsKey(key) || next.size() < MAX_STATIONS) {
			next.put(key, new StationMessage(stationName, message));
		} else {
			return null;
		}
		return new ServiceMessages(network, next);
	}

	/**
	 * The message a board shows: its own local text (as an INFO message) first, then its station's, then the network's;
	 * {@link ServiceMessage#NONE} when there is none.
	 */
	public ServiceMessage select(String local, String stationName) {
		final ServiceMessage own = new ServiceMessage(local, MessageSeverity.INFO);
		if (!own.isEmpty()) {
			return own;
		}
		final ServiceMessage station = forStation(stationName);
		return !station.isEmpty() ? station : network;
	}

	// ---- NBT -----------------------------------------------------------------------------------------------------

	public NbtCompound toNbt() {
		final NbtCompound tag = new NbtCompound();
		tag.put("Network", network.toNbt());
		final NbtList list = new NbtList();
		for (final StationMessage entry : stations.values()) {
			final NbtCompound item = entry.message().toNbt();
			item.putString("Station", entry.station());
			list.add(item);
		}
		tag.put("Stations", list);
		return tag;
	}

	public static ServiceMessages fromNbt(NbtCompound tag) {
		ServiceMessages result = EMPTY.withNetwork(ServiceMessage.fromNbt(tag.getCompound("Network")));
		final NbtList list = tag.getList("Stations", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < Math.min(list.size(), MAX_STATIONS); i++) {
			final NbtCompound item = list.getCompound(i);
			final ServiceMessages next = result.withStation(item.getString("Station"), ServiceMessage.fromNbt(item));
			if (next != null) {
				result = next;
			}
		}
		return result;
	}

	// ---- packets -------------------------------------------------------------------------------------------------

	public void write(PacketByteBuf buf) {
		network.write(buf);
		buf.writeVarInt(stations.size());
		for (final StationMessage entry : stations.values()) {
			buf.writeString(entry.station(), MAX_STATION_NAME * 4);
			entry.message().write(buf);
		}
	}

	/**
	 * Reads from an untrusted packet: strings are length-capped and the count is checked before anything is allocated.
	 */
	public static ServiceMessages read(PacketByteBuf buf) {
		ServiceMessages result = EMPTY.withNetwork(ServiceMessage.read(buf));
		final int count = buf.readVarInt();
		if (count < 0 || count > MAX_STATIONS) {
			throw new IllegalArgumentException("Too many station messages: " + count);
		}
		final List<StationMessage> read = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			final String station = buf.readString(MAX_STATION_NAME * 4);
			read.add(new StationMessage(station, ServiceMessage.read(buf)));
		}
		for (final StationMessage entry : read) {
			final ServiceMessages next = result.withStation(entry.station(), entry.message());
			if (next != null) {
				result = next;
			}
		}
		return result;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ServiceMessages that && network.equals(that.network) && stations.equals(that.stations);
	}

	@Override
	public int hashCode() {
		return 31 * network.hashCode() + stations.hashCode();
	}
}
