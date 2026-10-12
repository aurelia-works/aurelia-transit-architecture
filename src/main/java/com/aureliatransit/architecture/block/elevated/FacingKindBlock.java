package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A horizontally rotating block with a selectable style ("kind"): wall panels, braces, fascias, tactile junctions. The
 * kind is a block state cycled by right-clicking with an empty hand, so every look is one inventory item. Shapes are
 * authored for the north-facing model, one per kind, and rotated per facing once.
 * <p>
 * A {@linkplain #handed() handed} block (angled and curved platform pieces) also comes as a left- and a right-hand piece
 * ({@link #MIRRORED}). Its shapes are mirror images of themselves across a diagonal, so the left-hand piece facing F is the
 * right-hand piece turned to face one quarter anticlockwise of F; the blockstate file turns the same model the same way.
 */
public abstract class FacingKindBlock<K extends Enum<K> & StringIdentifiable> extends HorizontalFacingBlock {

	/** The left-hand piece: the mirror image of the north-authored (right-hand) shape. Only on {@link #handed()} blocks. */
	public static final BooleanProperty MIRRORED = BooleanProperty.of("mirrored");
	public static final String HAND_MESSAGE = "message." + com.aureliatransit.architecture.AureliaTransitArchitecture.MOD_ID + ".hand.";

	private final String id;
	private final Placement placement;
	private final Map<K, Map<Direction, VoxelShape>> shapes;

	protected FacingKindBlock(Settings settings, String id, Placement placement, Class<K> kinds, Function<K, VoxelShape> northShape) {
		super(settings);
		this.id = id;
		this.placement = placement;
		this.shapes = new EnumMap<>(kinds);
		for (final K kind : kinds.getEnumConstants()) {
			shapes.put(kind, Shapes.horizontalMap(northShape.apply(kind)));
		}
		BlockState initial = getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(kindProperty(), kinds.getEnumConstants()[0]);
		if (initial.contains(MIRRORED)) {
			initial = initial.with(MIRRORED, false);
		}
		setDefaultState(initial);
	}

	/** The block's (static) kind property; called while the state definition is built, so it must not use instance fields. */
	protected abstract EnumProperty<K> kindProperty();

	/**
	 * Whether the block comes as left- and right-hand pieces ({@link #MIRRORED}). Called while the state definition is built,
	 * so it must return a constant.
	 */
	protected boolean handed() {
		return false;
	}

	/** A kind that is its own mirror image across the facing axis, so both hands look the same (a straight piece). */
	protected boolean symmetric(K kind) {
		return false;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, kindProperty());
		if (handed()) {
			builder.add(MIRRORED);
		}
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final Direction looking = ctx.getHorizontalPlayerFacing();
		final BlockState state = getDefaultState().with(FACING, placement == Placement.TOWARD_PLAYER ? looking.getOpposite() : looking);
		if (!state.contains(MIRRORED)) {
			return state;
		}
		return state.with(MIRRORED, leftHand(looking, ctx.getHitPos().subtract(Vec3d.ofCenter(ctx.getBlockPos())), ctx.getSide()));
	}

	/**
	 * Which hand a placed piece is. Clicking the side of a block on your left or right runs the piece on from that block
	 * (a block on your right gives the left-hand piece, whose angled corner is on your left); otherwise the half of the
	 * spot you click picks the corner: the left half gives the left-hand piece.
	 *
	 * @param hit  where the click landed, relative to the centre of the block being placed
	 * @param side the face that was clicked (the new block is on its outside)
	 */
	public static boolean leftHand(Direction looking, Vec3d hit, Direction side) {
		final Direction right = looking.rotateYClockwise();
		if (side.getAxis() == right.getAxis()) {
			return side == right.getOpposite();
		}
		return hit.x * right.getOffsetX() + hit.z * right.getOffsetZ() < 0;
	}

	/** The facing whose right-hand shape this state shows. */
	protected Direction shapeFacing(BlockState state) {
		final Direction facing = state.get(FACING);
		return state.contains(MIRRORED) && state.get(MIRRORED) && !symmetric(state.get(kindProperty())) ? facing.rotateYCounterclockwise() : facing;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes.get(state.get(kindProperty())).get(shapeFacing(state));
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (state.contains(MIRRORED) && player.isSneaking()) {
			// sneak + right-click with an empty hand swaps the left- and right-hand piece
			if (hand != Hand.MAIN_HAND || !player.getStackInHand(hand).isEmpty() || !player.canModifyBlocks()) {
				return ActionResult.PASS;
			}
			if (!world.isClient) {
				final BlockState flipped = state.cycle(MIRRORED);
				world.setBlockState(pos, flipped, Block.NOTIFY_ALL);
				player.sendMessage(net.minecraft.text.Text.translatable(HAND_MESSAGE + (flipped.get(MIRRORED) ? "left" : "right")), true);
			}
			return ActionResult.success(world.isClient);
		}
		return StyleCycle.use(id, kindProperty(), state, world, pos, player, hand);
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	/** A handed piece's mirror image is the other hand facing the mirrored way; other pieces only turn. */
	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		final BlockState turned = state.rotate(mirror.getRotation(state.get(FACING)));
		return mirror != BlockMirror.NONE && turned.contains(MIRRORED) ? turned.cycle(MIRRORED) : turned;
	}
}
