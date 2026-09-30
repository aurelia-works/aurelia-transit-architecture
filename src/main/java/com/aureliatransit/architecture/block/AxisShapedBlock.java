package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * A block that runs along a horizontal axis (beams, gantries, roof supports). Shapes are authored along X.
 */
public class AxisShapedBlock extends Block {

	public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

	private final VoxelShape shapeX;
	private final VoxelShape shapeZ;

	public AxisShapedBlock(Settings settings, VoxelShape shapeAlongX) {
		super(settings);
		this.shapeX = shapeAlongX;
		this.shapeZ = Shapes.rotate(shapeAlongX, 1);
		setDefaultState(getStateManager().getDefaultState().with(AXIS, Direction.Axis.X));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(AXIS, ctx.getHorizontalPlayerFacing().getAxis());
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(AXIS) == Direction.Axis.X ? shapeX : shapeZ;
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
			return state.with(AXIS, state.get(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}
}
