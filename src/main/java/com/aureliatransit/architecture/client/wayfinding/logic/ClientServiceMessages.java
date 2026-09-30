package com.aureliatransit.architecture.client.wayfinding.logic;

import com.aureliatransit.architecture.wayfinding.ServiceMessage;
import com.aureliatransit.architecture.wayfinding.ServiceMessages;

/**
 * The latest server-synced {@link ServiceMessages} on this client. Written from the network handler, read by display
 * builders (about once a second per visible board), so it is a single volatile reference to an immutable value.
 */
public final class ClientServiceMessages {

	private static volatile ServiceMessages current = ServiceMessages.EMPTY;

	private ClientServiceMessages() {
	}

	public static void set(ServiceMessages messages) {
		current = messages == null ? ServiceMessages.EMPTY : messages;
	}

	public static void clear() {
		current = ServiceMessages.EMPTY;
	}

	public static ServiceMessages get() {
		return current;
	}

	/**
	 * The message a board shows: the display's own text, else its station's, else the network's.
	 *
	 * @param local       the display's own message ({@code DisplayConfig.message()})
	 * @param stationName the board's resolved station display name, or empty
	 */
	public static ServiceMessage select(String local, String stationName) {
		return current.select(local, stationName);
	}

	/**
	 * Every message applicable to a station, in priority order: the station's own, then the network's (empty ones left
	 * out). For the terminal's service-info page; {@link #select} is unchanged.
	 */
	public static java.util.List<ServiceMessage> allFor(String stationName) {
		final ServiceMessages messages = current;
		final ServiceMessage station = messages.forStation(stationName);
		final ServiceMessage network = messages.network();
		final java.util.List<ServiceMessage> out = new java.util.ArrayList<>(2);
		if (!station.isEmpty()) {
			out.add(station);
		}
		if (!network.isEmpty()) {
			out.add(network);
		}
		return java.util.List.copyOf(out);
	}
}
