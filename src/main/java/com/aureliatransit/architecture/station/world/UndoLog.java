package com.aureliatransit.architecture.station.world;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
	/** Each written cell's rebuild key (block state and text, see {@link StationBuildJob#key}), last write wins, in write order. */
	private final Map<BlockPos, String> keys = new LinkedHashMap<>();
	private final Map<BlockPos, BlockState> coveredSoil = new HashMap<>();
	/** One stop of each MTR lift the build formed; undo deletes those lifts before the blocks. */
	private final List<BlockPos> lifts = new ArrayList<>();
	/** Both nodes of every MTR platform rail the build laid. */
	private final List<BlockPos> railNodes = new ArrayList<>();
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

	void add(Entry entry, String key) {
		entries.add(entry);
		keys.remove(entry.pos());
		keys.put(entry.pos(), key);
	}

	/** What each written cell was written as, in write order; a rebuild at the same spot compares its plan with this. */
	Map<BlockPos, String> keys() {
		return keys;
	}

	/** Each cell's state before the build first wrote it, and the state its last write left. */
	Map<BlockPos, Entry> cells() {
		final Map<BlockPos, Entry> cells = new LinkedHashMap<>();
		for (final Entry entry : entries) {
			final Entry first = cells.get(entry.pos());
			cells.put(entry.pos(), new Entry(entry.pos(), first == null ? entry.previous() : first.previous(), entry.placed()));
		}
		return cells;
	}

	/** Covered soil carries over to the log of a rebuild at the same spot. */
	void copySoil(UndoLog from) {
		from.coveredSoil.forEach(coveredSoil::putIfAbsent);
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

	void addRail(BlockPos a, BlockPos b) {
		railNodes.add(a);
		railNodes.add(b);
	}

	/** The nodes of the platform rails the build laid, once: undo removes the rails and forgets them. */
	List<BlockPos> takeRailNodes() {
		final List<BlockPos> taken = List.copyOf(railNodes);
		railNodes.clear();
		return taken;
	}

	/** Whether {@code pos} is a node of a platform rail the build laid. */
	boolean railNode(BlockPos pos) {
		return railNodes.contains(pos);
	}

	/** A rebuild keeps the last build's platform rails (MTR routes use them) and its undo still removes them. */
	void copyRails(UndoLog last) {
		railNodes.addAll(last.railNodes);
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
