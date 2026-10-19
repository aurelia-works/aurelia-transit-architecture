package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StylePalette;
import com.aureliatransit.architecture.station.WorldOp;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the plan's block ids and headings into block states. Ids are looked up with {@code getOrEmpty}: a typo is a missing
 * block, never air. MTR's rail node is resolved by id and its boolean properties by name, so ATA does not link against MTR.
 */
public final class BlockStateResolver {

	/** Shown when MTR is absent and the planned rail nodes are left out. */
	public static final String RAIL_SKIPPED = "MTR rail nodes skipped: MTR is not loaded";

	private BlockStateResolver() {
	}

	/** A world op with its block state, before neighbour post-processing. */
	public record Prepared(WorldOp op, BlockState state) {
	}

	/**
	 * @param ops         every op that can be executed, in order
	 * @param missing     block ids that are not in the registry (sorted, without duplicates)
	 * @param railSkipped rail node ops were dropped because MTR is not loaded
	 */
	public record Resolution(List<Prepared> ops, List<String> missing, boolean railSkipped) {

		public boolean ok() {
			return missing.isEmpty();
		}
	}

	/** Block ids of a plan that are not in the registry. */
	public record Check(List<String> missing, boolean railSkipped) {

		public boolean ok() {
			return missing.isEmpty();
		}
	}

	/** The block for an id; empty for an unknown or malformed id. */
	public static Optional<Block> block(String id) {
		final Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? Optional.empty() : Registries.BLOCK.getOrEmpty(identifier);
	}

	public static boolean railAvailable() {
		return block(StylePalette.MTR_RAIL_NODE).isPresent();
	}

	public static Check check(StationPlan plan) {
		return check(plan, railAvailable());
	}

	public static Check check(StationPlan plan, boolean railAvailable) {
		final Set<String> ids = new TreeSet<>();
		boolean railSkipped = false;
		for (final PlannedBlock block : plan.blocks()) {
			ids.add(block.blockId());
		}
		ids.add(plan.foundationId());
		final List<String> missing = new ArrayList<>();
		for (final String id : ids) {
			if (id.startsWith("mtr:") && !railAvailable) {
				railSkipped = true;
			} else if (block(id).isEmpty()) {
				missing.add(id);
			}
		}
		return new Check(missing, railSkipped);
	}

	public static Resolution resolve(List<WorldOp> ops) {
		return resolve(ops, railAvailable());
	}

	public static Resolution resolve(List<WorldOp> ops, boolean railAvailable) {
		final Map<String, Optional<Block>> cache = new HashMap<>();
		final List<Prepared> prepared = new ArrayList<>(ops.size());
		final Set<String> missing = new TreeSet<>();
		boolean railSkipped = false;
		for (final WorldOp op : ops) {
			if (op.blockId().startsWith("mtr:") && !railAvailable) {
				railSkipped = true;
				continue;
			}
			final Optional<Block> block = cache.computeIfAbsent(op.blockId(), BlockStateResolver::block);
			if (block.isEmpty()) {
				missing.add(op.blockId());
			} else {
				prepared.add(new Prepared(op, withState(state(block.get(), op.facing()), op.state())));
			}
		}
		return new Resolution(prepared, List.copyOf(missing), railSkipped);
	}

	/** Blocks whose front (the ticket machine's screen, the tactile map's sloped face, the route map's panel) faces against their FACING property: MTR sets FACING to the way the
	 * placing player looks, so the screen faces the player. */
	static final Set<String> FACING_AWAY = Set.of("mtr:ticket_machine", "mtr:tactile_map", "mtr:route_sign_wall_light");

	/**
	 * The state for a block facing {@code facing}: FACING for facing-shaped blocks, the facing's axis for axis blocks, and for
	 * {@code mtr:rail} the {@code facing} flag true for a track along X, with no 45 or 22.5 degree angle and not connected.
	 */
	public static BlockState state(Block block, Heading facing) {
		final Heading heading = facing == null ? Heading.NORTH : facing;
		final BlockState state = block.getDefaultState();
		if (Registries.BLOCK.getId(block).toString().equals(StylePalette.MTR_RAIL_NODE)) {
			BlockState rail = withBoolean(state, "facing", heading.alongX());
			rail = withBoolean(rail, "is_45", false);
			rail = withBoolean(rail, "is_22_5", false);
			return withBoolean(rail, "is_connected", false);
		}
		if (state.contains(Properties.HORIZONTAL_FACING)) {
			final Direction front = direction(heading);
			return state.with(Properties.HORIZONTAL_FACING, FACING_AWAY.contains(Registries.BLOCK.getId(block).toString()) ? front.getOpposite() : front);
		}
		if (state.contains(Properties.HORIZONTAL_AXIS)) {
			return state.with(Properties.HORIZONTAL_AXIS, direction(heading).getAxis());
		}
		return state;
	}

	/**
	 * Applies extra properties written as {@code name=value,name=value}; a property the block does not have, or a value it does
	 * not accept, is skipped, so a plan never fails on a block that changed shape.
	 */
	public static BlockState withState(BlockState state, String extras) {
		if (extras == null || extras.isEmpty()) {
			return state;
		}
		BlockState result = state;
		for (final String pair : extras.split(",")) {
			final int eq = pair.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			final Property<?> property = result.getBlock().getStateManager().getProperty(pair.substring(0, eq).trim());
			if (property != null) {
				result = with(result, property, pair.substring(eq + 1).trim());
			}
		}
		return result;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
	}

	private static BlockState withBoolean(BlockState state, String name, boolean value) {
		final Property<?> property = state.getBlock().getStateManager().getProperty(name);
		return property instanceof BooleanProperty flag ? state.with(flag, value) : state;
	}

	public static Direction direction(Heading heading) {
		return Direction.valueOf(heading.name());
	}

	public static Heading heading(Direction direction) {
		return Heading.valueOf(direction.name());
	}
}
