package com.aureliatransit.architecture.station;

/**
 * What fills the ground under the tracks (y = -1 across each track's clearance): the ground as it is, or gravel or andesite
 * ballast from wall to wall, the row against each platform wall with a lit channel under the platform's lip. Laying it changes terrain, so a build asks for approval
 * wherever it replaces ground.
 */
public enum TrackBed {
	NONE(""), GRAVEL("minecraft:gravel"), ANDESITE("minecraft:andesite");

	private final String blockId;

	TrackBed(String blockId) {
		this.blockId = blockId;
	}

	public String blockId() {
		return blockId;
	}

	/** The same ballast with a lit channel along its front edge, laid against a platform wall; empty for {@link #NONE}. */
	public String light() {
		return switch (this) {
			case NONE -> "";
			case GRAVEL -> "aurelia_transit_architecture:gravel_bed_light";
			case ANDESITE -> "aurelia_transit_architecture:andesite_bed_light";
		};
	}

	/** Falls without a block under it, so the cell below needs support. */
	public boolean falls() {
		return this == GRAVEL;
	}
}
