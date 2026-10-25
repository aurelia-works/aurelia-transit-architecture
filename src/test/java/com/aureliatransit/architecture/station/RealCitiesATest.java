package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The city words of Amsterdam, St Pancras and Frankfurt build their real station's front, shed and size, not only a look. */
class RealCitiesATest {

	static StationSpec spec(String words) {
		return StationDescriptionParser.parse(words).spec();
	}

	@Test
	void frankfurtTerminusTakesTheSheetOfFrankfurtHbf() {
		final StationSpec s = spec("8 track real frankfurt terminus, 160 long");
		assertEquals("", s.name());
		assertSame(Landmarks.FRANKFURT, Landmarks.of(s));
		assertEquals(3, Landmarks.FRANKFURT.shed().spans(), "a row of three arched halls");
	}

	@Test
	void stPancrasTerminusTakesTheSheetOfStPancras() {
		final StationSpec s = spec("8 track real st pancras terminus, 160 long");
		assertSame(Landmarks.ST_PANCRAS, Landmarks.of(s));
		assertEquals(Landmark.ShedProfile.POINTED, Landmarks.ST_PANCRAS.shed().profile());
		assertEquals(1, Landmarks.ST_PANCRAS.shed().spans(), "one pointed span over all the tracks");
	}

	@Test
	void aBareCityWordIsOnlyTheLook() {
		for (final String words : List.of("8 track frankfurt terminus, 160 long", "8 track st pancras terminus, 160 long", "8 track paris terminus, 160 long",
				"8 track new york terminus, 160 long", "4 track berlin station building, 120 long")) {
			assertNull(Landmarks.of(spec(words)), words);
		}
	}

	@Test
	void styleOnlyOrSmallOrThroughStaysTheGenericLook() {
		assertNull(Landmarks.of(spec("8 track real frankfurt style terminus, 160 long")));
		assertNull(Landmarks.of(spec("2 track real frankfurt terminus, 64 long")));
		assertNull(Landmarks.of(spec("8 track real frankfurt station, 160 long, station building")));
		assertNull(Landmarks.of(spec("8 track real frankfurt hub, 160 long")));
		assertNull(Landmarks.of(spec("8 track real frankfurt and hamburg combined terminus, 160 long")));
	}

	@Test
	void aNameOfItsOwnWinsOverTheCityWord() {
		assertSame(Landmarks.ST_PANCRAS, Landmarks.of(spec("8 track real frankfurt terminus, 160 long, named St Pancras")));
	}

	@Test
	void amsterdamIsAGrandBuildingUnlessToldOtherwise() {
		assertEquals(StationScale.GRAND, spec("amsterdam station building").scale());
		assertEquals(StationScale.COMPACT, spec("small amsterdam station building").scale());
		assertEquals(StationScale.CITY, spec("amsterdam station, city building").scale());
	}

	@Test
	void parisMilanNewYorkAndMunichTerminiTakeTheirSheets() {
		assertSame(Landmarks.PARIS, Landmarks.of(spec("8 track real paris terminus, 160 long")));
		assertSame(Landmarks.MILAN, Landmarks.of(spec("10 track real milan terminus, 160 long")));
		assertSame(Landmarks.GRAND_CENTRAL, Landmarks.of(spec("8 track real new york terminus, 160 long")));
		assertSame(Landmarks.MUNICH, Landmarks.of(spec("8 track real munich terminus, 160 long")));
		assertNull(Landmarks.of(spec("2 track real paris terminus, 64 long")));
		assertNull(Landmarks.of(spec("8 track real munich station, 160 long, station building")), "a through Munich stays Munchen Ost's look");
	}

	@Test
	void berlinAndCoventryThroughStationsTakeTheirBuildings() {
		assertSame(Landmarks.BERLIN, Landmarks.of(spec("4 track real berlin station building, 120 long")));
		assertSame(Landmarks.COVENTRY, Landmarks.of(spec("3 track real coventry station building, 100 long")));
		assertSame(Landmarks.BERLIN, Landmarks.of(spec("4 track real berlin station building, 120 long, named Berlin Hbf")));
	}

	@Test
	void everySheetedCityPlansCleanly() {
		for (final String words : List.of("8 track frankfurt terminus, 160 long", "8 track real st pancras terminus, 160 long", "8 track paris terminus, 160 long",
				"10 track milan terminus, 160 long", "8 track new york terminus, 160 long", "8 track munich terminus, 160 long",
				"4 track berlin station building, 120 long", "3 track coventry station building, 100 long", "2 track amsterdam station building, 64 long")) {
			final StationPlan plan = StationPlanner.plan(spec(words));
			assertEquals(List.of(), LayoutValidator.validate(plan), words);
			assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
		}
	}

	/**
	 * A spread of tracks (2-10, odd ones too), lengths (48-300), sizes, platform layouts, terminus and through for every city whose
	 * words bring a sheet: whatever the sheet is chosen or not, the plan keeps the design rules and passes the audit.
	 */
	@Test
	void theSpreadOfEveryCityPlansAndAuditsClean() {
		final String[] cities = {"real frankfurt", "real st pancras", "real paris", "real milan", "real new york", "real munich", "real berlin",
				"real coventry", "amsterdam"};
		final int[] lengths = {48, 64, 96, 160, 240, 300};
		final String[] sizes = {"", "small", "big", "grand building", "compact building", "city building"};
		final String[] layouts = {"", "side platforms", "island platforms"};
		final List<String> failures = new java.util.ArrayList<>();
		int index = 0;
		int sheets = 0;
		for (final String city : cities) {
			for (int i = 0; i < 90; i++) {
				index++;
				final int tracks = 2 + i % 9;
				final String mode = i % 3 == 0 ? "station building" : i % 3 == 1 ? "terminus" : "station building with footbridge";
				final String words = tracks + " track " + city + " " + mode + ", " + lengths[(i / 3 + index) % lengths.length] + " long, "
						+ sizes[(i / 2) % sizes.length] + ", " + layouts[(i / 5) % layouts.length];
				final ParseResult parsed = StationDescriptionParser.parse(words);
				final StationSpec spec = parsed.spec();
				if (Landmarks.of(spec) != null && !city.equals("amsterdam")) {
					sheets++;
				}
				final StationPlan plan = StationPlanner.plan(spec);
				final List<Violation> violations = LayoutValidator.validate(plan);
				final StationAudit.Result audit = StationAudit.audit(plan);
				if (!violations.isEmpty()) {
					failures.add(words + ": " + violations.get(0));
				} else if (!audit.buildable()) {
					failures.add(words + ": " + audit.problems());
				}
			}
		}
		assertEquals(List.of(), failures.subList(0, Math.min(8, failures.size())), failures.size() + " of " + index);
		assertTrue(sheets > 40, "the spread reaches the sheets: " + sheets);
	}
}
