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

	/**
	 * The platform rails of a plan built in {@code frame}: one per track with a platform, numbered like its signs. An airport's are
	 * those of the railway station under its terminal ({@link AirportStation}), none without one.
	 */
	public static List<PlatformRail> of(StationPlan plan, LocalFrame frame) {
		if (plan.spec().isAirport()) {
			final AirportStation rail = AirportStation.of(plan.spec());
			return rail == null ? new ArrayList<>() : of(rail.spec(), rail.layout(), frame, rail.du(), rail.dy(), rail.dv());
		}
		return of(plan.spec(), plan.layout(), frame, 0, 0, 0);
	}

	/** The platform rails of a station's layout moved by ({@code du}, {@code dy}, {@code dv}) in the plan and built in {@code frame}. */
	static List<PlatformRail> of(StationSpec spec, TrackLayout layout, LocalFrame frame, int du, int dy, int dv) {
		final List<PlatformRail> rails = new ArrayList<>();
		if (!spec.railNodes()) {
			return rails;
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			final int number = layout.platformNumber(track);
			if (number <= 0) {
				continue;
			}
			final int u = track.u() + du;
			final int from = layout.platformFrom(track) + dv;
			final int to = layout.platformTo(track) + dv;
			final int y = frame.y(track.level() + dy);
			rails.add(new PlatformRail(Integer.toString(number), frame.x(u, from), y, frame.z(u, from), frame.x(u, to), y, frame.z(u, to)));
		}
		// the turn-back rails after every platform rail, as they start from a node the platform rail has just connected
		for (final TrackLayout.Track track : layout.tracks()) {
			final int to = layout.platformTo(track);
			final int end = layout.trackEnd(track);
			if (layout.platformNumber(track) > 0 && layout.buffered(track) && end > to) {
				final int u = track.u() + du;
				final int y = frame.y(track.level() + dy);
				rails.add(new PlatformRail("", frame.x(u, to + dv), y, frame.z(u, to + dv), frame.x(u, end + dv), y, frame.z(u, end + dv), true));
			}
		}
		return rails;
	}
}
