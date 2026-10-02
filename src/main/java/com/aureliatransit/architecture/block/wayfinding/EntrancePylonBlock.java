package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Freestanding block two blocks tall (lower/upper {@code half}, handled like a vanilla door): the station-entrance
 * totem and the passenger information kiosk. Only the lower half has a block entity and draws the tall panel; the upper
 * half forwards clicks to it.
 */
public class EntrancePylonBlock extends WayfindingPlateBlock {

	public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;

	public EntrancePylonBlock(Settings settings, VoxelShape northOutline, WayfindingPanelSpec spec) {
		super(settings, northOutline, spec);
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
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		final DoubleBlockHalf half = state.get(HALF);
		if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
			return neighborState.isOf(this) && neighborState.get(HALF) != half ? state : Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && player.isCreative() && state.get(HALF) == DoubleBlockHalf.UPPER) {
			// Creative: take the lower half without a drop (the loot table drops only from the lower half).
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
	public BlockPos editorPos(World world, BlockPos pos, BlockState state) {
		return state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
	}

	@Override
	public boolean drawsPanel(BlockState state) {
		return state.get(HALF) == DoubleBlockHalf.LOWER;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(HALF) == DoubleBlockHalf.LOWER ? new WayfindingSignBlockEntity(pos, state) : null;
	}
}
