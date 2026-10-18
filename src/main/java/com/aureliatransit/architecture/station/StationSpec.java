package com.aureliatransit.architecture.station;

import java.util.List;
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
 * @param canopy         the profile of a platform canopy's roof; ignored by a hall roof
 * @param scale          how grand the building is: storeys, wings, tower; ignored without a building
 * @param hub            a terminus whose two outer tracks run on through, past both sides of the head building (the inner
 *                       tracks end at its buffers); needs island platforms and an even number of tracks, at least four
 * @param platforms      each platform's own settings, left to right, when they are not all alike (empty otherwise); the list
 *                       fixes the tracks, up to {@link #MAX_LISTED_TRACKS}
 * @param trackBed       what fills the ground under the tracks
 * @param escalators     an up escalator beside each footbridge stair where the platform is wide enough
 * @param stairsBothWays a second footbridge stair to each platform wide enough, from the corridor's other side
 * @param enclosedStairs walls round every footbridge stairwell instead of glass
 * @param travelators    moving walkways along long platforms where there is room
 * @param seed           the generator seed ("seed k7f2ra" in the words): varies what the words left open, such as the canopy
 *                       profile, the materials and where the benches stand; 0 for none, which plans as before
 * @param stationClass   the class the player asked for ("city class"), which filled in what the words left open;
 *                       {@link StationClass#AUTO} when none was asked (the plan then reports {@link StationClass#of})
 * @param era            the era asked for ("postwar era"), which chose the look; {@link StationEra#AUTO} when none was asked
 * @param facing         the compass direction the entrance faces ("facing north"); {@link Facing#AUTO} to leave it to the way the
 *                       player looks. It places the station and changes no block of the plan
 */
public record StationSpec(int trackCount, PlatformLayout platformLayout, int platformLength, int platformWidth, StationStyle style,
		RoofType roof, SurfaceMaterial surface, RoofMaterial roofMaterial, boolean stepFree, boolean tactile, boolean furniture,
		boolean railNodes, String name, BuildingStyle building, boolean footbridge, boolean terminus, ShedShape shed, CanopyShape canopy, StationScale scale, boolean hub,
		List<PlatformSpec> platforms, TrackBed trackBed, boolean escalators,
		boolean stairsBothWays, boolean enclosedStairs, boolean travelators, boolean parking, boolean underpass, boolean tram, boolean mezzanine, long seed, StationClass stationClass, StationEra era,
		Facing facing) {

	public static final int MIN_TRACKS = 1;
	public static final int MAX_TRACKS = 10;
	/** Tracks of a station with a platform list: ten islands bring twenty. */
	public static final int MAX_LISTED_TRACKS = 2 * PlatformSpec.MAX_PLATFORMS;
	public static final int MIN_LENGTH = 16;
	public static final int MAX_LENGTH = 300;
	public static final int MIN_WIDTH = 3;
	public static final int MAX_WIDTH = 20;
	public static final int MAX_NAME = 32;

	public static final int DEFAULT_TRACKS = 2;
	public static final int DEFAULT_LENGTH = 48;
	/**
	 * Platforms are wide by default, as at a main station: room to walk past a lift, a stair and an up escalator
	 * ({@link StationLayout#MIN_ESCALATOR_SIDE}, {@link StationLayout#MIN_ESCALATOR_ISLAND}) and for a travelator lane.
	 * "narrow" or a width in blocks still gives a smaller one.
	 */
	public static final int DEFAULT_SIDE_WIDTH = 10;
	public static final int DEFAULT_ISLAND_WIDTH = 11;
	/** Side platforms of a footbridge station: as wide as any other side platform. */
	public static final int DEFAULT_FOOTBRIDGE_SIDE_WIDTH = DEFAULT_SIDE_WIDTH;
	/** The width a spec built in code starts with; a description always gets one of the defaults above. */
	public static final int BUILDER_WIDTH = 4;

	public StationSpec {
		Objects.requireNonNull(platformLayout, "platformLayout");
		Objects.requireNonNull(style, "style");
		Objects.requireNonNull(roof, "roof");
		Objects.requireNonNull(surface, "surface");
		Objects.requireNonNull(roofMaterial, "roofMaterial");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(shed, "shed");
		Objects.requireNonNull(canopy, "canopy");
		Objects.requireNonNull(scale, "scale");
		Objects.requireNonNull(trackBed, "trackBed");
		Objects.requireNonNull(stationClass, "stationClass");
		Objects.requireNonNull(era, "era");
		Objects.requireNonNull(facing, "facing");
		platforms = List.copyOf(platforms);
		check(trackCount, MIN_TRACKS, platforms.isEmpty() ? MAX_TRACKS : MAX_LISTED_TRACKS, "trackCount");
		if (platforms.size() > PlatformSpec.MAX_PLATFORMS) {
			throw new IllegalArgumentException("more than " + PlatformSpec.MAX_PLATFORMS + " platforms listed");
		}
		if (!platforms.isEmpty() && trackCount != listedTracks(platforms)) {
			throw new IllegalArgumentException("trackCount " + trackCount + " but the listed platforms bring " + listedTracks(platforms));
		}
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

	/** Tracks a platform list brings: one per side platform, two per island. */
	public static int listedTracks(List<PlatformSpec> platforms) {
		int tracks = 0;
		for (final PlatformSpec platform : platforms) {
			tracks += platform.kind().tracks();
		}
		return tracks;
	}

	/** The platforms are listed one by one (Station Studio's advanced platform list). */
	public boolean listed() {
		return !platforms.isEmpty();
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
				.railNodes(railNodes).name(name).building(building).footbridge(footbridge).terminus(terminus).shed(shed).canopy(canopy).scale(scale).hub(hub).platforms(platforms).trackBed(trackBed)
				.escalators(escalators).stairsBothWays(stairsBothWays).enclosedStairs(enclosedStairs).travelators(travelators).parking(parking).underpass(underpass).tram(tram).mezzanine(mezzanine).seed(seed)
				.stationClass(stationClass).era(era).facing(facing);
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
		private int platformWidth = BUILDER_WIDTH;
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
		private CanopyShape canopy = CanopyShape.FLAT;
		private StationScale scale = StationScale.COMPACT;
		private boolean hub;
		private List<PlatformSpec> platforms = List.of();
		private TrackBed trackBed = TrackBed.NONE;
		private boolean escalators;
		private boolean stairsBothWays;
		private boolean enclosedStairs;
		private boolean travelators;
		private boolean parking;
		private boolean underpass;
		private boolean tram;
		private boolean mezzanine;
		private long seed;
		private StationClass stationClass = StationClass.AUTO;
		private StationEra era = StationEra.AUTO;
		private Facing facing = Facing.AUTO;

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

		public Builder platforms(List<PlatformSpec> value) {
			platforms = value;
			return this;
		}

		public Builder trackBed(TrackBed value) {
			trackBed = value;
			return this;
		}

		public Builder escalators(boolean value) {
			escalators = value;
			return this;
		}

		public Builder stairsBothWays(boolean value) {
			stairsBothWays = value;
			return this;
		}

		public Builder enclosedStairs(boolean value) {
			enclosedStairs = value;
			return this;
		}

		public Builder travelators(boolean value) {
			travelators = value;
			return this;
		}

		public Builder parking(boolean value) {
			parking = value;
			return this;
		}

		public Builder underpass(boolean value) {
			underpass = value;
			return this;
		}

		public Builder tram(boolean value) {
			tram = value;
			return this;
		}

		public Builder mezzanine(boolean value) {
			mezzanine = value;
			return this;
		}

		public Builder seed(long value) {
			seed = value;
			return this;
		}

		public Builder stationClass(StationClass value) {
			stationClass = value;
			return this;
		}

		public Builder era(StationEra value) {
			era = value;
			return this;
		}

		public Builder facing(Facing value) {
			facing = value;
			return this;
		}

		public Builder shed(ShedShape value) {
			shed = value;
			return this;
		}

		public Builder canopy(CanopyShape value) {
			canopy = value;
			return this;
		}

		public Builder scale(StationScale value) {
			scale = value;
			return this;
		}

		public StationSpec build() {
			return new StationSpec(trackCount, platformLayout, platformLength, platformWidth, style, roof, surface, roofMaterial, stepFree,
					tactile, furniture, railNodes, name, building, footbridge, terminus, shed, canopy, scale, hub, platforms, trackBed, escalators,
					stairsBothWays, enclosedStairs, travelators, parking, underpass, tram, mezzanine, seed, stationClass, era, facing);
		}
	}
}
