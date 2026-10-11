package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.world.PreviewGeometry.Layer;
import com.aureliatransit.architecture.station.world.PreviewGeometry.Point;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.UUID;

/**
 * A 30 second particle outline of a pending plan, shown to the planning player only. It changes nothing in the world:
 * white footprint, blue track centre lines, yellow platform edges, green rail nodes, red site issues.
 */
public final class StationPreview {

	public static final int DURATION_TICKS = 600;
	public static final int REFRESH_TICKS = 10;

	private static final EnumMap<Layer, DustParticleEffect> COLOURS = new EnumMap<>(Layer.class);

	static {
		COLOURS.put(Layer.OUTLINE, dust(1.0F, 1.0F, 1.0F));
		COLOURS.put(Layer.TRACK, dust(0.2F, 0.4F, 1.0F));
		COLOURS.put(Layer.EDGE, dust(1.0F, 0.9F, 0.1F));
		COLOURS.put(Layer.NODE, dust(0.2F, 1.0F, 0.3F));
		COLOURS.put(Layer.ISSUE, dust(1.0F, 0.1F, 0.1F));
	}

	private final UUID player;
	private final RegistryKey<World> dimension;
	private final PreviewGeometry geometry;
	private int age;
	private int refreshes;

	public StationPreview(UUID player, RegistryKey<World> dimension, StationPlan plan, LocalFrame frame, SurveyReport report) {
		this.player = player;
		this.dimension = dimension;
		this.geometry = new PreviewGeometry(plan, frame, report.issues());
	}

	private static DustParticleEffect dust(float r, float g, float b) {
		return new DustParticleEffect(new Vector3f(r, g, b), 1.0F);
	}

	public UUID player() {
		return player;
	}

	/** Ticks lived so far. */
	public int age() {
		return age;
	}

	/** Particle count the next refresh would send, and advances the sampling. For tests. */
	int nextRefreshSize() {
		int count = 0;
		for (final Layer layer : Layer.values()) {
			count += geometry.sample(layer, refreshes).size();
		}
		refreshes++;
		return count;
	}

	PreviewGeometry geometry() {
		return geometry;
	}

	/** One server tick; false when the preview is over (30 s, player gone). */
	public boolean tick(MinecraftServer server) {
		age++;
		if (age > DURATION_TICKS) {
			return false;
		}
		final ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(player);
		if (viewer == null) {
			return false;
		}
		if ((age - 1) % REFRESH_TICKS == 0) {
			final ServerWorld world = server.getWorld(dimension);
			if (world != null && viewer.getServerWorld() == world && viewer.networkHandler != null) {
				refresh(world, viewer);
			}
		}
		return true;
	}

	private void refresh(ServerWorld world, ServerPlayerEntity viewer) {
		for (final Layer layer : Layer.values()) {
			final DustParticleEffect effect = COLOURS.get(layer);
			for (final Point point : geometry.sample(layer, refreshes)) {
				world.spawnParticles(viewer, effect, true, point.x(), point.y(), point.z(), 1, 0, 0, 0, 0);
			}
		}
		refreshes++;
	}
}
