package com.aureliatransit.architecture.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.util.math.Direction;

/**
 * A queue post with a belt (Null, 10-26): it joins the queue posts beside it the way a fence joins fences, with a strap between the
 * post heads, and joins nothing else. Static: no block entity, nothing ticks.
 */
public class QueuePostBlock extends FenceBlock {

	public QueuePostBlock(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canConnect(BlockState state, boolean neighborIsFullSquare, Direction dir) {
		return state.isOf(this);
	}
}
