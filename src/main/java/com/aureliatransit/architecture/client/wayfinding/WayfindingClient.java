package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.block.wayfinding.WayfindingPlateBlock;
import com.aureliatransit.architecture.registry.WayfindingBlocks;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/**
 * Client init of the 1.2 presentation blocks (subagent B): the wayfinding panel renderer and the wayfinding editor.
 * Render layers of the new blocks are handled by the main client initializer through the ModBlocks entries.
 */
public final class WayfindingClient {

	private WayfindingClient() {
	}

	public static void init() {
		BlockEntityRendererFactories.register(WayfindingBlocks.SIGN_ENTITY, WayfindingSignRenderer::new);
		WayfindingPlateBlock.openEditor = WayfindingEditScreen::open;
		WayfindingPlateBlock.openTerminal = pos -> {
			final net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
			if (client.world != null && client.world.getBlockEntity(pos) instanceof com.aureliatransit.architecture.block.entity.WayfindingSignBlockEntity terminal) {
				client.setScreen(new PassengerTerminalScreen(pos, terminal));
			}
		};
	}
}
