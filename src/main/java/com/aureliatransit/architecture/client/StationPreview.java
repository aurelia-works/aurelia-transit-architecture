package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LayoutValidator;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.Violation;
import com.aureliatransit.architecture.station.studio.StudioApi;
import com.aureliatransit.architecture.station.world.BlockStateResolver;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A live 3D preview of a station description inside a screen, like the preview in MTR's own menus. The station is parsed and
 * planned on a worker thread with the same parser, planner and checks the server uses, then meshed once from the game's own
 * baked block models (faces hidden by a full neighbour are left out, faces shaded as in the world) into two vertex buffers,
 * opaque and translucent, which are drawn every frame under an orbit camera: drag to turn, scroll to zoom, right-drag to pan.
 * Only one mesh is built at a time; a newer description replaces a pending one. Close it with {@link #close()}.
 */
public final class StationPreview {

	/** One worker for every preview; meshing a 60 000 block station takes well under a second. */
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATA station preview");
		thread.setDaemon(true);
		return thread;
	});
	/**
	 * Two builders reused by every preview (native memory, never freed), and a permit held from the start of a mesh until the
	 * render thread has uploaded or released it, so a builder is never written while its last mesh is still being read.
	 */
	private static final BufferBuilder OPAQUE_BUILDER = new BufferBuilder(1 << 20);
	private static final BufferBuilder TRANSLUCENT_BUILDER = new BufferBuilder(1 << 16);
	private static final Semaphore BUILDERS = new Semaphore(1);
	private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	private static final Direction[] DIRECTIONS = Direction.values();
	/** The first view: from the street side and the near end, looking down a little, so the building's front faces you. */
	private static final float START_YAW = 125;
	private static final float START_PITCH = 25;

	private final AtomicInteger generation = new AtomicInteger();
	private VertexBuffer opaque;
	private VertexBuffer translucent;
	private boolean closed;
	private String requested;
	/** The meshed station's bounds in blocks, for the camera. */
	private float sizeX = 1;
	private float sizeY = 1;
	private float sizeZ = 1;
	private String status = "";
	private boolean good;
	private boolean busy;
	private float yaw = START_YAW;
	private float pitch = START_PITCH;
	private float zoom = 1;
	private float panX;
	private float panY;

	/** Plans and meshes {@code description} unless it is the one already shown or being built. */
	public void show(String description) {
		if (closed || description.equals(requested)) {
			return;
		}
		requested = description;
		busy = true;
		final int ticket = generation.incrementAndGet();
		WORKER.execute(() -> build(description, ticket));
	}

	/** Frees the buffers; call when the screen closes. A mesh still being built is released when it arrives. */
	public void close() {
		closed = true;
		generation.incrementAndGet();
		if (opaque != null) {
			opaque.close();
			translucent.close();
			opaque = null;
			translucent = null;
		}
	}

	// ---- building, on the worker ------------------------------------------------------------------------------------

	private void build(String description, int ticket) {
		if (ticket != generation.get()) {
			return;
		}
		final StationPlan plan;
		final String summary;
		final boolean buildable;
		try {
			final ParseResult parse = StationDescriptionParser.parse(description);
			plan = StationPlanner.plan(parse.spec());
			final List<Violation> violations = LayoutValidator.validate(plan);
			final StationAudit.Result audit = StationAudit.audit(plan);
			buildable = violations.isEmpty() && audit.buildable();
			final String size = String.format("%,d blocks · %d × %d × %d", plan.blocks().size(), audit.width(), audit.length(), audit.height());
			if (buildable) {
				summary = size + " · ready to build";
			} else if (!violations.isEmpty()) {
				summary = size + " · " + violations.size() + " design problems";
			} else {
				summary = size + " · " + audit.problems().size() + " failed checks";
			}
		} catch (RuntimeException e) {
			AureliaTransitArchitecture.LOGGER.warn("Station preview could not plan {}", description, e);
			MinecraftClient.getInstance().execute(() -> {
				if (ticket == generation.get()) {
					busy = false;
					status = "Could not plan this station";
					good = false;
				}
			});
			return;
		}
		if (ticket != generation.get()) {
			return;
		}
		try {
			BUILDERS.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		final BufferBuilder.BuiltBuffer[] built;
		final int[] bounds = new int[6];
		try {
			built = mesh(plan, bounds);
		} catch (RuntimeException e) {
			BUILDERS.release();
			AureliaTransitArchitecture.LOGGER.warn("Station preview could not draw {}", description, e);
			return;
		}
		MinecraftClient.getInstance().execute(() -> {
			try {
				if (ticket != generation.get() || closed) {
					built[0].release();
					built[1].release();
					return;
				}
				if (opaque == null) {
					opaque = new VertexBuffer(VertexBuffer.Usage.STATIC);
					translucent = new VertexBuffer(VertexBuffer.Usage.STATIC);
				}
				opaque.bind();
				opaque.upload(built[0]);
				translucent.bind();
				translucent.upload(built[1]);
				VertexBuffer.unbind();
				sizeX = bounds[3] - bounds[0] + 1;
				sizeY = bounds[4] - bounds[1] + 1;
				sizeZ = bounds[5] - bounds[2] + 1;
				status = summary;
				good = buildable;
				busy = false;
			} finally {
				BUILDERS.release();
			}
		});
	}

	/**
	 * Both meshes, the station moved so its footprint starts at the origin ({@code bounds} gets min and max x, y, z before the
	 * move). Faces whose neighbour in the plan is a full opaque cube are skipped.
	 */
	private static BufferBuilder.BuiltBuffer[] mesh(StationPlan plan, int[] bounds) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final Map<Long, String> idAt = new HashMap<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (final PlannedBlock block : plan.blocks()) {
			final int x = StudioApi.FRAME.x(block.u(), block.v());
			final int z = StudioApi.FRAME.z(block.u(), block.v());
			idAt.put(StudioApi.cell(x, block.y(), z), block.blockId());
			minX = Math.min(minX, x);
			minY = Math.min(minY, block.y());
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, block.y());
			maxZ = Math.max(maxZ, z);
		}
		bounds[0] = minX;
		bounds[1] = minY;
		bounds[2] = minZ;
		bounds[3] = maxX;
		bounds[4] = maxY;
		bounds[5] = maxZ;
		final Map<String, Optional<Block>> blocks = new HashMap<>();
		final Map<Long, BlockState> states = new HashMap<>();
		final List<long[]> order = new ArrayList<>();
		for (final PlannedBlock block : plan.blocks()) {
			final Optional<Block> found = blocks.computeIfAbsent(block.blockId(), BlockStateResolver::block);
			if (found.isEmpty()) {
				continue;
			}
			final int x = StudioApi.FRAME.x(block.u(), block.v());
			final int z = StudioApi.FRAME.z(block.u(), block.v());
			final Heading heading = StudioApi.FRAME.heading(block.front());
			final BlockState state = BlockStateResolver.withState(BlockStateResolver.state(found.get(), heading), StudioApi.withConnections(block, idAt));
			final long key = StudioApi.cell(x, block.y(), z);
			states.put(key, state);
			order.add(new long[]{key, x - minX, block.y() - minY, z - minZ});
		}
		final Map<BlockState, Boolean> fullCube = new HashMap<>();
		OPAQUE_BUILDER.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
		TRANSLUCENT_BUILDER.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
		final MatrixStack matrices = new MatrixStack();
		final Random random = Random.create(42L);
		final float[] shade = new float[4];
		final int[] light = {LIGHT, LIGHT, LIGHT, LIGHT};
		for (final long[] entry : order) {
			final BlockState state = states.get(entry[0]);
			final BakedModel model = client.getBlockRenderManager().getModel(state);
			final BufferBuilder out = RenderLayers.getBlockLayer(state) == RenderLayer.getTranslucent() ? TRANSLUCENT_BUILDER : OPAQUE_BUILDER;
			final int x = (int) entry[1] + minX;
			final int y = (int) entry[2] + minY;
			final int z = (int) entry[3] + minZ;
			matrices.push();
			matrices.translate(entry[1], entry[2], entry[3]);
			for (final Direction direction : DIRECTIONS) {
				final BlockState neighbour = states.get(StudioApi.cell(x + direction.getOffsetX(), y + direction.getOffsetY(), z + direction.getOffsetZ()));
				if (neighbour != null && fullCube.computeIfAbsent(neighbour, s -> s.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN))) {
					continue;
				}
				random.setSeed(42L);
				quads(out, matrices, state, model.getQuads(state, direction, random), shade, light);
			}
			random.setSeed(42L);
			quads(out, matrices, state, model.getQuads(state, null, random), shade, light);
			matrices.pop();
		}
		return new BufferBuilder.BuiltBuffer[]{OPAQUE_BUILDER.end(), TRANSLUCENT_BUILDER.end()};
	}

	private static void quads(BufferBuilder out, MatrixStack matrices, BlockState state, List<BakedQuad> quads, float[] shade, int[] light) {
		for (final BakedQuad quad : quads) {
			final float brightness = quad.hasShade() ? faceShade(quad.getFace()) : 1;
			java.util.Arrays.fill(shade, brightness);
			float r = 1;
			float g = 1;
			float b = 1;
			if (quad.hasColor()) {
				final int color = tint(state, quad.getColorIndex());
				r = (color >> 16 & 255) / 255f;
				g = (color >> 8 & 255) / 255f;
				b = (color & 255) / 255f;
			}
			out.quad(matrices.peek(), quad, shade, r, g, b, light, OverlayTexture.DEFAULT_UV, true);
		}
	}

	/** The world's fixed face shading: tops full, sides by axis, bottoms half. */
	private static float faceShade(Direction face) {
		return switch (face) {
			case UP -> 1.0f;
			case DOWN -> 0.5f;
			case NORTH, SOUTH -> 0.8f;
			case EAST, WEST -> 0.6f;
		};
	}

	private static int tint(BlockState state, int index) {
		try {
			return MinecraftClient.getInstance().getBlockColors().getColor(state, null, null, index);
		} catch (RuntimeException e) {
			return -1;
		}
	}

	// ---- drawing and the camera, on the render thread ----------------------------------------------------------------

	/** Draws the preview into the box and its status line under it. */
	public void render(DrawContext context, int x0, int y0, int x1, int y1) {
		final MinecraftClient client = MinecraftClient.getInstance();
		context.fill(x0, y0, x1, y1, 0xFF101317);
		context.drawBorder(x0, y0, x1 - x0, y1 - y0, 0xFF2A2F36);
		if (opaque != null) {
			context.draw();
			context.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
			RenderSystem.enableDepthTest();
			RenderSystem.depthMask(true);
			// fit the box as the first view projects it, so turning does not change the zoom
			final double yawR = Math.toRadians(START_YAW);
			final double pitchR = Math.toRadians(START_PITCH);
			final double across = Math.abs(sizeX * Math.cos(yawR)) + Math.abs(sizeZ * Math.sin(yawR));
			final double deep = Math.abs(sizeX * Math.sin(yawR)) + Math.abs(sizeZ * Math.cos(yawR));
			final double high = sizeY * Math.cos(pitchR) + deep * Math.sin(pitchR);
			final float fit = (float) Math.min((x1 - x0 - 12) / across, (y1 - y0 - 24) / high);
			final float scale = fit * zoom;
			final Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(context.getMatrices().peek().getPositionMatrix())
					.translate((x0 + x1) / 2f + panX, (y0 + y1) / 2f + panY, 0)
					.scale(scale, -scale, scale)
					.rotateX((float) Math.toRadians(pitch))
					.rotateY((float) Math.toRadians(yaw))
					.translate(-sizeX / 2f, -sizeY / 2f, -sizeZ / 2f);
			final Matrix4f projection = RenderSystem.getProjectionMatrix();
			final float fogStart = RenderSystem.getShaderFogStart();
			RenderSystem.setShaderFogStart(Float.MAX_VALUE);
			RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
			client.gameRenderer.getLightmapTextureManager().enable();
			RenderSystem.disableBlend();
			opaque.bind();
			opaque.draw(view, projection, GameRenderer.getRenderTypeCutoutProgram());
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.depthMask(false);
			translucent.bind();
			translucent.draw(view, projection, GameRenderer.getRenderTypeTranslucentProgram());
			VertexBuffer.unbind();
			RenderSystem.depthMask(true);
			client.gameRenderer.getLightmapTextureManager().disable();
			RenderSystem.setShaderFogStart(fogStart);
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
			context.disableScissor();
		}
		final String line = busy ? "Planning…" : status;
		context.drawTextWithShadow(client.textRenderer, line, x0 + 4, y1 + 4, busy ? 0x9AA0A6 : good ? 0x5FC77A : 0xF0A83A);
		context.drawTextWithShadow(client.textRenderer, "Drag: turn · Scroll: zoom · Right-drag: move", x0 + 4, y0 + 4, 0x6B737C);
	}

	/** Left-drag turns the station, right-drag moves it. */
	public void drag(int button, double deltaX, double deltaY) {
		if (button == 1) {
			panX += (float) deltaX;
			panY += (float) deltaY;
		} else {
			yaw += (float) deltaX * 0.6f;
			pitch = Math.max(-10, Math.min(90, pitch + (float) deltaY * 0.6f));
		}
	}

	public void zoom(double amount) {
		zoom = Math.max(0.3f, Math.min(12f, zoom * (float) Math.pow(1.15, amount)));
	}

	/** Back to the first view. */
	public void reset() {
		yaw = START_YAW;
		pitch = START_PITCH;
		zoom = 1;
		panX = 0;
		panY = 0;
	}
}
