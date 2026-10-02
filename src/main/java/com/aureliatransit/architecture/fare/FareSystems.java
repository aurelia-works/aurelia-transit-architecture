package com.aureliatransit.architecture.fare;

import com.aureliatransit.architecture.block.mtr.MtrFareContract;
import com.aureliatransit.architecture.registry.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Common/server init of fares: the {@code /card} command and a free transit card for every player on their first join
 * (remembered with a player command tag, so it survives relogs and is never given twice).
 */
public final class FareSystems {

	private static final String GIVEN_TAG = "ata_transit_card_given";

	private FareSystems() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CardCommand.register(dispatcher));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			final ServerPlayerEntity player = handler.getPlayer();
			if (MtrFareContract.available() && player.addCommandTag(GIVEN_TAG)) {
				if (!CardCommand.hasCard(player)) {
					player.getInventory().offerOrDrop(new ItemStack(ModItems.TRANSIT_CARD));
				}
				player.sendMessage(FareText.of("welcome"), false);
			}
		});
	}
}
