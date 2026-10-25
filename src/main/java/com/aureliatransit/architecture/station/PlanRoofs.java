package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Canopy, hall and shed roofs, their girders and vaults.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanRoofs extends PlanColumns {

	PlanRoofs(StationSpec spec) {
		super(spec);
	}

	void canopyRoofs() {
		final int y = StationGeometry.CANOPY_Y;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (roof(band) != RoofType.CANOPY) {
				continue;
			}
			final List<Integer> lines = columnLines(band, RoofType.CANOPY);
			final int from = canopyFrom(band);
			final int to = canopyTo(band);
			final String girder = palette.blockId(Role.BEAM);
			final String kind = "kind=" + palette.girderKind();
			final CanopyShape shape = spec.canopy();
			final RoofPiece[] across = acrossProfile(shape, lines, from, to);
			final int[] lamp = lampsAlong(band.length(), shape);
			// a butterfly is a thin V on cantilevers: arms at the columns, the valley beam along the line and (wide) a girder
			// up under each side; braces under its raised edges at the columns alone, so nothing hangs under its eaves
			final boolean butterfly = shape == CanopyShape.BUTTERFLY;
			// each line's arms reach towards the roof edge (or halfway to the next line), stopping a cell short of it
			final int[] armLeft = new int[lines.size()];
			final int[] armRight = new int[lines.size()];
			// a butterfly's girder along the platform under each side, only where the roof runs on further than the valley
			// beam reaches: as far in as it can carry the edge from, so it sits up under the V, not under the eaves
			final int[] purlinLeft = new int[lines.size()];
			final int[] purlinRight = new int[lines.size()];
			for (int i = 0; i < lines.size(); i++) {
				final int u = lines.get(i);
				final int left = i == 0 ? u - from : (u - lines.get(i - 1)) / 2;
				final int right = i == lines.size() - 1 ? to - u : (lines.get(i + 1) - u) / 2;
				armLeft[i] = Math.max(0, Math.min(StationGeometry.MAX_CANTILEVER, left - 1));
				armRight[i] = Math.max(0, Math.min(StationGeometry.MAX_CANTILEVER, right - 1));
				purlinLeft[i] = left > StationGeometry.MAX_CANTILEVER ? Math.min(armLeft[i], left - StationGeometry.MAX_CANTILEVER) : 0;
				purlinRight[i] = right > StationGeometry.MAX_CANTILEVER ? Math.min(armRight[i], right - StationGeometry.MAX_CANTILEVER) : 0;
			}
			for (int v = canopyStart(band); v <= canopyEnd(band); v++) {
				// under a real station's hall its floor is the roof: no canopy and no girders under it, but in its two edge rows,
				// where the canopy's girders run in to the piers inside its walls and carry the canopy's end
				if (underHall(v) && Math.abs(v - site.bridgeV()) < bridgeWall() - 1) {
					continue;
				}
				// on every column: an arm across the platform on its cap; between columns a girder along the line and an edge
				// girder along each arm's end, resting on the arms
				for (int i = 0; i < lines.size(); i++) {
					final int u = lines.get(i);
					final PlannedBlock cap = cells.get(new Cell(u, y - 2, v));
					if (cap != null && cap.role() == Role.COLUMN_CAP) {
						// the arm runs out from the cap until something stands in its way (a stairwell, an escalator); past
						// that, what a wall or pier can carry is framed into it and the edge girder runs on along the line
						put(Role.BEAM, u, y - 1, v, LocalDir.PLUS_U, girder, kind);
						for (final int step : new int[]{-1, 1}) {
							final int reach = step < 0 ? armLeft[i] : armRight[i];
							int a = u + step;
							while (Math.abs(a - u) <= reach && !taken(a, y - 1, v)) {
								put(Role.BEAM, a, y - 1, v, LocalDir.PLUS_U, girder, kind);
								a += step;
							}
							if (Math.abs(a - u) <= reach) {
								final int lo = Math.min(a, u + step * reach);
								final int hi = Math.max(a, u + step * reach);
								final java.util.Set<Integer> framed = framedIntoPiers(lo, hi, y - 1, v, true);
								for (final int f : framed) {
									put(Role.BEAM, f, y - 1, v, LocalDir.PLUS_U, girder, kind);
								}
								// the edge girder rests on the arm's end: past an arm cut short it would hang over the stairwell on
								// nothing, unless it is framed into the far wall
								if (reach > 0 && framed.contains(u + step * (reach - 1))) {
									put(Role.BEAM, u + step * reach, y - 1, v, LocalDir.PLUS_V, girder, kind);
								}
							}
						}
					} else {
						// a footbridge or stair pier rising through the girders carries a cross beam framed into its sides,
						// as far as a cantilever reaches (on the footbridge line, right across the canopy); the girders run on
						// past the rest of the row
						final boolean bridgeRow = bridgeRow(v);
						final int start = bridgeRow && i > 0 ? lines.get(i - 1) + armRight[i - 1] + 1 : u - armLeft[i];
						final int end = u + armRight[i];
						// where a stairwell or lift shaft wall stops the girder along the line, the cross beam frames into it
						final java.util.Set<Integer> framed = framedIntoPiers(start, end, y - 1, v, taken(u, y - 1, v));
						for (final int a : framed) {
							put(Role.BEAM, a, y - 1, v, LocalDir.PLUS_U, girder, kind);
						}
						for (final int a : butterfly ? new int[]{u, u - purlinLeft[i], u + purlinRight[i]} : new int[]{u, u - armLeft[i], u + armRight[i]}) {
							if (!framed.contains(a)) {
								put(Role.BEAM, a, y - 1, v, LocalDir.PLUS_V, girder, kind);
							}
						}
					}
				}
				// the roof row: its profile across the platform (butterfly, gable) lifts some cells above y
				final int along = v - band.vFrom();
				boolean armRow = false;
				for (final int line : lines) {
					final PlannedBlock cap = cells.get(new Cell(line, y - 2, v));
					armRow |= cap != null && cap.role() == Role.COLUMN_CAP;
				}
				for (int u = from; u <= to; u++) {
					final RoofPiece piece = across[u - from];
					final boolean edge = u == from || u == to;
					final int rise = edge ? piece.edgeRise() : piece.rise();
					// a roof lifted above the platform's level leaves the cells below it alone: a footbridge or stairwell there
					// has its own cover
					boolean clear = true;
					for (int level = y; level < y + rise && clear; level++) {
						clear = !taken(u, level, v);
					}
					if (!clear) {
						continue;
					}
					// a girder under the raised roof, where it meets the sloping blocks; over a column line's girder it is a post
					// in the column's block instead (it carries the roof, so it cannot be left out, but it would stack girders)
					if (rise > 0 && piece.braced() && (armRow || !butterfly)) {
						final PlannedBlock under = cells.get(new Cell(u, y - 1, v));
						final boolean onGirder = under != null && under.role() == Role.BEAM;
						for (int level = y; level < y + rise; level++) {
							if (onGirder) {
								put(Role.RIB, u, level, v, LocalDir.PLUS_V, palette.blockId(Role.COLUMN), "");
							} else {
								put(Role.RIB, u, level, v, LocalDir.PLUS_V, palette.blockId(Role.RIB), kind);
							}
						}
					}
					if (edge) {
						put(Role.ROOF_EDGE, u, y + rise, v, u == from ? LocalDir.MINUS_U : LocalDir.PLUS_U);
					} else if (lamp[along] != NO_LAMP && piece.block() == null && onLamp(lines, u, lamp[along])) {
						put(Role.ROOF_LIGHT, u, y + rise, v, LocalDir.PLUS_V);
					} else if (piece.block() == null) {
						put(Role.ROOF, u, y + rise, v, LocalDir.PLUS_V);
					} else {
						put(Role.ROOF, u, y + rise, v, piece.low(), piece.block(), "");
					}
				}
			}
		}
		pruneLooseGirders(y - 1);
	}

	/** The lamp offset of a row with no lamp. */
	static final int NO_LAMP = 2;

	/** Cells from a column line to the roof edge (or the next line's half) below which a canopy stays level. */
	static final int MIN_SLOPE_REACH = 3;

	/**
	 * One cell of a canopy's roof: how many blocks it stands above the canopy level, the sloping block it is made of (null
	 * for the style's flat roof block), the way that block's low end points, how high the edge cell of its row stands, and
	 * whether a girder block is set under it. A raised roof touches the sloping block beside it only at an edge, so the
	 * girder under a ridge, a fold or a crest is what joins it to the rest (and carries it, as a post).
	 */
	/**
	 * Girders along a canopy's lines and edges that a stairwell, lift or escalator cut off from their columns: a piece resting
	 * on nothing is left out, one carried at a single end is cut back to what a cantilever reaches, and one longer than a
	 * girder spans between two supports becomes a cantilever off each. The rules match
	 * {@link LayoutValidator}'s, so what stands is what the checks accept.
	 */
	void pruneLooseGirders(int y) {
		final Map<Integer, java.util.TreeSet<Integer>> lines = new TreeMap<>();
		for (final PlannedBlock block : cells.values()) {
			if (block.role() == Role.BEAM && block.y() == y && (block.front() == LocalDir.PLUS_V || block.front() == LocalDir.MINUS_V)) {
				lines.computeIfAbsent(block.u(), k -> new java.util.TreeSet<>()).add(block.v());
			}
		}
		for (final Map.Entry<Integer, java.util.TreeSet<Integer>> line : lines.entrySet()) {
			final int u = line.getKey();
			final List<int[]> runs = new ArrayList<>();
			for (final int v : line.getValue()) {
				if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == v - 1) {
					runs.get(runs.size() - 1)[1] = v;
				} else {
					runs.add(new int[]{v, v});
				}
			}
			for (final int[] run : runs) {
				final boolean before = carriesGirder(u, y, run[0] - 1);
				final boolean after = carriesGirder(u, y, run[1] + 1);
				boolean capped = false;
				for (int v = run[0]; v <= run[1] && !capped; v++) {
					final PlannedBlock cap = cells.get(new Cell(u, y - 1, v));
					capped = cap != null && cap.role() == Role.COLUMN_CAP;
				}
				if (capped || before && after && run[1] - run[0] + 1 <= StationGeometry.MAX_BEAM_SPAN) {
					continue;
				}
				if (before && after) {
					// longer than a girder spans between its two supports: two cantilevers, one off each
					for (int v = run[0] + StationGeometry.MAX_CANTILEVER; v <= run[1] - StationGeometry.MAX_CANTILEVER; v++) {
						cells.remove(new Cell(u, y, v));
					}
					continue;
				}
				final int keepFrom = before ? run[0] : after ? run[1] - StationGeometry.MAX_CANTILEVER + 1 : Integer.MAX_VALUE;
				final int keepTo = before ? run[0] + StationGeometry.MAX_CANTILEVER - 1 : after ? run[1] : Integer.MIN_VALUE;
				for (int v = run[0]; v <= run[1]; v++) {
					if (v < keepFrom || v > keepTo) {
						cells.remove(new Cell(u, y, v));
					}
				}
			}
		}
	}

	/** A cross beam, a column cap, a wall or a pier at (u, y, v) that a girder along v can end on. */
	boolean carriesGirder(int u, int y, int v) {
		final PlannedBlock block = cells.get(new Cell(u, y, v));
		if (block == null) {
			return false;
		}
		final Role role = block.role();
		return role == Role.BEAM && block.front() != LocalDir.PLUS_V && block.front() != LocalDir.MINUS_V || role == Role.COLUMN_CAP
				|| role == Role.WALL || role == Role.BRIDGE_PIER;
	}

	record RoofPiece(int rise, String block, LocalDir low, int edgeRise, boolean braced) {

		static RoofPiece level(int rise) {
			return new RoofPiece(rise, null, null, rise, false);
		}

		static RoofPiece slope(int rise, String block, LocalDir low) {
			return new RoofPiece(rise, block, low, rise, false);
		}

		RoofPiece withBrace() {
			return new RoofPiece(rise, block, low, edgeRise, true);
		}
	}

	/**
	 * The roof's profile across a platform, one piece per u from {@code from} to {@code to}, for the shapes that have one
	 * (butterfly, gable; the others are level). Each column line owns the cells up to halfway to the next line, or to the
	 * roof edge. A butterfly is level over its line and rises one block over the next two cells, outwards; a gable stands
	 * its ridge over the line, one block high (two on a wide roof), and falls the same way to the edge. A stretch of
	 * fewer than three cells to either side is left level: there is no room for the slope.
	 */
	RoofPiece[] acrossProfile(CanopyShape shape, List<Integer> lines, int from, int to) {
		final RoofPiece[] pieces = new RoofPiece[to - from + 1];
		java.util.Arrays.fill(pieces, RoofPiece.level(0));
		if (shape != CanopyShape.BUTTERFLY && shape != CanopyShape.GABLE) {
			return pieces;
		}
		for (int i = 0; i < lines.size(); i++) {
			final int line = lines.get(i);
			final int low = i == 0 ? from : lines.get(i - 1) + (line - lines.get(i - 1)) / 2 + 1;
			final int high = i == lines.size() - 1 ? to : line + (lines.get(i + 1) - line) / 2;
			final int reach = Math.min(line - low, high - line);
			if (reach < MIN_SLOPE_REACH) {
				continue;
			}
			// the gable's ridge height; the butterfly's rise is always one block
			final int ridge = shape == CanopyShape.GABLE ? Math.min(2, (reach - 1) / 2) : 1;
			for (int u = low; u <= high; u++) {
				final int d = Math.abs(u - line);
				final LocalDir outward = u < line ? LocalDir.MINUS_U : LocalDir.PLUS_U;
				if (shape == CanopyShape.GABLE) {
					if (d == 0) {
						pieces[u - from] = RoofPiece.level(ridge).withBrace();
					} else if (d <= 2 * ridge) {
						// counting from the eaves: the lowest half-block step first
						final int step = 2 * ridge - d + 1;
						pieces[u - from] = RoofPiece.slope((step - 1) / 2, step % 2 == 1 ? CANOPY_SLOPE_LOWER : CANOPY_SLOPE_UPPER, outward);
					}
				} else if (d >= 1 && d <= 2) {
					pieces[u - from] = RoofPiece.slope(0, d == 1 ? CANOPY_SLOPE_LOWER : CANOPY_SLOPE_UPPER, outward.opposite());
				} else if (d > 2) {
					pieces[u - from] = d == 3 ? RoofPiece.level(1).withBrace() : RoofPiece.level(1);
				}
			}
		}
		return pieces;
	}

	/**
	 * Where the lamps of a canopy go along a platform, per cell along it: every 4th cell, beside each girder on alternating
	 * sides (a butterfly or gable lights its line itself: the valley or the ridge). Each entry is -1 or 1 for the side of the line, 0 for on it, or {@link #NO_LAMP}.
	 */
	static int[] lampsAlong(int length, CanopyShape shape) {
		final int[] lamps = new int[length];
		java.util.Arrays.fill(lamps, NO_LAMP);
		for (int along = 2; along < length; along += 4) {
			lamps[along] = shape == CanopyShape.BUTTERFLY || shape == CanopyShape.GABLE ? 0 : along % 8 == 2 ? 1 : -1;
		}
		return lamps;
	}

	/** True when the roof cell at {@code u} is lit by this row's lamp: beside a line on the lamp's side, or on the line. */
	static boolean onLamp(List<Integer> lines, int u, int lamp) {
		return lines.contains(u - lamp);
	}

	/**
	 * True when the hall's girders need a row on the footbridge roof at {@code vB}: the column rows either side are further
	 * apart than a girder spans, or the bridge is past the last row. Otherwise piers on the bridge roof only poke up through
	 * it beside columns that already carry the frame (Jah, 10-20).
	 */
	static boolean girdersNeedBridge(List<Integer> rows, int vB) {
		Integer lo = null;
		Integer hi = null;
		for (final int v : rows) {
			if (v < vB) {
				lo = v;
			} else if (v > vB && hi == null) {
				hi = v;
			}
		}
		return lo == null || hi == null || hi - lo > StationGeometry.MAX_BEAM_SPAN;
	}

	/**
	 * The free cells from u0 to u1 at height y in row v that a cross beam framed into a footbridge or stair pier can reach:
	 * out from each pier (one just outside the range counts) through free cells, at most a cantilever's reach; with
	 * {@code walls}, out from walls too.
	 */
	java.util.Set<Integer> framedIntoPiers(int u0, int u1, int y, int v, boolean walls) {
		final java.util.Set<Integer> framed = new java.util.TreeSet<>();
		for (int p = u0 - 1; p <= u1 + 1; p++) {
			final PlannedBlock block = cells.get(new Cell(p, y, v));
			if (block == null || block.role() != Role.BRIDGE_PIER && !(walls && block.role() == Role.WALL)) {
				continue;
			}
			for (final int step : new int[]{-1, 1}) {
				for (int a = p + step; Math.abs(a - p) <= StationGeometry.MAX_CANTILEVER && a >= u0 && a <= u1 && !taken(a, y, v); a += step) {
					framed.add(a);
				}
			}
		}
		return framed;
	}

	/** The pendant lamp hung from a glass hall's frame. */
	static final String HALL_LAMP = "aurelia_transit_architecture:hall_pendant_lamp";

	/** How far above the platform a hall lamp hangs: low enough to light it, high over every head and screen. */
	static final int HALL_LAMP_HEIGHT = 10;

	/** The hall's cross-girder rows and frame height, kept for {@link #hallLamps}; none without a hall. */
	private List<Integer> lampRows = List.of();
	private int lampBeamY;

	/**
	 * Pendant lamps on rods under every other cross girder, over the middle of each platform (Jah 10-20: the glass hall
	 * was dark at night). Hung after the screens and signs: where one already hangs under the spot, from the next girder
	 * along instead. Only where the whole drop from the lamp to the girder is free; never on a footbridge row.
	 */
	void hallLamps() {
		final int beamY = lampBeamY;
		final int y = Math.min(StationGeometry.WALK_Y + HALL_LAMP_HEIGHT, beamY - 2);
		if (lampRows.isEmpty() || y <= StationGeometry.WALK_Y + 4) {
			return;
		}
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final int u = (band.uMin() + band.uMax() + 1) / 2;
			for (int r = 0; r < lampRows.size(); r += 2) {
				for (int pick = r; pick < Math.min(r + 2, lampRows.size()); pick++) {
					final int v = lampRows.get(pick);
					if (lampFits(band, u, y, v, beamY)) {
						put(Role.FIXTURE, u, y, v, LocalDir.PLUS_V, HALL_LAMP);
						dropRod(u, y, v);
						break;
					}
				}
			}
		}
	}

	/** A cross girder over (u, v) on the platform, and nothing from head height up to it. */
	private boolean lampFits(TrackLayout.PlatformBand band, int u, int y, int v, int beamY) {
		final PlannedBlock girder = cells.get(new Cell(u, beamY, v));
		if (girder == null || girder.role() != Role.BEAM || v < band.vFrom() || band.vTo() >= 0 && v > band.vTo()) {
			return false;
		}
		for (int h = StationGeometry.WALK_Y + 3; h < beamY; h++) {
			if (taken(u, h, v)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * y of the hall's girder frame: just above the track envelopes; higher under a wide vault; above the footbridge
	 * and a building beside the tracks when the station has them; and higher again for a city or grand station, whose
	 * shed is a real train-shed height rather than the compact frame (see {@link StationGeometry#hallBeamY}).
	 */
	int hallBeamY() {
		final Landmark.Shed real = realShed();
		if (real != null && real.eavesY() > 0 && !site.bridge() && !spec.sideBuilding()) {
			return real.eavesY();
		}
		if (scale == StationScale.GRAND) {
			return StationGeometry.tallHallBeamY(scale);
		}
		if (site.bridge() || spec.sideBuilding()) {
			return StationGeometry.tallHallBeamY(scale);
		}
		if (spec.shed().arched() && layout.width() - layout.buildingWidth() >= 16) {
			return StationGeometry.highHallBeamY(scale);
		}
		return StationGeometry.hallBeamY(scale);
	}

	/**
	 * A train shed over the railway (u from the end of any side building to the far edge, the whole platform length): a
	 * closed girder frame (cross girders over every column row, girders along every column line, both eaves and every
	 * valley), then the roof. A flat roof sits on the frame with lights over the column lines. A vault rises from the
	 * eaves in face-joined steps, glazed above a solid lower band; stepped girder ribs follow its underside over every
	 * column row and at both ends, with hangers from the column lines up to the rib. A terminus closes the vault above
	 * the head building with a glass screen.
	 */
	void hallRoof() {
		if (spec.roof() != RoofType.HALL) {
			return;
		}
		// a shed's frame is a lattice truss rather than the plain girder (Jah, 10-20); a palette with its own beam keeps it
		final String girder = palette.blockId(Role.BEAM).equals(GIRDER) ? ROOF_TRUSS : palette.blockId(Role.BEAM);
		final Landmark.Shed real = realShed();
		final String kind = "kind=" + (real != null ? real.truss() : palette.girderKind());
		final int beamY = hallBeamY();
		final int u0 = site.street() != null ? StationGeometry.STREET_WIDTH : layout.buildingWidth();
		final int u1 = layout.width() - 1;
		final int n = u1 - u0 + 1;
		final int[] rise = shedRise(u0, n);
		final boolean[] lit = new boolean[layout.width()];
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (final int u : columnLines(band, RoofType.HALL)) {
				lit[u] = true;
			}
		}
		final List<Integer> rows = new ArrayList<>(hallRows());
		// over a footbridge the frame also rests on the bridge roof, where the column rows either side are too far apart:
		// short piers under every girder line, a cross girder on them
		final int bridgeRoof = StationGeometry.BUILDING_ROOF_Y;
		final List<Integer> onBridges = new ArrayList<>();
		for (final int vB : beamY >= bridgeRoof + 2 ? bridgeLines() : List.<Integer>of()) {
			if (girdersNeedBridge(rows, vB)) {
				onBridges.add(vB);
			}
		}
		for (final int vB : onBridges) {
			if (!rows.contains(vB)) {
				rows.add(vB);
			}
		}
		rows.sort(Comparator.naturalOrder());
		// a single vault is its own arch: no tie girders across the tracks, only brackets over the platforms and eaves (the
		// bridge row still crosses on its piers); several vaults keep full ties, which carry their valley gutters
		final boolean freeArch = spec.shed() == ShedShape.ARCH || spec.shed() == ShedShape.POINTED;
		final boolean[] untied = freeArch ? betweenPlatforms() : new boolean[layout.width()];
		final boolean free = freeShed();
		for (final int v : rows) {
			for (int u = u0; u <= u1; u++) {
				if ((untied[u] || free) && !onBridges.contains(v)) {
					continue;
				}
				put(Role.BEAM, u, beamY, v, LocalDir.PLUS_U, girder, kind);
			}
		}
		// girders along v: column lines, eaves, valleys, and (under a flat roof) purlins halving any gap wider than 8 so
		// every roof cell is within reach of one; a vault carries its purlins in the curve instead (below)
		final java.util.TreeSet<Integer> along = new java.util.TreeSet<>();
		// a real station's shed that springs from the platforms (St Pancras) spans them freely: its ribs carry it, with no
		// girders along it (its edges stand over the outer tracks, where nothing may stand under them)
		for (int i = 0; i < n; i++) {
			if (free ? false : lit[u0 + i] || i == 0 || i == n - 1 || spec.shed().arched() && rise[i] == 0) {
				along.add(i);
			}
		}
		for (boolean added = !spec.shed().arched(); added; ) {
			added = false;
			Integer previous = null;
			for (final int i : List.copyOf(along)) {
				if (previous != null && i - previous > 2 * StationGeometry.MAX_CANTILEVER) {
					along.add((previous + i) / 2);
					added = true;
				}
				previous = i;
			}
		}
		for (final int i : along) {
			for (int v = 0; v < length; v++) {
				put(Role.BEAM, u0 + i, beamY, v, LocalDir.PLUS_V, girder, kind);
			}
		}
		// lamps hang from the cross girders once the screens and signs are placed (hallLamps); not from a footbridge row
		lampRows = free ? List.of() : rows.stream().filter(v -> !onBridges.contains(v)).toList();
		lampBeamY = beamY;
		// an end frame across each end of the shed, joined to the girders along v so they finish on it instead of stopping
		// short; it hangs from them (a rib, not a beam on columns)
		for (final int end : along.isEmpty() ? new int[0] : new int[]{0, length - 1}) {
			for (int u = u0 + along.first(); u <= u0 + along.last(); u++) {
				if (!untied[u] && !free) {
					put(Role.RIB, u, beamY, end, LocalDir.PLUS_U, girder, kind);
				}
			}
		}
		for (final int vB : onBridges) {
			for (int u = u0; u <= u1; u++) {
				final PlannedBlock below = cells.get(new Cell(u, bridgeRoof, vB));
				if (below != null && below.role() == Role.BUILDING_ROOF && (along.contains(u - u0) || lit[u])) {
					bridgePier(u, bridgeRoof, beamY, vB);
				}
			}
			// where the cross girder would still span more than a girder may (a vault's tie over several tracks), more piers
			// on the bridge roof, each as far along as the span allows
			final java.util.TreeSet<Integer> carried = new java.util.TreeSet<>();
			for (int u = u0; u <= u1; u++) {
				final PlannedBlock under = cells.get(new Cell(u, beamY - 1, vB));
				if (under != null && (under.role() == Role.BRIDGE_PIER || under.role() == Role.COLUMN_CAP)) {
					carried.add(u);
				}
			}
			Integer previous = null;
			for (final int u : List.copyOf(carried)) {
				int from = previous == null ? u : previous;
				while (previous != null && u - from > StationGeometry.MAX_BEAM_SPAN) {
					int pick = -1;
					for (int c = from + StationGeometry.MAX_BEAM_SPAN; c > from && pick < 0; c--) {
						final PlannedBlock roofBelow = cells.get(new Cell(c, bridgeRoof, vB));
						if (roofBelow != null && roofBelow.role() == Role.BUILDING_ROOF && !taken(c, bridgeRoof + 1, vB)) {
							pick = c;
						}
					}
					if (pick < 0) {
						break;
					}
					bridgePier(pick, bridgeRoof, beamY, vB);
					from = pick;
				}
				previous = u;
			}
		}
		if (!free) {
			buildingLineWall(u0, beamY);
		}
		final int roofY = beamY + 1;
		final int[] top = new int[n];
		for (int i = 0; i < n; i++) {
			top[i] = roofY + rise[i];
		}
		final int[] low = steps(top, false);
		final int peak = java.util.Arrays.stream(rise).max().orElse(0);
		final String glass = real != null ? real.glass() : ata("glass_wall");
		final String cladding = real != null ? real.cladding() : ata("metro_steel_cladding");
		// a vault is glazed above a solid lower band; pitched roofs are solid with a glass lantern along each ridge
		final boolean ridgeLights = spec.shed() == ShedShape.GABLED;
		for (int v = 0; v < length; v++) {
			for (int i = 0; i < n; i++) {
				final int u = u0 + i;
				final boolean edge = i == 0 || i == n - 1;
				for (int y = low[i]; y <= top[i]; y++) {
					if (!spec.shed().arched()) {
						if (edge) {
							put(Role.ROOF_EDGE, u, y, v, i == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U);
						} else if (lit[u] && isLightV(v)) {
							put(Role.ROOF_LIGHT, u, y, v, LocalDir.PLUS_V);
						} else {
							put(Role.ROOF, u, y, v, LocalDir.PLUS_V);
						}
					} else {
						final double height = (y - roofY) / (double) Math.max(1, peak);
						final boolean glazed = real != null ? height >= real.glazedFrom() && height <= real.glazedTo()
								: palette.roofMaterialGlass() || (ridgeLights ? rise[i] >= peak - 1 : y - roofY >= Math.max(1, peak / 3));
						final boolean eaves = edge && y == top[i];
						put(eaves ? Role.ROOF_EDGE : Role.ROOF, u, y, v, i == 0 ? LocalDir.MINUS_U : i == n - 1 ? LocalDir.PLUS_U : LocalDir.PLUS_V,
								glazed && !eaves ? glass : cladding);
					}
				}
			}
		}
		if (!spec.shed().arched()) {
			return;
		}
		// ribs under the vault, one cell below its underside, joined face to face like the roof above them
		final int[] ribTop = new int[n];
		for (int i = 0; i < n; i++) {
			ribTop[i] = low[i] - 1;
		}
		final int[] ribLow = steps(ribTop, true);
		final List<Integer> ribV = new ArrayList<>(rows);
		ribV.add(0);
		ribV.add(length - 1);
		for (final int v : ribV) {
			for (int i = 0; i < n; i++) {
				for (int y = Math.max(ribLow[i], beamY); y <= ribTop[i]; y++) {
					put(Role.RIB, u0 + i, y, v, LocalDir.PLUS_U, girder, kind);
				}
			}
		}
		// purlins in the curve: girder lines along v just under the vault, joining every rib, about every 4 across
		for (int i = 2; i < n - 2; i++) {
			if (rise[i] > 0 && (i % (free ? 8 : 4) == 2 || isCrown(rise, i))) {
				for (int v = 0; v < length; v++) {
					put(Role.RIB, u0 + i, ribTop[i], v, LocalDir.PLUS_V, girder, kind);
				}
			}
		}
		if (free) {
			sideWalls(u0, u1, beamY, real);
		}
		// hangers from the frame over each column line up to the rib
		for (final int v : free ? List.<Integer>of() : rows) {
			for (int i = 0; i < n; i++) {
				if (lit[u0 + i]) {
					for (int y = beamY + 1; y < ribLow[i]; y++) {
						put(Role.RIB, u0 + i, y, v, LocalDir.PLUS_U, girder, kind);
					}
				}
			}
		}
		if (layout.terminus()) {
			// glass screen closing the vault above the head building's cornice
			final int screenFrom = StationGeometry.BUILDING_ROOF_Y + 2;
			for (int i = 0; i < n; i++) {
				for (int y = screenFrom; y <= top[i]; y++) {
					put(Role.WINDOW, u0 + i, y, length, LocalDir.PLUS_V, glass);
				}
			}
		}
	}

	/**
	 * Cells strictly between the column lines of two neighbouring platforms: the bays over the tracks that a single vault
	 * spans without a tie. Every other piece of a cross girder ends on a column line.
	 */
	boolean[] betweenPlatforms() {
		final boolean[] out = new boolean[layout.width()];
		int previous = -1;
		TrackLayout.PlatformBand previousBand = null;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final List<Integer> lines = columnLines(band, RoofType.HALL);
			if (lines.isEmpty()) {
				continue;
			}
			final int first = java.util.Collections.min(lines);
			if (previousBand != null) {
				for (int u = previous + 1; u < first; u++) {
					out[u] = true;
				}
			}
			previous = java.util.Collections.max(lines);
			previousBand = band;
		}
		return out;
	}

	/** Height of a round vault over its span. */
	static final double ARCH_RISE = 0.4;

	/** Height of a pitched roof's ridge over its span. */
	static final double GABLE_RISE = 0.25;

	/** A round vault's rise over its span for the look: Berlin's glass tube is flat, Amsterdam's and Hamburg's are low. */
	double vaultRise() {
		return switch (spec.building()) {
			case BERLIN -> 0.2;
			// Bangkok's hall vault stays under its great window
			case AMSTERDAM, BANGKOK -> 0.3;
			case CLINKER -> 0.35;
			default -> ARCH_RISE;
		};
	}

	/** Highest a vault rises over its frame: the widest single vault (100 blocks) would otherwise top the station's height limit. */
	static final int MAX_VAULT_RISE = 40;

	/**
	 * Rise of the vault above the eaves at each u0 + i: 0 for a flat roof; half an ellipse (barrel vault), a pointed arch, or
	 * one half ellipse per span between the island platforms' centre lines (several vaults side by side).
	 */
	int[] shedRise(int u0, int n) {
		final int[] rise = new int[n];
		final Landmark.Shed real = realShed();
		if (real != null && spec.shed().arched()) {
			// a real station's shed: its own spans, cross-section and rise
			final int spans = Math.max(1, real.spans());
			final Profile profile = switch (real.profile()) {
				case ROUND -> Profile.ROUND;
				case POINTED -> Profile.POINTED;
				case GABLE -> Profile.GABLE;
			};
			for (int k = 0; k < spans; k++) {
				final int a = k * (n - 1) / spans;
				final int b = (k + 1) * (n - 1) / spans;
				scaledVault(rise, a, b, profile, real.rise());
			}
			return rise;
		}
		switch (spec.shed()) {
			case FLAT -> {
			}
			case ARCH -> {
				if (spec.real().trainShed()) {
					naveAndAisles(rise, n, spec.real().naveShare(), spec.real().roofTopY() - (hallBeamY() + 1));
				} else {
					vault(rise, 0, n - 1, Profile.ROUND, vaultRise());
				}
			}
			case POINTED -> vault(rise, 0, n - 1, Profile.POINTED, ARCH_RISE);
			case MULTI_ARCH, GABLED -> {
				// a valley between two neighbouring tracks with no platform between them: one vault per platform and its tracks
				final List<Integer> cuts = new ArrayList<>();
				cuts.add(0);
				final List<TrackLayout.Track> tracks = layout.tracks();
				for (int t = 0; t + 1 < tracks.size(); t++) {
					final TrackLayout.Track left = tracks.get(t);
					final TrackLayout.Track right = tracks.get(t + 1);
					final int c = (left.u() + right.u()) / 2 - u0;
					if (!left.platformRight() && !right.platformLeft() && c >= 4 && c <= n - 5) {
						cuts.add(c);
					}
				}
				cuts.add(n - 1);
				final boolean gabled = spec.shed() == ShedShape.GABLED;
				for (int k = 0; k + 1 < cuts.size(); k++) {
					vault(rise, cuts.get(k), cuts.get(k + 1), gabled ? Profile.GABLE : Profile.ROUND, gabled ? GABLE_RISE : ARCH_RISE);
				}
			}
		}
		return rise;
	}

	/**
	 * A real train shed's cross-section (Hamburg): a round vault over the middle {@code share} of the width rising
	 * {@code crown} over the frame, and a lower round aisle each side. The nave springs from the aisles' crowns, so a glazed
	 * clerestory step stands between them, as in a basilica. With no room for aisles it is one vault.
	 */
	static void naveAndAisles(int[] rise, int n, double share, int crown) {
		final int aisle = (int) Math.round(n * (1 - share) / 2);
		if (aisle < 4 || crown < 4) {
			vault(rise, 0, n - 1, Profile.ROUND, ARCH_RISE);
			return;
		}
		vault(rise, 0, aisle, Profile.ROUND, ARCH_RISE);
		vault(rise, n - 1 - aisle, n - 1, Profile.ROUND, ARCH_RISE);
		// the aisle vault's crown, as vault() sizes it from its span of aisle + 1 blocks
		final int base = Math.min(crown - 2, (int) Math.round((aisle + 1) * ARCH_RISE));
		final int a = aisle;
		final int b = n - 1 - aisle;
		final double half = (b - a) / 2.0;
		final double centre = (a + b) / 2.0;
		final int top = Math.min(MAX_VAULT_RISE, crown);
		for (int u = a; u <= b; u++) {
			final double x = Math.min(1, Math.abs(u - centre) / half);
			rise[u] = Math.max(rise[u], base + (int) Math.round((top - base) * Math.sqrt(1 - x * x)));
		}
	}

	/** The cross-section of one span of a shed roof. */
	enum Profile {
		ROUND, POINTED, GABLE
	}

	/**
	 * The side walls of a real shed that springs from the platforms, under its eaves at both edges (wherever no track runs):
	 * the sheet's wall block with a tall window every six cells.
	 */
	void sideWalls(int u0, int u1, int beamY, Landmark.Shed real) {
		if (real.sideWall() == null) {
			return;
		}
		for (final int u : new int[]{u0, u1}) {
			if (layout.tracks().stream().anyMatch(t -> Math.abs(t.u() - u) <= StationGeometry.ENVELOPE_HALF_WIDTH)) {
				continue;
			}
			for (int v = 0; v < length; v++) {
				for (int y = StationGeometry.WALK_Y; y < beamY; y++) {
					final boolean window = Math.floorMod(v, 6) >= 2 && Math.floorMod(v, 6) <= 3 && y >= 2 && y <= beamY - 2;
					put(window ? Role.WINDOW : Role.WALL, u, y, v, u == u0 ? LocalDir.MINUS_U : LocalDir.PLUS_U,
							window ? ata("wired_glass_pane") : real.sideWall());
				}
			}
		}
	}

	/**
	 * The shed's wall on its building side wherever the side building does not stand: the building's track-side wall line
	 * carried on under the eaves, with a tall window every six cells. A side building that is only an entrance block
	 * (Hamburg) or ends short of the shed (Amsterdam) otherwise leaves the shed open to the street there, its platform
	 * columns standing in a row under the roof. The far side over the outer track stays as it is (no wall may stand in a
	 * track's envelope), and a station without a side building, or with a street between it and the tracks, is untouched.
	 * Rows where anything already stands in the wall line (the building, its end walls, a bridge) are left alone.
	 */
	void buildingLineWall(int u0, int beamY) {
		final int u = u0 - 1;
		if (!spec.sideBuilding() || site.street() != null || u0 != layout.buildingWidth() || u < 0) {
			return;
		}
		final String wall = palette.building().wall();
		final String window = palette.building().window();
		for (int v = 0; v < length; v++) {
			boolean clear = true;
			for (int y = StationGeometry.WALK_Y; y <= beamY && clear; y++) {
				clear = !taken(u, y, v);
			}
			if (!clear) {
				continue;
			}
			for (int y = StationGeometry.WALK_Y; y <= beamY; y++) {
				final boolean glazed = Math.floorMod(v, 6) >= 2 && Math.floorMod(v, 6) <= 3 && y >= 2 && y <= beamY - 2;
				put(glazed ? Role.WINDOW : Role.WALL, u, y, v, LocalDir.MINUS_U, glazed ? window : wall);
			}
		}
	}

	/** A real station's train shed from its sheet, or null for the generator's own. */
	Landmark.Shed realShed() {
		return landmark == null ? null : landmark.shed();
	}

	/** One span of a vault rising {@code ratio} of its span, whatever its cross-section. */
	static void scaledVault(int[] rise, int a, int b, Profile profile, double ratio) {
		final int span = b - a + 1;
		final double half = (b - a) / 2.0;
		final double centre = (a + b) / 2.0;
		final int height = Math.min(MAX_VAULT_RISE, Math.max(2, (int) Math.round(span * ratio)));
		for (int u = a; u <= b; u++) {
			final double x = Math.min(1, Math.abs(u - centre) / half);
			final double f = switch (profile) {
				case POINTED -> Math.sqrt(Math.max(0, 2.5 - (x + 0.5) * (x + 0.5))) - 0.5;
				case ROUND -> Math.sqrt(1 - x * x);
				case GABLE -> 1 - x;
			};
			rise[u] = Math.max(rise[u], (int) Math.round(height * f));
		}
	}

	/** The middle of a vault's flat crown: as high as both neighbours and not left of an equal one. */
	static boolean isCrown(int[] rise, int i) {
		return rise[i] >= rise[i + 1] && rise[i] > rise[i - 1];
	}

	static void vault(int[] rise, int a, int b, Profile profile, double ratio) {
		final int span = b - a + 1;
		final double half = (b - a) / 2.0;
		final double centre = (a + b) / 2.0;
		final int height = Math.min(MAX_VAULT_RISE, profile == Profile.POINTED ? Math.max(3, (int) Math.round(span * 0.42))
				: Math.max(2, (int) Math.round(span * ratio)));
		for (int u = a; u <= b; u++) {
			final double x = Math.min(1, Math.abs(u - centre) / half);
			// pointed: two arcs centred half a span outside and below the crown's opposite side, meeting at the crown at an
			// angle; round: half an ellipse, which springs steeply from the eaves like a real barrel vault; gable: straight
			// slopes up to a ridge
			final double f = switch (profile) {
				case POINTED -> Math.sqrt(Math.max(0, 2.5 - (x + 0.5) * (x + 0.5))) - 0.5;
				case ROUND -> Math.sqrt(1 - x * x);
				case GABLE -> 1 - x;
			};
			rise[u] = Math.max(rise[u], (int) Math.round(height * f));
		}
	}

	/** The height of an open platform's lamp head, on its pole. */
	static final int LAMP_HEAD_Y = StationGeometry.WALK_Y + 3;
}
