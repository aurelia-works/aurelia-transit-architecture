package com.aureliatransit.architecture.registry;

/**
 * Registers the 1.2 wayfinding, accessibility and street/bus presentation blocks (and their block entity types) via
 * {@link ModBlocks#register}. Owned by the presentation workstream (subagent B). Use families WAYFINDING,
 * ACCESSIBILITY, STREET or BUS so each block lands in the right creative tab.
 */
public final class WayfindingBlocks {

	private WayfindingBlocks() {
	}

	public static void init() {
		// Class loading registers everything declared here.
	}
}
