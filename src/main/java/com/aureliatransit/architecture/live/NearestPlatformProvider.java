package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.transit.StationAssociation;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Optional extension of the station data provider used by platform displays: the id of the MTR platform right next to
 * a block, so an AUTO-associated CIS/PIDS can show only its own platform instead of the whole station.
 * Implemented by the MTR-backed provider (an additive helper, not part of the shared StationDataProvider contract).
 */
public interface NearestPlatformProvider {

	/**
	 * A display looks for the closest platform within this many blocks of its position lowered by {@link #SEARCH_DROP}.
	 * MTR's own PIDS uses 5, which misses the platforms of a screen on the middle column of a generated 11-wide island
	 * (6-7 blocks from either track; Jah 10-26: every board in a 5-island hub showed the same station-wide list) and the
	 * wall screens of a cut-and-cover box's wide side platforms (10-11). The closest platform still wins, so a wider
	 * search cannot pick a farther one. The planner keeps every platform screen within reach ({@code StationPlannerTest}).
	 */
	int SEARCH_RADIUS = 12;
	int SEARCH_DROP = 4;
	/**
	 * A platform within this many blocks of the closest one belongs to the same island: a board over an island serves both
	 * its tracks, not only the one MTR's single closest-platform search happens to pick (Jah 10-26: per island platform PIDS).
	 * A side platform's far neighbour is across at least the two tracks, so it stays out.
	 */
	double ISLAND_SLACK = 2.5;
	/** An island has two faces. */
	int MAX_HERE = 2;

	/** A platform in reach of a display and how far it is. */
	record Candidate(long id, double distance) {
	}

	/**
	 * @return the platform id close to {@code pos}, or 0 when there is none (or the association is MANUAL)
	 */
	long nearestPlatformId(BlockPos pos, StationAssociation association);

	/**
	 * @return the platforms a platform display beside {@code pos} serves, closest first: its platform, or both faces of its
	 * island; empty when there is none (or the association is MANUAL)
	 */
	default List<Long> platformIdsHere(BlockPos pos, StationAssociation association) {
		final long id = nearestPlatformId(pos, association);
		return id == 0 ? List.of() : List.of(id);
	}

	/** Pure: the closest candidate plus any within {@link #ISLAND_SLACK} of it, closest first, at most {@link #MAX_HERE}. */
	static List<Long> island(List<Candidate> candidates) {
		final List<Candidate> sorted = new ArrayList<>(candidates);
		sorted.sort(Comparator.comparingDouble(Candidate::distance).thenComparingLong(Candidate::id));
		final List<Long> out = new ArrayList<>(MAX_HERE);
		for (final Candidate candidate : sorted) {
			if (out.size() == MAX_HERE || candidate.distance() > sorted.get(0).distance() + ISLAND_SLACK) {
				break;
			}
			out.add(candidate.id());
		}
		return List.copyOf(out);
	}
}
