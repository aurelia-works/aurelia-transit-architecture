package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
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
	 * sits {@link #Z_LAYER} further out, text in front of all of them. The step stays depth-resolvable at the full
	 * {@link #RENDER_DISTANCE} with a 24-bit depth buffer (about 0.0023 blocks at 44 blocks) yet is invisible (1/16 px).
	 */
	private static final float Z_BACKGROUND = 0.004F;
	private static final float Z_LAYER = 0.004F;
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
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(facing)));
		matrices.translate(-0.5, 0, -0.5);

		final float frontZ = block.frontZPixels() / 16F;
		final float left = 1 - BoardBuilder.FRAME;
		final float top = 1 - block.topInsetPixels() / 16F - BoardBuilder.FRAME;
		drawFace(matrices, vertexConsumers, model, left, top, frontZ);
		if (block.doubleSided()) {
			final float centre = 1 - cache.width / 2F;
			matrices.translate(centre, 0, 0.5);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
			matrices.translate(-centre, 0, -0.5);
			drawFace(matrices, vertexConsumers, model, left, top, frontZ);
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
		long nearest = 0;
		if (provider instanceof NearestPlatformProvider nearestProvider && !block.kind().stationWide()) {
			nearest = nearestProvider.nearestPlatformId(entity.getPos(), config.association());
		}
		StationSnapshot snapshot = null;
		if (nearest != 0) {
			// An AUTO platform display shows one platform. Resolving the whole station would cap the list at the
			// station's first MAX_SERVICES departures, so this platform's trains could drop out behind other platforms'.
			final StationSnapshot station = provider.resolve(entity.getPos(), config.association(), false);
			if (station.station() != null && station.station().id() > 0) {
				final StationSnapshot platform = provider.resolve(entity.getPos(),
						new StationAssociation(StationAssociationMode.MANUAL, station.station().id(), List.of(nearest)), true);
				if (platform.hasStation()) {
					snapshot = platform;
				}
			}
		}
		if (snapshot == null) {
			snapshot = provider.resolve(entity.getPos(), config.association(), true);
		}
		BoardBuilder.build(cache.model, textRenderer, block.kind(), config, snapshot, nearest, cache.width, cache.height, block.topInsetPixels(), now, clock(world));
		cache.nextRebuild = now + (cache.model.stripScroll ? MessageRotation.STEP_MILLIS : cache.model.marquee ? REBUILD_MARQUEE_MILLIS : REBUILD_MILLIS);
	}

	/**
	 * In-game time of day as HH:MM (world tick 0 is 06:00).
	 */
	private static String clock(World world) {
		final long ticks = Math.floorMod(world.getTimeOfDay(), 24000L);
		final int hour = (int) ((ticks / 1000 + 6) % 24);
		final int minute = (int) ((ticks % 1000) * 60 / 1000);
		return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
	}

	private void drawFace(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BoardModel model, float left, float top, float frontZ) {
		matrices.push();
		matrices.translate(left, top, frontZ);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
		matrices.scale(model.scale, -model.scale, model.scale);
		final MatrixStack.Entry entry = matrices.peek();
		final Matrix4f position = entry.getPositionMatrix();
		final Matrix3f normal = entry.getNormalMatrix();

		final VertexConsumer quads = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(PIXEL));
		for (int i = 0; i < model.rectCount; i++) {
			final float z = (Z_BACKGROUND + model.rl[i] * Z_LAYER) / model.scale;
			quad(quads, position, normal, model.rx[i], model.ry[i], model.rx[i] + model.rw[i], model.ry[i] + model.rh[i], z, model.rc[i]);
		}

		final float zText = Z_TEXT / model.scale;
		for (int i = 0; i < model.textCount; i++) {
			scratch.set(position).translate(model.tx[i], model.ty[i], zText).scale(model.tk[i] * model.tsx[i], model.tk[i], 1);
			textRenderer.draw(model.ts[i], 0, 0, model.tc[i], false, scratch, vertexConsumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
		}
		matrices.pop();
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
