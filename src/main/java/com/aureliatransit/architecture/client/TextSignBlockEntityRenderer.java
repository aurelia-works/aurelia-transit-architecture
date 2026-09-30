package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

import java.util.List;

/**
 * Draws sign text on both faces of the sign. For joined rows only the leftmost block draws, centred across the row.
 */
public class TextSignBlockEntityRenderer implements BlockEntityRenderer<TextSignBlockEntity> {

	private static final float LINE_HEIGHT = 10;
	private static final float SURFACE_OFFSET = 0.004F;

	private final TextRenderer textRenderer;

	public TextSignBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	@Override
	public void render(TextSignBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof TextSignBlock block)) {
			return;
		}
		final TextLayout layout = block.getLayout();
		if (layout.joins() && state.get(TextSignBlock.LEFT)) {
			return;
		}

		final List<String> lines = trailingTrimmed(entity.getLines(), layout.maxLines());
		if (lines.isEmpty()) {
			return;
		}

		final int rowLength = layout.joins() ? rowLength(entity.getWorld(), entity.getPos(), state, block) : 1;
		final float widthPx = layout.joins() ? rowLength * 16 - 2.5F : layout.width();
		final float centerXPx = layout.joins() ? rowLength * 8 : 8;

		float maxWidth = 1;
		for (final String line : lines) {
			maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
		}
		final float totalHeight = lines.size() * LINE_HEIGHT - 1;
		final float scale = Math.min(layout.height() / 16 / totalHeight, widthPx * 0.94F / 16 / maxWidth);
		final int textLight = state.getLuminance() > 0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(state.get(TextSignBlock.FACING))));
		matrices.translate(-0.5, 0, -0.5);

		drawFace(matrices, vertexConsumers, lines, centerXPx, layout, layout.frontZ() / 16 - SURFACE_OFFSET, true, scale, totalHeight, textLight);
		if (layout.doubleSided()) {
			drawFace(matrices, vertexConsumers, lines, centerXPx, layout, layout.backZ() / 16 + SURFACE_OFFSET, false, scale, totalHeight, textLight);
		}
		matrices.pop();
	}

	private void drawFace(MatrixStack matrices, VertexConsumerProvider vertexConsumers, List<String> lines, float centerXPx, TextLayout layout, float z, boolean front, float scale, float totalHeight, int light) {
		matrices.push();
		matrices.translate(centerXPx / 16, layout.centerY() / 16, z);
		if (front) {
			// The model front faces north (-Z); vanilla text faces +Z.
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
		}
		matrices.scale(scale, -scale, scale);
		for (int i = 0; i < lines.size(); i++) {
			final String line = lines.get(i);
			final float x = -textRenderer.getWidth(line) / 2F;
			final float y = -totalHeight / 2 + i * LINE_HEIGHT;
			textRenderer.draw(line, x, y, layout.color(), false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
		}
		matrices.pop();
	}

	private static int rowLength(World world, BlockPos owner, BlockState ownerState, TextSignBlock block) {
		if (world == null) {
			return 1;
		}
		final Direction right = TextSignBlock.localRight(ownerState.get(TextSignBlock.FACING));
		int length = 1;
		BlockState current = ownerState;
		BlockPos pos = owner;
		while (length < TextSignBlock.MAX_ROW && current.get(TextSignBlock.RIGHT)) {
			pos = pos.offset(right);
			current = world.getBlockState(pos);
			if (!current.isOf(block) || current.get(TextSignBlock.FACING) != ownerState.get(TextSignBlock.FACING)) {
				break;
			}
			length++;
		}
		return length;
	}

	private static List<String> trailingTrimmed(List<String> lines, int maxLines) {
		int last = -1;
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			if (!lines.get(i).isEmpty()) {
				last = i;
			}
		}
		return lines.subList(0, last + 1);
	}

	@Override
	public boolean rendersOutsideBoundingBox(TextSignBlockEntity entity) {
		return true;
	}
}
