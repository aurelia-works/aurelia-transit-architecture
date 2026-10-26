package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.CellKind;
import com.aureliatransit.architecture.station.ChunkHold;
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
import java.util.Map;

/**
 * {@link SiteProbe} over a server world. A plain probe never loads a chunk: every read is guarded by a loaded check. A
 * {@link #loading} probe loads each chunk it is asked about and holds it with a ticket, so a station longer than the render
 * distance can be surveyed and built. It holds at most a {@link ChunkHold} window of chunks: the one used longest ago loses its
 * ticket and unloads at the next server tick, so a site of any size streams through instead of staying loaded. Whoever walks
 * a site with it calls {@link #release} when done; the ticket's ten minutes are only the fallback.
 */
public final class WorldSiteProbe implements SiteProbe {

	/** Holds a station's chunks loaded through a survey and its build; renewed whenever the walk comes back to a chunk. */
	private static final ChunkTicketType<ChunkPos> HOLD = ChunkTicketType.create("ata_station", Comparator.comparingLong(ChunkPos::toLong),
			20 * 60 * 10);

	private final ServerWorld world;
	private final boolean load;
	private final ChunkHold held;
	/**
	 * Chunks this probe made the server load or generate: one it found unloaded, or one it newly holds while a neighbour is not
	 * loaded yet (the ticket loads that ring too). A walk paces itself by them.
	 */
	private int loads;
	/** Cells read as another state than the world's: a rebuild surveys the last build's cells as the site they replaced. */
	private final Map<BlockPos, BlockState> under;

	public WorldSiteProbe(ServerWorld world) {
		this(world, false, Map.of(), 1);
	}

	private WorldSiteProbe(ServerWorld world, boolean load, Map<BlockPos, BlockState> under, int cap) {
		this.world = world;
		this.load = load;
		this.under = under;
		this.held = new ChunkHold(cap);
	}

	/** A loading probe that reads each cell of {@code under} as the state given there instead of the world's. */
	public static WorldSiteProbe under(ServerWorld world, Map<BlockPos, BlockState> under) {
		return new WorldSiteProbe(world, true, Map.copyOf(under), ChunkHold.DEFAULT_CAP);
	}

	private BlockState state(BlockPos pos) {
		final BlockState state = under.get(pos);
		return state != null ? state : world.getBlockState(pos);
	}

	/** A probe that loads (and generates if needed) every chunk it reads. */
	public static WorldSiteProbe loading(ServerWorld world) {
		return loading(world, ChunkHold.DEFAULT_CAP);
	}

	/** A loading probe that holds at most {@code cap} chunks at a time. */
	public static WorldSiteProbe loading(ServerWorld world, int cap) {
		return new WorldSiteProbe(world, true, Map.of(), cap);
	}

	/** True when the chunk holding the column is loaded; a loading probe loads it first. */
	public boolean loaded(int x, int z) {
		final int cx = ChunkSectionPos.getSectionCoord(x);
		final int cz = ChunkSectionPos.getSectionCoord(z);
		if (load) {
			final long key = ChunkPos.toLong(cx, cz);
			// another probe letting go of the same chunk drops this one's ticket too (equal tickets are one), so look again
			final boolean unloaded = !world.isChunkLoaded(cx, cz);
			if (!held.holds(key) || unloaded) {
				if (unloaded || !ringLoaded(cx, cz)) {
					loads++;
				}
				final ChunkPos pos = new ChunkPos(cx, cz);
				world.getChunkManager().addTicket(HOLD, pos, 1, pos);
				if (unloaded) {
					world.getChunk(cx, cz);
				}
			}
			final Long gone = held.use(key);
			if (gone != null) {
				final ChunkPos old = new ChunkPos(gone);
				world.getChunkManager().removeTicket(HOLD, old, 1, old);
			}
		}
		return world.isChunkLoaded(cx, cz);
	}

	/** True when the eight chunks around this one are loaded, so holding it costs the server no loading. */
	private boolean ringLoaded(int cx, int cz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && !world.isChunkLoaded(cx + dx, cz + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Chunks this probe has made the server load or generate so far (see {@link #loads}). */
	public int loads() {
		return loads;
	}

	/** Chunks held right now. */
	public int held() {
		return held.size();
	}

	/** Lets go of every chunk held; they unload at the next server tick unless something else keeps them. */
	public void release() {
		for (final long chunk : held.clear()) {
			final ChunkPos pos = new ChunkPos(chunk);
			world.getChunkManager().removeTicket(HOLD, pos, 1, pos);
		}
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
		final BlockState state = state(pos);
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
		return state(pos).isSideSolidFullSquare(world, pos, Direction.UP);
	}

	@Override
	public String describe(int x, int y, int z) {
		if (world.isOutOfHeightLimit(y)) {
			return "outside the world";
		}
		if (!loaded(x, z)) {
			return "unloaded chunk";
		}
		return Registries.BLOCK.getId(state(new BlockPos(x, y, z)).getBlock()).toString();
	}
}
