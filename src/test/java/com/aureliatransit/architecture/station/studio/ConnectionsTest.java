package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.LocalDir;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.Role;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The previews join blocks the way the game does once they stand. */
class ConnectionsTest {

	private static final String ATA = "aurelia_transit_architecture:";
	/** No neighbour here shows a full side, so only the blocks' own rules join them. */
	private static final Connections.FullFace NONE = (block, heading, side) -> false;

	@Test
	void theModsPanesJoinIronworkAndScreensJoinIntoOneDisplay() {
		final PlannedBlock pane = block(0, 0, ATA + "clear_float_glass_pane", LocalDir.PLUS_V);
		final PlannedBlock vanilla = block(0, 2, "minecraft:glass_pane", LocalDir.PLUS_V);
		final Map<Long, PlannedBlock> at = at(List.of(pane, vanilla,
				block(1, 0, ATA + "station_girder", LocalDir.PLUS_V),
				block(-1, 0, ATA + "amsterdam_cast_iron_shed_leg", LocalDir.PLUS_V),
				block(1, 2, ATA + "station_girder", LocalDir.PLUS_V)));
		final String state = Connections.state(pane, at, NONE);
		assertTrue(state.contains("east=true") && state.contains("west=true"), "joins the girder and the iron leg: " + state);
		assertTrue(state.contains("north=false"), state);
		assertTrue(Connections.state(vanilla, at, NONE).contains("east=false"), "vanilla panes know nothing of the tag");
		assertTrue(Connections.state(vanilla, at, (block, heading, side) -> true).contains("east=true"), "but join full sides");

		final PlannedBlock left = block(0, 0, ATA + "platform_pids", LocalDir.PLUS_V);
		final PlannedBlock right = block(1, 0, ATA + "platform_pids", LocalDir.PLUS_V);
		final PlannedBlock turned = block(2, 0, ATA + "platform_pids", LocalDir.MINUS_V);
		final Map<Long, PlannedBlock> screens = at(List.of(left, right, turned));
		assertEquals("left=true,right=false,up=false,down=false", Connections.state(left, screens, NONE));
		assertEquals("left=false,right=true,up=false,down=false", Connections.state(right, screens, NONE));
		assertEquals("left=false,right=false,up=false,down=false", Connections.state(turned, screens, NONE), "facing the other way, apart");
	}

	@Test
	void aRowOfBenchesJoinsIntoOneLongBench() {
		final PlannedBlock end = block(0, 0, ATA + "steel_bench", LocalDir.PLUS_V);
		final PlannedBlock middle = block(1, 0, ATA + "steel_bench", LocalDir.PLUS_V);
		final PlannedBlock other = block(2, 0, ATA + "steel_bench", LocalDir.PLUS_V);
		final PlannedBlock wood = block(3, 0, ATA + "wooden_bench", LocalDir.PLUS_V);
		final Map<Long, PlannedBlock> row = at(List.of(end, middle, other, wood));
		assertEquals("left=true,right=true", Connections.state(middle, row, NONE));
		assertEquals("left=false,right=true", Connections.state(end, row, NONE));
		assertEquals("left=true,right=false", Connections.state(other, row, NONE), "a different bench is not part of the row");
		assertEquals("left=false,right=false", Connections.state(wood, row, NONE));
	}

	private static PlannedBlock block(int u, int v, String id, LocalDir front) {
		return new PlannedBlock(u, 1, v, Role.FIXTURE, id, front, "", "");
	}

	private static Map<Long, PlannedBlock> at(List<PlannedBlock> blocks) {
		final Map<Long, PlannedBlock> at = new HashMap<>();
		for (final PlannedBlock block : blocks) {
			at.put(StudioApi.cell(StudioApi.FRAME.x(block.u(), block.v()), block.y(), StudioApi.FRAME.z(block.u(), block.v())), block);
		}
		return at;
	}
}
