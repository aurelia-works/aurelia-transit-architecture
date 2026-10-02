package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Server to client sync of the full {@link StationSuffixes} set (at most {@value StationSuffixes#MAX_ENTRIES} short
 * entries). Sent to a player when they join and to everyone when a suffix changes; never polled.
 */
public final class StationSuffixSync {

	public static final Identifier SYNC = AureliaTransitArchitecture.id("station_suffixes");

	private StationSuffixSync() {
	}

	public static PacketByteBuf write(StationSuffixes suffixes) {
		final PacketByteBuf buf = PacketByteBufs.create();
		suffixes.write(buf);
		return buf;
	}

	public static void send(ServerPlayerEntity player, StationSuffixes suffixes) {
		ServerPlayNetworking.send(player, SYNC, write(suffixes));
	}

	public static void broadcast(MinecraftServer server, StationSuffixes suffixes) {
		for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			send(player, suffixes);
		}
	}
}
