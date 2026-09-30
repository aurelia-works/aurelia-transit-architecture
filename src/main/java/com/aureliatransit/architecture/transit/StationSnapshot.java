package com.aureliatransit.architecture.transit;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable, cached view of a station for one association. Consumers (renderers, announcement engine) must treat it as
 * read-only and may cache derived layout keyed by {@link #version()}, which changes only when the content changes.
 *
 * @param station  resolved station, or null when no MTR station could be associated
 * @param services upcoming services sorted by arrival; empty unless services were requested
 */
public record StationSnapshot(@Nullable StationReference station, List<PlatformReference> platforms, List<ServiceSnapshot> services, long version) {

	public static final int MAX_SERVICES = 12;
	public static final StationSnapshot EMPTY = new StationSnapshot(null, List.of(), List.of(), 0);

	public StationSnapshot {
		platforms = List.copyOf(platforms);
		services = List.copyOf(services.size() > MAX_SERVICES ? services.subList(0, MAX_SERVICES) : services);
	}

	public boolean hasStation() {
		return station != null;
	}
}
