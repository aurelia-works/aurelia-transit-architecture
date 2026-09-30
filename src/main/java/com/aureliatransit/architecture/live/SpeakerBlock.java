package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * A small announcement speaker (wall or ceiling model). Holds only configuration; the client speaker registry finds it
 * through block-entity load events, so it never ticks.
 */
public class SpeakerBlock extends FacingShapedBlock implements BlockEntityProvider {

	/**
	 * Set by the client initializer; opens the speaker configuration screen. No-op on dedicated servers.
	 */
	public static Consumer<BlockPos> openEditor = pos -> {
	};

	public SpeakerBlock(Settings settings, VoxelShape northOutline) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (player.getStackInHand(hand).getItem() instanceof BlockItem || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			openEditor.accept(pos);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SpeakerBlockEntity(pos, state);
	}
}
