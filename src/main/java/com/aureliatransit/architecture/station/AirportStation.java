package com.aureliatransit.architecture.station;

/**
 * The railway station under an airport's terminal (words: "airport with a train station", "transit", "rail link"; Jah 10-26:
 * Schiphol and Zurich style). It is a two-track cut-and-cover box with an island platform ({@link PlanDeep}), planned on its own
 * and moved under the terminal's middle by ({@link #du}, {@link #dy}, {@link #dv}): its street slab becomes the terminal floor, its
 * escalators and lift come up into the hall, and its street pavilions are left out ({@link PlanAirport#railStation}).
 * <p>
 * The honest subset: the tracks end at the box's end walls. The tunnels on to the next station are the player's to dig.
 *
 * @param spec the box station's own spec, named after the airport
 * @param du   columns the box moves across the airport, so it sits in the middle of the terminal's depth
 * @param dy   rows it moves down: its street slab ({@code DEPTH - 1}) lands on the terminal floor (0)
 * @param dv   rows it moves along, so its middle is under the terminal's entrance
 */
public record AirportStation(StationSpec spec, int du, int dy, int dv) {

	/** Blocks from rail level up to the terminal floor. */
	public static final int DEPTH = 16;
	/** Columns the box takes across, walls included: u 0..WIDTH-1 of its own plan (two tracks either side of an island). */
	static final int WIDTH = 19;
	/** Rows its escalator wells reach past each platform end, walls included. */
	static final int END = 14;
	/** Rows kept between the box's ends and the terminal's end walls. */
	static final int MARGIN = 2;
	/** The shallowest terminal with room for the box and its own walls. */
	public static final int MIN_TERMINAL_DEPTH = WIDTH + 2;
	static final int MIN_LENGTH = 48;
	static final int MAX_LENGTH = 200;

	/** The platform length that fits under this terminal, in tens of blocks, at most {@link #MAX_LENGTH}. */
	static int length(AirportGeometry air) {
		final int rows = air.terminalV1() - air.terminalV0() + 1 - 2 * (END + MARGIN);
		return Math.min(MAX_LENGTH, Math.max(0, rows) / 10 * 10);
	}

	/** True when a railway station fits under this airport's terminal: a terminal deep and long enough for the box. */
	public static boolean fits(AirportSpec airport) {
		if (!airport.present() || !airport.terminal()) {
			return false;
		}
		final AirportGeometry air = AirportGeometry.of(airport);
		return air.dims().terminalDepth() >= MIN_TERMINAL_DEPTH && length(air) >= MIN_LENGTH;
	}

	/** The station under this airport's terminal, or null when the words asked for none or it does not fit. */
	public static AirportStation of(StationSpec airportSpec) {
		final AirportSpec airport = airportSpec.airport();
		if (!airport.rail() || !fits(airport)) {
			return null;
		}
		final AirportGeometry air = AirportGeometry.of(airport);
		final int length = length(air);
		final String words = "2 track cut and cover box station, island platform, " + DEPTH + " blocks deep, " + length + " long";
		final StationSpec spec = StationDescriptionParser.parse(words).spec().toBuilder().name(airportSpec.name()).build();
		final int du = air.terminalU0() + (air.dims().terminalDepth() - WIDTH) / 2;
		final int dv = (air.terminalV0() + air.terminalV1()) / 2 - length / 2;
		return new AirportStation(spec, du, 1 - DEPTH, dv);
	}

	/** The box's railway geometry in its own frame (the plan's layout: a box's planning never moves it). */
	public TrackLayout layout() {
		return StationGeometry.layout(spec);
	}
}
