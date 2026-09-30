package com.aureliatransit.architecture.live.announce;

import java.util.List;

/**
 * Chooses which loaded speaker voices an announcement: the closest speaker to the listener that is within its own
 * radius and has the category enabled. Returns nothing when the listener is out of range of every speaker.
 */
public final class SpeakerPicker {

	/**
	 * @param radius       hearing radius in blocks
	 * @param categoryMask enabled {@link AnnouncementCategory} bits
	 */
	public record Point(Object handle, double x, double y, double z, double radius, int categoryMask) {
	}

	private SpeakerPicker() {
	}

	public static Point closest(List<Point> speakers, double px, double py, double pz, AnnouncementCategory category) {
		Point best = null;
		double bestDistance = Double.MAX_VALUE;
		for (final Point speaker : speakers) {
			if (!category.enabledIn(speaker.categoryMask())) {
				continue;
			}
			final double dx = speaker.x() - px;
			final double dy = speaker.y() - py;
			final double dz = speaker.z() - pz;
			final double distance = dx * dx + dy * dy + dz * dz;
			if (distance <= speaker.radius() * speaker.radius() && distance < bestDistance) {
				bestDistance = distance;
				best = speaker;
			}
		}
		return best;
	}

	public static boolean inRange(Point speaker, double px, double py, double pz) {
		final double dx = speaker.x() - px;
		final double dy = speaker.y() - py;
		final double dz = speaker.z() - pz;
		return dx * dx + dy * dy + dz * dz <= speaker.radius() * speaker.radius();
	}
}
