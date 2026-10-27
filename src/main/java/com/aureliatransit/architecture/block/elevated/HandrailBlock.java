package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.RailKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;

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

	/** Outlined like a vanilla fence, solid to the top: the drawn post is 2 px and the rails have gaps, too thin to aim at (Jah 10-26). */
	@Override
	protected VoxelShape post(RailKind kind) {
		return box(6, 0, 6, 10, 16, 10);
	}

	@Override
	protected VoxelShape arm(RailKind kind) {
		return box(7, 0, 0, 9, 16, 6);
	}
}
