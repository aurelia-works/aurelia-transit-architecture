package com.aureliatransit.architecture.station;

import java.util.HashSet;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * The station building: storeys, roofs, towers and wings.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanBuilding extends PlanPlatforms {

	PlanBuilding(StationSpec spec) {
		super(spec);
	}

	/**
	 * The station building shell beside the tracks: floor, entrance ramps, perimeter walls with pilasters and windows,
	 * the band and upper slab with lights, the flat roof with a cornice parapet, the street entrance and platform
	 * doorways, and the inner stair to the upper floor. Interior fitting is in {@link #interior()}.
	 */
	void building() {
		if (!site.building()) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		final int vS = site.vStart();
		final int vE = site.vEnd();
		final int vEntr = site.entranceV();
		final int uA = uP - 2;
		final int uB = uP - 1;
		final int door = entranceHalf();
		// reserve the openings before any wall goes up
		for (int v = vEntr - door; v <= vEntr + door; v++) {
			for (int y = 1; y <= 3; y++) {
				keepClear(uS, y, v);
			}
		}
		if (site.bridge()) {
			// (a wide corridor's mouth leaves the lift shaft's side wall standing)
			final StationLayout.Lift shaft = site.buildingLift(layout);
			for (int v = site.bridgeV() - bridgeHalf(); v <= site.bridgeV() + bridgeHalf(); v++) {
				if (shaft != null && v >= shaft.vMin() && v <= shaft.vMax()) {
					continue;
				}
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					keepClear(uP, y, v);
				}
			}
		}
		for (int bay = 0; bay < site.bays(); bay++) {
			final int centre = site.bayCentre(bay);
			final StationLayout.Lift lift = site.buildingLift(layout);
			final boolean clearOfLift = lift == null || centre + 1 < lift.vMin() || centre - 1 > lift.vMax();
			if (clearOfLift && (centre + 1 < site.innerStairMin() || centre - 1 > site.innerStairMax())) {
				platformDoorway(uP, centre, b.window());
			}
		}
		// with wings, the paid side opens into their waiting rooms through a three-wide doorway in each end wall; the wall
		// stays down to the floor either side of it
		if (wingBays > 0) {
			final int doorCentre = (uS + 6 + uP - 2) / 2;
			for (int u = doorCentre - 1; u <= doorCentre + 1; u++) {
				for (int y = 1; y <= 3; y++) {
					keepClear(u, y, vS);
					keepClear(u, y, vE);
				}
			}
		}
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int u = uA; u <= uB; u++) {
				keepClear(u, y + 1, v);
				keepClear(u, y + 2, v);
			}
		}
		final int vBot = site.innerStairV(1) + site.innerStairSide();
		for (int u = uA; u <= uB; u++) {
			for (int y = 1; y <= 3; y++) {
				keepClear(u, y, vBot);
			}
		}
		// a real station's own building first, so the plain walls below only fill what its sheet leaves open
		landmarkSide();
		final boolean sheet = landmark != null && landmark.anchor() == Landmark.Anchor.SIDE;
		// ground floor and entrance ramps on the apron
		for (int u = uS; u <= uP; u++) {
			for (int v = vS; v <= vE; v++) {
				// the plinth under the walls, so the patterned floor does not show as a band along the base
				final boolean edge = u == uS || u == uP || v == vS || v == vE;
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : b.floor());
			}
		}
		for (int v = vEntr - door; v <= vEntr + door; v++) {
			put(Role.RAMP_LOWER, apronU(), StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
			put(Role.RAMP_UPPER, apronU() + 1, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
			for (int y = 1; y <= 3; y++) {
				keepClear(apronU(), y, v);
				keepClear(apronU() + 1, y, v);
			}
		}
		// perimeter walls, ground storey y=1..4
		for (int v = vS; v <= vE; v++) {
			final boolean pilaster = (v - vS) % StationGeometry.BAY == 0;
			for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
				if (y == 1) {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.plinth());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.plinth());
				} else if (!windowRow(y)) {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.wall());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.wall());
				} else if (isBayCentre(v)) {
					put(Role.WINDOW, uS, y, v, LocalDir.MINUS_U, b.window());
					put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
				} else {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.wall());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.wall());
				}
			}
			// band at the slab level (leaving the bridge doorway open)
			put(Role.WALL, uS, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.MINUS_U, b.trim());
			if (!(site.bridge() && v >= site.bridgeV() - bridgeHalf() && v <= site.bridgeV() + bridgeHalf())) {
				put(Role.WALL, uP, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_U, b.trim());
			}
			// the upper storey, windows in its middle rows
			for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
				if (windowRow(y) && isBayCentre(v)) {
					put(Role.WINDOW, uS, y, v, LocalDir.MINUS_U, b.window());
					put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
				} else {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.wall());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.wall());
				}
			}
		}
		for (int u = uS; u <= uP; u++) {
			final boolean pilaster = (u - uS) % 3 == 0;
			for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
				if (y == 1) {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.plinth());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.plinth());
				} else if (!windowRow(y)) {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
				} else if (u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0) {
					put(Role.WINDOW, u, y, vS, LocalDir.MINUS_V, b.window());
					put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
				} else {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
				}
			}
			put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vS, LocalDir.MINUS_V, b.trim());
			put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vE, LocalDir.PLUS_V, b.trim());
			for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
				if (windowRow(y) && u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0) {
					put(Role.WINDOW, u, y, vS, LocalDir.MINUS_V, b.window());
					put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
				} else {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
				}
			}
		}
		// upper slab with light panels, leaving the stair well open over the flight only (the top step is part of the slab), and
		// running on past both ends of the flight, so there is no gap to fall through. A panel that falls
		// in the well moves to the slab beside it, so the stair's landing is not left dark
		final int wellMin = Math.min(site.innerStairV(StationGeometry.STAIR_STEPS), site.innerStairV(1));
		final int wellMax = Math.max(site.innerStairV(StationGeometry.STAIR_STEPS), site.innerStairV(1));
		final Set<Cell> moved = new HashSet<>();
		for (int u = uA; u <= uB; u++) {
			for (int v = wellMin; v <= wellMax; v++) {
				if ((u + v) % 4 != 0) {
					continue;
				}
				for (final int[] c : new int[][]{{uA - 1, v}, {uB + 1, v}, {u, wellMin - 1}, {u, wellMax + 1}}) {
					if (c[0] > uS && c[0] < uP && c[1] > vS && c[1] < vE && !(c[0] >= uA && c[0] <= uB && c[1] >= wellMin
							&& c[1] <= wellMax)) {
						moved.add(new Cell(c[0], StationGeometry.UPPER_FLOOR_Y, c[1]));
						break;
					}
				}
			}
		}
		for (int u = uS + 1; u <= uP - 1; u++) {
			for (int v = vS + 1; v <= vE - 1; v++) {
				if (u >= uA && u <= uB && v >= wellMin && v <= wellMax) {
					continue;
				}
				if ((u + v) % 4 == 0 || moved.contains(new Cell(u, StationGeometry.UPPER_FLOOR_Y, v))) {
					put(Role.LIGHT_PANEL, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
				} else {
					put(Role.FLOOR, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.upperFloor());
				}
			}
		}
		// storeys above the second (a city or grand station), the clock tower, then the roof and cornice parapet
		for (int storey = 0; storey < scale.extraStoreys(); storey++) {
			storey(StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * storey, uS, uP, vS, vE, true);
		}
		final int top = roofY();
		if (scale == StationScale.GRAND && !sheet) {
			clockTower(top, uS, site.entranceV() - 2, LocalDir.PLUS_U);
		}
		roof(top, uS, uP, vS, vE, sheet ? null : pitchedRoof());
		// inner stair flights (two columns wide)
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int u = uA; u <= uB; u++) {
				put(Role.STAIR, u, y, v, site.innerStairFront(), b.stair());
				// a stringer under each step too low to stand under, so nobody walks in under the flight's low end
				if (y <= StationGeometry.HEADROOM) {
					for (int below = StationGeometry.WALK_Y; below < y; below++) {
						put(Role.WALL, u, below, v, site.innerStairFront(), b.interiorWall());
					}
				}
			}
		}
		// the flight's open side: a wall under the steps with a glass barrier along them, carried on round the well upstairs
		// and across its far end, so nobody steps off the stair or the upper floor into the drop
		final int side = site.innerStairSide();
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int h = 1; h <= y; h++) {
				put(Role.WALL, uA - 1, h, v, LocalDir.MINUS_U, b.interiorWall());
			}
			put(Role.BARRIER, uA - 1, y + 1, v, LocalDir.PLUS_U);
			if (y < StationGeometry.STAIR_STEPS) {
				put(Role.BARRIER, uA - 1, StationGeometry.UPPER_WALK_Y, v, LocalDir.PLUS_U);
			}
		}
		for (int u = uA; u <= uB; u++) {
			put(Role.BARRIER, u, StationGeometry.UPPER_WALK_Y, site.innerStairV(1) + side, side < 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V);
		}
	}

	/** y of the building block's roof: above its two storeys and any storeys a city or grand station adds. */
	int roofY() {
		return StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * scale.extraStoreys();
	}

	/**
	 * One more storey on a building box from {@code floorY}: the slab (a trim band round the edge, the floor with light
	 * panels inside, which light the storey below too) and three wall rows with a window row in the middle, pilasters
	 * every bay along the long walls. {@code endWindows} puts windows in the end walls too (a wing's end walls are shared).
	 */
	void storey(int floorY, int uS, int uP, int vS, int vE, boolean endWindows) {
		storey(floorY, uS, uP, vS, vE, endWindows, false);
	}

	/**
	 * {@link #storey(int, int, int, int, int, boolean)}; with {@code allRound} the side walls take the end walls' rhythm (a
	 * window every other cell, trim every third), so a head building's street front carries on round its corners.
	 */
	void storey(int floorY, int uS, int uP, int vS, int vE, boolean endWindows, boolean allRound) {
		final StylePalette.Building b = palette.building();
		for (int u = uS; u <= uP; u++) {
			for (int v = vS; v <= vE; v++) {
				final boolean edge = u == uS || u == uP || v == vS || v == vE;
				if (edge) {
					put(Role.WALL, u, floorY, v, outward(u, v, uS, uP, vS, vE), b.trim());
				} else {
					slabCell(u, floorY, v);
				}
			}
		}
		for (int y = floorY + 1; y < floorY + StationGeometry.STOREY; y++) {
			final boolean windowRow = windowRow(y);
			for (int v = vS; v <= vE; v++) {
				final boolean pilaster = allRound ? (v - vS) % 3 == 0 : Math.floorMod(v - vS, StationGeometry.BAY) == 0;
				final boolean window = allRound ? windowRow && v >= vS + 2 && v <= vE - 2 && (v - vS) % 2 == 0
						: windowRow && !pilaster && Math.abs(Math.floorMod(v - vS, StationGeometry.BAY) - 3) <= 1;
				for (final int u : new int[]{uS, uP}) {
					final LocalDir front = u == uS ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					if (window) {
						put(Role.WINDOW, u, y, v, front, b.window());
					} else {
						put(Role.WALL, u, y, v, front, pilaster ? b.trim() : b.wall());
					}
				}
			}
			for (int u = uS + 1; u <= uP - 1; u++) {
				final boolean window = endWindows && windowRow && u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0;
				for (final int v : new int[]{vS, vE}) {
					final LocalDir front = v == vS ? LocalDir.MINUS_V : LocalDir.PLUS_V;
					put(window ? Role.WINDOW : Role.WALL, u, y, v, front, window ? b.window() : (u - uS) % 3 == 0 ? b.trim() : b.wall());
				}
			}
		}
	}

	/** A floor slab cell: a light panel on every fourth diagonal, so no floor cell above or below is more than 2 from one. */
	void slabCell(int u, int y, int v) {
		final StylePalette.Building b = palette.building();
		if (Math.floorMod(u + v, 4) == 0) {
			put(Role.LIGHT_PANEL, u, y, v, LocalDir.PLUS_V, b.light());
		} else {
			put(Role.FLOOR, u, y, v, LocalDir.PLUS_V, b.upperFloor());
		}
	}

	static LocalDir outward(int u, int v, int uS, int uP, int vS, int vE) {
		if (u == uS) {
			return LocalDir.MINUS_U;
		}
		if (u == uP) {
			return LocalDir.PLUS_U;
		}
		return v == vS ? LocalDir.MINUS_V : LocalDir.PLUS_V;
	}

	/**
	 * The roof of a building box at {@code top}: a flat roof with a cornice parapet, or for a masonry look at city or grand
	 * scale a hipped roof of stairs inside the cornice, over an attic lit through its floor.
	 */
	void roof(int top, int uS, int uP, int vS, int vE) {
		roof(top, uS, uP, vS, vE, pitchedRoof());
	}

	/** {@link #roof(int, int, int, int, int)} with the given pitched-roof blocks, or flat for null. */
	void roof(int top, int uS, int uP, int vS, int vE, String[] pitched) {
		final StylePalette.Building b = palette.building();
		for (int u = uS; u <= uP; u++) {
			for (int v = vS; v <= vE; v++) {
				final boolean edge = u == uS || u == uP || v == vS || v == vE;
				if (pitched != null && !edge && Math.floorMod(u + v, 4) == 0) {
					put(Role.LIGHT_PANEL, u, top, v, LocalDir.PLUS_V, b.light());
				} else {
					put(Role.BUILDING_ROOF, u, top, v, LocalDir.PLUS_V, b.roof());
				}
			}
		}
		for (int v = vS; v <= vE; v++) {
			put(Role.BUILDING_ROOF, uS, top + 1, v, LocalDir.MINUS_U, b.cornice());
			put(Role.BUILDING_ROOF, uP, top + 1, v, LocalDir.PLUS_U, b.cornice());
		}
		for (int u = uS; u <= uP; u++) {
			put(Role.BUILDING_ROOF, u, top + 1, vS, LocalDir.MINUS_V, b.cornice());
			put(Role.BUILDING_ROOF, u, top + 1, vE, LocalDir.PLUS_V, b.cornice());
		}
		if (pitched == null) {
			return;
		}
		hipped(uS + 1, uP - 1, vS + 1, vE - 1, top + 1, pitched[0], pitched[1], Integer.MAX_VALUE);
	}

	/**
	 * A hipped roof over u0..u1 x v0..v1 from {@code y}: rings of stairs stepping in one cell and up one each, every stair
	 * rising towards the middle, at most {@code rings} rings, then a full-block top. Each ring above the first rests on a
	 * full block, the roof's thickness, so every tile touches the one below it face to face.
	 */
	void hipped(int u0, int u1, int v0, int v1, int y, String stair, String solid, int rings) {
		for (int k = 0; ; k++) {
			final int a0 = u0 + k;
			final int a1 = u1 - k;
			final int b0 = v0 + k;
			final int b1 = v1 - k;
			final int at = y + k;
			if (a0 >= a1 || b0 >= b1 || k == rings) {
				for (int u = a0; u <= a1; u++) {
					for (int v = b0; v <= b1; v++) {
						if (k > 0) {
							put(Role.BUILDING_ROOF, u, at - 1, v, LocalDir.PLUS_V, solid);
						}
						put(Role.BUILDING_ROOF, u, at, v, LocalDir.PLUS_V, solid);
					}
				}
				return;
			}
			for (int u = a0; u <= a1; u++) {
				for (int v = b0; v <= b1; v++) {
					if (u != a0 && u != a1 && v != b0 && v != b1) {
						continue;
					}
					if (k > 0) {
						put(Role.BUILDING_ROOF, u, at - 1, v, LocalDir.PLUS_V, solid);
					}
					final LocalDir up = v == b0 ? LocalDir.PLUS_V : v == b1 ? LocalDir.MINUS_V : u == a0 ? LocalDir.PLUS_U : LocalDir.MINUS_U;
					put(Role.BUILDING_ROOF, u, at, v, up, stair, PITCH_STATE);
				}
			}
		}
	}

	/** Stairs and the matching full block for a pitched roof, or null for a flat one (modern and concrete looks, compact). */
	String[] pitchedRoof() {
		if (scale == StationScale.COMPACT) {
			return null;
		}
		return switch (spec.building()) {
			case ANTWERP, BEAUX_ARTS -> new String[]{"minecraft:oxidized_cut_copper_stairs", "minecraft:oxidized_cut_copper"};
			case AMSTERDAM, GOTHIC_BRICK, SANDSTONE, CLINKER, TOKYO, ZURICH, VICTORIAN -> new String[]{"minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles"};
			// Mumbai's red clay tiles
			case MUMBAI -> new String[]{"minecraft:brick_stairs", "minecraft:bricks"};
			case NONE, MODERN, CONCRETE, PARIS, MILAN, BERLIN, HONG_KONG, BEIJING, BANGKOK, KUALA_LUMPUR, ART_DECO, MODERNIST -> null;
		};
	}

	/**
	 * A grand station's clock tower over the entrance, standing on the street wall: a hollow square shaft five cells across
	 * (u0..u0+4, v0..v0+4) rising {@link #TOWER_HEIGHT} above the roof, louvred openings, a clock face on every side but the
	 * one facing {@code inward} (into the building), a cornice and a pyramid cap with a finial. Planned before the roof so the
	 * roof leaves the tower open.
	 */
	void clockTower(int top, int u0, int v0, LocalDir inward) {
		// a real station's tower stands as high as the real one (Hamburg's 45 m)
		tower(top, u0, v0, Math.max(TOWER_HEIGHT, spec.real().towerTopY() - top), inward, Cap.PYRAMID, true, false);
	}

	/** Copper domes, except white ones in Kuala Lumpur, stone ones in Mumbai and dark slate ones in Tokyo. */
	String domeBlock() {
		return switch (spec.building()) {
			case KUALA_LUMPUR -> "minecraft:calcite";
			case MUMBAI -> "minecraft:cut_sandstone";
			case TOKYO -> "minecraft:deepslate_tiles";
			default -> DOME;
		};
	}

	/** How a tower ends: a pyramid, a pyramid with a tall spire, a copper dome, or flat (a glass tower). */
	enum Cap {
		PYRAMID, SPIRE, DOME, FLAT
	}

	/**
	 * A tower five cells across standing on the roof at {@code top}, {@code height} high, like {@link #clockTower}: clock faces
	 * when {@code clock}, glass between the corners and bands when {@code glass} (a modern glass tower), and its {@code cap}.
	 */
	/** Storey height of a tall tower, between its string courses. */
	static final int TALL_TOWER_STOREY = 8;

	void tower(int top, int u0, int v0, int height, LocalDir inward, Cap cap, boolean clock, boolean glass) {
		final StylePalette.Building b = palette.building();
		final int u1 = u0 + 4;
		final int v1 = v0 + 4;
		final int vC = v0 + 2;
		final int crown = top + height;
		for (int u = u0 + 1; u <= u1 - 1; u++) {
			for (int v = v0 + 1; v <= v1 - 1; v++) {
				// the roof under the tower is lit so the closed shaft stays dark-free; the rest of the shaft stays empty
				if (u == u0 + 2 && v == vC) {
					put(Role.LIGHT_PANEL, u, top, v, LocalDir.PLUS_V, b.light());
				}
				for (int y = top + 1; y <= crown; y++) {
					keepClear(u, y, v);
				}
			}
		}
		// a tall tower (a real station's) is stone storeys with string courses and narrow windows, its belfry under the clock,
		// so it does not read as a chimney
		final boolean tall = height > TOWER_HEIGHT;
		for (int y = top + 1; y <= crown; y++) {
			final int storey = (y - top - 1) % TALL_TOWER_STOREY;
			final boolean band = y == top + 1 || y == crown || tall && storey == 0;
			final boolean louvre = tall ? y >= crown - 7 && y <= crown - 5 || storey >= 3 && storey <= 5 && y < crown - 9
					: y >= top + 3 && y <= top + 5;
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					final boolean ring = u == u0 || u == u1 || v == v0 || v == v1;
					if (!ring) {
						continue;
					}
					final boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
					final boolean middle = u == u0 + 2 || v == vC;
					final LocalDir front = outward(u, v, u0, u1, v0, v1);
					if (band || corner) {
						put(Role.WALL, u, y, v, front, b.trim());
					} else if (glass || louvre && middle) {
						put(Role.WINDOW, u, y, v, front, b.window());
					} else {
						put(Role.WALL, u, y, v, front, b.wall());
					}
				}
			}
		}
		// clock faces on the outer sides, one row under the cornice
		final int clockY = crown - 2;
		final String clockFace = ata("amsterdam_clock_face_panel");
		for (final LocalDir side : LocalDir.values()) {
			if (!clock || side == inward) {
				continue;
			}
			final int cu = side == LocalDir.MINUS_U ? u0 - 1 : side == LocalDir.PLUS_U ? u1 + 1 : u0 + 2;
			final int cv = side == LocalDir.MINUS_V ? v0 - 1 : side == LocalDir.PLUS_V ? v1 + 1 : vC;
			// on a head building as narrow as the tower a side face would hang outside the station's footprint
			if (cu >= 0 && cu < layout.width() && cv >= layout.vMin() && cv <= layout.vMax()) {
				put(Role.FIXTURE, cu, clockY, cv, side, clockFace);
			}
		}
		// the cap: a closed slab, then a pyramid of two rings and a point, then the finial
		final String[] pitched = pitchedRoof();
		final String stair = pitched != null ? pitched[0] : "minecraft:stone_brick_stairs";
		final String solid = pitched != null ? pitched[1] : b.trim();
		for (int u = u0 + 1; u <= u1 - 1; u++) {
			for (int v = v0 + 1; v <= v1 - 1; v++) {
				air.remove(new Cell(u, crown, v));
				put(Role.BUILDING_ROOF, u, crown, v, LocalDir.PLUS_V, b.roof());
			}
		}
		switch (cap) {
			case PYRAMID -> {
				hipped(u0, u1, v0, v1, crown + 1, stair, solid, 2);
				put(Role.BUILDING_ROOF, u0 + 2, crown + 4, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
			}
			case SPIRE -> {
				hipped(u0, u1, v0, v1, crown + 1, stair, solid, 2);
				for (int y = crown + 4; y <= crown + 6; y++) {
					put(Role.BUILDING_ROOF, u0 + 2, y, vC, LocalDir.PLUS_V, solid);
				}
				put(Role.BUILDING_ROOF, u0 + 2, crown + 7, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
			}
			case DOME -> {
				// a round copper dome: the ring without its corners, the middle three by three, then the top and a finial
				for (int u = u0; u <= u1; u++) {
					for (int v = v0; v <= v1; v++) {
						final boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
						if (!corner) {
							put(Role.BUILDING_ROOF, u, crown + 1, v, LocalDir.PLUS_V, domeBlock());
						}
						if (u > u0 && u < u1 && v > v0 && v < v1) {
							put(Role.BUILDING_ROOF, u, crown + 2, v, LocalDir.PLUS_V, domeBlock());
						}
					}
				}
				put(Role.BUILDING_ROOF, u0 + 2, crown + 3, vC, LocalDir.PLUS_V, domeBlock());
				put(Role.BUILDING_ROOF, u0 + 2, crown + 4, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
			}
			case FLAT -> {
				// a parapet round the flat top
				for (int u = u0; u <= u1; u++) {
					for (int v = v0; v <= v1; v++) {
						if (u == u0 || u == u1 || v == v0 || v == v1) {
							put(Role.BUILDING_ROOF, u, crown + 1, v, outward(u, v, u0, u1, v0, v1), b.cornice());
						}
					}
				}
			}
		}
	}

	/**
	 * The wings of a city or grand station: lower blocks along the platform on both sides of the building block, whole bays
	 * long, two storeys with the same cornice height as a compact building. Their ground floors are waiting rooms on the
	 * paid side, opening onto platform 1 through a doorway in every bay, with benches along the street wall; the upper
	 * storeys are closed. A grand station's wings end in pavilions a storey higher. The block's own end walls separate them.
	 */
	void wings() {
		if (wingBays == 0) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		final boolean pavilions = scale == StationScale.GRAND && wingBays >= 2;
		for (final int side : new int[]{-1, 1}) {
			final int inner = side < 0 ? site.vStart() : site.vEnd();
			final int end = inner + side * wingBays * StationGeometry.BAY;
			final int pavilionFrom = inner + side * (wingBays - 1) * StationGeometry.BAY;
			for (int j = 0; j < wingBays; j++) {
				platformDoorway(uP, inner + side * (StationGeometry.BAY * j + 3), b.window());
			}
			final int lo = Math.min(inner + side, end);
			final int hi = Math.max(inner + side, end);
			// the wing's own end wall and, before a pavilion, the cross wall above the wing roof
			final int pavilionRoof = StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY;
			for (int u = uS + 1; u <= uP - 1; u++) {
				for (int y = 1; y < (pavilions ? pavilionRoof : StationGeometry.BUILDING_ROOF_Y); y++) {
					final boolean band = y == StationGeometry.UPPER_FLOOR_Y || y == StationGeometry.BUILDING_ROOF_Y;
					final boolean window = !band && windowRow(y)
							&& u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0;
					final String id = y == 1 ? b.plinth() : band || (u - uS) % 3 == 0 ? b.trim() : b.wall();
					put(window ? Role.WINDOW : Role.WALL, u, y, end, side < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V, window ? b.window() : id);
				}
				if (pavilions) {
					for (int y = StationGeometry.BUILDING_ROOF_Y + 1; y < pavilionRoof; y++) {
						put(Role.WALL, u, y, pavilionFrom, side < 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V, b.wall());
					}
				}
			}
			for (int v = lo; v <= hi; v++) {
				final int along = Math.abs(v - inner);
				final boolean pilaster = along % StationGeometry.BAY == 0;
				final boolean inPavilion = pavilions && along >= Math.abs(pavilionFrom - inner);
				final int roofAt = inPavilion ? pavilionRoof : StationGeometry.BUILDING_ROOF_Y;
				final boolean windowBay = !pilaster && Math.abs(along % StationGeometry.BAY - 3) <= 1;
				for (int u = uS; u <= uP; u++) {
					final boolean edge = u == uS || u == uP || v == end;
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : b.floor());
				}
				for (final int u : new int[]{uS, uP}) {
					final LocalDir front = u == uS ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					for (int y = 1; y < roofAt; y++) {
						final boolean band = y == StationGeometry.UPPER_FLOOR_Y || y == StationGeometry.BUILDING_ROOF_Y;
						if (windowBay && windowRow(y) && !band) {
							put(Role.WINDOW, u, y, v, front, b.window());
						} else {
							put(Role.WALL, u, y, v, front, band || pilaster ? b.trim() : y == 1 ? b.plinth() : b.wall());
						}
					}
				}
				for (int u = uS + 1; u <= uP - 1; u++) {
					slabCell(u, StationGeometry.UPPER_FLOOR_Y, v);
					if (inPavilion) {
						slabCell(u, StationGeometry.BUILDING_ROOF_Y, v);
					}
				}
				if (!inPavilion) {
					for (int u = uS; u <= uP; u++) {
						put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, b.roof());
					}
					put(Role.BUILDING_ROOF, uS, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.MINUS_U, b.cornice());
					put(Role.BUILDING_ROOF, uP, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.PLUS_U, b.cornice());
				}
				// benches against the street wall, either side of each bay's middle
				if (!pilaster && Math.abs(along % StationGeometry.BAY - 3) == 1) {
					put(Role.BENCH, uS + 1, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, b.bench());
				}
			}
			if (pavilions) {
				roof(pavilionRoof, uS, uP, Math.min(pavilionFrom, end), Math.max(pavilionFrom, end));
			} else {
				for (int u = uS; u <= uP; u++) {
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y + 1, end, side < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.cornice());
				}
			}
		}
	}
}
