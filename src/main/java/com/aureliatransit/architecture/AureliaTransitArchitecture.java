package com.aureliatransit.architecture;

import com.aureliatransit.architecture.interactive.InteractiveSystems;
import com.aureliatransit.architecture.live.LiveSystems;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.registry.InteractiveBlocks;
import com.aureliatransit.architecture.registry.LiveBlocks;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import com.aureliatransit.architecture.registry.ModBlocks;
import com.aureliatransit.architecture.registry.ModItemGroups;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AureliaTransitArchitecture implements ModInitializer {

	public static final String MOD_ID = "aurelia_transit_architecture";
	public static final Logger LOGGER = LoggerFactory.getLogger("Aurelia Transit Architecture");

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		LiveBlocks.init();
		InteractiveBlocks.init();
		ModBlockEntities.init();
		ModItemGroups.init();
		ModPackets.registerServerReceivers();
		LiveSystems.init();
		InteractiveSystems.init();
		LOGGER.info("Registered {} Aurelia Transit Architecture blocks", ModBlocks.entries().size());
	}
}
