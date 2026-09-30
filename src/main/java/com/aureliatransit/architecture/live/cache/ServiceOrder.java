package com.aureliatransit.architecture.live.cache;

import com.aureliatransit.architecture.transit.ServiceSnapshot;

import java.util.Comparator;

/**
 * The one ordering of departure rows. It is total over the fields that identify a service, so identical data always
 * gives identical row order no matter in which order MTR returned the arrivals: services due at the same second (common
 * with synchronised timetables and whole-second rounding) no longer swap rows between refreshes.
 */
public final class ServiceOrder {

	public static final Comparator<ServiceSnapshot> COMPARATOR = Comparator.comparingLong(ServiceSnapshot::arrivalMillis)
			.thenComparingLong(ServiceSnapshot::departureMillis)
			.thenComparingLong(ServiceSnapshot::platformId)
			.thenComparingLong(ServiceSnapshot::routeId)
			.thenComparing(ServiceSnapshot::destination)
			.thenComparing(ServiceSnapshot::routeNumber)
			.thenComparing(ServiceSnapshot::routeName);

	private ServiceOrder() {
	}
}
