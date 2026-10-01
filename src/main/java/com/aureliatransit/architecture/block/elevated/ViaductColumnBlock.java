package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.ColumnStyle;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * Viaduct support column: one block, four styles (heavy or narrow, steel or concrete), cycled by right-click. Columns
 * stack and meet beams directly; no end pieces are needed because every style fills its block top to bottom.
 */
public class ViaductColumnBlock extends StateShapedBlock {

	public static final EnumProperty<ColumnStyle> STYLE = EnumProperty.of("style", ColumnStyle.class);
	private static final VoxelShape STEEL_HEAVY = box(3, 0, 3, 13, 16, 13);
	private static final VoxelShape STEEL_NARROW = box(5, 0, 5, 11, 16, 11);
	private static final VoxelShape CONCRETE = box(2, 0, 2, 14, 16, 14);
	private static final VoxelShape CONCRETE_NARROW = box(4, 0, 4, 12, 16, 12);

	public ViaductColumnBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(STYLE, ColumnStyle.STEEL_HEAVY));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(STYLE);
	}

	@Override
	protected VoxelShape outlineFor(BlockState state) {
		return switch (state.get(STYLE)) {
			case STEEL_HEAVY -> STEEL_HEAVY;
			case STEEL_NARROW -> STEEL_NARROW;
			case CONCRETE -> CONCRETE;
			case CONCRETE_NARROW -> CONCRETE_NARROW;
		};
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		return StyleCycle.use("viaduct_column", STYLE, state, world, pos, player, hand);
	}
}
