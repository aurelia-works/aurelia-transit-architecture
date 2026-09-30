package com.aureliatransit.architecture.wayfinding;

import java.util.List;

/**
 * Merges manual configuration with MTR facts. Rule: a non-empty manual value always wins; an {@code auto*} flag lets
 * MTR fill an empty field; nothing else is filled in.
 *
 * <p>LEAD STUB - owned by the wayfinding-logic workstream (subagent A), which refines service texts, exit matching and
 * tests. Keep the signature.
 */
public final class WayfindingResolver {

	private WayfindingResolver() {
	}

	public static ResolvedWayfinding merge(WayfindingData data, StationFacts facts) {
		final boolean mtrName = data.stationName().isEmpty() && data.autoStation() && facts.station() != null;
		final String name = mtrName ? facts.station().displayName() : data.stationName();
		final List<LineBadge> lines = data.lines().isEmpty() && data.autoLines() ? facts.lines() : data.lines();
		final String service = switch (data.serviceType()) {
			case NONE -> "";
			case LOCAL -> "Local";
			case EXPRESS -> "Express";
			case LIMITED -> "Limited";
			case CUSTOM -> data.serviceLabel();
		};
		List<String> exitDestinations = List.of();
		if (!data.exitLabel().isEmpty()) {
			for (final ExitInfo exit : facts.exits()) {
				if (exit.label().equalsIgnoreCase(data.exitLabel())) {
					exitDestinations = exit.destinations();
					break;
				}
			}
		}
		return new ResolvedWayfinding(name, data.secondaryName(), data.stationCode(), lines, data.arrow(), data.destination(), data.serviceType(), service,
				data.platform(), data.exitLabel(), exitDestinations, data.streetLabel(), data.transfers(), data.languageLayout(), data.pictogram(),
				data.accent(), mtrName);
	}
}
