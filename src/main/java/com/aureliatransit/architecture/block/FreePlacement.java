package com.aureliatransit.architecture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Free placement of screens, signs and clocks: an extra turn between the four facings ({@link #ANGLE}, 22.5 degree steps, so
 * 16 directions like a vanilla sign) and a nudge sideways, forwards or back ({@link #OFFSET}: half a block, or just far
 * enough to sit flush with that edge of the block space), so a block can stand at an angle or line up between two blocks.
 * <p>
 * A {@link FacingShapedBlock} subclass opts in by calling {@link #addTurn} and/or {@link #addNudge} in its
 * {@code appendProperties}; {@link FacingShapedBlock} then places, shapes, rotates and mirrors it. Both default to none, so
 * blocks placed before keep their state. Plain placement is unchanged; sneak-placing turns the block to face you exactly
 * and nudges it towards where you clicked. Models follow through a client model wrapper and block entity renderers through
 * {@code FreePlacementRender}; no block entity or ticking is added.
 */
public final class FreePlacement {

	/** Extra clockwise turn (seen from above) past FACING, in 22.5 degree steps. */
	public static final IntProperty ANGLE = IntProperty.of("angle", 0, 3);
	/** Nudge in the block's own frame (seen from the front), along FACING and its sides. */
	public static final EnumProperty<Nudge> OFFSET = EnumProperty.of("offset", Nudge.class);
	public static final float STEP_DEGREES = 22.5F;
	/** How far a half nudge moves the block, in blocks. */
	public static final double HALF = 0.5;
	/** Turned boxes are cut into slices this long (in blocks) so a turned panel is held by a staircase, not a big square. */
	private static final double SLICE = 0.25;
	/** Click zones on a face, from its centre: inside {@link #CENTRE} stays centred, inside {@link #FLUSH} sits flush. */
	private static final double CENTRE = 1.0 / 6.0;
	private static final double FLUSH = 1.0 / 3.0;

	private static final Map<Key, Moved> MOVED = new ConcurrentHashMap<>();

	private FreePlacement() {
	}

	/**
	 * A nudge. {@code side} +1 moves the block to the left of someone looking at its front, {@code depth} +1 moves it
	 * towards its front; a flush nudge moves only until the block's shape touches that edge of its block space.
	 */
	public enum Nudge implements StringIdentifiable {
		NONE(0, 0, false), LEFT(1, 0, false), RIGHT(-1, 0, false), FRONT(0, 1, false), BACK(0, -1, false),
		FLUSH_LEFT(1, 0, true), FLUSH_RIGHT(-1, 0, true), FLUSH_FRONT(0, 1, true), FLUSH_BACK(0, -1, true);

		private final int side;
		private final int depth;
		private final boolean flush;

		Nudge(int side, int depth, boolean flush) {
			this.side = side;
			this.depth = depth;
			this.flush = flush;
		}

		public int side() {
			return side;
		}

		public int depth() {
			return depth;
		}

		public boolean flush() {
			return flush;
		}

		/** The nudge one way along one axis; a sideways {@code side} wins when both are given. */
		public static Nudge of(int side, int depth, boolean flush) {
			final int s = Integer.signum(side);
			final int d = s != 0 ? 0 : Integer.signum(depth);
			for (final Nudge nudge : values()) {
				if (nudge != NONE && nudge.flush == flush && nudge.side == s && nudge.depth == d) {
					return nudge;
				}
			}
			return NONE;
		}

		/** Left and right swap in a mirror image. */
		public Nudge mirrored() {
			return side == 0 ? this : of(-side, 0, flush);
		}

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Lets the block turn in 16 steps. */
	public static void addTurn(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ANGLE);
	}

	/** Lets the block be nudged half a block or flush to an edge. */
	public static void addNudge(StateManager.Builder<Block, BlockState> builder) {
		builder.add(OFFSET);
	}

	public static int angle(BlockState state) {
		return state.contains(ANGLE) ? state.get(ANGLE) : 0;
	}

	public static Nudge nudge(BlockState state) {
		return state.contains(OFFSET) ? state.get(OFFSET) : Nudge.NONE;
	}

	/** Turned off the four facings or nudged off the block centre. */
	public static boolean moved(BlockState state) {
		return angle(state) != 0 || nudge(state) != Nudge.NONE;
	}

	/** Whether two blocks sit the same way, so they may join into one screen or sign: square on and nudged alike. */
	public static boolean joinable(BlockState a, BlockState b) {
		return angle(a) == 0 && angle(b) == 0 && nudge(a) == nudge(b);
	}

	/** Clockwise turn past FACING in degrees (seen from above). */
	public static float degrees(BlockState state) {
		return angle(state) * STEP_DEGREES;
	}

	/** The nudge in world blocks (x, 0, z), measured on the turned outline. Zero for other blocks. */
	public static Vec3d offset(BlockState state) {
		if (nudge(state) == Nudge.NONE || !(state.getBlock() instanceof FacingShapedBlock block)) {
			return Vec3d.ZERO;
		}
		return moved(state, block.squareOutline(state.get(FacingShapedBlock.FACING))).offset;
	}

	/**
	 * Placement: plain placement keeps the facing it was given and sits square in the middle. Sneaking turns the block to
	 * face the player in 16 steps and nudges it towards the clicked spot on the face that was clicked (see
	 * {@link #place(BlockState, float, Vec3d, Direction, boolean)}).
	 *
	 * @param towardPlayer the block's front faces the player (signs, screens); otherwise it faces where the player looks
	 */
	public static BlockState place(BlockState state, ItemPlacementContext ctx, boolean towardPlayer) {
		if (state == null || ctx.getPlayer() == null || !ctx.getPlayer().isSneaking() || !(state.contains(ANGLE) || state.contains(OFFSET))) {
			return state;
		}
		final Vec3d centre = Vec3d.ofCenter(ctx.getBlockPos());
		return place(state, ctx.getPlayerYaw(), ctx.getHitPos().subtract(centre), ctx.getSide(), towardPlayer);
	}

	/**
	 * Sneak placement from the player's yaw and the clicked spot. Along the clicked face, the middle third keeps the block
	 * centred, the next sixth sits it flush with that edge, and the outer sixth moves it half a block that way. Only the
	 * block's own sideways and front-back directions count; when both are off-centre the larger one wins.
	 *
	 * @param hit  where the click landed, relative to the centre of the block being placed
	 * @param side the face that was clicked (the new block is on its outside)
	 */
	public static BlockState place(BlockState state, float playerYaw, Vec3d hit, Direction side, boolean towardPlayer) {
		BlockState result = state;
		if (result.contains(ANGLE)) {
			// 0 = south, counting clockwise seen from above, like a vanilla sign's rotation
			final float yaw = playerYaw + (towardPlayer ? 180F : 0F);
			final int sixteenth = Math.floorMod((int) Math.floor(yaw * 16F / 360F + 0.5F), 16);
			result = result.with(FacingShapedBlock.FACING, Direction.fromHorizontal(sixteenth / 4)).with(ANGLE, sixteenth % 4);
		}
		if (result.contains(OFFSET)) {
			final Direction facing = result.get(FacingShapedBlock.FACING);
			final Direction left = facing.rotateYClockwise();
			// the clicked face's own axis is always half a block off (the face itself), so it never counts
			final double sideways = side.getAxis() == left.getAxis() ? 0 : hit.x * left.getOffsetX() + hit.z * left.getOffsetZ();
			final double forward = side.getAxis() == facing.getAxis() ? 0 : hit.x * facing.getOffsetX() + hit.z * facing.getOffsetZ();
			final boolean bySide = Math.abs(sideways) >= Math.abs(forward);
			final double d = bySide ? sideways : forward;
			final double distance = Math.abs(d);
			final int way = (int) Math.signum(d);
			result = result.with(OFFSET, distance <= CENTRE ? Nudge.NONE : Nudge.of(bySide ? way : 0, bySide ? 0 : way, distance <= FLUSH));
		}
		return result;
	}

	/** A mirrored block turns the other way and swaps a left nudge for a right one. */
	public static BlockState mirror(BlockState mirrored, BlockState original, BlockMirror mirror) {
		if (mirror == BlockMirror.NONE) {
			return mirrored;
		}
		BlockState result = mirrored;
		final int angle = angle(original);
		if (angle != 0) {
			// facing F turned a steps clockwise mirrors to F' turned a steps anticlockwise: F' one quarter back, 4 - a steps on
			result = result.with(FacingShapedBlock.FACING, result.get(FacingShapedBlock.FACING).rotateYCounterclockwise()).with(ANGLE, 4 - angle);
		}
		if (result.contains(OFFSET)) {
			result = result.with(OFFSET, nudge(original).mirrored());
		}
		return result;
	}

	/** The outline of a free block: its square-on shape turned and nudged. */
	public static VoxelShape outline(BlockState state, VoxelShape square) {
		return moved(state) ? moved(state, square).shape : square;
	}

	/** The collision shape of a free block, moved like its outline (a flush nudge is measured on the outline). */
	public static VoxelShape collision(BlockState state, VoxelShape square) {
		if (!moved(state)) {
			return square;
		}
		final Vec3d offset = offset(state);
		final VoxelShape turned = moved(state, square).turned;
		return offset.lengthSquared() == 0 ? turned : turned.offset(offset.x, 0, offset.z);
	}

	/** Shapes depend only on the square shape (one per block and facing), the turn and the nudge, so states share them. */
	private record Key(VoxelShape square, int angle, Nudge nudge, Direction facing) {
	}

	private record Moved(VoxelShape turned, VoxelShape shape, Vec3d offset) {
	}

	private static Moved moved(BlockState state, VoxelShape square) {
		return MOVED.computeIfAbsent(new Key(square, angle(state), nudge(state), state.get(FacingShapedBlock.FACING)), FreePlacement::move);
	}

	private static Moved move(Key key) {
		final VoxelShape turned = key.angle == 0 ? key.square : turn(key.square, key.angle * STEP_DEGREES);
		final Vec3d offset = offset(turned, key.facing, key.nudge);
		final VoxelShape shape = offset.lengthSquared() == 0 ? turned : turned.offset(offset.x, 0, offset.z);
		return new Moved(turned, shape, offset);
	}

	/** The world direction of a nudge for a block facing {@code facing}, and how far it moves {@code shape}. */
	static Vec3d offset(VoxelShape shape, Direction facing, Nudge nudge) {
		if (nudge == Nudge.NONE) {
			return Vec3d.ZERO;
		}
		final Direction way = nudge.side() > 0 ? facing.rotateYClockwise() : nudge.side() < 0 ? facing.rotateYCounterclockwise()
				: nudge.depth() > 0 ? facing : facing.getOpposite();
		double amount = HALF;
		if (nudge.flush()) {
			// just far enough for the shape to touch that edge of the block space
			amount = shape.isEmpty() ? 0 : way.getDirection() == Direction.AxisDirection.POSITIVE
					? 1 - shape.getMax(way.getAxis()) : shape.getMin(way.getAxis());
			if (amount <= 0) {
				return Vec3d.ZERO;
			}
		}
		return new Vec3d(way.getOffsetX() * amount, 0, way.getOffsetZ() * amount);
	}

	/**
	 * A shape turned {@code degrees} clockwise (seen from above) about the block centre. Each box is cut into short slices
	 * along its longer side and each slice is held by its bounding box, clipped to the block space.
	 */
	static VoxelShape turn(VoxelShape square, double degrees) {
		final double radians = Math.toRadians(-degrees);
		final double cos = Math.cos(radians);
		final double sin = Math.sin(radians);
		final VoxelShape[] turned = {VoxelShapes.empty()};
		square.forEachBox((x0, y0, z0, x1, y1, z1) -> {
			final boolean alongX = x1 - x0 >= z1 - z0;
			final double start = alongX ? x0 : z0;
			final double length = alongX ? x1 - x0 : z1 - z0;
			final int slices = Math.max(1, (int) Math.ceil(length / SLICE - 1e-6));
			for (int i = 0; i < slices; i++) {
				final double a = start + length * i / slices;
				final double b = start + length * (i + 1) / slices;
				double minX = Double.MAX_VALUE;
				double minZ = Double.MAX_VALUE;
				double maxX = -Double.MAX_VALUE;
				double maxZ = -Double.MAX_VALUE;
				for (final double x : alongX ? new double[]{a, b} : new double[]{x0, x1}) {
					for (final double z : alongX ? new double[]{z0, z1} : new double[]{a, b}) {
						final double rx = 0.5 + (x - 0.5) * cos + (z - 0.5) * sin;
						final double rz = 0.5 - (x - 0.5) * sin + (z - 0.5) * cos;
						minX = Math.min(minX, rx);
						maxX = Math.max(maxX, rx);
						minZ = Math.min(minZ, rz);
						maxZ = Math.max(maxZ, rz);
					}
				}
				minX = Math.max(0, minX);
				minZ = Math.max(0, minZ);
				maxX = Math.min(1, maxX);
				maxZ = Math.min(1, maxZ);
				if (maxX > minX && maxZ > minZ && y1 > y0) {
					turned[0] = VoxelShapes.union(turned[0], VoxelShapes.cuboid(minX, y0, minZ, maxX, y1, maxZ));
				}
			}
		});
		return turned[0].simplify();
	}
}
