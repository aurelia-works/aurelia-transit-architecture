package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.block.TextSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * A wayfinding sign that joins with identical neighbours on its local left/right into one wide panel (directional,
 * exit and bus e-paper boards). Same join model as {@link TextSignBlock}: the leftmost block owns the data and draws.
 */
public class WayfindingSignBlock extends WayfindingPlateBlock {

	public static final BooleanProperty LEFT = TextSignBlock.LEFT;
	public static final BooleanProperty RIGHT = TextSignBlock.RIGHT;
	public static final int MAX_ROW = TextSignBlock.MAX_ROW;

	public WayfindingSignBlock(Settings settings, VoxelShape northOutline, WayfindingPanelSpec spec) {
		super(settings, northOutline, spec);
		setDefaultState(getDefaultState().with(LEFT, false).with(RIGHT, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(LEFT, RIGHT);
	}

	private boolean joinsWith(BlockState self, BlockState other) {
		return other.isOf(this) && other.get(FACING) == self.get(FACING);
	}

	private BlockState withConnections(BlockState state, WorldAccess world, BlockPos pos) {
		final Direction facing = state.get(FACING);
		return state
				.with(LEFT, joinsWith(state, world.getBlockState(pos.offset(TextSignBlock.localLeft(facing)))))
				.with(RIGHT, joinsWith(state, world.getBlockState(pos.offset(TextSignBlock.localRight(facing)))));
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : withConnections(state, ctx.getWorld(), ctx.getBlockPos());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction.getAxis().isHorizontal()) {
			return withConnections(state, world, pos);
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public boolean drawsPanel(BlockState state) {
		return !state.get(LEFT);
	}

	@Override
	public BlockPos editorPos(World world, BlockPos pos, BlockState state) {
		return rowOwner(world, pos, state);
	}

	/**
	 * The leftmost block of the row containing {@code pos}.
	 */
	public BlockPos rowOwner(BlockView world, BlockPos pos, BlockState state) {
		final Direction left = TextSignBlock.localLeft(state.get(FACING));
		BlockPos current = pos;
		BlockState currentState = state;
		for (int i = 0; i < MAX_ROW && currentState.get(LEFT); i++) {
			final BlockPos next = current.offset(left);
			final BlockState nextState = world.getBlockState(next);
			if (!joinsWith(currentState, nextState)) {
				break;
			}
			current = next;
			currentState = nextState;
		}
		return current;
	}

	/**
	 * Number of blocks in the row that starts at {@code owner}.
	 */
	public int rowLength(BlockView world, BlockPos owner, BlockState ownerState) {
		final Direction right = TextSignBlock.localRight(ownerState.get(FACING));
		return WayfindingRow.length(MAX_ROW, index -> {
			final BlockState current = world.getBlockState(owner.offset(right, index));
			return joinsWith(ownerState, current) && current.get(RIGHT);
		});
	}
}
