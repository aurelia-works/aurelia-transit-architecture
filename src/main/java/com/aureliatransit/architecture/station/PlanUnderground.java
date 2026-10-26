package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * An underground station's box (Enviro, 10-20), after Berlin's shallow U-Bahn stations (Rosenthaler Platz: about 7 m deep, an
 * island platform, stairs from it straight up to the street): tiled side walls, end walls with a tunnel mouth over every
 * track, a ceiling slab at the footbridge's deck level whose top is the street, lights in it over the platforms, a row of
 * columns down each wide platform, and on the street a rail round every stair opening and an entrance pylon beside it. The
 * footbridge's flights (and the far one's) are the stairs; the bridge itself is only its deck, part of the slab. Everything
 * inside the box is dug, so the site survey asks for terrain approval.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanUnderground extends PlanStairs {

	/** The ceiling slab's level: the footbridge deck's, so the platform stairs come up through it to the street above. */
	static final int CEILING_Y = StationGeometry.UPPER_FLOOR_Y;
	/** The street over the box, where a person coming up the stairs stands. */
	static final int STREET_Y = StationGeometry.UPPER_WALK_Y;
	/** Height of a tunnel mouth in the end walls: a track's clearance envelope. */
	static final int MOUTH_HEIGHT = StationGeometry.ENVELOPE_HEIGHT;
	static final String TUNNEL_LINING = "aurelia_transit_architecture:metro_tunnel_lining";
	static final String PYLON = "aurelia_transit_architecture:entrance_pylon";
	static final String DEFAULT_TILE = "aurelia_transit_architecture:metro_blue_grey_tile";
	/** The ceiling's light pattern over a platform: one light in this many cells along a diagonal. */
	static final int LIGHT_EVERY = 4;

	PlanUnderground(StationSpec spec) {
		super(spec);
	}

	/** The block an underground station's walls are tiled with: the asked or look's tile, else the operator's or style's. */
	String undergroundTile() {
		if (!spec.options().tile().isEmpty()) {
			return spec.options().tile();
		}
		return switch (spec.operator()) {
			case UNDERGROUND, NATIONAL_RAIL -> "aurelia_transit_architecture:metro_tile_green";
			case DB, SBAHN -> "aurelia_transit_architecture:metro_tile_orange";
			default -> spec.style() == StationStyle.GERMAN_SBAHN ? "aurelia_transit_architecture:metro_tile_orange" : DEFAULT_TILE;
		};
	}

	/** A deep station (a bored tube or a cut-and-cover box): see {@link PlanDeep}. */
	abstract void deepStation();

	/** Planned last, so it only fills what the station left free. */
	void underground() {
		if (!spec.options().underground()) {
			return;
		}
		if (spec.options().deepStation()) {
			deepStation();
			return;
		}
		final int w = layout.width();
		final int v0 = layout.vMin();
		final int v1 = layout.vMax();
		final String tile = undergroundTile();
		final StylePalette.Building b = palette.building();
		// everything inside is dug
		extraHeadroom.add(new Box(1, StationGeometry.WALK_Y, v0 + 1, w - 2, CEILING_Y - 1, v1 - 1));
		// end walls, open in a mouth over every track and lined round it
		for (final int v : new int[]{v0, v1}) {
			final LocalDir in = v == v0 ? LocalDir.PLUS_V : LocalDir.MINUS_V;
			for (int u = 1; u <= w - 2; u++) {
				for (int y = StationGeometry.PLATFORM_Y; y < CEILING_Y; y++) {
					if (mouth(u, y)) {
						continue;
					}
					final boolean ring = mouth(u - 1, y) || mouth(u + 1, y) || mouth(u, y - 1);
					put(Role.WALL, u, y, v, in, ring ? TUNNEL_LINING : tile);
				}
			}
		}
		// side walls
		for (final int u : new int[]{0, w - 1}) {
			final LocalDir in = u == 0 ? LocalDir.PLUS_U : LocalDir.MINUS_U;
			for (int v = v0; v <= v1; v++) {
				for (int y = StationGeometry.PLATFORM_Y; y < CEILING_Y; y++) {
					put(Role.WALL, u, y, v, in, tile);
				}
			}
		}
		boxColumns();
		// the ceiling slab; its top is the street, one material all over
		for (int u = 0; u < w; u++) {
			for (int v = v0; v <= v1; v++) {
				put(Role.BUILDING_ROOF, u, CEILING_Y, v, LocalDir.PLUS_V, b.deck());
			}
		}
		ceilingLights(b.light());
		streetOpenings();
	}

	/**
	 * Lights hung under the slab over the platforms, not set in it: a light in the slab showed its top in the street. Only where
	 * nothing stands below, clear of the stairs.
	 */
	void ceilingLights(String light) {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			for (int u = band.uMin(); u <= band.uMax(); u++) {
				for (int v = band.vFrom(); v <= band.vTo(); v++) {
					if (band.runs(v) && Math.floorMod(u + v, LIGHT_EVERY) == 0 && !nearStairs(band, v)
							&& freeColumn(u, StationGeometry.WALK_Y, CEILING_Y - 1, v)) {
						put(Role.LIGHT_PANEL, u, CEILING_Y - 1, v, LocalDir.PLUS_V, light);
					}
				}
			}
		}
	}

	/** A cell of a tunnel mouth in an end wall: within a track's envelope. */
	boolean mouth(int u, int y) {
		if (y < StationGeometry.PLATFORM_Y || y >= MOUTH_HEIGHT) {
			return false;
		}
		return layout.tracks().stream().anyMatch(t -> Math.abs(u - t.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH);
	}

	boolean onPlatform(int u, int v) {
		return layout.platforms().stream().anyMatch(band -> u >= band.uMin() && u <= band.uMax() && band.runs(v));
	}

	/** A column down the middle of each platform wide enough to walk round it, every {@link StationGeometry#COLUMN_SPACING}. */
	void boxColumns() {
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (band.uMax() - band.uMin() + 1 < StationGeometry.BAY + 1) {
				continue;
			}
			final int u = (band.uMin() + band.uMax()) / 2;
			for (int v = band.vFrom() + StationGeometry.COLUMN_SPACING / 2; v <= band.vTo() - 2; v += StationGeometry.COLUMN_SPACING) {
				if (nearStairs(band, v) || !freeColumn(u, StationGeometry.WALK_Y, CEILING_Y - 1, v)) {
					continue;
				}
				for (int y = StationGeometry.WALK_Y; y < CEILING_Y; y++) {
					put(y == CEILING_Y - 1 ? Role.COLUMN_CAP : Role.COLUMN, u, y, v, LocalDir.PLUS_V);
				}
			}
		}
	}

	/**
	 * On the street: a rail on the slab round every opening a flight comes up through (the way off its top step stays open),
	 * and an entrance pylon with the station's name beside each flight, clear of the way off it.
	 */
	void streetOpenings() {
		final List<StationLayout.Flight> flights = new ArrayList<>();
		final List<StationLayout> bridges = far == null ? List.of(site) : List.of(site, far);
		for (final StationLayout bridge : bridges) {
			for (final TrackLayout.PlatformBand band : bridge.bridgeBands(layout)) {
				flights.addAll(bridge.flights(band));
			}
		}
		for (int u = 0; u < layout.width(); u++) {
			for (int v = layout.vMin(); v <= layout.vMax(); v++) {
				final PlannedBlock slab = at(u, CEILING_Y, v);
				if (slab == null || slab.role() == Role.STAIR || slab.role() == Role.ESCALATOR || slab.role() == Role.LIFT) {
					continue;
				}
				for (final int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					if (cleared(u + d[0], CEILING_Y, v + d[1]) && at(u + d[0], CEILING_Y, v + d[1]) == null) {
						put(Role.BARRIER, u, STREET_Y, v, d[0] == 0 ? LocalDir.PLUS_V : LocalDir.PLUS_U);
						break;
					}
				}
			}
		}
		final String name = spec.name().isEmpty() ? "Underground" : spec.name();
		for (final StationLayout.Flight flight : flights) {
			final int beside = flight.wallV() + flight.side() * 2;
			final int first = flight.columns().get(0);
			final int last = flight.columns().get(flight.columns().size() - 1);
			for (final int u : new int[]{last + 2, first - 2}) {
				if (u < 1 || u > layout.width() - 2 || !slab(u, beside) || taken(u, STREET_Y, beside) || taken(u, STREET_Y + 1, beside)) {
					continue;
				}
				final LocalDir face = flight.side() > 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
				putText(Role.FIXTURE, u, STREET_Y, beside, face, PYLON, "NONE:" + name);
				put(Role.FIXTURE, u, STREET_Y + 1, beside, face, PYLON, "half=upper");
				break;
			}
		}
	}

	/** The slab is planned at (u, v): something to stand a pylon on. */
	boolean slab(int u, int v) {
		final PlannedBlock block = at(u, CEILING_Y, v);
		return block != null && (block.role() == Role.BUILDING_ROOF || block.role() == Role.LIGHT_PANEL || block.role() == Role.BRIDGE_DECK);
	}
}
