package com.aureliatransit.architecture.terminal;

/**
 * One station on a schematic line.
 *
 * @param current  the terminal's own station
 * @param transfer another line of the map also serves this station
 */
public record MapStop(long stationId, String name, boolean current, boolean transfer) {
}
