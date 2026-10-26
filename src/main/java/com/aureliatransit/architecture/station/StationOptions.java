package com.aureliatransit.architecture.station;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The newer choices a station takes (Enviro's play-test, 10-20), kept together so the spec does not grow a field each.
 *
 * @param gates         which fare gates the gate lines get
 * @param entranceWidth how many blocks wide the station building's street doorway is; 0 for the building's own width
 * @param swaps         blocks the player swapped for others ("use stone bricks instead of sandstone"), planned id to the id
 *                      built in its place, applied to the whole plan last, in order of asking
 * @param underground   the station is a box dug under the street, entered by stairs from it (Enviro, 10-20)
 * @param tile          the block an underground station's walls are tiled with; empty for the operator's or style's own
 * @param sheet         the real station a city word names ("St Pancras", "Frankfurt Hbf", "Amsterdam Centraal"): its sheet in
 *                      {@link Landmarks} is built as if the station were named so; empty for none (the station's own name still wins)
 * @param airport       the airport the words describe ({@link AirportSpec#NONE} for a railway station): the spec then plans an airport
 */
public record StationOptions(GateStyle gates, int entranceWidth, Map<String, String> swaps, boolean underground, String tile, String sheet,
		AirportSpec airport) {

	public static final int MIN_ENTRANCE = 3;
	public static final int MAX_ENTRANCE = 9;
	public static final int MAX_SWAPS = 32;
	public static final StationOptions DEFAULTS = new StationOptions(GateStyle.MTR, 0, Map.of(), false, "", "", AirportSpec.NONE);

	public StationOptions {
		Objects.requireNonNull(gates, "gates");
		if (entranceWidth != 0 && (entranceWidth < MIN_ENTRANCE || entranceWidth > MAX_ENTRANCE)) {
			throw new IllegalArgumentException("entranceWidth " + entranceWidth + " outside " + MIN_ENTRANCE + ".." + MAX_ENTRANCE);
		}
		if (swaps.size() > MAX_SWAPS) {
			throw new IllegalArgumentException("more than " + MAX_SWAPS + " block swaps");
		}
		swaps = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(swaps));
		Objects.requireNonNull(tile, "tile");
		Objects.requireNonNull(sheet, "sheet");
		Objects.requireNonNull(airport, "airport");
	}

	public StationOptions withAirport(AirportSpec value) {
		return new StationOptions(gates, entranceWidth, swaps, underground, tile, sheet, value);
	}

	public StationOptions withSheet(String value) {
		return new StationOptions(gates, entranceWidth, swaps, underground, tile, value, airport);
	}

	public StationOptions withGates(GateStyle value) {
		return new StationOptions(value, entranceWidth, swaps, underground, tile, sheet, airport);
	}

	public StationOptions withEntranceWidth(int value) {
		return new StationOptions(gates, value, swaps, underground, tile, sheet, airport);
	}

	public StationOptions withUnderground(boolean value) {
		return new StationOptions(gates, entranceWidth, swaps, value, tile, sheet, airport);
	}

	public StationOptions withTile(String value) {
		return new StationOptions(gates, entranceWidth, swaps, underground, value, sheet, airport);
	}

	/** These options with one more swap ({@code from} built as {@code to}); a later swap of the same block replaces it. */
	public StationOptions withSwap(String from, String to) {
		final Map<String, String> more = new LinkedHashMap<>(swaps);
		more.remove(from);
		more.put(from, to);
		return new StationOptions(gates, entranceWidth, more, underground, tile, sheet, airport);
	}

	/** The block built for planned {@code blockId}: its swap, or itself. */
	public String swapped(String blockId) {
		return swaps.getOrDefault(blockId, blockId);
	}

	/** The fare gates of a gate line. */
	public enum GateStyle {
		/**
		 * MTR's own ticket barriers (Enviro: "just use default MTR gates"): one-way, so each bank has entry lanes (the left
		 * ones and the wide one, seen from the ticket hall) and exit lanes; a glass panel closes each end of a bank.
		 */
		MTR,
		/** ATA's two-way fare gates with their end cabinets and wide gate. */
		ATA
	}
}
