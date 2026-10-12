package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a short English description ("two track island station, 64 blocks long, dutch style, glass canopy, named Leidsche Rijn")
 * into a {@link StationSpec}. Deterministic, local and bounded: a fixed vocabulary, no randomness, no network.
 *
 * <p>Terrain changes are never approved by the description, whatever it says; only the confirm command's literal token does.
 */
public final class StationDescriptionParser {

	/** Longer descriptions are cut to this many characters (with a warning). */
	public static final int MAX_INPUT = 1200;
	/** Tracks for "large" or "big" without a number. */
	static final int LARGE_TRACKS = 4;
	/**
	 * A terminus is a city's main station: without a number it gets this many tracks and this length. A "city" terminus gets
	 * {@link #LARGE_TRACKS} and 96; "small", "compact", "halt", "village" and "rural" keep the ordinary defaults.
	 */
	static final int TERMINUS_TRACKS = 8;
	static final int TERMINUS_LENGTH = 160;
	/** A "large" or "big" terminus without a number. */
	static final int LARGE_TERMINUS_TRACKS = 10;
	static final int LARGE_TERMINUS_LENGTH = 200;

	private static final Pattern QUOTED_NAME = Pattern.compile("(?i)\\b(?:named|called|name)\\s*\"([^\"]*)\"");
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
	private static final Pattern BARE_NAME = Pattern.compile("(?i)\\b(?:named|called)\\s+([^,.;\"]+)");
	private static final Pattern NAME_STOP = Pattern.compile("(?i)\\s+\\b(?:with|and|having|style|styled|in|using)\\b.*$");
	private static final Pattern SEPARATORS = Pattern.compile("[-_/,.;:!?()\"]+");
	private static final Pattern DIGITS_THEN_LETTERS = Pattern.compile("(\\d+)([a-z]+)");

	private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(Map.entry("one", 1), Map.entry("two", 2),
			Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
			Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12));
	/** Only count before "track(s)": "double track", "single-track". */
	private static final Map<String, Integer> TRACK_WORDS = Map.of("single", 1, "double", 2, "twin", 2, "triple", 3, "quadruple", 4, "quad", 4);
	private static final Set<String> METRE_UNITS = Set.of("m", "metre", "metres", "meter", "meters");
	private static final Set<String> UNITS = Set.of("m", "metre", "metres", "meter", "meters", "block", "blocks");
	private static final Set<String> TRACK_NOUNS = Set.of("track", "tracks");
	private static final Set<String> PLATFORM_NOUNS = Set.of("platform", "platforms");
	private static final Set<String> ROOF_NOUNS = Set.of("roof", "canopy", "canopies");

	private static final Map<String, StationStyle> STYLE_WORDS = Map.ofEntries(Map.entry("modern", StationStyle.MODERN),
			Map.entry("contemporary", StationStyle.MODERN), Map.entry("generic", StationStyle.MODERN), Map.entry("default", StationStyle.MODERN),
			Map.entry("simple", StationStyle.MODERN), Map.entry("standard", StationStyle.MODERN), Map.entry("dutch", StationStyle.DUTCH),
			Map.entry("netherlands", StationStyle.DUTCH), Map.entry("holland", StationStyle.DUTCH), Map.entry("ns", StationStyle.DUTCH),
			Map.entry("utrecht", StationStyle.DUTCH), Map.entry("leidsche", StationStyle.DUTCH), Map.entry("amsterdam", StationStyle.DUTCH),
			Map.entry("rotterdam", StationStyle.DUTCH), Map.entry("german", StationStyle.GERMAN_SBAHN), Map.entry("germany", StationStyle.GERMAN_SBAHN),
			Map.entry("db", StationStyle.GERMAN_SBAHN), Map.entry("sbahn", StationStyle.GERMAN_SBAHN), Map.entry("hamburg", StationStyle.GERMAN_SBAHN),
			Map.entry("munich", StationStyle.GERMAN_SBAHN), Map.entry("munchen", StationStyle.GERMAN_SBAHN), Map.entry("berlin", StationStyle.GERMAN_SBAHN),
			Map.entry("frankfurt", StationStyle.GERMAN_SBAHN), Map.entry("metro", StationStyle.METRO), Map.entry("subway", StationStyle.METRO),
			Map.entry("underground", StationStyle.METRO), Map.entry("ubahn", StationStyle.METRO), Map.entry("tube", StationStyle.METRO));
	private static final Set<String> UNDERGROUND_WORDS = Set.of("underground", "subway", "tunnel", "tube");
	private static final Set<String> ELEVATED_WORDS = Set.of("elevated", "viaduct");
	/** City words that pick a station building's look when a building is asked for (some also pick the platform style). */
	private static final Map<String, BuildingStyle> CITY_BUILDINGS = Map.ofEntries(Map.entry("utrecht", BuildingStyle.MODERN),
			Map.entry("leidsche", BuildingStyle.MODERN), Map.entry("berlin", BuildingStyle.MODERN),
			Map.entry("amsterdam", BuildingStyle.AMSTERDAM), Map.entry("hamburg", BuildingStyle.CLINKER), Map.entry("munich", BuildingStyle.CONCRETE),
			Map.entry("munchen", BuildingStyle.CONCRETE), Map.entry("frankfurt", BuildingStyle.SANDSTONE), Map.entry("london", BuildingStyle.GOTHIC_BRICK),
			Map.entry("pancras", BuildingStyle.GOTHIC_BRICK), Map.entry("gothic", BuildingStyle.GOTHIC_BRICK), Map.entry("victorian", BuildingStyle.GOTHIC_BRICK),
			Map.entry("antwerp", BuildingStyle.ANTWERP), Map.entry("antwerpen", BuildingStyle.ANTWERP), Map.entry("belgian", BuildingStyle.ANTWERP),
			Map.entry("philadelphia", BuildingStyle.BEAUX_ARTS), Map.entry("washington", BuildingStyle.BEAUX_ARTS), Map.entry("chicago", BuildingStyle.BEAUX_ARTS),
			Map.entry("american", BuildingStyle.BEAUX_ARTS), Map.entry("beaux", BuildingStyle.BEAUX_ARTS), Map.entry("classical", BuildingStyle.BEAUX_ARTS),
			Map.entry("brick", BuildingStyle.AMSTERDAM), Map.entry("sandstone", BuildingStyle.SANDSTONE), Map.entry("limestone", BuildingStyle.ANTWERP),
			Map.entry("marble", BuildingStyle.ANTWERP), Map.entry("clinker", BuildingStyle.CLINKER), Map.entry("brutalist", BuildingStyle.CONCRETE),
			Map.entry("america", BuildingStyle.BEAUX_ARTS), Map.entry("usa", BuildingStyle.BEAUX_ARTS), Map.entry("us", BuildingStyle.BEAUX_ARTS),
			Map.entry("amtrak", BuildingStyle.BEAUX_ARTS), Map.entry("nyc", BuildingStyle.BEAUX_ARTS), Map.entry("york", BuildingStyle.BEAUX_ARTS),
			Map.entry("boston", BuildingStyle.BEAUX_ARTS), Map.entry("english", BuildingStyle.GOTHIC_BRICK), Map.entry("british", BuildingStyle.GOTHIC_BRICK),
			Map.entry("uk", BuildingStyle.GOTHIC_BRICK), Map.entry("leipzig", BuildingStyle.SANDSTONE), Map.entry("cologne", BuildingStyle.CLINKER),
			Map.entry("koln", BuildingStyle.CLINKER), Map.entry("brussels", BuildingStyle.ANTWERP), Map.entry("rotterdam", BuildingStyle.MODERN));
	/** Famous stations: naming one asks for its building. */
	private static final Set<String> LANDMARKS = Set.of("pancras", "antwerp", "antwerpen", "philadelphia", "washington", "chicago");
	/** Words that ask for a station building with a furnished interior. */
	private static final Set<String> BUILDING_WORDS = Set.of("building", "buildings", "concourse", "interior", "interiors", "interiorized",
			"interiorised", "furnished", "ticket", "tickets", "entrance", "centraal", "central", "centrale", "hauptbahnhof", "hbf", "terminal",
			"terminus", "termini", "kopfbahnhof", "depot", "hub", "interchange");
	/** Words that ask for a grand station building (and so for a building at all). */
	private static final Set<String> GRAND_WORDS = Set.of("grand", "monumental", "majestic", "palatial", "imposing", "magnificent",
			"hauptbahnhof", "hbf");
	/** Words that ask for a city-sized station building. */
	private static final Set<String> CITY_WORDS = Set.of("city", "regional", "town");
	/** Words that ask for a compact station building. */
	private static final Set<String> COMPACT_WORDS = Set.of("compact", "halt", "village", "rural");
	/** Words that make the tracks end at buffer stops in front of a head building. */
	private static final Set<String> TERMINUS_WORDS = Set.of("terminus", "termini", "terminal", "kopfbahnhof");
	/** A terminus whose outer tracks run on through: some trains end here, some carry on. */
	private static final Set<String> HUB_WORDS = Set.of("hub", "interchange");
	private static final Set<String> FOOTBRIDGE_WORDS = Set.of("footbridge", "footbridges", "overbridge", "overpass", "bridge", "skywalk");
	private static final Set<String> TERRAIN_WORDS = Set.of("clear", "flatten", "level", "demolish", "replace", "remove", "terraform", "dig");
	private static final Set<String> HALL_WORDS = Set.of("hall", "trainshed", "shed");
	/** Shed shapes; each also asks for a hall roof. */
	private static final Map<String, ShedShape> SHED_WORDS = Map.ofEntries(Map.entry("arch", ShedShape.ARCH), Map.entry("arched", ShedShape.ARCH),
			Map.entry("barrel", ShedShape.ARCH), Map.entry("vault", ShedShape.ARCH), Map.entry("vaulted", ShedShape.ARCH), Map.entry("curved", ShedShape.ARCH),
			Map.entry("pointed", ShedShape.POINTED), Map.entry("arches", ShedShape.MULTI_ARCH), Map.entry("vaults", ShedShape.MULTI_ARCH),
			Map.entry("multispan", ShedShape.MULTI_ARCH));
	/** Words that say several looks are mixed ("Utrecht and Hamburg combined"); read so they are not reported as ignored. */
	private static final Set<String> MIX_WORDS = Set.of("combined", "combine", "combo", "mix", "mixed", "mixture", "hybrid", "fusion", "meets",
			"cross", "crossed", "x", "blend", "blended", "inspired", "inspiration", "both", "between", "together", "merged", "merge");
	private static final Set<String> CANOPY_WORDS = Set.of("canopy", "canopies", "covered", "roofed", "shelter", "shelters");
	private static final Set<String> OPEN_WORDS = Set.of("open", "uncovered", "roofless");
	private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "with", "and", "or", "of", "for", "to", "in", "on", "at", "by", "as",
			"from", "into", "please", "build", "make", "create", "generate", "give", "me", "i", "we", "want", "need", "would", "like", "some",
			"any", "very", "really", "also", "plus", "station", "stations", "railway", "rail", "train", "trains", "platform", "platforms",
			"track", "tracks", "style", "styled", "type", "kind", "it", "its", "that", "this", "is", "are", "be", "have", "has", "new", "nice",
			"little", "no", "not", "without", "only", "s", "u");

	private StationDescriptionParser() {
	}

	public static ParseResult parse(String description) {
		return new Parse().run(description == null ? "" : description);
	}

	/** A recognised phrase and the token where it started, so feedback comes out in text order. */
	private record Note(int at, String text) {
	}

	/** A city or look word: the building look it gives, and the platform style it gives (null for looks such as "american"). */
	private record Look(int at, String word, BuildingStyle building, StationStyle style) {
	}

	/** One parse: the tokens, what each was used for, and what the text set. Null fields were not mentioned. */
	private static final class Parse {

		private String[] tokens = new String[0];
		private boolean[] used = new boolean[0];
		private final List<Note> understood = new ArrayList<>();
		private final List<String> defaulted = new ArrayList<>();
		private final List<String> warnings = new ArrayList<>();
		private final Set<String> warned = new LinkedHashSet<>();

		private Integer tracks;
		private int tracksAt = -1;
		private Integer islandPlatforms;
		private boolean sidePlatforms;
		private int islandAt = -1;
		private int sideAt = -1;
		private Integer length;
		private Integer lengthAdjective;
		private Integer width;
		private boolean wide;
		private boolean narrow;
		private boolean small;
		private boolean large;
		private StationStyle style;
		private boolean roofNone;
		private RoofType roof;
		private boolean weakRoof;
		private RoofMaterial roofMaterial;
		private SurfaceMaterial surface;
		private Boolean stepFree;
		private Boolean tactile;
		private Boolean furniture;
		private Boolean railNodes;
		private String name = "";
		private Boolean building;
		private int buildingAt = -1;
		private BuildingStyle buildingStyle;
		private int buildingStyleAt = -1;
		private Boolean footbridge;
		private boolean terminus;
		private int terminusAt = -1;
		private boolean hub;
		private int hubAt = -1;
		/** The first word that only sets a building's look (e.g. "brick"), to warn about when no building was asked for. */
		private String lookOnlyWord;
		/** City and look words in text order; mixed together when there are several. */
		private final List<Look> looks = new ArrayList<>();
		private boolean mixing;
		private ShedShape shed;
		private StationScale scale;
		private int scaleAt = -1;

		ParseResult run(String description) {
			String text = description;
			if (text.length() > MAX_INPUT) {
				text = text.substring(0, MAX_INPUT);
				warn("the description is longer than " + MAX_INPUT + " characters; the rest was ignored");
			}
			text = extractName(text);
			tokens = tokenize(text);
			used = new boolean[tokens.length];
			for (int i = 0; i < tokens.length; i++) {
				if (!used[i]) {
					scan(i);
				}
			}
			final StationSpec spec = resolve(description.isBlank());
			final List<String> ignored = new ArrayList<>(new LinkedHashSet<>(ignoredWords()));
			understood.sort(Comparator.comparingInt(Note::at));
			final List<String> understoodLines = new ArrayList<>();
			for (final Note note : understood) {
				understoodLines.add(note.text());
			}
			return new ParseResult(spec, understoodLines, defaulted, warnings, ignored);
		}

		// ---- name -------------------------------------------------------------------------------------------------------

		/** Takes the station name out of the text so words inside it are never read as keywords. */
		private String extractName(String text) {
			Matcher matcher = QUOTED_NAME.matcher(text);
			if (!matcher.find()) {
				matcher = QUOTED.matcher(text);
				if (!matcher.find()) {
					matcher = BARE_NAME.matcher(text);
					if (!matcher.find()) {
						return text;
					}
					final String raw = NAME_STOP.matcher(matcher.group(1)).replaceFirst("");
					setName(raw);
					return text.substring(0, matcher.start()) + " " + text.substring(matcher.start(1) + raw.length());
				}
			}
			setName(matcher.group(1));
			return text.substring(0, matcher.start()) + " " + text.substring(matcher.end());
		}

		private void setName(String raw) {
			String clean = raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
			if (clean.length() > StationSpec.MAX_NAME) {
				clean = clean.substring(0, StationSpec.MAX_NAME).trim();
				warn("the name is longer than " + StationSpec.MAX_NAME + " characters; using \"" + clean + "\"");
			}
			name = clean;
			if (!clean.isEmpty()) {
				understood.add(new Note(-1, "name \"" + clean + "\""));
			}
		}

		private static String[] tokenize(String text) {
			String normal = SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ");
			normal = DIGITS_THEN_LETTERS.matcher(normal).replaceAll("$1 $2");
			normal = normal.trim();
			return normal.isEmpty() ? new String[0] : normal.split("\\s+");
		}

		// ---- scanning ---------------------------------------------------------------------------------------------------

		private String at(int i) {
			return i >= 0 && i < tokens.length ? tokens[i] : "";
		}

		private void use(int from, int to) {
			Arrays.fill(used, from, Math.min(to + 1, used.length), true);
		}

		private void note(int at, String text) {
			understood.add(new Note(at, text));
		}

		private void warn(String text) {
			if (warned.add(text)) {
				warnings.add(text);
			}
		}

		private void scan(int i) {
			final String token = tokens[i];
			if (negation(i) || number(i) || phrase(i)) {
				return;
			}
			keyword(i, token);
		}

		/** "no roof", "without ramps", "not accessible": the keyword may be up to 2 words later. */
		private boolean negation(int i) {
			final String token = tokens[i];
			if (!token.equals("no") && !token.equals("without") && !token.equals("not")) {
				return false;
			}
			for (int j = i + 1; j <= i + 2 && j < tokens.length; j++) {
				final String word = tokens[j];
				if (ROOF_NOUNS.contains(word)) {
					roofNone = true;
					note(i, "no roof");
				} else if (word.equals("ramp") || word.equals("ramps")) {
					stepFree = false;
					note(i, "no ramps");
				} else if (word.equals("accessible")) {
					stepFree = false;
					note(i, "no ramps");
				} else if (word.equals("tactile")) {
					tactile = false;
					note(i, "no tactile paving");
				} else if (word.equals("furniture") || word.equals("benches")) {
					furniture = false;
					note(i, "no furniture");
				} else if (word.equals("building") || word.equals("buildings") || word.equals("interior")) {
					building = false;
					note(i, "no station building");
				} else if (FOOTBRIDGE_WORDS.contains(word)) {
					footbridge = false;
					note(i, "no footbridge");
				} else if (word.equals("rails") || word.equals("nodes") || word.equals("rail") && at(j + 1).equals("nodes")) {
					railNodes = false;
					note(i, "no rail nodes");
					if (word.equals("rail")) {
						j++;
					}
				} else {
					continue;
				}
				use(i, j);
				return true;
			}
			return false;
		}

		private static Integer value(String token) {
			if (!token.isEmpty() && token.length() <= 6 && token.chars().allMatch(Character::isDigit)) {
				return Integer.parseInt(token);
			}
			return NUMBER_WORDS.get(token);
		}

		private boolean number(int i) {
			final String token = tokens[i];
			final Integer trackWord = TRACK_WORDS.get(token);
			if (trackWord != null && TRACK_NOUNS.contains(at(i + 1))) {
				setTracks(trackWord, i);
				use(i, i + 1);
				return true;
			}
			final Integer n = value(token);
			if (n == null) {
				return false;
			}
			// <n> track(s), <n> platform track(s), <n> rail tracks
			if (TRACK_NOUNS.contains(at(i + 1))) {
				setTracks(n, i);
				use(i, i + 1);
				return true;
			}
			if ((at(i + 1).equals("platform") || at(i + 1).equals("rail")) && TRACK_NOUNS.contains(at(i + 2))) {
				setTracks(n, i);
				use(i, i + 2);
				return true;
			}
			// <n> island platforms, <n> side platforms
			if (at(i + 1).equals("island") && PLATFORM_NOUNS.contains(at(i + 2))) {
				islandPlatforms = n;
				islandMention(i);
				use(i, i + 2);
				return true;
			}
			if (at(i + 1).equals("side") && PLATFORM_NOUNS.contains(at(i + 2))) {
				sideMention(i);
				use(i, i + 2);
				return true;
			}
			// <n> [unit] long|length|wide|width, or <n> metres alone
			int j = i + 1;
			final String unit = at(j);
			if (UNITS.contains(unit)) {
				j++;
			}
			final String word = at(j);
			if (word.equals("long") || word.equals("length")) {
				setLength(n, i);
				use(i, j);
				return true;
			}
			if (word.equals("wide") || word.equals("width")) {
				setWidth(n, i);
				use(i, j);
				return true;
			}
			if (METRE_UNITS.contains(unit)) {
				setLength(n, i);
				use(i, i + 1);
				return true;
			}
			use(i, i);
			return true;
		}

		/** "length [of] <n> [unit]", "platform length 80", "width 5". */
		private boolean measured(int i, boolean isLength) {
			int j = i + 1;
			if (at(j).equals("of")) {
				j++;
			}
			final Integer n = value(at(j));
			if (n == null) {
				return false;
			}
			if (UNITS.contains(at(j + 1))) {
				j++;
			}
			if (isLength) {
				setLength(n, i);
			} else {
				setWidth(n, i);
			}
			use(i, j);
			return true;
		}

		private boolean phrase(int i) {
			final String token = tokens[i];
			final String next = at(i + 1);
			switch (token) {
				case "length":
					return measured(i, true);
				case "width":
					return measured(i, false);
				case "s":
				case "u":
					if (next.equals("bahn")) {
						setStyle(token.equals("s") ? StationStyle.GERMAN_SBAHN : StationStyle.METRO, i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "leidsche":
					setStyle(StationStyle.DUTCH, i);
					use(i, next.equals("rijn") ? i + 1 : i);
					return true;
				case "train":
					if (next.equals("shed")) {
						setRoof(RoofType.HALL, i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "flat":
					if (next.equals("roof") || HALL_WORDS.contains(next)) {
						if (shed == null) {
							shed = ShedShape.FLAT;
						}
						setRoof(RoofType.HALL, i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "single":
				case "multi":
				case "multiple":
				case "several":
				case "triple":
				case "twin":
					if (next.equals("span") || next.equals("arch") || next.equals("arches") || next.equals("vault") || next.equals("vaults")) {
						if (shed == null) {
							shed = token.equals("single") ? ShedShape.ARCH : ShedShape.MULTI_ARCH;
							note(i, shed == ShedShape.ARCH ? "one arched train shed" : "train shed of several arches");
						}
						if (roof == null) {
							roof = RoofType.HALL;
						}
						use(i, i + 1);
						return true;
					}
					return false;
				case "new":
					if (next.equals("york")) {
						setBuildingStyle(BuildingStyle.BEAUX_ARTS, i);
						if (looks.stream().noneMatch(look -> look.building() == BuildingStyle.BEAUX_ARTS)) {
							looks.add(new Look(i, "new york", BuildingStyle.BEAUX_ARTS, null));
						}
						use(i, i + 1);
						return true;
					}
					return false;
				case "overall":
				case "full":
				case "big":
					if (next.equals("roof")) {
						setRoof(RoofType.HALL, i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "step":
				case "barrier":
					if (next.equals("free")) {
						setStepFree(true, i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "stairs":
					if (next.equals("only")) {
						stepFree = false;
						note(i, "no ramps");
						use(i, i + 1);
						return true;
					}
					return false;
				case "very":
					if (next.equals("long")) {
						lengthAdjective = 128;
						note(i, "length 128");
						use(i, i + 1);
						return true;
					}
					return false;
				case "light":
				case "dark":
					if (next.equals("paving") || next.equals("floor") || PLATFORM_NOUNS.contains(next)) {
						setSurface(token.equals("light") ? SurfaceMaterial.LIGHT_PAVING : SurfaceMaterial.DARK_PAVING, i);
						use(i, next.equals("paving") || next.equals("floor") ? i + 1 : i);
						return true;
					}
					return false;
				case "wood":
				case "wooden":
					if (ROOF_NOUNS.contains(next) || ROOF_NOUNS.contains(at(i + 2))) {
						setRoofMaterial(RoofMaterial.TIMBER, i);
						use(i, i);
						return true;
					}
					return false;
				default:
					return false;
			}
		}

		private void keyword(int i, String token) {
			if (buildingWord(i, token)) {
				return;
			}
			final StationStyle styleWord = STYLE_WORDS.get(token);
			if (UNDERGROUND_WORDS.contains(token)) {
				warn("underground stations are not built yet; building at ground level with metro finishes");
			}
			if (styleWord != null) {
				setStyle(styleWord, i);
			} else if (UNDERGROUND_WORDS.contains(token)) {
				setStyle(StationStyle.METRO, i);
			} else if (ELEVATED_WORDS.contains(token)) {
				warn("elevated stations are not built yet; building at ground level");
			} else if (TERRAIN_WORDS.contains(token)) {
				warn("terrain changes are only approved with /ata_station confirm replace_terrain");
			} else if (token.equals("island")) {
				islandMention(i);
			} else if (token.equals("side")) {
				sideMention(i);
			} else if (HALL_WORDS.contains(token) || token.equals("trainshed")) {
				setRoof(RoofType.HALL, i);
			} else if (CANOPY_WORDS.contains(token)) {
				setRoof(RoofType.CANOPY, i);
			} else if (token.equals("roof")) {
				weakRoof = true;
			} else if (OPEN_WORDS.contains(token)) {
				roofNone = true;
				note(i, "no roof");
			} else if (token.equals("glass") || token.equals("glazed") || token.equals("skylight") || token.equals("skylights")) {
				setRoofMaterial(RoofMaterial.GLASS, i);
			} else if (token.equals("timber")) {
				setRoofMaterial(RoofMaterial.TIMBER, i);
			} else if (token.equals("steel") || token.equals("metal")) {
				setRoofMaterial(RoofMaterial.STEEL, i);
			} else if (token.equals("concrete")) {
				setSurface(SurfaceMaterial.CONCRETE, i);
			} else if (token.equals("terrazzo")) {
				setSurface(SurfaceMaterial.TERRAZZO, i);
			} else if (token.equals("granite")) {
				setSurface(SurfaceMaterial.GRANITE, i);
			} else if (token.equals("accessible") || token.equals("wheelchair") || token.equals("ramp") || token.equals("ramps")) {
				setStepFree(true, i);
			} else if (token.equals("tactile")) {
				if (tactile == null) {
					tactile = true;
					note(i, "tactile paving");
				}
			} else if (token.equals("benches") || token.equals("furniture") || token.equals("furnished")) {
				if (furniture == null) {
					furniture = true;
					note(i, "furniture");
				}
			} else if (token.equals("bare") || token.equals("minimal")) {
				furniture = false;
				note(i, "no furniture");
			} else if (token.equals("short") || token.equals("medium") || token.equals("long")) {
				if (lengthAdjective == null) {
					lengthAdjective = token.equals("short") ? 24 : token.equals("medium") ? 48 : 96;
					note(i, "length " + lengthAdjective);
				}
			} else if (token.equals("wide")) {
				wide = true;
				note(i, "wide platforms");
			} else if (token.equals("narrow")) {
				narrow = true;
				note(i, "narrow platforms");
			} else if (GRAND_WORDS.contains(token)) {
				setScale(StationScale.GRAND, i);
				askBuilding(i);
			} else if (CITY_WORDS.contains(token)) {
				setScale(StationScale.CITY, i);
			} else if (COMPACT_WORDS.contains(token)) {
				setScale(StationScale.COMPACT, i);
			} else if (token.equals("small")) {
				small = true;
				setScale(StationScale.COMPACT, i);
				note(i, "small station");
			} else if (token.equals("large") || token.equals("big") || token.equals("major")) {
				large = true;
				note(i, "large station");
			} else {
				return;
			}
			use(i, i);
		}

		/** Landmarks, city looks, building and footbridge words; true if the token was used here. */
		private boolean buildingWord(int i, String token) {
			if (token.equals("grand") && at(i + 1).equals("central") || token.equals("union") && at(i + 1).equals("station")
					|| token.equals("30th") && at(i + 1).equals("street")) {
				setBuildingStyle(BuildingStyle.BEAUX_ARTS, i);
				askBuilding(i);
				if (scale == null) {
					// the landmark's own size; a size word said before it wins without a warning
					scale = StationScale.GRAND;
					scaleAt = i;
				}
				use(i, i + 1);
				return true;
			}
			if (token.equals("ticket") && (at(i + 1).equals("hall") || at(i + 1).equals("office") || at(i + 1).equals("halls"))) {
				askBuilding(i);
				use(i, i + 1);
				return true;
			}
			if ((token.equals("st") || token.equals("saint")) && at(i + 1).equals("pancras")) {
				use(i, i);
				return true;
			}
			final BuildingStyle city = CITY_BUILDINGS.get(token);
			if (city != null && !(token.equals("us") && !looks.isEmpty())) {
				setBuildingStyle(city, i);
				if (LANDMARKS.contains(token)) {
					askBuilding(i);
				}
				final StationStyle platformStyle = token.equals("antwerp") || token.equals("antwerpen") || token.equals("belgian")
						|| token.equals("brussels") ? StationStyle.DUTCH : STYLE_WORDS.get(token);
				if (looks.stream().noneMatch(look -> look.building() == city && look.style() == platformStyle)) {
					looks.add(new Look(i, token, city, platformStyle));
				}
				if (lookOnlyWord == null && platformStyle == null && !LANDMARKS.contains(token)) {
					lookOnlyWord = token;
				}
				use(i, i);
				return true;
			}
			if (MIX_WORDS.contains(token)) {
				mixing = true;
				use(i, i);
				return true;
			}
			final ShedShape shape = SHED_WORDS.get(token);
			if (shape != null) {
				if (shed == null) {
					shed = shape;
					note(i, switch (shape) {
						case ARCH -> "arched train shed";
						case POINTED -> "pointed-arch train shed";
						case MULTI_ARCH -> "train shed of several arches";
						case FLAT -> "flat train shed";
					});
				}
				if (roof == null) {
					roof = RoofType.HALL;
				}
				use(i, i);
				return true;
			}
			if (BUILDING_WORDS.contains(token)) {
				askBuilding(i);
				if (GRAND_WORDS.contains(token)) {
					setScale(StationScale.GRAND, i);
				}
				if (TERMINUS_WORDS.contains(token) && !terminus) {
					terminus = true;
					terminusAt = i;
				}
				if (HUB_WORDS.contains(token) && !hub) {
					hub = true;
					hubAt = i;
					if (!terminus) {
						terminus = true;
						terminusAt = i;
					}
				}
				if (token.equals("furnished") && furniture == null) {
					furniture = true;
				}
				use(i, i);
				return true;
			}
			if (FOOTBRIDGE_WORDS.contains(token)) {
				if (footbridge == null) {
					footbridge = true;
					note(i, "footbridge");
				}
				use(i, i);
				return true;
			}
			return false;
		}

		/**
		 * City and look words: the first gives the building's look and, unless the text says "no station building", asks for
		 * a building; the first that has a platform style gives the platforms (an explicit style word wins). The train shed
		 * comes from the last other look whose own station has an arched shed (Hamburg, Amsterdam, St Pancras, Antwerp,
		 * Frankfurt), or from the only look when that has one, unless the text chose a roof.
		 */
		private void resolveLooks() {
			if (looks.isEmpty()) {
				return;
			}
			final Look first = looks.get(0);
			if (building == null) {
				askBuilding(first.at());
			}
			Look platforms = null;
			if (style == null) {
				for (final Look look : looks) {
					if (look.style() != null) {
						style = look.style();
						platforms = look;
						note(look.at(), styleName(style) + " platforms");
						break;
					}
				}
			}
			// the shed: from the last other look that has one, else from the first look
			Look shedLook = null;
			for (int k = looks.size() - 1; k >= 0 && shedLook == null; k--) {
				final Look look = looks.get(k);
				if (ShedShape.forLook(look.building(), look.style() == null ? StationStyle.MODERN : look.style()).arched()) {
					shedLook = look;
				}
			}
			if (shedLook != null && !roofNone && (roof == null && !weakRoof || roof == RoofType.HALL)) {
				if (roof == null) {
					roof = RoofType.HALL;
				}
				if (shed == null) {
					shed = ShedShape.forLook(shedLook.building(), shedLook.style() == null ? StationStyle.MODERN : shedLook.style());
					note(shedLook.at(), switch (shed) {
						case POINTED -> "pointed-arch train shed";
						case MULTI_ARCH -> "train shed of several arches";
						default -> "arched glass train shed";
					});
				}
			}
			if (looks.size() > 1 || mixing) {
				final StringBuilder mix = new StringBuilder("mix: building from ").append(title(first.word()));
				if (platforms != null) {
					mix.append(", platforms from ").append(title(platforms.word()));
				}
				if (shedLook != null && roof == RoofType.HALL) {
					mix.append(", train shed from ").append(title(shedLook.word()));
				}
				note(first.at(), mix.toString());
			}
		}

		private static String title(String word) {
			return word.length() <= 3 ? word.toUpperCase(Locale.ROOT) : Character.toUpperCase(word.charAt(0)) + word.substring(1);
		}

		/** The first size word wins; a later one is ignored. */
		/** A terminus not asked to be small: it defaults to a main station's size. */
		private boolean bigTerminus() {
			return terminus && !small && scale != StationScale.COMPACT;
		}

		private void setScale(StationScale value, int i) {
			if (scale == null) {
				scale = value;
				scaleAt = i;
			} else if (scale != value) {
				warn("the building size was given twice; using " + scale.label());
			}
		}

		private void askBuilding(int i) {
			if (building == null) {
				building = true;
				buildingAt = i;
			}
		}

		private void setBuildingStyle(BuildingStyle value, int i) {
			if (buildingStyle == null) {
				buildingStyle = value;
				buildingStyleAt = i;
			}
		}

		private List<String> ignoredWords() {
			final List<String> ignored = new ArrayList<>();
			for (int i = 0; i < tokens.length; i++) {
				final String token = tokens[i];
				if (!used[i] && !STOP_WORDS.contains(token) && !UNITS.contains(token) && value(token) == null && !TRACK_WORDS.containsKey(token)) {
					ignored.add(token);
				}
			}
			return ignored;
		}

		// ---- setters ----------------------------------------------------------------------------------------------------

		private void setTracks(int n, int i) {
			if (tracks != null) {
				if (n != tracks) {
					warn("track count given twice; using " + tracks);
				}
				return;
			}
			tracks = n;
			tracksAt = i;
		}

		private void islandMention(int i) {
			if (islandAt < 0) {
				islandAt = i;
			}
		}

		private void sideMention(int i) {
			sidePlatforms = true;
			if (sideAt < 0) {
				sideAt = i;
			}
		}

		private void setLength(int n, int i) {
			if (length == null) {
				length = n;
				note(i, "length " + n);
			}
		}

		private void setWidth(int n, int i) {
			if (width == null) {
				width = n;
				note(i, "width " + n);
			}
		}

		private void setStyle(StationStyle value, int i) {
			if (style == null) {
				style = value;
				note(i, styleName(value) + " style");
			} else if (style != value) {
				warn("style given twice; using " + styleName(style));
			}
		}

		private void setRoof(RoofType value, int i) {
			if (roof == null) {
				roof = value;
				note(i, value == RoofType.HALL ? "hall roof" : "canopy");
			} else if (roof != value) {
				warn("roof given twice; using " + (roof == RoofType.HALL ? "a hall roof" : "a canopy"));
			}
		}

		private void setRoofMaterial(RoofMaterial value, int i) {
			if (roofMaterial == null) {
				roofMaterial = value;
				note(i, value.name().toLowerCase(Locale.ROOT) + " roof");
			}
		}

		private void setSurface(SurfaceMaterial value, int i) {
			if (surface == null) {
				surface = value;
				note(i, switch (value) {
					case LIGHT_PAVING -> "light paving";
					case DARK_PAVING -> "dark paving";
					default -> value.name().toLowerCase(Locale.ROOT) + " floor";
				});
			}
		}

		private void setStepFree(boolean value, int i) {
			if (stepFree == null) {
				stepFree = value;
				note(i, value ? "step-free access" : "no ramps");
			}
		}

		private static String styleName(StationStyle style) {
			return switch (style) {
				case MODERN -> "Modern";
				case DUTCH -> "Dutch";
				case GERMAN_SBAHN -> "German S-Bahn";
				case METRO -> "Metro";
			};
		}

		// ---- resolution -------------------------------------------------------------------------------------------------

		private int clampTracks(int n) {
			if (n < StationSpec.MIN_TRACKS) {
				warn(n + " tracks is too few; using " + StationSpec.MIN_TRACKS);
				return StationSpec.MIN_TRACKS;
			}
			if (n > StationSpec.MAX_TRACKS) {
				warn(n + " tracks is more than " + StationSpec.MAX_TRACKS + " (the current limit); using " + StationSpec.MAX_TRACKS);
				return StationSpec.MAX_TRACKS;
			}
			return n;
		}

		private int clamp(int n, int min, int max, String what) {
			if (n < min) {
				warn(what + " " + n + " is too small; using " + min);
				return min;
			}
			if (n > max) {
				warn(what + " " + n + " is too large; using " + max);
				return max;
			}
			return n;
		}

		private StationSpec resolve(boolean blank) {
			final StationSpec.Builder builder = StationSpec.builder();
			if (blank) {
				defaulted.add("no description: using defaults");
			}
			final boolean island = islandAt >= 0;

			// tracks
			int trackCount;
			if (tracks != null) {
				trackCount = clampTracks(tracks);
				note(tracksAt, trackCount + (trackCount == 1 ? " track" : " tracks"));
			} else if (islandPlatforms != null) {
				trackCount = clampTracks(islandPlatforms * 2);
				note(islandAt, trackCount + " tracks");
			} else if (large) {
				trackCount = terminus ? LARGE_TERMINUS_TRACKS : LARGE_TRACKS;
			} else if (bigTerminus()) {
				trackCount = scale == StationScale.CITY ? LARGE_TRACKS : TERMINUS_TRACKS;
				defaulted.add(trackCount + " tracks (default for a " + (scale == StationScale.CITY ? "city " : "") + "terminus)");
			} else if (island || sidePlatforms) {
				trackCount = StationSpec.DEFAULT_TRACKS;
				note(Math.max(islandAt, sideAt), trackCount + " tracks");
			} else {
				trackCount = StationSpec.DEFAULT_TRACKS;
				defaulted.add(trackCount + " tracks (default)");
			}
			if (hub && (trackCount < 4 || trackCount % 2 != 0)) {
				// the outer two tracks run through and the inner ones end in pairs at island platforms
				final int even = Math.min(StationSpec.MAX_TRACKS, Math.max(4, trackCount + trackCount % 2));
				warn("a hub needs an even number of tracks, at least 4; using " + even);
				trackCount = even;
			}
			builder.trackCount(trackCount);

			// layout
			final PlatformLayout layout;
			if (hub) {
				layout = PlatformLayout.ISLAND;
				if (sidePlatforms) {
					warn("a hub has island platforms, each shared by a track that ends and one that runs through");
				}
			} else if (island && sidePlatforms) {
				if (trackCount >= 3) {
					layout = PlatformLayout.ISLAND;
					note(Math.min(islandAt, sideAt), "island and side platforms");
				} else if (trackCount == 2) {
					layout = PlatformLayout.ISLAND;
					note(islandAt, "island platforms");
					warn("island and side both given; using island");
				} else {
					layout = PlatformLayout.SIDE;
					note(sideAt, "side platforms");
				}
			} else if (island) {
				if (trackCount < 2) {
					layout = PlatformLayout.SIDE;
					warn("an island platform needs two tracks; using a side platform");
				} else {
					layout = PlatformLayout.ISLAND;
					note(islandAt, "island platforms");
				}
			} else if (sidePlatforms) {
				layout = PlatformLayout.SIDE;
				note(sideAt, "side platforms");
			} else {
				layout = trackCount <= 2 ? PlatformLayout.SIDE : PlatformLayout.ISLAND;
				defaulted.add((layout == PlatformLayout.SIDE ? "side" : "island") + " platforms (default for " + trackCount
						+ (trackCount == 1 ? " track)" : " tracks)"));
			}
			builder.platformLayout(layout);

			// length
			if (length != null) {
				builder.platformLength(clamp(length, StationSpec.MIN_LENGTH, StationSpec.MAX_LENGTH, "length"));
			} else if (lengthAdjective != null) {
				builder.platformLength(lengthAdjective);
			} else if (small) {
				builder.platformLength(32);
			} else if (large) {
				builder.platformLength(terminus ? LARGE_TERMINUS_LENGTH : 96);
			} else if (bigTerminus()) {
				final int terminusLength = scale == StationScale.CITY ? 96 : TERMINUS_LENGTH;
				builder.platformLength(terminusLength);
				defaulted.add("length " + terminusLength + " (default for a " + (scale == StationScale.CITY ? "city " : "") + "terminus)");
			} else {
				defaulted.add("length " + StationSpec.DEFAULT_LENGTH + " (default)");
			}

			// width
			// a footbridge's side platforms need room for a two-column stair, or they get no lift and are not step-free
			final boolean bridgeSide = layout != PlatformLayout.ISLAND && Boolean.TRUE.equals(footbridge) && !terminus;
			final int defaultWidth = layout == PlatformLayout.ISLAND ? StationSpec.DEFAULT_ISLAND_WIDTH
					: bridgeSide ? StationSpec.DEFAULT_FOOTBRIDGE_SIDE_WIDTH : StationSpec.DEFAULT_SIDE_WIDTH;
			if (width != null) {
				builder.platformWidth(clamp(width, StationSpec.MIN_WIDTH, StationSpec.MAX_WIDTH, "width"));
			} else if (narrow) {
				builder.platformWidth(StationSpec.MIN_WIDTH);
			} else if (wide) {
				builder.platformWidth(layout == PlatformLayout.ISLAND ? 8 : 6);
			} else if (small) {
				builder.platformWidth(StationSpec.MIN_WIDTH);
			} else {
				builder.platformWidth(defaultWidth);
				defaulted.add("width " + defaultWidth + (bridgeSide ? " (default with a footbridge, so the lifts fit)" : " (default)"));
			}

			resolveLooks();
			if (style != null) {
				builder.style(style);
			} else {
				defaulted.add("Modern style (default)");
			}

			if (roofNone) {
				builder.roof(RoofType.NONE);
				if (roof != null) {
					warn("roof given twice; using no roof");
				}
			} else if (roof != null) {
				builder.roof(roof);
			} else if (!weakRoof) {
				defaulted.add("canopy (default)");
			}

			if (roofMaterial != null) {
				builder.roofMaterial(roofMaterial);
				if (roofNone) {
					warn("a roof material was given but the station has no roof");
				}
			}
			if (surface != null) {
				builder.surface(surface);
			}
			if (stepFree != null) {
				builder.stepFree(stepFree);
			} else {
				defaulted.add("step-free access (default)");
			}
			if (tactile != null) {
				builder.tactile(tactile);
			} else {
				defaulted.add("tactile paving (default)");
			}
			if (furniture != null) {
				builder.furniture(furniture);
			} else {
				defaulted.add("furniture (default)");
			}
			if (railNodes != null) {
				builder.railNodes(railNodes);
			} else {
				defaulted.add("rail nodes at platform ends (default)");
			}
			final boolean wantsBuilding = Boolean.TRUE.equals(building);
			if (wantsBuilding) {
				final BuildingStyle look = buildingStyle != null ? buildingStyle : BuildingStyle.forStyle(style != null ? style : StationStyle.MODERN);
				builder.building(look);
				note(buildingAt, "station building: " + look.label() + ", furnished");
			} else if (lookOnlyWord != null) {
				warn("\"" + lookOnlyWord + "\" only sets a station building's look; add \"station building\" to get one");
			}
			if (wantsBuilding) {
				final StationSpec sized = builder.build();
				if (scale != null) {
					builder.scale(scale);
					note(scaleAt, scale.label() + " station building");
				} else {
					final StationScale auto = StationScale.forSize(true, sized.trackCount(), sized.platformLength());
					builder.scale(auto);
					defaulted.add(auto.label() + " station building (default for " + sized.trackCount() + (sized.trackCount() == 1 ? " track, " : " tracks, ")
							+ sized.platformLength() + " long)");
				}
			}
			if (terminus && hub) {
				builder.terminus(true).hub(true);
				note(hubAt, "hub: the outer tracks run through past the head building, the inner ones end at its buffer stops");
			} else if (terminus) {
				builder.terminus(true);
				note(terminusAt, "terminus: the tracks end at buffer stops in front of the head building");
			}
			final boolean bridge = Boolean.TRUE.equals(footbridge);
			if (bridge && terminus) {
				warn("a terminus is reached from its concourse, so it gets no footbridge");
			} else if (bridge) {
				builder.footbridge(true);
			}
			if (roof == RoofType.HALL && !roofNone) {
				builder.shed(shed != null ? shed : ShedShape.forLook(wantsBuilding ? builder.build().building() : BuildingStyle.NONE,
						style != null ? style : StationStyle.MODERN));
			}
			builder.name(name);
			return builder.build();
		}
	}
}
