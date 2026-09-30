package com.aureliatransit.architecture.network;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Server-side checks every Aurelia configuration packet must pass before touching a block entity.
 * Call from inside {@code server.execute(...)} (i.e. on the server thread).
 */
public final class EditValidation {

	public static final double MAX_REACH = 8.0;

	private EditValidation() {
	}

	public static boolean canEdit(ServerPlayerEntity player, BlockPos pos) {
		return canEdit(player, pos, MAX_REACH);
	}

	public static boolean canEdit(ServerPlayerEntity player, BlockPos pos, double reach) {
		final ServerWorld world = player.getServerWorld();
		return !player.isSpectator()
				&& player.canModifyBlocks()
				&& world.isChunkLoaded(pos)
				&& world.canPlayerModifyAt(player, pos)
				&& player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
	}
}
