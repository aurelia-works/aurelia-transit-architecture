package com.aureliatransit.architecture.interactive;

import com.aureliatransit.architecture.block.SeatBlock;
import com.google.common.collect.ImmutableList;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The invisible vehicle a sitting player rides. It is never saved, has no AI or gravity and discards itself as soon as
 * it has no passenger, its seat block is gone, or it was left without a valid seat.
 */
public class SeatEntity extends Entity {

	private static final int CHECK_INTERVAL = 10;

	private SeatManager.SeatKey key;
	private BlockPos seatPos;
	private int checkTimer;

	public SeatEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	/**
	 * Server side only: binds the entity to its seat slot.
	 */
	void bind(SeatManager.SeatKey key, BlockPos seatPos) {
		this.key = key;
		this.seatPos = seatPos;
	}

	@Override
	protected void initDataTracker() {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	/**
	 * Seat entities are transient: never written to disk, so a chunk unload cannot leave one behind.
	 */
	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public void tick() {
		if (getWorld().isClient) {
			return;
		}
		final Entity passenger = getFirstPassenger();
		if (passenger == null || !passenger.isAlive()) {
			discard();
			return;
		}
		if (++checkTimer >= CHECK_INTERVAL) {
			checkTimer = 0;
			if (seatPos == null || !getWorld().isChunkLoaded(seatPos) || !(getWorld().getBlockState(seatPos).getBlock() instanceof SeatBlock)) {
				discard();
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		if (key != null) {
			SeatManager.release(key, getUuid());
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!getWorld().isClient && getPassengerList().isEmpty()) {
			discard();
		}
	}

	@Override
	public double getMountedHeightOffset() {
		return 0;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengerList().isEmpty();
	}

	/**
	 * Keeps a seated player's body facing along the seat, letting the head turn like in a boat.
	 */
	@Override
	public void onPassengerLookAround(Entity passenger) {
		passenger.setBodyYaw(getYaw());
		final float relative = MathHelper.wrapDegrees(passenger.getYaw() - getYaw());
		final float clamped = MathHelper.clamp(relative, -105.0F, 105.0F);
		passenger.prevYaw += clamped - relative;
		passenger.setYaw(passenger.getYaw() + clamped - relative);
		passenger.setHeadYaw(passenger.getYaw());
	}

	/**
	 * Steps the passenger off beside the seat onto the first free, supported spot: in front, then the sides, then behind,
	 * and finally on top of the seat.
	 */
	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		final BlockPos base = BlockPos.ofFloored(getX(), getY(), getZ());
		for (final double[] offset : SeatGeometry.dismountOffsets(getYaw())) {
			final BlockPos cell = base.add((int) offset[0], 0, (int) offset[1]);
			final List<Vec3d> candidates = new ArrayList<>(2);
			final double here = getWorld().getDismountHeight(cell);
			if (Dismounting.canDismountInBlock(here)) {
				candidates.add(new Vec3d(cell.getX() + 0.5, cell.getY() + here, cell.getZ() + 0.5));
			}
			final double below = getWorld().getDismountHeight(cell.down());
			if (Dismounting.canDismountInBlock(below)) {
				candidates.add(new Vec3d(cell.getX() + 0.5, cell.getY() - 1 + below, cell.getZ() + 0.5));
			}
			final ImmutableList<EntityPose> poses = passenger.getPoses();
			for (final EntityPose pose : poses) {
				for (final Vec3d candidate : candidates) {
					if (Dismounting.canPlaceEntityAt(getWorld(), candidate, passenger, pose)) {
						passenger.setPose(pose);
						return candidate;
					}
				}
			}
		}
		return new Vec3d(getX(), getY() + 0.3, getZ());
	}

	@Override
	public boolean hasNoGravity() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource damageSource) {
		return true;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
