package com.aureliatransit.architecture.live.cache;

import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Hands out {@link StationSnapshot}s whose {@code version} changes only when their content changes: if a refresh
 * produces content equal to the previous snapshot, the previous instance (and version) is returned unchanged.
 */
public final class SnapshotVersions {

	private long counter;

	public StationSnapshot stabilize(@Nullable StationSnapshot previous, @Nullable StationReference station, List<PlatformReference> platforms, List<ServiceSnapshot> services) {
		final StationSnapshot candidate = new StationSnapshot(station, platforms, services, 0);
		if (previous != null && previous != StationSnapshot.EMPTY && sameContent(previous, candidate)) {
			return previous;
		}
		if (station == null && candidate.platforms().isEmpty() && candidate.services().isEmpty()) {
			return StationSnapshot.EMPTY;
		}
		return new StationSnapshot(station, platforms, services, ++counter);
	}

	private static boolean sameContent(StationSnapshot a, StationSnapshot b) {
		return Objects.equals(a.station(), b.station()) && a.platforms().equals(b.platforms()) && a.services().equals(b.services());
	}
}
