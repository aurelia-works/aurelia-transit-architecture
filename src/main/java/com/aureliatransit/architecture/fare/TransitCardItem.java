package com.aureliatransit.architecture.fare;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.mtr.MtrFareContract;
import com.aureliatransit.architecture.registry.ModItems;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Stored-value transit card. The balance lives in MTR's fare system (not on the item), so every card a player holds
 * shows the same balance and a lost card loses nothing. Carrying one anywhere in the inventory lets fare gates charge
 * the player; right-click shows the balance.
 */
public class TransitCardItem extends Item {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".transit_card";

	public TransitCardItem(Settings settings) {
		super(settings);
	}

	/** Whether {@code player} carries a card anywhere in the inventory (hotbar, main, off hand or armour slots). */
	public static boolean carriedBy(PlayerEntity player) {
		return player.getInventory().containsAny(Set.of(ModItems.TRANSIT_CARD));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		if (!world.isClient) {
			player.sendMessage(FareText.balance(MtrFareContract.balance(world, player)), true);
		}
		return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable(TIP).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".load").formatted(Formatting.DARK_GRAY));
	}
}
