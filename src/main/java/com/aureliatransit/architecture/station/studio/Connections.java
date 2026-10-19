package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.StylePalette;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The joining states a planned block gets in the game once its neighbours stand, worked out from the plan alone for the
 * previews (Station Studio and the wrench's 3D view). Each rule mirrors the block's own class:
 * <ul>
 *     <li>screens ({@code PidsBlock}): the same screen facing the same way left, right, above and below, so a row of
 *     blocks is one display; left is clockwise of the facing;</li>
 *     <li>joining signs ({@code TextSignBlock}, {@code WayfindingSignBlock}): the same sign facing the same way left and
 *     right; left is anticlockwise of the facing;</li>
 *     <li>panes and iron bars ({@code PaneBlock}): other panes and bars, walls, and any solid full side; the mod's own panes
 *     ({@code ArchGlassPaneBlock}) also the roof and frame blocks in its {@code glass_pane_joins} tag;</li>
 *     <li>railings ({@code RailingBlock}: handrails, station fences): other railings and any solid full side;</li>
 *     <li>station girders ({@code GirderBlock}): other girders on all six sides, and any solid full side beside them;</li>
 *     <li>vanilla fences and walls: their own kind and any solid full side (walls as {@code low});</li>
 *     <li>sign poles ({@code SignPoleBlock}): a pole-mounted sign directly above or below.</li>
 * </ul>
 * States the plan already sets are kept.
 */
public final class Connections {

	/** Whether {@code block} (turned to {@code heading}) shows a solid full square on its {@code side} face. */
	@FunctionalInterface
	public interface FullFace {
		boolean test(PlannedBlock block, Heading heading, Heading side);
	}

	private static final String ATA = "aurelia_transit_architecture:";
	private static final Set<String> SCREENS = Set.of(ATA + "platform_pids", ATA + "hanging_platform_pids", ATA + "platform_cis",
			ATA + "hanging_platform_cis", ATA + "concourse_board");
	/** Signs with left and right joins whose layout joins (the lift status panel and stand back sign never do). */
	private static final Set<String> JOINING_SIGNS = Set.of(ATA + "bus_epaper_board", ATA + "composition_board", ATA + "direction_sign",
			ATA + "dutch_station_sign", ATA + "exit_sign", ATA + "german_station_sign", ATA + "hanging_station_sign",
			ATA + "hanging_wayfinding_sign", ATA + "passenger_info_terminal", ATA + "psd_text_panel", ATA + "station_info_board",
			ATA + "station_name_sign", ATA + "wall_wayfinding_sign", ATA + "ns_station_sign", ATA + "db_station_sign", ATA + "gvb_station_sign");
	private static final Set<String> RAILINGS = Set.of(ATA + "handrail", ATA + "station_fence");
	private static final String GIRDER = ATA + "station_girder";
	private static final String SIGN_POLE = ATA + "sign_pole";
	/** The {@code girder_joins} block tag: structural blocks (columns, piers, trusses) a girder frames into. */
	private static final Set<String> GIRDER_JOINS = tag("girder_joins");
	/** The {@code pole_mounts} block tag: signs a pole runs into. */
	private static final Set<String> POLE_MOUNTS = tag("pole_mounts");
	/** The {@code glass_pane_joins} block tag: roof and frame blocks the mod's glass panes join (ArchGlassPaneBlock). */
	private static final Set<String> PANE_JOINS = tag("glass_pane_joins");
	private static final Heading[] AROUND = {Heading.NORTH, Heading.EAST, Heading.SOUTH, Heading.WEST};

	private Connections() {
	}

	/**
	 * {@code block}'s state with its joins added. {@code at} maps {@link StudioApi#cell} keys in {@link StudioApi#FRAME} to
	 * the planned blocks.
	 */
	public static String state(PlannedBlock block, Map<Long, PlannedBlock> at, FullFace full) {
		return state(block, at, full, StudioApi.FRAME);
	}

	/** As {@link #state(PlannedBlock, Map, FullFace)}, for a station placed in {@code frame} ({@code at} is keyed in that frame too). */
	public static String state(PlannedBlock block, Map<Long, PlannedBlock> at, FullFace full, LocalFrame frame) {
		final String id = block.blockId();
		final int x = frame.x(block.u(), block.v());
		final int y = block.y();
		final int z = frame.z(block.u(), block.v());
		final Heading facing = frame.heading(block.front());
		final StringBuilder out = new StringBuilder(block.state());
		if (SCREENS.contains(id)) {
			add(out, "left", same(frame, block, facing, beside(at, x, y, z, facing.right())));
			add(out, "right", same(frame, block, facing, beside(at, x, y, z, facing.left())));
			add(out, "up", same(frame, block, facing, at.get(StudioApi.cell(x, y + 1, z))));
			add(out, "down", same(frame, block, facing, at.get(StudioApi.cell(x, y - 1, z))));
		} else if (JOINING_SIGNS.contains(id)) {
			add(out, "left", same(frame, block, facing, beside(at, x, y, z, facing.left())));
			add(out, "right", same(frame, block, facing, beside(at, x, y, z, facing.right())));
		} else if (id.equals(SIGN_POLE)) {
			add(out, "up", mounts(at.get(StudioApi.cell(x, y + 1, z))));
			add(out, "down", mounts(at.get(StudioApi.cell(x, y - 1, z))));
		} else if (id.equals(GIRDER)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (other.blockId().equals(GIRDER) || girderJoins(other) || solid(frame, other, side, full)));
			}
			add(out, "up", girder(at.get(StudioApi.cell(x, y + 1, z))));
			add(out, "down", girder(at.get(StudioApi.cell(x, y - 1, z))));
		} else if (pane(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (pane(other.blockId()) || wall(other.blockId()) || solid(frame, other, side, full)
						|| id.startsWith(ATA) && PANE_JOINS.contains(other.blockId())));
			}
		} else if (RAILINGS.contains(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (RAILINGS.contains(other.blockId()) || solid(frame, other, side, full)));
			}
		} else if (fence(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (fence(other.blockId()) || other.blockId().endsWith("_fence_gate") || solid(frame, other, side, full)));
			}
		} else if (wall(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				final boolean joined = other != null && (wall(other.blockId()) || pane(other.blockId()) || solid(frame, other, side, full));
				if (!out.toString().contains(name(side) + "=")) {
					out.append(out.length() == 0 ? "" : ",").append(name(side)).append('=').append(joined ? "low" : "none");
				}
			}
		}
		return out.toString();
	}

	private static final String ESCALATOR_STEP = StylePalette.MTR_ESCALATOR_STEP;
	private static final String ESCALATOR_SIDE = StylePalette.MTR_ESCALATOR_SIDE;

	/**
	 * The state the finished station holds, for a schematic: {@link #state} plus the shapes MTR's own blocks take from their
	 * neighbours once the station stands (the preview does not draw these, so its models are unchanged):
	 * <ul>
	 *     <li>an escalator step or side takes its {@code orientation} from the flight round it, by MTR's rule
	 *     ({@code BlockEscalatorBase.getOrientation}): with a same block ahead and behind it is flat; a block one up ahead and one down
	 *     behind, a slope; one up ahead and one behind, the bottom transition; one ahead and one down behind, the top transition;
	 *     otherwise the top landing when there is one behind and the bottom landing when not;</li>
	 *     <li>a lift door's {@code end} is true only when the block on its far side is a glass end piece, which the planner never
	 *     puts there ({@code BlockPSDAPGDoorBase.getStateForNeighborUpdate2}), so it is false.</li>
	 * </ul>
	 * States the plan already sets are kept.
	 */
	public static String finishedState(PlannedBlock block, Map<Long, PlannedBlock> at, FullFace full, LocalFrame frame) {
		final String joined = state(block, at, full, frame);
		final String id = block.blockId();
		if (id.equals(ESCALATOR_STEP) || id.equals(ESCALATOR_SIDE)) {
			return append(joined, "orientation", escalatorOrientation(block, at, frame));
		}
		if (id.equals(StylePalette.MTR_LIFT_DOOR)) {
			return append(joined, "end", "false");
		}
		return joined;
	}

	/**
	 * {@code models} with MTR's escalators answered by their own rule: an escalator block's outline ({@code BlockEscalatorBase.getOutlineShape2})
	 * is a full cube except in a slope or the top transition, so a handrail or pane beside a landing joins it and one beside a slope does
	 * not, whatever its model's boxes say.
	 */
	public static FullFace withMtrShapes(FullFace models, Map<Long, PlannedBlock> at, LocalFrame frame) {
		return (other, heading, side) -> {
			if (other.blockId().equals(ESCALATOR_STEP) || other.blockId().equals(ESCALATOR_SIDE)) {
				final String orientation = escalatorOrientation(other, at, frame);
				return !orientation.equals("slope") && !orientation.equals("transition_top");
			}
			return models.test(other, heading, side);
		};
	}

	private static String escalatorOrientation(PlannedBlock block, Map<Long, PlannedBlock> at, LocalFrame frame) {
		final String id = block.blockId();
		final Heading facing = frame.heading(block.front());
		final int x = frame.x(block.u(), block.v());
		final int z = frame.z(block.u(), block.v());
		final int y = block.y();
		final boolean ahead = sameBlock(at, x + facing.dx(), y, z + facing.dz(), id);
		final boolean aheadUp = sameBlock(at, x + facing.dx(), y + 1, z + facing.dz(), id);
		final boolean behind = sameBlock(at, x - facing.dx(), y, z - facing.dz(), id);
		final boolean behindDown = sameBlock(at, x - facing.dx(), y - 1, z - facing.dz(), id);
		return ahead && behind ? "flat" : aheadUp && behindDown ? "slope" : aheadUp && behind ? "transition_bottom"
				: ahead && behindDown ? "transition_top" : behind ? "landing_top" : "landing_bottom";
	}

	private static boolean sameBlock(Map<Long, PlannedBlock> at, int x, int y, int z, String id) {
		final PlannedBlock other = at.get(StudioApi.cell(x, y, z));
		return other != null && other.blockId().equals(id);
	}

	private static String append(String state, String key, String value) {
		if (state.contains(key + "=") ) {
			return state;
		}
		return state.isEmpty() ? key + "=" + value : state + "," + key + "=" + value;
	}

	/** A block tag of the mod, read from its data file on the classpath (the game's own list). */
	private static Set<String> tag(String name) {
		try (java.io.InputStream in = Connections.class.getResourceAsStream("/data/aurelia_transit_architecture/tags/blocks/" + name + ".json")) {
			if (in == null) {
				return Set.of();
			}
			final java.util.Set<String> out = new java.util.HashSet<>();
			for (final Object value : Json.list(Json.object(Json.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))).get("values"))) {
				out.add(Json.string(value, ""));
			}
			return Set.copyOf(out);
		} catch (final java.io.IOException e) {
			return Set.of();
		}
	}

	private static PlannedBlock beside(Map<Long, PlannedBlock> at, int x, int y, int z, Heading side) {
		return at.get(StudioApi.cell(x + side.dx(), y, z + side.dz()));
	}

	private static boolean same(LocalFrame frame, PlannedBlock block, Heading facing, PlannedBlock other) {
		return other != null && other.blockId().equals(block.blockId()) && frame.heading(other.front()) == facing;
	}

	/** {@code other}, beside a block on its {@code side}, shows a solid full square towards it. */
	private static boolean solid(LocalFrame frame, PlannedBlock other, Heading side, FullFace full) {
		return full.test(other, frame.heading(other.front()), side.opposite());
	}

	private static boolean mounts(PlannedBlock other) {
		return other != null && POLE_MOUNTS.contains(other.blockId());
	}

	private static boolean girder(PlannedBlock other) {
		return other != null && (other.blockId().equals(GIRDER) || girderJoins(other));
	}

	/** A structural block (a column, pier or truss) a girder frames into although it has no solid full side. */
	private static boolean girderJoins(PlannedBlock other) {
		return other != null && GIRDER_JOINS.contains(other.blockId());
	}

	private static boolean pane(String id) {
		return id.endsWith("_pane") || id.equals("minecraft:iron_bars");
	}

	private static boolean wall(String id) {
		return id.startsWith("minecraft:") && id.endsWith("_wall");
	}

	private static boolean fence(String id) {
		return id.startsWith("minecraft:") && id.endsWith("_fence");
	}

	private static String name(Heading side) {
		return side.name().toLowerCase(Locale.ROOT);
	}

	private static void add(StringBuilder out, String key, boolean value) {
		if (out.toString().contains(key + "=")) {
			return;
		}
		out.append(out.length() == 0 ? "" : ",").append(key).append('=').append(value);
	}
}
