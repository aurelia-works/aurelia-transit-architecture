package com.aureliatransit.architecture.block.mtr;

import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;
import org.mtr.mod.block.PlatformHelper;

/**
 * The block classes that carry MTR's platform marker. Only reached through {@link MtrPlatformContract} once it has
 * confirmed the marker exists, so a missing interface never breaks class loading.
 */
final class MtrPlatformBlocks {

	private MtrPlatformBlocks() {
	}

	static Block surface(AbstractBlock.Settings settings) {
		return new Surface(settings);
	}

	static Block edge(AbstractBlock.Settings settings, Placement placement, VoxelShape northShape) {
		return new Edge(settings, placement, northShape);
	}

	static Block curve(AbstractBlock.Settings settings) {
		return new Curve(settings);
	}

	private static final class Curve extends com.aureliatransit.architecture.block.elevated.PlatformEdgeCurveBlock implements PlatformHelper {
		Curve(Settings settings) {
			super(settings);
		}
	}

	private static final class Surface extends Block implements PlatformHelper {
		Surface(Settings settings) {
			super(settings);
		}
	}

	private static final class Edge extends FacingShapedBlock implements PlatformHelper {
		Edge(Settings settings, Placement placement, VoxelShape northShape) {
			super(settings, placement, northShape);
		}
	}
}
