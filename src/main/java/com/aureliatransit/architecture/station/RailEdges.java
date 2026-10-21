package com.aureliatransit.architecture.station;

import java.util.Collection;
import java.util.StringJoiner;

/**
 * The sides of its block a railing stands on, flush with the drops it guards (RailingBlock's {@code edge_north} and so on).
 * A plan records them against the railing's front, as {@code edges=0+1}: quarter turns clockwise from the front (0 the
 * front, 1 its right, 2 the back, 3 its left), so they turn with the station; {@link #worldState} names the world sides.
 * <p>
 * Where two runs meet round an outside corner (a fence along a pit's side and one across its end), their rails stand in
 * two blocks and touch only at a point: one of them carries a corner post there, {@code corner=1c}: the side its rail is
 * on, as a quarter turn from the front, and the end of that rail, clockwise ({@code c}) or anticlockwise ({@code a}),
 * named in the world {@code corner=east_cw}.
 */
public final class RailEdges {

	private static final String KEY = "edges=";
	private static final String CORNER = "corner=";

	private RailEdges() {
	}

	/** Clockwise quarter turns from {@code from} to {@code to} (+U is clockwise from +V). */
	static int turns(LocalDir from, LocalDir to) {
		return Math.floorMod(index(to) - index(from), 4);
	}

	private static int index(LocalDir dir) {
		return switch (dir) {
			case PLUS_V -> 0;
			case PLUS_U -> 1;
			case MINUS_V -> 2;
			case MINUS_U -> 3;
		};
	}

	/** The side a quarter turn clockwise from {@code dir}. */
	static LocalDir clockwise(LocalDir dir) {
		return switch (dir) {
			case PLUS_V -> LocalDir.PLUS_U;
			case PLUS_U -> LocalDir.MINUS_V;
			case MINUS_V -> LocalDir.MINUS_U;
			case MINUS_U -> LocalDir.PLUS_V;
		};
	}

	/** The plan's state for a corner post at the {@code clockwise} (or anticlockwise) end of the rail on {@code side}. */
	static String planCorner(LocalDir front, LocalDir side, boolean clockwise) {
		return CORNER + turns(front, side) + (clockwise ? "c" : "a");
	}

	/** The plan's state for a railing fronting {@code front} that stands on {@code sides}. */
	static String planState(LocalDir front, Collection<LocalDir> sides) {
		final StringJoiner out = new StringJoiner("+", KEY, "");
		sides.stream().distinct().mapToInt(side -> turns(front, side)).sorted().forEach(turn -> out.add(Integer.toString(turn)));
		return out.toString();
	}

	/** {@code state} with its {@code edges=} and {@code corner=} entries (if any) written as world sides for a block fronting {@code front}. */
	public static String worldState(String state, Heading front) {
		if (state == null || !state.contains(KEY) && !state.contains(CORNER)) {
			return state;
		}
		final StringJoiner out = new StringJoiner(",");
		for (final String pair : state.split(",")) {
			if (pair.startsWith(KEY)) {
				for (final String turn : pair.substring(KEY.length()).split("\\+")) {
					out.add("edge_" + side(front, Integer.parseInt(turn.trim())) + "=true");
				}
			} else if (pair.startsWith(CORNER)) {
				final String corner = pair.substring(CORNER.length()).trim();
				out.add(CORNER + side(front, Integer.parseInt(corner.substring(0, corner.length() - 1)))
						+ (corner.endsWith("c") ? "_cw" : "_ccw"));
			} else {
				out.add(pair);
			}
		}
		return out.toString();
	}

	private static String side(Heading front, int turns) {
		Heading side = front;
		for (int i = turns; i > 0; i--) {
			side = side.right();
		}
		return side.name().toLowerCase(java.util.Locale.ROOT);
	}
}
