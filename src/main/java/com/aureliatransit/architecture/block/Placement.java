package com.aureliatransit.architecture.block;

/**
 * How a horizontally rotating block picks its facing when placed. Models are authored with their front
 * (sign face, seat front, platform edge, low end of a slope) pointing north.
 */
public enum Placement {
	/**
	 * The front faces the player: signs, seats, cases.
	 */
	TOWARD_PLAYER,
	/**
	 * The front points where the player is looking: platform edges, curbs, canopy edges, slopes (low end), wall-mounted pieces.
	 */
	AWAY_FROM_PLAYER
}
