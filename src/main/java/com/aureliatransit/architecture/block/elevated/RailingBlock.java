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
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * Posts and rails that join their neighbours: platform and trackside fencing, handrails, glass balustrades and ramp
 * edge rails. All railings connect to each other and to any full side face, whatever their style. Connections are
 * block states set at placement and on neighbour updates (no scanning); the visible style is a state cycled by
 * right-clicking with an empty hand.
 *
 * <p>A railing can instead stand on one or more sides of its block ({@code edge_north} and so on), flush with the drop it
 * guards, so the block itself stays walkable; two sides make a corner piece. The station generator sets these; a railing
 * with any edge set shows only its edge rails. Where two edge runs meet round an outside corner their rails touch only at
 * a point, and one of them carries a post there ({@link #CORNER}: the side its rail is on and which end of it, clockwise
 * or anticlockwise seen from above); the post is drawn only, it stands just past the block's corner.
 */
public abstract class RailingBlock<K extends Enum<K> & StringIdentifiable> extends StateShapedBlock {

	public static final BooleanProperty NORTH = Properties.NORTH;
	public static final BooleanProperty EAST = Properties.EAST;
	public static final BooleanProperty SOUTH = Properties.SOUTH;
	public static final BooleanProperty WEST = Properties.WEST;
	public static final BooleanProperty EDGE_NORTH = BooleanProperty.of("edge_north");
	public static final BooleanProperty EDGE_EAST = BooleanProperty.of("edge_east");
	public static final BooleanProperty EDGE_SOUTH = BooleanProperty.of("edge_south");
	public static final BooleanProperty EDGE_WEST = BooleanProperty.of("edge_west");
	public static final EnumProperty<Corner> CORNER = EnumProperty.of("corner", Corner.class);

	/** Where an edge railing's corner post stands: none, or a side's clockwise or anticlockwise end. */
	public enum Corner implements StringIdentifiable {
		NONE, NORTH_CW, NORTH_CCW, EAST_CW, EAST_CCW, SOUTH_CW, SOUTH_CCW, WEST_CW, WEST_CCW;

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}
	/** Pixels: railings stop players like a fence, whatever their drawn height. */
	private static final double COLLISION_HEIGHT = 24;
	/** A rail along the north side, as drawn (post and rails within 3 px of the face), and as it stops players. */
	private static final Map<Direction, VoxelShape> EDGE_OUTLINES = Shapes.horizontalMap(Shapes.box(0, 0, 0, 16, 15, 3));
	private static final Map<Direction, VoxelShape> EDGE_COLLISIONS = Shapes.horizontalMap(Shapes.box(0, 0, 0, 16, COLLISION_HEIGHT, 2));

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
				.with(NORTH, false).with(EAST, false).with(SOUTH, false).with(WEST, false)
				.with(EDGE_NORTH, false).with(EDGE_EAST, false).with(EDGE_SOUTH, false).with(EDGE_WEST, false).with(CORNER, Corner.NONE));
	}

	/** The block's (static) kind property; used while the state definition is built, so it must not use instance fields. */
	protected abstract EnumProperty<K> kindProperty();

	/** The post at the centre of the block (always present). */
	protected abstract VoxelShape post(K kind);

	/** The arm reaching toward the north neighbour; rotated for the other three directions. */
	protected abstract VoxelShape arm(K kind);

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(kindProperty(), NORTH, EAST, SOUTH, WEST, EDGE_NORTH, EDGE_EAST, EDGE_SOUTH, EDGE_WEST, CORNER);
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

	/** The property for a rail on the {@code direction} side of the block. */
	public static BooleanProperty edge(Direction direction) {
		return switch (direction) {
			case NORTH -> EDGE_NORTH;
			case EAST -> EDGE_EAST;
			case SOUTH -> EDGE_SOUTH;
			case WEST -> EDGE_WEST;
			default -> throw new IllegalArgumentException("Not horizontal: " + direction);
		};
	}

	/** Whether a railing joins the block at {@code neighbour}, whose {@code side} faces this railing. */
	public static boolean joins(BlockView world, BlockPos neighbour, Direction side) {
		return joins(world.getBlockState(neighbour), world, neighbour, side);
	}

	/** As above, on the state the caller already holds (so a build sees a neighbour placed earlier in the same tick). */
	public static boolean joins(BlockState state, BlockView world, BlockPos neighbour, Direction side) {
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
		final BlockView world = ctx.getWorld();
		final BlockPos pos = ctx.getBlockPos();
		return continueRun(connected(getDefaultState(), world, pos), direction -> world.getBlockState(pos.offset(direction)));
	}

	/**
	 * A railing placed by hand carries on the edge run it is placed into: it takes each side whose rail a neighbour along
	 * that side also has, so a broken piece of a generated run, corner included, goes back as it was (Jah 10-26: it came
	 * back as a centre fence).
	 */
	public static BlockState continueRun(BlockState state, Function<Direction, BlockState> neighbour) {
		BlockState result = state;
		for (final Direction side : Direction.Type.HORIZONTAL) {
			for (final Direction along : new Direction[]{side.rotateYClockwise(), side.rotateYCounterclockwise()}) {
				final BlockState next = neighbour.apply(along);
				if (next.getBlock() instanceof RailingBlock && next.get(edge(side))) {
					result = result.with(edge(side), true);
					break;
				}
			}
		}
		return result;
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction.getAxis().isHorizontal()) {
			// the state handed in, not a fresh world read: during a station build the neighbour is placed in the same tick
			return state.with(property(direction), joins(neighborState, world, neighborPos, direction.getOpposite()));
		}
		return state;
	}

	private VoxelShape build(BlockState state, Map<K, VoxelShape> postShapes, Map<K, Map<Direction, VoxelShape>> armShapes) {
		final Map<Direction, VoxelShape> edges = postShapes == posts ? EDGE_OUTLINES : EDGE_COLLISIONS;
		VoxelShape edged = null;
		for (final Direction direction : Direction.Type.HORIZONTAL) {
			if (state.get(edge(direction))) {
				edged = edged == null ? edges.get(direction) : union(edged, edges.get(direction));
			}
		}
		if (edged != null) {
			return edged;
		}
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
