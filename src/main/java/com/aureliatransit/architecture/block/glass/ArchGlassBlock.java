package com.aureliatransit.architecture.block.glass;

import net.minecraft.block.AbstractGlassBlock;

/**
 * Full-cube architectural glass. Faces between adjacent blocks of the same type are culled like vanilla glass.
 */
public class ArchGlassBlock extends AbstractGlassBlock {

	public ArchGlassBlock(Settings settings) {
		super(settings);
	}
}
