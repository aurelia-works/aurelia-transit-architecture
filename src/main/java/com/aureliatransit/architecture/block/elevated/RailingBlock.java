package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.EnumMap;
import java.util.Map;

import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * Posts and rails that join their neighbours: platform and trackside fencing, handrails, glass balustrades and ramp
 * edge rails. All railings connect to each other and to any full side face, whatever their style. Connections are
 * block states set at placement and on neighbour updates (no scanning); the visible style is a state cycled by
 * right-clicking with an empty hand.
 */
public abstract class RailingBlock<K extends Enum<K> & StringIdentifiable> extends StateShapedBlock {

	public static final BooleanProperty NORTH = Properties.NORTH;
	public static final BooleanProperty EAST = Properties.EAST;
	public static final BooleanProperty SOUTH = Properties.SOUTH;
	public static final BooleanProperty WEST = Properties.WEST;
	/** Pixels: railings stop players like a fence, whatever their drawn height. */
	private static final double COLLISION_HEIGHT = 24;

	private final String id;
	private final Class<K> kinds;
	private final Map<K, VoxelShape> posts;
	private final Map<K, Map<Direction, VoxelShape>> arms;
	private final Map<K, VoxelShape> collisionPosts;
	private final Map<K, Map<Direction, VoxelShape>> collisionArms;

	protected RailingBlock(Settings settings, String id, Class<K> kinds) {
		super(settings);
		this.id = id;
		this.kinds = kinds;
		this.posts = new EnumMap<>(kinds);
		this.arms = new EnumMap<>(kinds);
		this.collisionPosts = new EnumMap<>(kinds);
		this.collisionArms = new EnumMap<>(kinds);
		for (final K kind : kinds.getEnumConstants()) {
			posts.put(kind, post(kind));
			arms.put(kind, Shapes.horizontalMap(arm(kind)));
			collisionPosts.put(kind, Shapes.box(6.5, 0, 6.5, 9.5, COLLISION_HEIGHT, 9.5));
			collisionArms.put(kind, Shapes.horizontalMap(Shapes.box(7, 0, 0, 9, COLLISION_HEIGHT, 6.5)));
		}
		setDefaultState(getStateManager().getDefaultState().with(kindProperty(), kinds.getEnumConstants()[0])
				.with(NORTH, false).with(EAST, false).with(SOUTH, false).with(WEST, false));
	}

	/** The block's (static) kind property; used while the state definition is built, so it must not use instance fields. */
	protected abstract EnumProperty<K> kindProperty();

	/** The post at the centre of the block (always present). */
	protected abstract VoxelShape post(K kind);

	/** The arm reaching toward the north neighbour; rotated for the other three directions. */
	protected abstract VoxelShape arm(K kind);

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(kindProperty(), NORTH, EAST, SOUTH, WEST);
	}

	public static BooleanProperty property(Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH;
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			default -> throw new IllegalArgumentException("Not horizontal: " + direction);
		};
	}

	/** Whether a railing joins the block at {@code neighbour}, whose {@code side} faces this railing. */
	public static boolean joins(BlockView world, BlockPos neighbour, Direction side) {
		final BlockState state = world.getBlockState(neighbour);
		return state.getBlock() instanceof RailingBlock || state.isSideSolidFullSquare(world, neighbour, side);
	}

	private BlockState connected(BlockState state, BlockView world, BlockPos pos) {
		BlockState result = state;
		for (final Direction direction : Direction.Type.HORIZONTAL) {
			result = result.with(property(direction), joins(world, pos.offset(direction), direction.getOpposite()));
		}
		return result;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return connected(getDefaultState(), ctx.getWorld(), ctx.getBlockPos());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction.getAxis().isHorizontal()) {
			return state.with(property(direction), joins(world, neighborPos, direction.getOpposite()));
		}
		return state;
	}

	private VoxelShape build(BlockState state, Map<K, VoxelShape> postShapes, Map<K, Map<Direction, VoxelShape>> armShapes) {
		final K kind = state.get(kindProperty());
		VoxelShape shape = postShapes.get(kind);
		for (final Direction direction : Direction.Type.HORIZONTAL) {
			if (state.get(property(direction))) {
				shape = union(shape, armShapes.get(kind).get(direction));
			}
		}
		return shape;
	}

	@Override
	protected VoxelShape outlineFor(BlockState state) {
		return build(state, posts, arms);
	}

	@Override
	protected VoxelShape collisionFor(BlockState state) {
		return build(state, collisionPosts, collisionArms);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		return StyleCycle.use(id, kindProperty(), state, world, pos, player, hand);
	}
}
