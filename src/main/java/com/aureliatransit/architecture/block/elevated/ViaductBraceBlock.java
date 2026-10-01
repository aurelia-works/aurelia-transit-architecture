package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.BraceKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/** Diagonal brace and canopy knee bracket. Rises toward the direction it faces (north-authored). */
public class ViaductBraceBlock extends FacingKindBlock<BraceKind> {

	public static final EnumProperty<BraceKind> KIND = EnumProperty.of("kind", BraceKind.class);

	public ViaductBraceBlock(Settings settings) {
		super(settings, "viaduct_brace", Placement.AWAY_FROM_PLAYER, BraceKind.class, ViaductBraceBlock::shape);
	}

	/** Eight pixel-steps along the diagonal (the visible model is a true 45 degree bar). */
	private static VoxelShape shape(BraceKind kind) {
		VoxelShape result;
		if (kind == BraceKind.DIAGONAL) {
			result = box(6, 0, 14, 10, 2, 16);
			for (int i = 0; i < 8; i++) {
				result = union(result, box(6, 2 * i, 14 - 2 * i, 10, Math.min(16, 2 * i + 4), 16 - 2 * i));
			}
			return result;
		}
		// knee: horizontal arm along the top plus a strut rising from the wall
		result = box(6, 12, 0, 10, 16, 16);
		for (int i = 0; i < 6; i++) {
			result = union(result, box(6, 2 * i, 16 - 2 * i - 2, 10, 2 * i + 4, 16 - 2 * i));
		}
		return result;
	}

	@Override
	protected EnumProperty<BraceKind> kindProperty() {
		return KIND;
	}
}
