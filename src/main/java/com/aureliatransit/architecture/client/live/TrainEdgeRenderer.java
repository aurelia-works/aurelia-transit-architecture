package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.EdgePart;
import com.aureliatransit.architecture.block.elevated.TrainEdgeBlock;
import com.aureliatransit.architecture.block.entity.TrainEdgeBlockEntity;
import com.aureliatransit.architecture.live.NearestPlatformProvider;
import com.aureliatransit.architecture.live.display.EdgeMotion;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationAssociationMode;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationDataProvider;
import com.aureliatransit.architecture.transit.StationSnapshot;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the moving part of a train-keyed edge (1.4): drop-down barrier bars or a boarding step. Runs only while the
 * block is visible and in range (block-entity renderer, no ticker). At most once a second per block it asks the shared,
 * cached provider whether a train stands at the nearest MTR platform: the same snapshot a PIDS at that platform uses,
 * so one arrivals request per platform, never per block. No platform nearby or no data: the part stays at rest.
 */
public class TrainEdgeRenderer implements BlockEntityRenderer<TrainEdgeBlockEntity> {

	/** How far the barrier bars drop (into the posts) and the step slides out (over the gap), in blocks. */
	private static final float BAR_DROP = 9F / 16F;
	private static final float STEP_OUT = 4F / 16F;

	private static final class State {
		final EdgeMotion motion = new EdgeMotion();
		long nextCheck;
		boolean deployed;
	}

	private final Map<TrainEdgeBlockEntity, State> states = new WeakHashMap<>();

	public TrainEdgeRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(TrainEdgeBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof TrainEdgeBlock block)) {
			return;
		}
		final State s = states.computeIfAbsent(entity, key -> new State());
		final long now = System.currentTimeMillis();
		if (now >= s.nextCheck || s.nextCheck - now > EdgeMotion.CHECK_MILLIS * 2) {
			// stagger by position so a long row of edges does not look up in the same frame
			s.nextCheck = now + EdgeMotion.CHECK_MILLIS + Math.floorMod(entity.getPos().asLong(), 200);
			s.deployed = trainStanding(entity.getPos(), now);
		}
		final float p = s.motion.update(now, s.deployed);
		matrices.push();
		if (block.mode() == TrainEdgeBlock.Mode.BARRIER) {
			matrices.translate(0, -p * BAR_DROP, 0);
		} else {
			final Direction toward = state.get(TrainEdgeBlock.FACING);
			matrices.translate(toward.getOffsetX() * p * STEP_OUT, 0, toward.getOffsetZ() * p * STEP_OUT);
		}
		MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state.with(TrainEdgeBlock.PART, EdgePart.MOVING), matrices, vertexConsumers, light, overlay);
		matrices.pop();
	}

	private static boolean trainStanding(BlockPos pos, long now) {
		final StationDataProvider provider = StationData.provider();
		if (!(provider instanceof NearestPlatformProvider nearestProvider)) {
			return false;
		}
		final long nearest = nearestProvider.nearestPlatformId(pos, StationAssociation.AUTO);
		if (nearest == 0) {
			return false;
		}
		final StationSnapshot station = provider.resolve(pos, StationAssociation.AUTO, false);
		if (station.station() == null || station.station().id() <= 0) {
			return false;
		}
		final StationSnapshot platform = provider.resolve(pos, new StationAssociation(StationAssociationMode.MANUAL, station.station().id(), List.of(nearest)), true);
		return EdgeMotion.trainStanding(platform.services(), nearest, now);
	}

	/** Diagnostic for {@code /aurelia_live edge}: each step of the lookup the renderer makes for {@code pos}. */
	static String describe(BlockPos pos, long now) {
		final StationDataProvider provider = StationData.provider();
		if (!(provider instanceof NearestPlatformProvider nearestProvider)) {
			return "no MTR provider";
		}
		final long nearest = nearestProvider.nearestPlatformId(pos, StationAssociation.AUTO);
		final StationSnapshot station = provider.resolve(pos, StationAssociation.AUTO, false);
		if (nearest == 0 || station.station() == null) {
			return "nearest platform " + nearest + ", station " + (station.station() == null ? "none" : station.station().displayName());
		}
		final StationSnapshot platform = provider.resolve(pos, new StationAssociation(StationAssociationMode.MANUAL, station.station().id(), List.of(nearest)), true);
		final StringBuilder out = new StringBuilder("station " + station.station().displayName() + ", platform " + nearest % 1000 + ", services " + platform.services().size());
		for (final var service : platform.services()) {
			out.append(" | p").append(service.platformId() % 1000).append(" arr").append((service.arrivalMillis() - now) / 1000).append("s dep")
					.append((service.departureMillis() - now) / 1000).append('s');
		}
		return out.append(", standing ").append(EdgeMotion.trainStanding(platform.services(), nearest, now)).toString();
	}

	@Override
	public int getRenderDistance() {
		return 48;
	}
}
