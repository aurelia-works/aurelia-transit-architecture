package com.aureliatransit.architecture.station;

/**
 * An inclusive box of cells in local coordinates (see {@link PlannedBlock}).
 */
public record Box(int uMin, int yMin, int vMin, int uMax, int yMax, int vMax) {

	public Box {
		if (uMax < uMin || yMax < yMin || vMax < vMin) {
			throw new IllegalArgumentException("empty box " + uMin + "," + yMin + "," + vMin + " .. " + uMax + "," + yMax + "," + vMax);
		}
	}

	public boolean contains(int u, int y, int v) {
		return u >= uMin && u <= uMax && y >= yMin && y <= yMax && v >= vMin && v <= vMax;
	}

	public long volume() {
		return (long) (uMax - uMin + 1) * (yMax - yMin + 1) * (vMax - vMin + 1);
	}
}
