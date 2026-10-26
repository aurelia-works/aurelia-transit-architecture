package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the words of an airport description ("small airport with 2 runways, 3000 m runway, 6 gates and a terminal") into an
 * {@link AirportSpec}. {@link StationDescriptionParser} hands its tokens here as soon as one of them names an airport, so a
 * description without those words is read exactly as before. Metres become blocks at {@link AirportSpec#BLOCKS_PER_METRE}; what
 * the plan cannot hold yet is clamped with a warning, never silently.
 */
final class AirportWords {

	/** Words that make a description an airport. */
	private static final Set<String> TRIGGERS = Set.of("airport", "airports", "airfield", "aerodrome", "airstrip");
	private static final Set<String> RUNWAY = Set.of("runway", "runways");
	private static final Set<String> GATE = Set.of("gate", "gates");
	private static final Set<String> TERMINAL = Set.of("terminal", "terminals");
	private static final Set<String> ALWAYS = Set.of("taxiway", "taxiways", "apron", "aprons", "stand", "stands");
	private static final Set<String> SMALL = Set.of("small", "tiny", "regional", "minor", "local");
	private static final Set<String> LARGE = Set.of("large", "big", "major", "international", "huge");
	private static final Set<String> NO = Set.of("no", "without", "remote");
	/** Words for a railway station under the terminal ("airport with a train station", "transit", "rail link"). */
	private static final Set<String> RAIL = Set.of("train", "trains", "rail", "railway", "metro", "subway", "transit");
	private static final Set<String> RAIL_NOUN = Set.of("station", "link", "stop", "connection");
	private static final Map<String, Integer> NUMBERS = Map.ofEntries(Map.entry("one", 1), Map.entry("single", 1), Map.entry("two", 2),
			Map.entry("twin", 2), Map.entry("dual", 2), Map.entry("parallel", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5),
			Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
			Map.entry("twelve", 12));

	private static final int SMALL_LENGTH = 240;
	private static final int SMALL_WIDTH = 15;
	private static final int SMALL_GATES = 2;
	private static final int LARGE_LENGTH = AirportSpec.MAX_RUNWAY_LENGTH;
	private static final int LARGE_WIDTH = AirportSpec.MAX_RUNWAY_WIDTH;
	private static final int LARGE_GATES = 8;

	private AirportWords() {
	}

	/** A recognised phrase and the token where it started. */
	record Note(int at, String text) {
	}

	/** What the words came to: the airport, one line per phrase understood, per default taken, per clamp, and which tokens were used. */
	record Reading(AirportSpec spec, List<Note> understood, List<String> defaulted, List<String> warnings, boolean[] used) {
	}

	/** True when a token names an airport ("airport", "airfield", "airstrip") or a runway. */
	static boolean mentioned(String[] tokens) {
		for (final String token : tokens) {
			if (TRIGGERS.contains(token) || RUNWAY.contains(token)) {
				return true;
			}
		}
		return false;
	}

	static Reading read(String[] tokens) {
		return new Read(tokens).run();
	}

	private static final class Read {

		private final String[] tokens;
		private final boolean[] used;
		private final List<Note> understood = new ArrayList<>();
		private final List<String> defaulted = new ArrayList<>();
		private final List<String> warnings = new ArrayList<>();
		private Integer runways;
		private Integer length;
		private Integer width;
		private Integer gates;
		private Boolean terminal;
		private boolean rail;
		private int size;
		private boolean always;

		Read(String[] tokens) {
			this.tokens = tokens;
			this.used = new boolean[tokens.length];
		}

		private String at(int i) {
			return i >= 0 && i < tokens.length ? tokens[i] : "";
		}

		private void use(int from, int to) {
			for (int i = from; i <= to && i < used.length; i++) {
				if (i >= 0) {
					used[i] = true;
				}
			}
		}

		private static Integer digits(String token) {
			return !token.isEmpty() && token.length() <= 6 && token.chars().allMatch(Character::isDigit) ? Integer.valueOf(Integer.parseInt(token)) : null;
		}

		private static Integer number(String token) {
			final Integer n = digits(token);
			return n != null ? n : NUMBERS.get(token);
		}

		/** Blocks per unit word, or 0 when the token is not a unit. */
		private static double unit(String token) {
			return switch (token) {
				case "m", "metre", "metres", "meter", "meters" -> AirportSpec.BLOCKS_PER_METRE;
				case "km", "kilometre", "kilometres", "kilometer", "kilometers" -> AirportSpec.BLOCKS_PER_METRE * 1000;
				case "block", "blocks" -> 1;
				default -> 0;
			};
		}

		Reading run() {
			for (int i = 0; i < tokens.length; i++) {
				if (used[i]) {
					continue;
				}
				final String token = tokens[i];
				if (TRIGGERS.contains(token)) {
					use(i, i);
					note(i, "an airport: runway, taxiway, apron with gates");
				} else if (SMALL.contains(token) && airportNear(i)) {
					size = -1;
					use(i, i);
				} else if (LARGE.contains(token) && airportNear(i)) {
					size = 1;
					use(i, i);
				} else if (RUNWAY.contains(token)) {
					runwayWord(i);
				} else if (GATE.contains(token)) {
					gateWord(i);
				} else if (TERMINAL.contains(token)) {
					terminalWord(i);
				} else if (RAIL.contains(token)) {
					railWord(i);
				} else if (ALWAYS.contains(token)) {
					use(i, i);
					always = true;
				} else if (number(token) != null) {
					numberWord(i);
				}
			}
			if (always) {
				note(0, "taxiway and apron: always built");
			}
			return new Reading(resolve(), understood, defaulted, warnings, used);
		}

		private boolean airportNear(int i) {
			for (int j = Math.max(0, i - 1); j <= Math.min(tokens.length - 1, i + 2); j++) {
				if (TRIGGERS.contains(tokens[j]) || RUNWAY.contains(tokens[j])) {
					return true;
				}
			}
			return false;
		}

		private void note(int at, String text) {
			understood.add(new Note(at, text));
		}

		/** "2 runways", "3000 m runway", "runway 480 long", "runway length 500", "runway 30 wide". */
		private void runwayWord(int i) {
			final String next = at(i + 1);
			int j = i + 1;
			boolean lengthWord = false;
			boolean widthWord = false;
			if (next.equals("length") || next.equals("long")) {
				lengthWord = true;
				j++;
			} else if (next.equals("width") || next.equals("wide")) {
				widthWord = true;
				j++;
			}
			final Integer n = digits(at(j));
			if (n != null) {
				final double factor = unit(at(j + 1));
				int end = factor > 0 ? j + 1 : j;
				final String tail = at(end + 1);
				final boolean wide = widthWord || tail.equals("wide") || tail.equals("width");
				if (tail.equals("wide") || tail.equals("width") || tail.equals("long")) {
					end++;
				}
				setSize(wide, n, factor > 0 ? factor : 1, i, end);
				return;
			}
			use(i, lengthWord || widthWord ? i + 1 : i);
			if (runways == null) {
				runways = 1;
				note(i, "a runway");
			}
		}

		private void gateWord(int i) {
			final Integer n = number(at(i + 1));
			if (n != null) {
				setGates(n, i, i + 1);
				return;
			}
			use(i, i);
			if (gates == null) {
				note(i, "gates at the terminal");
			}
		}

		private void terminalWord(int i) {
			final boolean none = NO.contains(at(i - 1));
			use(none ? i - 1 : i, i);
			if (terminal == null) {
				terminal = !none;
				note(none ? i - 1 : i, none ? "no terminal: the stands are remote, out on the apron" : "a terminal building");
			}
		}

		/** "train station", "rail link", "metro", "transit": a railway station under the terminal; "no train station" says none. */
		private void railWord(int i) {
			final boolean none = NO.contains(at(i - 1));
			final int end = RAIL_NOUN.contains(at(i + 1)) ? i + 1 : i;
			use(none ? i - 1 : i, end);
			if (!none && !rail) {
				rail = true;
				note(i, "a railway station under the terminal");
			}
		}

		/** A number with its unit and noun: "2 runways", "6 gates", "3000 m runway", "480 long", "30 wide". */
		private void numberWord(int i) {
			final int n = number(tokens[i]);
			final double factor = unit(at(i + 1));
			int j = factor > 0 ? i + 2 : i + 1;
			final String noun = at(j);
			if (RUNWAY.contains(noun)) {
				if (factor == 0 && !at(j + 1).equals("long") && !at(j + 1).equals("wide")) {
					if (runways != null && runways != n) {
						warn("runway count given twice; using " + runways);
					} else if (runways == null) {
						runways = n;
						note(i, n == 1 ? "a runway" : n + " runways");
					}
					use(i, j);
				} else {
					setSize(false, n, factor == 0 ? 1 : factor, i, j);
				}
			} else if (GATE.contains(noun) && factor == 0) {
				setGates(n, i, j);
			} else if (TERMINAL.contains(noun) && factor == 0) {
				use(i, j);
				if (terminal == null) {
					terminal = true;
					note(i, "a terminal building");
				}
			} else if ((noun.equals("long") || noun.equals("wide") || noun.equals("width")) && airportAround()) {
				int end = j;
				if (RUNWAY.contains(at(j + 1))) {
					end++;
				}
				setSize(!noun.equals("long"), n, factor == 0 ? 1 : factor, i, end);
			}
		}

		private boolean airportAround() {
			for (final String token : tokens) {
				if (TRIGGERS.contains(token) || RUNWAY.contains(token)) {
					return true;
				}
			}
			return false;
		}

		private void setSize(boolean wide, int n, double factor, int from, int to) {
			use(from, to);
			final int blocks = (int) Math.round(n * factor);
			final String shown = factor == 1 ? blocks + " blocks" : n + (factor == AirportSpec.BLOCKS_PER_METRE ? " m" : " km") + " = " + blocks + " blocks";
			if (wide) {
				if (width == null) {
					width = blocks;
					note(from, "runway " + shown + " wide");
				} else {
					warn("runway width given twice; using " + width);
				}
			} else if (length == null) {
				length = blocks;
				note(from, "runway " + shown + " long");
			} else {
				warn("runway length given twice; using " + length);
			}
		}

		private void setGates(int n, int from, int to) {
			use(from, to);
			if (gates == null) {
				gates = n;
				note(from, n == 1 ? "1 gate" : n + " gates");
			} else if (gates != n) {
				warn("gate count given twice; using " + gates);
			}
		}

		private void warn(String text) {
			if (!warnings.contains(text)) {
				warnings.add(text);
			}
		}

		private AirportSpec resolve() {
			final int baseLength = size < 0 ? SMALL_LENGTH : size > 0 ? LARGE_LENGTH : AirportSpec.DEFAULT_RUNWAY_LENGTH;
			final int baseWidth = size < 0 ? SMALL_WIDTH : size > 0 ? LARGE_WIDTH : AirportSpec.DEFAULT_RUNWAY_WIDTH;
			final int baseGates = size < 0 ? SMALL_GATES : size > 0 ? LARGE_GATES : AirportSpec.DEFAULT_GATES;
			final String sizeName = size < 0 ? "small airport" : size > 0 ? "large airport" : "";
			if (size != 0) {
				note(0, sizeName + ": runway " + baseLength + " long, " + baseWidth + " wide, " + baseGates + " gates unless said");
			}
			int r = 1;
			if (runways != null) {
				r = runways;
				if (r < 1 || r > AirportSpec.MAX_RUNWAYS) {
					r = Math.max(1, Math.min(AirportSpec.MAX_RUNWAYS, r));
					warn(runways + " runways is outside 1.." + AirportSpec.MAX_RUNWAYS + "; using " + r);
				}
			} else {
				defaulted.add("1 runway (default)");
			}
			int wid = baseWidth;
			if (width != null) {
				wid = width;
				if (wid < AirportSpec.MIN_RUNWAY_WIDTH || wid > AirportSpec.MAX_RUNWAY_WIDTH) {
					wid = Math.max(AirportSpec.MIN_RUNWAY_WIDTH, Math.min(AirportSpec.MAX_RUNWAY_WIDTH, wid));
					warn("a runway " + width + " blocks wide is outside " + AirportSpec.MIN_RUNWAY_WIDTH + ".." + AirportSpec.MAX_RUNWAY_WIDTH + "; using " + wid);
				}
			} else {
				defaulted.add("runway " + wid + " blocks wide (default" + (size != 0 ? " for a " + sizeName : "") + ")");
			}
			int len = baseLength;
			if (length != null) {
				len = length;
				final int least = AirportSpec.minLength(wid);
				if (len < least || len > AirportSpec.MAX_RUNWAY_LENGTH) {
					len = Math.max(least, Math.min(AirportSpec.MAX_RUNWAY_LENGTH, len));
					warn("a runway " + length + " blocks long is outside " + least + ".." + AirportSpec.MAX_RUNWAY_LENGTH
							+ " here (the plan holds every block in memory, so the longest runways of real airports do not fit yet); using " + len);
				}
			} else {
				len = Math.max(len, AirportSpec.minLength(wid));
				defaulted.add("runway " + len + " blocks long (default" + (size != 0 ? " for a " + sizeName : "") + ")");
			}
			final int fit = Math.min(AirportSpec.MAX_GATES, AirportSpec.maxGates(len, wid));
			int g = baseGates;
			if (gates != null) {
				g = gates;
				if (g < AirportSpec.MIN_GATES || g > fit) {
					g = Math.max(AirportSpec.MIN_GATES, Math.min(fit, g));
					warn(gates + " gates do not fit along a runway " + len + " blocks long (1.." + fit + "); using " + g);
				}
			} else {
				g = Math.min(g, fit);
				defaulted.add(g + " gates (default" + (size != 0 ? " for a " + sizeName : "") + (g < baseGates ? ", all that fit" : "") + ")");
			}
			final boolean t = terminal == null || terminal;
			if (terminal == null) {
				defaulted.add("a terminal building (default)");
			}
			AirportSpec spec = new AirportSpec(r, len, wid, g, t);
			if (rail) {
				if (!t) {
					warn("a railway station needs the terminal over it; left out");
				} else if (!AirportStation.fits(spec)) {
					warn("a railway station fits only under a terminal at least " + AirportStation.MIN_TERMINAL_DEPTH
							+ " deep (a runway " + AirportGeometry.REAL_WIDTH + " or more wide); left out");
				} else {
					spec = spec.withRail(true);
				}
			}
			return spec;
		}
	}
}
