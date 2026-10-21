package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * An MTR rail a build lays between two of a track's rail nodes, in world coordinates: a platform rail between a platform
 * track's two nodes, with the platform number it is given ({@link TrackLayout#platformNumber}), so nobody has to connect
 * the platforms and number them by hand (Enviro, 10-20); and on a dead end, a turn-back rail on from the platform's end to
 * the last node of its overrun before the buffer stop (Jah, 10-21). Through tracks with no platform get none; their rail is
 * the player's to lay.
 *
 * @param turnBack a turn-back rail (no platform, no name) rather than a platform rail
 */
public record PlatformRail(String name, int x1, int y1, int z1, int x2, int y2, int z2, boolean turnBack) {

	public PlatformRail(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
		this(name, x1, y1, z1, x2, y2, z2, false);
	}

	/** The platform rails of a plan built in {@code frame}: one per track with a platform, numbered like its signs. */
	public static List<PlatformRail> of(StationPlan plan, LocalFrame frame) {
		final List<PlatformRail> rails = new ArrayList<>();
		if (!plan.spec().railNodes()) {
			return rails;
		}
		final TrackLayout layout = plan.layout();
		final int y = frame.y(StationGeometry.PLATFORM_Y);
		for (final TrackLayout.Track track : layout.tracks()) {
			final int number = layout.platformNumber(track);
			if (number <= 0) {
				continue;
			}
			final int from = layout.platformFrom(track);
			final int to = layout.platformTo(track);
			rails.add(new PlatformRail(Integer.toString(number), frame.x(track.u(), from), y, frame.z(track.u(), from),
					frame.x(track.u(), to), y, frame.z(track.u(), to)));
		}
		// the turn-back rails after every platform rail, as they start from a node the platform rail has just connected
		for (final TrackLayout.Track track : layout.tracks()) {
			final int to = layout.platformTo(track);
			final int end = layout.trackEnd(track);
			if (layout.platformNumber(track) > 0 && layout.buffered(track) && end > to) {
				rails.add(new PlatformRail("", frame.x(track.u(), to), y, frame.z(track.u(), to), frame.x(track.u(), end), y, frame.z(track.u(), end), true));
			}
		}
		return rails;
	}
}
