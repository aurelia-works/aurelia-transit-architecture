package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The complete, immutable list of active service messages: any number of NETWORK and STATION messages (at most
 * {@value #MAX_MESSAGES} in total), each with a stable id that is never reused. Holds no MTR types, so the server can
 * store it; boards match STATION messages by the station name they already display.
 *
 * <p>{@link #applicable} gives the deterministic order a board cycles through: the display's own message, then station
 * messages, then network messages; within a group the most severe first, then by id.
 */
public final class ServiceMessages {

	public static final int MAX_MESSAGES = 32;
	public static final int MAX_STATION_NAME = ServiceMessage.MAX_STATION_NAME;
	public static final ServiceMessages EMPTY = new ServiceMessages(List.of(), 1);

	/** Display order: scope group, severity descending, then id ascending. */
	public static final Comparator<ServiceMessage> ORDER = Comparator
			.<ServiceMessage, Integer>comparing(m -> m.scope().ordinal())
			.thenComparing(m -> -m.severity().ordinal())
			.thenComparingInt(ServiceMessage::id);

	private final List<ServiceMessage> messages;
	private final int nextId;

	private ServiceMessages(List<ServiceMessage> messages, int nextId) {
		this.messages = messages;
		this.nextId = nextId;
	}

	/** Normalised lookup key of a station name: trimmed, whitespace collapsed, lower case. */
	public static String key(String stationName) {
		return TextSanitizer.sanitize(stationName, MAX_STATION_NAME).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/**
	 * The station name a block's messages are looked up by. Messages are added against MTR's station name (the name the
	 * PIDS use), so a block that resolves an MTR station uses that name even when a manual name override is shown; the
	 * manual name is the key only when no MTR station resolves.
	 *
	 * @param mtrStationName display name of the MTR station the block resolves, or empty/null when none
	 * @param manualName     the name the block shows (manual override or MTR), may be empty
	 */
	public static String messageStation(String mtrStationName, String manualName) {
		if (mtrStationName != null && !mtrStationName.isBlank()) {
			return mtrStationName;
		}
		return manualName == null ? "" : manualName;
	}

	/** Every stored message in id order. */
	public List<ServiceMessage> all() {
		return messages;
	}

	public int size() {
		return messages.size();
	}

	public boolean isEmpty() {
		return messages.isEmpty();
	}

	public int nextId() {
		return nextId;
	}

	public ServiceMessage byId(int id) {
		for (final ServiceMessage message : messages) {
			if (message.id() == id) {
				return message;
			}
		}
		return null;
	}

	/**
	 * Adds a message and assigns it the next id. Returns null when the text is empty, a STATION message has no station
	 * name, the scope is DISPLAY (display messages live in the display's config), or the list is full.
	 */
	public ServiceMessages add(MessageScope scope, String station, MessageSeverity severity, String text) {
		if (scope == MessageScope.DISPLAY || messages.size() >= MAX_MESSAGES) {
			return null;
		}
		final ServiceMessage message = new ServiceMessage(nextId, scope, station, severity, text);
		if (message.isEmpty() || scope == MessageScope.STATION && message.stationKey().isEmpty()) {
			return null;
		}
		final List<ServiceMessage> next = new ArrayList<>(messages);
		next.add(message);
		return new ServiceMessages(List.copyOf(next), nextId + 1);
	}

	/** Removes one message by id; returns this when there is no such id. */
	public ServiceMessages remove(int id) {
		if (byId(id) == null) {
			return this;
		}
		final List<ServiceMessage> next = new ArrayList<>(messages);
		next.removeIf(m -> m.id() == id);
		return new ServiceMessages(List.copyOf(next), nextId);
	}

	/** Removes all network messages. */
	public ServiceMessages clearNetwork() {
		return removeIf(m -> m.scope() == MessageScope.NETWORK);
	}

	/** Removes all messages of one station (name matched case-insensitively). */
	public ServiceMessages clearStation(String stationName) {
		final String key = key(stationName);
		return removeIf(m -> m.scope() == MessageScope.STATION && m.stationKey().equals(key));
	}

	public ServiceMessages clearAll() {
		return messages.isEmpty() ? this : new ServiceMessages(List.of(), nextId);
	}

	private ServiceMessages removeIf(java.util.function.Predicate<ServiceMessage> predicate) {
		final List<ServiceMessage> next = new ArrayList<>(messages);
		return next.removeIf(predicate) ? new ServiceMessages(List.copyOf(next), nextId) : this;
	}

	/**
	 * Messages that apply to a board: its own text (DISPLAY scope) first, then the messages for its station, then the
	 * network messages, in {@link #ORDER}. Deterministic; empty when there is nothing to show.
	 *
	 * @param local       the display's own message text, may be empty
	 * @param stationName the board's resolved station display name, may be empty
	 */
	public List<ServiceMessage> applicable(String local, String stationName) {
		final ServiceMessage own = ServiceMessage.display(local);
		if (messages.isEmpty() && own.isEmpty()) {
			return List.of();
		}
		final String key = stationName == null ? "" : key(stationName);
		final List<ServiceMessage> out = new ArrayList<>(messages.size() + 1);
		if (!own.isEmpty()) {
			out.add(own);
		}
		for (final ServiceMessage message : messages) {
			if (message.scope() == MessageScope.NETWORK || !key.isEmpty() && message.stationKey().equals(key)) {
				out.add(message);
			}
		}
		out.sort(ORDER);
		return List.copyOf(out);
	}

	// ---- NBT -----------------------------------------------------------------------------------------------------

	public NbtCompound toNbt() {
		final NbtCompound tag = new NbtCompound();
		tag.putInt("NextId", nextId);
		final NbtList list = new NbtList();
		for (final ServiceMessage message : messages) {
			list.add(message.toNbt());
		}
		tag.put("Messages", list);
		return tag;
	}

	/** Reads current data, or migrates the single-network/per-station layout written by 1.2. */
	public static ServiceMessages fromNbt(NbtCompound tag) {
		if (tag.contains("Network", NbtElement.COMPOUND_TYPE) || tag.contains("Stations", NbtElement.LIST_TYPE)) {
			ServiceMessages result = EMPTY;
			final NbtCompound network = tag.getCompound("Network");
			final ServiceMessage legacyNetwork = ServiceMessage.fromNbt(network);
			if (!legacyNetwork.isEmpty()) {
				result = orSame(result, result.add(MessageScope.NETWORK, "", legacyNetwork.severity(), legacyNetwork.text()));
			}
			final NbtList stations = tag.getList("Stations", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < stations.size(); i++) {
				final NbtCompound item = stations.getCompound(i);
				final ServiceMessage legacy = ServiceMessage.fromNbt(item);
				result = orSame(result, result.add(MessageScope.STATION, item.getString("Station"), legacy.severity(), legacy.text()));
			}
			return result;
		}
		final NbtList list = tag.getList("Messages", NbtElement.COMPOUND_TYPE);
		final List<ServiceMessage> read = new ArrayList<>();
		int max = 0;
		for (int i = 0; i < Math.min(list.size(), MAX_MESSAGES); i++) {
			final ServiceMessage message = ServiceMessage.fromNbt(list.getCompound(i));
			if (!valid(message, read)) {
				continue;
			}
			read.add(message);
			max = Math.max(max, message.id());
		}
		return new ServiceMessages(List.copyOf(read), Math.max(Math.max(tag.getInt("NextId"), max + 1), 1));
	}

	private static ServiceMessages orSame(ServiceMessages current, ServiceMessages next) {
		return next == null ? current : next;
	}

	/** A stored/received message must have text, a positive unique id, a real scope and, for stations, a name. */
	private static boolean valid(ServiceMessage message, List<ServiceMessage> existing) {
		if (message.isEmpty() || message.id() <= 0 || message.scope() == MessageScope.DISPLAY
				|| message.scope() == MessageScope.STATION && message.stationKey().isEmpty()) {
			return false;
		}
		for (final ServiceMessage other : existing) {
			if (other.id() == message.id()) {
				return false;
			}
		}
		return true;
	}

	// ---- packets -------------------------------------------------------------------------------------------------

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(nextId);
		buf.writeVarInt(messages.size());
		for (final ServiceMessage message : messages) {
			message.write(buf);
		}
	}

	/** Reads from an untrusted packet: strings are length-capped and the count is checked before anything is allocated. */
	public static ServiceMessages read(PacketByteBuf buf) {
		final int next = buf.readVarInt();
		final int count = buf.readVarInt();
		if (count < 0 || count > MAX_MESSAGES) {
			throw new IllegalArgumentException("Too many service messages: " + count);
		}
		final List<ServiceMessage> read = new ArrayList<>(count);
		int max = 0;
		for (int i = 0; i < count; i++) {
			final ServiceMessage message = ServiceMessage.read(buf);
			if (valid(message, read)) {
				read.add(message);
				max = Math.max(max, message.id());
			}
		}
		return new ServiceMessages(List.copyOf(read), Math.max(Math.max(next, max + 1), 1));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ServiceMessages that && nextId == that.nextId && messages.equals(that.messages);
	}

	@Override
	public int hashCode() {
		return 31 * nextId + messages.hashCode();
	}
}
