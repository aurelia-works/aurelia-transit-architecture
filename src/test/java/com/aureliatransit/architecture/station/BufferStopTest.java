package com.aureliatransit.architecture.station;

import com.aureliatransit.architecture.block.BufferStopBlock.Part;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BufferStopTest {

	@Test
	void partsJoinFromTheirNeighbours() {
		assertEquals(Part.SINGLE, Part.of(false, false));
		assertEquals(Part.LEFT, Part.of(false, true));
		assertEquals(Part.MIDDLE, Part.of(true, true));
		assertEquals(Part.RIGHT, Part.of(true, false));
	}

	@Test
	void everyTerminusTrackEndsInOneBufferFacingTheTrain() {
		for (final String key : List.of("st_pancras", "antwerp", "frankfurt")) {
			final StationPresets.Preset preset = StationPresets.ALL.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
			final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(preset.description()).spec());
			final List<PlannedBlock> buffers = plan.blocks().stream().filter(b -> b.role() == Role.BUFFER_STOP).toList();
			assertFalse(buffers.isEmpty(), key);
			final String id = "aurelia_transit_architecture:" + (key.equals("st_pancras") ? "heritage_buffer_stop" : "buffer_stop");
			for (final PlannedBlock b : buffers) {
				assertEquals(id, b.blockId(), key);
				assertEquals(LocalDir.MINUS_V, b.front(), key);
				final boolean left = buffers.stream().anyMatch(o -> o.v() == b.v() && o.y() == b.y() && o.u() == b.u() - 1);
				final boolean right = buffers.stream().anyMatch(o -> o.v() == b.v() && o.y() == b.y() && o.u() == b.u() + 1);
				assertEquals("part=" + Part.of(left, right).asString(), b.state(), key + " at u=" + b.u());
			}
		}
	}
}
