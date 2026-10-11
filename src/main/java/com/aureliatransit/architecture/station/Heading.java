package com.aureliatransit.architecture.station;

/**
 * A horizontal world direction, without Minecraft classes (north = -z, east = +x).
 */
public enum Heading {
	NORTH(0, -1),
	EAST(1, 0),
	SOUTH(0, 1),
	WEST(-1, 0);

	private final int dx;
	private final int dz;

	Heading(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	public int dx() {
		return dx;
	}

	public int dz() {
		return dz;
	}

	/** Clockwise seen from above: north to east. */
	public Heading right() {
		return values()[(ordinal() + 1) & 3];
	}

	public Heading left() {
		return values()[(ordinal() + 3) & 3];
	}

	public Heading opposite() {
		return values()[(ordinal() + 2) & 3];
	}

	/** True for east and west. */
	public boolean alongX() {
		return dx != 0;
	}
}
