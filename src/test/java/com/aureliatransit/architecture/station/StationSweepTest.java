package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plans random descriptions across every look, mode, size, roof, building and option, and random platform lists: none may
 * throw, break a layout rule or fail an audit check. Seeded, so a failure reproduces.
 */
class StationSweepTest {
	static final String[] LOOKS = {"utrecht", "amsterdam", "st pancras", "antwerp", "frankfurt", "hamburg", "munich", "grand central", "tokyo", "zurich",
			"paris", "milan", "berlin", "hong kong", "beijing", ""};
	static final String[] MODES = {"", "terminus", "hub"};
	static final String[] LAYOUTS = {"side", "island", ""};
	static final String[] ROOFS = {"", "canopies", "hall roof", "open", "flat canopy", "butterfly canopy", "gable canopy", "arched shed", "pointed shed", "train shed"};
	static final String[] BUILDINGS = {"", "station building", "compact", "city", "grand", "no building"};
	static final String[] OPTIONS = {"footbridge", "escalators", "stairs both ways", "enclosed", "travelators", "step-free", "gravel bed", "andesite bed",
			"no furniture", "terrazzo floors"};

	@Test
	void randomStationsPlanCleanly() {
		final int n = 300;
		final List<String> out = Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, n).parallel().forEach(i -> {
			final Random r = new Random(i * 7919L + 17);
			final StringBuilder d = new StringBuilder();
			final int tracks = new int[]{1, 2, 3, 4, 5, 6, 8, 10}[r.nextInt(8)];
			d.append(tracks).append(" tracks ");
			d.append(LAYOUTS[r.nextInt(LAYOUTS.length)]).append(", ");
			final String mode = MODES[r.nextInt(MODES.length)];
			if (!mode.isEmpty()) {
				d.append(mode).append(", ");
			}
			d.append(new int[]{16, 24, 32, 48, 64, 96, 120, 160, 201, 300}[r.nextInt(10)]).append(" long, ");
			if (r.nextInt(3) == 0) {
				d.append(3 + r.nextInt(14)).append(" wide, ");
			}
			final String look = LOOKS[r.nextInt(LOOKS.length)];
			if (!look.isEmpty()) {
				d.append(look).append(", ");
			}
			final String roof = ROOFS[r.nextInt(ROOFS.length)];
			if (!roof.isEmpty()) {
				d.append(roof).append(", ");
			}
			final String building = BUILDINGS[r.nextInt(BUILDINGS.length)];
			if (!building.isEmpty()) {
				d.append(building).append(", ");
			}
			for (final String o : OPTIONS) {
				if (r.nextInt(5) == 0) {
					d.append(o).append(", ");
				}
			}
			d.append("named Sweep ").append(i);
			run(d.toString(), out);
		});
		// platform lists
		IntStream.range(0, n / 5).parallel().forEach(i -> {
			final Random r = new Random(i * 104729L + 3);
			final StringBuilder d = new StringBuilder();
			final int count = 1 + r.nextInt(10);
			for (int p = 1; p <= count; p++) {
				d.append("platform ").append(p).append(": ").append(r.nextBoolean() ? "island" : "side");
				if (r.nextInt(4) == 0) {
					d.append(" bay");
				}
				if (r.nextInt(3) == 0) {
					d.append(" ").append(3 + r.nextInt(18)).append(" wide");
				}
				if (r.nextInt(4) == 0) {
					d.append(" ").append(16 + r.nextInt(285)).append(" long");
				}
				if (r.nextInt(5) == 0) {
					d.append(r.nextBoolean() ? " open" : " canopy");
				}
				d.append("; ");
			}
			d.append(MODES[r.nextInt(MODES.length)]).append(", ").append(LOOKS[r.nextInt(LOOKS.length)]).append(", ")
					.append(BUILDINGS[r.nextInt(BUILDINGS.length)]).append(", ");
			for (final String o : OPTIONS) {
				if (r.nextInt(5) == 0) {
					d.append(o).append(", ");
				}
			}
			d.append("named List ").append(i);
			run(d.toString(), out);
		});
		Collections.sort(out);
		assertEquals(List.of(), out.stream().limit(20).toList());
	}

	private static void run(String text, List<String> out) {
		try {
			final ParseResult parsed = StationDescriptionParser.parse(text);
			final StationPlan plan = StationPlanner.plan(parsed.spec());
			final List<?> violations = LayoutValidator.validate(plan);
			final StationAudit.Result audit = StationAudit.audit(plan);
			for (final Object v : violations) {
				out.add("VIOLATION\t" + v.toString().replaceAll("\\s+", " ") + "\t" + text);
			}
			for (final StationAudit.Check c : audit.checks()) {
				if (c.status() == StationAudit.Status.FAIL) {
					out.add(c.status() + "\t" + c.topic() + ": " + c.message() + "\t" + text);
				}
			}
		} catch (Throwable e) {
			final StackTraceElement[] st = e.getStackTrace();
			out.add("EXCEPTION\t" + e + " at " + (st.length > 0 ? st[0] : "?") + (st.length > 1 ? " < " + st[1] : "") + "\t" + text);
		}
	}
}
