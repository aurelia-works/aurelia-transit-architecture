package com.aureliatransit.architecture.interactive;

import com.aureliatransit.architecture.block.SeatBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Server-authoritative seating: decides whether a player may sit, spawns the seat entity and keeps slot occupancy.
 */
public final class SeatManager {

	/**
	 * Identifies one seat slot of one block.
	 */
	public record SeatKey(String dimension, long pos, int slot) {
	}

	private static final SeatOccupancy<SeatKey> OCCUPANCY = new SeatOccupancy<>();
	private static final double MAX_SIT_DISTANCE_SQ = 7 * 7;

	private SeatManager() {
	}

	/**
	 * Tries to seat the player on the block, preferring the slot nearest to the click.
	 */
	public static boolean trySit(ServerWorld world, BlockPos pos, BlockState state, SeatBlock block, ServerPlayerEntity player, Vec3d hit) {
		if (player.hasVehicle() || player.isSpectator() || !player.isAlive() || player.isSneaking()
				|| player.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_SIT_DISTANCE_SQ) {
			return false;
		}
		final Direction facing = state.get(SeatBlock.FACING);
		final int turns = com.aureliatransit.architecture.util.Shapes.quarterTurns(facing);
		final int slots = block.slots();
		final int preferred = SeatGeometry.slotForHit(hit.x - (pos.getX() + 0.5), hit.z - (pos.getZ() + 0.5), slots, turns);
		final String dimension = world.getRegistryKey().getValue().toString();
		for (final int slot : SeatGeometry.slotOrder(preferred, slots)) {
			final SeatKey key = new SeatKey(dimension, pos.asLong(), slot);
			if (OCCUPANCY.isOccupied(key, id -> isLive(world, id))) {
				continue;
			}
			final SeatEntity seat = InteractiveEntities.SEAT.create(world);
			if (seat == null) {
				return false;
			}
			final double[] offset = SeatGeometry.slotOffset(slot, slots, block.seatZPx(), turns);
			final Direction sitting = block.sitterFacesFront() ? facing : facing.getOpposite();
			seat.refreshPositionAndAngles(pos.getX() + 0.5 + offset[0], pos.getY() + block.seatTopPx() / 16.0 - SeatGeometry.SEAT_DROP,
					pos.getZ() + 0.5 + offset[1], sitting.asRotation(), 0);
			seat.bind(key, pos.toImmutable());
			OCCUPANCY.claim(key, seat.getUuid(), id -> isLive(world, id));
			if (!world.spawnEntity(seat)) {
				OCCUPANCY.release(key, seat.getUuid());
				return false;
			}
			if (!player.startRiding(seat, true)) {
				seat.discard();
				return false;
			}
			return true;
		}
		return false;
	}

	/**
	 * Removes every seat entity at a block that was broken or replaced.
	 */
	public static void discardSeatsAt(World world, BlockPos pos) {
		if (world.isClient) {
			return;
		}
		for (final SeatEntity seat : world.getEntitiesByClass(SeatEntity.class, new Box(pos).expand(0.3), entity -> true)) {
			seat.discard();
		}
	}

	static void release(SeatKey key, UUID seat) {
		OCCUPANCY.release(key, seat);
	}

	public static void clear() {
		OCCUPANCY.clear();
	}

	private static boolean isLive(ServerWorld world, UUID id) {
		final Entity entity = world.getEntity(id);
		return entity instanceof SeatEntity && entity.isAlive() && entity.hasPassengers();
	}
}
