package com.aureliatransit.architecture.util;

import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Shape helpers. All shapes are authored in pixel units (0-16) for a block whose front faces NORTH,
 * matching the JSON models, and rotated clockwise (viewed from above) the same way blockstate "y" rotations are.
 */
public final class Shapes {

	private Shapes() {
	}

	public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Block.createCuboidShape(x1, y1, z1, x2, y2, z2);
	}

	public static VoxelShape union(VoxelShape... shapes) {
		VoxelShape result = VoxelShapes.empty();
		for (final VoxelShape shape : shapes) {
			result = VoxelShapes.union(result, shape);
		}
		return result.simplify();
	}

	/**
	 * Rotates a north-facing shape by the given number of clockwise quarter turns.
	 */
	public static VoxelShape rotate(VoxelShape shape, int quarterTurns) {
		VoxelShape result = shape;
		for (int i = 0; i < (quarterTurns & 3); i++) {
			final VoxelShape[] rotated = {VoxelShapes.empty()};
			result.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
					rotated[0] = VoxelShapes.union(rotated[0], VoxelShapes.cuboid(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
			result = rotated[0];
		}
		return result.simplify();
	}

	public static int quarterTurns(Direction facing) {
		return (facing.getHorizontal() + 2) & 3;
	}

	public static Map<Direction, VoxelShape> horizontalMap(VoxelShape north) {
		final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
		for (final Direction direction : Direction.Type.HORIZONTAL) {
			map.put(direction, rotate(north, quarterTurns(direction)));
		}
		return map;
	}

	/**
	 * Builds a stepped approximation of a canopy/ramp profile. {@code bottom} gives the underside height (pixels)
	 * as a function of z (pixels, 0 = north), and the plate is {@code thickness} thick. When {@code solidBelow}
	 * is set the shape is filled down to y = 0 (used for ramps).
	 */
	public static VoxelShape profile(DoubleUnaryOperator bottom, double thickness, boolean solidBelow, int step) {
		VoxelShape result = VoxelShapes.empty();
		for (int z = 0; z < 16; z += step) {
			final double a = bottom.applyAsDouble(z);
			final double b = bottom.applyAsDouble(z + step);
			final double low = Math.max(0, Math.min(a, b));
			final double high = Math.min(16, Math.max(a, b) + thickness);
			if (high > low) {
				result = VoxelShapes.union(result, box(0, solidBelow ? 0 : low, z, 16, high, z + step));
			}
		}
		return result.simplify();
	}
}
