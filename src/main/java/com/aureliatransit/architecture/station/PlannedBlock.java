package com.aureliatransit.architecture.station;

import java.util.Objects;

/**
 * One block of a plan in local coordinates: u across the station (0 = leftmost column), v along the tracks (0 = first
 * platform cell; ramps sit at negative v and past the platform end), y relative to rail level (0 = rail and platform blocks,
 * 1 = walking level).
 *
 * @param blockId a namespaced block id such as {@code aurelia_transit_architecture:platform_edge}
 * @param front   where the model's front points (ATA models are authored front-north, so this becomes FACING; axis blocks use
 *                its axis; non-rotating blocks ignore it)
 * @param text    sign text for {@link Role#NAME_SIGN}, empty otherwise
 */
public record PlannedBlock(int u, int y, int v, Role role, String blockId, LocalDir front, String text) {

	public PlannedBlock {
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(blockId, "blockId");
		Objects.requireNonNull(front, "front");
		Objects.requireNonNull(text, "text");
	}
}
