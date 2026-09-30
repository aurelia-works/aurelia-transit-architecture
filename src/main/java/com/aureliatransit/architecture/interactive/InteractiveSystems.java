package com.aureliatransit.architecture.interactive;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Common/server init for seating, editable text, clocks and bus content. Owned by workstream B.
 */
public final class InteractiveSystems {

	private InteractiveSystems() {
	}

	public static void init() {
		InteractiveEntities.init();
		// Seat entities are not saved, so nothing survives a restart; drop the bookkeeping with the server.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SeatManager.clear());
	}
}
