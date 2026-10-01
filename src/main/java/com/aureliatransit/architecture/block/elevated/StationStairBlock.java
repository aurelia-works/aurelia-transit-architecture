package com.aureliatransit.architecture.block.elevated;

import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;

/** Steel-pan stair for elevated stair towers; behaves like any stair (facing, half, inner/outer corners). */
public class StationStairBlock extends StairsBlock {

	public StationStairBlock(Settings settings) {
		super(Blocks.IRON_BLOCK.getDefaultState(), settings);
	}
}
