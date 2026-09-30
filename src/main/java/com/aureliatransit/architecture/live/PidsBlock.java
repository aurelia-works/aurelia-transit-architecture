package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.function.Consumer;

/**
 * A live passenger-information display (CIS, PIDS or concourse board). Identical displays with the same facing that
 * touch each other join into one larger screen. Connection flags are from the <b>viewer's</b> point of view; the
 * top-left block (no left and no up neighbour) is the owner that stores the config and draws the whole screen.
 */
public class PidsBlock extends FacingShapedBlock implements BlockEntityProvider {

	public static final BooleanProperty LEFT = BooleanProperty.of("left");
	public static final BooleanProperty RIGHT = BooleanProperty.of("right");
	public static final BooleanProperty UP = BooleanProperty.of("up");
	public static final BooleanProperty DOWN = BooleanProperty.of("down");
	public static final int MAX_WIDTH = 16;
	public static final int MAX_HEIGHT = 8;

	/**
	 * Set by the client initializer; opens the configuration screen for a display position. No-op on dedicated servers.
	 */
	public static Consumer<BlockPos> openEditor = pos -> {
	};

	private final DisplayKind kind;
	private final boolean doubleSided;
	private final float frontZ;
	private final float topInset;

	/**
	 * @param frontZ   z in model pixels of the screen surface on the north (front) face
	 * @param topInset pixels the screen starts below the top of the block (hanging rods above it)
	 */
	public PidsBlock(Settings settings, DisplayKind kind, boolean doubleSided, float frontZ, float topInset, VoxelShape northOutline) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
		this.kind = kind;
		this.doubleSided = doubleSided;
		this.frontZ = frontZ;
		this.topInset = topInset;
		setDefaultState(getDefaultState().with(LEFT, false).with(RIGHT, false).with(UP, false).with(DOWN, false));
	}

	public DisplayKind kind() {
		return kind;
	}

	public boolean doubleSided() {
		return doubleSided;
	}

	public float frontZPixels() {
		return frontZ;
	}

	public float topInsetPixels() {
		return topInset;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(LEFT, RIGHT, UP, DOWN);
	}

	/**
	 * World direction of the viewer's left when looking at the front of a block with this facing.
	 */
	public static Direction viewerLeft(Direction facing) {
		return facing.rotateYClockwise();
	}

	public static Direction viewerRight(Direction facing) {
		return facing.rotateYCounterclockwise();
	}

	private boolean joinsWith(BlockState self, BlockState other) {
		return other.isOf(this) && other.get(FACING) == self.get(FACING);
	}

	private BlockState withConnections(BlockState state, WorldAccess world, BlockPos pos) {
		final Direction facing = state.get(FACING);
		return state
				.with(LEFT, joinsWith(state, world.getBlockState(pos.offset(viewerLeft(facing)))))
				.with(RIGHT, joinsWith(state, world.getBlockState(pos.offset(viewerRight(facing)))))
				.with(UP, joinsWith(state, world.getBlockState(pos.up())))
				.with(DOWN, joinsWith(state, world.getBlockState(pos.down())));
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : withConnections(state, ctx.getWorld(), ctx.getBlockPos());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		return withConnections(state, world, pos);
	}

	/**
	 * The top-left block of the screen containing {@code pos} (walks at most {@link #MAX_WIDTH}/{@link #MAX_HEIGHT} blocks).
	 */
	public BlockPos findOwner(BlockView world, BlockPos pos, BlockState state) {
		BlockPos current = pos;
		BlockState currentState = state;
		final Direction left = viewerLeft(state.get(FACING));
		for (int i = 0; i < MAX_WIDTH && currentState.isOf(this) && currentState.get(LEFT); i++) {
			current = current.offset(left);
			currentState = world.getBlockState(current);
		}
		for (int i = 0; i < MAX_HEIGHT && currentState.isOf(this) && currentState.get(UP); i++) {
			current = current.up();
			currentState = world.getBlockState(current);
		}
		return current;
	}

	/**
	 * Width in blocks of the screen whose owner is {@code owner}.
	 */
	public int width(BlockView world, BlockPos owner, BlockState ownerState) {
		final Direction right = viewerRight(ownerState.get(FACING));
		int width = 1;
		BlockPos current = owner;
		BlockState currentState = ownerState;
		while (width < MAX_WIDTH && currentState.get(RIGHT)) {
			current = current.offset(right);
			currentState = world.getBlockState(current);
			if (!currentState.isOf(this) || currentState.get(FACING) != ownerState.get(FACING)) {
				break;
			}
			width++;
		}
		return width;
	}

	/**
	 * Height in blocks of the screen whose owner is {@code owner}.
	 */
	public int height(BlockView world, BlockPos owner, BlockState ownerState) {
		int height = 1;
		BlockPos current = owner;
		BlockState currentState = ownerState;
		while (height < MAX_HEIGHT && currentState.get(DOWN)) {
			current = current.down();
			currentState = world.getBlockState(current);
			if (!currentState.isOf(this) || currentState.get(FACING) != ownerState.get(FACING)) {
				break;
			}
			height++;
		}
		return height;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (player.getStackInHand(hand).getItem() instanceof BlockItem || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			openEditor.accept(findOwner(world, pos, state));
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PidsBlockEntity(pos, state);
	}
}
