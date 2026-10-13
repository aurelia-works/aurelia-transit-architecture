package com.aureliatransit.architecture.station;

/**
 * What fills the ground under the tracks (y = -1 across each track's clearance): the ground as it is, or gravel or andesite
 * ballast with a glowstone strip at the foot of every platform wall. Laying it changes terrain, so a build asks for approval
 * wherever it replaces ground.
 */
public enum TrackBed {
	NONE(""), GRAVEL("minecraft:gravel"), ANDESITE("minecraft:andesite");

	/** The strip of light along the foot of a platform wall. */
	public static final String LIGHT = "minecraft:glowstone";

	private final String blockId;

	TrackBed(String blockId) {
		this.blockId = blockId;
	}

	public String blockId() {
		return blockId;
	}

	/** Falls without a block under it, so the cell below needs support. */
	public boolean falls() {
		return this == GRAVEL;
	}
}
