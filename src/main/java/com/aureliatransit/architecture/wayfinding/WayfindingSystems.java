package com.aureliatransit.architecture.wayfinding;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Common/server init of the wayfinding-logic workstream: service-message state, the {@code /ata_message} command and
 * the join sync. Must not reference client or MTR classes.
 */
public final class WayfindingSystems {

	private WayfindingSystems() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ServiceMessageCommand.register(dispatcher));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			final ServiceMessages messages = ServiceMessageState.get(server).messages();
			if (!messages.isEmpty()) {
				ServiceMessageSync.send(handler.getPlayer(), messages);
			}
		});
	}
}
