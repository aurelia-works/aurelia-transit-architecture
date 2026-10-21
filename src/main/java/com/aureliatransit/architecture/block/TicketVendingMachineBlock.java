package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.mtr.MtrFareContract;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
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
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * ATA's ticket vending machine, two blocks tall like a real one (lower/upper {@link #HALF}, handled like a vanilla door):
 * the ledge, card reader and ticket tray below, the touch screen and lit header above, each half with its own hitbox so
 * the whole machine can be clicked. Using either half opens MTR's own ticket machine screen, so it sells the same tickets
 * and tops up the same balance as MTR's machine; without MTR fares it is a prop. No block entity, no ticking.
 */
public class TicketVendingMachineBlock extends FacingShapedBlock {

	public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;

	private final Map<Direction, VoxelShape> upperShapes;

	/**
	 * @param northLower the lower half's outline and collision facing north
	 * @param northUpper the upper half's
	 */
	public TicketVendingMachineBlock(Settings settings, VoxelShape northLower, VoxelShape northUpper) {
		super(settings, Placement.TOWARD_PLAYER, northLower);
		this.upperShapes = Shapes.horizontalMap(northUpper);
		setDefaultState(getDefaultState().with(HALF, DoubleBlockHalf.LOWER));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(HALF);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockPos pos = ctx.getBlockPos();
		final World world = ctx.getWorld();
		if (pos.getY() >= world.getTopY() - 1 || !world.getBlockState(pos.up()).canReplace(ctx)) {
			return null;
		}
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : state.with(HALF, DoubleBlockHalf.LOWER);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
	}

	@Override
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (state.get(HALF) == DoubleBlockHalf.UPPER) {
			final BlockState below = world.getBlockState(pos.down());
			return below.isOf(this) && below.get(HALF) == DoubleBlockHalf.LOWER;
		}
		return true;
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
			BlockPos pos, BlockPos neighborPos) {
		final DoubleBlockHalf half = state.get(HALF);
		if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
			return neighborState.isOf(this) && neighborState.get(HALF) != half ? state : Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && player.isCreative() && state.get(HALF) == DoubleBlockHalf.UPPER) {
			// creative: take the lower half without a drop (the loot table drops only from the lower half)
			final BlockPos below = pos.down();
			final BlockState belowState = world.getBlockState(below);
			if (belowState.isOf(this) && belowState.get(HALF) == DoubleBlockHalf.LOWER) {
				world.setBlockState(below, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
				world.syncWorldEvent(player, WorldEvents.BLOCK_BROKEN, below, Block.getRawIdFromState(belowState));
			}
		}
		super.onBreak(world, pos, state, player);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(HALF) == DoubleBlockHalf.UPPER ? upperShapes.get(state.get(FACING)) : super.getOutlineShape(state, world, pos, context);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(HALF) == DoubleBlockHalf.UPPER ? upperShapes.get(state.get(FACING)) : super.getCollisionShape(state, world, pos, context);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (player.getStackInHand(hand).getItem() instanceof BlockItem || !MtrFareContract.available()) {
			return ActionResult.PASS;
		}
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			MtrFareContract.openTicketMachine(world, serverPlayer);
		}
		return ActionResult.success(world.isClient);
	}
}
