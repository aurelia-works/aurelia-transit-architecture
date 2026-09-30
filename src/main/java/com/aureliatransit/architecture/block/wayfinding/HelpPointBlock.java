package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Wall-mounted help point. Decorative: a lit unit with a help symbol; right-click shows a short action-bar line. There
 * is no network or telephony behind it.
 */
public class HelpPointBlock extends FacingShapedBlock {

	public static final String MESSAGE = "message." + AureliaTransitArchitecture.MOD_ID + ".help_point";

	public HelpPointBlock(Settings settings, VoxelShape northOutline) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (hand != Hand.MAIN_HAND || player.getStackInHand(hand).getItem() instanceof BlockItem) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			player.sendMessage(Text.translatable(MESSAGE), true);
		}
		return ActionResult.success(world.isClient);
	}
}
