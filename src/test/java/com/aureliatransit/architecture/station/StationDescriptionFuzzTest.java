package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Word soup, numeric extremes, control characters and over-long input: the parser never throws and its spec always plans. */
class StationDescriptionFuzzTest {
	static final String[] WORDS = {"platform", "1:", "2:", "island", "side", "bay", "wide", "long", "tracks", "track", "terminus", "hub", "named", "footbridge",
			"no", "without", "with", "canopy", "open", ";", ",", "0", "-5", "99999", "2147483648", "1e9", "3.5", "½", "utrecht", "grand", "compact", "city",
			"station", "building", "hall", "roof", "shed", "arched", "stairs", "both", "ways", "escalators", "travelators", "\"", "'", ":", "named\"", "§c",
			"\u0000", "\n", "\t", "日本", "🚆", "platform 0:", "platform 11:", "platform 999:", "platform -1:", "length", "width", "x", "by", "m", "metres",
			"blocks", "storey", "storeys", "floors", "tower", "kopfbahnhof", "gravel", "andesite", "bed", "step-free", "enclosed", "furniture"};

	@Test
	void anyTextParsesAndPlans() {
		final List<String> problems = new ArrayList<>();
		final Random r = new Random(42);
		for (int i = 0; i < 3000; i++) {
			final StringBuilder d = new StringBuilder();
			final int n = r.nextInt(40);
			for (int k = 0; k < n; k++) {
				d.append(WORDS[r.nextInt(WORDS.length)]).append(r.nextInt(4) == 0 ? "" : " ");
			}
			check(d.toString(), problems, i % 10 == 0);
		}
		check("x".repeat(5000), problems, true);
		check("platform 1: island; ".repeat(200), problems, true);
		check("named " + "y".repeat(3000), problems, true);
		check("", problems, true);
		check("   ", problems, true);
		assertEquals(List.of(), problems.stream().distinct().limit(20).toList());
	}

	private static void check(String text, List<String> problems, boolean plan) {
		try {
			final ParseResult result = StationDescriptionParser.parse(text);
			if (plan) {
				StationPlanner.plan(result.spec());
			}
		} catch (Throwable e) {
			final StackTraceElement[] st = e.getStackTrace();
			problems.add(e + " at " + (st.length > 0 ? st[0] : "?") + " | " + text.replace("\n", "\\n").substring(0, Math.min(200, text.length())));
		}
	}
}
