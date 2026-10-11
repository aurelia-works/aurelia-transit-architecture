package com.aureliatransit.architecture.station;

/**
 * Places a station's local frame in the world: local (u, y, v) = (0, 0, 0) is world (ox, oy, oz), +v points {@code forward}
 * and +u to its right.
 */
public record LocalFrame(int ox, int oy, int oz, Heading forward) {

	/** Cells between the planning player and the first footprint cell. */
	public static final int GAP_IN_FRONT = 2;

	/**
	 * The frame for a player standing at (px, py, pz) and looking {@code facing}: rail level is the player's feet, the footprint
	 * starts {@link #GAP_IN_FRONT} cells ahead and is centred across the player.
	 */
	public static LocalFrame inFrontOf(int px, int py, int pz, Heading facing, TrackLayout layout) {
		final Heading right = facing.right();
		final int ahead = GAP_IN_FRONT + layout.rampLength();
		final int across = -(layout.width() / 2);
		return new LocalFrame(px + facing.dx() * ahead + right.dx() * across, py, pz + facing.dz() * ahead + right.dz() * across, facing);
	}

	public int x(int u, int v) {
		return ox + forward.right().dx() * u + forward.dx() * v;
	}

	public int y(int y) {
		return oy + y;
	}

	public int z(int u, int v) {
		return oz + forward.right().dz() * u + forward.dz() * v;
	}

	public Heading heading(LocalDir dir) {
		return switch (dir) {
			case PLUS_V -> forward;
			case MINUS_V -> forward.opposite();
			case PLUS_U -> forward.right();
			case MINUS_U -> forward.left();
		};
	}
}
