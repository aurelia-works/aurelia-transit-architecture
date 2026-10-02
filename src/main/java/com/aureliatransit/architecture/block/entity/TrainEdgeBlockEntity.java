package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Block entity of a train-keyed edge (1.4): no data and no ticker; it exists so the client renderer can draw the moving part. */
public class TrainEdgeBlockEntity extends BlockEntity {

	public TrainEdgeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TRAIN_EDGE, pos, state);
	}
}
