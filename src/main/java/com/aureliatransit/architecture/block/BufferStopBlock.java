package com.aureliatransit.architecture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldAccess;

/**
 * A buffer stop at the end of a track, front towards the train. Cells of the same buffer facing the same way side by side
 * join into one: the end cells carry the buffer heads and the braced posts, the middle ones the beam and the lamp. A lone
 * cell is a narrow buffer of its own. Left and right are the driver's, looking at the front.
 */
public class BufferStopBlock extends FacingShapedBlock {

	public enum Part implements StringIdentifiable {
		SINGLE, LEFT, MIDDLE, RIGHT;

		@Override
		public String asString() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** The part a cell is with a joined cell on its left and/or right. */
		public static Part of(boolean left, boolean right) {
			return left && right ? MIDDLE : right ? LEFT : left ? RIGHT : SINGLE;
		}
	}

	public static final EnumProperty<Part> PART = EnumProperty.of("part", Part.class);

	public BufferStopBlock(Settings settings, VoxelShape northOutline) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
		setDefaultState(getDefaultState().with(PART, Part.SINGLE));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(PART);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : joined(state, ctx.getWorld(), ctx.getBlockPos());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos) {
		return direction.getAxis().isHorizontal() ? joined(state, world, pos) : state;
	}

	/** The state with its part set from the cells on the driver's left (east of a north front) and right. */
	private BlockState joined(BlockState state, WorldAccess world, BlockPos pos) {
		final Direction front = state.get(FACING);
		return state.with(PART, Part.of(joins(state, world.getBlockState(pos.offset(front.rotateYClockwise()))),
				joins(state, world.getBlockState(pos.offset(front.rotateYCounterclockwise())))));
	}

	private boolean joins(BlockState state, BlockState other) {
		return other.isOf(this) && other.get(FACING) == state.get(FACING);
	}
}
