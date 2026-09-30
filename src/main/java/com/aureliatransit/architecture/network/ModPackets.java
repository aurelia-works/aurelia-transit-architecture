package com.aureliatransit.architecture.network;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The only packet in V1: a client asking to change the text of a sign it has open in the editor.
 */
public final class ModPackets {

	public static final Identifier UPDATE_SIGN_TEXT = AureliaTransitArchitecture.id("update_sign_text");
	private static final double MAX_EDIT_DISTANCE_SQ = 12 * 12;

	private ModPackets() {
	}

	public static PacketByteBuf writeSignText(BlockPos pos, List<String> lines) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(pos);
		buf.writeVarInt(lines.size());
		for (final String line : lines) {
			buf.writeString(line, TextSignBlockEntity.MAX_LENGTH * 4);
		}
		return buf;
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_SIGN_TEXT, (server, player, handler, buf, responseSender) -> {
			final BlockPos pos = buf.readBlockPos();
			final int count = Math.min(buf.readVarInt(), TextSignBlockEntity.MAX_LINES);
			final List<String> lines = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				lines.add(buf.readString(TextSignBlockEntity.MAX_LENGTH * 4));
			}
			server.execute(() -> applySignText(player, pos, lines));
		});
	}

	private static void applySignText(ServerPlayerEntity player, BlockPos pos, List<String> lines) {
		final ServerWorld world = player.getServerWorld();
		if (!world.isChunkLoaded(pos) || !player.canModifyBlocks() || !world.canPlayerModifyAt(player, pos)) {
			return;
		}
		final BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof TextSignBlock block && world.getBlockEntity(pos) instanceof TextSignBlockEntity sign && isRowInReach(player, block, state, pos)) {
			sign.setLines(lines);
		}
	}

	/**
	 * The player may edit a joined row from any of its blocks, so reach is measured to the nearest block of the row.
	 */
	private static boolean isRowInReach(ServerPlayerEntity player, TextSignBlock block, BlockState ownerState, BlockPos owner) {
		BlockPos current = owner;
		BlockState currentState = ownerState;
		for (int i = 0; i < TextSignBlock.MAX_ROW; i++) {
			if (player.squaredDistanceTo(Vec3d.ofCenter(current)) <= MAX_EDIT_DISTANCE_SQ) {
				return true;
			}
			if (!currentState.get(TextSignBlock.RIGHT)) {
				return false;
			}
			current = current.offset(TextSignBlock.localRight(currentState.get(TextSignBlock.FACING)));
			currentState = player.getServerWorld().getBlockState(current);
			if (!currentState.isOf(block)) {
				return false;
			}
		}
		return false;
	}
}
