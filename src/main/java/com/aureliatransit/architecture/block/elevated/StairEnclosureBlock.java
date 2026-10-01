package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.EnclosureKind;
import net.minecraft.state.property.EnumProperty;

import static com.aureliatransit.architecture.util.Shapes.box;

/** Wall panel of an enclosed stair tower: solid cladding, a windowed panel or a fully glazed one. */
public class StairEnclosureBlock extends FacingKindBlock<EnclosureKind> {

	public static final EnumProperty<EnclosureKind> KIND = EnumProperty.of("kind", EnclosureKind.class);

	public StairEnclosureBlock(Settings settings) {
		super(settings, "stair_enclosure", Placement.AWAY_FROM_PLAYER, EnclosureKind.class, kind -> box(0, 0, 0, 16, 16, 2));
	}

	@Override
	protected EnumProperty<EnclosureKind> kindProperty() {
		return KIND;
	}
}
