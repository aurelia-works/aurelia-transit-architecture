package com.aureliatransit.architecture.station;

/**
 * Something on site that stops a build, in world coordinates.
 *
 * @param block what is in the cell (from {@link SiteProbe#describe})
 */
public record SiteIssue(Kind kind, int x, int y, int z, String block) {

	public enum Kind {
		/** A planned cell is not air (terrain, plants, water or a player's build). Approval replaces it. */
		OCCUPIED(false),
		/** Something solid or a fluid inside a track envelope or platform headroom. Approval clears it to air. */
		OBSTRUCTED(false),
		/** No sturdy ground under a platform, ramp or track. Approval fills down to sturdy ground with foundation. */
		UNSUPPORTED(false),
		/** No sturdy ground within {@link SiteSurvey#MAX_FILL_DEPTH} cells below. Never built (would need a viaduct). */
		GAP_TOO_DEEP(true),
		/** A block entity or an unbreakable block in a cell the build would change. Never touched. */
		PROTECTED(true),
		/** Part of the site is in an unloaded chunk. */
		UNLOADED(true),
		/** Part of the site is outside the world's height range. */
		OUT_OF_WORLD(true);

		private final boolean hard;

		Kind(boolean hard) {
			this.hard = hard;
		}

		/** True when no approval can fix it. */
		public boolean hard() {
			return hard;
		}
	}
}
