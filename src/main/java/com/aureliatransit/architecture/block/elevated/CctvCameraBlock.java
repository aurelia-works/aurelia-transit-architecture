package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.CctvKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShapes;

import static net.minecraft.block.Block.createCuboidShape;

/** CCTV camera housing (A10). A prop: no camera function. Faces the way it was placed (toward the player). */
public class CctvCameraBlock extends FacingKindBlock<CctvKind> {

	public static final EnumProperty<CctvKind> KIND = EnumProperty.of("kind", CctvKind.class);

	public CctvCameraBlock(Settings settings) {
		super(settings, "cctv_camera", Placement.TOWARD_PLAYER, CctvKind.class, kind -> switch (kind) {
			case WALL -> VoxelShapes.union(createCuboidShape(7, 8, 12, 9, 10, 16), createCuboidShape(5.5, 9, 4, 10.5, 13, 13));
			case PENDANT -> VoxelShapes.union(createCuboidShape(7, 12, 7, 9, 16, 9), createCuboidShape(5.5, 8, 3, 10.5, 12, 12));
			case DOME -> createCuboidShape(4, 11, 4, 12, 16, 12);
		});
	}

	@Override
	protected EnumProperty<CctvKind> kindProperty() {
		return KIND;
	}
}
