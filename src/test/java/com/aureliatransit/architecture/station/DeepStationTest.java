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

	/** The three deep presets (Angel, Canary Wharf, Vijzelgracht): their wrench text, by key. */
	private static String presetText(String key) {
		return StationPresets.ALL.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().description();
	}

	@Test
	void theDeepPresetsPlanCleanWithNoFalls() {
		for (final String key : List.of("angel", "canary_wharf", "vijzelgracht")) {
			final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(presetText(key)).spec());
			assertEquals(List.of(), LayoutValidator.validate(plan), key);
			final StationAudit.Result report = StationAudit.audit(plan);
			assertTrue(report.checks().stream().noneMatch(c -> c.topic() == StationAudit.Topic.FALLS && c.status() != StationAudit.Status.PASS),
					key + " " + report.checks());
		}
	}

	@Test
	void theDeepPresetsKeepTheirRealDepthTypeAndOperator() {
		// Angel 27 m is 40 blocks (the research's figure); Canary Wharf 24 m is 36 and Vijzelgracht 26 m is 39, at 1.5 blocks a metre
		final StationSpec angel = StationDescriptionParser.parse(presetText("angel")).spec();
		assertEquals(UndergroundType.DEEP_TUBE, angel.options().type());
		assertEquals(40, angel.options().streetDepth());
		assertEquals(Operator.UNDERGROUND, angel.operator());
		// Angel is escalator-only: a tube without the step-free words gets no lift
		assertFalse(angel.stepFree());
		final StationSpec canary = StationDescriptionParser.parse(presetText("canary_wharf")).spec();
		assertEquals(UndergroundType.CUT_AND_COVER, canary.options().type());
		assertEquals(36, canary.options().streetDepth());
		assertEquals(Operator.UNDERGROUND, canary.operator());
		final StationSpec vijzelgracht = StationDescriptionParser.parse(presetText("vijzelgracht")).spec();
		assertEquals(UndergroundType.CUT_AND_COVER, vijzelgracht.options().type());
		assertEquals(39, vijzelgracht.options().streetDepth());
		assertEquals(Operator.GVB, vijzelgracht.operator());
	}

	/** The 188-long island box (the preset's real length): its lift landing's lamp used to float, so it must plan clean and grounded. */
	@Test
	void vijzelgrachtAt188IsCleanAndGrounded() {
		final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(presetText("vijzelgracht")).spec());
		assertEquals(List.of(), LayoutValidator.validate(plan));
		final StationAudit.Result report = StationAudit.audit(plan);
		assertEquals(StationAudit.Status.PASS, report.checks(StationAudit.Topic.GROUNDED).get(0).status(), report.checks().toString());
		assertTrue(report.checks().stream().noneMatch(c -> c.topic() == StationAudit.Topic.FALLS && c.status() != StationAudit.Status.PASS),
				report.checks().toString());
	}

	@Test
	void westminsterStacksItsTwoPlatforms() {
		final StationSpec spec = StationDescriptionParser.parse(presetText("westminster")).spec();
		assertEquals(UndergroundType.DEEP_STACKED, spec.options().type());
		final StationPlan plan = StationPlanner.plan(spec);
		assertEquals(List.of(0, StationGeometry.STACK_RISE), plan.layout().levels());
		assertEquals(List.of(), LayoutValidator.validate(plan));
		// rail nodes at both levels
		for (final TrackLayout.Track track : plan.layout().tracks()) {
			assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.RAIL_NODE && b.u() == track.u() && b.y() == track.level()),
					"rail node on track " + track.number());
		}
		final StationAudit.Result report = StationAudit.audit(plan);
		for (final StationAudit.Check check : report.checks()) {
			if (check.topic() == StationAudit.Topic.FALLS || check.topic() == StationAudit.Topic.ACCESS) {
				assertEquals(StationAudit.Status.PASS, check.status(), check.topic() + ": " + check);
			}
		}
	}
}
