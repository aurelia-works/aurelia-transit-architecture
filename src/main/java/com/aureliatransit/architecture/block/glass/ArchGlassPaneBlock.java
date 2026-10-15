package com.aureliatransit.architecture.block.glass;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

/**
 * Connecting architectural glass pane with vanilla pane behaviour (connects to panes, walls and solid sides), and also to the
 * station's roof and frame blocks in {@link #JOINS} (canopies, columns, girders, piers, roof panels, stairs), which are not
 * full blocks: glazing set against them meets them instead of ending in a loose post.
 */
public class ArchGlassPaneBlock extends PaneBlock {

	/** Blocks the glass joins although they have no solid full side. */
	public static final TagKey<Block> JOINS = TagKey.of(RegistryKeys.BLOCK, AureliaTransitArchitecture.id("glass_pane_joins"));

	public ArchGlassPaneBlock(Settings settings) {
		super(settings);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState state = super.getPlacementState(ctx);
		for (final Direction side : Direction.Type.HORIZONTAL) {
			if (ctx.getWorld().getBlockState(ctx.getBlockPos().offset(side)).isIn(JOINS)) {
				state = state.with(HorizontalConnectingBlock.FACING_PROPERTIES.get(side), true);
			}
		}
		return state;
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos) {
		final BlockState updated = super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
		if (direction.getAxis().isHorizontal() && neighborState.isIn(JOINS)) {
			return updated.with(HorizontalConnectingBlock.FACING_PROPERTIES.get(direction), true);
		}
		return updated;
	}
}
