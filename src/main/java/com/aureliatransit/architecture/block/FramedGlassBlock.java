package com.aureliatransit.architecture.block;

import net.minecraft.block.AbstractGlassBlock;

/**
 * Full-cube steel-framed curtain wall glass. Faces between adjacent panes are culled like vanilla glass.
 */
public class FramedGlassBlock extends AbstractGlassBlock {

	public FramedGlassBlock(Settings settings) {
		super(settings);
	}
}
