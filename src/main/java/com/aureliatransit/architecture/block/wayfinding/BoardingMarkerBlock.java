package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * A paper-thin floor plate marking where to board. The marker type is a block state, so it needs no block entity and no
 * renderer; it has no collision, so players walk straight over it. The arrows point the way you were looking.
 */
public class BoardingMarkerBlock extends FacingShapedBlock {

	public static final EnumProperty<BoardingMarkerType> MARKER = EnumProperty.of("marker", BoardingMarkerType.class);
	private static final String MESSAGE = "message." + AureliaTransitArchitecture.MOD_ID + ".boarding_marker.";

	public BoardingMarkerBlock(Settings settings, VoxelShape northOutline) {
		super(settings, Placement.AWAY_FROM_PLAYER, northOutline, VoxelShapes.empty());
		setDefaultState(getDefaultState().with(MARKER, BoardingMarkerType.DOOR));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(MARKER);
	}

	@Override
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return Block.sideCoversSmallSquare(world, pos.down(), Direction.UP);
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !canPlaceAt(state, world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND || player.getStackInHand(hand).getItem() instanceof BlockItem || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			final BlockState next = state.with(MARKER, state.get(MARKER).next());
			world.setBlockState(pos, next, Block.NOTIFY_ALL);
			player.sendMessage(Text.translatable(MESSAGE + next.get(MARKER).asString()), true);
		}
		return ActionResult.success(world.isClient);
	}
}
