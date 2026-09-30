package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.InfoDisplayBlockEntity;
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
 * Information case, information pillar and timetable case: a facing block with an editable heading and body rows,
 * rendered by the one shared information renderer.
 */
public class InfoDisplayBlock extends FacingShapedBlock implements BlockEntityProvider {

	/**
	 * Set by the client initializer; opens the shared text editor for a block position. A no-op on dedicated servers.
	 */
	public static Consumer<BlockPos> openEditor = pos -> {
	};

	private final InfoLayout layout;

	public InfoDisplayBlock(Settings settings, Placement placement, VoxelShape northOutline, InfoLayout layout) {
		super(settings, placement, northOutline);
		this.layout = layout;
	}

	public InfoLayout getLayout() {
		return layout;
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
		return new InfoDisplayBlockEntity(pos, state);
	}
}
