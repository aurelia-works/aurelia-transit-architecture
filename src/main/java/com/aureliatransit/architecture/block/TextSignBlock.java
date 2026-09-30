package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
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
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.function.Consumer;

/**
 * A sign with editable text. Joining signs track neighbours on their local left (-X) and right (+X) so that a row of
 * identical signs renders as one continuous panel; the leftmost (-X) block of a row owns the text.
 */
public class TextSignBlock extends FacingShapedBlock implements BlockEntityProvider {

	/**
	 * Connected to an identical sign on the model's -X side.
	 */
	public static final BooleanProperty LEFT = BooleanProperty.of("left");
	/**
	 * Connected to an identical sign on the model's +X side.
	 */
	public static final BooleanProperty RIGHT = BooleanProperty.of("right");
	public static final int MAX_ROW = 32;

	/**
	 * Set by the client initializer; opens the text editor for a sign position. A no-op on dedicated servers.
	 */
	public static Consumer<BlockPos> openEditor = pos -> {
	};

	private final TextLayout layout;

	public TextSignBlock(Settings settings, TextLayout layout, VoxelShape northOutline) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
		this.layout = layout;
		setDefaultState(getDefaultState().with(LEFT, false).with(RIGHT, false));
	}

	public TextLayout getLayout() {
		return layout;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(LEFT, RIGHT);
	}

	public static Direction localLeft(Direction facing) {
		return facing.rotateYCounterclockwise();
	}

	public static Direction localRight(Direction facing) {
		return facing.rotateYClockwise();
	}

	private boolean joinsWith(BlockState self, BlockState other) {
		return layout.joins() && other.isOf(this) && other.get(FACING) == self.get(FACING);
	}

	private BlockState withConnections(BlockState state, WorldAccess world, BlockPos pos) {
		final Direction facing = state.get(FACING);
		return state
				.with(LEFT, joinsWith(state, world.getBlockState(pos.offset(localLeft(facing)))))
				.with(RIGHT, joinsWith(state, world.getBlockState(pos.offset(localRight(facing)))));
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : withConnections(state, ctx.getWorld(), ctx.getBlockPos());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction.getAxis().isHorizontal()) {
			return withConnections(state, world, pos);
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	/**
	 * Finds the block that owns the text for the row containing {@code pos}.
	 */
	public BlockPos findRowOwner(World world, BlockPos pos, BlockState state) {
		final Direction left = localLeft(state.get(FACING));
		BlockPos current = pos;
		BlockState currentState = state;
		for (int i = 0; i < MAX_ROW && currentState.isOf(this) && currentState.get(LEFT); i++) {
			final BlockPos next = current.offset(left);
			final BlockState nextState = world.getBlockState(next);
			if (!joinsWith(currentState, nextState)) {
				break;
			}
			current = next;
			currentState = nextState;
		}
		return current;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (player.getStackInHand(hand).getItem() instanceof BlockItem || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			openEditor.accept(findRowOwner(world, pos, state));
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new TextSignBlockEntity(pos, state);
	}
}
