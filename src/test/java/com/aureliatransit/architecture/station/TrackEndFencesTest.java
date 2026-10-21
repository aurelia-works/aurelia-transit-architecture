package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Jah, 10-21 ("missing gaps"): no open drop at a platform's end or behind a buffer stop. */
class TrackEndFencesTest {

	private static final List<String> STATIONS = List.of("amsterdam hub, 10 tracks, 120 long, seed tmptnb",
			"eight track amsterdam station, four island platforms, 240 long", "4 track terminus, 2 island platforms, 96 long",
			"3 tracks, 80 long, station building, footbridge");

	@Test
	void platformEndsAndPitEndsAreFenced() {
		final List<String> open = new ArrayList<>();
		for (final String words : STATIONS) {
			final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(words).spec());
			final Map<String, PlannedBlock> at = new HashMap<>();
			for (final PlannedBlock b : plan.blocks()) {
				at.put(b.u() + "," + b.y() + "," + b.v(), b);
			}
			final TrackLayout layout = plan.layout();
			// behind every buffer stop, across the whole open pit: floor behind an open pit cell carries a fence
			for (final TrackLayout.Track track : layout.tracks()) {
				if (!layout.buffered(track)) {
					continue;
				}
				final int buffer = layout.trackEnd(track) + 1;
				for (int u = track.u() - 4; u <= track.u() + 4; u++) {
					final PlannedBlock pit = at.get(u + ",0," + buffer);
					final PlannedBlock floor = at.get(u + ",0," + (buffer + 1));
					final boolean openPit = pit == null || pit.role() == Role.BUFFER_STOP;
					if (openPit && floor != null && floor.role().flatFloor() && at.get(u + ",1," + (buffer + 1)) == null) {
						open.add(words + ": behind the buffer at " + u + "," + (buffer + 1));
					}
				}
			}
			// each platform's last floor row before a drop
			for (final TrackLayout.PlatformBand band : layout.platforms()) {
				for (int u = band.uMin(); u <= band.uMax(); u++) {
					int v = band.vTo();
					while (v + 1 < layout.platformLength() && flat(at.get(u + ",0," + (v + 1)))) {
						v++;
					}
					if (v + 1 < layout.platformLength() && at.get(u + ",0," + (v + 1)) == null && at.get(u + ",1," + v) == null
							&& flat(at.get(u + ",0," + v)) && !(layout.terminus() ? false : ramped(at, u, band))) {
						open.add(words + ": platform " + band.number() + " end at " + u + "," + v);
					}
				}
			}
		}
		assertEquals(List.of(), open);
	}

	private static boolean flat(PlannedBlock block) {
		return block != null && block.role().flatFloor();
	}

	/** A through platform with a ramp down off its end leaves it open. */
	private static boolean ramped(Map<String, PlannedBlock> at, int u, TrackLayout.PlatformBand band) {
		final PlannedBlock next = at.get(u + ",0," + (band.vTo() + 1));
		return next != null && (next.role() == Role.RAMP_UPPER || next.role() == Role.RAMP_LOWER);
	}
}
