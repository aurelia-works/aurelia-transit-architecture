package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.EdgePart;
import com.aureliatransit.architecture.block.entity.TrainEdgeBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/**
 * Platform edge with a part that moves while a train stands at the platform (1.4): drop-down barrier bars (c) or a
 * boarding step / gap filler (d). The block entity has no data and no ticker; the client renderer moves the
 * {@link EdgePart#MOVING} model, keyed to MTR's cached arrivals of the nearest platform (at most one lookup a second per
 * visible block). The moving part is visual only: collision is the edge, and the server never polls. Carries MTR's
 * platform marker through {@code MtrPlatformContract}. See docs/DESIGN_1.4.md (c, d).
 */
public class TrainEdgeBlock extends FacingShapedBlock implements BlockEntityProvider {

	public static final EnumProperty<EdgePart> PART = EnumProperty.of("part", EdgePart.class);

	/** What moves. */
	public enum Mode {
		/** Bars drop into the posts while a train stands. */
		BARRIER,
		/** A step slides out over the gap while a train stands. */
		STEP
	}

	private final Mode mode;

	public TrainEdgeBlock(Settings settings, VoxelShape northShape, Mode mode) {
		super(settings, Placement.AWAY_FROM_PLAYER, northShape);
		this.mode = mode;
		setDefaultState(getDefaultState().with(PART, EdgePart.BASE));
	}

	public Mode mode() {
		return mode;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(PART);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new TrainEdgeBlockEntity(pos, state);
	}
}
