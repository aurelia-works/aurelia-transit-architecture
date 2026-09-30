package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.wayfinding.LineBadges;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the {@link SystemMap} from plain route data (no MTR types). Pure and deterministic: shuffled input gives an
 * identical map.
 *
 * <ol>
 *     <li>Each route's stops are taken in route order with consecutive duplicates (same station id) removed. Stops
 *     without a positive station id or a name are dropped: stations are never invented.</li>
 *     <li>Routes of the same line (equal badge label and colour) collapse into one {@link MapLine}. MTR models the two
 *     directions, branches and short-turns as separate routes; the line shows the most complete one: the route with the
 *     most distinct stations, ties to the lower route id. Other variants are not merged (no invented topology).</li>
 *     <li>A stop is {@code transfer} when at least two map lines serve its station; {@code current} when it is
 *     {@code currentStationId}.</li>
 *     <li>Order: lines serving the current station first, then numeric-aware by label, colour, id. At most
 *     {@link SystemMap#MAX_LINES} lines of {@link SystemMap#MAX_STOPS} stops (a longer line is cut to its first
 *     stops).</li>
 * </ol>
 */
public final class SystemMapBuilder {

	public record RawStop(long stationId, String name) {
	}

	public record RawRoute(long id, LineBadge badge, String name, List<RawStop> stops) {
	}

	private record Candidate(long id, LineBadge badge, String name, List<RawStop> stops, int distinct) {
	}

	private SystemMapBuilder() {
	}

	public static SystemMap build(List<RawRoute> routes, long currentStationId) {
		final Map<String, Candidate> best = new LinkedHashMap<>();
		for (final RawRoute route : routes) {
			if (route.badge() == null || route.badge().isEmpty()) {
				continue;
			}
			final List<RawStop> stops = cleaned(route.stops());
			if (stops.isEmpty()) {
				continue;
			}
			final Set<Long> ids = new HashSet<>();
			stops.forEach(stop -> ids.add(stop.stationId()));
			final Candidate candidate = new Candidate(route.id(), route.badge(), route.name() == null ? "" : route.name(), stops, ids.size());
			final String key = route.badge().label() + "#" + route.badge().rgb();
			final Candidate existing = best.get(key);
			if (existing == null || candidate.distinct() > existing.distinct() || (candidate.distinct() == existing.distinct() && candidate.id() < existing.id())) {
				best.put(key, candidate);
			}
		}
		final List<Candidate> ordered = new ArrayList<>(best.values());
		ordered.sort((a, b) -> {
			final boolean ca = serves(a, currentStationId);
			final boolean cb = serves(b, currentStationId);
			if (ca != cb) {
				return ca ? -1 : 1;
			}
			final int byLabel = LineBadges.compareLabels(a.badge().label(), b.badge().label());
			if (byLabel != 0) {
				return byLabel;
			}
			final int byColour = Integer.compare(a.badge().rgb(), b.badge().rgb());
			return byColour != 0 ? byColour : Long.compare(a.id(), b.id());
		});
		final List<Candidate> kept = ordered.size() > SystemMap.MAX_LINES ? ordered.subList(0, SystemMap.MAX_LINES) : ordered;

		final Map<Long, Integer> served = new HashMap<>();
		for (final Candidate line : kept) {
			final Set<Long> ids = new HashSet<>();
			for (final RawStop stop : bounded(line.stops())) {
				if (ids.add(stop.stationId())) {
					served.merge(stop.stationId(), 1, Integer::sum);
				}
			}
		}
		final List<MapLine> lines = new ArrayList<>(kept.size());
		for (final Candidate line : kept) {
			final List<MapStop> stops = new ArrayList<>();
			for (final RawStop stop : bounded(line.stops())) {
				stops.add(new MapStop(stop.stationId(), stop.name(), stop.stationId() == currentStationId && currentStationId != 0, served.get(stop.stationId()) >= 2));
			}
			lines.add(new MapLine(line.id(), line.badge(), line.name(), stops));
		}
		return new SystemMap(lines);
	}

	private static boolean serves(Candidate line, long stationId) {
		if (stationId == 0) {
			return false;
		}
		for (final RawStop stop : bounded(line.stops())) {
			if (stop.stationId() == stationId) {
				return true;
			}
		}
		return false;
	}

	private static List<RawStop> bounded(List<RawStop> stops) {
		return stops.size() > SystemMap.MAX_STOPS ? stops.subList(0, SystemMap.MAX_STOPS) : stops;
	}

	private static List<RawStop> cleaned(List<RawStop> stops) {
		final List<RawStop> out = new ArrayList<>(stops.size());
		long previous = Long.MIN_VALUE;
		for (final RawStop stop : stops) {
			if (stop.stationId() <= 0 || stop.name() == null || stop.name().isBlank() || stop.stationId() == previous) {
				continue;
			}
			out.add(stop);
			previous = stop.stationId();
		}
		return out;
	}
}
