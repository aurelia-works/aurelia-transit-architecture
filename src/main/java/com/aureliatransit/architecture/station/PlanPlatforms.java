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
			final List<Integer> numbers = layout.platformNumbers(band);
			if (numbers.size() == 2) {
				// an island: each face's number over its own half, so "1" hangs on the side trains for platform 1 stand
				final int mid = (band.uMin() + band.uMax()) / 2;
				numberSignIn(band, mid, band.uMin(), v, numbers.get(0));
				numberSignIn(band, mid + 1, band.uMax(), v, numbers.get(1));
			} else if (numbers.size() == 1) {
				numberSignIn(band, band.uMin(), band.uMax(), v, numbers.get(0));
			}
		}
	}

	/** One number plate hung over columns {@code from}..{@code to} (either order), nearest the middle of that run first. */
	private void numberSignIn(TrackLayout.PlatformBand band, int from, int to, int v, int number) {
		final int lo = Math.min(from, to);
		final int hi = Math.max(from, to);
		final int mid = (lo + hi) / 2;
		for (int d = 0; d <= hi - lo + 1; d++) {
			final int u = mid + (d % 2 == 0 ? d / 2 : -(d + 1) / 2);
			if (u < lo || u > hi || isEdge(band, u)) {
				continue;
			}
			final int y = ceiling(u, v) - 1;
			if (y >= StationGeometry.WALK_Y + 2 && numberSign(u, y, v, LocalDir.PLUS_V, Integer.toString(number))) {
				return;
			}
		}
	}

	/** A big platform number plate hung from the structure above; double-sided, so it reads from both ways. */
	boolean numberSign(int u, int y, int v, LocalDir front, String number) {
		final Cell cell = new Cell(u, y, v);
		if (cells.containsKey(cell) || air.contains(cell)) {
			return false;
		}
		cells.put(cell, new PlannedBlock(u, y, v, Role.FIXTURE, NUMBER_SIGN, front, number, ""));
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

	/**
	 * The platforms (and footbridge street landings) that ramp down at their ends for step-free access. A platform between
	 * tracks (an island, or side platforms back to back) is left out when it has a way off that does not cross a track (a
	 * footbridge, an underpass, the station building's bridge or a terminus's concourse): its ramp would only lead down onto
	 * the track bed between the tracks (Enviro, 10-20: "some of the platform logic doesn't make sense"). It gets an end
	 * railing instead.
	 */
	List<TrackLayout.PlatformBand> rampedBands() {
		// underground the ends are the box's walls, and nobody walks the tunnels
		if (layout.rampLength() == 0 || spec.options().underground()) {
			return List.of();
		}
		final List<TrackLayout.PlatformBand> all = site.street() != null ? site.bridgeBands(layout) : layout.platforms();
		// a hub's outer platforms run past its head with the through tracks; their end ramps stay their step-free way on
		if (spec.hub() || !spec.footbridge() && !spec.underpass() && !site.building() && !layout.terminus()) {
			return all;
		}
		return all.stream().filter(band -> !betweenTracks(band)).toList();
	}

	/** A track runs somewhere on each side of the band. */
	final boolean betweenTracks(TrackLayout.PlatformBand band) {
		return layout.tracks().stream().anyMatch(t -> t.u() < band.uMin()) && layout.tracks().stream().anyMatch(t -> t.u() > band.uMax());
	}

	void ramps() {
		for (final TrackLayout.PlatformBand band : rampedBands()) {
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
	 * A handrail across each end of every platform that has no ramp at that end, edge cells too, so nobody walks off the end
	 * onto the tracks: on the platform's last row of floor (past its run, the floor goes on to the buffer stops), wherever
	 * there is a drop beyond it. A terminus's platforms run on into its concourse at the head end and stay open there. Planned
	 * last, so it only takes cells nothing else wanted.
	 */
	void endFences() {
		final Set<TrackLayout.PlatformBand> ramped = new HashSet<>(rampedBands());
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				for (final int step : new int[]{-1, 1}) {
					// a ramp's end stays open (a terminus's platforms have one only at the far end)
					if (ramped.contains(band) && (step < 0 || !layout.terminus())) {
						continue;
					}
					// along the floor to its last row inside the platforms' zone; floor going on past the zone (a terminus's
					// concourse) leaves the end open
					int v = step < 0 ? band.vFrom() : band.vTo();
					while (v + step >= layout.vMin() && v + step < layout.platformLength() && floorAt(u, v + step)) {
						v += step;
					}
					if (!floorAt(u, v + step) && at(u, StationGeometry.PLATFORM_Y, v + step) == null) {
						put(Role.RAILING, u, StationGeometry.WALK_Y, v, step < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V);
					}
				}
			}
		}
	}

	/**
	 * The fence behind each buffer stop carried on sideways across the pit, past the track's clearance, wherever the pit
	 * beside the buffer is open and floor stands behind it (between two buffered tracks, nothing else closes that gap).
	 * Planned once every floor is down.
	 */
	void pitEndFences() {
		for (final TrackLayout.Track track : layout.tracks()) {
			if (!layout.buffered(track)) {
				continue;
			}
			final int buffer = layout.trackEnd(track) + 1;
			for (final int step : new int[]{-1, 1}) {
				int u = track.u() + step * (StationGeometry.ENVELOPE_HALF_WIDTH + 1);
				while (at(u, StationGeometry.PLATFORM_Y, buffer) == null && floorAt(u, buffer + 1)
						&& put(Role.RAILING, u, StationGeometry.WALK_Y, buffer + 1, LocalDir.PLUS_V, OVERRUN_FENCE)) {
					u += step;
				}
			}
		}
	}

	/** True when a platform's floor (or the floor it runs on into) is at {@code (u, PLATFORM_Y, v)}. */
	private boolean floorAt(int u, int v) {
		final PlannedBlock block = at(u, StationGeometry.PLATFORM_Y, v);
		return block != null && block.role().flatFloor();
	}

	void railNodes() {
		if (!spec.railNodes()) {
			return;
		}
		for (final TrackLayout.Track track : layout.tracks()) {
			put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformFrom(track), LocalDir.PLUS_V);
			put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.platformTo(track), LocalDir.PLUS_V);
			// a dead end's last node, at the end of its overrun before the buffer stop: the build lays a turn-back rail to it
			if (layout.buffered(track) && layout.trackEnd(track) > layout.platformTo(track)) {
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, layout.trackEnd(track), LocalDir.PLUS_V);
			}
		}
	}

	/**
	 * One solid layer at y = -1 under the whole railway: ballast under every track's clearance for its whole run and across
	 * the gaps between neighbouring tracks, with a lit strip in the row at the foot of each platform wall along that
	 * platform, and black concrete under every platform floor and ramp, so the platforms stand on something and the bed
	 * has no holes.
	 */
	/**
	 * A tram stop's street: the street track block (to lay the MTR rail on) under each track's centre and dark paving across
	 * its envelope, the whole run of the stop, as on a hub's forecourt.
	 */
	void streetTrack() {
		for (final TrackLayout.Track track : layout.tracks()) {
			for (int v = layout.vMin(); v <= layout.trackEnd(track); v++) {
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					if (!taken(u, -1, v)) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, u == track.u() ? Planning.TRAM_TRACK : Planning.LOT_MARK);
					}
				}
			}
		}
		// and the street between neighbouring tracks
		for (final Box gap : bedGaps()) {
			for (int u = gap.uMin(); u <= gap.uMax(); u++) {
				for (int v = gap.vMin(); v <= gap.vMax(); v++) {
					if (!taken(u, -1, v)) {
						put(Role.TRACK_BED, u, -1, v, LocalDir.PLUS_V, Planning.LOT_MARK);
					}
				}
			}
		}
	}

	/** A tram stop's sign at each end of each platform, by the edge the tram stops at. */
	void tramSigns() {
		if (!spec.tram() || spec.hub()) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final int u = band.trackLeft() ? band.uMin() + 1 : band.uMax() - 1;
			for (final int v : new int[]{band.vFrom() + 1, band.vTo() - 1}) {
				if (!taken(u, StationGeometry.WALK_Y, v)) {
					put(Role.FIXTURE, u, StationGeometry.WALK_Y, v, band.trackLeft() ? LocalDir.MINUS_U : LocalDir.PLUS_U, Planning.TRAM_STOP_SIGN);
				}
			}
		}
	}

	void trackBed() {
		if (spec.tram() && !spec.hub()) {
			streetTrack();
			return;
		}
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
	 * One buffer stop across a track's clearance at row v, its front towards the train (-v): the driver's left end at the lower
	 * u, the right end at the higher, the cells between the middle.
	 */
	void bufferStop(TrackLayout.Track track, int y, int v) {
		final int u0 = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH;
		final int u1 = track.u() + StationGeometry.ENVELOPE_HALF_WIDTH;
		for (int u = u0; u <= u1; u++) {
			final String part = u0 == u1 ? "single" : u == u0 ? "left" : u == u1 ? "right" : "middle";
			put(Role.BUFFER_STOP, u, y, v, LocalDir.MINUS_V, palette.blockId(Role.BUFFER_STOP), "part=" + part);
		}
	}

	/**
	 * Each dead-end track's end: past its platform the track runs on {@link StationGeometry#overrun} rows to its last rail
	 * node, then a buffer stop across its clearance at rail level, on ballast, and a fence across the row behind it when
	 * the station goes on. Level floor carries the platforms on beside the overruns (as a terminus's concourse meets its
	 * platform ends); {@link #overrunFences} fences it along the pits.
	 */
	void bayEnds() {
		final TrackBed bed = spec.trackBed();
		for (final TrackLayout.Track track : layout.tracks()) {
			if (!layout.buffered(track)) {
				continue;
			}
			final int buffer = layout.trackEnd(track) + 1;
			if (bed != TrackBed.NONE) {
				for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
					put(Role.TRACK_BED, u, -1, buffer, LocalDir.PLUS_V, bed.blockId());
				}
			}
			bufferStop(track, StationGeometry.PLATFORM_Y, buffer);
			// the fence across the end, behind the buffer, when the station goes on past it
			for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; buffer < layout.vMax() && u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
				put(Role.RAILING, u, StationGeometry.WALK_Y, buffer + 1, LocalDir.PLUS_V, OVERRUN_FENCE);
			}
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			overrunFloor(band);
		}
	}

	/**
	 * The last row of the floor carrying a platform on past its end: to the last buffer of the buffered tracks along it,
	 * and at a terminus to the head (a hub's platforms between through tracks too); -1 if it stops at its end.
	 */
	int overrunTo(TrackLayout.PlatformBand band) {
		int to = -1;
		for (final TrackLayout.Track track : layout.tracks()) {
			final boolean beside = track.u() == band.uMin() - StationGeometry.EDGE_OFFSET || track.u() == band.uMax() + StationGeometry.EDGE_OFFSET;
			if (beside && layout.buffered(track)) {
				to = Math.max(to, layout.trackEnd(track) + 1);
			}
		}
		if (layout.terminus() && band.number() != 0) {
			to = Math.max(to, layout.platformLength() - 1);
		}
		return to;
	}

	/**
	 * The floor carrying a platform on past its end (see {@link #overrunTo}): the concourse floor, and along each side that
	 * faces a track the platform's paving, without the edge line that marks where to board. {@link #overrunFences} fences
	 * those sides once the footbridges are planned.
	 */
	private void overrunFloor(TrackLayout.PlatformBand band) {
		final int to = overrunTo(band);
		for (int v = band.vTo() + 1; v <= to; v++) {
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				final boolean left = u == band.uMin() && band.trackLeft();
				final boolean right = u == band.uMax() && band.trackRight();
				if (left || right) {
					put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, left ? LocalDir.MINUS_U : LocalDir.PLUS_U);
				} else {
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, palette.building().floor());
				}
			}
		}
	}

	/**
	 * The fence along each side of the floor past a platform's end that faces a track, so nobody steps down beside an
	 * overrun or a through track. Only where two cells stay free between the fences and a row of roof columns (a narrower
	 * floor keeps its paving open, as at the platform beside it), and from two rows past the last thing standing in the
	 * middle of the floor (a footbridge stair and its walls), so the lanes round it still lead on.
	 */
	void overrunFences() {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final int to = overrunTo(band);
			final int sides = (band.trackLeft() ? 1 : 0) + (band.trackRight() ? 1 : 0);
			final RoofType roof = band.number() == 0 ? RoofType.NONE : StationGeometry.roof(spec, band.number());
			final int columns = roof == RoofType.NONE ? 0 : StationPlanner.columnLines(band, roof).size();
			if (to < 0 || sides == 0 || band.uMax() - band.uMin() + 1 - sides - columns < 2) {
				continue;
			}
			int from = band.vTo() + 1;
			for (int v = band.vTo() + 1; v <= to; v++) {
				for (int u = band.uMin() + 1; u < band.uMax(); u++) {
					if (taken(u, StationGeometry.WALK_Y, v)) {
						from = v + 2;
					}
				}
			}
			for (int v = from; v <= to; v++) {
				if (band.trackLeft() && !taken(band.uMin(), StationGeometry.WALK_Y, v)) {
					put(Role.RAILING, band.uMin(), StationGeometry.WALK_Y, v, LocalDir.MINUS_U, OVERRUN_FENCE);
				}
				if (band.trackRight() && !taken(band.uMax(), StationGeometry.WALK_Y, v)) {
					put(Role.RAILING, band.uMax(), StationGeometry.WALK_Y, v, LocalDir.PLUS_U, OVERRUN_FENCE);
				}
			}
		}
	}

	/** The fence round a track's overrun: station fencing, which stops players like any railing (1.5 blocks). */
	static final String OVERRUN_FENCE = "aurelia_transit_architecture:station_fence";

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
