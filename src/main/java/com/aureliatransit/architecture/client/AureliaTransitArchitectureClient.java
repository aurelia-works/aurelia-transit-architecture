package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.client.interactive.InteractiveClient;
import com.aureliatransit.architecture.client.live.LiveClient;
import com.aureliatransit.architecture.client.wayfinding.WayfindingClient;
import com.aureliatransit.architecture.client.wayfinding.WayfindingLogicClient;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.registry.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;

public final class AureliaTransitArchitectureClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		LiveClient.init();
		InteractiveClient.init();
		WayfindingLogicClient.init();
		WayfindingClient.init();
		for (final ModBlocks.Entry entry : ModBlocks.entries()) {
			switch (entry.renderKind()) {
				case CUTOUT -> BlockRenderLayerMap.INSTANCE.putBlock(entry.block(), RenderLayer.getCutout());
				case TRANSLUCENT -> BlockRenderLayerMap.INSTANCE.putBlock(entry.block(), RenderLayer.getTranslucent());
				case SOLID -> {
				}
			}
		}

		TextSignBlock.openEditor = pos -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client.world != null && client.world.getBlockEntity(pos) instanceof TextSignBlockEntity sign) {
				client.setScreen(new TextSignEditScreen(pos, sign));
			}
		};
	}
}
