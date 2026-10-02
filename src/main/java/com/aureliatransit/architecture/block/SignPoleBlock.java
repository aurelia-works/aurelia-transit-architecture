package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.registry.WayfindingBlocks;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

import java.util.Locale;
import java.util.Map;

/**
 * Sign pole that joins the sign it carries.
 * <ul>
 *     <li>A sign from the {@code pole_mounts} tag directly above ({@code up}) or hanging below ({@code down}): the model
 *     continues the centred pole into that block until the sign panel hides it.</li>
 *     <li>A street sign directly above ({@code align}): the street sign has its own post beside the blade, off centre, so
 *     the pole moves under that post instead, and so does every pole stacked below it. One continuous post, no second
 *     pole through the blade.</li>
 * </ul>
 * Outline and collision follow the drawn pole; the extensions into the sign's block are drawn only.
 */
public class SignPoleBlock extends Block {

	public static final BooleanProperty UP = Properties.UP;
	public static final BooleanProperty DOWN = Properties.DOWN;
	public static final EnumProperty<Align> ALIGN = EnumProperty.of("align", Align.class);
	public static final TagKey<Block> POLE_MOUNTS = TagKey.of(RegistryKeys.BLOCK, AureliaTransitArchitecture.id("pole_mounts"));

	private static final VoxelShape CENTRE = Block.createCuboidShape(7, 0, 7, 9, 16, 9);
	/** Under the post of a north-facing street sign (x 1..3); rotated with the sign's facing. */
	private static final Map<Direction, VoxelShape> UNDER_POST = Shapes.horizontalMap(Block.createCuboidShape(1, 0, 7, 3, 16, 9));

	public SignPoleBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(UP, false).with(DOWN, false).with(ALIGN, Align.CENTRE));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(UP, DOWN, ALIGN);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		final Direction post = state.get(ALIGN).facing();
		return post == null ? CENTRE : UNDER_POST.get(post);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockPos pos = ctx.getBlockPos();
		return withAbove(withBelow(getDefaultState(), ctx.getWorld().getBlockState(pos.down())), ctx.getWorld().getBlockState(pos.up()));
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.UP) {
			return withAbove(state, neighborState);
		}
		if (direction == Direction.DOWN) {
			return withBelow(state, neighborState);
		}
		return state;
	}

	private BlockState withAbove(BlockState state, BlockState above) {
		final Align align;
		if (above.isOf(WayfindingBlocks.STREET_SIGN)) {
			align = Align.of(above.get(HorizontalFacingBlock.FACING));
		} else if (above.isOf(this)) {
			align = above.get(ALIGN);
		} else {
			align = Align.CENTRE;
		}
		return state.with(ALIGN, align).with(UP, align == Align.CENTRE && above.isIn(POLE_MOUNTS));
	}

	private static BlockState withBelow(BlockState state, BlockState below) {
		return state.with(DOWN, below.isIn(POLE_MOUNTS));
	}

	/** Where the pole stands: centred, or under the post of a street sign facing that way. */
	public enum Align implements StringIdentifiable {
		CENTRE, NORTH, EAST, SOUTH, WEST;

		static Align of(Direction facing) {
			return switch (facing) {
				case EAST -> EAST;
				case SOUTH -> SOUTH;
				case WEST -> WEST;
				default -> NORTH;
			};
		}

		Direction facing() {
			return switch (this) {
				case CENTRE -> null;
				case NORTH -> Direction.NORTH;
				case EAST -> Direction.EAST;
				case SOUTH -> Direction.SOUTH;
				case WEST -> Direction.WEST;
			};
		}

		@Override
		public String asString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}
}
