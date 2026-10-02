package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.NoiseBarrierKind;
import net.minecraft.state.property.EnumProperty;

import static net.minecraft.block.Block.createCuboidShape;

/** Trackside noise barrier panel (A12): a 2 px panel on the far side of the block, full or half height. */
public class NoiseBarrierBlock extends FacingKindBlock<NoiseBarrierKind> {

	public static final EnumProperty<NoiseBarrierKind> KIND = EnumProperty.of("kind", NoiseBarrierKind.class);

	public NoiseBarrierBlock(Settings settings) {
		super(settings, "noise_barrier", Placement.AWAY_FROM_PLAYER, NoiseBarrierKind.class,
				kind -> createCuboidShape(0, 0, 0, 16, kind.half() ? 8 : 16, 2));
	}

	@Override
	protected EnumProperty<NoiseBarrierKind> kindProperty() {
		return KIND;
	}
}
