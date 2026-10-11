package com.aureliatransit.architecture.station;

/**
 * Read-only view of the world in world coordinates. The game implements it over a ServerWorld; tests use a fake grid.
 */
public interface SiteProbe {

	CellKind kind(int x, int y, int z);

	/** True when the cell's top face is full and sturdy enough to carry a platform block or a track. */
	boolean sturdyTop(int x, int y, int z);

	/** Short name of the block in the cell for messages, such as {@code minecraft:oak_log}. */
	String describe(int x, int y, int z);
}
