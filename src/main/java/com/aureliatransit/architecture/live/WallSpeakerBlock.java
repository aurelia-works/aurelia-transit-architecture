package com.aureliatransit.architecture.live;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

/**
 * The wall speaker. Against a column or pole, whose face is set back from the block's edge, it stands on a short bracket
 * arm reaching back to that face ({@link #POST}) instead of floating in front of it.
 */
public class WallSpeakerBlock extends SpeakerBlock {

	public static final BooleanProperty POST = BooleanProperty.of("post");

	public WallSpeakerBlock(Settings settings, VoxelShape northOutline) {
		super(settings, northOutline);
		setDefaultState(getDefaultState().with(POST, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(POST);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : state.with(POST, onPost(ctx.getWorld(), ctx.getBlockPos(), state.get(FACING)));
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
			BlockPos pos, BlockPos neighborPos) {
		if (direction == state.get(FACING).getOpposite()) {
			return state.with(POST, onPost(world, pos, state.get(FACING)));
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	/** True when the block behind a speaker facing {@code facing} is there but has no full face towards it: a column or pole. */
	static boolean onPost(BlockView world, BlockPos pos, Direction facing) {
		final BlockPos back = pos.offset(facing.getOpposite());
		final BlockState behind = world.getBlockState(back);
		return !behind.isAir() && !behind.isSideSolidFullSquare(world, back, facing);
	}
}
