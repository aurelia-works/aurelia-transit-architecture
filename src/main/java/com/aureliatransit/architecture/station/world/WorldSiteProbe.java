package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.SiteProbe;
import com.aureliatransit.architecture.station.StylePalette;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;

/**
 * {@link SiteProbe} over a server world. Never loads a chunk: every read is guarded by a loaded check.
 */
public final class WorldSiteProbe implements SiteProbe {

	private final ServerWorld world;

	public WorldSiteProbe(ServerWorld world) {
		this.world = world;
	}

	/** True when the chunk holding the column is loaded. */
	public boolean loaded(int x, int z) {
		return world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
	}

	@Override
	public CellKind kind(int x, int y, int z) {
		if (world.isOutOfHeightLimit(y)) {
			return CellKind.OUT_OF_WORLD;
		}
		if (!loaded(x, z)) {
			return CellKind.UNLOADED;
		}
		final BlockPos pos = new BlockPos(x, y, z);
		final BlockState state = world.getBlockState(pos);
		if (state.isAir()) {
			return CellKind.AIR;
		}
		if (Registries.BLOCK.getId(state.getBlock()).toString().equals(StylePalette.MTR_RAIL_NODE)) {
			return CellKind.RAIL_NODE;
		}
		if (state.hasBlockEntity() || state.getHardness(world, pos) < 0) {
			return CellKind.PROTECTED;
		}
		if (!state.getFluidState().isEmpty()) {
			return CellKind.FLUID;
		}
		return state.getCollisionShape(world, pos).isEmpty() ? CellKind.SOFT : CellKind.SOLID;
	}

	@Override
	public boolean sturdyTop(int x, int y, int z) {
		if (world.isOutOfHeightLimit(y) || !loaded(x, z)) {
			return false;
		}
		final BlockPos pos = new BlockPos(x, y, z);
		return world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP);
	}

	@Override
	public String describe(int x, int y, int z) {
		if (world.isOutOfHeightLimit(y)) {
			return "outside the world";
		}
		if (!loaded(x, z)) {
			return "unloaded chunk";
		}
		return Registries.BLOCK.getId(world.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
	}
}
