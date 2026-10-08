package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

import java.util.Collections;
import java.util.Map;

/**
 * A board at the platform end that tells the driver where to stop a train of N cars. No block entity and no ticking: the
 * number is the {@code cars} block state, changed by right-clicking with an empty hand (sneak counts back down).
 * <p>
 * Aiming at the side of a block mounts the board on that wall; anywhere else it stands on its own thin post and faces the
 * player. A freestanding board is a {@code pole_mounts} sign: a sign pole joins it from above and below.
 */
public abstract class CarStopBlock extends HorizontalFacingBlock {

	public static final BooleanProperty WALL = BooleanProperty.of("wall");
	private static final int DEFAULT_CARS = 4;
	private static final String MESSAGE = "message." + AureliaTransitArchitecture.MOD_ID + ".car_stop.";

	private final Map<Direction, VoxelShape> post;
	private final Map<Direction, VoxelShape> wall;

	protected CarStopBlock(Settings settings, VoxelShape northPost, VoxelShape northWall) {
		super(settings);
		this.post = Shapes.horizontalMap(northPost);
		this.wall = Shapes.horizontalMap(northWall);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(WALL, false).with(cars(), DEFAULT_CARS));
	}

	/** The number property; a constant of the subclass (called while the state manager is built). */
	public abstract IntProperty cars();

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, WALL, cars());
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return (state.get(WALL) ? wall : post).get(state.get(FACING));
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final Direction side = ctx.getSide();
		if (side.getAxis().isHorizontal()) {
			final BlockState mounted = getDefaultState().with(WALL, true).with(FACING, side);
			if (mounted.canPlaceAt(ctx.getWorld(), ctx.getBlockPos())) {
				return mounted;
			}
		}
		return getDefaultState().with(WALL, false).with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (!state.get(WALL)) {
			return true;
		}
		final Direction facing = state.get(FACING);
		return Block.sideCoversSmallSquare(world, pos.offset(facing.getOpposite()), facing);
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (state.get(WALL) && direction == state.get(FACING).getOpposite() && !canPlaceAt(state, world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND || !player.getStackInHand(hand).isEmpty() || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			final IntProperty cars = cars();
			final int next = CarStopCycle.step(state.get(cars), Collections.min(cars.getValues()), Collections.max(cars.getValues()), player.isSneaking());
			world.setBlockState(pos, state.with(cars, next), Block.NOTIFY_ALL);
			player.sendMessage(next == 0 ? Text.translatable(MESSAGE + "stop") : Text.translatable(MESSAGE + "cars", next), true);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
