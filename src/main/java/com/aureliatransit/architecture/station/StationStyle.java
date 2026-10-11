package com.aureliatransit.architecture.station;

/**
 * Architectural presets. A style only chooses blocks; the railway geometry never depends on it.
 */
public enum StationStyle {
	/** ATA's generic platform kit: light paving, steel columns, flat canopies. */
	MODERN,
	/** Dutch (Utrecht, Leidsche Rijn): concrete pavers, timber soffit canopies, white tree columns. */
	DUTCH,
	/** German S-Bahn: S-Bahn slabs and edges, round steel columns, S-Bahn lamps. */
	GERMAN_SBAHN,
	/** Metro finishes (terrazzo, tiled columns, recessed lights), built at ground level. */
	METRO
}
