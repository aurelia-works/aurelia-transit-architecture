package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.FreePlacement;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * Wall-mounted help point: a lit unit with a help symbol. Right-click with an empty hand and it says where you are
 * ("This is Central, platform 3.") through the announcement voice (client side, from MTR's nearest platform); with an
 * item in hand, or away from any station, it shows a short action-bar line. There is no network or telephony behind it.
 */
public class HelpPointBlock extends FacingShapedBlock {

	public static final String MESSAGE = "message." + AureliaTransitArchitecture.MOD_ID + ".help_point";
	/**
	 * Installed by the client: speaks the station and platform at a help point (falls back to {@link #MESSAGE}).
	 */
	public static Consumer<BlockPos> speak;

	public HelpPointBlock(Settings settings, VoxelShape northOutline) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		FreePlacement.addTurn(builder);
		FreePlacement.addNudge(builder);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND || player.getStackInHand(hand).getItem() instanceof BlockItem) {
			return ActionResult.PASS;
		}
		if (player.getStackInHand(hand).isEmpty()) {
			if (world.isClient && speak != null) {
				speak.accept(pos);
			} else if (world.isClient) {
				player.sendMessage(Text.translatable(MESSAGE), true);
			}
		} else if (!world.isClient) {
			player.sendMessage(Text.translatable(MESSAGE), true);
		}
		return ActionResult.success(world.isClient);
	}
}
