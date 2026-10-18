package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RebuildDiffTest {

	private static RebuildDiff.Want<String> want(String pos, String key) {
		return new RebuildDiff.Want<>(pos, key);
	}

	@Test
	void theSamePlanWritesNothing() {
		final Map<String, String> built = new LinkedHashMap<>(Map.of("a", "stone", "b", "glass"));
		final RebuildDiff<String> diff = RebuildDiff.of(built, List.of(want("a", "stone"), want("b", "glass")));
		assertEquals(List.of(), diff.write());
		assertEquals(2, diff.kept());
		assertEquals(List.of(), diff.remove());
	}

	@Test
	void changedNewAndDroppedCellsAreSortedOut() {
		final Map<String, String> built = new LinkedHashMap<>();
		built.put("a", "stone");
		built.put("b", "glass");
		built.put("c", "sign|Old Name");
		built.put("d", "bench");
		final RebuildDiff<String> diff = RebuildDiff.of(built, List.of(want("a", "stone"), want("c", "sign|New Name"), want("e", "lamp")));
		assertEquals(List.of(1, 2), diff.write(), "the renamed sign and the new lamp are written");
		assertEquals(1, diff.kept());
		assertEquals(List.of("d", "b"), diff.remove(), "dropped cells go newest first, as undo takes them");
	}

	@Test
	void onlyTheLastOpOnACellCountsWhereTheCellIsOurs() {
		final Map<String, String> built = new LinkedHashMap<>(Map.of("a", "stone"));
		// a fresh site would clear the cell and then place on it; on our own cell only the place matters
		final RebuildDiff<String> same = RebuildDiff.of(built, List.of(want("a", "air"), want("a", "stone")));
		assertEquals(List.of(), same.write());
		assertEquals(1, same.kept());
		final RebuildDiff<String> changed = RebuildDiff.of(built, List.of(want("a", "air"), want("a", "glass")));
		assertEquals(List.of(1), changed.write());
		// on a cell that is not ours both ops still run
		final RebuildDiff<String> fresh = RebuildDiff.of(Map.of(), List.of(want("z", "air"), want("z", "glass")));
		assertEquals(List.of(0, 1), fresh.write());
	}
}
