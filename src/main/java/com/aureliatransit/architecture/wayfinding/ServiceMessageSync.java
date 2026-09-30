package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Server to client sync of the full {@link ServiceMessages} set (at most 17 short strings). Sent to a player when they
 * join and to everyone when a message changes; never polled.
 */
public final class ServiceMessageSync {

	public static final Identifier SYNC = AureliaTransitArchitecture.id("service_messages");

	private ServiceMessageSync() {
	}

	public static PacketByteBuf write(ServiceMessages messages) {
		final PacketByteBuf buf = PacketByteBufs.create();
		messages.write(buf);
		return buf;
	}

	public static void send(ServerPlayerEntity player, ServiceMessages messages) {
		ServerPlayNetworking.send(player, SYNC, write(messages));
	}

	public static void broadcast(MinecraftServer server, ServiceMessages messages) {
		for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			send(player, messages);
		}
	}
}
