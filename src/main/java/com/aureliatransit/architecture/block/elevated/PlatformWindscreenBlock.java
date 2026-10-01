package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.WindscreenKind;
import net.minecraft.state.property.EnumProperty;

import static com.aureliatransit.architecture.util.Shapes.box;

/** Elevated platform wind screen: a lower screen with kick plate, and an upper screen that stacks on it. */
public class PlatformWindscreenBlock extends FacingKindBlock<WindscreenKind> {

	public static final EnumProperty<WindscreenKind> KIND = EnumProperty.of("kind", WindscreenKind.class);

	public PlatformWindscreenBlock(Settings settings) {
		super(settings, "platform_windscreen", Placement.AWAY_FROM_PLAYER, WindscreenKind.class, kind -> box(0, 0, 0, 16, 16, 1.5));
	}

	@Override
	protected EnumProperty<WindscreenKind> kindProperty() {
		return KIND;
	}
}
