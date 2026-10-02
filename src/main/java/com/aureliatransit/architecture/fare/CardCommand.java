package com.aureliatransit.architecture.fare;

import com.aureliatransit.architecture.block.mtr.MtrFareContract;
import com.aureliatransit.architecture.registry.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * {@code /card}: shows the balance (and hands out a card if the player has none); {@code /card load <emeralds>} pays
 * emeralds from the inventory into MTR's balance at MTR's ticket machine rates ({@link CardTopUp}). Open to every
 * player: loading costs emeralds, as at the machine.
 */
final class CardCommand {

	private CardCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("card")
				.executes(CardCommand::show)
				.then(CommandManager.literal("load").then(CommandManager.argument("emeralds", IntegerArgumentType.integer(1, CardTopUp.MAX_EMERALDS))
						.executes(CardCommand::load))));
	}

	private static int show(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		if (!MtrFareContract.available()) {
			context.getSource().sendError(FareText.of("unavailable"));
			return 0;
		}
		if (!hasCard(player)) {
			player.getInventory().offerOrDrop(new ItemStack(ModItems.TRANSIT_CARD));
			player.sendMessage(FareText.of("given"), false);
		}
		player.sendMessage(FareText.balance(MtrFareContract.balance(player.getWorld(), player)), false);
		player.sendMessage(FareText.of("how_to_load"), false);
		return 1;
	}

	private static int load(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		if (!MtrFareContract.available()) {
			context.getSource().sendError(FareText.of("unavailable"));
			return 0;
		}
		final int emeralds = IntegerArgumentType.getInteger(context, "emeralds");
		final int carried = player.getInventory().count(Items.EMERALD);
		if (carried < emeralds) {
			context.getSource().sendError(FareText.of("not_enough", emeralds, carried));
			return 0;
		}
		Inventories.remove(player.getInventory(), stack -> stack.isOf(Items.EMERALD), emeralds, false);
		final int amount = CardTopUp.amountFor(emeralds);
		MtrFareContract.addBalance(player.getWorld(), player, amount);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 1, 1);
		player.sendMessage(FareText.of("loaded", amount, emeralds, MtrFareContract.balance(player.getWorld(), player)), false);
		return amount;
	}

	static boolean hasCard(ServerPlayerEntity player) {
		return TransitCardItem.carriedBy(player);
	}
}
