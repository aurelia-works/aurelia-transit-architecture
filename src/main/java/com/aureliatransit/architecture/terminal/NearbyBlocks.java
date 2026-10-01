package com.aureliatransit.architecture.terminal;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure selection helper for bounded, local discovery of blocks around a terminal (standalone help points). The world
 * part only gathers candidates inside a fixed radius; this keeps the nearest few, nearest first, deterministically.
 */
public final class NearbyBlocks {

	private NearbyBlocks() {
	}

	/**
	 * @param candidates positions found by the bounded scan, in any order
	 * @param radius     blocks (spherical); candidates farther than this are dropped
	 * @param max        most positions to keep
	 */
	public static List<BlockPos> nearest(Iterable<BlockPos> candidates, BlockPos centre, int radius, int max) {
		final long limit = (long) radius * radius;
		final List<BlockPos> kept = new ArrayList<>();
		for (final BlockPos pos : candidates) {
			if (pos.getSquaredDistance(centre) <= limit) {
				kept.add(pos.toImmutable());
			}
		}
		kept.sort(Comparator.<BlockPos>comparingDouble(pos -> pos.getSquaredDistance(centre)).thenComparingLong(BlockPos::asLong));
		return List.copyOf(kept.size() > max ? kept.subList(0, Math.max(0, max)) : kept);
	}

	/** Whole blocks between two positions, for display ("12 blocks away"). */
	public static int distance(BlockPos a, BlockPos b) {
		return (int) Math.round(Math.sqrt(a.getSquaredDistance(b)));
	}
}
