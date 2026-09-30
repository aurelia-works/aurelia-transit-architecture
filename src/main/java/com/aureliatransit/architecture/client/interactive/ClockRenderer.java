package com.aureliatransit.architecture.client.interactive;

import com.aureliatransit.architecture.block.ClockBlock;
import com.aureliatransit.architecture.block.entity.ClockBlockEntity;
import com.aureliatransit.architecture.interactive.ClockTime;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.TextFit;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws digital readouts and analog hands from Minecraft world time. The displayed string is rebuilt at most once per
 * in-game minute; between minutes a frame costs one integer division and a cache hit.
 */
public class ClockRenderer implements BlockEntityRenderer<ClockBlockEntity> {

	private static final int DIGITS = 0xFFFFB347;
	private static final int HAND = 0xFF1B1F23;
	private static final int SECOND_ACCENT = 0xFFB23A3A;

	private static final class Cached {
		int minute = -1;
		PanelLayout.Panel panel = PanelLayout.Panel.EMPTY;
	}

	private final TextRenderer textRenderer;
	private final Map<ClockBlockEntity, Cached> cache = new WeakHashMap<>();

	public ClockRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	@Override
	public void render(ClockBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final World world = entity.getWorld();
		final BlockState state = entity.getCachedState();
		if (world == null || !(state.getBlock() instanceof ClockBlock block)) {
			return;
		}
		final ClockBlock.Face face = block.getFace();
		final int minute = ClockTime.minuteOfDay(world.getTimeOfDay(), 0);
		final int textLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(state.get(ClockBlock.FACING))));
		matrices.translate(-0.5, 0, -0.5);
		if (face.analog()) {
			drawHands(matrices, vertexConsumers, face, minute, textLight);
		} else {
			Cached cached = cache.get(entity);
			if (cached == null) {
				cached = new Cached();
				cache.put(entity, cached);
			}
			if (cached.minute != minute) {
				cached.minute = minute;
				cached.panel = digitalPanel(face, minute);
			}
			PanelDrawer.beginFace(matrices, 8, face.centerY(), face.frontZ(), true);
			PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, textLight);
			PanelDrawer.endFace(matrices);
			if (face.doubleSided()) {
				PanelDrawer.beginFace(matrices, 8, face.centerY(), face.backZ(), false);
				PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, textLight);
				PanelDrawer.endFace(matrices);
			}
		}
		matrices.pop();
	}

	private PanelLayout.Panel digitalPanel(ClockBlock.Face face, int minute) {
		final String text = ClockTime.format(minute);
		// Scale from the widest possible reading so the digits do not change size as the time changes.
		final float scale = TextFit.fitOne(textRenderer.getWidth("88:88"), face.width() * 0.94F, face.height() * 0.8F, 10);
		return new PanelLayout.Panel(List.of(), List.of(new PanelLayout.Label(text, 0, 0, scale, DIGITS, textRenderer.getWidth(text))));
	}

	private void drawHands(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ClockBlock.Face face, int minute, int light) {
		PanelDrawer.beginFace(matrices, 8, face.centerY(), face.frontZ(), true);

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-ClockTime.hourAngle(minute)));
		PanelDrawer.fill(matrices, vertexConsumers, 0, 1.3F, 0.95F, 3.8F, HAND, 0, light);
		matrices.pop();

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-ClockTime.minuteAngle(minute)));
		PanelDrawer.fill(matrices, vertexConsumers, 0, 2.0F, 0.6F, 5.6F, HAND, 0.0012F, light);
		matrices.pop();

		PanelDrawer.fill(matrices, vertexConsumers, 0, 0, 1.3F, 1.3F, SECOND_ACCENT, 0.0024F, light);
		PanelDrawer.endFace(matrices);
	}

	@Override
	public int getRenderDistance() {
		return 32;
	}
}
