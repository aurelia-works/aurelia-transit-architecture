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
 * @param type          which underground station it is ({@link UndergroundType}); only read when {@code underground}
 * @param depth         blocks from rail level up to the street of a deep type; 0 for the type's own ({@link UndergroundType#defaultDepth})
 * @param sheet         the real station a city word names ("St Pancras", "Frankfurt Hbf", "Amsterdam Centraal"): its sheet in
 *                      {@link Landmarks} is built as if the station were named so; empty for none (the station's own name still wins)
 */
public record StationOptions(GateStyle gates, int entranceWidth, Map<String, String> swaps, boolean underground, String tile, String sheet,
		UndergroundType type, int depth) {

	public static final int MIN_ENTRANCE = 3;
	public static final int MAX_ENTRANCE = 9;
	public static final int MAX_SWAPS = 32;
	public static final int MIN_DEPTH = 12;
	public static final int MAX_DEPTH = 80;
	public static final StationOptions DEFAULTS = new StationOptions(GateStyle.MTR, 0, Map.of(), false, "", "", UndergroundType.SHALLOW, 0);

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
		Objects.requireNonNull(type, "type");
		if (depth != 0 && (depth < MIN_DEPTH || depth > MAX_DEPTH)) {
			throw new IllegalArgumentException("depth " + depth + " outside " + MIN_DEPTH + ".." + MAX_DEPTH);
		}
	}

	public StationOptions withSheet(String value) {
		return new StationOptions(gates, entranceWidth, swaps, underground, tile, value, type, depth);
	}

	public StationOptions withGates(GateStyle value) {
		return new StationOptions(value, entranceWidth, swaps, underground, tile, sheet, type, depth);
	}

	public StationOptions withEntranceWidth(int value) {
		return new StationOptions(gates, value, swaps, underground, tile, sheet, type, depth);
	}

	public StationOptions withUnderground(boolean value) {
		return new StationOptions(gates, entranceWidth, swaps, value, tile, sheet, type, depth);
	}

	public StationOptions withTile(String value) {
		return new StationOptions(gates, entranceWidth, swaps, underground, value, sheet, type, depth);
	}

	/** These options as an underground station of {@code value}'s kind. */
	public StationOptions withType(UndergroundType value) {
		return new StationOptions(gates, entranceWidth, swaps, true, tile, sheet, value, depth);
	}

	/** These options with the street {@code value} blocks above rail level (0 for the type's own). */
	public StationOptions withDepth(int value) {
		return new StationOptions(gates, entranceWidth, swaps, underground, tile, sheet, type, value);
	}

	/** True for a deep underground station (bored tube or cut-and-cover), planned by {@link PlanDeep}. */
	public boolean deepStation() {
		return underground && type.deep();
	}

	/** True for a deep tube whose second platform is raised over the first ({@link UndergroundType#DEEP_STACKED}). */
	public boolean stacked() {
		return underground && type == UndergroundType.DEEP_STACKED;
	}

	/** Blocks from rail level up to the street of a deep station: the asked depth, else the type's. */
	public int streetDepth() {
		return depth > 0 ? depth : type.defaultDepth();
	}

	/** These options with one more swap ({@code from} built as {@code to}); a later swap of the same block replaces it. */
	public StationOptions withSwap(String from, String to) {
		final Map<String, String> more = new LinkedHashMap<>(swaps);
		more.remove(from);
		more.put(from, to);
		return new StationOptions(gates, entranceWidth, more, underground, tile, sheet, type, depth);
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
