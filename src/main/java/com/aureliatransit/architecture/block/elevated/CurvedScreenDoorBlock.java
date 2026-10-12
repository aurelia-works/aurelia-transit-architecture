package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.ScreenCurveKind;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import static net.minecraft.block.Block.createCuboidShape;

/**
 * Platform screen door piece (1.4) that follows ATA's straight, 45 degree, convex and concave platform edges. Static:
 * a fixed screen, or an always-open doorway (header and posts only). The doorway carries MTR's platform marker
 * through {@code MtrPlatformContract}, so train doors open beside it; there is no moving leaf, because MTR exposes no
 * door state to addons and a moving door would need per-tick work. See docs/DESIGN_1.4.md (b).
 * <p>
 * Curved pieces come as left- and right-hand pieces like the edges; placed on a curved platform edge, a screen takes
 * that edge's facing, hand and curve, so it follows the edge line.
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

	@Override
	protected boolean handed() {
		return true;
	}

	@Override
	protected boolean symmetric(ScreenCurveKind kind) {
		return kind == ScreenCurveKind.STRAIGHT;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		final BlockState below = ctx.getWorld().getBlockState(ctx.getBlockPos().down());
		return state == null || !(below.getBlock() instanceof PlatformEdgeCurveBlock) ? state : onEdge(state, below);
	}

	/** A screen standing on a curved edge piece: the same facing, hand and curve. */
	public static BlockState onEdge(BlockState screen, BlockState edge) {
		ScreenCurveKind kind = screen.get(KIND);
		for (final ScreenCurveKind candidate : ScreenCurveKind.values()) {
			if (candidate.curve() == edge.get(PlatformEdgeCurveBlock.KIND)) {
				kind = candidate;
			}
		}
		return screen.with(FACING, edge.get(FACING)).with(MIRRORED, edge.get(MIRRORED)).with(KIND, kind);
	}
}
