package com.aureliatransit.architecture.station;

/**
 * What the player asked for when the words describe an airport ("airport with 2 runways and 6 gates"): a station of a different
 * kind, planned by the same pipeline. Kept in {@link StationOptions#airport()}; {@link #NONE} means a railway station.
 * <p>
 * Sizes are in blocks. Real airports are drawn at {@link #BLOCKS_PER_METRE} blocks to the metre, but this first version plans
 * every block into memory, so a runway is capped at {@link #MAX_RUNWAY_LENGTH} (the parser says so when words ask for more).
 *
 * @param runways       parallel runways, {@code 0} for no airport, otherwise {@link #MAX_RUNWAYS} at most
 * @param runwayLength  paved length of each runway
 * @param runwayWidth   width of each runway
 * @param gates         aircraft stands along the apron, each with its boarding bridge when there is a terminal
 * @param terminal      a terminal building behind the apron, with a road in front of it; without one the stands are remote
 */
public record AirportSpec(int runways, int runwayLength, int runwayWidth, int gates, boolean terminal) {

	/** Real airports are built at this many blocks to the metre. */
	public static final double BLOCKS_PER_METRE = 1.5;
	public static final int MAX_RUNWAYS = 2;
	public static final int MIN_RUNWAY_LENGTH = 120;
	/** The longest runway planned today (2,000 m at 1.5 blocks a metre): every block of the plan is held in memory. */
	public static final int MAX_RUNWAY_LENGTH = 3000;
	/** A regional airport's runway, 1,700 m at 1.5 blocks a metre. */
	public static final int DEFAULT_RUNWAY_LENGTH = 2550;
	public static final int MIN_RUNWAY_WIDTH = 11;
	public static final int MAX_RUNWAY_WIDTH = 45;
	/** 30 m at 1.5 blocks a metre. */
	public static final int DEFAULT_RUNWAY_WIDTH = 45;
	public static final int MIN_GATES = 1;
	public static final int MAX_GATES = 12;
	public static final int DEFAULT_GATES = 4;

	public static final AirportSpec NONE = new AirportSpec(0, 0, 0, 0, false);

	public AirportSpec {
		if (runways == 0) {
			if (runwayLength != 0 || runwayWidth != 0 || gates != 0 || terminal) {
				throw new IllegalArgumentException("no runways, but an airport's other settings");
			}
		} else {
			check(runways, 1, MAX_RUNWAYS, "runways");
			check(runwayWidth, MIN_RUNWAY_WIDTH, MAX_RUNWAY_WIDTH, "runwayWidth");
			check(runwayLength, minLength(runwayWidth), MAX_RUNWAY_LENGTH, "runwayLength");
			check(gates, MIN_GATES, Math.min(MAX_GATES, maxGates(runwayLength, runwayWidth)), "gates");
		}
	}

	/** A regional airport: one runway 1,700 m by 30 m, a terminal and four gates. */
	public static AirportSpec defaults() {
		return new AirportSpec(1, DEFAULT_RUNWAY_LENGTH, DEFAULT_RUNWAY_WIDTH, DEFAULT_GATES, true);
	}

	/** True for an airport; false for a railway station. */
	public boolean present() {
		return runways > 0;
	}

	/** The most gates that fit along a runway this long and wide: the apron stays clear of the taxiway links at both ends. */
	public static int maxGates(int runwayLength, int runwayWidth) {
		final AirportGeometry.Dims dims = AirportGeometry.dims(runwayWidth);
		return Math.max(0, (runwayLength - dims.apronClear()) / dims.standPitch());
	}

	/** The shortest runway of this width: long enough for one gate. */
	public static int minLength(int runwayWidth) {
		return AirportGeometry.dims(runwayWidth).minLength();
	}

	/** Blocks for a length in metres at the airport scale, rounded. */
	public static int blocks(double metres) {
		return (int) Math.round(metres * BLOCKS_PER_METRE);
	}

	private static void check(int value, int min, int max, String what) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(what + " " + value + " outside " + min + ".." + max);
		}
	}
}
