package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * A single facing block that stores {@link com.aureliatransit.architecture.wayfinding.WayfindingData} and shows one
 * wayfinding panel (street blade, pictogram sign). {@link WayfindingSignBlock} and {@link EntrancePylonBlock} extend it.
 */
public class WayfindingPlateBlock extends FacingShapedBlock implements BlockEntityProvider {

	/**
	 * Set by the client initializer; opens the wayfinding editor for a block position. A no-op on dedicated servers.
	 */
	public static Consumer<BlockPos> openEditor = pos -> {
	};

	private final WayfindingPanelSpec spec;

	public WayfindingPlateBlock(Settings settings, VoxelShape northOutline, WayfindingPanelSpec spec) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
		this.spec = spec;
	}

	public WayfindingPanelSpec spec() {
		return spec;
	}

	/**
	 * Whether the block entity of this state draws the panel (joined rows: only the leftmost block).
	 */
	public boolean drawsPanel(BlockState state) {
		return true;
	}

	/**
	 * The position whose block entity holds the content a click on {@code pos} edits.
	 */
	public BlockPos editorPos(World world, BlockPos pos, BlockState state) {
		return pos;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (player.getStackInHand(hand).getItem() instanceof BlockItem || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			openEditor.accept(editorPos(world, pos, state));
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new WayfindingSignBlockEntity(pos, state);
	}
}
