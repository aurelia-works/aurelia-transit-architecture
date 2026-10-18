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
		if (layout.terminus()) {
			return new int[]{(layout.headFrom() + layout.headTo()) / 2, 0, layout.vMax() + 1};
		}
		if (spec.hasBuilding() && layout.buildingWidth() > 0) {
			return new int[]{-1, 0, StationLayout.of(spec, layout).entranceV()};
		}
		final TrackLayout.PlatformBand band = layout.platforms().isEmpty() ? null : layout.platforms().get(0);
		return new int[]{band == null ? 0 : (band.uMin() + band.uMax()) / 2, 0, layout.vMin() - 1};
	}

	/**
	 * The heading a station is built facing: the way the frame's {@code +v} runs. Station Studio draws and exports every station facing
	 * north (a terminus's front to the north, a through station's street to the west).
	 */
	public static Heading heading(StationSpec spec) {
		return Heading.NORTH;
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
