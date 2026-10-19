package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Platforms, track bed, rail nodes, bay ends and the doorways beside the building.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanPlatforms extends PlanState {

	PlanPlatforms(StationSpec spec) {
		super(spec);
	}

	/**
	 * A big number beside each platform's pier, seen as you come off the stairs, hung right under whatever is above it (the
	 * deck, or a girder under it). Planned after the roofs, so it never takes a girder's cell; off the middle when a lift
	 * landing or the structure has the middle. A platform numbered over its gates at a terminus head gets no second one.
	 */
	void numberSigns() {
		if (!site.bridge()) {
			return;
		}
		final int v = site.bridgeV() - site.stairSide();
		for (final TrackLayout.PlatformBand band : site.bridgeBands(layout)) {
			if (!site.served().contains(band.number()) || gateNumbered(band)) {
				continue;
			}
			final int mid = (band.uMin() + band.uMax()) / 2;
			for (int d = 0; d <= band.width(); d++) {
				final int u = mid + (d % 2 == 0 ? d / 2 : -(d + 1) / 2);
				if (u < band.uMin() || u > band.uMax() || isEdge(band, u)) {
					continue;
				}
				final int y = ceiling(u, v) - 1;
				if (y >= StationGeometry.WALK_Y + 2 && numberSign(band, u, y, v, LocalDir.PLUS_V)) {
					break;
				}
			}
		}
	}

	/** A big platform number plate hung from the structure above; double-sided, so it reads from both ways. */
	boolean numberSign(TrackLayout.PlatformBand band, int u, int y, int v, LocalDir front) {
		final Cell cell = new Cell(u, y, v);
		if (cells.containsKey(cell) || air.contains(cell)) {
			return false;
		}
		cells.put(cell, new PlannedBlock(u, y, v, Role.FIXTURE, NUMBER_SIGN, front, Integer.toString(band.number()), ""));
		return true;
	}

	void floors() {
		for (final TrackLayout.PlatformBand street : site.streets()) {
			// the street entrance: paved like the platforms, railed off along the track, which nobody boards from here
			final int trackSide = street.trackRight() ? street.uMax() : street.uMin();
			for (int v = street.vFrom(); v <= street.vTo(); v++) {
				for (int u = street.uMin(); u <= street.uMax(); u++) {
					put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
				}
				// a real station's square is walled off from the track under its hall instead (see PlanBridges.plateaus)
				if (!site.plateau(street)) {
					put(Role.RAILING, trackSide, StationGeometry.WALK_Y, v, street.trackRight() ? LocalDir.PLUS_U : LocalDir.MINUS_U);
				}
			}
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (int v = band.vFrom(); v <= band.vTo(); v++) {
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					if (u == band.uMin() && band.trackLeft()) {
						put(Role.PLATFORM_EDGE, u, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
					} else if (u == band.uMax() && band.trackRight()) {
						put(Role.PLATFORM_EDGE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U);
					} else {
						put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
					}
				}
			}
		}
	}

	void ramps() {
		if (layout.rampLength() == 0) {
			return;
		}
		for (final TrackLayout.PlatformBand band : site.street() != null ? site.bridgeBands(layout) : layout.platforms()) {
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, band.vFrom() - 2, LocalDir.MINUS_V);
				put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, band.vFrom() - 1, LocalDir.MINUS_V);
				if (!layout.terminus()) { // a terminus's platforms run level into its concourse
					put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, band.vTo() + 1, LocalDir.PLUS_V);
					put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, band.vTo() + 2, LocalDir.PLUS_V);
				}
			}
		}
	}

	/**
	 * A handrail across each end of every platform that has no ramp there, edge cells too, so nobody walks off the end
	 * onto the tracks; a terminus's platforms run on into its concourse at the head end and stay open there. Planned last,
	 * so it only takes cells nothing else wanted.
	 */
	void endFences() {
		final Set<TrackLayout.PlatformBand> ramped = new HashSet<>(layout.rampLength() == 0 ? List.of()
				: site.street() != null ? site.bridgeBands(layout) : layout.platforms());
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (ramped.contains(band)) {
				continue;
			}
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				put(Role.RAILING, u, StationGeometry.WALK_Y, band.vFrom(), LocalDir.MINUS_V);
				if (!layout.terminus()) {
					put(Role.RAILING, u, StationGeometry.WALK_Y, band.vTo(), LocalDir.PLUS_V);
				}
			}
		}
	}

	void railNodes() {
		if (!spec.railNodes()) {
			return;
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformFrom(track), LocalDir.PLUS_V);
			put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformTo(track), LocalDir.PLUS_V);
		}
	}

	/**
	 * One solid layer at y = -1 under the whole railway: ballast under every track's clearance for its whole run and across
	 * the gaps between neighbouring tracks, with a lit strip in the row at the foot of each platform wall along that
	 * platform, and black concrete under every platform floor and ramp, so the platforms stand on something and the bed
	 * has no holes.
	 */
	void trackBed() {
		final TrackBed bed = spec.trackBed();
		if (bed == TrackBed.NONE) {
			return;
		}
		for (final PlannedBlock block : List.copyOf(cells.values())) {
			if (block.y() == StationGeometry.PLATFORM_Y && (block.role().platformFloor() || block.role() == Role.RAMP_LOWER || block.role() == Role.RAMP_UPPER)) {
				put(Role.TRACK_BED, block.u(), -1, block.v(), LocalDir.PLUS_V, PLATFORM_FILL);
			}
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			for (final int side : new int[]{-1, 1}) {
				if (side < 0 ? track.platformLeft() : track.platformRight()) {
					for (int v = layout.platformFrom(track); v <= layout.platformTo(track); v++) {
						// the channel against the wall, under the platform's lip
						put(Role.TRACK_BED, track.u() + side, -1, v, side < 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U, bed.light());
					}
				}
			}
			for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
				}
			}
		}
		for (final Box gap : bedGaps()) {
			for (int u = gap.uMin(); u <= gap.uMax(); u++) {
				for (int v = gap.vMin(); v <= gap.vMax(); v++) {
					put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, bed.blockId());
				}
			}
		}
	}

	/**
	 * The strips between two neighbouring tracks' clearances where no platform stands (y = -1), as far as both tracks run:
	 * ballast fills them so the bed is one surface from wall to wall.
	 */
	List<Box> bedGaps() {
		final List<TrackLayout.Track> tracks = new ArrayList<>(layout.tracks());
		tracks.sort(Comparator.comparingInt(TrackLayout.Track::u));
		final List<Box> gaps = new ArrayList<>();
		for (int i = 0; i + 1 < tracks.size(); i++) {
			final TrackLayout.Track a = tracks.get(i);
			final TrackLayout.Track b = tracks.get(i + 1);
			final int u0 = a.u() + StationGeometry.ENVELOPE_HALF_WIDTH + 1;
			final int u1 = b.u() - StationGeometry.ENVELOPE_HALF_WIDTH - 1;
			if (u0 > u1 || layout.platforms().stream().anyMatch(band -> band.uMin() <= u1 && band.uMax() >= u0)) {
				continue;
			}
			gaps.add(new Box(u0, -1, layout.vMin(), u1, -1, Math.min(layout.trackEnd(a), layout.trackEnd(b))));
		}
		return gaps;
	}

	/**
	 * A buffer stop across each bay track's clearance at rail level, just past its platform's end, on ground the build makes
	 * sure of (a terminus has its own, on the head building's floor).
	 */
	void bayEnds() {
		for (final TrackLayout.Track track : layout.tracks()) {
			if (!layout.deadEnd(track)) {
				continue;
			}
			final int v = layout.trackEnd(track) + 1;
			for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
				put(Role.BUFFER_STOP, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U);
			}
		}
	}

	/**
	 * True for a row of a building's walls that carries windows: tall windows through the ground storey (y = 2..5), and two
	 * rows in the middle of the first floor and of every storey above it, with a solid band at each floor and under the roof.
	 */
	static boolean windowRow(int y) {
		if (y < StationGeometry.UPPER_FLOOR_Y) {
			return y >= 2 && y <= StationGeometry.UPPER_FLOOR_Y - 2;
		}
		if (y < StationGeometry.BUILDING_ROOF_Y) {
			return y >= StationGeometry.UPPER_WALK_Y + 1 && y <= StationGeometry.BUILDING_ROOF_Y - 2;
		}
		final int k = Math.floorMod(y - StationGeometry.BUILDING_ROOF_Y, StationGeometry.STOREY);
		return k == 2 || k == 3;
	}

	/** True when v is the centre of a facade bay. */
	boolean isBayCentre(int v) {
		if (!site.building()) {
			return false;
		}
		for (int bay = 0; bay < site.bays(); bay++) {
			if (site.bayCentre(bay) == v) {
				return true;
			}
		}
		return false;
	}

	/** True when a platform runs along the building's platform wall, so a doorway in it leads onto that platform. */
	boolean platformBesideBuilding() {
		return layout.platforms().stream().anyMatch(band -> band.uMin() == layout.buildingWidth());
	}

	/**
	 * A doorway three wide centred on {@code centre} in the platform wall at {@code uP}; where a track rather than a
	 * platform runs along that wall, a window instead, so nobody can step out onto the track.
	 */
	void platformDoorway(int uP, int centre, String window) {
		for (int v = centre - 1; v <= centre + 1; v++) {
			if (platformBesideBuilding()) {
				for (int y = 1; y <= 3; y++) {
					keepClear(uP, y, v);
				}
				doorV.add(v);
			} else {
				for (int y = 2; y <= 3; y++) {
					put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, window);
				}
			}
		}
	}
}
