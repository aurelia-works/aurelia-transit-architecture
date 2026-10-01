package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.UtilityKind;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * Cable tray or conduit run along any axis, hugging the top of its block so it reads as running under a deck.
 * Placement follows the clicked face: against a side face it runs vertically, otherwise along the player's heading.
 */
public class UtilityRunBlock extends StateShapedBlock {

	public static final EnumProperty<Direction.Axis> AXIS = Properties.AXIS;
	public static final EnumProperty<UtilityKind> KIND = EnumProperty.of("kind", UtilityKind.class);

	private static final VoxelShape ALONG_X = box(0, 11, 4, 16, 16, 12);
	private static final VoxelShape ALONG_Z = Shapes.rotate(ALONG_X, 1);
	private static final VoxelShape ALONG_Y = box(4, 0, 4, 12, 16, 12);

	public UtilityRunBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(AXIS, Direction.Axis.X).with(KIND, UtilityKind.TRAY));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(AXIS, KIND);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final Direction side = ctx.getSide();
		final Direction.Axis axis = side.getAxis().isHorizontal() ? Direction.Axis.Y : ctx.getHorizontalPlayerFacing().getAxis();
		return getDefaultState().with(AXIS, axis);
	}

	@Override
	protected VoxelShape outlineFor(BlockState state) {
		return switch (state.get(AXIS)) {
			case X -> ALONG_X;
			case Y -> ALONG_Y;
			case Z -> ALONG_Z;
		};
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		return StyleCycle.use("utility_run", KIND, state, world, pos, player, hand);
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		if ((rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) && state.get(AXIS) != Direction.Axis.Y) {
			return state.with(AXIS, state.get(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}
}
