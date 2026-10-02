package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.LiftStatus;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShape;

/**
 * Lift status panel (A8): an editable wall sign (lift name, levels served) with an in service / out of service /
 * maintenance status set by hand in its editor. The status is a block state, so the panel is static: no ticking, no
 * polling, and MTR's lifts are never read (MTR exposes no lift status to addons we could rely on).
 */
public class LiftStatusPanelBlock extends TextSignBlock {

	public static final EnumProperty<LiftStatus> STATUS = EnumProperty.of("status", LiftStatus.class);

	public LiftStatusPanelBlock(Settings settings, TextLayout layout, VoxelShape northOutline) {
		super(settings, layout, northOutline);
		setDefaultState(getDefaultState().with(STATUS, LiftStatus.IN_SERVICE));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(STATUS);
	}
}
