package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.interactive.SeatManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * A bench or shelter seat a player can sit on by right-clicking with an empty hand (or any item that is not a block).
 * Seating is server-authoritative; see {@link SeatManager}.
 */
public class SeatBlock extends FacingShapedBlock {

	private final int slots;
	private final double seatTopPx;
	private final double seatZPx;
	private final boolean sitterFacesFront;

	/**
	 * @param slots            sitting places per block (1 or 2)
	 * @param seatTopPx        height of the seat surface in model pixels
	 * @param seatZPx          model-space z of the seat centre (north-facing model)
	 * @param sitterFacesFront whether the occupant looks the way the model faces (benches) or away from it (wall-backed seats)
	 */
	public SeatBlock(Settings settings, Placement placement, VoxelShape outline, VoxelShape collision, int slots, double seatTopPx, double seatZPx, boolean sitterFacesFront) {
		super(settings, placement, outline, collision);
		this.slots = slots;
		this.seatTopPx = seatTopPx;
		this.seatZPx = seatZPx;
		this.sitterFacesFront = sitterFacesFront;
	}

	public int slots() {
		return slots;
	}

	public double seatTopPx() {
		return seatTopPx;
	}

	public double seatZPx() {
		return seatZPx;
	}

	public boolean sitterFacesFront() {
		return sitterFacesFront;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND || player.getStackInHand(hand).getItem() instanceof BlockItem || player.isSneaking() || player.hasVehicle() || player.isSpectator()) {
			return ActionResult.PASS;
		}
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
			return SeatManager.trySit(serverWorld, pos, state, this, serverPlayer, hit.getPos()) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			SeatManager.discardSeatsAt(world, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
