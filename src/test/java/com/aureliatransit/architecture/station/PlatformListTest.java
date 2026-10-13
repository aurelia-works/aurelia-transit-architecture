package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stations whose platforms are listed one by one, each with its own kind, width, length and roof. */
class PlatformListTest {

	private static ParseResult parse(String text) {
		return StationDescriptionParser.parse(text);
	}

	@Test
	void theListFixesTracksAndEachPlatformsWidth() {
		final StationSpec spec = parse("platform 1: side 12 wide; platform 2: island 8 wide; platform 3: island; platform 4: side 5 wide").spec();
		assertEquals(4, spec.platforms().size());
		assertEquals(6, spec.trackCount());
		final TrackLayout layout = StationGeometry.layout(spec);
		assertEquals(4, layout.platforms().size());
		assertEquals(List.of(12, 8, spec.platformWidth(), 5), layout.platforms().stream().map(TrackLayout.PlatformBand::width).toList());
		assertTrue(layout.platforms().get(1).island());
		assertFalse(layout.platforms().get(3).island());
	}

	@Test
	void wordsReadBackToTheSamePlatform() {
		final List<PlatformSpec> platforms = List.of(new PlatformSpec(PlatformSpec.Kind.ISLAND, 14, 120, PlatformSpec.Roof.OPEN, true),
				new PlatformSpec(PlatformSpec.Kind.SIDE, 0, 0, PlatformSpec.Roof.CANOPY), PlatformSpec.of(PlatformSpec.Kind.SIDE));
		final StringBuilder text = new StringBuilder("200 long");
		for (int i = 0; i < platforms.size(); i++) {
			text.append("; platform ").append(i + 1).append(": ").append(platforms.get(i).words());
		}
		final ParseResult result = parse(text.toString());
		assertEquals(platforms, result.spec().platforms());
		assertEquals(List.of(), result.warnings());
	}

	@Test
	void outOfRangeValuesAreClampedWithAWarning() {
		final ParseResult result = parse("platform 1: island 40 wide");
		assertEquals(StationSpec.MAX_WIDTH, result.spec().platforms().get(0).width());
		assertFalse(result.warnings().isEmpty());
	}

	@Test
	void aHubOfMixedPlatformsBecomesATerminus() {
		final ParseResult result = parse("hub; platform 1: side; platform 2: island; platform 3: side");
		assertFalse(result.spec().hub());
		assertTrue(result.spec().terminus());
		assertFalse(result.warnings().isEmpty());
	}

	@Test
	void eachPlatformGetsItsOwnRoof() {
		final StationPlan plan = StationPlanner.plan(parse("no roof; platform 1: side canopy; platform 2: island; platform 3: side open").spec());
		final TrackLayout layout = plan.layout();
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			final boolean roofed = plan.blocks().stream().anyMatch(b -> b.role() == Role.ROOF && b.u() >= band.uMin() && b.u() <= band.uMax());
			final boolean lamps = plan.blocks().stream().anyMatch(b -> b.role() == Role.LAMP && b.u() >= band.uMin() && b.u() <= band.uMax());
			assertEquals(band.number() == 1, roofed, "platform " + band.number() + " canopy");
			assertEquals(band.number() != 1, lamps, "platform " + band.number() + " lamps");
		}
		assertTrue(StationAudit.audit(plan).buildable());
	}

	@Test
	void eachPlatformRunsItsOwnLength() {
		final StationSpec through = parse("120 long; platform 1: side 40 long; platform 2: island; platform 3: side 200 long").spec();
		assertEquals(200, through.platformLength(), "the station grows to its longest platform");
		final TrackLayout layout = StationGeometry.layout(through);
		final TrackLayout.PlatformBand first = layout.platforms().get(0);
		assertEquals(41, first.length(), "40 between its rail nodes");
		assertEquals(201, layout.platforms().get(1).length());
		final int line = StationLayout.entranceV(false, 200);
		assertTrue(first.runs(line - StationGeometry.BRIDGE_REACH) && first.runs(line + StationGeometry.BRIDGE_REACH), "reaches the footbridge");
		final StationPlan plan = StationPlanner.plan(through);
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable(), () -> StationAudit.audit(plan).problems().toString());
		assertTrue(plan.blocks().stream().noneMatch(b -> b.role() == Role.PLATFORM_EDGE && b.u() >= first.uMin() && b.u() <= first.uMax()
				&& !first.runs(b.v())), "no edge past the short platform");

		final TrackLayout terminus = StationGeometry.layout(parse("terminus 100 long; platform 1: island 30 long; platform 2: island").spec());
		assertEquals(100, terminus.platforms().get(0).vTo(), "a terminus platform ends at the head");
		assertEquals(70, terminus.platforms().get(0).vFrom());
	}

	@Test
	void aBayTrackEndsAtABufferStopPastItsPlatform() {
		final StationPlan plan = StationPlanner.plan(parse("80 long; platform 1: side; platform 2: island bay 40 long; platform 3: side").spec());
		final TrackLayout layout = plan.layout();
		final TrackLayout.PlatformBand bay = layout.platforms().get(1);
		assertTrue(bay.deadEnd());
		for (final TrackLayout.Track track : layout.tracks()) {
			final boolean dead = layout.platformOf(track) == bay;
			assertEquals(dead, layout.deadEnd(track), "track " + track.number());
			assertEquals(dead ? bay.vTo() : layout.vMax(), layout.trackEnd(track), "track " + track.number());
			assertEquals(dead, plan.blocks().stream().anyMatch(b -> b.role() == Role.BUFFER_STOP && b.u() == track.u() && b.v() == bay.vTo() + 1),
					"buffer stop on track " + track.number());
		}
		assertEquals(List.of(), LayoutValidator.validate(plan));
		assertTrue(StationAudit.audit(plan).buildable());
		// a full-length bay stops a row short, so its buffer stops stay inside the station
		final TrackLayout full = StationGeometry.layout(parse("no ramps 60 long; platform 1: side bay; platform 2: side").spec());
		assertEquals(59, full.platforms().get(0).vTo());
		// in a terminus every track already ends at the head
		assertFalse(StationGeometry.layout(parse("terminus; platform 1: island bay; platform 2: island").spec()).platforms().get(0).deadEnd());
	}

	@Test
	void mixedListsPlanCleanlyInEveryStationType() {
		final Random random = new Random(1600);
		final String[] kinds = {"", "with a station building", "with a footbridge", "terminus", "hub"};
		final String[] roofs = {"canopies", "train shed", "no roof"};
		final List<String> problems = new ArrayList<>();
		for (int round = 0; round < 400; round++) {
			final String kind = kinds[round % kinds.length];
			final String roof = roofs[(round / kinds.length) % roofs.length];
			final int count = 1 + random.nextInt(kind.equals("hub") ? 5 : 6);
			final StringBuilder text = new StringBuilder(kind + " " + roof + " " + (40 + random.nextInt(80)) + " long");
			for (int i = 1; i <= count; i++) {
				final boolean island = kind.equals("hub") || random.nextBoolean();
				text.append("; platform ").append(i).append(": ").append(island ? "island" : "side");
				if (random.nextInt(4) == 0) {
					text.append(" bay");
				}
				if (random.nextBoolean()) {
					text.append(' ').append(StationSpec.MIN_WIDTH + random.nextInt(StationSpec.MAX_WIDTH - StationSpec.MIN_WIDTH + 1)).append(" wide");
				}
				if (random.nextInt(3) == 0) {
					text.append(' ').append(StationSpec.MIN_LENGTH + random.nextInt(100)).append(" long");
				}
				text.append(new String[]{"", "", " canopy", " open"}[random.nextInt(4)]);
			}
			final String description = text.toString();
			final StationPlan plan = StationPlanner.plan(parse(description).spec());
			final List<Violation> violations = LayoutValidator.validate(plan);
			if (!violations.isEmpty()) {
				problems.add(description + " -> " + violations.subList(0, Math.min(3, violations.size())));
			} else if (!StationAudit.audit(plan).buildable()) {
				problems.add(description + " -> audit: " + StationAudit.audit(plan).problems());
			}
		}
		assertEquals(List.of(), problems);
	}
}
