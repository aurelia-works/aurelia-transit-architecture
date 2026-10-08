package com.aureliatransit.architecture.block.wayfinding;

import net.minecraft.state.property.IntProperty;
import net.minecraft.util.shape.VoxelShape;

/**
 * A car stop board that always shows a number of cars, 1 to 12 (UK style).
 */
public class CarStopMarkerBlock extends CarStopBlock {

	public static final IntProperty CARS = IntProperty.of("cars", 1, 12);

	public CarStopMarkerBlock(Settings settings, VoxelShape northPost, VoxelShape northWall) {
		super(settings, northPost, northWall);
	}

	@Override
	public IntProperty cars() {
		return CARS;
	}
}
