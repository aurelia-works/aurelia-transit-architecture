package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FreePlacement;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws {@link FreePlacement} blocks turned and nudged: block models through a wrapper that moves their quads per block
 * state (plain vanilla quads, so it works with any renderer), block entity renderers through {@link #apply}.
 */
public final class FreePlacementRender {

	/** Ints per vertex in the block vertex format: position (3), colour, uv (2), light, normal. */
	private static final int STRIDE = 8;
	private static final int NORMAL = 7;

	private FreePlacementRender() {
	}

	/** Wraps the block-state models of every ATA block with free placement properties. */
	public static void init() {
		ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
			final Identifier id = context.id();
			if (model == null || !(id instanceof ModelIdentifier variant) || "inventory".equals(variant.getVariant())
					|| !variant.getNamespace().equals(AureliaTransitArchitecture.MOD_ID)) {
				return model;
			}
			return freeBlocks().contains(new Identifier(variant.getNamespace(), variant.getPath())) ? new Model(model) : model;
		}));
	}

	private static Set<Identifier> freeBlocks;

	private static Set<Identifier> freeBlocks() {
		if (freeBlocks == null) {
			final Set<Identifier> found = new HashSet<>();
			for (final Block block : Registries.BLOCK) {
				final BlockState state = block.getDefaultState();
				if (state.contains(FreePlacement.ANGLE) || state.contains(FreePlacement.OFFSET)) {
					found.add(Registries.BLOCK.getId(block));
				}
			}
			freeBlocks = found;
		}
		return freeBlocks;
	}

	/**
	 * Moves a block entity renderer's matrices the way the block model is moved; call right after {@code push()}, before the
	 * renderer turns to its facing.
	 */
	public static void apply(MatrixStack matrices, BlockState state) {
		if (!FreePlacement.moved(state)) {
			return;
		}
		final Vec3d offset = FreePlacement.offset(state);
		matrices.translate(offset.x + 0.5, 0, offset.z + 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-FreePlacement.degrees(state)));
		matrices.translate(-0.5, 0, -0.5);
	}

	/**
	 * Block-format vertex data turned {@code degrees} clockwise (seen from above) about the block centre, then nudged by (ox, oz)
	 * blocks; positions and normals both turn.
	 */
	static int[] moveVertices(int[] vertices, float degrees, double ox, double oz) {
		final double radians = Math.toRadians(-degrees);
		final float cos = (float) Math.cos(radians);
		final float sin = (float) Math.sin(radians);
		final int[] data = vertices.clone();
		for (int i = 0; i + STRIDE <= data.length; i += STRIDE) {
			final float x = Float.intBitsToFloat(data[i]) - 0.5F;
			final float z = Float.intBitsToFloat(data[i + 2]) - 0.5F;
			data[i] = Float.floatToRawIntBits(0.5F + x * cos + z * sin + (float) ox);
			data[i + 2] = Float.floatToRawIntBits(0.5F - x * sin + z * cos + (float) oz);
			final int normal = data[i + NORMAL];
			final float nx = (byte) normal;
			final float nz = (byte) (normal >> 16);
			final int rx = Math.round(nx * cos + nz * sin) & 0xFF;
			final int rz = Math.round(-nx * sin + nz * cos) & 0xFF;
			data[i + NORMAL] = normal & 0xFF00FF00 | rx | rz << 16;
		}
		return data;
	}

	/** A block model whose quads are turned about the block centre and nudged for free-placed states. */
	private static final class Model implements BakedModel {

		private final BakedModel wrapped;
		private final Map<BlockState, List<BakedQuad>> moved = new ConcurrentHashMap<>();

		Model(BakedModel wrapped) {
			this.wrapped = wrapped;
		}

		@Override
		public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
			if (state == null || !FreePlacement.moved(state)) {
				return wrapped.getQuads(state, face, random);
			}
			// moved quads no longer lie on the block's faces, so none of them is culled by a neighbour
			if (face != null) {
				return List.of();
			}
			return moved.computeIfAbsent(state, s -> move(s, random));
		}

		private List<BakedQuad> move(BlockState state, Random random) {
			final float degrees = FreePlacement.degrees(state);
			final double radians = Math.toRadians(-degrees);
			final float cos = (float) Math.cos(radians);
			final float sin = (float) Math.sin(radians);
			final Vec3d offset = FreePlacement.offset(state);
			final List<BakedQuad> out = new ArrayList<>();
			final List<BakedQuad> all = new ArrayList<>(wrapped.getQuads(state, null, random));
			for (final Direction direction : Direction.values()) {
				all.addAll(wrapped.getQuads(state, direction, random));
			}
			for (final BakedQuad quad : all) {
				final int[] data = moveVertices(quad.getVertexData(), degrees, offset.x, offset.z);
				out.add(new BakedQuad(data, quad.getColorIndex(), turn(quad.getFace(), cos, sin), quad.getSprite(), quad.hasShade()));
			}
			return List.copyOf(out);
		}

		/** The face nearest to a turned face's direction, for shading. */
		private static Direction turn(Direction face, float cos, float sin) {
			if (face.getAxis() == Direction.Axis.Y) {
				return face;
			}
			final float x = face.getOffsetX() * cos + face.getOffsetZ() * sin;
			final float z = -face.getOffsetX() * sin + face.getOffsetZ() * cos;
			return Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.EAST : Direction.WEST) : (z > 0 ? Direction.SOUTH : Direction.NORTH);
		}

		@Override
		public boolean useAmbientOcclusion() {
			return wrapped.useAmbientOcclusion();
		}

		@Override
		public boolean hasDepth() {
			return wrapped.hasDepth();
		}

		@Override
		public boolean isSideLit() {
			return wrapped.isSideLit();
		}

		@Override
		public boolean isBuiltin() {
			return wrapped.isBuiltin();
		}

		@Override
		public Sprite getParticleSprite() {
			return wrapped.getParticleSprite();
		}

		@Override
		public ModelTransformation getTransformation() {
			return wrapped.getTransformation();
		}

		@Override
		public ModelOverrideList getOverrides() {
			return wrapped.getOverrides();
		}
	}
}
