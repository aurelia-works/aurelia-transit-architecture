package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.block.elevated.LiftStatusPanelBlock;
import com.aureliatransit.architecture.client.interactive.PanelDrawer;
import com.aureliatransit.architecture.text.LiftPanelLayout;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.SignData;
import com.aureliatransit.architecture.text.SignStyle;
import com.aureliatransit.architecture.text.TextSanitizer;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationReference;
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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws sign content on the visible face(s) of a sign. For joined rows only the leftmost block draws, across the
 * whole row. The layout is cached per sign and recomputed only when the sign data, the row length or the resolved
 * station name changes; the row length and automatic station name are re-checked once per second.
 */
public class TextSignBlockEntityRenderer implements BlockEntityRenderer<TextSignBlockEntity> {

	private static final int RECHECK_TICKS = 20;
	private static final float JOIN_MARGIN = 2.5F;

	private static final class Cached {
		SignData data;
		int rowLength;
		String primary;
		int status = -1;
		PanelLayout.Panel panel = PanelLayout.Panel.EMPTY;
		long nextCheck;
		int rowLengthChecked = -1;
	}

	private final TextRenderer textRenderer;
	private final Map<TextSignBlockEntity, Cached> cache = new WeakHashMap<>();

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
		final World world = entity.getWorld();
		final SignData data = entity.getData();
		final long time = world == null ? 0 : world.getTime();

		Cached cached = cache.get(entity);
		if (cached == null) {
			cached = new Cached();
			cache.put(entity, cached);
		}
		if (cached.data != data || time >= cached.nextCheck || time < cached.nextCheck - RECHECK_TICKS * 2L) {
			cached.nextCheck = time + RECHECK_TICKS;
			final int rowLength = layout.joins() ? rowLength(world, entity.getPos(), state, block) : 1;
			final String primary = resolvePrimary(data, layout.style(), entity.getPos());
			final int status = state.getBlock() instanceof LiftStatusPanelBlock ? state.get(LiftStatusPanelBlock.STATUS).ordinal() : -1;
			if (cached.data != data || cached.rowLengthChecked != rowLength || !primary.equals(cached.primary) || cached.status != status) {
				cached.data = data;
				cached.rowLengthChecked = rowLength;
				cached.rowLength = rowLength;
				cached.primary = primary;
				cached.status = status;
				final float width = layout.joins() ? rowLength * 16 - JOIN_MARGIN : layout.width();
				cached.panel = status >= 0
						? LiftPanelLayout.layout(primary, data.secondary(), LiftPanelLayout.status(status), width, layout.height(), layout.style().textColor(),
						layout.style().secondaryColor(), s -> textRenderer.getWidth(s))
						: PanelLayout.sign(data, primary, layout.style(), width, layout.height(), s -> textRenderer.getWidth(s));
			}
		}
		if (cached.panel.isEmpty()) {
			return;
		}

		final float centerX = layout.joins() ? cached.rowLength * 8 : 8;
		final int textLight = state.getLuminance() > 0 ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(state.get(TextSignBlock.FACING))));
		matrices.translate(-0.5, 0, -0.5);
		PanelDrawer.beginFace(matrices, centerX, layout.centerY(), layout.frontZ(), true);
		PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, textLight);
		PanelDrawer.endFace(matrices);
		if (layout.doubleSided()) {
			PanelDrawer.beginFace(matrices, centerX, layout.centerY(), layout.backZ(), false);
			PanelDrawer.panel(matrices, vertexConsumers, textRenderer, cached.panel, textLight);
			PanelDrawer.endFace(matrices);
		}
		matrices.pop();
	}

	/**
	 * The text to show as the main name: the MTR station name when the sign is in automatic mode and a station resolves,
	 * otherwise the manually entered text.
	 */
	public static String resolvePrimary(SignData data, SignStyle style, BlockPos pos) {
		if (style.hasAutoName() && data.autoName()) {
			final StationReference station = StationData.provider().resolve(pos, StationAssociation.AUTO, false).station();
			if (station != null) {
				final String name = TextSanitizer.sanitize(station.displayName(), SignData.MAX_PRIMARY);
				if (!name.isEmpty()) {
					return name;
				}
			}
		}
		return data.primary();
	}

	/**
	 * Number of signs in the row that starts at the given owner (its leftmost block).
	 */
	public static int rowLength(World world, BlockPos owner, BlockState ownerState, TextSignBlock block) {
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

	@Override
	public boolean rendersOutsideBoundingBox(TextSignBlockEntity entity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 48;
	}
}
