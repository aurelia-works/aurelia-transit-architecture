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
}
