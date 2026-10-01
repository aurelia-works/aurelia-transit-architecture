package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.RailKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/** Accessibility railings in one connecting block: handrail, glass balustrade and ramp edge rail. */
public class HandrailBlock extends RailingBlock<RailKind> {

	public static final EnumProperty<RailKind> KIND = EnumProperty.of("kind", RailKind.class);

	public HandrailBlock(Settings settings) {
		super(settings, "handrail", RailKind.class);
	}

	@Override
	protected EnumProperty<RailKind> kindProperty() {
		return KIND;
	}

	@Override
	protected VoxelShape post(RailKind kind) {
		return box(7, 0, 7, 9, 15, 9);
	}

	@Override
	protected VoxelShape arm(RailKind kind) {
		return switch (kind) {
			case HANDRAIL -> union(box(7, 13, 0, 9, 15, 7), box(7.5, 7, 0, 8.5, 8.5, 7));
			case BALUSTRADE -> union(box(7.25, 2, 0, 8.75, 13, 7), box(6.5, 13, 0, 9.5, 15, 7));
			case RAMP_RAIL -> union(box(6, 0, 0, 10, 2, 7), box(7, 13, 0, 9, 15, 7), box(7.5, 6, 0, 8.5, 7.5, 7));
		};
	}
}
