package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.live.PidsBlock;
import com.aureliatransit.architecture.registry.WayfindingBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

import java.util.EnumMap;
import java.util.Map;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * A thin rod from the roof that screens and signs hang from. Over a hanging screen or sign (or over a rod that is) it splits
 * into hangers ({@link #HANGER}) that land on the stubs on the block's top, turned with the screen ({@link #FACING}): both
 * stubs on a one-block screen or a sign, only the outer one on the end block of a wide screen (its middle blocks have none;
 * Jah 10-20: the rod has to meet the little stub at the screen's end). Worked out from the block below on placement and
 * neighbour updates only; nothing ticks.
 */
public class DropRodBlock extends Block {

	/** Which of a screen block's two top stubs the rod lands on, in the screen's model: low at x 3-4, high at x 12-13. */
	public enum Hanger implements StringIdentifiable {
		NONE, BOTH, LOW, HIGH;

		@Override
		public String asString() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** The hangers over a screen block with the given stubs. */
		public static Hanger of(boolean low, boolean high) {
			return low && high ? BOTH : low ? LOW : high ? HIGH : NONE;
		}
	}

	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<Hanger> HANGER = EnumProperty.of("hanger", Hanger.class);

	private static final VoxelShape ROD = box(7, 0, 7, 9, 16, 9);
	/** The hangers' outlines by facing and kind, a little wider than the hangers so they are easy to hit. */
	private static final Map<Direction, Map<Hanger, VoxelShape>> HANGERS = new EnumMap<>(Direction.class);

	static {
		for (final Direction facing : Direction.Type.HORIZONTAL) {
			final Map<Hanger, VoxelShape> shapes = new EnumMap<>(Hanger.class);
			final VoxelShape low = turned(facing, 2.5, 4.5);
			final VoxelShape high = turned(facing, 11.5, 13.5);
			shapes.put(Hanger.NONE, ROD);
			shapes.put(Hanger.BOTH, VoxelShapes.union(low, high));
			shapes.put(Hanger.LOW, low);
			shapes.put(Hanger.HIGH, high);
			HANGERS.put(facing, shapes);
		}
	}

	/** A hanger from model x {@code a} to {@code b} (z 7 to 9) turned like a block model for {@code facing} (north unturned). */
	private static VoxelShape turned(Direction facing, double a, double b) {
		return switch (facing) {
			case EAST -> box(7, 0, a, 9, 16, b);
			case SOUTH -> box(16 - b, 0, 7, 16 - a, 16, 9);
			case WEST -> box(7, 0, 16 - b, 9, 16, 16 - a);
			default -> box(a, 0, 7, b, 16, 9);
		};
	}

	public DropRodBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(HANGER, Hanger.NONE));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, HANGER);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		// faces the player like any fitting; a screen or sign below turns it to its own facing
		return fromBelow(getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()),
				ctx.getWorld().getBlockState(ctx.getBlockPos().down()));
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
			BlockPos pos, BlockPos neighborPos) {
		return direction == Direction.DOWN ? fromBelow(state, neighborState) : state;
	}

	/** Hangers onto a hanging screen's or sign's stubs, or as a rod below has them; a plain rod over anything else. */
	BlockState fromBelow(BlockState state, BlockState below) {
		if (below.isOf(this)) {
			return state.with(FACING, below.get(FACING)).with(HANGER, below.get(HANGER));
		}
		if (!below.contains(Properties.HORIZONTAL_FACING)) {
			return state.with(HANGER, Hanger.NONE);
		}
		final Direction facing = below.get(Properties.HORIZONTAL_FACING);
		// a hanging screen starts below its block's top, where its stubs are: none over a block joined on that side
		if (below.getBlock() instanceof PidsBlock pids && pids.topInsetPixels() > 0) {
			final boolean low = !below.get(PidsBlock.RIGHT);
			final boolean high = !below.get(PidsBlock.LEFT);
			return state.with(FACING, facing).with(HANGER, Hanger.of(low, high));
		}
		// a hanging sign's stubs are where a one-block screen's are
		if (below.isOf(WayfindingBlocks.HANGING_WAYFINDING_SIGN)) {
			return state.with(FACING, facing).with(HANGER, Hanger.BOTH);
		}
		return state.with(HANGER, Hanger.NONE);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return HANGERS.get(state.get(FACING)).get(state.get(HANGER));
	}
}
