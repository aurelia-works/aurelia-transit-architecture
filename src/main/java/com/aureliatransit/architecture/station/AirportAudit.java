package com.aureliatransit.architecture.station;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aureliatransit.architecture.station.StationAudit.Check;
import com.aureliatransit.architecture.station.StationAudit.Status;
import com.aureliatransit.architecture.station.StationAudit.Topic;

/**
 * The checks of {@link StationAudit} for an airport plan. Like the station checks they measure the planned blocks (the runways
 * as connected areas of runway pavement, the taxiways by walking from the apron, the gates as openings in the terminal wall) and
 * compare what they find with what was asked for, so a planner bug shows up here.
 */
final class AirportAudit {

	private AirportAudit() {
	}

	static List<Check> checks(StationPlan plan) {
		final Audit audit = new Audit(plan);
		audit.runways();
		audit.taxiways();
		audit.gates();
		audit.terminal();
		return audit.checks;
	}

	private static final class Audit {

		private final StationPlan plan;
		private final AirportSpec airport;
		private final AirportGeometry air;
		private final Map<Long, PlannedBlock> cells = new HashMap<>();
		private final List<Check> checks = new ArrayList<>();
		/** Runway pavement, one set of cells per connected area. */
		private List<Set<Long>> runwayAreas;

		Audit(StationPlan plan) {
			this.plan = plan;
			this.airport = plan.spec().airport();
			this.air = AirportGeometry.of(airport);
			for (final PlannedBlock block : plan.blocks()) {
				cells.putIfAbsent(StationAudit.key(block.u(), block.y(), block.v()), block);
			}
		}

		private PlannedBlock at(int u, int y, int v) {
			return cells.get(StationAudit.key(u, y, v));
		}

		private boolean is(int u, int y, int v, String id) {
			final PlannedBlock block = at(u, y, v);
			return block != null && block.blockId().equals(id);
		}

		private void add(Topic topic, Status status, String message, String fix) {
			checks.add(new Check(topic, status, message, fix));
		}

		/** The cells at ground level whose block is one of {@code ids}, as keys. */
		private Set<Long> ground(Set<String> ids) {
			final Set<Long> out = new HashSet<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.y() == StationGeometry.PLATFORM_Y && ids.contains(block.blockId())) {
					out.add(StationAudit.key(block.u(), block.y(), block.v()));
				}
			}
			return out;
		}

		private static long step(long key, int du, int dv) {
			// keys pack u, y, v in 21 bits each (see StationAudit.key)
			return key + ((long) du << 42) + dv;
		}

		/** Connected areas (four-neighbour) of the given cells. */
		private static List<Set<Long>> components(Set<Long> all) {
			final List<Set<Long>> out = new ArrayList<>();
			final Set<Long> seen = new HashSet<>();
			for (final long start : all) {
				if (!seen.add(start)) {
					continue;
				}
				final Set<Long> area = new HashSet<>();
				final ArrayDeque<Long> queue = new ArrayDeque<>();
				queue.add(start);
				area.add(start);
				while (!queue.isEmpty()) {
					final long key = queue.poll();
					for (final int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						final long next = step(key, d[0], d[1]);
						if (all.contains(next) && seen.add(next)) {
							area.add(next);
							queue.add(next);
						}
					}
				}
				out.add(area);
			}
			return out;
		}

		private static int uOf(long key) {
			return (int) ((key >> 42) - (1 << 20));
		}

		private static int vOf(long key) {
			return (int) ((key & ((1L << 21) - 1)) - (1 << 20));
		}

		/** Cells of runway pavement that make an area a runway. */
		private static final int MIN_PAVEMENT = 50;

		void runways() {
			// the runway's pavement, its painted lines and its lights make one area (the lines and lights cut the black apart)
			final List<Set<Long>> all = components(ground(Set.of(PlanAirport.RUNWAY_SURFACE, PlanAirport.PAINT_WHITE, PlanAirport.EDGE_LIGHT,
					PlanAirport.THRESHOLD_LIGHT)));
			runwayAreas = new ArrayList<>();
			for (final Set<Long> area : all) {
				// white lines alone (the stands') are not a runway: it has pavement
				if (area.stream().filter(key -> is(uOf(key), 0, vOf(key), PlanAirport.RUNWAY_SURFACE)).count() >= MIN_PAVEMENT) {
					runwayAreas.add(area);
				}
			}
			final List<String> wrong = new ArrayList<>();
			final List<String> found = new ArrayList<>();
			for (final Set<Long> area : runwayAreas) {
				int u0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, v0 = Integer.MAX_VALUE, v1 = Integer.MIN_VALUE;
				for (final long key : area) {
					u0 = Math.min(u0, uOf(key));
					u1 = Math.max(u1, uOf(key));
					v0 = Math.min(v0, vOf(key));
					v1 = Math.max(v1, vOf(key));
				}
				final int width = u1 - u0 + 1;
				final int length = v1 - v0 + 1;
				found.add(length + " x " + width);
				if (width != airport.runwayWidth() || length != airport.runwayLength()) {
					wrong.add(length + " x " + width + " at u=" + u0);
				}
			}
			if (runwayAreas.size() != airport.runways() || !wrong.isEmpty()) {
				add(Topic.RUNWAYS, Status.FAIL, runwayAreas.size() + " runways measured (" + String.join(", ", found) + "); asked for " + airport.runways()
						+ " of " + airport.runwayLength() + " x " + airport.runwayWidth(),
						"Plan again; if it stays, copy the description and report it: the runway pavement is not what was asked for.");
				return;
			}
			int dark = 0;
			for (int i = 0; i < airport.runways(); i++) {
				int lights = 0;
				int centre = 0;
				for (int v = air.runwayV0(); v <= air.runwayV1(); v++) {
					if (is(air.runwayU0().get(i), 0, v, PlanAirport.EDGE_LIGHT) || is(air.runwayU1(i), 0, v, PlanAirport.EDGE_LIGHT)) {
						lights++;
					}
					if (is(air.runwayCentre(i), 0, v, PlanAirport.PAINT_WHITE)) {
						centre++;
					}
				}
				if (lights == 0 || centre == 0) {
					dark++;
				}
			}
			if (dark > 0) {
				add(Topic.RUNWAYS, Status.FAIL, dark + (dark == 1 ? " runway has" : " runways have") + " no edge lights or no centreline",
						"Plan again; if it stays, copy the description and report it.");
				return;
			}
			add(Topic.RUNWAYS, Status.PASS, airport.runways() + (airport.runways() == 1 ? " runway, " : " runways, each ") + airport.runwayLength() + " x "
					+ airport.runwayWidth() + " blocks with edge lights, threshold bars, numbers and a centreline", "");
		}

		/** Taxiway pavement, painted, or lit: the cells an aircraft can taxi on besides the runway and the apron. */
		private static final Set<String> TAXI = Set.of(PlanAirport.TAXI_SURFACE, PlanAirport.PAINT_YELLOW, PlanAirport.TAXI_LIGHT);

		void taxiways() {
			final Set<Long> drivable = new HashSet<>();
			final Set<Long> apron = new HashSet<>();
			for (final PlannedBlock block : plan.blocks()) {
				if (block.y() != StationGeometry.PLATFORM_Y) {
					continue;
				}
				final String id = block.blockId();
				final long key = StationAudit.key(block.u(), block.y(), block.v());
				if (id.equals(PlanAirport.APRON_SURFACE) || id.equals(PlanAirport.BLAST_SURFACE) || TAXI.contains(id)
						|| id.equals(PlanAirport.RUNWAY_SURFACE) || id.equals(PlanAirport.PAINT_WHITE) || id.equals(PlanAirport.EDGE_LIGHT)
						|| id.equals(PlanAirport.THRESHOLD_LIGHT)) {
					drivable.add(key);
					if (id.equals(PlanAirport.APRON_SURFACE) && air.apronU0() <= block.u() && block.u() <= air.apronU1()) {
						apron.add(key);
					}
				}
			}
			// walk from the apron's taxiway edge, out over every taxiway, link and runway
			final Set<Long> reached = new HashSet<>();
			final ArrayDeque<Long> queue = new ArrayDeque<>();
			for (final long key : apron) {
				if (uOf(key) == air.apronU1() && reached.add(key)) {
					queue.add(key);
				}
			}
			while (!queue.isEmpty()) {
				final long key = queue.poll();
				for (final int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					final long next = step(key, d[0], d[1]);
					if (drivable.contains(next) && reached.add(next)) {
						queue.add(next);
					}
				}
			}
			final List<String> cut = new ArrayList<>();
			for (int i = 0; i < runwayAreas.size(); i++) {
				if (runwayAreas.get(i).stream().noneMatch(reached::contains)) {
					cut.add("runway " + (i + 1));
				}
			}
			if (apron.isEmpty()) {
				add(Topic.TAXIWAYS, Status.FAIL, "no apron", "Plan again; if it stays, copy the description and report it.");
			} else if (!cut.isEmpty()) {
				add(Topic.TAXIWAYS, Status.FAIL, String.join(" and ", cut) + " cannot be reached by taxiing from the apron",
						"Plan again; if it stays, copy the description and report it: a taxiway or link is missing.");
			} else {
				add(Topic.TAXIWAYS, Status.PASS, "A parallel taxiway with " + air.links().size() + " links reaches "
						+ (runwayAreas.size() == 1 ? "the runway" : "every runway") + " from the apron", "");
			}
		}

		void gates() {
			final int wanted = airport.gates();
			int found = 0;
			int bridged = 0;
			if (airport.terminal()) {
				// a gate is an opening in the terminal's airside wall with a boarding bridge floor outside it
				boolean open = false;
				for (int v = air.terminalV0(); v <= air.terminalV1() + 1; v++) {
					final boolean gap = v <= air.terminalV1() && at(air.terminalU1(), StationGeometry.WALK_Y, v) == null
							&& at(air.terminalU1(), StationGeometry.WALK_Y + 1, v) == null;
					if (gap && !open) {
						found++;
						if (at(air.terminalU1() + 1, StationGeometry.PLATFORM_Y, v) != null
								&& at(air.terminalU1() + 1, StationGeometry.PLATFORM_Y, v).role() == Role.FLOOR) {
							bridged++;
						}
					}
					open = gap;
				}
			} else {
				for (int v = 0; v < air.length(); v++) {
					if (is(air.apronU0() + 10, StationGeometry.PLATFORM_Y, v, PlanAirport.PAINT_YELLOW)) {
						found++;
					}
				}
			}
			if (found != wanted || airport.terminal() && bridged != wanted) {
				add(Topic.GATES, Status.FAIL, found + " gates measured" + (airport.terminal() ? " (" + bridged + " with a boarding bridge)" : "") + "; asked for " + wanted,
						"Plan again; if it stays, copy the description and report it.");
			} else {
				add(Topic.GATES, Status.PASS, wanted + (wanted == 1 ? " gate" : " gates") + ", each a stand with a lead-in line and stop bar"
						+ (airport.terminal() ? " and a boarding bridge from the terminal" : ", out on the apron (no terminal)"), "");
			}
		}

		void terminal() {
			if (!airport.terminal()) {
				add(Topic.TERMINAL, Status.PASS, "No terminal asked for: the stands are remote", "");
				return;
			}
			final int door = (air.terminalV0() + air.terminalV1()) / 2;
			boolean blocked = false;
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; y++) {
				blocked |= at(air.terminalU0(), y, door) != null;
			}
			final PlannedBlock roof = at((air.terminalU0() + air.terminalU1()) / 2, air.dims().wallTop() + 1, door);
			if (blocked) {
				add(Topic.TERMINAL, Status.FAIL, "The terminal's entrance is blocked", "Plan again; if it stays, copy the description and report it.");
			} else if (roof == null) {
				add(Topic.TERMINAL, Status.FAIL, "The terminal has no roof", "Plan again; if it stays, copy the description and report it.");
			} else {
				add(Topic.TERMINAL, Status.PASS, "A terminal " + (air.terminalU1() - air.terminalU0() + 1) + " deep and "
						+ (air.terminalV1() - air.terminalV0() + 1) + " long with an entrance from the road, a roof and gate doors", "");
			}
		}
	}
}
