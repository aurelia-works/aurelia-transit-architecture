package com.aureliatransit.architecture.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * A rotating, partially glazed block (panels, barriers, shelter walls, skylights). Lets light and sky through
 * and does not darken neighbours with ambient occlusion.
 */
public class GlassFacingBlock extends FacingShapedBlock {

	public GlassFacingBlock(Settings settings, Placement placement, VoxelShape northOutline) {
		super(settings, placement, northOutline);
	}

	public GlassFacingBlock(Settings settings, Placement placement, VoxelShape northOutline, VoxelShape northCollision) {
		super(settings, placement, northOutline, northCollision);
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
