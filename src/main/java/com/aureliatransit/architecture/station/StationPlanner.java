package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link StationPlan} from a spec: railway geometry from {@link StationGeometry}, then roles for every cell, then
 * block ids from the {@link StylePalette}. Deterministic: the same spec always gives an equal plan.
 * <p>
 * Rules run in a fixed order and each cell holds at most one block: when two rules want the same cell the earlier rule wins
 * and the later block is skipped.
 */
public final class StationPlanner {

	private static final Comparator<PlannedBlock> BUILD_ORDER = Comparator.comparingInt((PlannedBlock b) -> b.role().ordinal())
			.thenComparingInt(PlannedBlock::y).thenComparingInt(PlannedBlock::v).thenComparingInt(PlannedBlock::u);

	private StationPlanner() {
	}

	public static StationPlan plan(StationSpec spec) {
		final Planning planning = new Planning(spec);
		planning.floors();
		planning.ramps();
		planning.railNodes();
		planning.columns();
		planning.canopyRoofs();
		planning.hallRoof();
		planning.lamps();
		planning.benchesAndBins();
		planning.nameSigns();
		return planning.finish();
	}

	/** The v positions of the columns of every column line: from v = 2 every COLUMN_SPACING, plus one near the far end if needed. */
	static List<Integer> columnPositions(int platformLength) {
		final List<Integer> positions = new ArrayList<>();
		int last = 0;
		for (int p = 2; p <= platformLength - 3; p += StationGeometry.COLUMN_SPACING) {
			positions.add(p);
			last = p;
		}
		if (positions.isEmpty() || platformLength - 1 - last > StationGeometry.MAX_CANTILEVER) {
			positions.add(platformLength - 3);
		}
		return positions;
	}

	/**
	 * The u of each column line of a platform, ascending. A canopy has one line down the middle. A hall has one line next to
	 * the track edge of a side platform (plus one next to the back column when the platform is so wide that the beams would
	 * overhang the back by more than the cantilever limit), and one or two lines in an island.
	 */
	static List<Integer> columnLines(TrackLayout.PlatformBand band, RoofType roof) {
		final List<Integer> lines = new ArrayList<>();
		if (roof == RoofType.CANOPY) {
			lines.add((band.uMin() + band.uMax()) / 2);
		} else if (roof == RoofType.HALL) {
			if (band.island()) {
				lines.add(band.uMin() + 1);
				if (band.width() >= 4) {
					lines.add(band.uMax() - 1);
				}
			} else if (band.trackRight()) {
				if (band.width() - 2 > StationGeometry.MAX_CANTILEVER) {
					lines.add(band.uMin() + 1);
				}
				lines.add(band.uMax() - 1);
			} else {
				lines.add(band.uMin() + 1);
				if (band.width() - 2 > StationGeometry.MAX_CANTILEVER) {
					lines.add(band.uMax() - 1);
				}
			}
		}
		return lines;
	}

	private record Cell(int u, int y, int v) {
	}

	private static final class Planning {

		private final StationSpec spec;
		private final TrackLayout layout;
		private final StylePalette palette;
		private final int length;
		private final Map<Cell, PlannedBlock> cells = new HashMap<>();
		private final List<Integer> columnV;

		Planning(StationSpec spec) {
			this.spec = spec;
			this.layout = StationGeometry.layout(spec);
			this.palette = StylePalette.of(spec);
			this.length = layout.platformLength();
			this.columnV = columnPositions(length);
		}

		/** Places a block unless the cell is taken; true if placed. */
		private boolean put(Role role, int u, int y, int v, LocalDir front) {
			final Cell cell = new Cell(u, y, v);
			if (cells.containsKey(cell)) {
				return false;
			}
			final String text = role == Role.NAME_SIGN ? spec.name() : "";
			cells.put(cell, new PlannedBlock(u, y, v, role, palette.blockId(role), front, text));
			return true;
		}

		private boolean taken(int u, int y, int v) {
			return cells.containsKey(new Cell(u, y, v));
		}

		/** The direction from a side platform's back column towards its track. */
		private static LocalDir towardTrack(TrackLayout.PlatformBand band) {
			return band.trackRight() ? LocalDir.PLUS_U : LocalDir.MINUS_U;
		}

		private static int backColumn(TrackLayout.PlatformBand band) {
			return band.trackRight() ? band.uMin() : band.uMax();
		}

		private static boolean isEdge(TrackLayout.PlatformBand band, int u) {
			return u == band.uMin() && band.trackLeft() || u == band.uMax() && band.trackRight();
		}

		void floors() {
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (int v = 0; v < length; v++) {
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
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, -2, LocalDir.MINUS_V);
					put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, -1, LocalDir.MINUS_V);
					put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, length, LocalDir.PLUS_V);
					put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, length + 1, LocalDir.PLUS_V);
				}
			}
		}

		void railNodes() {
			if (!spec.railNodes()) {
				return;
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, 0, LocalDir.PLUS_V);
				put(Role.RAIL_NODE, track.u(), StationGeometry.PLATFORM_Y, length - 1, LocalDir.PLUS_V);
			}
		}

		void columns() {
			if (spec.roof() == RoofType.NONE) {
				return;
			}
			final int top = spec.roof() == RoofType.CANOPY ? StationGeometry.CANOPY_Y : StationGeometry.HALL_BEAM_Y;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int u : columnLines(band, spec.roof())) {
					for (final int v : columnV) {
						for (int y = StationGeometry.WALK_Y; y < top - 1; y++) {
							put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
						}
						put(Role.COLUMN_CAP, u, top - 1, v, LocalDir.PLUS_V);
					}
				}
			}
		}

		private boolean isColumnV(int v) {
			return columnV.contains(v);
		}

		/** True for the cells of a roof lit from above: every 4th cell along a column line, except where a column stands. */
		private boolean isLightV(int v) {
			return v >= 4 && v % 4 == 0 && !isColumnV(v);
		}

		void canopyRoofs() {
			if (spec.roof() != RoofType.CANOPY) {
				return;
			}
			final int y = StationGeometry.CANOPY_Y;
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final int line = columnLines(band, RoofType.CANOPY).get(0);
				for (int v = 0; v < length; v++) {
					for (int u = band.uMin(); u <= band.uMax(); u++) {
						if (u == band.uMin()) {
							put(Role.ROOF_EDGE, u, y, v, LocalDir.MINUS_U);
						} else if (u == band.uMax()) {
							put(Role.ROOF_EDGE, u, y, v, LocalDir.PLUS_U);
						} else if (u == line && isLightV(v)) {
							put(Role.ROOF_LIGHT, u, y, v, LocalDir.PLUS_V);
						} else {
							put(Role.ROOF, u, y, v, LocalDir.PLUS_V);
						}
					}
				}
			}
		}

		void hallRoof() {
			if (spec.roof() != RoofType.HALL) {
				return;
			}
			for (final int v : columnV) {
				for (int u = 0; u < layout.width(); u++) {
					put(Role.BEAM, u, StationGeometry.HALL_BEAM_Y, v, LocalDir.PLUS_U);
				}
			}
			final boolean[] lit = new boolean[layout.width()];
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (final int u : columnLines(band, RoofType.HALL)) {
					lit[u] = true;
				}
			}
			final int y = StationGeometry.HALL_ROOF_Y;
			for (int v = 0; v < length; v++) {
				for (int u = 0; u < layout.width(); u++) {
					if (u == 0) {
						put(Role.ROOF_EDGE, u, y, v, LocalDir.MINUS_U);
					} else if (u == layout.width() - 1) {
						put(Role.ROOF_EDGE, u, y, v, LocalDir.PLUS_U);
					} else if (lit[u] && isLightV(v)) {
						put(Role.ROOF_LIGHT, u, y, v, LocalDir.PLUS_V);
					} else {
						put(Role.ROOF, u, y, v, LocalDir.PLUS_V);
					}
				}
			}
		}

		void lamps() {
			if (spec.roof() != RoofType.NONE || !spec.furniture()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				final int u = band.island() ? (band.uMin() + band.uMax()) / 2 : backColumn(band);
				final LocalDir front = band.island() ? LocalDir.MINUS_U : towardTrack(band);
				for (int v = 4; v <= length - 1; v += 8) {
					put(Role.LAMP, u, StationGeometry.WALK_Y, v, front);
				}
			}
		}

		void benchesAndBins() {
			if (!spec.furniture()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				int index = 0;
				for (int v = 8; v <= length - 2; v += 16, index++) {
					final int u;
					final LocalDir front;
					if (band.island()) {
						u = nearestFree(band, (band.uMin() + band.uMax()) / 2, v);
						front = index % 2 == 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					} else {
						u = backColumn(band);
						front = towardTrack(band);
					}
					if (u < 0 || isEdge(band, u)) {
						continue;
					}
					if (put(Role.BENCH, u, StationGeometry.WALK_Y, v, front)) {
						put(Role.BIN, u, StationGeometry.WALK_Y, v + 1, front);
					}
				}
			}
		}

		/** The free, non-edge walking cell of an island nearest to u0 at v (lower u wins ties); -1 if there is none. */
		private int nearestFree(TrackLayout.PlatformBand band, int u0, int v) {
			for (int d = 0; d <= band.width(); d++) {
				for (int sign = -1; sign <= 1; sign += 2) {
					final int u = u0 + sign * d;
					if (u >= band.uMin() && u <= band.uMax() && !isEdge(band, u) && !taken(u, StationGeometry.WALK_Y, v)) {
						return u;
					}
					if (d == 0) {
						break;
					}
				}
			}
			return -1;
		}

		void nameSigns() {
			if (spec.name().isEmpty()) {
				return;
			}
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				if (band.island()) {
					final int centre = (band.uMin() + band.uMax()) / 2;
					signRow(centre, LocalDir.MINUS_U);
					if (band.width() >= 4) {
						signRow(centre + 1, LocalDir.PLUS_U);
					}
				} else {
					signRow(backColumn(band), towardTrack(band));
				}
			}
		}

		/**
		 * Three signs side by side along v, centred on the platform's middle; shifted by 2 along v until the cells are free.
		 * Shifts run towards the platform end first and, if the end is reached, back towards its start.
		 */
		private void signRow(int u, LocalDir front) {
			final int start = length / 2 - 1;
			for (int v = start; v + 2 <= length - 1; v += 2) {
				if (placeRow(u, v, front)) {
					return;
				}
			}
			for (int v = start - 2; v >= 0; v -= 2) {
				if (placeRow(u, v, front)) {
					return;
				}
			}
		}

		private boolean placeRow(int u, int v, LocalDir front) {
			for (int i = 0; i < 3; i++) {
				if (taken(u, StationGeometry.WALK_Y, v + i)) {
					return false;
				}
			}
			for (int i = 0; i < 3; i++) {
				put(Role.NAME_SIGN, u, StationGeometry.WALK_Y, v + i, front);
			}
			return true;
		}

		StationPlan finish() {
			final List<PlannedBlock> blocks = new ArrayList<>(cells.values());
			blocks.sort(BUILD_ORDER);
			final List<Box> envelopes = new ArrayList<>();
			final List<Box> supports = new ArrayList<>();
			for (final TrackLayout.Track track : layout.tracks()) {
				envelopes.add(new Box(track.u() - StationGeometry.ENVELOPE_HALF_WIDTH, StationGeometry.PLATFORM_Y, layout.vMin(),
						track.u() + StationGeometry.ENVELOPE_HALF_WIDTH, StationGeometry.ENVELOPE_HEIGHT - 1, layout.vMax()));
			}
			final List<Box> headroom = new ArrayList<>();
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				headroom.add(new Box(band.uMin(), StationGeometry.WALK_Y, 0, band.uMax(), StationGeometry.WALK_Y + StationGeometry.HEADROOM - 1,
						length - 1));
				supports.add(new Box(band.uMin(), -1, layout.vMin(), band.uMax(), -1, layout.vMax()));
			}
			for (final TrackLayout.Track track : layout.tracks()) {
				supports.add(new Box(track.u(), -1, layout.vMin(), track.u(), -1, layout.vMax()));
			}
			return new StationPlan(spec, layout, blocks, envelopes, headroom, supports, palette.blockId(Role.FOUNDATION));
		}
	}
}
