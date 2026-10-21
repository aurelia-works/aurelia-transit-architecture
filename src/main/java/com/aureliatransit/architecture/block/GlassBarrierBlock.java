package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.Map;

/**
 * The glass barrier: a glazed panel across the middle of its block, or with {@link #EDGE} flush with its front face, so it
 * stands at the edge of the drop it guards and the block stays walkable (the station generator sets this).
 */
public class GlassBarrierBlock extends GlassFacingBlock {

	public static final BooleanProperty EDGE = BooleanProperty.of("edge");
	private static final Map<Direction, VoxelShape> EDGE_OUTLINES = Shapes.horizontalMap(Shapes.union(Shapes.box(0, 0, 0, 16, 13, 1.5),
			Shapes.box(0, 13, 0, 16, 15, 3)));
	private static final Map<Direction, VoxelShape> EDGE_COLLISIONS = Shapes.horizontalMap(Shapes.box(0, 0, 0, 16, 24, 2));

	public GlassBarrierBlock(Settings settings, Placement placement, VoxelShape northOutline, VoxelShape northCollision) {
		super(settings, placement, northOutline, northCollision);
		setDefaultState(getDefaultState().with(EDGE, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(EDGE);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(EDGE) ? EDGE_OUTLINES.get(state.get(FACING)) : super.getOutlineShape(state, world, pos, context);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(EDGE) ? EDGE_COLLISIONS.get(state.get(FACING)) : super.getCollisionShape(state, world, pos, context);
	}
}
