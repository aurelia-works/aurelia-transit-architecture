package com.aureliatransit.architecture.live.display;

import com.aureliatransit.architecture.text.Tr;
import com.aureliatransit.architecture.transit.PlatformReference;

import java.util.List;

/**
 * The station summary line of a concourse board (1.4): how many platforms MTR's station has, and which one is beside
 * the board. Both come from MTR's station data; with no platform nearby that part is left out. Pure.
 */
public final class BoardSummary {

	private BoardSummary() {
	}

	public static String platformName(List<PlatformReference> platforms, long platformId) {
		if (platformId == 0) {
			return "";
		}
		for (final PlatformReference platform : platforms) {
			if (platform.id() == platformId) {
				return platform.name();
			}
		}
		return "";
	}

	/**
	 * @param platformCount the station's platforms (0: nothing to say)
	 * @param here          name of the platform beside the board, or empty
	 */
	public static String text(int platformCount, String here) {
		if (platformCount <= 0) {
			return "";
		}
		final String count = Tr.t(platformCount == 1 ? "board_summary_one" : "board_summary_many", platformCount);
		return here.isEmpty() ? count : count + " · " + Tr.t("board_summary_here", here);
	}
}
