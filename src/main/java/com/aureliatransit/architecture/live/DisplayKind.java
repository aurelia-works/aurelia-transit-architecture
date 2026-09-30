package com.aureliatransit.architecture.live;

/**
 * The three layouts of the shared passenger-information display core. Every kind uses the same block entity, config
 * and renderer; this enum only carries the per-type layout parameters.
 */
public enum DisplayKind {
	/**
	 * Platform customer-information screen: the next train, large. Optionally a second train below.
	 */
	CIS(1, 2, 1, 1.3F, false),
	/**
	 * Multi-row platform information display.
	 */
	PIDS(1, 4, 3, 1.0F, false),
	/**
	 * Large concourse departure board with paging.
	 */
	CONCOURSE(4, 10, 8, 1.0F, true);

	private final int minRows;
	private final int maxRows;
	private final int defaultRows;
	private final float textScale;
	private final boolean stationWide;

	DisplayKind(int minRows, int maxRows, int defaultRows, float textScale, boolean stationWide) {
		this.minRows = minRows;
		this.maxRows = maxRows;
		this.defaultRows = defaultRows;
		this.textScale = textScale;
		this.stationWide = stationWide;
	}

	public int minRows() {
		return minRows;
	}

	public int maxRows() {
		return maxRows;
	}

	public int defaultRows() {
		return defaultRows;
	}

	/**
	 * Scale of the main text line relative to the base 8-unit font.
	 */
	public float textScale() {
		return textScale;
	}

	/**
	 * Concourse boards always show every platform of the station; platform displays prefer the platform next to them.
	 */
	public boolean stationWide() {
		return stationWide;
	}

	public int clampRows(int rows) {
		return Math.max(minRows, Math.min(maxRows, rows));
	}

	public boolean hasRowChoice() {
		return minRows != maxRows;
	}
}
