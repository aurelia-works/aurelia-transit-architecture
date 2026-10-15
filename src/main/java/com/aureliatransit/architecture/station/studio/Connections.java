package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.PlannedBlock;

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
			ATA + "station_name_sign", ATA + "wall_wayfinding_sign");
	private static final Set<String> RAILINGS = Set.of(ATA + "handrail", ATA + "station_fence");
	private static final String GIRDER = ATA + "station_girder";
	private static final String SIGN_POLE = ATA + "sign_pole";
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
		final String id = block.blockId();
		final int x = StudioApi.FRAME.x(block.u(), block.v());
		final int y = block.y();
		final int z = StudioApi.FRAME.z(block.u(), block.v());
		final Heading facing = StudioApi.FRAME.heading(block.front());
		final StringBuilder out = new StringBuilder(block.state());
		if (SCREENS.contains(id)) {
			add(out, "left", same(block, facing, beside(at, x, y, z, facing.right())));
			add(out, "right", same(block, facing, beside(at, x, y, z, facing.left())));
			add(out, "up", same(block, facing, at.get(StudioApi.cell(x, y + 1, z))));
			add(out, "down", same(block, facing, at.get(StudioApi.cell(x, y - 1, z))));
		} else if (JOINING_SIGNS.contains(id)) {
			add(out, "left", same(block, facing, beside(at, x, y, z, facing.left())));
			add(out, "right", same(block, facing, beside(at, x, y, z, facing.right())));
		} else if (id.equals(SIGN_POLE)) {
			add(out, "up", mounts(at.get(StudioApi.cell(x, y + 1, z))));
			add(out, "down", mounts(at.get(StudioApi.cell(x, y - 1, z))));
		} else if (id.equals(GIRDER)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (other.blockId().equals(GIRDER) || solid(other, side, full)));
			}
			add(out, "up", girder(at.get(StudioApi.cell(x, y + 1, z))));
			add(out, "down", girder(at.get(StudioApi.cell(x, y - 1, z))));
		} else if (pane(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (pane(other.blockId()) || wall(other.blockId()) || solid(other, side, full)
						|| id.startsWith(ATA) && PANE_JOINS.contains(other.blockId())));
			}
		} else if (RAILINGS.contains(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (RAILINGS.contains(other.blockId()) || solid(other, side, full)));
			}
		} else if (fence(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				add(out, name(side), other != null && (fence(other.blockId()) || other.blockId().endsWith("_fence_gate") || solid(other, side, full)));
			}
		} else if (wall(id)) {
			for (final Heading side : AROUND) {
				final PlannedBlock other = beside(at, x, y, z, side);
				final boolean joined = other != null && (wall(other.blockId()) || pane(other.blockId()) || solid(other, side, full));
				if (!out.toString().contains(name(side) + "=")) {
					out.append(out.length() == 0 ? "" : ",").append(name(side)).append('=').append(joined ? "low" : "none");
				}
			}
		}
		return out.toString();
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

	private static boolean same(PlannedBlock block, Heading facing, PlannedBlock other) {
		return other != null && other.blockId().equals(block.blockId()) && StudioApi.FRAME.heading(other.front()) == facing;
	}

	/** {@code other}, beside a block on its {@code side}, shows a solid full square towards it. */
	private static boolean solid(PlannedBlock other, Heading side, FullFace full) {
		return full.test(other, StudioApi.FRAME.heading(other.front()), side.opposite());
	}

	private static boolean mounts(PlannedBlock other) {
		return other != null && POLE_MOUNTS.contains(other.blockId());
	}

	private static boolean girder(PlannedBlock other) {
		return other != null && other.blockId().equals(GIRDER);
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
