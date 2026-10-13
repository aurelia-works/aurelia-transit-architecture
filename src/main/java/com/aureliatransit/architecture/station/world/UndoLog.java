package com.aureliatransit.architecture.station.world;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a build changed, in write order: the state each cell had before and the state it had right after the write. Undo walks
 * it backwards and only touches cells that still hold the written block (same block, and the same {@code is_connected} for
 * rail nodes; see {@link StationBuildJob#stillPlaced}).
 *
 * <p>It also remembers grass and mycelium the build covered. Vanilla turns them into dirt under a solid block, which is not a
 * write of ours; undo puts them back once the block above is gone, but only where the cell is still plain dirt.
 */
public final class UndoLog {

	/** @param placed the cell's state read back after the write, so neighbour updates during the write are included */
	public record Entry(BlockPos pos, BlockState previous, BlockState placed) {
	}

	private final RegistryKey<World> dimension;
	private final List<Entry> entries = new ArrayList<>();
	private final Map<BlockPos, BlockState> coveredSoil = new HashMap<>();
	/** One stop of each MTR lift the build formed; undo deletes those lifts before the blocks. */
	private final List<BlockPos> lifts = new ArrayList<>();
	/** Ids of the MTR stations the build added; undo deletes them. */
	private final List<Long> stations = new ArrayList<>();
	/** Set once undo has taken the stations: one MTR answers after that is deleted at once instead of kept. */
	private boolean stationsTaken;

	public UndoLog(RegistryKey<World> dimension) {
		this.dimension = dimension;
	}

	public RegistryKey<World> dimension() {
		return dimension;
	}

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	void add(Entry entry) {
		entries.add(entry);
	}

	/** Remembers soil that decays when covered, the first time the build writes above it. */
	void coverSoil(BlockPos pos, BlockState soil) {
		coveredSoil.putIfAbsent(pos, soil);
	}

	/** The covered soil state at {@code pos}, or null. */
	BlockState coveredSoil(BlockPos pos) {
		return coveredSoil.get(pos);
	}

	void addLift(BlockPos stop) {
		lifts.add(stop);
	}

	/** How many MTR lifts the build formed (and undo will delete). */
	public int lifts() {
		return lifts.size();
	}

	/** The lifts' stops, once: undo deletes the lifts and forgets them. */
	List<BlockPos> takeLifts() {
		final List<BlockPos> taken = List.copyOf(lifts);
		lifts.clear();
		return taken;
	}

	/** @return false when undo already ran, so the caller deletes the station instead */
	boolean addStation(long id) {
		if (stationsTaken) {
			return false;
		}
		stations.add(id);
		return true;
	}

	/** How many MTR stations the build added (and undo will delete). */
	public int stations() {
		return stations.size();
	}

	/** The added MTR stations' ids, once: undo deletes them and forgets them. */
	List<Long> takeStations() {
		final List<Long> taken = List.copyOf(stations);
		stations.clear();
		stationsTaken = true;
		return taken;
	}

	Entry last() {
		return entries.get(entries.size() - 1);
	}

	void removeLast() {
		entries.remove(entries.size() - 1);
	}
}
