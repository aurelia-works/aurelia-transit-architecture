package com.aureliatransit.architecture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * A non-rotating block with a fixed outline and collision shape.
 */
public class ShapedBlock extends Block {

	private final VoxelShape outline;
	private final VoxelShape collision;

	public ShapedBlock(Settings settings, VoxelShape outline) {
		this(settings, outline, outline);
	}

	public ShapedBlock(Settings settings, VoxelShape outline, VoxelShape collision) {
		super(settings);
		this.outline = outline;
		this.collision = collision;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return outline;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return collision;
	}
}
