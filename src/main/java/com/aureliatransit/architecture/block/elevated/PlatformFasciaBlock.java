package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.FasciaKind;
import net.minecraft.state.property.EnumProperty;

import static com.aureliatransit.architecture.util.Shapes.box;

/** Under-platform fascia: the finished vertical face below a platform or deck edge. */
public class PlatformFasciaBlock extends FacingKindBlock<FasciaKind> {

	public static final EnumProperty<FasciaKind> KIND = EnumProperty.of("kind", FasciaKind.class);

	public PlatformFasciaBlock(Settings settings) {
		super(settings, "platform_fascia", Placement.AWAY_FROM_PLAYER, FasciaKind.class, kind -> box(0, 0, 0, 16, 16, 3));
	}

	@Override
	protected EnumProperty<FasciaKind> kindProperty() {
		return KIND;
	}
}
