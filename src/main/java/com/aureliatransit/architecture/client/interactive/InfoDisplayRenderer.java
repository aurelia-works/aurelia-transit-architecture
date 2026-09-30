package com.aureliatransit.architecture.client.interactive;

import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.InfoLayout;
import com.aureliatransit.architecture.block.entity.InfoDisplayBlockEntity;
import com.aureliatransit.architecture.text.ConfigurableTextData;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The single renderer for information case, information pillar and timetable case: heading band plus body rows on the
 * front face (and the back face for double-sided blocks). The layout is cached per block entity and rebuilt only when
 * its text object changes.
 */
public class InfoDisplayRenderer implements BlockEntityRenderer<InfoDisplayBlockEntity> {

	private record Cached(ConfigurableTextData text, PanelLayout.Panel panel) {
	}

	private final TextRenderer textRenderer;
	private final Map<InfoDisplayBlockEntity, Cached> cache = new WeakHashMap<>();

	public InfoDisplayRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	@Override
	public void render(InfoDisplayBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final ConfigurableTextData text = entity.getText();
		if (text.isEmpty()) {
			return;
		}
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof InfoDisplayBlock block)) {
			return;
		}
		final InfoLayout layout = block.getLayout();
		Cached cached = cache.get(entity);
		if (cached == null || cached.text != text) {
			cached = new Cached(text, PanelLayout.info(text, styleOf(layout), layout.width(), layout.height(), s -> textRenderer.getWidth(s)));
			cache.put(entity, cached);
		}
		if (cached.panel.isEmpty()) {
			return;
		}
		final int textLight = state.getLuminance() > 0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(state.get(InfoDisplayBlock.FACING))));
		matrices.translate(-0.5, 0, -0.5);
		PanelDrawer.beginFace(matrices, layout.centerX(), layout.centerY(), layout.frontZ(), true);
		PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, textLight);
		PanelDrawer.endFace(matrices);
		if (layout.doubleSided()) {
			PanelDrawer.beginFace(matrices, layout.centerX(), layout.centerY(), layout.backZ(), false);
			PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, textLight);
			PanelDrawer.endFace(matrices);
		}
		matrices.pop();
	}

	public static PanelLayout.InfoStyle styleOf(InfoLayout layout) {
		return new PanelLayout.InfoStyle(layout.bodyColor(), layout.headingColor(), layout.maxScale(), layout.maxBodyLines());
	}

	@Override
	public int getRenderDistance() {
		return 48;
	}
}
