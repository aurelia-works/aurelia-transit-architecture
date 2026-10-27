package com.aureliatransit.architecture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.WorldAccess;

/**
 * A bench that joins the same bench facing the same way beside it into one long bench (Null, 10-26: "better benches"): the
 * legs and armrests stand only at the ends of the row and a middle block gets a centre support, so a row reads as one bench
 * and not as chairs side by side. Left and right are the sitter's, who looks the way the bench faces: left is anticlockwise
 * of the facing, as on the joining signs ({@link TextSignBlock#localLeft}).
 */
public class JoiningSeatBlock extends SeatBlock {

	public static final BooleanProperty LEFT = BooleanProperty.of("left");
	public static final BooleanProperty RIGHT = BooleanProperty.of("right");

	public JoiningSeatBlock(Settings settings, Placement placement, VoxelShape outline, VoxelShape collision, int slots, double seatTopPx, double seatZPx,
			boolean sitterFacesFront) {
		super(settings, placement, outline, collision, slots, seatTopPx, seatZPx, sitterFacesFront);
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

	/** A mirror turns the sitter round, so their left end becomes their right. */
	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		final BlockState turned = super.mirror(state, mirror);
		return mirror == BlockMirror.NONE ? turned : turned.with(LEFT, state.get(RIGHT)).with(RIGHT, state.get(LEFT));
	}
}
