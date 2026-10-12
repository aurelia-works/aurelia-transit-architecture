package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.SiteProbe;
import com.aureliatransit.architecture.station.StylePalette;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link SiteProbe} over a server world. A plain probe never loads a chunk: every read is guarded by a loaded check. A
 * {@link #loading} probe loads each chunk it is asked about and holds it with a ticket for a while, so a station longer than
 * the render distance can be surveyed and built.
 */
public final class WorldSiteProbe implements SiteProbe {

	/** Holds a station's chunks loaded through a survey and its build; renewed whenever the build comes back to a chunk. */
	private static final ChunkTicketType<ChunkPos> HOLD = ChunkTicketType.create("ata_station", Comparator.comparingLong(ChunkPos::toLong),
			20 * 60 * 10);

	private final ServerWorld world;
	private final boolean load;
	private final Set<Long> held = new HashSet<>();

	public WorldSiteProbe(ServerWorld world) {
		this(world, false);
	}

	private WorldSiteProbe(ServerWorld world, boolean load) {
		this.world = world;
		this.load = load;
	}

	/** A probe that loads (and generates if needed) every chunk it reads. */
	public static WorldSiteProbe loading(ServerWorld world) {
		return new WorldSiteProbe(world, true);
	}

	/** True when the chunk holding the column is loaded; a loading probe loads it first. */
	public boolean loaded(int x, int z) {
		final int cx = ChunkSectionPos.getSectionCoord(x);
		final int cz = ChunkSectionPos.getSectionCoord(z);
		if (load && (held.add(ChunkPos.toLong(cx, cz)) || !world.isChunkLoaded(cx, cz))) {
			final ChunkPos pos = new ChunkPos(cx, cz);
			world.getChunkManager().addTicket(HOLD, pos, 1, pos);
			world.getChunk(cx, cz);
		}
		return world.isChunkLoaded(cx, cz);
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
