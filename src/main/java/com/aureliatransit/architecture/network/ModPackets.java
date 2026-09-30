package com.aureliatransit.architecture.network;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.InfoDisplayBlockEntity;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.text.ConfigurableTextData;
import com.aureliatransit.architecture.text.SignData;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingEditable;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-server configuration packets of the interactive content: sign text and information text. Every handler
 * decodes with bounded reads, then on the server thread checks permission and reach ({@link EditValidation}), the
 * block type and the block entity before applying re-sanitized data.
 */
public final class ModPackets {

	public static final Identifier UPDATE_SIGN = AureliaTransitArchitecture.id("update_sign");
	public static final Identifier UPDATE_INFO_TEXT = AureliaTransitArchitecture.id("update_info_text");
	public static final Identifier UPDATE_WAYFINDING = AureliaTransitArchitecture.id("update_wayfinding");

	private ModPackets() {
	}

	public static PacketByteBuf writeSign(BlockPos pos, SignData data) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		data.write(buf);
		return buf;
	}

	public static PacketByteBuf writeInfoText(BlockPos pos, ConfigurableTextData text) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		text.write(buf);
		return buf;
	}

	public static PacketByteBuf writeWayfinding(BlockPos pos, WayfindingData data) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		data.write(buf);
		return buf;
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_WAYFINDING, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos;
			final WayfindingData data;
			try {
				pos = buf.readBlockPos();
				data = WayfindingData.read(buf);
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> {
				final ServerWorld world = player.getServerWorld();
				if (world.isChunkLoaded(pos) && EditValidation.canEdit(player, pos) && world.getBlockEntity(pos) instanceof WayfindingEditable editable) {
					editable.setWayfinding(data);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_SIGN, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos = buf.readBlockPos();
			final SignData data;
			try {
				data = SignData.read(buf);
			} catch (RuntimeException e) {
				return; // malformed or oversized payload: ignore
			}
			server.execute(() -> applySign(player, pos, data));
		});
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_INFO_TEXT, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos = buf.readBlockPos();
			final ConfigurableTextData text;
			try {
				text = ConfigurableTextData.read(buf);
			} catch (RuntimeException e) {
				return;
			}
			server.execute(() -> applyInfoText(player, pos, text));
		});
	}

	private static void applySign(ServerPlayerEntity player, BlockPos pos, SignData data) {
		final ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos)) {
			return;
		}
		final BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof TextSignBlock block && isRowInReach(player, block, state, pos)
				&& world.getBlockEntity(pos) instanceof TextSignBlockEntity sign) {
			sign.setData(data);
		}
	}

	private static void applyInfoText(ServerPlayerEntity player, BlockPos pos, ConfigurableTextData text) {
		final ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos) || !EditValidation.canEdit(player, pos)) {
			return;
		}
		if (world.getBlockState(pos).getBlock() instanceof InfoDisplayBlock && world.getBlockEntity(pos) instanceof InfoDisplayBlockEntity display) {
			display.setText(text);
		}
	}

	/**
	 * The player may edit a joined row from any of its blocks, so reach and permission are checked against each block of the row.
	 */
	private static boolean isRowInReach(ServerPlayerEntity player, TextSignBlock block, BlockState ownerState, BlockPos owner) {
		BlockPos current = owner;
		BlockState currentState = ownerState;
		for (int i = 0; i < TextSignBlock.MAX_ROW; i++) {
			if (EditValidation.canEdit(player, current)) {
				return true;
			}
			if (!currentState.get(TextSignBlock.RIGHT)) {
				return false;
			}
			current = current.offset(TextSignBlock.localRight(currentState.get(TextSignBlock.FACING)));
			if (!player.getServerWorld().isChunkLoaded(current)) {
				return false;
			}
			currentState = player.getServerWorld().getBlockState(current);
			if (!currentState.isOf(block)) {
				return false;
			}
		}
		return false;
	}
}
