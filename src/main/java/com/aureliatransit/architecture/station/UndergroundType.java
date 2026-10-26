package com.aureliatransit.architecture.station;

/**
 * The kind of underground station a spec asks for (1.7.0). The first is the shallow U-Bahn box of 1.5 ({@link PlanUnderground});
 * the other two are the deep types, planned by {@link PlanDeep} from the research in {@code docs/research/underground_types.md}.
 */
public enum UndergroundType {
	/** A box just under the street, stairs from the platforms straight up to it (Berlin's Rosenthaler Platz, about 5 m down). */
	SHALLOW("shallow U-Bahn box", 0),
	/**
	 * A bored tube station (Angel, Westminster): twin round bores with the platforms facing each other through a pillar with
	 * cross-passages, and one long escalator shaft up to a ticket hall at the surface. Angel is 27 m down.
	 */
	DEEP_TUBE("deep tube", 40),
	/**
	 * A cut-and-cover box station (Canary Wharf, Vijzelgracht): one deep rectangular box, a mezzanine at each end, long
	 * escalator banks and a glass canopy at each street entrance. Canary Wharf is 24 m down.
	 */
	CUT_AND_COVER("cut-and-cover box", 36),
	/**
	 * A bored tube whose two platforms are at different heights (Westminster's deep Jubilee platforms, one above the other in a
	 * 39 m box): {@link StationGeometry#STACK_RISE} between them, a bank of escalators and a three-stop lift joining the levels.
	 */
	DEEP_STACKED("stacked deep tube", 58);

	private final String label;
	private final int defaultDepth;

	UndergroundType(String label, int defaultDepth) {
		this.label = label;
		this.defaultDepth = defaultDepth;
	}

	public String label() {
		return label;
	}

	/** Blocks from rail level up to the street for this type when no depth is asked (0 for the shallow box, which has its own). */
	public int defaultDepth() {
		return defaultDepth;
	}

	/** True for the bored types: twin bores, a pillar between them and one escalator shaft. */
	public boolean tube() {
		return this == DEEP_TUBE || this == DEEP_STACKED;
	}

	/** True for the types that go deep: bored tube and cut-and-cover. */
	public boolean deep() {
		return this != SHALLOW;
	}
}
