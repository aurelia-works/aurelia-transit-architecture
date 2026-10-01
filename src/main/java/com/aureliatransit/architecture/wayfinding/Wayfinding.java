package com.aureliatransit.architecture.wayfinding;

import net.minecraft.util.math.BlockPos;

/**
 * Global access point of the wayfinding model: holds the installed {@link WayfindingSource} and resolves a block's
 * {@link WayfindingData} into display-ready {@link ResolvedWayfinding}. This is the one call every wayfinding consumer
 * (pylons, directional/platform/exit signs, street blades, bus boards) uses.
 */
public final class Wayfinding {

	private static volatile WayfindingSource source = WayfindingSource.NONE;

	private Wayfinding() {
	}

	public static void install(WayfindingSource value) {
		source = value == null ? WayfindingSource.NONE : value;
	}

	public static WayfindingSource source() {
		return source;
	}

	public static ResolvedWayfinding resolve(BlockPos pos, WayfindingData data) {
		return WayfindingResolver.merge(data, source.facts(pos, data.autoStation(), data.association()));
	}
}
