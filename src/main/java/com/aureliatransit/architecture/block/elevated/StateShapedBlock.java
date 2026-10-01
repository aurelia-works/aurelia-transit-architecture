package com.aureliatransit.architecture.block.elevated;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A block whose outline and collision depend on its block state. Each state's shapes are computed once, on first use,
 * then served from a map, so shape queries (which the game makes constantly) allocate nothing.
 */
public abstract class StateShapedBlock extends Block {

	private final Map<BlockState, VoxelShape> outlines = new ConcurrentHashMap<>();
	private final Map<BlockState, VoxelShape> collisions = new ConcurrentHashMap<>();

	protected StateShapedBlock(Settings settings) {
		super(settings);
	}

	protected abstract VoxelShape outlineFor(BlockState state);

	/** Defaults to the outline; railings override it with a taller shape. */
	protected VoxelShape collisionFor(BlockState state) {
		return outlineFor(state);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return outlines.computeIfAbsent(state, this::outlineFor);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return collisions.computeIfAbsent(state, this::collisionFor);
	}
}
