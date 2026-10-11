package com.aureliatransit.architecture.station;

/**
 * What a {@link SiteProbe} finds in a world cell.
 */
public enum CellKind {
	AIR,
	/** Not air, no collision and no fluid: grass, flowers, a thin snow layer, a torch. */
	SOFT,
	/** Anything with collision (stone, dirt, logs, leaves, slabs, fences ...). */
	SOLID,
	/** A fluid, waterlogged or not. */
	FLUID,
	/**
	 * An existing MTR rail node. Its rails live in MTR's own data, so a block change would orphan them: the survey keeps it in a
	 * planned node cell, leaves it alone in clearances and treats it as protected everywhere else.
	 */
	RAIL_NODE,
	/** Never touched: has a block entity (chests, signs ...) or cannot be broken (bedrock, barriers). */
	PROTECTED,
	/** In a chunk that is not loaded. Never loaded on purpose. */
	UNLOADED,
	/** Below the bottom or above the top of the world. */
	OUT_OF_WORLD
}
