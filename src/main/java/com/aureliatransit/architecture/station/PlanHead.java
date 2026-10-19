package com.aureliatransit.architecture.station;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * The head building of a terminus: facade, giant arch, domes, towers and crowns.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanHead extends PlanLifts {

	PlanHead(StationSpec spec) {
		super(spec);
	}

	/** The u of a terminus head building's street entrance (the middle of the 3-wide doorway). */
	int headDoor() {
		return (layout.headFrom() + layout.headTo() + 1) / 2;
	}

	/**
	 * A terminus head building across the track ends (v = L .. L+11, the full width, or only a hub's platforms): the floor
	 * runs level from the platform ends; buffer stops close every bay track at v = L; the platform side is open under a
	 * lintel with clerestory windows; side and street walls carry windows and a trim band; a 3-wide street entrance with
	 * ramps outside; a flat roof with light panels and a cornice. The gate line and fittings are in {@link #headInterior()}.
	 */
	void head() {
		if (!layout.terminus()) {
			return;
		}
		final StylePalette.Building b = palette.building();
		// a hub's head building spans its platforms only; the outer tracks run on past its side walls
		final int u0 = layout.headFrom();
		final int u1 = layout.headTo();
		final int vL = length;
		final int vE = layout.headEnd();
		final int door = headDoor();
		for (int u = door - 1; u <= door + 1; u++) {
			for (int y = 1; y <= 3; y++) {
				keepClear(u, y, vE);
				keepClear(u, y, vE + 1);
				keepClear(u, y, vE + 2);
			}
		}
		if (scale != StationScale.COMPACT) {
			headFront(u0, u1, vE, door);
		}
		for (int u = u0; u <= u1; u++) {
			final TrackLayout.PlatformBand band = bandAt(u);
			for (int v = vL; v <= vE; v++) {
				final boolean edge = u == u0 || u == u1 || v == vE;
				// each platform's colour runs across the floor either side of its gates
				final boolean stripe = !edge && band != null && (v == vL + 1 || v == vL + 3);
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : stripe ? platformColour(band) : b.floor());
			}
		}
		// buffer stops across each track's clearance, just past its last rail node
		for (final TrackLayout.Track track : layout.tracks()) {
			if (!layout.bay(track)) {
				continue;
			}
			for (int u = track.u() - StationGeometry.ENVELOPE_HALF_WIDTH; u <= track.u() + StationGeometry.ENVELOPE_HALF_WIDTH; u++) {
				put(Role.BUFFER_STOP, u, StationGeometry.WALK_Y, vL, LocalDir.PLUS_U);
			}
		}
		final int top = StationGeometry.BUILDING_ROOF_Y - 1;
		// side walls
		for (int v = vL; v <= vE; v++) {
			final boolean pilaster = (v - vL) % 3 == 0;
			for (int y = 1; y <= top; y++) {
				final String id = y == 1 ? (pilaster ? b.trim() : b.plinth()) : y == StationGeometry.UPPER_FLOOR_Y || pilaster ? b.trim() : b.wall();
				final boolean window = !pilaster && windowRow(y);
				put(window ? Role.WINDOW : Role.WALL, u0, y, v, LocalDir.MINUS_U, window ? b.window() : id);
				put(window ? Role.WINDOW : Role.WALL, u1, y, v, LocalDir.PLUS_U, window ? b.window() : id);
			}
		}
		// street wall with the entrance, and the platform-side lintel and clerestory over the open arcade
		for (int u = u0 + 1; u < u1; u++) {
			final boolean pilaster = (u - u0) % 3 == 0;
			for (int y = 1; y <= top; y++) {
				final String id = y == 1 ? (pilaster ? b.trim() : b.plinth()) : y == StationGeometry.UPPER_FLOOR_Y || pilaster ? b.trim() : b.wall();
				final boolean window = !pilaster && y > StationGeometry.UPPER_FLOOR_Y && windowRow(y);
				// ground-floor windows either side of the entrance
				final boolean street = window || !pilaster && windowRow(y) && Math.abs(u - door) > 2;
				put(street ? Role.WINDOW : Role.WALL, u, y, vE, LocalDir.PLUS_V, street ? b.window() : id);
				if (y >= StationGeometry.UPPER_FLOOR_Y) {
					// the lintel over each platform carries its colour, with its number hung below
					final TrackLayout.PlatformBand band = bandAt(u);
					final String lintel = y == StationGeometry.UPPER_FLOOR_Y && band != null ? platformColour(band) : id;
					put(window ? Role.WINDOW : Role.WALL, u, y, vL, LocalDir.MINUS_V, window ? b.window() : lintel);
				}
			}
		}
		// entrance ramps outside the street wall
		for (int u = door - 1; u <= door + 1; u++) {
			put(Role.RAMP_UPPER, u, StationGeometry.PLATFORM_Y, vE + 1, LocalDir.PLUS_V);
			put(Role.RAMP_LOWER, u, StationGeometry.PLATFORM_Y, vE + 2, LocalDir.PLUS_V);
		}
		if (scale != StationScale.COMPACT) {
			// a city or grand head building: storeys over the whole concourse (its ceiling is their first floor), the roof,
			// and a grand one's clock tower over the entrance
			for (int storey = 0; storey < scale.extraStoreys(); storey++) {
				storey(StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * storey, u0, u1, vL, vE, true, true);
			}
			headCrown(u0, u1, vL, vE, door);
			return;
		}
		// a ceiling with light panels, covered by a flat roof flush with the cornice (the panels' tops would otherwise show
		// from above as a checkerboard)
		for (int u = u0; u <= u1; u++) {
			for (int v = vL; v <= vE; v++) {
				final boolean inner = u > u0 && u < u1 && v > vL && v < vE;
				put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, inner && (u + v) % 4 == 0 ? b.light() : b.roof());
				put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.PLUS_V, inner ? b.roof() : b.cornice());
			}
		}
	}

	/**
	 * The street front of a city or grand head building, planned before its plain walls so it takes their cells: giant
	 * arched windows (one, two or three), tall round-headed windows between columns, or a glass curtain wall, by the look's
	 * {@link BuildingStyle.Facade}. Brick looks keep their banded walls; their towers come with the roof.
	 */
	void headFront(int u0, int u1, int vE, int door) {
		final StylePalette.Building b = palette.building();
		final BuildingStyle look = spec.building();
		final int width = u1 - u0 + 1;
		// windows stop two rows under the roof, so the cornice band runs unbroken over them
		final int top = roofY() - 2;
		switch (look.facade()) {
			case BRICK_TOWERS -> {
				if (look == BuildingStyle.MUMBAI || look == BuildingStyle.GOTHIC_BRICK || look == BuildingStyle.TOKYO) {
					// a tall arch over the entrance and an arcade of arches along the ground floor: pointed (Gothic) in Mumbai
					// and London, round-headed in Tokyo
					final ArchShape shape = look == BuildingStyle.TOKYO ? ArchShape.ROUND : ArchShape.POINTED;
					giantArch(door, 3, StationGeometry.UPPER_FLOOR_Y + 2, vE, u0, u1, shape);
					for (int c = door - 9; c - 2 > u0; c -= 6) {
						giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, shape);
					}
					for (int c = door + 9; c + 2 < u1; c += 6) {
						giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, shape);
					}
				}
			}
			case ARCHED_GLASS -> {
				final int arches = look == BuildingStyle.SANDSTONE && width >= 30 ? 3 : look == BuildingStyle.CLINKER && width >= 28 ? 2 : 1;
				if (look == BuildingStyle.BANGKOK) {
					// a colonnade of white columns, one every third cell, along the whole front; the great window rises over
					// the roof with the crown (headCrown)
					for (int c = u0 + 2; c < u1 - 1; c += 3) {
						if (Math.abs(c - door) > 1) {
							columnRun(c, 1, StationGeometry.BUILDING_ROOF_Y - 1, vE, u0, u1, 1);
						}
					}
				} else if (arches == 1) {
					final int h = Math.min(top - 4, Math.max(2, width / 6));
					giantArch(door, h, top, vE, u0, u1);
				} else if (arches == 2) {
					final int h = Math.min(top - 4, width / 8);
					giantArch(u0 + width / 4, h, top, vE, u0, u1);
					giantArch(u1 - width / 4, h, top, vE, u0, u1);
				} else {
					final int h = Math.min(top - 4, width / 8);
					for (final int c : new int[]{door - width / 3, door, door + width / 3}) {
						giantArch(c, h, top, vE, u0, u1);
					}
				}
			}
			case SCULPTED_ATTIC -> {
				if (look == BuildingStyle.KUALA_LUMPUR) {
					// an arcade of pointed arches along the ground floor, and one taller round arch over the entrance
					final int h = Math.min(top - 4, Math.max(2, width / 8));
					giantArch(door, h, top, vE, u0, u1);
					for (int c = door - h - 4; c - 2 > u0; c -= 5) {
						giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, ArchShape.POINTED);
					}
					for (int c = door + h + 4; c + 2 < u1; c += 5) {
						giantArch(c, 2, StationGeometry.UPPER_FLOOR_Y - 1, vE, u0, u1, ArchShape.POINTED);
					}
				} else if (look == BuildingStyle.PARIS || width < 30) {
					// one great window over the entrance between pairs of columns
					final int h = Math.min(top - 4, Math.max(2, width / 6));
					giantArch(door, h, top, vE, u0, u1);
					columnPair(door - h - 1, -1, top, vE, u0, u1);
					columnPair(door + h + 1, 1, top, vE, u0, u1);
				} else {
					// three tall round-headed windows, with paired columns between and beside them
					final int h = 3;
					for (final int c : new int[]{door - 10, door, door + 10}) {
						giantArch(c, h, top, vE, u0, u1);
						columnPair(c - h - 1, -1, top, vE, u0, u1);
						columnPair(c + h + 1, 1, top, vE, u0, u1);
					}
				}
			}
			case GLASS_MODERN -> {
				// a curtain wall: glass between mullions every three cells, with a band at every floor; a glass hall (Utrecht,
				// Hong Kong) keeps only the first-floor band, and Munich's concrete has ribbon windows, two rows of glass in
				// every storey
				final boolean hall = look == BuildingStyle.MODERN || look == BuildingStyle.HONG_KONG || look == BuildingStyle.BEIJING;
				final boolean ribbons = look == BuildingStyle.CONCRETE;
				for (int u = u0 + 1; u < u1; u++) {
					for (int y = 1; y < roofY(); y++) {
						final boolean upper = y >= StationGeometry.BUILDING_ROOF_Y && (y - StationGeometry.BUILDING_ROOF_Y) % 4 == 0;
						final boolean floorLine = y == StationGeometry.UPPER_FLOOR_Y || !hall && upper
								|| ribbons && Math.floorMod(y - 1, 4) >= 2;
						if (floorLine || !ribbons && (u - u0) % 3 == 0) {
							put(Role.WALL, u, y, vE, LocalDir.PLUS_V, b.trim());
						} else {
							put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
						}
					}
				}
			}
		}
	}

	/**
	 * A giant round-headed window in the street wall (v = {@code vE}), centred on {@code cu}, {@code h} cells each side of
	 * the middle, its crown at {@code top}: glass with glazing bars every three cells and at the first floor, framed by a
	 * ring of stone. Left out where it would not fit between the side walls.
	 */
	void giantArch(int cu, int h, int top, int vE, int u0, int u1) {
		giantArch(cu, h, top, vE, u0, u1, ArchShape.ROUND);
	}

	/** How the head of an arched window is drawn. */
	enum ArchShape {
		/** A semicircle: as high as half the span. */
		ROUND,
		/** Two arcs of radius the full span meeting at a point (equilateral Gothic): about 1.7 times the half span. */
		POINTED,
		/** A flat arc, part of a circle, rising three tenths of the span (Bangkok's great window). */
		SEGMENTAL
	}

	/** Height of an arch's head above its springing line, for half span {@code h}. */
	static int archRise(ArchShape shape, int h) {
		return switch (shape) {
			case ROUND -> h;
			case POINTED -> (int) Math.round(Math.sqrt(3) * h);
			case SEGMENTAL -> Math.max(1, (int) Math.round(h * 0.6));
		};
	}

	/**
	 * Distance of cell (du, k) from the arch's curve, in the curve's own radius units: below zero inside the opening, at zero
	 * on the curve. {@code k} counts rows above the springing line.
	 */
	static double archDistance(ArchShape shape, int du, int k, int h) {
		return switch (shape) {
			case ROUND -> Math.hypot(du, k) - h;
			// each side is an arc of radius 2h centred on the far side's springing point
			case POINTED -> Math.hypot(Math.abs(du) + h, k) - 2 * h;
			case SEGMENTAL -> {
				final int r = archRise(shape, h);
				final double radius = (h * h + r * r) / (2.0 * r);
				yield Math.hypot(du, k + radius - r) - radius;
			}
		};
	}

	/** {@link #giantArch(int, int, int, int, int, int)} with a round, pointed or segmental head. */
	void giantArch(int cu, int h, int top, int vE, int u0, int u1, ArchShape shape) {
		if (h < 2 || cu - h <= u0 || cu + h >= u1) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int spring = top - archRise(shape, h);
		// the ring and glazing bars in a stone that stands out from the wall
		final String frame = switch (spec.building()) {
			case SANDSTONE -> ata("antwerp_ornate_limestone");
			case CLINKER, ANTWERP, ZURICH -> ata("frankfurt_sandstone_rusticated");
			case BANGKOK -> "minecraft:smooth_quartz";
			default -> b.trim();
		};
		// Mumbai's arch stones alternate red and cream
		final boolean banded = spec.building() == BuildingStyle.MUMBAI;
		for (int u = cu - h; u <= cu + h; u++) {
			final int du = Math.abs(u - cu);
			for (int y = 1; y <= top; y++) {
				final boolean inside;
				final boolean ring;
				if (y <= spring) {
					inside = du < h;
					ring = du == h;
				} else {
					final double d = archDistance(shape, du, y - spring, h);
					inside = d < -0.5;
					ring = !inside && d < 0.5;
				}
				final boolean bar = y == StationGeometry.UPPER_FLOOR_Y || y > 3 && du % 3 == 0;
				if (ring && banded && y > spring && (u + y) % 2 == 0) {
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, "minecraft:smooth_red_sandstone");
				} else if (ring || inside && bar) {
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, frame);
				} else if (inside) {
					put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, ARCH_GLASS);
				}
			}
		}
	}

	/**
	 * A barrel vault standing on the head building's roof at {@code top}, centred on {@code cu}, {@code h} cells each side
	 * and a segmental arch in section, from the platform side ({@code vL}) to the street front ({@code vE}): its street end
	 * a great glazed window with a white frame, glazing bars every three cells and a clock, its platform end glazed, its
	 * shell in smooth stone.
	 */
	void lunetteVault(int cu, int h, int top, int vL, int vE) {
		final int rise = archRise(ArchShape.SEGMENTAL, h);
		for (int u = cu - h; u <= cu + h; u++) {
			final int du = Math.abs(u - cu);
			for (int k = 1; k <= rise + 1; k++) {
				final double d = archDistance(ArchShape.SEGMENTAL, du, k, h);
				if (d >= 0.5) {
					continue;
				}
				final boolean ring = d > -0.5;
				for (int v = vL; v <= vE; v++) {
					final boolean end = v == vL || v == vE;
					if (ring) {
						put(Role.BUILDING_ROOF, u, top + k, v, LocalDir.PLUS_V, v == vE ? "minecraft:smooth_quartz" : "minecraft:smooth_stone");
					} else if (end) {
						final boolean bar = v == vE && (du % 3 == 0 || k == rise / 2);
						put(bar ? Role.WALL : Role.WINDOW, u, top + k, v, v == vL ? LocalDir.MINUS_V : LocalDir.PLUS_V,
								bar ? "minecraft:smooth_quartz" : ARCH_GLASS);
					} else {
						keepClear(u, top + k, v);
					}
				}
			}
		}
		put(Role.FIXTURE, cu, top + Math.max(2, rise - 2), vE + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
	}

	/** Two columns side by side in the street wall from the ground to {@code top}, from {@code u} going {@code dir}. */
	void columnPair(int u, int dir, int top, int vE, int u0, int u1) {
		columnRun(u, dir, top, vE, u0, u1, 2);
	}

	/** {@code count} columns side by side in the street wall from the ground to {@code top}, from {@code u} going {@code dir}. */
	void columnRun(int u, int dir, int top, int vE, int u0, int u1, int count) {
		for (int k = 0; k < count; k++) {
			final int at = u + dir * k;
			if (at <= u0 || at >= u1) {
				continue;
			}
			for (int y = 1; y <= top; y++) {
				put(Role.WALL, at, y, vE, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
			}
		}
	}

	/**
	 * The top of a city or grand head building, by the look's {@link BuildingStyle.Facade}: clock towers, gables and domed
	 * pavilions over a hipped roof (brick), a tower or a statue group over the arches (arched), a raised attic with a clock
	 * and statues on a flat roof (Beaux-Arts), or a flat roof with a street canopy, a glass tower, a wave or a deep
	 * overhang (modern). Towers and attics come first, so the roof leaves them open.
	 */
	void headCrown(int u0, int u1, int vL, int vE, int door) {
		final BuildingStyle look = spec.building();
		final int top = roofY();
		final int width = u1 - u0 + 1;
		final boolean grand = scale == StationScale.GRAND;
		final int towerV = vE - 4;
		switch (look.facade()) {
			case BRICK_TOWERS -> {
				if (look == BuildingStyle.MUMBAI && width >= 20) {
					// over the entrance a clock tower crowned by a stone onion dome, tall spired Gothic towers at both ends, and
					// where there is room a smaller domed pavilion halfway between them on each side
					final int centre = Math.max(2, Math.min(4, width / 12));
					final int corner = width >= 30 ? 3 : 2;
					octTower(door, vE - centre, centre, top, grand ? 9 : 6, OctCap.ONION, true);
					octTower(u0 + corner, vE - corner, corner, top, grand ? 11 : 8, OctCap.SPIRE, false);
					octTower(u1 - corner, vE - corner, corner, top, grand ? 11 : 8, OctCap.SPIRE, false);
					final int gap = door - centre - (u0 + 2 * corner);
					if (gap >= 8) {
						octTower((u0 + 2 * corner + door - centre) / 2, vE - 2, 2, top, 2, OctCap.ONION, false);
						octTower((u1 - 2 * corner + door + centre) / 2, vE - 2, 2, top, 2, OctCap.ONION, false);
					}
				} else if (look == BuildingStyle.TOKYO && width >= 20) {
					// octagonal corner pavilions under dark slate domes, and over the entrance a domed clock tower or a gable
					final int r = Math.max(2, Math.min(3, width / 12));
					octTower(u0 + r, vE - r, r, top, 4, OctCap.DOME, false);
					octTower(u1 - r, vE - r, r, top, 4, OctCap.DOME, false);
					if (grand) {
						octTower(door, vE - 2, 2, top, 7, OctCap.DOME, true);
					} else {
						gable(door, top, vE);
					}
				} else if (look == BuildingStyle.AMSTERDAM && width >= 30) {
					// twin clock towers at a quarter and three quarters of the front, a gable between them
					tower(top, u0 + width / 4 - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
					tower(top, u1 - width / 4 - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
					gable(door, top, vE);
				} else if (grand || look == BuildingStyle.GOTHIC_BRICK) {
					final boolean gothic = look == BuildingStyle.GOTHIC_BRICK;
					tower(top, door - 2, towerV, gothic ? TOWER_HEIGHT + 4 : TOWER_HEIGHT, LocalDir.MINUS_V, gothic ? Cap.SPIRE : Cap.PYRAMID, true, false);
				} else {
					gable(door, top, vE);
				}
				roof(top, u0, u1, vL, vE);
			}
			case ARCHED_GLASS -> {
				if (look == BuildingStyle.ZURICH) {
					attic(door, Math.max(3, Math.min(6, width / 8)), 2, top, vE, u0, u1, true);
				} else if (look == BuildingStyle.BANGKOK) {
					// the end of the vaulted hall over the colonnade: a great segmental window three fifths of the front wide
					// with a clock in it, between square end pavilions a storey higher
					lunetteVault(door, Math.max(3, width * 3 / 10), top, vL, vE);
					if (width >= 24) {
						tower(top, u0, towerV, 4, LocalDir.MINUS_V, Cap.FLAT, false, false);
						tower(top, u1 - 4, towerV, 4, LocalDir.MINUS_V, Cap.FLAT, false, false);
					}
				} else if (look == BuildingStyle.CLINKER) {
					// the clock tower near one end of the front, a cell in so its side clock stays over the building
					tower(top, u1 - 5, towerV, TOWER_HEIGHT + 2, LocalDir.MINUS_V, Cap.PYRAMID, true, false);
				} else if (grand) {
					tower(top, door - 2, towerV, TOWER_HEIGHT, LocalDir.MINUS_V, look == BuildingStyle.ANTWERP ? Cap.DOME : Cap.PYRAMID, true, false);
				} else {
					gable(door, top, vE);
				}
				roof(top, u0, u1, vL, vE);
			}
			case SCULPTED_ATTIC -> {
				final int half = Math.max(3, Math.min(9, width / 6));
				if (look == BuildingStyle.KUALA_LUMPUR) {
					// a flat-topped centre block with a slim pinnacle at each corner, wide octagonal towers at the corners of the
					// front under open domed pavilions, a small domed kiosk a quarter of the front out on each side, and
					// pinnacles along the parapet between
					final int centre = Math.max(3, Math.min(6, width / 8));
					attic(door, centre, 2, top, vE, u0, u1, false);
					for (final int u : new int[]{door - centre, door + centre}) {
						pinnacle(u, top + 4, vE);
						pinnacle(u, top + 4, vE - 3);
					}
					final int r = Math.max(2, Math.min(4, width / 12));
					if (width >= 20) {
						// a cell in from the corners, so the pavilions' flared eaves stay over the building
						octTower(u0 + r + 1, vE - r - 1, r, top, grand ? 8 : 5, OctCap.CHHATRI, false);
						octTower(u1 - r - 1, vE - r - 1, r, top, grand ? 8 : 5, OctCap.CHHATRI, false);
					}
					for (final int side : new int[]{-1, 1}) {
						final int kiosk = door + side * (width / 4);
						if (Math.abs(kiosk - door) > centre + 2) {
							chhatri(kiosk, top + 1, vE - 1);
						}
					}
					for (int u = u0 + 2 * r + 4; u <= u1 - 2 * r - 4; u += 4) {
						if (Math.abs(u - door) > centre + 1 && Math.abs(Math.abs(u - door) - width / 4) > 2) {
							pinnacle(u, top + 2, vE);
						}
					}
				} else if (look == BuildingStyle.MILAN && grand) {
					// a stepped centre: a wide attic and a taller narrow one on it
					attic(door, half, 3, top, vE, u0, u1, false);
					attic(door, Math.max(2, half / 2), 3, top + 4, vE, u0, u1, true);
				} else {
					attic(door, half, look == BuildingStyle.PARIS ? 2 : 3, top, vE, u0, u1, true);
				}
				if (look == BuildingStyle.PARIS) {
					// statues along the parapet
					for (int u = u0 + 3; u <= u1 - 3; u += 4) {
						statue(u, top + 2, vE);
					}
				}
				roof(top, u0, u1, vL, vE, null);
			}
			case GLASS_MODERN -> {
				// waves over glass halls: Hong Kong's tall white sweep, Utrecht's low one, and Beijing's shallow steel crown
				// under its deep eaves
				switch (look) {
					case HONG_KONG -> waveRoof(top, u0, u1, vL, vE, Math.max(4, Math.min(12, width / 4)), "minecraft:smooth_quartz_stairs",
							"minecraft:smooth_quartz");
					case MODERN -> waveRoof(top, u0, u1, vL, vE, Math.max(2, Math.min(4, width / 10)), "minecraft:polished_diorite_stairs",
							"minecraft:polished_diorite");
					case BEIJING -> waveRoof(top, u0, u1, vL, vE, Math.max(3, Math.min(6, width / 8)), "minecraft:polished_andesite_stairs",
							"minecraft:polished_andesite");
					default -> {
					}
				}
				if (look == BuildingStyle.BERLIN && width >= 20) {
					tower(top, u1 - 4, towerV, TOWER_HEIGHT + 6, LocalDir.MINUS_V, Cap.FLAT, false, true);
				}
				roof(top, u0, u1, vL, vE);
				if (look == BuildingStyle.CONCRETE) {
					// a deep concrete canopy over the street, at the first floor
					overhang(StationGeometry.UPPER_FLOOR_Y, u0, u1, vE, false);
				}
				if (look == BuildingStyle.BEIJING) {
					overhang(top, u0, u1, vE, true);
				}
			}
		}
	}

	/** A stepped gable on the street wall over the entrance, seven cells wide at the cornice, with a clock face. */
	void gable(int door, int top, int vE) {
		final StylePalette.Building b = palette.building();
		for (int k = 0; k < 3; k++) {
			for (int u = door - 3 + k; u <= door + 3 - k; u++) {
				put(Role.WALL, u, top + 1 + k, vE, LocalDir.PLUS_V, k == 0 || u == door - 3 + k || u == door + 3 - k ? b.trim() : b.wall());
			}
		}
		put(Role.FIXTURE, door, top + 2, vE + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
	}

	/**
	 * A raised attic over the street front centred on {@code cu}, {@code half} cells each side and {@code high} rows above
	 * {@code top}, four cells deep: closed walls, a flat top with a cornice, a clock face on its front and, when
	 * {@code statues}, a statue group round the clock and one at each end.
	 */
	void attic(int cu, int half, int high, int top, int vE, int u0, int u1, boolean statues) {
		final StylePalette.Building b = palette.building();
		final int a0 = Math.max(u0 + 1, cu - half);
		final int a1 = Math.min(u1 - 1, cu + half);
		final int v0 = vE - 3;
		final int capY = top + high + 1;
		for (int u = a0; u <= a1; u++) {
			for (int v = v0; v <= vE; v++) {
				final boolean ring = u == a0 || u == a1 || v == v0 || v == vE;
				if (ring) {
					for (int y = top + 1; y <= top + high; y++) {
						put(Role.WALL, u, y, v, outward(u, v, a0, a1, v0, vE), y == top + high ? b.trim() : b.wall());
					}
				}
				put(Role.BUILDING_ROOF, u, capY, v, outward(u, v, a0, a1, v0, vE), ring ? b.cornice() : b.roof());
			}
		}
		put(Role.FIXTURE, cu, top + (high + 1) / 2, vE + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
		if (statues) {
			for (final int u : new int[]{cu - 1, cu, cu + 1, a0, a1}) {
				statue(u, capY + 1, vE - 1);
			}
		}
	}

	/**
	 * A small domed kiosk (a chhatri) three cells across standing on the roof at {@code y}: four white posts, a slab, a
	 * stepped dome and a finial, centred on cell ({@code cu}, {@code cv}).
	 */
	void chhatri(int cu, int y, int cv) {
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				final boolean corner = du != 0 && dv != 0;
				if (corner) {
					put(Role.WALL, cu + du, y, cv + dv, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
					put(Role.WALL, cu + du, y + 1, cv + dv, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
				}
				put(Role.BUILDING_ROOF, cu + du, y + 2, cv + dv, LocalDir.PLUS_V, "minecraft:smooth_quartz_slab", "type=bottom");
			}
		}
		put(Role.BUILDING_ROOF, cu, y + 2, cv, LocalDir.PLUS_V, "minecraft:smooth_quartz");
		put(Role.BUILDING_ROOF, cu, y + 3, cv, LocalDir.PLUS_V, "minecraft:smooth_quartz");
		put(Role.BUILDING_ROOF, cu, y + 4, cv, LocalDir.PLUS_V, "minecraft:end_rod", "facing=up");
	}

	/** How an octagonal tower ends. */
	enum OctCap {
		/** A stepped stone spire with a pinnacle at every corner (Mumbai's Gothic turrets). */
		SPIRE,
		/** An open domed pavilion on eight posts (Kuala Lumpur's corner towers). */
		CHHATRI,
		/** A bulbous dome swelling out over its drum, with a figure on top (Mumbai's central tower). */
		ONION,
		/** A round dome (Tokyo's corner pavilions). */
		DOME
	}

	/** Whether (du, dv) lies inside an octagon of radius {@code r}: a square with its corners cut at 45 degrees. */
	static boolean octagon(int du, int dv, int r) {
		return r >= 0 && Math.max(Math.abs(du), Math.abs(dv)) <= r && Math.abs(du) + Math.abs(dv) <= r + r / 2;
	}

	static LocalDir facing(int du, int dv) {
		return Math.abs(du) >= Math.abs(dv) ? (du < 0 ? LocalDir.MINUS_U : LocalDir.PLUS_U) : (dv < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V);
	}

	/**
	 * An octagonal tower of radius {@code r} standing on the roof at {@code top}, centred on ({@code cu}, {@code cv}),
	 * {@code height} rows of hollow shaft with a band at its foot and crown and slit windows on its four main faces, a
	 * solid floor over the shaft, then its {@code cap}. A clock goes on the street face when {@code clock}. Its inside is
	 * kept clear, so a roof planned later leaves it open.
	 */
	void octTower(int cu, int cv, int r, int top, int height, OctCap cap, boolean clock) {
		final StylePalette.Building b = palette.building();
		final int crown = top + height;
		for (int du = -r; du <= r; du++) {
			for (int dv = -r; dv <= r; dv++) {
				if (!octagon(du, dv, r)) {
					continue;
				}
				final boolean ring = !octagon(du, dv, r - 1);
				for (int y = top + 1; y < crown; y++) {
					if (!ring) {
						keepClear(cu + du, y, cv + dv);
						continue;
					}
					final boolean band = y == top + 1 || y == crown - 1 || (y - top) % 5 == 0;
					final boolean slit = (du == 0 || dv == 0) && !band && (y - top) % 5 >= 2;
					put(slit ? Role.WINDOW : Role.WALL, cu + du, y, cv + dv, facing(du, dv), slit ? b.window() : band ? b.trim() : b.wall());
				}
				put(Role.BUILDING_ROOF, cu + du, crown, cv + dv, facing(du, dv), ring ? b.cornice() : b.roof());
			}
		}
		if (clock) {
			put(Role.FIXTURE, cu, crown - 3, cv + r + 1, LocalDir.PLUS_V, ata("amsterdam_clock_face_panel"));
		}
		switch (cap) {
			case SPIRE -> spire(cu, cv, r, crown);
			case CHHATRI -> openChhatri(cu, cv, r, crown);
			case ONION -> onionDome(cu, cv, r, crown);
			case DOME -> {
				roundDome(cu, cv, r, crown + 1);
				put(Role.BUILDING_ROOF, cu, crown + 1 + r, cv, LocalDir.PLUS_V, "minecraft:lightning_rod");
			}
		}
	}

	/**
	 * A stepped spire of dark slate on an octagon of radius {@code r} from {@code y}: rings narrowing every second row to a
	 * point, a slim pinnacle on each of the four corners of the base, and a finial.
	 */
	void spire(int cu, int cv, int r, int y) {
		final String solid = "minecraft:deepslate_tiles";
		int k = 1;
		for (int rr = r - 1; rr >= 0; rr--) {
			for (int step = 0; step < 2; step++, k++) {
				for (int du = -rr; du <= rr; du++) {
					for (int dv = -rr; dv <= rr; dv++) {
						if (octagon(du, dv, rr)) {
							put(Role.BUILDING_ROOF, cu + du, y + k, cv + dv, LocalDir.PLUS_V, solid);
						}
					}
				}
			}
		}
		put(Role.BUILDING_ROOF, cu, y + k, cv, LocalDir.PLUS_V, solid);
		put(Role.BUILDING_ROOF, cu, y + k + 1, cv, LocalDir.PLUS_V, "minecraft:lightning_rod");
		if (r >= 2) {
			for (final int[] corner : new int[][]{{-r, -r / 2}, {r, -r / 2}, {-r, r / 2}, {r, r / 2}}) {
				put(Role.BUILDING_ROOF, cu + corner[0], y + 1, cv + corner[1], LocalDir.PLUS_V, palette.building().trim());
				put(Role.BUILDING_ROOF, cu + corner[0], y + 2, cv + corner[1], LocalDir.PLUS_V, "minecraft:end_rod", "facing=up");
			}
		}
	}

	/**
	 * An open pavilion on the octagon of radius {@code r} from {@code y}: posts three high at its eight corners, a slab eave
	 * flaring a cell out past them, and a tall dome in the look's dome stone with a finial.
	 */
	void openChhatri(int cu, int cv, int r, int y) {
		final int s = r / 2;
		for (final int[] post : new int[][]{{r, s}, {r, -s}, {-r, s}, {-r, -s}, {s, r}, {-s, r}, {s, -r}, {-s, -r}}) {
			for (int k = 1; k <= 3; k++) {
				put(Role.WALL, cu + post[0], y + k, cv + post[1], LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
			}
		}
		for (int du = -r - 1; du <= r + 1; du++) {
			for (int dv = -r - 1; dv <= r + 1; dv++) {
				if (!octagon(du, dv, r + 1)) {
					continue;
				}
				if (octagon(du, dv, r - 1)) {
					for (int k = 1; k <= 3; k++) {
						keepClear(cu + du, y + k, cv + dv);
					}
				}
				put(Role.BUILDING_ROOF, cu + du, y + 4, cv + dv, LocalDir.PLUS_V, "minecraft:smooth_quartz_slab", "type=top");
			}
		}
		onionDome(cu, cv, Math.max(1, r - 1), y + 4);
	}

	/**
	 * A solid round dome of radius {@code r} whose bottom row is {@code y}: rings of the look's dome block narrowing like a
	 * hemisphere, the top cell a finial seat.
	 */
	void roundDome(int cu, int cv, int r, int y) {
		for (int k = 0; k < r; k++) {
			final double radius = Math.sqrt(Math.max(0, (r + 0.5) * (r + 0.5) - (k + 0.5) * (k + 0.5)));
			for (int du = -r; du <= r; du++) {
				for (int dv = -r; dv <= r; dv++) {
					if (Math.hypot(du, dv) <= radius) {
						put(Role.BUILDING_ROOF, cu + du, y + k, cv + dv, LocalDir.PLUS_V, domeBlock());
					}
				}
			}
		}
	}

	/**
	 * An onion dome on the octagon of radius {@code r} at {@code y}: a shell that swells a third wider than its drum then
	 * draws in to a point, {@code 2r + 1} rows high, with a figure on a short pillar and a finial on top.
	 */
	void onionDome(int cu, int cv, int r, int y) {
		final int rows = 2 * r + 1;
		final double[] radius = new double[rows + 1];
		for (int k = 0; k <= rows; k++) {
			final double t = (double) k / rows;
			radius[k] = r * (t < 0.3 ? 1 + t : 1.3 * Math.cos((t - 0.3) / 0.7 * Math.PI / 2)) + 0.3;
		}
		final int reach = (int) Math.ceil(1.3 * r + 0.3);
		for (int k = 1; k <= rows; k++) {
			for (int du = -reach; du <= reach; du++) {
				for (int dv = -reach; dv <= reach; dv++) {
					final double d = Math.hypot(du, dv);
					if (d > radius[k]) {
						continue;
					}
					if (k == rows || d > Math.min(radius[k], radius[Math.min(rows, k + 1)]) - 1.2) {
						put(Role.BUILDING_ROOF, cu + du, y + k, cv + dv, LocalDir.PLUS_V, domeBlock());
					} else {
						keepClear(cu + du, y + k, cv + dv);
					}
				}
			}
		}
		put(Role.BUILDING_ROOF, cu, y + rows + 1, cv, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
		put(Role.BUILDING_ROOF, cu, y + rows + 2, cv, LocalDir.PLUS_V, "minecraft:lightning_rod");
	}

	/** A slim pinnacle on the parapet at {@code y}: a white shaft two high, a slab, a dome-stone cap and a finial. */
	void pinnacle(int u, int y, int v) {
		put(Role.WALL, u, y, v, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
		put(Role.WALL, u, y + 1, v, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
		put(Role.BUILDING_ROOF, u, y + 2, v, LocalDir.PLUS_V, domeBlock());
		put(Role.BUILDING_ROOF, u, y + 3, v, LocalDir.PLUS_V, "minecraft:end_rod", "facing=up");
	}

	/** A statue two cells high from {@code y}: a carved base and a pillar figure. */
	void statue(int u, int y, int v) {
		put(Role.WALL, u, y, v, LocalDir.PLUS_V, STATUE);
		put(Role.WALL, u, y + 1, v, LocalDir.PLUS_V, "minecraft:quartz_pillar", "axis=y");
	}

	/**
	 * A roof sweeping up from both side walls to a crest {@code crest} rows over the middle, on the flat roof at
	 * {@code top}: {@code stair} steps on the slopes and {@code solid} blocks under them where the slope is steep, so the
	 * shell is closed. Its ends are glazed gables with a mullion every third cell: the head building's own wave.
	 */
	void waveRoof(int top, int u0, int u1, int vL, int vE, int crest, String stair, String solid) {
		final StylePalette.Building b = palette.building();
		final int span = u1 - u0;
		final int[] surface = new int[span + 1];
		for (int u = u0; u <= u1; u++) {
			surface[u - u0] = top + (int) Math.round(crest * Math.sin(Math.PI * (u - u0) / span));
		}
		final int[] low = steps(surface, false);
		for (int u = u0; u <= u1; u++) {
			final int i = u - u0;
			final int y0 = surface[i];
			if (y0 <= top) {
				continue;
			}
			final LocalDir rising = i < span && surface[i + 1] > y0 ? LocalDir.PLUS_U : i > 0 && surface[i - 1] > y0 ? LocalDir.MINUS_U : null;
			for (int v = vL; v <= vE; v++) {
				for (int y = Math.max(top + 1, low[i]); y < y0; y++) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, solid);
				}
				if (rising != null) {
					put(Role.BUILDING_ROOF, u, y0, v, rising, stair, PITCH_STATE);
				} else {
					put(Role.BUILDING_ROOF, u, y0, v, LocalDir.PLUS_V, solid);
				}
				if (v == vL || v == vE) {
					for (int y = top + 1; y < Math.max(top + 1, low[i]); y++) {
						final boolean mullion = (u - u0) % 3 == 0;
						put(mullion ? Role.WALL : Role.WINDOW, u, y, v, v == vL ? LocalDir.MINUS_V : LocalDir.PLUS_V, mullion ? b.trim() : b.window());
					}
				}
			}
		}
	}

	/**
	 * A roof running on two cells past the street wall at {@code y}, across the whole front: a deep street canopy, or with
	 * {@code upturned} its outer row turned up a cell and its corners two, like a Chinese station roof (the station's footprint
	 * ends two cells past the front, at the foot of the entrance ramps).
	 */
	void overhang(int y, int u0, int u1, int vE, boolean upturned) {
		final StylePalette.Building b = palette.building();
		final int depth = 2;
		for (int u = u0; u <= u1; u++) {
			for (int v = vE + 1; v <= vE + depth; v++) {
				put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, b.roof());
			}
			if (upturned) {
				put(Role.BUILDING_ROOF, u, y + 1, vE + depth, LocalDir.PLUS_V, b.trim());
				// the corners sweep up another row
				if (u - u0 < 2 || u1 - u < 2) {
					put(Role.BUILDING_ROOF, u, y + 2, vE + depth, LocalDir.PLUS_V, b.trim());
				}
			}
		}
	}
}
