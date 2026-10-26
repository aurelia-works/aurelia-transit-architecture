package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.PlatformRail;
import com.aureliatransit.architecture.station.WorldOp;

import java.util.List;

/**
 * The MTR station area a build registers: the station's name, a colour and the ground footprint of what it places (terrain
 * clearing and fill left out), one block wider all round so the platforms' rail nodes at the ends are inside. MTR areas are
 * columns: like the dashboard, the zone covers every height.
 */
public record StationZone(String name, int color, int minX, int minZ, int maxX, int maxZ, int centerY, List<PlatformRail> rails, List<com.aureliatransit.architecture.station.AirportRails.AirRail> airRails) {

	public StationZone {
		rails = List.copyOf(rails);
		airRails = List.copyOf(airRails);
	}

	/** The same zone with the platform rails the build lays and numbers. */
	public StationZone withRails(List<PlatformRail> platformRails) {
		return new StationZone(name, color, minX, minZ, maxX, maxZ, centerY, platformRails, airRails);
	}

	/** The same zone with the gate-loop rails of an airport, which the build lays. */
	public StationZone withAirRails(List<com.aureliatransit.architecture.station.AirportRails.AirRail> loopRails) {
		return new StationZone(name, color, minX, minZ, maxX, maxZ, centerY, rails, loopRails);
	}

	public static final String UNNAMED = "New Station";
	static final int MARGIN = 1;

	/** The zone of a build's ops, or null when nothing is placed. */
	public static StationZone of(String stationName, List<WorldOp> ops) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (final WorldOp op : ops) {
			if (op.kind() == WorldOp.Kind.CLEAR || op.kind() == WorldOp.Kind.FILL) {
				continue;
			}
			minX = Math.min(minX, op.x());
			minZ = Math.min(minZ, op.z());
			maxX = Math.max(maxX, op.x());
			maxZ = Math.max(maxZ, op.z());
			minY = Math.min(minY, op.y());
			maxY = Math.max(maxY, op.y());
		}
		if (minX > maxX) {
			return null;
		}
		final String name = stationName == null || stationName.isBlank() ? UNNAMED : stationName.trim();
		return new StationZone(name, colorFor(name), minX - MARGIN, minZ - MARGIN, maxX + MARGIN, maxZ + MARGIN, (minY + maxY) / 2, List.of(), List.of());
	}

	/** A steady, readable colour per name (MTR shows it on the map and route maps). */
	static int colorFor(String name) {
		final float hue = Math.floorMod(name.hashCode(), 360) / 360F;
		return java.awt.Color.HSBtoRGB(hue, 0.55F, 0.8F) & 0xFFFFFF;
	}

	public boolean overlaps(long otherMinX, long otherMinZ, long otherMaxX, long otherMaxZ) {
		return otherMinX <= maxX && otherMaxX >= minX && otherMinZ <= maxZ && otherMaxZ >= minZ;
	}

	public int centerX() {
		return (minX + maxX) / 2;
	}

	public int centerZ() {
		return (minZ + maxZ) / 2;
	}

	/** Half the zone's diagonal, rounded up: a radius that reaches every corner from the centre. */
	public int reach() {
		final double dx = (maxX - minX) / 2.0;
		final double dz = (maxZ - minZ) / 2.0;
		return (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
	}
}
