package com.aureliatransit.architecture.station;

/**
 * One change to the world, in world coordinates, as listed by {@link SiteSurvey}.
 *
 * @param role    the planned role ({@link Role#FOUNDATION} for fills); null for {@link Kind#CLEAR}
 * @param blockId block to set ({@code minecraft:air} for {@link Kind#CLEAR})
 * @param facing  world direction of the block's front (from {@link PlannedBlock#front})
 * @param text    sign text, empty when none
 * @param state   extra block state properties (from {@link PlannedBlock#state}), empty when none
 */
public record WorldOp(Kind kind, int x, int y, int z, Role role, String blockId, Heading facing, String text, String state) {

	public static final String AIR = "minecraft:air";

	public WorldOp(Kind kind, int x, int y, int z, Role role, String blockId, Heading facing, String text) {
		this(kind, x, y, z, role, blockId, facing, text, "");
	}

	public enum Kind {
		/** Remove an obstruction (approved terrain change). */
		CLEAR,
		/** Fill unsupported ground (approved terrain change). */
		FILL,
		/** Place a planned block. */
		PLACE
	}
}
