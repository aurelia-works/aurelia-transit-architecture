package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Columns and piers: where the roof stands on the platforms and between tracks.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanColumns extends PlanMovingWalks {

	PlanColumns(StationSpec spec) {
		super(spec);
	}

	void columns() {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (roof(band) == RoofType.NONE || roof(band) == RoofType.HALL && freeShed()) {
				continue;
			}
			final int top = roof(band) == RoofType.CANOPY ? StationGeometry.CANOPY_Y : hallBeamY();
			for (final int u : roof(band) == RoofType.HALL ? hallLines(band) : columnLines(band, roof(band))) {
				for (final int v : spec.roof() == RoofType.HALL ? hallRows() : columnVForLine(band, u)) {
					final int base = spec.roof() == RoofType.HALL ? standsOn(u, v, top) : StationGeometry.WALK_Y - 1;
					if (base >= StationGeometry.WALK_Y) {
						// the row crosses a lift shaft or another roof: a pier on that roof carries the frame
						for (int y = base + 1; y < top; y++) {
							put(Role.BRIDGE_PIER, u, y, v, LocalDir.PLUS_V, palette.building().pier());
						}
						continue;
					}
					if (!band.runs(v)) {
						// past a shorter platform's end a hall column stands on a footing of platform floor, never on its ramp; on
						// the floor beside a terminus overrun it stands as on the platform
						if (!footing(band, u, v)) {
							continue;
						}
						if (!taken(u, StationGeometry.PLATFORM_Y, v)) {
							put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
						}
					}
					// a canopy column's cap carries the girder along its line, which carries the roof
					final int capY = roof(band) == RoofType.CANOPY ? top - 2 : top - 1;
					for (int y = StationGeometry.WALK_Y; y < capY; y++) {
						put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
					}
					put(Role.COLUMN_CAP, u, capY, v, LocalDir.PLUS_V);
				}
			}
		}
		trackColumns();
	}

	/** A short pier on the footbridge roof at u, up to the hall frame. */
	void bridgePier(int u, int bridgeRoof, int beamY, int vB) {
		for (int y = bridgeRoof + 1; y < beamY; y++) {
			put(Role.BRIDGE_PIER, u, y, vB, LocalDir.PLUS_V, palette.building().pier());
		}
	}

	/** The centre lines of the footbridges: the main one's and the far one's (empty without a footbridge). */
	List<Integer> bridgeLines() {
		final List<Integer> lines = new ArrayList<>();
		if (site.bridge()) {
			lines.add(site.bridgeV());
		}
		if (far != null) {
			lines.add(far.bridgeV());
		}
		return lines;
	}

	/**
	 * Columns between tracks, where a hall's cross girders would otherwise span further than
	 * {@link StationGeometry#MAX_BEAM_SPAN} between two platforms' column lines (several tracks side by side with no platform
	 * between them). Each stands on a plinth in the free cell between two tracks' clearances, at every hall row.
	 */
	void trackColumns() {
		// a single vault spans the tracks by itself, with no tie girders across them for a column to carry
		if (spec.roof() != RoofType.HALL || spec.shed() == ShedShape.ARCH || spec.shed() == ShedShape.POINTED) {
			return;
		}
		final java.util.TreeSet<Integer> lines = new java.util.TreeSet<>();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			lines.addAll(columnLines(band, RoofType.HALL));
		}
		final java.util.TreeSet<Integer> free = new java.util.TreeSet<>();
		for (int u = layout.buildingWidth(); u < layout.width(); u++) {
			if (betweenTracks(u)) {
				free.add(u);
			}
		}
		final List<Integer> added = new ArrayList<>();
		Integer previous = null;
		for (final int line : List.copyOf(lines)) {
			int from = previous == null ? line : previous;
			while (previous != null && line - from > StationGeometry.MAX_BEAM_SPAN) {
				final Integer pick = free.floor(from + StationGeometry.MAX_BEAM_SPAN);
				if (pick == null || pick <= from) {
					break;
				}
				added.add(pick);
				from = pick;
			}
			previous = line;
		}
		final int top = hallBeamY();
		for (final int u : added) {
			for (final int v : hallRows()) {
				if (standsOn(u, v, top) >= StationGeometry.WALK_Y || taken(u, StationGeometry.PLATFORM_Y, v)) {
					continue;
				}
				// on a plinth of platform floor, level with the platforms
				put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
				for (int y = StationGeometry.WALK_Y; y < top - 1; y++) {
					put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
				}
				put(Role.COLUMN_CAP, u, top - 1, v, LocalDir.PLUS_V);
				extraSupport.add(new Box(u, -1, v, u, -1, v));
			}
		}
	}

	/** Outside every track's clearance and every platform: the strip between two tracks. */
	boolean betweenTracks(int u) {
		boolean left = false;
		boolean right = false;
		for (final TrackLayout.Track track : layout.tracks()) {
			if (Math.abs(u - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH) {
				return false;
			}
			left |= track.u() < u;
			right |= track.u() > u;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (u >= band.uMin() && u <= band.uMax()) {
				return false;
			}
		}
		return left && right;
	}

	List<Integer> canopyV(TrackLayout.PlatformBand band) {
		// over the canopy's own stretch of the platform (all of it, but beside a compact building)
		final int start = canopyStart(band);
		final List<Integer> positions = new ArrayList<>();
		for (final int v : canopyPositions(canopyEnd(band) - start + 1)) {
			positions.add(start + v);
		}
		return positions;
	}

	/** Column v for one column line: the platform's spacing minus cells where stairs, railings or piers stand, with gaps of at most 9. */
	List<Integer> columnVForLine(TrackLayout.PlatformBand band, int line) {
		if (!site.bridge()) {
			return canopyV(band);
		}
		// up to the roof cell over the cap: a sign or a stair there leaves the column carrying nothing
		final int top = StationGeometry.CANOPY_Y;
		final List<Integer> lines = List.of(line);
		final List<Integer> kept = new ArrayList<>();
		for (final int v : canopyV(band)) {
			boolean clash = false;
			for (final int u : lines) {
				for (int y = StationGeometry.WALK_Y; y <= top; y++) {
					if (taken(u, y, v)) {
						clash = true;
						break;
					}
				}
				if (clash) {
					break;
				}
			}
			if (!clash) {
				kept.add(v);
			}
		}
		// the footbridge pier carries the roof at bridgeV, so gaps are measured with it as a support
		final List<Integer> supports = new ArrayList<>(kept);
		supports.add(site.bridgeV());
		supports.sort(Comparator.naturalOrder());
		// fill any remaining gap longer than the girder spans with a midpoint column that stands free
		final List<Integer> extra = new ArrayList<>();
		for (int i = 0; i + 1 < supports.size(); i++) {
			int a = supports.get(i);
			int b = supports.get(i + 1);
			while (b - a > StationGeometry.MAX_BEAM_SPAN) {
				int mid = (a + b) / 2;
				if (mid <= a || mid >= b || !band.runs(mid)) {
					break;
				}
				boolean clash = false;
				for (final int u : lines) {
					for (int y = StationGeometry.WALK_Y; y <= top; y++) {
						if (taken(u, y, mid)) {
							clash = true;
							break;
						}
					}
					if (clash) {
						break;
					}
				}
				if (clash) {
					break;
				}
				extra.add(mid);
				a = mid;
			}
		}
		kept.addAll(extra);
		kept.sort(Comparator.naturalOrder());
		// a column kept off a stair or an escalator's run-off can leave an end of the girders past their reach: stand one
		// nearer that end, as near the usual spot (two cells in) as is free
		final int reach = StationGeometry.MAX_CANTILEVER;
		supports.addAll(extra);
		supports.sort(Comparator.naturalOrder());
		final int first = canopyStart(band);
		final int last = canopyEnd(band);
		if (supports.get(supports.size() - 1) < last - reach) {
			endColumn(kept, band, line, top, new int[]{last - 2, last - 1, last - 3, last - 4, last});
		}
		if (supports.get(0) > first + reach) {
			endColumn(kept, band, line, top, new int[]{first + 2, first + 1, first + 3, first + 4, first});
		}
		return kept;
	}

	/** Adds the first of {@code candidates} where the column line is free from the walking surface to the roof. */
	void endColumn(List<Integer> kept, TrackLayout.PlatformBand band, int line, int top, int[] candidates) {
		for (final int v : candidates) {
			if (!band.runs(v) || kept.contains(v)) {
				continue;
			}
			boolean clash = false;
			for (int y = StationGeometry.WALK_Y; y <= top && !clash; y++) {
				clash = taken(line, y, v);
			}
			if (!clash) {
				kept.add(v);
				kept.sort(Comparator.naturalOrder());
				return;
			}
		}
	}

	/**
	 * The v of every column row of a hall: the regular spacing, each row moved by up to two cells (nearest first) to
	 * where every column line of every platform is free from the floor to the frame (clear of stairs, landings and
	 * footbridge walls), so each cross girder stands on a full row of columns.
	 */
	List<Integer> hallRows() {
		if (hallRows != null) {
			return hallRows;
		}
		final int top = hallBeamY();
		final List<Integer> rows = new ArrayList<>();
		for (final int v0 : columnV) {
			// clear of the building's doorways when a shift of up to 3 allows it, else only clear of what is built
			int found = -1;
			for (final int d : new int[] {0, 1, -1, 2, -2, 3, -3}) {
				final int v = v0 + d;
				if (v >= 0 && v < length && !rows.contains(v) && rowFree(v, top, true)) {
					found = v;
					break;
				}
			}
			for (final int d : new int[] {0, 1, -1, 2, -2}) {
				final int v = v0 + d;
				if (found < 0 && v >= 0 && v < length && !rows.contains(v) && rowFree(v, top, false)) {
					found = v;
				}
			}
			if (found >= 0) {
				rows.add(found);
			}
		}
		// over a footbridge the frame also rests on the bridge roof (see hallRoof): that row carries the girders too, so the
		// gaps either side of it are measured from it and no extra row of columns is squeezed in beside the stairs
		final List<Integer> bridgeRows = new ArrayList<>();
		if (top >= StationGeometry.BUILDING_ROOF_Y + 2) {
			for (final int vB : bridgeLines()) {
				if (!rows.contains(vB)) {
					bridgeRows.add(vB);
					rows.add(vB);
				}
			}
		}
		rows.sort(Comparator.naturalOrder());
		// a row kept off a doorway or a stair can leave two rows further apart than a girder spans: add one between them,
		// nearest the middle, clear of doorways if possible
		boolean filled = true;
		while (filled) {
			filled = false;
			for (int i = 0; i + 1 < rows.size() && !filled; i++) {
				final int a = rows.get(i);
				final int b = rows.get(i + 1);
				if (b - a <= StationGeometry.MAX_BEAM_SPAN) {
					continue;
				}
				final int found = gapRow(a, b, top);
				if (found >= 0) {
					rows.add(i + 1, found);
					filled = true;
				}
			}
		}
		// shifting rows off doorways can leave an end out of the girders' reach (short platforms): add a row near that end
		final int reach = StationGeometry.MAX_CANTILEVER;
		if (!rows.isEmpty() && rows.get(rows.size() - 1) < length - 1 - reach) {
			endRow(rows, length - 1 - reach, length - 1, top);
		}
		if (!rows.isEmpty() && rows.get(0) > reach) {
			endRow(rows, 0, reach, top);
		}
		rows.removeAll(bridgeRows);
		rows.sort(Comparator.naturalOrder());
		hallRows = rows;
		return rows;
	}

	/** A free row strictly between rows a and b, nearest their middle (clear of doorways first); -1 if there is none. */
	int gapRow(int a, int b, int top) {
		final int mid = (a + b) / 2;
		for (final boolean doors : new boolean[]{true, false}) {
			for (int d = 0; d <= (b - a) / 2; d++) {
				for (final int v : new int[]{mid + d, mid - d}) {
					if (v > a && v < b && rowFree(v, top, doors)) {
						return v;
					}
				}
			}
		}
		return -1;
	}

	/**
	 * Adds the first free row in {@code from..to}, clear of doorways if one is, else clear of what is built, else one whose
	 * columns can stand on a roof, wall or deck below the frame (a lift shaft's roof on a short platform).
	 */
	void endRow(List<Integer> rows, int from, int to, int top) {
		for (final boolean doors : new boolean[] {true, false}) {
			for (int v = from; v <= to; v++) {
				if (!rows.contains(v) && rowFree(v, top, doors)) {
					rows.add(v);
					return;
				}
			}
		}
		for (int v = to; v >= from; v--) {
			if (!rows.contains(v) && rowCarried(v, top)) {
				rows.add(v);
				return;
			}
		}
	}

	/** The y of the highest block under the frame at (u, v) that a pier can stand on, or WALK_Y - 1 when the cell column is clear. */
	int standsOn(int u, int v, int top) {
		for (int y = top - 1; y >= StationGeometry.WALK_Y; y--) {
			if (cells.containsKey(new Cell(u, y, v))) {
				return y;
			}
		}
		return StationGeometry.WALK_Y - 1;
	}

	/** Every column line at {@code v} is clear up to the frame or ends on a solid roof, wall or deck it can stand on. */
	boolean rowCarried(int v, int top) {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (final int u : hallLines(band)) {
				// past a shorter platform's end a column needs its footing cell (columns() skips one on a ramp)
				if (!band.runs(v) && !footing(band, u, v) && standsOn(u, v, top) < StationGeometry.WALK_Y) {
					return false;
				}
				for (int y = top - 1; y >= StationGeometry.WALK_Y; y--) {
					final PlannedBlock below = cells.get(new Cell(u, y, v));
					if (air.contains(new Cell(u, y, v)) && below == null) {
						return false;
					}
					if (below != null) {
						final Role role = below.role();
						if (role != Role.BUILDING_ROOF && role != Role.WALL && role != Role.BRIDGE_DECK && role != Role.ROOF) {
							return false;
						}
						break;
					}
				}
			}
		}
		return true;
	}

	boolean rowFree(int v, int top, boolean doors) {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (final int u : hallLines(band)) {
				if (doors && site.building() && Math.abs(u - site.platformWall(layout)) <= 3 && (doorV.contains(v - 1) || doorV.contains(v)
						|| doorV.contains(v + 1))) {
					return false;
				}
				for (int y = StationGeometry.WALK_Y; y < top; y++) {
					if (taken(u, y, v) || air.contains(new Cell(u, y, v))) {
						return false;
					}
				}
				// past a shorter platform's end the column needs its footing cell free (not the platform's ramp)
				if (!band.runs(v) && !footing(band, u, v)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * A column past a platform's end can stand at (u, v): its cell is free, or floor beside a terminus overrun where the
	 * row of columns still leaves two cells to walk between the fences (else the platform is cut off from its concourse).
	 */
	boolean footing(TrackLayout.PlatformBand band, int u, int v) {
		final PlannedBlock at = cells.get(new Cell(u, StationGeometry.PLATFORM_Y, v));
		if (at == null) {
			return true;
		}
		if (at.role() != Role.FLOOR && !at.role().platformFloor()) {
			return false;
		}
		int free = 0;
		for (int w = band.uMin(); w <= band.uMax(); w++) {
			if (!taken(w, StationGeometry.WALK_Y, v) && !columnLines(band, roof(band)).contains(w)) {
				free++;
			}
		}
		return free >= 2;
	}

	/** The roof over this platform (see {@link StationGeometry#roof}). */
	RoofType roof(TrackLayout.PlatformBand band) {
		// the street entrance (number 0) is open to the sky, under a train shed too: the shed starts at the first track
		return band.number() == 0 ? RoofType.NONE : StationGeometry.roof(spec, band.number());
	}

	/** The hall column lines on this platform that carry the frame: all of them, or under a real shed only its kept lines. */
	List<Integer> hallLines(TrackLayout.PlatformBand band) {
		final java.util.TreeSet<Integer> kept = shedLines();
		return columnLines(band, RoofType.HALL).stream().filter(u -> kept == null || kept.contains(u)).toList();
	}

	/**
	 * The column lines under a real station's shed of several spans (Frankfurt, Zurich, Milan, Munich, Mumbai) or of nave
	 * and aisles (Hamburg): the line nearest each valley, where one vault meets the next, and the outermost line each side.
	 * The real halls stand on rows between their spans with the platforms under a span free; before, every island had two
	 * rows of columns (Jah 10-26: realism pass). Null when every column line carries the frame.
	 */
	java.util.TreeSet<Integer> shedLines() {
		if (shedLinesDone) {
			return shedLines;
		}
		shedLinesDone = true;
		if (spec.roof() != RoofType.HALL || !spec.shed().arched()) {
			return null;
		}
		final int u0 = site.street() != null ? StationGeometry.STREET_WIDTH : layout.buildingWidth();
		final int n = layout.width() - u0;
		final Landmark.Shed real = landmark == null ? null : landmark.shed();
		final List<Integer> valleys = new ArrayList<>();
		if (real != null && real.spans() > 1) {
			for (int k = 1; k < real.spans(); k++) {
				valleys.add(u0 + k * (n - 1) / real.spans());
			}
		} else if (real == null && spec.real().trainShed() && spec.real().naveShare() < 1) {
			final int aisle = (int) Math.round(n * (1 - spec.real().naveShare()) / 2);
			valleys.add(u0 + aisle);
			valleys.add(u0 + n - 1 - aisle);
		}
		final java.util.TreeSet<Integer> all = new java.util.TreeSet<>();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (roof(band) == RoofType.HALL) {
				all.addAll(columnLines(band, RoofType.HALL));
			}
		}
		// a single round or pointed vault (Berlin's glass barrel over a footbridge, which keeps it off its own eaves) spans
		// every track from its outer rows; a single pitched roof (Paris) keeps its rows
		final boolean singleVault = real != null && real.spans() <= 1 && real.profile() != Landmark.ShedProfile.GABLE;
		if (valleys.isEmpty() && !singleVault || all.size() < 3) {
			return null;
		}
		final java.util.TreeSet<Integer> kept = new java.util.TreeSet<>(List.of(all.first(), all.last()));
		for (final int valley : valleys) {
			kept.add(all.stream().min(Comparator.comparingInt(u -> Math.abs(u - valley))).orElseThrow());
		}
		shedLines = kept;
		return kept;
	}

	private java.util.TreeSet<Integer> shedLines;
	private boolean shedLinesDone;

	boolean isColumnV(int v) {
		return spec.roof() == RoofType.HALL ? hallRows().contains(v) : columnV.contains(v);
	}

	/** True for the cells of a roof lit from above: every 4th cell along a column line, except where a column stands. */
	boolean isLightV(int v) {
		return v >= 4 && v % 4 == 0 && !isColumnV(v);
	}
}
