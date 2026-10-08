package com.aureliatransit.architecture.block.glass;

import com.aureliatransit.architecture.block.AxisShapedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * A thin full-height glass fin that runs along the horizontal axis you face. Lets light through like glass.
 */
public class GlassFinBlock extends AxisShapedBlock {

	public GlassFinBlock(Settings settings, VoxelShape shapeAlongX) {
		super(settings, shapeAlongX);
	}

	@Override
	public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
		return 1.0F;
	}

	@Override
	public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
		return true;
	}
}
