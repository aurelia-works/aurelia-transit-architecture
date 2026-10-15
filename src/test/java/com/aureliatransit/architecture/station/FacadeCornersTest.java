package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Window blocks are thin (a panel in the middle of the cell, or a pane): in a straight wall they read as a recessed window,
 * but where two walls meet (a corner, or a wall butting into one) their edge shows as a sliver or a slit, and the corner
 * looks open. Every corner and junction of a building's walls is solid.
 */
class FacadeCornersTest {

	private static final String[] LOOKS = {"utrecht", "amsterdam", "st pancras", "antwerp", "frankfurt", "hamburg", "munich", "grand central",
			"tokyo", "zurich", "paris", "milan", "berlin", "hong kong", "beijing", "bangkok", "kuala lumpur", "mumbai"};
	private static final String[] KINDS = {"station building", "city station building", "grand station building", "terminus", "city terminus",
			"grand terminus", "hub"};

	@Test
	void noWindowWhereTwoWallsMeet() {
		final List<String> problems = new ArrayList<>();
		for (final String look : LOOKS) {
			for (final String kind : KINDS) {
				for (final int tracks : new int[]{2, 4, 6}) {
					final String text = look + " " + kind + ", " + tracks + " tracks, island, 96 long";
					final StationPlan plan = StationPlanner.plan(StationDescriptionParser.parse(text).spec());
					final Map<Long, Role> at = new HashMap<>();
					for (final PlannedBlock block : plan.blocks()) {
						at.put(key(block.u(), block.y(), block.v()), block.role());
					}
					int found = 0;
					String first = "";
					for (final PlannedBlock block : plan.blocks()) {
						if (block.role() != Role.WINDOW || !thin(block.blockId())) {
							continue;
						}
						final boolean alongU = wall(at.get(key(block.u() - 1, block.y(), block.v()))) || wall(at.get(key(block.u() + 1, block.y(), block.v())));
						final boolean alongV = wall(at.get(key(block.u(), block.y(), block.v() - 1))) || wall(at.get(key(block.u(), block.y(), block.v() + 1)));
						if (alongU && alongV) {
							if (found++ == 0) {
								first = block.blockId() + " at " + block.u() + "," + block.y() + "," + block.v();
							}
						}
					}
					if (found > 0) {
						problems.add(text + ": " + found + " corner windows, first " + first);
					}
				}
			}
		}
		assertEquals(List.of(), problems.subList(0, Math.min(25, problems.size())), problems.size() + " plans with corner windows");
	}

	private static final Map<String, Boolean> THIN = new HashMap<>();

	/** True for a block that does not fill its cell: a pane, or a model whose boxes leave part of the cell open. */
	static boolean thin(String id) {
		return THIN.computeIfAbsent(id, FacadeCornersTest::readThin);
	}

	private static boolean readThin(String id) {
		final String[] parts = id.split(":");
		if (parts[1].endsWith("_pane") || parts[1].endsWith("_bars")) {
			return true;
		}
		final String state = read("/assets/" + parts[0] + "/blockstates/" + parts[1] + ".json");
		if (state == null) {
			return false;
		}
		final java.util.regex.Matcher model = java.util.regex.Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"").matcher(state);
		if (!model.find()) {
			return false;
		}
		final String[] m = model.group(1).split(":");
		final String json = read("/assets/" + (m.length > 1 ? m[0] : "minecraft") + "/models/" + (m.length > 1 ? m[1] : m[0]) + ".json");
		if (json == null || !json.contains("\"elements\"")) {
			return false;
		}
		// the union of the boxes must reach every face of the cell
		final java.util.regex.Matcher box = java.util.regex.Pattern.compile("\"from\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*\"to\"\\s*:\\s*\\[([^\\]]+)\\]").matcher(json);
		final double[] lo = {16, 16, 16};
		final double[] hi = {0, 0, 0};
		while (box.find()) {
			final String[] f = box.group(1).split(",");
			final String[] t = box.group(2).split(",");
			for (int i = 0; i < 3; i++) {
				lo[i] = Math.min(lo[i], Double.parseDouble(f[i].trim()));
				hi[i] = Math.max(hi[i], Double.parseDouble(t[i].trim()));
			}
		}
		return lo[0] > 0 || lo[1] > 0 || lo[2] > 0 || hi[0] < 16 || hi[1] < 16 || hi[2] < 16;
	}

	private static String read(String path) {
		try (java.io.InputStream in = FacadeCornersTest.class.getResourceAsStream(path)) {
			return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			return null;
		}
	}

	private static boolean wall(Role role) {
		return role == Role.WALL || role == Role.WINDOW;
	}

	private static long key(int u, int y, int v) {
		return ((long) (u + 4096) << 42) | ((long) (y + 4096) << 21) | (v + 4096);
	}
}
