package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.CurveKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * Platform edge for angled and curved track: a 45 degree edge, a convex or a concave quarter round. Same section as the
 * straight platform edge (walking surface 12-16 px, face set back 2 px below it) and, through
 * {@code MtrPlatformContract}, the same MTR door marker. MTR checks only the block type near a doorway, never its
 * shape, so the angled outline does not change door behaviour. Purely architectural: MTR platforms themselves are
 * defined by MTR's rails, not by these blocks.
 */
public class PlatformEdgeCurveBlock extends FacingKindBlock<CurveKind> {

	public static final EnumProperty<CurveKind> KIND = EnumProperty.of("kind", CurveKind.class);
	/** Strips per block along z; matches the generated model (tools/assets_elevated.py). */
	public static final int STRIPS = 8;

	public PlatformEdgeCurveBlock(Settings settings) {
		super(settings, "platform_edge_curve", Placement.AWAY_FROM_PLAYER, CurveKind.class, PlatformEdgeCurveBlock::shape);
	}

	static VoxelShape shape(CurveKind kind) {
		VoxelShape result = VoxelShapes.empty();
		final double step = 16.0 / STRIPS;
		for (int i = 0; i < STRIPS; i++) {
			final double z1 = i * step;
			final double reach = Math.round(kind.reach(z1 + step / 2) * 2) / 2.0;
			if (reach <= 0) {
				continue;
			}
			result = union(result, box(0, 12, z1, reach, 16, z1 + step));
			if (reach > 2) {
				result = union(result, box(0, 0, z1, reach - 2, 12, z1 + step));
			}
		}
		return result;
	}

	@Override
	protected EnumProperty<CurveKind> kindProperty() {
		return KIND;
	}
}
