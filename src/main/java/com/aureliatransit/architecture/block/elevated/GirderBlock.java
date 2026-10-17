package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.GirderKind;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.Map;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * A full-depth roof girder that joins its neighbours: other girders on all six sides (so a stepped arch rib stays one piece)
 * and any full side face (a wall it frames into). It fills its block from bottom to top, so it always meets the column or
 * cap below it and the roof above it. Connections are block states set at placement and on neighbour updates; a girder
 * with no connections runs along its {@code axis}, chosen from the way the player looks. The colour is a state cycled by
 * right-clicking with an empty hand.
 */
public class GirderBlock extends StateShapedBlock {

	public static final EnumProperty<GirderKind> KIND = EnumProperty.of("kind", GirderKind.class);
	public static final BooleanProperty NORTH = Properties.NORTH;
	public static final BooleanProperty EAST = Properties.EAST;
	public static final BooleanProperty SOUTH = Properties.SOUTH;
	public static final BooleanProperty WEST = Properties.WEST;
	public static final BooleanProperty UP = Properties.UP;
	public static final BooleanProperty DOWN = Properties.DOWN;
	public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

	/** Structural blocks a girder frames into although they are not full cubes (columns, piers, trusses, other girders). */
	public static final TagKey<Block> JOINS = TagKey.of(RegistryKeys.BLOCK, AureliaTransitArchitecture.id("girder_joins"));

	/** The centre: a 4 px web post with flange plates top and bottom (always drawn). */
	private static final VoxelShape NODE = union(box(6, 0, 6, 10, 16, 10), box(4, 0, 4, 12, 2, 12), box(4, 14, 4, 12, 16, 12));
	/** An I-section arm toward the north neighbour: web plus top and bottom flanges. */
	private static final Map<Direction, VoxelShape> ARMS = Shapes.horizontalMap(
			union(box(6, 2, 0, 10, 14, 6), box(4, 0, 0, 12, 2, 6), box(4, 14, 0, 12, 16, 6)));
	/** A stiffener across the post when the girder continues up or down (arch ribs, hangers). */
	private static final VoxelShape VERTICAL = box(4, 0, 7, 12, 16, 9);

	public GirderBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(KIND, GirderKind.STEEL).with(AXIS, Direction.Axis.X)
				.with(NORTH, false).with(EAST, false).with(SOUTH, false).with(WEST, false).with(UP, false).with(DOWN, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(KIND, AXIS, NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}

	public static BooleanProperty property(Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH;
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case UP -> UP;
			case DOWN -> DOWN;
		};
	}

	/** Whether a girder joins {@code neighbourState} at {@code neighbour}, whose {@code side} faces the girder. */
	public static boolean joins(BlockState neighbourState, BlockView world, BlockPos neighbour, Direction side) {
		if (neighbourState.getBlock() instanceof GirderBlock) {
			return true;
		}
		// the mod's own structural blocks (columns, piers, trusses) are not full cubes, but a girder still frames into them
		if (neighbourState.isIn(JOINS)) {
			return true;
		}
		return side.getAxis().isHorizontal() && neighbourState.isSideSolidFullSquare(world, neighbour, side);
	}

	private BlockState connected(BlockState state, BlockView world, BlockPos pos) {
		BlockState result = state;
		for (final Direction direction : Direction.values()) {
			final BlockPos neighbour = pos.offset(direction);
			result = result.with(property(direction), joins(world.getBlockState(neighbour), world, neighbour, direction.getOpposite()));
		}
		return result;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final Direction.Axis axis = ctx.getHorizontalPlayerFacing().getAxis();
		return connected(getDefaultState().with(AXIS, axis), ctx.getWorld(), ctx.getBlockPos());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos) {
		return state.with(property(direction), joins(neighborState, world, neighborPos, direction.getOpposite()));
	}

	/** True when no side is joined: the girder then runs along its axis. */
	public static boolean alone(BlockState state) {
		for (final Direction direction : Direction.values()) {
			if (state.get(property(direction))) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected VoxelShape outlineFor(BlockState state) {
		VoxelShape shape = NODE;
		final boolean alone = alone(state);
		for (final Direction direction : Direction.Type.HORIZONTAL) {
			if (state.get(property(direction)) || alone && direction.getAxis() == state.get(AXIS)) {
				shape = union(shape, ARMS.get(direction));
			}
		}
		if (state.get(UP) || state.get(DOWN)) {
			shape = union(shape, VERTICAL);
		}
		return shape;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		return StyleCycle.use("station_girder", KIND, state, world, pos, player, hand);
	}
}
