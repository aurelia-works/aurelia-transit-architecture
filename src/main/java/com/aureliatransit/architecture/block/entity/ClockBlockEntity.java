package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Carrier for the clock renderer. Stateless: the time comes from the world.
 */
public class ClockBlockEntity extends BlockEntity {

	public ClockBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CLOCK, pos, state);
	}
}
