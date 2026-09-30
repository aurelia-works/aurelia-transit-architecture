package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.network.EditValidation;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Common/server init for live systems: the two validated configuration packets. No client or MTR-client classes are
 * referenced here, so a dedicated server never loads them. Block entity types live in
 * {@link com.aureliatransit.architecture.registry.LiveBlocks}.
 */
public final class LiveSystems {

	public static final Identifier UPDATE_DISPLAY = AureliaTransitArchitecture.id("live_update_display");
	public static final Identifier UPDATE_SPEAKER = AureliaTransitArchitecture.id("live_update_speaker");

	private LiveSystems() {
	}

	public static PacketByteBuf writeDisplayUpdate(BlockPos pos, DisplayConfig config) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		config.write(buf);
		return buf;
	}

	public static PacketByteBuf writeSpeakerUpdate(BlockPos pos, SpeakerConfig config) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		config.write(buf);
		return buf;
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LiveStressCommand.register(dispatcher));
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_DISPLAY, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos;
			final DisplayConfig config;
			try {
				pos = buf.readBlockPos();
				config = DisplayConfig.read(buf);
			} catch (RuntimeException malformed) {
				return;
			}
			server.execute(() -> {
				if (EditValidation.canEdit(player, pos) && player.getServerWorld().getBlockEntity(pos) instanceof PidsBlockEntity display) {
					display.setConfig(config);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_SPEAKER, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos;
			final SpeakerConfig config;
			try {
				pos = buf.readBlockPos();
				config = SpeakerConfig.read(buf);
			} catch (RuntimeException malformed) {
				return;
			}
			server.execute(() -> {
				if (EditValidation.canEdit(player, pos) && player.getServerWorld().getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
					speaker.setConfig(config);
				}
			});
		});
	}
}
