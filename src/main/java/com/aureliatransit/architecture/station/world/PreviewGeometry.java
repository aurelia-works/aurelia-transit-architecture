package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.SiteIssue;
import com.aureliatransit.architecture.station.StationGeometry;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.TrackLayout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The marker points of a plan preview in world coordinates, computed once. Pure data, so it needs no game classes; each
 * refresh takes a rotating sample of every layer so a long station is covered over several refreshes.
 */
final class PreviewGeometry {

	enum Layer {
		/** Footprint outline at walking level. */
		OUTLINE(160),
		/** Track centre lines at rail level. */
		TRACK(160),
		/** Platform edge cells. */
		EDGE(160),
		/** Rail node cells. */
		NODE(48),
		/** Site issues. */
		ISSUE(64);

		private final int budget;

		Layer(int budget) {
			this.budget = budget;
		}

		int budget() {
			return budget;
		}
	}

	record Point(double x, double y, double z) {
	}

	/** Particles per refresh at most. */
	static final int MAX_PER_REFRESH = 592;
	/** Spacing of track and edge markers along the track. */
	private static final int STEP = 2;

	private final Map<Layer, List<Point>> points = new EnumMap<>(Layer.class);

	PreviewGeometry(StationPlan plan, LocalFrame frame, List<SiteIssue> issues) {
		for (final Layer layer : Layer.values()) {
			points.put(layer, new ArrayList<>());
		}
		final TrackLayout layout = plan.layout();
		for (int u = 0; u < layout.width(); u++) {
			for (int v = layout.vMin(); v <= layout.vMax(); v++) {
				if (u == 0 || u == layout.width() - 1 || v == layout.vMin() || v == layout.vMax()) {
					add(Layer.OUTLINE, frame, u, StationGeometry.WALK_Y, v, 0.1);
				}
			}
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			for (int v = layout.vMin(); v <= layout.vMax(); v += STEP) {
				add(Layer.TRACK, frame, track.u(), StationGeometry.PLATFORM_Y, v, 0.3);
			}
		}
		for (final PlannedBlock block : plan.blocks()) {
			if (block.role() == Role.PLATFORM_EDGE && Math.floorMod(block.v(), STEP) == 0) {
				add(Layer.EDGE, frame, block.u(), block.y(), block.v(), 1.1);
			} else if (block.role() == Role.RAIL_NODE) {
				add(Layer.NODE, frame, block.u(), block.y(), block.v(), 0.5);
			}
		}
		for (final SiteIssue issue : issues) {
			points.get(Layer.ISSUE).add(new Point(issue.x() + 0.5, issue.y() + 0.5, issue.z() + 0.5));
		}
	}

	private void add(Layer layer, LocalFrame frame, int u, int y, int v, double lift) {
		points.get(layer).add(new Point(frame.x(u, v) + 0.5, frame.y(y) + lift, frame.z(u, v) + 0.5));
	}

	/** Every point of a layer. */
	List<Point> all(Layer layer) {
		return points.get(layer);
	}

	/** At most the layer's budget of points; a different slice for each {@code refresh} when the layer is larger. */
	List<Point> sample(Layer layer, int refresh) {
		final List<Point> all = points.get(layer);
		if (all.size() <= layer.budget()) {
			return all;
		}
		final int stride = (all.size() + layer.budget() - 1) / layer.budget();
		final List<Point> picked = new ArrayList<>(layer.budget());
		for (int i = Math.floorMod(refresh, stride); i < all.size(); i += stride) {
			picked.add(all.get(i));
		}
		return picked;
	}
}
