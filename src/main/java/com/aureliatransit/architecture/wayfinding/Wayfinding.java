package com.aureliatransit.architecture.wayfinding;

import net.minecraft.util.math.BlockPos;

/**
 * Global access point of the wayfinding model: holds the installed {@link WayfindingSource} and resolves a block's
 * {@link WayfindingData} into display-ready {@link ResolvedWayfinding}. This is the one call every wayfinding consumer
 * (pylons, directional/platform/exit signs, street blades, bus boards) uses.
 */
public final class Wayfinding {

	private static volatile WayfindingSource source = WayfindingSource.NONE;
	/** Station suffixes (A5), installed by the client; identity on a server. */
	private static volatile java.util.function.BiFunction<String, SuffixContext, String> suffixes = (name, context) -> name;

	private Wayfinding() {
	}

	public static void install(WayfindingSource value) {
		source = value == null ? WayfindingSource.NONE : value;
	}

	public static WayfindingSource source() {
		return source;
	}

	public static void installSuffixes(java.util.function.BiFunction<String, SuffixContext, String> value) {
		suffixes = value == null ? (name, context) -> name : value;
	}

	/** Resolution for signs, pylons and boards ({@link SuffixContext#SIGNS}). */
	public static ResolvedWayfinding resolve(BlockPos pos, WayfindingData data) {
		return resolve(pos, data, SuffixContext.SIGNS);
	}

	/** Resolution in a context: an MTR station name gets the station's suffix where that context shows it. */
	public static ResolvedWayfinding resolve(BlockPos pos, WayfindingData data, SuffixContext context) {
		final java.util.function.BiFunction<String, SuffixContext, String> current = suffixes;
		return WayfindingResolver.merge(data, source.facts(pos, data.autoStation(), data.association()), name -> current.apply(name, context));
	}
}
