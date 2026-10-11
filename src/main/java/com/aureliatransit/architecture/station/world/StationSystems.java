package com.aureliatransit.architecture.station.world;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Common/server init of the station generator: the {@code /ata_station} command, one server tick listener (idle unless a
 * build or preview is running), the station wrench's left-click and cleanup when players leave or the server stops. The
 * wrench item is registered in {@code ModItems} and its screen packet in {@code ModPackets}.
 */
public final class StationSystems {

	private StationSystems() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> StationCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(StationSessions::tick);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> StationWrenchItem.attack(player, world, hand));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> StationSessions.forgetPlayer(handler.getPlayer().getUuid()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> StationSessions.clear());
	}
}
