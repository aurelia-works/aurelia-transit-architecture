package com.aureliatransit.architecture.station;

import java.util.Objects;

/**
 * What the player asked for, after parsing and defaults: every field is resolved, nothing is left to the planner to guess.
 * Bounds are hard limits; the parser clamps (with a warning) and this constructor rejects anything outside them.
 *
 * @param trackCount     platform tracks, {@link #MIN_TRACKS}..{@link #MAX_TRACKS}
 * @param platformLayout side or island platforms ({@link PlatformLayout#ISLAND} needs at least two tracks)
 * @param platformLength platform length along the track in blocks
 * @param platformWidth  width of every platform in blocks
 * @param name           station name for the name signs; empty means no name signs
 * @param building       the station building's style; {@link BuildingStyle#NONE} for platforms only
 * @param footbridge     a footbridge over the tracks to every platform was asked for (a building adds one when a platform
 *                       needs it)
 * @param shed           the hall roof's cross-section; ignored unless {@code roof} is {@link RoofType#HALL}
 * @param scale          how grand the building is: storeys, wings, tower; ignored without a building
 * @param hub            a terminus whose two outer tracks run on through, past both sides of the head building (the inner
 *                       tracks end at its buffers); needs island platforms and an even number of tracks, at least four
 */
public record StationSpec(int trackCount, PlatformLayout platformLayout, int platformLength, int platformWidth, StationStyle style,
		RoofType roof, SurfaceMaterial surface, RoofMaterial roofMaterial, boolean stepFree, boolean tactile, boolean furniture,
		boolean railNodes, String name, BuildingStyle building, boolean footbridge, boolean terminus, ShedShape shed, StationScale scale, boolean hub) {

	public static final int MIN_TRACKS = 1;
	public static final int MAX_TRACKS = 10;
	public static final int MIN_LENGTH = 16;
	public static final int MAX_LENGTH = 300;
	public static final int MIN_WIDTH = 3;
	public static final int MAX_WIDTH = 8;
	public static final int MAX_NAME = 32;

	public static final int DEFAULT_TRACKS = 2;
	public static final int DEFAULT_LENGTH = 48;
	public static final int DEFAULT_SIDE_WIDTH = 4;
	public static final int DEFAULT_ISLAND_WIDTH = 6;
	/** Side platforms of a footbridge station: wide enough for a two-column stair, so each gets a lift. */
	public static final int DEFAULT_FOOTBRIDGE_SIDE_WIDTH = 6;

	public StationSpec {
		Objects.requireNonNull(platformLayout, "platformLayout");
		Objects.requireNonNull(style, "style");
		Objects.requireNonNull(roof, "roof");
		Objects.requireNonNull(surface, "surface");
		Objects.requireNonNull(roofMaterial, "roofMaterial");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(shed, "shed");
		Objects.requireNonNull(scale, "scale");
		check(trackCount, MIN_TRACKS, MAX_TRACKS, "trackCount");
		check(platformLength, MIN_LENGTH, MAX_LENGTH, "platformLength");
		check(platformWidth, MIN_WIDTH, MAX_WIDTH, "platformWidth");
		if (platformLayout == PlatformLayout.ISLAND && trackCount < 2) {
			throw new IllegalArgumentException("an island platform needs at least two tracks");
		}
		if (terminus && building == BuildingStyle.NONE) {
			throw new IllegalArgumentException("a terminus needs a head building");
		}
		if (terminus && footbridge) {
			throw new IllegalArgumentException("a terminus is reached from its concourse, not a footbridge");
		}
		if (hub && (!terminus || platformLayout != PlatformLayout.ISLAND || trackCount < 4 || trackCount % 2 != 0)) {
			throw new IllegalArgumentException("a hub is a terminus on island platforms with an even number of tracks, at least four");
		}
		if (name.length() > MAX_NAME) {
			throw new IllegalArgumentException("name longer than " + MAX_NAME + " characters");
		}
	}

	private static void check(int value, int min, int max, String what) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(what + " " + value + " outside " + min + ".." + max);
		}
	}

	/** Two tracks, side platforms, 48 x 4, modern style, canopy, step-free, tactile edges, furniture, rail nodes, no name. */
	public static StationSpec defaults() {
		return builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public Builder toBuilder() {
		return new Builder().trackCount(trackCount).platformLayout(platformLayout).platformLength(platformLength).platformWidth(platformWidth)
				.style(style).roof(roof).surface(surface).roofMaterial(roofMaterial).stepFree(stepFree).tactile(tactile).furniture(furniture)
				.railNodes(railNodes).name(name).building(building).footbridge(footbridge).terminus(terminus).shed(shed).scale(scale).hub(hub);
	}

	public boolean hasBuilding() {
		return building != BuildingStyle.NONE;
	}

	/** A station building beside the tracks (a through station's); a terminus has its head building across the track ends. */
	public boolean sideBuilding() {
		return hasBuilding() && !terminus;
	}

	/** The scale the station building (a side building or a terminus's head building) is built at; compact without one. */
	public StationScale buildingScale() {
		return hasBuilding() ? scale : StationScale.COMPACT;
	}

	/** Mutable builder; starts at {@link #defaults()}. {@link #build()} applies the constructor's checks. */
	public static final class Builder {

		private int trackCount = DEFAULT_TRACKS;
		private PlatformLayout platformLayout = PlatformLayout.SIDE;
		private int platformLength = DEFAULT_LENGTH;
		private int platformWidth = DEFAULT_SIDE_WIDTH;
		private StationStyle style = StationStyle.MODERN;
		private RoofType roof = RoofType.CANOPY;
		private SurfaceMaterial surface = SurfaceMaterial.STYLE_DEFAULT;
		private RoofMaterial roofMaterial = RoofMaterial.STYLE_DEFAULT;
		private boolean stepFree = true;
		private boolean tactile = true;
		private boolean furniture = true;
		private boolean railNodes = true;
		private String name = "";
		private BuildingStyle building = BuildingStyle.NONE;
		private boolean footbridge;
		private boolean terminus;
		private ShedShape shed = ShedShape.FLAT;
		private StationScale scale = StationScale.COMPACT;
		private boolean hub;

		private Builder() {
		}

		public Builder trackCount(int value) {
			trackCount = value;
			return this;
		}

		public Builder platformLayout(PlatformLayout value) {
			platformLayout = value;
			return this;
		}

		public Builder platformLength(int value) {
			platformLength = value;
			return this;
		}

		public Builder platformWidth(int value) {
			platformWidth = value;
			return this;
		}

		public Builder style(StationStyle value) {
			style = value;
			return this;
		}

		public Builder roof(RoofType value) {
			roof = value;
			return this;
		}

		public Builder surface(SurfaceMaterial value) {
			surface = value;
			return this;
		}

		public Builder roofMaterial(RoofMaterial value) {
			roofMaterial = value;
			return this;
		}

		public Builder stepFree(boolean value) {
			stepFree = value;
			return this;
		}

		public Builder tactile(boolean value) {
			tactile = value;
			return this;
		}

		public Builder furniture(boolean value) {
			furniture = value;
			return this;
		}

		public Builder railNodes(boolean value) {
			railNodes = value;
			return this;
		}

		public Builder name(String value) {
			name = value;
			return this;
		}

		public Builder building(BuildingStyle value) {
			building = value;
			return this;
		}

		public Builder footbridge(boolean value) {
			footbridge = value;
			return this;
		}

		public Builder terminus(boolean value) {
			terminus = value;
			return this;
		}

		public Builder hub(boolean value) {
			hub = value;
			return this;
		}

		public Builder shed(ShedShape value) {
			shed = value;
			return this;
		}

		public Builder scale(StationScale value) {
			scale = value;
			return this;
		}

		public StationSpec build() {
			return new StationSpec(trackCount, platformLayout, platformLength, platformWidth, style, roof, surface, roofMaterial, stepFree,
					tactile, furniture, railNodes, name, building, footbridge, terminus, shed, scale, hub);
		}
	}
}
