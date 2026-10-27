package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.client.FreePlacementRender;
import com.aureliatransit.architecture.live.LiveDebug;
import com.aureliatransit.architecture.live.NearestPlatformProvider;
import com.aureliatransit.architecture.live.PidsBlock;
import com.aureliatransit.architecture.live.PidsBlockEntity;
import com.aureliatransit.architecture.wayfinding.MessageRotation;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationAssociationMode;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationDataProvider;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a live display. Only the owner (top-left) block of a joined screen renders, and only if it is within range
 * and not viewed from behind. The expensive part (layout) lives in {@link BoardBuilder} and runs at most about once a
 * second per display; per frame this class only replays the cached {@link BoardModel}.
 */
public final class PidsRenderer implements BlockEntityRenderer<PidsBlockEntity> {

	private static final Identifier PIXEL = AureliaTransitArchitecture.id("textures/block/live_pixel.png");
	private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	/**
	 * Offsets (blocks) in front of the screen surface: each rectangle layer ({@link BoardModel#LAYER_BACKGROUND} ...)
	 * sits {@link #Z_LAYER} further out, text in front of all of them, masks and top text further still. A 24-bit depth
	 * buffer resolves about 0.0023 blocks at 44 blocks, so 0.004 left the background losing to the screen block's own face
	 * from about 25 blocks (the board turned near-black with its text still showing); these steps keep a wide margin at the
	 * full {@link #RENDER_DISTANCE}, and the whole stack (0.052) stays under one pixel (0.0625).
	 */
	private static final float Z_BACKGROUND = 0.012F;
	private static final float Z_LAYER = 0.008F;
	private static final float Z_TEXT = Z_BACKGROUND + BoardModel.LAYERS * Z_LAYER;
	private static final long REBUILD_MILLIS = 1000;
	private static final long REBUILD_MARQUEE_MILLIS = 350;
	private static final double RENDER_DISTANCE = 44;

	/** Per-entity render state, stored in the block entity's opaque client cache slot. */
	private static final class Cache {
		final BoardModel model = new BoardModel();
		long nextRebuild;
		int configVersion = -1;
		int width = 1;
		int height = 1;
		boolean built;
	}

	private final TextRenderer textRenderer;
	private final Matrix4f scratch = new Matrix4f();
	/** Minecraft recreates renderers on a resource reload, which is when font widths can change. */
	private final WidthCache widths = new WidthCache(BoardModel.Font.values().length);

	public PidsRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	@Override
	public int getRenderDistance() {
		return 48;
	}

	@Override
	public void render(PidsBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof PidsBlock block) || state.get(PidsBlock.LEFT) || state.get(PidsBlock.UP)) {
			return;
		}
		final World world = entity.getWorld();
		if (world == null) {
			return;
		}
		final Direction facing = state.get(PidsBlock.FACING);

		// Only when someone can see it: in range, and not from behind a single-sided screen.
		final Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		final double dx = camera.x - (entity.getPos().getX() + 0.5);
		final double dz = camera.z - (entity.getPos().getZ() + 0.5);
		final double dy = camera.y - (entity.getPos().getY() + 0.5);
		if (dx * dx + dy * dy + dz * dz > RENDER_DISTANCE * RENDER_DISTANCE) {
			return;
		}
		if (!block.doubleSided() && dx * facing.getOffsetX() + dz * facing.getOffsetZ() < -0.6) {
			return;
		}

		final long now = System.currentTimeMillis();
		Cache cache = entity.clientCache() instanceof Cache existing ? existing : null;
		if (cache == null) {
			cache = new Cache();
			entity.setClientCache(cache);
		}
		if (!cache.built || now >= cache.nextRebuild || cache.configVersion != entity.configVersion()) {
			rebuild(cache, entity, block, state, world, now);
		}
		final BoardModel model = cache.model;
		LiveDebug.count(LiveDebug.Counter.BOARD_FRAMES);

		matrices.push();
		FreePlacementRender.apply(matrices, state);
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(facing)));
		matrices.translate(-0.5, 0, -0.5);

		final float frontZ = block.frontZPixels() / 16F;
		final float left = 1 - BoardBuilder.FRAME;
		final float top = 1 - block.topInsetPixels() / 16F - BoardBuilder.FRAME;
		drawFace(matrices, vertexConsumers, model, left, top, frontZ, now);
		if (block.doubleSided()) {
			final float centre = 1 - cache.width / 2F;
			matrices.translate(centre, 0, 0.5);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
			matrices.translate(-centre, 0, -0.5);
			drawFace(matrices, vertexConsumers, model, left, top, frontZ, now);
		}
		matrices.pop();
	}

	private void rebuild(Cache cache, PidsBlockEntity entity, PidsBlock block, BlockState state, World world, long now) {
		LiveDebug.count(LiveDebug.Counter.BOARD_REBUILDS);
		cache.built = true;
		cache.configVersion = entity.configVersion();
		cache.width = block.width(world, entity.getPos(), state);
		cache.height = block.height(world, entity.getPos(), state);
		final var config = entity.config();
		final StationDataProvider provider = StationData.provider();
		List<Long> here = List.of();
		if (provider instanceof NearestPlatformProvider nearestProvider && (!block.kind().stationWide() || config.summary())) {
			// from the middle of the screen: a wide board's owner block sits at its end, nearer one face of an island
			final BlockPos middle = entity.getPos().offset(PidsBlock.viewerRight(state.get(PidsBlock.FACING)), (cache.width - 1) / 2);
			here = nearestProvider.platformIdsHere(middle, config.association());
		}
		StationSnapshot snapshot = null;
		if (!here.isEmpty() && !block.kind().stationWide()) {
			// An AUTO platform display shows its own platform (both faces of an island), never the whole station. Resolving
			// the whole station would also cap the list at the station's first MAX_SERVICES departures.
			final StationSnapshot station = provider.resolve(entity.getPos(), config.association(), false);
			if (station.station() != null && station.station().id() > 0) {
				final StationSnapshot platform = provider.resolve(entity.getPos(),
						new StationAssociation(StationAssociationMode.MANUAL, station.station().id(), here), true, config.callingTimes() && config.callingAt());
				if (platform.hasStation()) {
					snapshot = platform;
				}
			}
		}
		if (snapshot == null) {
			snapshot = provider.resolve(entity.getPos(), config.association(), true, config.callingTimes() && config.callingAt());
		}
		BoardBuilder.build(cache.model, textRenderer, widths, block.kind(), config, snapshot, here, cache.width, cache.height, block.topInsetPixels(), now);
		cache.nextRebuild = now + (cache.model.stripScroll ? MessageRotation.STEP_MILLIS : cache.model.marquee ? REBUILD_MARQUEE_MILLIS : REBUILD_MILLIS);
		if (cache.model.seconds) {
			// a clock with seconds ticks on the second, like the real board
			cache.nextRebuild = Math.min(cache.nextRebuild, (now / 1000 + 1) * 1000);
		}
	}

	private void drawFace(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BoardModel model, float left, float top, float frontZ, long now) {
		matrices.push();
		matrices.translate(left, top, frontZ);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
		matrices.scale(model.scale, -model.scale, model.scale);
		final MatrixStack.Entry entry = matrices.peek();
		final Matrix4f position = entry.getPositionMatrix();
		final Matrix3f normal = entry.getNormalMatrix();

		final VertexConsumer quads = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PIXEL));
		for (int i = 0; i < model.rectCount; i++) {
			// masks sit in front of the text, the other layers behind it
			final float z = (model.rl[i] == BoardModel.LAYER_MASK ? Z_TEXT + Z_LAYER : Z_BACKGROUND + model.rl[i] * Z_LAYER) / model.scale;
			quad(quads, position, normal, model.rx[i], model.ry[i], model.rx[i] + model.rw[i], model.ry[i] + model.rh[i], z, model.rc[i]);
		}

		final float zText = Z_TEXT / model.scale;
		final float zTop = (Z_TEXT + 2 * Z_LAYER) / model.scale;
		for (int i = 0; i < model.textCount; i++) {
			scratch.set(position).translate(model.tx[i], model.ty[i], model.tl[i] == BoardModel.TEXT_TOP ? zTop : zText)
					.scale(model.tk[i] * model.tsx[i], model.tk[i], 1);
			textRenderer.draw(model.ts[i], 0, 0, model.tc[i], false, scratch, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
		}
		for (int i = 0; i < model.scrollCount; i++) {
			drawScroll(model, i, position, vertexConsumers, zText, now);
		}
		matrices.pop();
	}

	/** The characters of scrolling line {@code i} that are inside its window this frame. */
	private void drawScroll(BoardModel model, int i, Matrix4f position, VertexConsumerProvider vertexConsumers, float z, long now) {
		final float start = model.scrollStart(i, now);
		final float[] at = model.sa[i];
		final float k = model.sk[i];
		final int length = at.length - 1;
		int first = 0;
		while (first < length && start + at[first + 1] * k <= model.sx0[i]) {
			first++;
		}
		int end = first;
		while (end < length && start + at[end] * k < model.sx1[i]) {
			end++;
		}
		if (end <= first) {
			return;
		}
		if (model.sDrawn[i] == null || model.sFirst[i] != first || model.sEnd[i] != end) {
			model.sDrawn[i] = model.sf[i].ordered(model.ss[i].substring(first, end));
			model.sFirst[i] = first;
			model.sEnd[i] = end;
		}
		scratch.set(position).translate(start + at[first] * k, model.sy[i], z).scale(k, k, 1);
		textRenderer.draw(model.sDrawn[i], 0, 0, model.sc[i], false, scratch, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
	}

	private static void quad(VertexConsumer vc, Matrix4f position, Matrix3f normal, float x1, float y1, float x2, float y2, float z, int argb) {
		final int a = argb >>> 24;
		final int r = (argb >> 16) & 0xFF;
		final int g = (argb >> 8) & 0xFF;
		final int b = argb & 0xFF;
		vertex(vc, position, normal, x1, y1, z, r, g, b, a);
		vertex(vc, position, normal, x1, y2, z, r, g, b, a);
		vertex(vc, position, normal, x2, y2, z, r, g, b, a);
		vertex(vc, position, normal, x2, y1, z, r, g, b, a);
	}

	private static void vertex(VertexConsumer vc, Matrix4f position, Matrix3f normal, float x, float y, float z, int r, int g, int b, int a) {
		vc.vertex(position, x, y, z).color(r, g, b, a).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(LIGHT).normal(normal, 0, 0, 1).next();
	}
}
