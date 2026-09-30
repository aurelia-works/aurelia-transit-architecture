package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.Map;

/**
 * A horizontally rotating block whose shapes are authored facing north and rotated per facing.
 */
public class FacingShapedBlock extends HorizontalFacingBlock {

	private final Map<Direction, VoxelShape> outlines;
	private final Map<Direction, VoxelShape> collisions;
	private final Placement placement;

	public FacingShapedBlock(Settings settings, Placement placement, VoxelShape northOutline) {
		this(settings, placement, northOutline, northOutline);
	}

	public FacingShapedBlock(Settings settings, Placement placement, VoxelShape northOutline, VoxelShape northCollision) {
		super(settings);
		this.placement = placement;
		this.outlines = Shapes.horizontalMap(northOutline);
		this.collisions = northCollision == northOutline ? outlines : Shapes.horizontalMap(northCollision);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final Direction looking = ctx.getHorizontalPlayerFacing();
		return getDefaultState().with(FACING, placement == Placement.TOWARD_PLAYER ? looking.getOpposite() : looking);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return outlines.get(state.get(FACING));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return collisions.get(state.get(FACING));
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
