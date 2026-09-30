package com.aureliatransit.architecture.live.cache;

import com.aureliatransit.architecture.transit.ServiceSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A platform set's service list plus when MTR last returned any service for it.
 *
 * <p>MTR's client arrivals cache is replaced wholesale by every response, and a platform that briefly drops out of a
 * response reads as "no services". Without a hold, a board with valid departures flashed to its idle text for one
 * refresh. {@link #next} keeps the previous services that have not yet departed for up to {@link #GRACE_MILLIS} after
 * the last non-empty result; after that an empty result is shown as empty. Nothing is invented: only services MTR
 * reported, and never past their departure.
 */
public record HeldServices(List<ServiceSnapshot> services, long lastDataMillis) {

	public static final long GRACE_MILLIS = 10_000;

	public HeldServices {
		services = List.copyOf(services);
	}

	public static HeldServices next(@Nullable HeldServices previous, List<ServiceSnapshot> fresh, long now) {
		if (!fresh.isEmpty()) {
			return new HeldServices(fresh, now);
		}
		if (previous == null || previous.services.isEmpty() || now - previous.lastDataMillis > GRACE_MILLIS) {
			return new HeldServices(List.of(), previous == null ? Long.MIN_VALUE : previous.lastDataMillis);
		}
		final List<ServiceSnapshot> kept = new ArrayList<>(previous.services.size());
		for (final ServiceSnapshot service : previous.services) {
			if (service.departureMillis() >= now) {
				kept.add(service);
			}
		}
		return kept.size() == previous.services.size() ? previous : new HeldServices(kept, previous.lastDataMillis);
	}
}
