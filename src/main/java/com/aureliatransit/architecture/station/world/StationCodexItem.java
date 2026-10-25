package com.aureliatransit.architecture.station.world;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** The Station Codex ({@link StationGuide}): right-click to open it. Opening is client only; the server does nothing. */
public class StationCodexItem extends Item {

	/** Client only: opens the Codex screen. Set by the client initializer. */
	public static Runnable openScreen = () -> {
	};

	public StationCodexItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		if (world.isClient) {
			openScreen.run();
		}
		return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
	}
}
