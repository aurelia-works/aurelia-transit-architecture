package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.7.0 phase 2: the deep underground types (a bored tube, a cut-and-cover box) and the Carve volume they dig. */
class DeepStationTest {

	private static final String TUBE = "2 track deep tube station, named Angel";
	private static final String BOX = "2 track cut and cover box station, named Canary Wharf";

	@Test
	void deepTubeWordsAskForABoredTube() {
		for (final String text : List.of("2 track deep tube station", "2 track bored tube station", "2 track deep level station")) {
			final StationOptions options = StationDescriptionParser.parse(text).spec().options();
			assertTrue(options.underground(), text);
			assertEquals(UndergroundType.DEEP_TUBE, options.type(), text);
			assertTrue(options.deepStation(), text);
			assertEquals(UndergroundType.DEEP_TUBE.defaultDepth(), options.streetDepth(), text);
		}
	}

	@Test
	void cutAndCoverWordsAskForABox() {
		for (final String text : List.of("2 track cut and cover box station", "2 track box station")) {
			final StationOptions options = StationDescriptionParser.parse(text).spec().options();
			assertTrue(options.underground(), text);
			assertEquals(UndergroundType.CUT_AND_COVER, options.type(), text);
			assertTrue(options.deepStation(), text);
			assertEquals(UndergroundType.CUT_AND_COVER.defaultDepth(), options.streetDepth(), text);
		}
	}

	@Test
	void aDepthInBlocksSetsTheStreetDepth() {
		final StationOptions blocks = StationDescriptionParser.parse("2 track deep tube station, 40 blocks deep").spec().options();
		assertEquals(UndergroundType.DEEP_TUBE, blocks.type());
		assertEquals(40, blocks.streetDepth());
		// Metres convert at StationGeometry.BLOCKS_PER_METRE (1.5): 40 m is 60 blocks.
		final StationOptions metres = StationDescriptionParser.parse("2 track deep tube station, 40 m deep").spec().options();
		assertEquals(60, metres.streetDepth());
	}

	@Test
	void shallowIsNotDeep() {
		final StationOptions options = StationDescriptionParser.parse("2 track underground station, 80 long").spec().options();
		assertEquals(UndergroundType.SHALLOW, options.type());
		assertFalse(options.deepStation());
	}

	@Test
	void aDeepTubePlanIsClean() {
		final StationSpec spec = StationDescriptionParser.parse(TUBE).spec();
		assertEquals(List.of(), LayoutValidator.validate(StationPlanner.plan(spec)));
	}

	@Test
	void aCutAndCoverBoxPlanIsClean() {
		final StationSpec spec = StationDescriptionParser.parse(BOX).spec();
		assertEquals(List.of(), LayoutValidator.validate(StationPlanner.plan(spec)));
	}

	@Test
	void carveAddsRemovesAndIgnoresCellsOutsideItsBounds() {
		final Carve carve = new Carve(new Box(0, -2, 0, 9, 5, 9));
		assertEquals(0, carve.count());
		carve.box(2, 0, 2, 4, 2, 4);
		assertEquals(27, carve.count());
		assertTrue(carve.has(3, 1, 3));
		assertFalse(carve.has(0, 1, 0));
		carve.remove(3, 1, 3);
		assertEquals(26, carve.count());
		assertFalse(carve.has(3, 1, 3));
		carve.add(100, 0, 0);
		assertEquals(26, carve.count());
		carve.removeBox(2, 0, 2, 4, 2, 4);
		assertEquals(0, carve.count());
	}

	@Test
	void carveLiningIsTheCellsOutsideTheVoidThatTouchIt() {
		final Carve carve = new Carve(new Box(0, -2, 0, 9, 5, 9));
		carve.add(5, 0, 5);
		final Set<String> lining = carve.lining().stream()
				.map(l -> l.u() + "," + l.y() + "," + l.v()).collect(Collectors.toSet());
		assertEquals(Set.of("4,0,5", "6,0,5", "5,-1,5", "5,1,5", "5,0,4", "5,0,6"), lining);
		for (final Carve.Lining cell : carve.lining()) {
			assertFalse(carve.has(cell.u(), cell.y(), cell.v()));
		}
	}
}
