package com.aureliatransit.architecture.transit;

/**
 * An MTR platform. {@code name} is the platform number/name as entered in MTR (e.g. "3").
 */
public record PlatformReference(long id, String name, long stationId) {
}
