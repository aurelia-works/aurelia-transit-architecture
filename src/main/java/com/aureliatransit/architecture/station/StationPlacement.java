package com.aureliatransit.architecture.station;

/**
 * Where a station stands relative to a point a person can name: the paste origin of an exported schematic, which is also the
 * anchor {@code /ata_station verify paste} uses. No Minecraft classes.
 */
public final class StationPlacement {

	private StationPlacement() {
	}

	/**
	 * The paste origin as {@code {u, y, v}} in the plan's own frame: on the ground just outside the footprint, in line with the
	 * street entrance (the column before the car park or apron of a through station's building, the cell past a terminus's front), so
	 * standing there and pasting puts the station in front of you. Nothing is planned there, so there is room to stand. A station
	 * without a building is anchored in front of platform 1's first ramp (or its first cell).
	 */
	public static int[] pasteOrigin(StationSpec spec, StationPlan plan) {
		final TrackLayout layout = plan.layout();
		if (spec.isAirport()) {
			// beside the road in front of the terminal (or the apron), level with the middle of the gates
			final AirportGeometry airport = AirportGeometry.of(spec.airport());
			return new int[]{-1, 0, (airport.apronV0() + airport.apronV1()) / 2};
		}
		if (layout.terminus()) {
			return new int[]{(layout.headFrom() + layout.headTo()) / 2, 0, layout.vMax() + 1};
		}
		if (spec.hasBuilding() && layout.buildingWidth() > 0) {
			return new int[]{-1, 0, StationLayout.of(spec, layout).entranceV()};
		}
		final TrackLayout.PlatformBand band = layout.platforms().isEmpty() ? null : layout.platforms().get(0);
		// an underground station's is on the street over its end wall
		if (spec.options().deepStation()) {
			return new int[]{layout.width() / 2, depth(spec), layout.vMin() - 1};
		}
		return new int[]{band == null ? 0 : (band.uMin() + band.uMax()) / 2, depth(spec), layout.vMin() - 1};
	}

	/**
	 * How far below the street an underground station's rail level is: the box's ceiling slab is the street's surface, so a
	 * player standing on it (where the station is planned from) is at local {@link StationGeometry#UPPER_WALK_Y}; a deep station's street is {@link StationOptions#streetDepth} up. 0 for a
	 * station at ground level.
	 */
	public static int depth(StationSpec spec) {
		if (spec.options().deepStation()) {
			return spec.options().streetDepth();
		}
		return spec.options().underground() ? StationGeometry.UPPER_WALK_Y : 0;
	}

	/** {@code frame} (rail level at the player's feet) lowered by the station's {@link #depth}. */
	public static LocalFrame sunk(StationSpec spec, LocalFrame frame) {
		final int depth = depth(spec);
		return depth == 0 ? frame : new LocalFrame(frame.ox(), frame.oy() - depth, frame.oz(), frame.forward());
	}

	/**
	 * The heading a station is built facing: the way the frame's {@code +v} runs. A station asked to face a compass direction ("facing
	 * north", {@link StationSpec#facing()}) has its street side, the way its entrance looks, that way: a terminus's head building is across
	 * the end of the tracks, so it faces the way {@code +v} runs; a through station's building is on the left of {@code +v}, so the
	 * heading is a quarter turn clockwise from the facing. A station not asked is drawn and exported facing north, which is also Station
	 * Studio's own view (a terminus's front to the north, a through station's street to the west); in the game it is built the way the
	 * player looks.
	 */
	public static Heading heading(StationSpec spec) {
		final Heading facing = spec.facing().direction();
		if (facing == null) {
			return Heading.NORTH;
		}
		return spec.terminus() ? facing : facing.right();
	}

	/** The frame Station Studio draws and exports the station in: its origin at 0, 0, 0 and {@link #heading}. */
	public static LocalFrame frame(StationSpec spec) {
		return new LocalFrame(0, 0, 0, heading(spec));
	}

	/** The frame that puts the plan's paste origin on the world cell ({@code x}, {@code y}, {@code z}), the station facing {@code heading}. */
	public static LocalFrame frameAt(StationSpec spec, StationPlan plan, Heading heading, int x, int y, int z) {
		final int[] origin = pasteOrigin(spec, plan);
		final LocalFrame zero = new LocalFrame(0, 0, 0, heading);
		return new LocalFrame(x - zero.x(origin[0], origin[2]), y - origin[1], z - zero.z(origin[0], origin[2]), heading);
	}
}
