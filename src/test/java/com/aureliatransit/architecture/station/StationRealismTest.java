package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The realism pass of 10-24: what a station looks like from above and from the street, not only whether it is valid. Roofs
 * are roofing (no lamps or facade brick laid open to the sky), a town's building is a building and not a wall along the
 * platform, Utrecht Centraal's highest wave is over its tracks, a city the words name keeps its own train shed, and a
 * real station's clock tower is a tower, not a chimney.
 */
class StationRealismTest {

	static StationPlan plan(String words) {
		return StationPlanner.plan(StationDescriptionParser.parse(words).spec());
	}

	/** The topmost planned block of every (u, v) column. */
	static Map<Long, PlannedBlock> tops(StationPlan plan) {
		final Map<Long, PlannedBlock> tops = new HashMap<>();
		for (final PlannedBlock b : plan.blocks()) {
			tops.merge(((long) b.u() << 32) ^ (b.v() & 0xffffffffL), b, (x, y) -> y.y() > x.y() ? y : x);
		}
		return tops;
	}

	@Test
	void noPresetHasLampsOpenToTheSky() {
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			final long lamps = tops(plan(preset.description())).values().stream().filter(b -> b.role() == Role.LIGHT_PANEL).count();
			// Grand Central's flat head roof had 678 of its ceiling lamps in its top layer
			assertEquals(0, lamps, preset.key());
		}
	}

	@Test
	void buildingRoofsAreRoofingNotFacade() {
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			final StationPlan plan = plan(preset.description());
			final String wall = StylePalette.Building.of(plan.spec().building()).wall();
			final Map<Long, PlannedBlock> tops = tops(plan);
			// a roof field of the wall's block: open to the sky with the same block level on all four sides (a parapet or a
			// cornice ring stands above the roof beside it, so it is not counted)
			final long facadeOnTop = tops.values().stream()
					.filter(b -> b.role() == Role.BUILDING_ROOF && b.blockId().equals(wall))
					.filter(b -> {
						for (final int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							final PlannedBlock n = tops.get(((long) (b.u() + d[0]) << 32) ^ ((b.v() + d[1]) & 0xffffffffL));
							if (n == null || n.y() != b.y() || !n.blockId().equals(wall)) {
								return false;
							}
						}
						return true;
					}).count();
			// a tower or attic cap may be the wall's stone; a whole roof was thousands of cells (Amsterdam's wings: 3,154 of
			// red brick)
			assertTrue(facadeOnTop <= 64, preset.key() + ": " + facadeOnTop + " roof cells of " + wall);
		}
	}

	@Test
	void flatRoofsUseTheLooksRoofingWhereItsRoofIsItsWall() {
		final StylePalette.Building amsterdam = StylePalette.Building.of(BuildingStyle.AMSTERDAM);
		assertEquals("minecraft:deepslate_tiles", PlanBuilding.flatRoof(amsterdam, BuildingStyle.AMSTERDAM));
		final StylePalette.Building deco = StylePalette.Building.of(BuildingStyle.ART_DECO);
		assertEquals(PlanBuilding.ROOF_MEMBRANE, PlanBuilding.flatRoof(deco, BuildingStyle.ART_DECO));
		for (final BuildingStyle style : BuildingStyle.values()) {
			final StylePalette.Building b = StylePalette.Building.of(style);
			final String roof = PlanBuilding.flatRoof(b, style);
			assertTrue(!roof.equals(b.wall()) && !roof.equals(b.trim()) && !roof.equals(b.plinth()), style + ": " + roof);
		}
	}

	@Test
	void aPitchedLookRoofsItsWingsToo() {
		final StationPlan plan = plan("eight track amsterdam station, four island platforms, 240 long, train shed, station building with footbridge, named Amsterdam Centraal");
		final long slate = tops(plan).values().stream().filter(b -> b.blockId().equals("minecraft:deepslate_tile_stairs")).count();
		final long brick = tops(plan).values().stream().filter(b -> b.blockId().endsWith("amsterdam_red_brick")).count();
		// before: 702 slate stairs on the middle block only, 3,154 cells of brick on the wings
		assertTrue(slate > 2000, "slate " + slate);
		assertTrue(brick < 64, "brick " + brick);
	}

	@Test
	void aCityBuildingIsABlockWithWingsNotAWall() {
		assertEquals(StationScale.CITY_SPAN, StationScale.CITY.wingSpan(250));
		assertEquals(70, StationScale.CITY.wingSpan(100));
		assertEquals(250 * 92 / 100, StationScale.GRAND.wingSpan(250));
		assertEquals(0, StationScale.COMPACT.wingSpan(250));
		final StationPlan plan = plan("four track surbiton station, two island platforms, 250 long, city station building with footbridge, named Surbiton");
		final int bw = plan.layout().buildingWidth();
		int v0 = Integer.MAX_VALUE;
		int v1 = Integer.MIN_VALUE;
		for (final PlannedBlock b : plan.blocks()) {
			if (b.u() < bw && b.y() > 2 && (b.role() == Role.WALL || b.role() == Role.WINDOW)) {
				v0 = Math.min(v0, b.v());
				v1 = Math.max(v1, b.v());
			}
		}
		// was 175 (117 m) on these 250-long platforms
		assertTrue(v1 - v0 + 1 <= StationScale.CITY_SPAN + 2, "Surbiton's building runs " + (v1 - v0 + 1));
	}

	@Test
	void utrechtsHighestWaveIsOverTheTracks() {
		final RealStation utrecht = RealStation.UTRECHT_CENTRAAL;
		final int eaves = 17;
		final int[] roof = utrecht.roofProfile(110, eaves, 11, 99);
		// the middle wave's feet at the outermost tracks, its crest over the middle track
		assertEquals(eaves, roof[11]);
		assertEquals(eaves, roof[99]);
		assertEquals(utrecht.roofTopY(), roof[55]);
		// the lower waves over the ends, beyond the tracks
		final int side = eaves + (int) Math.round(RealStation.SIDE_WAVE * (utrecht.roofTopY() - eaves));
		assertTrue(Math.abs(roof[5] - side) <= 1, "side crest " + roof[5]);
		assertTrue(Math.abs(roof[105] - side) <= 1, "side crest " + roof[105]);
		for (int i = 0; i <= 11; i++) {
			assertTrue(roof[i] < utrecht.roofTopY() && roof[110 - i] < utrecht.roofTopY(), "end column " + i);
		}
		// with no placement the middle wave keeps its share of the width
		final int[] even = utrecht.roofProfile(166, eaves);
		assertEquals(eaves, even[(int) Math.round(166 * (1 - RealStation.MIDDLE_WAVE) / 2)]);
	}

	@Test
	void aNamedCityKeepsItsOwnTrainShed() {
		for (int seed = 1; seed <= 30; seed++) {
			assertEquals(ShedShape.ARCH, StationDescriptionParser.parse(
					"eight track amsterdam station, four island platforms, 240 long, train shed, station building with footbridge, seed " + seed).spec().shed());
			assertEquals(ShedShape.MULTI_ARCH, StationDescriptionParser.parse(
					"ten track frankfurt terminus, five island platforms, 240 long, train shed, seed " + seed).spec().shed());
		}
		// with no city named, the seed still varies a barrel-vaulted shed
		final java.util.Set<ShedShape> sheds = new java.util.HashSet<>();
		for (int seed = 1; seed <= 30; seed++) {
			sheds.add(StationDescriptionParser.parse("four track s-bahn station, two island platforms, train shed, seed " + seed).spec().shed());
		}
		assertEquals(java.util.Set.of(ShedShape.ARCH, ShedShape.MULTI_ARCH), sheds);
	}

	/** The clock faces looking at the street (the lowest u among the clock faces): one per tower. */
	static java.util.List<PlannedBlock> clocks(StationPlan plan) {
		return plan.blocks().stream().filter(b -> b.blockId().endsWith("clock_face_panel")).toList();
	}

	static java.util.List<PlannedBlock> streetClocks(StationPlan plan) {
		final java.util.List<PlannedBlock> clocks = clocks(plan);
		final int street = clocks.stream().mapToInt(PlannedBlock::u).min().orElseThrow();
		return clocks.stream().filter(b -> b.u() == street).toList();
	}

	@Test
	void aTallTowerIsAnEighthOfItsHeightWide() {
		assertEquals(5, PlanBuilding.tallTowerSize(20));
		assertEquals(9, PlanBuilding.tallTowerSize(StationGeometry.metres(45)));
		for (int y = 1; y < 200; y++) {
			final int size = PlanBuilding.tallTowerSize(y);
			assertTrue(size % 2 == 1 && size >= 5 && size * 8 >= y, "height " + y + ": " + size);
		}
	}

	@Test
	void hamburgsClockTowerIsATowerNotAChimney() {
		final StationPlan hamburg = plan("hamburg hauptbahnhof");
		assertEquals(1, streetClocks(hamburg).size());
		// the side faces hang one cell outside the shaft: 5 cells (3.3 m) for 45 m read as a chimney
		final int street = streetClocks(hamburg).get(0).u();
		final java.util.IntSummaryStatistics side = clocks(hamburg).stream().filter(b -> b.u() > street).mapToInt(PlannedBlock::v).summaryStatistics();
		assertEquals(2, side.getCount());
		assertEquals(9, side.getMax() - side.getMin() - 1);
		// the pyramid's point at the real 45 m, the lightning rod on it
		final int top = hamburg.blocks().stream().filter(b -> !b.blockId().endsWith("lightning_rod")).mapToInt(PlannedBlock::y).max().orElseThrow();
		assertEquals(StationGeometry.metres(45), top);
	}

	@Test
	void theAmsterdamLookFlanksItsEntranceWithTwoTowers() {
		final StationPlan amsterdam = plan(StationPresets.ALL.stream().filter(p -> p.key().equals("amsterdam")).findFirst().orElseThrow().description());
		final java.util.List<PlannedBlock> towers = streetClocks(amsterdam);
		assertEquals(2, towers.size());
		// either side of the street entrance (its ramps on the apron), the same distance out, with the doors between them
		final int apron = amsterdam.blocks().stream().filter(b -> b.role() == Role.RAMP_LOWER).mapToInt(PlannedBlock::u).min().orElseThrow();
		final java.util.IntSummaryStatistics doors = amsterdam.blocks().stream().filter(b -> b.role() == Role.RAMP_LOWER && b.u() == apron)
				.mapToInt(PlannedBlock::v).summaryStatistics();
		final int low = Math.min(towers.get(0).v(), towers.get(1).v());
		final int high = Math.max(towers.get(0).v(), towers.get(1).v());
		assertEquals(doors.getMin() + doors.getMax(), low + high);
		assertTrue(low < doors.getMin() - 2 && high > doors.getMax() + 2, "towers " + low + ", " + high + ", doors " + doors);
	}

	static String preset(String key) {
		return StationPresets.ALL.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().description();
	}

	/** Highest building roof block over the side building's footprint (u below its width). */
	static long buildingRoof(StationPlan plan, String blockId) {
		final int bw = plan.layout().buildingWidth();
		return tops(plan).values().stream().filter(b -> b.u() < bw && b.role() == Role.BUILDING_ROOF && b.blockId().equals(blockId)).count();
	}

	@Test
	void aTownsBrickStationHouseHasAPitchedRoof() {
		// Teddington's was a flat field of slate at y 12 inside its cornice
		final StationPlan teddington = plan(preset("teddington"));
		assertEquals(StationScale.COMPACT, teddington.spec().scale());
		assertTrue(buildingRoof(teddington, "minecraft:deepslate_tile_stairs") > 100, "slate stairs");
		final int top = teddington.blocks().stream().filter(b -> b.u() < teddington.layout().buildingWidth() && b.role() == Role.BUILDING_ROOF)
				.mapToInt(PlannedBlock::y).max().orElseThrow();
		assertTrue(top > StationGeometry.BUILDING_ROOF_Y + 2, "ridge at " + top);
		// a modern look stays flat
		final StationPlan modern = plan("two track modern station, side platforms, 120 long, small station building with footbridge");
		assertEquals(0, buildingRoof(modern, "minecraft:deepslate_tile_stairs") + buildingRoof(modern, "minecraft:polished_diorite_stairs"));
	}

	@Test
	void tokyoEndsInDomedPavilionsWithNoTowerOverItsMiddle() {
		final StationPlan tokyo = plan(preset("tokyo"));
		final int bw = tokyo.layout().buildingWidth();
		// the domes' finials: one over each end pavilion, none over the middle (a clock tower there was the Amsterdam look again)
		final java.util.List<PlannedBlock> finials = tokyo.blocks().stream()
				.filter(b -> b.u() < bw && b.blockId().equals("minecraft:lightning_rod")).toList();
		assertEquals(2, finials.size(), finials.toString());
		final java.util.IntSummaryStatistics walls = tokyo.blocks().stream().filter(b -> b.u() < bw && b.role() == Role.WALL)
				.mapToInt(PlannedBlock::v).summaryStatistics();
		for (final PlannedBlock f : finials) {
			assertTrue(Math.min(f.v() - walls.getMin(), walls.getMax() - f.v()) <= 2 * StationGeometry.BAY, "finial at v " + f.v());
			// higher than the wings' ridges
			assertTrue(f.y() > StationGeometry.BUILDING_ROOF_Y + 12, "finial at y " + f.y());
		}
		assertTrue(clocks(tokyo).isEmpty());
		assertEquals(PlanHead.OctCap.DOME, PlanBuilding.pavilionCap(BuildingStyle.TOKYO));
		assertEquals(null, PlanBuilding.pavilionCap(BuildingStyle.AMSTERDAM));
	}

	@Test
	void aNamedCityKeepsItsOwnCanopies() {
		final CanopyShape own = StationDescriptionParser.parse(preset("teddington")).spec().canopy();
		for (int seed = 1; seed <= 30; seed++) {
			assertEquals(own, StationDescriptionParser.parse(preset("teddington") + ", seed " + seed).spec().canopy(), "seed " + seed);
			// a rolled historic era asked for gables over the city's own (Surbiton's art deco look has flat ones)
			assertEquals(CanopyShape.FLAT, StationDescriptionParser.parse("surbiton station, regional class, historic era, seed " + seed).spec().canopy(),
					"historic, seed " + seed);
		}
		// with no city named, the seed still varies the canopy, and a historic station still gets gables
		final java.util.Set<CanopyShape> shapes = new java.util.HashSet<>();
		for (int seed = 1; seed <= 30; seed++) {
			shapes.add(StationDescriptionParser.parse("two track station, island platform, canopy, seed " + seed).spec().canopy());
		}
		assertTrue(shapes.size() > 1, shapes.toString());
		assertEquals(CanopyShape.GABLE, StationDescriptionParser.parse("two track station, regional class, historic era").spec().canopy());
	}

	/** v of every canopy roof cell over the platforms (the canopy level and the rows a gable lifts above it). */
	static java.util.IntSummaryStatistics canopyRows(StationPlan plan) {
		final int bw = plan.layout().buildingWidth();
		return plan.blocks().stream().filter(b -> b.u() >= bw && (b.role() == Role.ROOF || b.role() == Role.ROOF_EDGE || b.role() == Role.ROOF_LIGHT)
				&& b.y() >= StationGeometry.CANOPY_Y && b.y() <= StationGeometry.CANOPY_Y + 3).mapToInt(PlannedBlock::v).summaryStatistics();
	}

	@Test
	void aTownStationsCanopyCoversPartOfItsPlatforms() {
		// Teddington's gable canopies ran all 200 blocks of its platforms
		final StationPlan teddington = plan(preset("teddington"));
		final int length = teddington.layout().platformLength();
		final java.util.IntSummaryStatistics roof = canopyRows(teddington);
		final int covered = roof.getMax() - roof.getMin() + 1;
		assertTrue(covered >= length * 2 / 5 && covered <= length * 3 / 5, "canopy " + roof.getMin() + ".." + roof.getMax() + " of " + length);
		// over the building's frontage and the footbridge stairs
		final java.util.IntSummaryStatistics walls = teddington.blocks().stream().filter(b -> b.u() < teddington.layout().buildingWidth() && b.role() == Role.WALL)
				.mapToInt(PlannedBlock::v).summaryStatistics();
		assertTrue(roof.getMin() <= walls.getMin() && roof.getMax() >= walls.getMax(), "building " + walls.getMin() + ".." + walls.getMax());
		// the main footbridge's flights beside the building (the far footbridge's stand in the open under hoods of their own)
		final java.util.IntSummaryStatistics stairs = teddington.blocks().stream().filter(b -> b.role() == Role.STAIR && b.u() >= teddington.layout().buildingWidth()
				&& b.v() > walls.getMin() - 30 && b.v() < walls.getMax() + 30).mapToInt(PlannedBlock::v).summaryStatistics();
		assertTrue(stairs.getCount() > 0 && roof.getMin() <= stairs.getMin() && roof.getMax() >= stairs.getMax(), "stairs " + stairs.getMin() + ".." + stairs.getMax());
		// columns only under it, one near each end (nothing hangs past a cantilever), and lamps on poles past it
		final java.util.IntSummaryStatistics columns = teddington.blocks().stream().filter(b -> b.role() == Role.COLUMN_CAP && b.y() == StationGeometry.CANOPY_Y - 2)
				.mapToInt(PlannedBlock::v).summaryStatistics();
		assertTrue(columns.getMin() >= roof.getMin() && columns.getMax() <= roof.getMax(), "columns " + columns.getMin() + ".." + columns.getMax());
		assertTrue(columns.getMin() - roof.getMin() <= StationGeometry.MAX_CANTILEVER && roof.getMax() - columns.getMax() <= StationGeometry.MAX_CANTILEVER,
				"roof " + roof.getMin() + ".." + roof.getMax() + ", columns " + columns.getMin() + ".." + columns.getMax());
		assertTrue(teddington.blocks().stream().anyMatch(b -> b.role() == Role.LAMP && (b.v() < roof.getMin() || b.v() > roof.getMax())), "no lamps in the open");
		// a city building's platforms stay covered end to end
		final StationPlan surbiton = plan(preset("surbiton"));
		final java.util.IntSummaryStatistics full = canopyRows(surbiton);
		assertTrue(full.getMax() - full.getMin() + 1 >= surbiton.layout().platformLength() - 2, full.toString());
	}

	@Test
	void hamburgsShedHasAnEntranceBlockBesideItNotAWingAlongIt() {
		// the side building's wings ran 207 of the 225 rows, longer than the shed's entrance halls (the real one has none)
		final StationPlan hamburg = plan("hamburg hauptbahnhof");
		final int bw = hamburg.layout().buildingWidth();
		// (the building's track-side line, bw - 1, is the shed's wall past it: see aShedIsClosedAlongItsBuildingSide)
		final java.util.IntSummaryStatistics walls = hamburg.blocks().stream().filter(b -> b.u() < bw - 1 && b.role() == Role.WALL)
				.mapToInt(PlannedBlock::v).summaryStatistics();
		assertTrue(walls.getMax() - walls.getMin() + 1 <= hamburg.layout().platformLength() / 3, "building " + walls.getMin() + ".." + walls.getMax());
		// its clock tower still stands over the entrance
		assertEquals(1, streetClocks(hamburg).size());
		// Amsterdam's long front is a look of its own: not a real train shed, its wings stay
		final StationPlan amsterdam = plan(preset("amsterdam"));
		final java.util.IntSummaryStatistics front = amsterdam.blocks().stream().filter(b -> b.u() < amsterdam.layout().buildingWidth() - 1 && b.role() == Role.WALL)
				.mapToInt(PlannedBlock::v).summaryStatistics();
		assertTrue(front.getMax() - front.getMin() + 1 > amsterdam.layout().platformLength() / 3, front.toString());
	}

	/** v of every row whose cell (u, 3, v) is a wall or a window. */
	static java.util.Set<Integer> wallRows(StationPlan plan, int u) {
		return plan.blocks().stream().filter(b -> b.u() == u && b.y() == 3 && (b.role() == Role.WALL || b.role() == Role.WINDOW))
				.map(PlannedBlock::v).collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void aShedIsClosedAlongItsBuildingSide() {
		// Hamburg's entrance block and Amsterdam's front stop short of their sheds; past them the street looked in under the roof
		// at a row of platform columns (seen in game 10-24)
		for (final String words : new String[]{"hamburg hauptbahnhof", preset("amsterdam")}) {
			final StationPlan plan = plan(words);
			final TrackLayout layout = plan.layout();
			final int line = layout.buildingWidth() - 1;
			final java.util.Set<Integer> closed = wallRows(plan, line);
			final java.util.List<Integer> open = java.util.stream.IntStream.range(0, layout.platformLength()).filter(v -> !closed.contains(v)).boxed().toList();
			assertEquals(java.util.List.of(), open, words + ": rows open along u " + line);
			// the wall stands clear of every track, and the far side over the outer track stays open
			for (final TrackLayout.Track track : layout.tracks()) {
				assertTrue(Math.abs(track.u() - line) > StationGeometry.ENVELOPE_HALF_WIDTH, words + ": track " + track.u());
			}
			assertTrue(wallRows(plan, layout.width() - 1).isEmpty(), words + ": far side closed");
			// the platform columns and the shed over them are still there
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.COLUMN && b.u() > line), words + ": no columns");
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.RIB && b.u() > line), words + ": no shed");
		}
	}

	@Test
	void stationsWithoutAShedBesideTheirBuildingStayOpen() {
		// Teddington's canopies and St Pancras's free shed (no side building) keep their open sides
		final StationPlan teddington = plan(preset("teddington"));
		final int line = teddington.layout().buildingWidth() - 1;
		assertTrue(wallRows(teddington, line).size() <= teddington.layout().platformLength() / 3, "teddington " + wallRows(teddington, line).size());
		final StationPlan pancras = plan(preset("st_pancras"));
		assertEquals(0, pancras.layout().buildingWidth());
		assertTrue(wallRows(pancras, 0).stream().allMatch(v -> v >= pancras.layout().platformLength()), "st pancras " + wallRows(pancras, 0));
	}
}
