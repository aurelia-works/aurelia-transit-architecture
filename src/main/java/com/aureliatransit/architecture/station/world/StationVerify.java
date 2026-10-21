package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.RailEdges;
import com.aureliatransit.architecture.station.StationPlan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a station's cells back from the world and compares them with its plan: the block, the way it faces and every property the
 * plan itself set (a stair's half, a door's side). Joins that come from neighbours (panes, fences, girders) are not compared, since
 * the game works them out and the plan does not set them. Read only: it places and removes nothing, and never loads a chunk (cells in
 * unloaded chunks are counted, not read). The work is one pass over the plan on the server thread, a few microseconds a block.
 *
 * <p>It answers "does the world hold this plan here?" for a station that was built, and for one that was pasted from an exported
 * schematic with the paste origin at a known spot ({@code /ata_station verify paste}). Things a block file cannot carry (sign text, a
 * lift's MTR data, the MTR station zone, rail connections) are not compared.
 */
public final class StationVerify {

	/** How many mismatches a report lists. */
	public static final int LISTED = 10;

	private StationVerify() {
	}

	/**
	 * @param planned    blocks in the plan
	 * @param matched    blocks that are as planned
	 * @param skipped    blocks left out on purpose (MTR rail nodes when MTR is absent)
	 * @param unloaded   blocks in chunks that are not loaded
	 * @param mismatches the first {@link #LISTED} blocks that are not as planned
	 * @param differing  how many blocks are not as planned
	 */
	public record Report(int planned, int matched, int skipped, int unloaded, List<Mismatch> mismatches, int differing) {

		/** Every planned block was read and is as planned. */
		public boolean exact() {
			return differing == 0 && unloaded == 0 && matched + skipped == planned;
		}
	}

	/** One block that is not as planned: where, what was planned there and what is there. */
	public record Mismatch(BlockPos pos, String expected, String found) {
	}

	public static Report verify(ServerWorld world, StationPlan plan, LocalFrame frame) {
		return verify(world, plan, frame, BlockStateResolver.railAvailable());
	}

	public static Report verify(ServerWorld world, StationPlan plan, LocalFrame frame, boolean railAvailable) {
		int matched = 0;
		int skipped = 0;
		int unloaded = 0;
		int differing = 0;
		final List<Mismatch> mismatches = new ArrayList<>();
		final Set<Long> loaded = new HashSet<>();
		final Set<Long> missing = new HashSet<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.blockId().startsWith("mtr:") && !railAvailable && block.blockId().equals(com.aureliatransit.architecture.station.StylePalette.MTR_RAIL_NODE)) {
				skipped++;
				continue;
			}
			final BlockPos pos = new BlockPos(frame.x(block.u(), block.v()), frame.y(block.y()), frame.z(block.u(), block.v()));
			final long chunk = net.minecraft.util.math.ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
			if (missing.contains(chunk) || !loaded.contains(chunk) && !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
				missing.add(chunk);
				unloaded++;
				continue;
			}
			loaded.add(chunk);
			final Optional<Block> wanted = BlockStateResolver.block(block.blockId());
			final BlockState found = world.getBlockState(pos);
			if (wanted.isEmpty()) {
				differing++;
				note(mismatches, pos, block.blockId() + " (not a block in this game)", found);
				continue;
			}
			final Heading heading = frame.heading(block.front());
			final String state = RailEdges.worldState(block.state(), heading);
			final BlockState expected = BlockStateResolver.withState(BlockStateResolver.state(wanted.get(), heading), state);
			final String why = differs(expected, found, state);
			if (why == null) {
				matched++;
			} else {
				differing++;
				note(mismatches, pos, describe(expected), found);
			}
		}
		return new Report(plan.blocks().size(), matched, skipped, unloaded, List.copyOf(mismatches), differing);
	}

	/**
	 * Null when {@code found} is {@code expected} as far as the plan decides it: the same block, and the same value for the facing,
	 * the horizontal axis and each property the plan set.
	 */
	static String differs(BlockState expected, BlockState found, String extras) {
		if (found.getBlock() != expected.getBlock()) {
			return "block";
		}
		final Set<String> planned = new HashSet<>();
		for (final String pair : extras.split(",")) {
			final int eq = pair.indexOf('=');
			if (eq > 0) {
				planned.add(pair.substring(0, eq).trim());
			}
		}
		for (final Property<?> property : expected.getBlock().getStateManager().getProperties()) {
			final boolean decided = property == Properties.HORIZONTAL_FACING || property == Properties.HORIZONTAL_AXIS || planned.contains(property.getName())
					|| isRailFlag(expected, property);
			if (decided && !expected.get(property).equals(found.get(property))) {
				return property.getName();
			}
		}
		return null;
	}

	/**
	 * A rail node's direction flags are planned; whether it is connected is not, since the build joins its platform nodes
	 * in MTR itself (and a player may join others).
	 */
	private static boolean isRailFlag(BlockState state, Property<?> property) {
		return Registries.BLOCK.getId(state.getBlock()).toString().equals(com.aureliatransit.architecture.station.StylePalette.MTR_RAIL_NODE)
				&& (property.getName().equals("facing") || property.getName().startsWith("is_")) && !property.getName().equals("is_connected");
	}

	private static void note(List<Mismatch> mismatches, BlockPos pos, String expected, BlockState found) {
		if (mismatches.size() < LISTED) {
			mismatches.add(new Mismatch(pos, expected, describe(found)));
		}
	}

	/** {@code id[property=value,...]} as a person reads it. */
	public static String describe(BlockState state) {
		final String id = Registries.BLOCK.getId(state.getBlock()).toString();
		final String text = state.toString();
		final int bracket = text.indexOf('[');
		return bracket < 0 ? id : id + text.substring(bracket);
	}
}
