package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a rebuild at the same spot has to do: the last build wrote {@code built} (each cell's key, in write order), the new plan
 * wants {@code wanted} (in op order). A cell whose key is the same is kept as it stands; every other wanted cell is written;
 * a built cell the new plan does not want goes back to what was there before the first build. Keys are whatever identifies
 * a write completely (the block state and any sign or screen text), so equal keys mean the cell would be written the same.
 *
 * @param write  indexes into {@code wanted} to write, in op order
 * @param kept   how many wanted cells already hold their key
 * @param remove built cells the new plan leaves out, newest write first (the order undo takes them)
 */
public record RebuildDiff<P>(List<Integer> write, int kept, List<P> remove) {

	/** One wanted cell: where, and the key it should hold. */
	public record Want<P>(P pos, String key) {
	}

	public static <P> RebuildDiff<P> of(Map<P, String> built, List<Want<P>> wanted) {
		final List<Integer> write = new ArrayList<>();
		final Set<P> wantedCells = new HashSet<>();
		// the last op on a cell is what it ends up holding (a clear and then a place on the same cell)
		final Map<P, Integer> lastOp = new LinkedHashMap<>();
		for (int i = 0; i < wanted.size(); i++) {
			wantedCells.add(wanted.get(i).pos());
			lastOp.put(wanted.get(i).pos(), i);
		}
		int kept = 0;
		for (int i = 0; i < wanted.size(); i++) {
			final Want<P> want = wanted.get(i);
			final boolean last = lastOp.get(want.pos()) == i;
			if (last && want.key().equals(built.get(want.pos()))) {
				kept++;
			} else if (last || !built.containsKey(want.pos())) {
				write.add(i);
			}
			// an earlier op on a cell the last build already wrote is skipped: the cell is ours, the final op decides it
		}
		final List<P> remove = new ArrayList<>();
		for (final P pos : built.keySet()) {
			if (!wantedCells.contains(pos)) {
				remove.add(pos);
			}
		}
		Collections.reverse(remove);
		return new RebuildDiff<>(List.copyOf(write), kept, List.copyOf(remove));
	}
}
