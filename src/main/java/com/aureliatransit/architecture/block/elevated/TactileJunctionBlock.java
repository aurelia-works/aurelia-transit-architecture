package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.JunctionKind;
import net.minecraft.state.property.EnumProperty;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * Tactile guidance junction: turn, tee or crossing in one item. Decorative paving for mezzanines and streets (it does
 * not carry MTR's platform marker; use the platform paving where train doors must open).
 */
public class TactileJunctionBlock extends FacingKindBlock<JunctionKind> {

	public static final EnumProperty<JunctionKind> KIND = EnumProperty.of("kind", JunctionKind.class);

	public TactileJunctionBlock(Settings settings) {
		super(settings, "tactile_junction", Placement.AWAY_FROM_PLAYER, JunctionKind.class, kind -> box(0, 0, 0, 16, 16, 16));
	}

	@Override
	protected EnumProperty<JunctionKind> kindProperty() {
		return KIND;
	}
}
