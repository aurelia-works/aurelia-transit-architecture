package com.aureliatransit.architecture.client.interactive;

import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.entity.InfoDisplayBlockEntity;
import com.aureliatransit.architecture.client.TextSignBlockEntityRenderer;
import com.aureliatransit.architecture.interactive.InteractiveEntities;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import com.aureliatransit.architecture.text.ConfigurableTextData;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.util.math.BlockPos;

/**
 * Client init: renderers and editor screens for interactive content. Owned by workstream B.
 */
public final class InteractiveClient {

	private InteractiveClient() {
	}

	public static void init() {
		BlockEntityRendererFactories.register(ModBlockEntities.TEXT_SIGN, TextSignBlockEntityRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.INFO_DISPLAY, InfoDisplayRenderer::new);
		BlockEntityRendererFactories.register(ModBlockEntities.CLOCK, ClockRenderer::new);
		EntityRendererRegistry.register(InteractiveEntities.SEAT, EmptyEntityRenderer::new);
		InfoDisplayBlock.openEditor = InteractiveClient::openInfoEditor;
	}

	private static void openInfoEditor(BlockPos pos) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		final BlockState state = client.world.getBlockState(pos);
		if (!(state.getBlock() instanceof InfoDisplayBlock block)) {
			return;
		}
		// A V1 block placed before it had a block entity has none until first edited; start from an empty text.
		final ConfigurableTextData current = client.world.getBlockEntity(pos) instanceof InfoDisplayBlockEntity display
				? display.getText() : ConfigurableTextData.EMPTY;
		client.setScreen(new InfoTextEditScreen(pos, current, block.getLayout()));
	}
}
