package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Right-click (empty hand) cycling of a block's style: one item, several looks, selected by a block state instead of
 * separate inventory entries. Holding any item, including a block, never cycles, so placing next to a styled block
 * works as usual.
 */
public final class StyleCycle {

	private static final String MESSAGE = "message." + AureliaTransitArchitecture.MOD_ID + ".style.";

	private StyleCycle() {
	}

	/** The value after {@code value}, wrapping round. */
	public static <E extends Enum<E>> E next(E value) {
		final E[] all = value.getDeclaringClass().getEnumConstants();
		return all[(value.ordinal() + 1) % all.length];
	}

	/**
	 * @param blockId block id, used for the action-bar name of the new style
	 */
	public static <E extends Enum<E> & StringIdentifiable> ActionResult use(String blockId, Property<E> property, BlockState state, World world, BlockPos pos,
																			  PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND || !player.getStackInHand(hand).isEmpty() || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			final BlockState updated = state.with(property, next(state.get(property)));
			world.setBlockState(pos, updated, Block.NOTIFY_ALL);
			player.sendMessage(Text.translatable(MESSAGE + blockId + "." + updated.get(property).asString()), true);
		}
		return ActionResult.success(world.isClient);
	}
}
