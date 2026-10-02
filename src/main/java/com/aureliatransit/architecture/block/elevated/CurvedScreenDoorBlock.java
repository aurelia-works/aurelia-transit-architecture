package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.ScreenCurveKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import static net.minecraft.block.Block.createCuboidShape;

/**
 * Platform screen door piece (1.4) that follows ATA's straight, 45 degree, convex and concave platform edges. Static:
 * a fixed screen, or an always-open doorway (header and posts only). The doorway carries MTR's platform marker
 * through {@code MtrPlatformContract}, so train doors open beside it; there is no moving leaf, because MTR exposes no
 * door state to addons and a moving door would need per-tick work. See docs/DESIGN_1.4.md (b).
 */
public class CurvedScreenDoorBlock extends FacingKindBlock<ScreenCurveKind> {

	public static final EnumProperty<ScreenCurveKind> KIND = EnumProperty.of("kind", ScreenCurveKind.class);

	public CurvedScreenDoorBlock(Settings settings, boolean doorway) {
		super(settings, doorway ? "screen_door_doorway" : "screen_door_panel", Placement.AWAY_FROM_PLAYER, ScreenCurveKind.class, kind -> shape(kind, doorway));
	}

	static VoxelShape shape(ScreenCurveKind kind, boolean doorway) {
		VoxelShape result = VoxelShapes.empty();
		for (final double[] b : ScreenDoorGeometry.boxes(kind, doorway)) {
			result = VoxelShapes.union(result, createCuboidShape(b[0], b[1], b[2], b[3], b[4], b[5]));
		}
		return result.simplify();
	}

	@Override
	protected EnumProperty<ScreenCurveKind> kindProperty() {
		return KIND;
	}
}
