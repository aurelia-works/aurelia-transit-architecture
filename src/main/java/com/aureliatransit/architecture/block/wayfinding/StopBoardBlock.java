package com.aureliatransit.architecture.block.wayfinding;

import net.minecraft.state.property.IntProperty;
import net.minecraft.util.shape.VoxelShape;

/**
 * A car stop board with a number of cars, 1 to 12, or 0 for the "stop here" board (German "H", Dutch red and white).
 */
public class StopBoardBlock extends CarStopBlock {

	public static final IntProperty CARS = IntProperty.of("cars", 0, 12);

	public StopBoardBlock(Settings settings, VoxelShape northPost, VoxelShape northWall) {
		super(settings, northPost, northWall);
	}

	@Override
	public IntProperty cars() {
		return CARS;
	}
}
