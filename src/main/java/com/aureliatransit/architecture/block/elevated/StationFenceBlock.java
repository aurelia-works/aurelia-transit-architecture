package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.FenceKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/** Platform fencing (rails and pickets) and trackside safety mesh in one connecting block. */
public class StationFenceBlock extends RailingBlock<FenceKind> {

	public static final EnumProperty<FenceKind> KIND = EnumProperty.of("kind", FenceKind.class);

	public StationFenceBlock(Settings settings) {
		super(settings, "station_fence", FenceKind.class);
	}

	@Override
	protected EnumProperty<FenceKind> kindProperty() {
		return KIND;
	}

	@Override
	protected VoxelShape post(FenceKind kind) {
		return kind == FenceKind.PLATFORM ? box(6.5, 0, 6.5, 9.5, 15, 9.5) : box(7, 0, 7, 9, 16, 9);
	}

	@Override
	protected VoxelShape arm(FenceKind kind) {
		return kind == FenceKind.PLATFORM
				? union(box(7.25, 12, 0, 8.75, 14, 6.5), box(7.25, 6, 0, 8.75, 8, 6.5))
				: union(box(7.75, 1, 0, 8.25, 15, 7), box(7, 15, 0, 9, 16, 7));
	}
}
