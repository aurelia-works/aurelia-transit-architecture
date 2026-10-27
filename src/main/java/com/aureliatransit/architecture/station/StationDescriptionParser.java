package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
	/**
	 * Shortest platforms a station building stands beside: four bays, with room for its stair up a full storey on one side of
	 * the entrance and its lift on the other.
	 */
	static final int BUILDING_LIFT_LENGTH = 24;
	/** Shortest platforms of a hub: its footbridge's stairs and lifts down to the outer islands need this much room. */
	static final int HUB_LENGTH = 20;
	static final int LARGE_TERMINUS_LENGTH = 200;
	/** What the station classes default to when nothing says otherwise (blocks, 1.5 to the metre): see docs/STATION_GENERATOR.md. */
	static final int HALT_LENGTH = 75;
	static final int HALT_WIDTH = 5;
	/** A tram stop's platforms, unless the words say: long enough for a three-car tram and its stop signs. */
	static final int TRAM_LENGTH = 40;
	static final int TRAM_WIDTH = 4;
	static final int REGIONAL_LENGTH = 180;
	static final int CITY_LENGTH = 200;
	static final int GRAND_TRACKS = 6;
	static final int GRAND_LENGTH = 210;

	private static final Pattern QUOTED_NAME = Pattern.compile("(?i)\\b(?:named|called|name)\\s*\"([^\"]*)\"");
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
	/** Platform length a real station is built at when the words give none: its hall and canopies past both ends. */
	static final int REAL_LENGTH = 240;
	private static final Pattern BARE_NAME = Pattern.compile("(?i)\\b(?:named|called)\\s+([^,.;\"]+)");
	private static final Pattern NAME_STOP = Pattern.compile("(?i)\\s+\\b(?:with|and|having|style|styled|in|using)\\b.*$");
	/** One platform of a platform list: "platform 2: island 12 wide 120 long open;" (Station Studio writes these). */
	private static final Pattern PLATFORM_CLAUSE = Pattern.compile("(?i)\\bplatform\\s+(\\d{1,2})\\s*:\\s*([^;]*);?");
	/**
	 * "seed k7f2ra": the generator seed ({@link StationSeed}: base 36, Station Studio's dice, one to twelve letters and digits).
	 * Whatever stands after the word is taken and checked, so a seed that is too long or has other characters in it is reported
	 * and left out instead of being half read.
	 */
	private static final String SWAP_END = "(?=\\s*(?:[,.;!]|\\band\\b|$))";
	/** "use stone bricks instead of sandstone", "replace sandstone with stone bricks", "swap sandstone for stone bricks", "sandstone -> stone bricks". */
	private static final List<Pattern> SWAPS = List.of(
			swap("\\b(?:use|using|with|build with)\\s+" + swapBlock("to") + "\\s+instead\\s+of\\s+" + swapBlock("from")),
			swap("\\breplace\\s+" + swapBlock("from") + "\\s+(?:with|by)\\s+" + swapBlock("to")),
			swap("\\bswap\\s+" + swapBlock("from") + "\\s+(?:for|with|to)\\s+" + swapBlock("to")),
			swap("(?<![a-z0-9_:])" + swapBlock("from") + "\\s*(?:->|\u2192)\\s*" + swapBlock("to")));

	/** A block named in a swap: words or an id, as few as reach the next part of the pattern. */
	private static String swapBlock(String group) {
		return "(?<" + group + ">[a-z0-9_:][a-z0-9_: -]*?)";
	}

	private static Pattern swap(String pattern) {
		return Pattern.compile(pattern + SWAP_END, Pattern.CASE_INSENSITIVE);
	}
	private static final Pattern SEED = Pattern.compile("(?i)\\bseed\\b(?:\\s*[:=]?\\s*([^\\s,;!?:()\"]+))?");
	/** "city class": the station's class (see {@link StationClass}); the bare size words "city", "halt" ... keep their older meaning. */
	private static final Pattern CLASS = Pattern.compile("(?i)\\b(halt|regional|town|city|grand)\\s+class\\b");
	/** "postwar era": the station's era (see {@link StationEra}); "modern" on its own stays the modern platform style. */
	private static final Pattern ERA = Pattern.compile("(?i)\\b(historic|interwar|postwar|modern)\\s+era\\b");
	/** "facing north": the way the entrance faces. */
	private static final Pattern FACING = Pattern.compile("(?i)\\b(?:facing|faces)\\s+(north|east|south|west)\\b");
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

	private static final String ATA = "aurelia_transit_architecture:";
	private static final Map<String, StationStyle> STYLE_WORDS = Map.ofEntries(Map.entry("modern", StationStyle.MODERN),
			Map.entry("contemporary", StationStyle.MODERN), Map.entry("generic", StationStyle.MODERN), Map.entry("default", StationStyle.MODERN),
			Map.entry("simple", StationStyle.MODERN), Map.entry("standard", StationStyle.MODERN), Map.entry("dutch", StationStyle.DUTCH),
			Map.entry("netherlands", StationStyle.DUTCH), Map.entry("holland", StationStyle.DUTCH), Map.entry("ns", StationStyle.DUTCH),
			Map.entry("utrecht", StationStyle.DUTCH), Map.entry("leidsche", StationStyle.DUTCH), Map.entry("amsterdam", StationStyle.DUTCH),
			Map.entry("rotterdam", StationStyle.DUTCH), Map.entry("german", StationStyle.GERMAN_SBAHN), Map.entry("germany", StationStyle.GERMAN_SBAHN),
			Map.entry("db", StationStyle.GERMAN_SBAHN), Map.entry("sbahn", StationStyle.GERMAN_SBAHN), Map.entry("hamburg", StationStyle.GERMAN_SBAHN),
			Map.entry("munich", StationStyle.GERMAN_SBAHN), Map.entry("munchen", StationStyle.GERMAN_SBAHN), Map.entry("berlin", StationStyle.GERMAN_SBAHN),
			Map.entry("frankfurt", StationStyle.GERMAN_SBAHN), Map.entry("metro", StationStyle.METRO), Map.entry("subway", StationStyle.METRO),
			Map.entry("underground", StationStyle.METRO), Map.entry("ubahn", StationStyle.METRO), Map.entry("tube", StationStyle.METRO),
			Map.entry("tokyo", StationStyle.MODERN), Map.entry("marunouchi", StationStyle.MODERN), Map.entry("japan", StationStyle.MODERN),
			Map.entry("japanese", StationStyle.MODERN), Map.entry("zurich", StationStyle.GERMAN_SBAHN), Map.entry("zürich", StationStyle.GERMAN_SBAHN),
			Map.entry("zuerich", StationStyle.GERMAN_SBAHN), Map.entry("swiss", StationStyle.GERMAN_SBAHN), Map.entry("switzerland", StationStyle.GERMAN_SBAHN),
			Map.entry("paris", StationStyle.MODERN), Map.entry("french", StationStyle.MODERN), Map.entry("france", StationStyle.MODERN),
			Map.entry("milan", StationStyle.MODERN), Map.entry("milano", StationStyle.MODERN), Map.entry("italian", StationStyle.MODERN),
			Map.entry("italy", StationStyle.MODERN), Map.entry("hong kong", StationStyle.MODERN), Map.entry("kowloon", StationStyle.MODERN),
			Map.entry("hongkong", StationStyle.MODERN), Map.entry("hk", StationStyle.MODERN), Map.entry("beijing", StationStyle.MODERN),
			Map.entry("peking", StationStyle.MODERN), Map.entry("china", StationStyle.MODERN), Map.entry("chinese", StationStyle.MODERN),
			Map.entry("shanghai", StationStyle.MODERN), Map.entry("bangkok", StationStyle.MODERN), Map.entry("thailand", StationStyle.MODERN),
			Map.entry("thai", StationStyle.MODERN), Map.entry("krungthep", StationStyle.MODERN), Map.entry("kuala lumpur", StationStyle.MODERN),
			Map.entry("malaysia", StationStyle.MODERN), Map.entry("malaysian", StationStyle.MODERN), Map.entry("kl", StationStyle.MODERN),
			Map.entry("moorish", StationStyle.MODERN), Map.entry("mumbai", StationStyle.MODERN), Map.entry("bombay", StationStyle.MODERN),
			Map.entry("india", StationStyle.MODERN), Map.entry("indian", StationStyle.MODERN), Map.entry("teddington", StationStyle.MODERN),
			Map.entry("surbiton", StationStyle.MODERN), Map.entry("coventry", StationStyle.MODERN));
	private static final Set<String> UNDERGROUND_WORDS = Set.of("underground", "subway", "tunnel", "tube");
	private static final Set<String> ELEVATED_WORDS = Set.of("elevated", "viaduct");
	/** Colours an underground station's walls are tiled in ("orange tiles": Berlin's U8, Rosenthaler Platz). */
	private static final Map<String, String> TILES = Map.of("orange", ATA + "metro_tile_orange", "green", ATA + "metro_tile_green",
			"yellow", ATA + "metro_tile_yellow", "blue", ATA + "metro_tile_blue", "grey", ATA + "metro_blue_grey_tile", "gray",
			ATA + "metro_blue_grey_tile", "ochre", ATA + "metro_ochre_tile");
	private static final Set<String> TILE_NOUNS = Set.of("tile", "tiles", "tiled", "tiling");
	/** City words that pick a station building's look when a building is asked for (some also pick the platform style). */
	private static final Map<String, BuildingStyle> CITY_BUILDINGS = Map.ofEntries(Map.entry("utrecht", BuildingStyle.MODERN),
			Map.entry("leidsche", BuildingStyle.MODERN), Map.entry("berlin", BuildingStyle.BERLIN),
			Map.entry("amsterdam", BuildingStyle.AMSTERDAM), Map.entry("hamburg", BuildingStyle.CLINKER), Map.entry("munich", BuildingStyle.CONCRETE),
			Map.entry("munchen", BuildingStyle.CONCRETE), Map.entry("frankfurt", BuildingStyle.SANDSTONE), Map.entry("london", BuildingStyle.GOTHIC_BRICK),
			Map.entry("pancras", BuildingStyle.GOTHIC_BRICK), Map.entry("gothic", BuildingStyle.GOTHIC_BRICK), Map.entry("victorian", BuildingStyle.VICTORIAN),
			Map.entry("antwerp", BuildingStyle.ANTWERP), Map.entry("antwerpen", BuildingStyle.ANTWERP), Map.entry("belgian", BuildingStyle.ANTWERP),
			Map.entry("philadelphia", BuildingStyle.BEAUX_ARTS), Map.entry("washington", BuildingStyle.BEAUX_ARTS), Map.entry("chicago", BuildingStyle.BEAUX_ARTS),
			Map.entry("american", BuildingStyle.BEAUX_ARTS), Map.entry("beaux", BuildingStyle.BEAUX_ARTS), Map.entry("classical", BuildingStyle.BEAUX_ARTS),
			Map.entry("brick", BuildingStyle.AMSTERDAM), Map.entry("sandstone", BuildingStyle.SANDSTONE), Map.entry("limestone", BuildingStyle.ANTWERP),
			Map.entry("marble", BuildingStyle.ANTWERP), Map.entry("clinker", BuildingStyle.CLINKER), Map.entry("brutalist", BuildingStyle.CONCRETE),
			Map.entry("america", BuildingStyle.BEAUX_ARTS), Map.entry("usa", BuildingStyle.BEAUX_ARTS), Map.entry("us", BuildingStyle.BEAUX_ARTS),
			Map.entry("amtrak", BuildingStyle.BEAUX_ARTS), Map.entry("nyc", BuildingStyle.BEAUX_ARTS), Map.entry("york", BuildingStyle.BEAUX_ARTS),
			Map.entry("boston", BuildingStyle.BEAUX_ARTS), Map.entry("english", BuildingStyle.VICTORIAN), Map.entry("british", BuildingStyle.VICTORIAN),
			Map.entry("uk", BuildingStyle.VICTORIAN), Map.entry("britain", BuildingStyle.VICTORIAN), Map.entry("england", BuildingStyle.VICTORIAN),
			Map.entry("teddington", BuildingStyle.VICTORIAN), Map.entry("suburban", BuildingStyle.VICTORIAN), Map.entry("lswr", BuildingStyle.VICTORIAN), Map.entry("surbiton", BuildingStyle.ART_DECO),
			Map.entry("deco", BuildingStyle.ART_DECO), Map.entry("artdeco", BuildingStyle.ART_DECO), Map.entry("coventry", BuildingStyle.MODERNIST),
			Map.entry("modernist", BuildingStyle.MODERNIST), Map.entry("leipzig", BuildingStyle.SANDSTONE), Map.entry("cologne", BuildingStyle.CLINKER),
			Map.entry("koln", BuildingStyle.CLINKER), Map.entry("brussels", BuildingStyle.ANTWERP), Map.entry("rotterdam", BuildingStyle.MODERN),
			Map.entry("tokyo", BuildingStyle.TOKYO), Map.entry("marunouchi", BuildingStyle.TOKYO), Map.entry("japan", BuildingStyle.TOKYO),
			Map.entry("japanese", BuildingStyle.TOKYO), Map.entry("zurich", BuildingStyle.ZURICH), Map.entry("zürich", BuildingStyle.ZURICH),
			Map.entry("zuerich", BuildingStyle.ZURICH), Map.entry("swiss", BuildingStyle.ZURICH), Map.entry("switzerland", BuildingStyle.ZURICH),
			Map.entry("paris", BuildingStyle.PARIS), Map.entry("french", BuildingStyle.PARIS), Map.entry("france", BuildingStyle.PARIS),
			Map.entry("milan", BuildingStyle.MILAN), Map.entry("milano", BuildingStyle.MILAN), Map.entry("italian", BuildingStyle.MILAN),
			Map.entry("italy", BuildingStyle.MILAN), Map.entry("kowloon", BuildingStyle.HONG_KONG), Map.entry("hongkong", BuildingStyle.HONG_KONG),
			Map.entry("hk", BuildingStyle.HONG_KONG), Map.entry("beijing", BuildingStyle.BEIJING), Map.entry("peking", BuildingStyle.BEIJING),
			Map.entry("china", BuildingStyle.BEIJING), Map.entry("chinese", BuildingStyle.BEIJING), Map.entry("shanghai", BuildingStyle.BEIJING),
			Map.entry("bangkok", BuildingStyle.BANGKOK), Map.entry("thailand", BuildingStyle.BANGKOK), Map.entry("thai", BuildingStyle.BANGKOK),
			Map.entry("lamphong", BuildingStyle.BANGKOK), Map.entry("krungthep", BuildingStyle.BANGKOK), Map.entry("malaysia", BuildingStyle.KUALA_LUMPUR),
			Map.entry("malaysian", BuildingStyle.KUALA_LUMPUR), Map.entry("kl", BuildingStyle.KUALA_LUMPUR), Map.entry("moorish", BuildingStyle.KUALA_LUMPUR),
			Map.entry("mumbai", BuildingStyle.MUMBAI), Map.entry("bombay", BuildingStyle.MUMBAI), Map.entry("india", BuildingStyle.MUMBAI),
			Map.entry("indian", BuildingStyle.MUMBAI), Map.entry("csmt", BuildingStyle.MUMBAI), Map.entry("shivaji", BuildingStyle.MUMBAI));
	/** Famous stations: naming one asks for its building. */
	private static final Set<String> LANDMARKS = Set.of("pancras", "antwerp", "antwerpen", "philadelphia", "washington", "chicago", "marunouchi",
			"kowloon", "lamphong", "csmt", "shivaji");
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
			Map.entry("multispan", ShedShape.MULTI_ARCH), Map.entry("ridge", ShedShape.GABLED),
			Map.entry("furrow", ShedShape.GABLED));
	/** Canopy shapes; each also asks for platform canopies (unless a roof was given). */
	private static final Map<String, CanopyShape> CANOPY_SHAPE_WORDS = Map.ofEntries(Map.entry("butterfly", CanopyShape.BUTTERFLY), Map.entry("gable", CanopyShape.GABLE),
			Map.entry("gabled", CanopyShape.GABLE), Map.entry("pitched", CanopyShape.GABLE), Map.entry("heritage", CanopyShape.GABLE));
	/** Words that say several looks are mixed ("Utrecht and Hamburg combined"); read so they are not reported as ignored. */
	private static final Set<String> MIX_WORDS = Set.of("combined", "combine", "combo", "mix", "mixed", "mixture", "hybrid", "fusion", "meets",
			"cross", "crossed", "x", "blend", "blended", "inspired", "inspiration", "both", "between", "together", "merged", "merge");
	private static final Set<String> CANOPY_WORDS = Set.of("canopy", "canopies", "covered", "roofed", "shelter", "shelters");
	private static final Set<String> OPEN_WORDS = Set.of("open", "uncovered", "roofless");
	private static final Set<String> STOP_WORDS = Set.of("a", "an", "the", "with", "and", "or", "of", "for", "to", "in", "on", "at", "by", "as",
			"from", "into", "please", "build", "make", "create", "generate", "give", "me", "i", "we", "want", "need", "would", "like", "some",
			"any", "very", "really", "also", "plus", "station", "stations", "railway", "rail", "train", "trains", "platform", "platforms",
			"track", "tracks", "style", "styled", "type", "kind", "it", "its", "that", "this", "is", "are", "be", "have", "has", "new", "nice",
			"little", "no", "not", "without", "only", "s", "u", "operator", "logo", "logos", "art");

	private StationDescriptionParser() {
	}

	public static ParseResult parse(String description) {
		return new Parse().run(description == null ? "" : description);
	}

	/**
	 * The Studio controls a description sets itself, by the Studio's control keys (tracks, look, mode, building, layout, length,
	 * width, roof, size, name). Typed words beat a pinned control, so the Studio leaves out each pin the words already set.
	 */
	public static Set<String> controlsSaid(String description) {
		final Parse parse = new Parse();
		parse.run(description == null ? "" : description);
		return parse.said;
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
		private TrackBed trackBed;
		private Operator operator;
		/** The real station named ("utrecht centraal"), or null. */
		private RealStation real;
		private int realAt = -1;
		/** A city said with "station", "central" or "cs" ("utrecht station"): the real station if nothing else in the words is against it. */
		private RealStation looseReal;
		private int looseAt = -1;
		private boolean escalators;
		private boolean bothWays;
		private boolean enclosedStairs;
		/** The gates, entrance width and block swaps the words asked for. */
		private StationOptions options = StationOptions.DEFAULTS;
		private boolean travelators;
		private boolean parking;
		private boolean underpass;
		private boolean tram;
		/** "underground", "subway", "tube", "tunnel": a box under the street (Enviro, 10-20); the word's index, or -1. */
		private int undergroundAt = -1;
		/** "orange tiles" and the like: an underground station's wall tiles, or null. */
		private String tile;
		private boolean mezzanine;
		private Boolean furniture;
		private Boolean railNodes;
		private String name = "";
		private Boolean building;
		private int buildingAt = -1;
		private BuildingStyle buildingStyle;
		private Boolean footbridge;
		private boolean terminus;
		private int terminusAt = -1;
		private boolean hub;
		/** The platform list, by platform number; empty when the platforms are not listed one by one. */
		private final TreeMap<Integer, PlatformSpec> listed = new TreeMap<>();
		private int hubAt = -1;
		/** The first word that only sets a building's look (e.g. "brick"), to warn about when no building was asked for. */
		private String lookOnlyWord;
		/** City and look words in text order; mixed together when there are several. */
		private final List<Look> looks = new ArrayList<>();
		private boolean mixing;
		private ShedShape shed;
		private CanopyShape canopy;
		private StationScale scale;
		/** The size came from a landmark (Grand Central is grand), so a size word said after it still wins. */
		private boolean scaleFromLandmark;
		private int scaleAt = -1;
		/**
		 * The real station the first city word names, said "real frankfurt" (St Pancras, Frankfurt Hbf, Paris Nord, Milano Centrale, Grand Central), or null: with nothing else in the words naming
		 * the station, its sheet and shed in {@link Landmarks} are built.
		 */
		private String cityStationName;
		private long seed;
		private String seedText = "";
		/** "single stairs": one way down to each platform even at a station that would get two. */
		private boolean singleStairs;
		private StationClass stationClass = StationClass.AUTO;
		private StationEra era = StationEra.AUTO;
		private Facing facing = Facing.AUTO;
		/** What the seed chose, for the understood list. */
		private final List<String> seeded = new ArrayList<>();

		/** One stream of the seed's dice per choice, so changing one choice's odds never moves the others. */
		private java.util.SplittableRandom dice(int choice) {
			return new java.util.SplittableRandom(seed * 0x9E3779B97F4A7C15L + choice);
		}

		@SafeVarargs
		private static <T> T pick(java.util.SplittableRandom dice, T... choices) {
			return choices[dice.nextInt(choices.length)];
		}

		ParseResult run(String description) {
			String text = description;
			if (text.length() > MAX_INPUT) {
				text = text.substring(0, MAX_INPUT);
				warn("the description is longer than " + MAX_INPUT + " characters; the rest was ignored");
			}
			text = extractSwaps(text);
			text = extractPlatforms(text);
			text = extractName(text);
			text = extractSeed(text);
			text = extractClassEraFacing(text);
			tokens = tokenize(text);
			used = new boolean[tokens.length];
			for (int i = 0; i < tokens.length; i++) {
				if (!used[i]) {
					scan(i);
				}
			}
			said = said();
			applyClassAndEra();
			final StationSpec spec = resolve(description.isBlank());
			final List<String> ignored = new ArrayList<>(new LinkedHashSet<>(ignoredWords()));
			understood.sort(Comparator.comparingInt(Note::at));
			final List<String> understoodLines = new ArrayList<>();
			for (final Note note : understood) {
				understoodLines.add(note.text());
			}
			return new ParseResult(spec, understoodLines, defaulted, warnings, ignored);
		}

		/** What the words themselves set, before a class, an era or the resolve step fills anything in. */
		private Set<String> said = Set.of();

		/**
		 * A station with no class named that is what the halt class builds: open platforms ("no roof") with no building on one or
		 * two tracks, no footbridge, escalators or platform list, and not a terminus. Its platforms get the halt class's width. A
		 * roofed station keeps the default width, so an empty description plans as before.
		 */
		private boolean impliedHalt(boolean buildingComing, int trackCount, boolean stairGroup) {
			return stationClass == StationClass.AUTO && roofNone && !buildingComing && !terminus && !hub && trackCount <= 2 && listed.isEmpty()
					&& !Boolean.TRUE.equals(footbridge) && !stairGroup && !escalators && !travelators;
		}

		/** See {@link StationDescriptionParser#controlsSaid}; taken right after the scan. */
		private Set<String> said() {
			final Set<String> said = new LinkedHashSet<>();
			if (tracks != null || !listed.isEmpty()) {
				said.add("tracks");
			}
			if (!looks.isEmpty() || buildingStyle != null || style != null) {
				said.add("look");
			}
			if (terminus || hub) {
				said.add("mode");
			}
			if (building != null) {
				said.add("building");
			}
			if (islandAt >= 0 || islandPlatforms != null || sidePlatforms || !listed.isEmpty()) {
				said.add("layout");
			}
			if (length != null || lengthAdjective != null) {
				said.add("length");
			}
			if (width != null || wide || narrow) {
				said.add("width");
			}
			if (roofNone || roof != null || shed != null || canopy != null) {
				said.add("roof");
			}
			if (small || large || scale != null) {
				said.add("size");
			}
			if (!name.isEmpty()) {
				said.add("name");
			}
			return said;
		}

		// ---- platform list ----------------------------------------------------------------------------------------------

		/** Takes "platform N: ..." clauses out of the text and reads each into the platform list. */
		private String extractPlatforms(String text) {
			final Matcher matcher = PLATFORM_CLAUSE.matcher(text);
			final StringBuilder rest = new StringBuilder();
			int last = 0;
			while (matcher.find()) {
				rest.append(text, last, matcher.start()).append(' ');
				last = matcher.end();
				final int number = Integer.parseInt(matcher.group(1));
				if (number < 1 || number > PlatformSpec.MAX_PLATFORMS) {
					warn("platform " + number + " is outside 1.." + PlatformSpec.MAX_PLATFORMS + "; ignored");
					continue;
				}
				if (listed.containsKey(number)) {
					warn("platform " + number + " given twice; using the first");
					continue;
				}
				listed.put(number, platformClause(number, matcher.group(2)));
			}
			rest.append(text.substring(last));
			if (!listed.isEmpty() && listed.lastKey() != listed.size()) {
				warn("the platform list skips a number; the platforms are taken in order");
			}
			return rest.toString();
		}

		/** One platform's words: side or island, "N wide", "N long", canopy or open. */
		private PlatformSpec platformClause(int number, String words) {
			final String[] t = tokenize(words);
			PlatformSpec.Kind kind = PlatformSpec.Kind.SIDE;
			int platformWidth = 0;
			int platformLength = 0;
			PlatformSpec.Roof platformRoof = PlatformSpec.Roof.STATION;
			boolean bay = false;
			for (int i = 0; i < t.length; i++) {
				final String w = t[i];
				final String next = i + 1 < t.length ? t[i + 1] : "";
				final Integer n = w.matches("\\d{1,4}") ? Integer.valueOf(w) : null;
				if (w.equals("island")) {
					kind = PlatformSpec.Kind.ISLAND;
				} else if (w.equals("side")) {
					kind = PlatformSpec.Kind.SIDE;
				} else if (w.equals("bay") || w.equals("dead") || w.equals("end") || w.equals("terminating")) {
					bay = true;
				} else if (n != null && (next.equals("wide") || next.equals("width"))) {
					platformWidth = clamp(n, StationSpec.MIN_WIDTH, StationSpec.MAX_WIDTH, "platform " + number + " width");
					i++;
				} else if (n != null && (next.equals("long") || next.equals("length"))) {
					platformLength = clamp(n, StationSpec.MIN_LENGTH, StationSpec.MAX_LENGTH, "platform " + number + " length");
					i++;
				} else if (w.equals("canopy") || w.equals("canopies") || w.equals("covered")) {
					platformRoof = PlatformSpec.Roof.CANOPY;
				} else if (w.equals("open") || w.equals("uncovered")) {
					platformRoof = PlatformSpec.Roof.OPEN;
				} else if (!w.equals("platform") && !w.equals("wide") && !w.equals("long") && !w.equals("roof") && !w.equals("blocks")) {
					warn("platform " + number + ": \"" + w + "\" not understood");
				}
			}
			return new PlatformSpec(kind, platformWidth, platformLength, platformRoof, bay);
		}

		// ---- name -------------------------------------------------------------------------------------------------------

		/** Takes the station name out of the text so words inside it are never read as keywords. */
		/**
		 * Takes "seed k7f2ra" out of the text (after the name, so a station named Seed Lane keeps its name). A seed that is not one
		 * to twelve letters or digits is ignored with a warning, never an error; the first valid one wins.
		 */
		/** Takes out the block swaps ({@link #SWAPS}), so their block words do not also pick a look or a material. */
		private String extractSwaps(String text) {
			String rest = text;
			for (final Pattern pattern : SWAPS) {
				final Matcher matcher = pattern.matcher(rest);
				final StringBuilder kept = new StringBuilder();
				int last = 0;
				while (matcher.find()) {
					final String from = BlockFamilies.resolve(matcher.group("from"));
					final String to = BlockFamilies.resolve(matcher.group("to"));
					if (from == null || to == null || from.equals(to)) {
						continue;
					}
					kept.append(rest, last, matcher.start()).append(' ');
					last = matcher.end();
					final java.util.Map<String, String> pairs = BlockFamilies.expand(from, to);
					if (options.swaps().size() + pairs.size() > StationOptions.MAX_SWAPS) {
						warn("too many block swaps; " + from + " is not swapped (at most " + StationOptions.MAX_SWAPS + " blocks)");
						continue;
					}
					for (final java.util.Map.Entry<String, String> pair : pairs.entrySet()) {
						options = options.withSwap(pair.getKey(), pair.getValue());
					}
					understood.add(new Note(-1, "build " + to + " instead of " + from + (pairs.size() > 1 ? " (with its slabs, stairs and walls)" : "")));
				}
				kept.append(rest.substring(last));
				rest = kept.toString();
			}
			return rest;
		}

		private String extractSeed(String text) {
			final Matcher matcher = SEED.matcher(text);
			final StringBuilder rest = new StringBuilder();
			int last = 0;
			boolean taken = false;
			while (matcher.find()) {
				rest.append(text, last, matcher.start()).append(' ');
				last = matcher.end();
				// a full stop ends the sentence, not the seed; one inside ("3.5") makes it invalid
				final String given = matcher.group(1) == null ? null : matcher.group(1).replaceAll("\\.+$", "");
				if (given == null || given.isEmpty()) {
					warn("\"seed\" needs 1 to " + StationSeed.MAX_CHARS + " letters or digits after it, like \"seed k7f2ra\"; no seed was used");
				} else if (!StationSeed.valid(given)) {
					final String shown = given.length() > 20 ? given.substring(0, 20) + "..." : given;
					warn("seed \"" + shown + "\" ignored: a seed is 1 to " + StationSeed.MAX_CHARS + " letters or digits (a-z, 0-9)");
				} else if (taken) {
					warn("seed given twice; using the first (" + seedText + ")");
				} else {
					seedText = given.toLowerCase(Locale.ROOT);
					seed = StationSeed.parse(seedText);
					taken = true;
				}
			}
			rest.append(text.substring(last));
			return rest.toString();
		}

		/**
		 * Takes "city class", "postwar era" and "facing north" out of the text before anything else reads it, so the older meaning of
		 * the bare words ("city" a building size, "modern" a platform style) is not triggered by them. The first of each wins.
		 */
		private String extractClassEraFacing(String text) {
			String rest = text;
			Matcher matcher = CLASS.matcher(rest);
			final StringBuilder cleaned = new StringBuilder();
			int last = 0;
			while (matcher.find()) {
				cleaned.append(rest, last, matcher.start()).append(' ');
				last = matcher.end();
				final StationClass given = StationClass.of(matcher.group(1));
				if (stationClass == StationClass.AUTO) {
					stationClass = given;
				} else if (stationClass != given) {
					warn("class given twice; using " + stationClass.label().toLowerCase(Locale.ROOT));
				}
			}
			rest = cleaned.append(rest.substring(last)).toString();
			matcher = ERA.matcher(rest);
			final StringBuilder eras = new StringBuilder();
			last = 0;
			while (matcher.find()) {
				eras.append(rest, last, matcher.start()).append(' ');
				last = matcher.end();
				final StationEra given = StationEra.of(matcher.group(1));
				if (era == StationEra.AUTO) {
					era = given;
				} else if (era != given) {
					warn("era given twice; using " + era.label().toLowerCase(Locale.ROOT));
				}
			}
			rest = eras.append(rest.substring(last)).toString();
			matcher = FACING.matcher(rest);
			final StringBuilder facings = new StringBuilder();
			last = 0;
			while (matcher.find()) {
				facings.append(rest, last, matcher.start()).append(' ');
				last = matcher.end();
				final Facing given = Facing.of(matcher.group(1));
				if (facing == Facing.AUTO) {
					facing = given;
				} else if (facing != given) {
					warn("facing given twice; using " + facing.key());
				}
			}
			rest = facings.append(rest.substring(last)).toString();
			if (facing != Facing.AUTO) {
				note(-1, "the entrance faces " + facing.key());
			}
			return rest;
		}

		/** The default era of each class, when the words and the era control said none. */
		private static StationEra defaultEra(StationClass value) {
			return switch (value) {
				case HALT -> StationEra.POSTWAR;
				case REGIONAL, TOWN, GRAND, AUTO -> StationEra.HISTORIC;
				case CITY -> StationEra.MODERN;
			};
		}

		/**
		 * Fills in what the words left open from the class and the era. A class sets the tracks, length, layout, building and its
		 * size and roof (a halt: one track, 75 long, 5 wide, open, no building; regional: a compact building; town and city: a
		 * city-sized one; grand: a grand building under a train shed); an era chooses the look when none was named. Anything the
		 * text said stays as it was said.
		 */
		private void applyClassAndEra() {
			if (stationClass == StationClass.AUTO && era == StationEra.AUTO) {
				return;
			}
			// a real station is built as it is: Studio rolls a class and an era with every seed, so leaving them out is no warning.
			// So is the one "utrecht station" means when nothing else in the words is against it: asked before the class fills in
			// a building, or a rolled "town class" turned Jah's 10-22 Utrecht into a station of its own with the city's look
			final RealStation asIs = real != null && real != RealStation.NONE ? real : real == null && looseReal != null && looseFits() ? looseReal : null;
			if (asIs != null) {
				note(-1, asIs.label() + " is built as it is; the class and era are left out");
				stationClass = StationClass.AUTO;
				era = StationEra.AUTO;
				return;
			}
			final StationClass c = stationClass;
			final StationEra e = era != StationEra.AUTO ? era : defaultEra(c);
			final List<String> set = new ArrayList<>();
			final boolean tracksSaid = tracks != null || islandPlatforms != null || !listed.isEmpty() || large;
			final boolean layoutSaid = islandAt >= 0 || sidePlatforms || !listed.isEmpty() || hub;
			final boolean lengthSaid = length != null || lengthAdjective != null || small || large;
			final boolean widthSaid = width != null || narrow || wide || small;
			final boolean buildingSaid = building != null || !looks.isEmpty() || buildingStyle != null || scale != null || terminus;
			final boolean roofSaid = roof != null || roofNone || weakRoof || canopy != null || shed != null;
			if (c != StationClass.AUTO) {
				final boolean modern = e == StationEra.MODERN;
				switch (c) {
					case HALT -> {
						if (!tracksSaid && !terminus) {
							tracks = 1;
							set.add("1 track");
						}
						if (!lengthSaid) {
							length = HALT_LENGTH;
							set.add(HALT_LENGTH + " long");
						}
						if (!widthSaid) {
							width = HALT_WIDTH;
							set.add(HALT_WIDTH + " wide");
						}
						if (!buildingSaid) {
							building = false;
							set.add("no building");
						}
						if (!roofSaid) {
							roofNone = true;
							set.add("open platform");
						}
					}
					case REGIONAL, TOWN, CITY -> {
						final int classTracks = c == StationClass.REGIONAL ? 2 : c == StationClass.TOWN ? 3 : 4;
						if (!tracksSaid && !terminus) {
							tracks = classTracks;
							set.add(classTracks + " tracks");
						}
						if (!lengthSaid && !terminus) {
							length = c == StationClass.CITY ? CITY_LENGTH : REGIONAL_LENGTH;
							set.add(length + " long");
						}
						if (!layoutSaid && !terminus && c == StationClass.REGIONAL) {
							if (modern) {
								islandAt = Integer.MAX_VALUE - 1;
								set.add("an island platform");
							} else {
								sidePlatforms = true;
								sideAt = Integer.MAX_VALUE - 1;
								if (footbridge == null) {
									footbridge = true;
								}
								set.add("side platforms and a footbridge");
							}
						}
						if (!buildingSaid) {
							building = true;
							buildingAt = -1;
						}
						if (scale == null) {
							scale = c == StationClass.REGIONAL ? StationScale.COMPACT : StationScale.CITY;
							set.add(scale.label() + " station building");
						}
						if (!roofSaid && e == StationEra.HISTORIC) {
							// a city the words name keeps its own canopies, as it keeps its own shed (Studio rolls an era with
							// every seed; Teddington's are the look's, not a rolled gable)
							roof = RoofType.CANOPY;
							if (looks.isEmpty()) {
								canopy = CanopyShape.GABLE;
								set.add("gable canopies");
							} else {
								set.add("the city's own canopies");
							}
						} else if (!roofSaid && modern && c == StationClass.REGIONAL && roofMaterial == null) {
							roofMaterial = RoofMaterial.GLASS;
							set.add("glass canopies");
						}
					}
					case GRAND -> {
						if (!tracksSaid && !terminus) {
							tracks = GRAND_TRACKS;
							set.add(GRAND_TRACKS + " tracks");
						}
						if (!lengthSaid && !terminus) {
							length = GRAND_LENGTH;
							set.add(GRAND_LENGTH + " long");
						}
						if (building == null) {
							building = true;
							buildingAt = -1;
						}
						if (scale == null || scaleFromLandmark) {
							scale = StationScale.GRAND;
							scaleFromLandmark = false;
							set.add("a grand station building");
						}
						if (!roofSaid) {
							roof = RoofType.HALL;
							// a city the words name keeps its own shed (St Pancras's pointed arch, Frankfurt's row of arches): the
							// class asks for a shed, the city says which (Studio rolls a class with every seed)
							if (looks.isEmpty()) {
								shed = e == StationEra.HISTORIC ? ShedShape.MULTI_ARCH : ShedShape.ARCH;
								set.add(e == StationEra.HISTORIC ? "a train shed of several arches" : "an arched train shed");
							} else {
								set.add("the city's own train shed");
							}
						}
						if (modern && !escalators) {
							escalators = true;
							set.add("escalators");
						}
					}
					case AUTO -> {
					}
				}
			}
			// the era chooses the look when none was named
			if (looks.isEmpty() && buildingStyle == null && style == null) {
				final boolean grand = c == StationClass.GRAND;
				final BuildingStyle lookBuilding = switch (e) {
					case HISTORIC, AUTO -> BuildingStyle.SANDSTONE;
					case INTERWAR -> grand ? BuildingStyle.MILAN : BuildingStyle.ART_DECO;
					case POSTWAR -> BuildingStyle.CONCRETE;
					case MODERN -> grand ? BuildingStyle.BERLIN : BuildingStyle.MODERN;
				};
				final StationStyle platforms = switch (e) {
					case HISTORIC, AUTO, POSTWAR -> StationStyle.GERMAN_SBAHN;
					case INTERWAR -> StationStyle.MODERN;
					case MODERN -> grand ? StationStyle.GERMAN_SBAHN : c == StationClass.HALT ? StationStyle.MODERN : StationStyle.DUTCH;
				};
				style = platforms;
				if (c != StationClass.HALT) {
					buildingStyle = lookBuilding;
				}
				set.add(e.label().toLowerCase(Locale.ROOT) + " look (" + styleName(platforms) + " platforms"
						+ (c == StationClass.HALT ? "" : ", " + lookBuilding.label() + " for a building") + ")");
			}
			final String name = (c == StationClass.AUTO ? "" : c.label().toLowerCase(Locale.ROOT) + " class") + (c != StationClass.AUTO && era != StationEra.AUTO ? ", " : "")
					+ (era == StationEra.AUTO ? "" : era.label().toLowerCase(Locale.ROOT) + " era");
			note(-1, name + (set.isEmpty() ? ": everything was said already" : ": " + String.join(", ", set)));
		}

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
			// two-word names of one thing, so a single token carries them
			normal = normal.replace("upper concourse", "mezzanine").replace("multi-level", "multilevel").replace("multi level", "multilevel").replace("tram stop", "tramstop").replace("car park", "carpark").replace("park and ride", "parkandride");
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

		/**
		 * No operator named: the first city or country word with an operator of its own gives it (Hamburg DB, Zurich SBB); London
		 * underground or metro is the Underground. "no operator" keeps the plain signs.
		 */
		private void cityOperator() {
			for (final Look look : looks) {
				Operator city = Operator.ofCity(look.word());
				if (city == Operator.NATIONAL_RAIL && style == StationStyle.METRO) {
					city = Operator.UNDERGROUND;
				}
				if (city != null) {
					operator = city;
					note(look.at(), city.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " signs (from " + look.word() + "; say \"no operator\" for plain ones)");
					return;
				}
			}
		}

		private void note(int at, String text) {
			understood.add(new Note(at, text));
		}

		private void setBothWays(int i) {
			if (!bothWays) {
				bothWays = true;
				note(i, "a second footbridge stair facing the other way on platforms wide enough (islands " + StationLayout.MIN_ESCALATOR_ISLAND
						+ ", side platforms " + StationLayout.MIN_ESCALATOR_SIDE + ")");
			}
		}

		/** "reverse stairs", "enclosed stairwells": the noun after the option word belongs to it. */
		private void useStairWord(int i) {
			final String word = at(i);
			if (word.equals("stair") || word.equals("stairs") || word.equals("stairwell") || word.equals("stairwells") || word.equals("staircases")) {
				use(i, i);
			}
		}

		private void setTravelators(int i) {
			if (!travelators) {
				travelators = true;
				note(i, "travelators along long platforms where there is room");
			}
		}

		private void warn(String text) {
			if (warned.add(text)) {
				warnings.add(text);
			}
		}

		private void scan(int i) {
			final String token = tokens[i];
			if (negation(i) || entranceWords(i) || number(i) || gateWords(i) || phrase(i)) {
				return;
			}
			keyword(i, token);
		}

		private static final Set<String> ENTRANCE_NOUNS = Set.of("entrance", "entrances", "door", "doors", "doorway", "doorways");

		/**
		 * The street doorway's width: "wide entrance" / "wider doors" (7), "narrow entrance" (3), "7 wide entrance",
		 * "entrance 7 blocks wide", "entrance width 7". Each asks for a station building, which is where the doorway is.
		 */
		private boolean entranceWords(int i) {
			final String token = tokens[i];
			if ((token.equals("wide") || token.equals("wider") || token.equals("big") || token.equals("bigger") || token.equals("narrow"))
					&& ENTRANCE_NOUNS.contains(at(i + 1))) {
				return setEntrance(token.equals("narrow") ? StationOptions.MIN_ENTRANCE : 7, i, i + 1);
			}
			final Integer n = value(token);
			if (n != null) {
				int j = i + 1;
				if (UNITS.contains(at(j))) {
					j++;
				}
				if ((at(j).equals("wide") || at(j).equals("width")) && ENTRANCE_NOUNS.contains(at(j + 1))) {
					return setEntrance(n, i, j + 1);
				}
				return false;
			}
			if (!ENTRANCE_NOUNS.contains(token)) {
				return false;
			}
			int j = i + 1;
			if (at(j).equals("width")) {
				j++;
			}
			final Integer m = value(at(j));
			if (m == null) {
				return false;
			}
			int k = j + 1;
			if (UNITS.contains(at(k))) {
				k++;
			}
			if (at(k).equals("wide")) {
				return setEntrance(m, i, k);
			}
			return j > i + 1 && setEntrance(m, i, k - 1);
		}

		private boolean setEntrance(int width, int from, int to) {
			final int w = Math.max(StationOptions.MIN_ENTRANCE, Math.min(StationOptions.MAX_ENTRANCE, width));
			if (w != width) {
				warn("entrance width " + width + " is outside " + StationOptions.MIN_ENTRANCE + "-" + StationOptions.MAX_ENTRANCE + "; using " + w);
			}
			use(from, to);
			options = options.withEntranceWidth(w);
			askBuilding(from);
			note(from, "street entrance " + (w % 2 == 0 ? w + 1 : w) + " wide");
			return true;
		}

		/**
		 * "mtr gates", "mtr ticket barriers", "default gates": MTR's own one-way barriers (the default); "ata gates",
		 * "aurelia gates", "two-way gates": ATA's fare gates.
		 */
		private boolean gateWords(int i) {
			final String token = tokens[i];
			final StationOptions.GateStyle style = switch (token) {
				case "mtr", "default", "standard" -> StationOptions.GateStyle.MTR;
				case "ata", "aurelia", "twoway" -> StationOptions.GateStyle.ATA;
				default -> null;
			};
			if (style == null) {
				// "gates", "gate line", "ticket barriers", "fare gates": a gate line in the default gates, which a German or Swiss
				// station otherwise goes without (Jah 10-26)
				int k = i;
				if (token.equals("ticket") || token.equals("fare")) {
					k++;
				}
				final String noun = at(k);
				final boolean line = noun.equals("gate") && at(k + 1).equals("line");
				if (!line && !noun.equals("gates") && !noun.equals("gateline") && !noun.equals("faregates") && !noun.equals("turnstiles")
						&& !(k > i && noun.equals("barriers"))) {
					return false;
				}
				use(i, line ? k + 1 : k);
				options = options.withGatesAsked();
				note(i, "a gate line");
				return true;
			}
			int j = i + 1;
			if (at(j).equals("ticket") || at(j).equals("fare")) {
				j++;
			}
			final String noun = at(j);
			if (!noun.equals("gate") && !noun.equals("gates") && !noun.equals("barrier") && !noun.equals("barriers") && !noun.equals("gateline")
					&& !noun.equals("faregates") && !noun.equals("turnstiles")) {
				return false;
			}
			use(i, j);
			options = options.withGates(style);
			note(i, style == StationOptions.GateStyle.MTR ? "MTR's own ticket barriers in the gate lines (entry and exit lanes)"
					: "ATA's two-way fare gates in the gate lines");
			return true;
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
				} else if (word.equals("ramp") || word.equals("ramps") || word.equals("accessible")) {
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
			// "300 blocks" alone is a length too, once it is long enough to be one
			if (METRE_UNITS.contains(unit) || (unit.equals("blocks") || unit.equals("block")) && n >= StationSpec.MIN_LENGTH) {
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
				case "parking":
				case "carpark":
				case "parkandride":
					setParking(i);
					use(i, i);
					return true;
				case "mezzanine":
				case "multilevel":
					if (!mezzanine) {
						mezzanine = true;
						note(i, "a mezzanine: the footbridge widened into an upper concourse over the tracks, with seats and departure screens");
					}
					use(i, i);
					return true;
				case "tram":
				case "trams":
				case "tramstop":
					if (!tram) {
						tram = true;
						note(i, "a tram stop: street track blocks to lay the MTR rail on, platforms, shelter seats and stop signs (on a hub, on its forecourt in place of the taxi rank)");
					}
					use(i, i);
					return true;
				case "underpass":
					if (!underpass) {
						underpass = true;
						note(i, "an underpass: a stairs-only pedestrian tunnel under the tracks to every platform (it digs: confirm with replace-terrain)");
					}
					use(i, i);
					return true;
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
				case "v":
					if (next.equals("roof") || next.equals("shaped") || next.equals("canopy") || next.equals("canopies")) {
						setCanopyShape(CanopyShape.BUTTERFLY, i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "flat":
					// "flat canopies": level platform canopies, whatever the look's own canopy is
					if (next.equals("canopy") || next.equals("canopies")) {
						setCanopyShape(CanopyShape.FLAT, i);
						use(i, i + 1);
						return true;
					}
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
					if (token.equals("single") && (next.equals("stair") || next.equals("stairs") || next.equals("staircase") || next.equals("staircases"))) {
						singleStairs = true;
						note(i, "one stair down to each platform");
						use(i, i + 1);
						return true;
					}
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
						if (looks.isEmpty() && at(i - 1).equals("real") && !at(i + 2).startsWith("style")) {
							cityStationName = "Grand Central";
						}
						setBuildingStyle(BuildingStyle.BEAUX_ARTS);
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
				case "both":
					if (next.equals("ways") || next.equals("way")) {
						setBothWays(i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "moving":
					if (next.equals("walkway") || next.equals("walkways")) {
						setTravelators(i);
						use(i, i + 1);
						return true;
					}
					return false;
				case "stair":
				case "stairs":
					if (next.equals("both") && (at(i + 2).equals("ways") || at(i + 2).equals("way"))) {
						setBothWays(i);
						use(i, i + 2);
						return true;
					}
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

		/**
		 * An operator word ("ns", "deutsche bahn", "gvb"), with the noun after it ("ns operator", "gvb logo"): 0 when it is not one,
		 * 1 for the bare word, 2 when the noun made it plainly the operator.
		 */
		private int operatorWord(int i, String token) {
			// "no operator": plain signs even where the city has an operator
			if (token.equals("no") && (at(i + 1).equals("operator") || at(i + 1).equals("logo") || at(i + 1).equals("logos"))) {
				if (operator == null) {
					operator = Operator.NONE;
					note(i, "plain name signs, no operator");
				}
				use(i + 1, i + 1);
				return 2;
			}
			final Operator pair = Operator.of(token + " " + at(i + 1));
			final boolean two = pair != null && pair != Operator.NONE;
			final Operator named = two ? pair : Operator.of(token);
			if (named == null || named == Operator.NONE) {
				return 0;
			}
			// a look word ("sbahn") or a station type ("london underground") is the operator only with the noun after it
			final String noun = at(i + (two ? 2 : 1));
			final boolean plain = noun.equals("operator") || noun.equals("logo") || noun.equals("signs");
			if (!plain && (!named.bare() || two && at(i + 1).equals("underground"))) {
				return 0;
			}
			if (operator == null) {
				operator = named;
				note(i, named.name() + " signs");
			} else if (operator != named) {
				warn("two operators given; the first (" + operator.name() + ") is used");
			}
			final int end = two ? i + 1 : i;
			if (two) {
				use(i + 1, i + 1);
			}
			if (at(end + 1).equals("operator") || at(end + 1).equals("logo") || at(end + 1).equals("signs")) {
				use(end + 1, end + 1);
				return 2;
			}
			return 1;
		}

		private void keyword(int i, String token) {
			// a bare "ns" or "db" also names a look (Dutch, German), which it keeps setting; "ns operator" is only the signs
			final int operatorKind = operatorWord(i, token);
			if (operatorKind == 2 || operatorKind == 1 && !STYLE_WORDS.containsKey(token)) {
				return;
			}
			if (buildingWord(i, token)) {
				return;
			}
			final StationStyle styleWord = STYLE_WORDS.get(token);
			if (UNDERGROUND_WORDS.contains(token) && undergroundAt < 0) {
				undergroundAt = i;
				note(i, "underground: a box under the street, stairs up to it");
			}
			final String tiled = TILES.get(token);
			if (tiled != null && TILE_NOUNS.contains(at(i + 1))) {
				if (tile == null) {
					tile = tiled;
					note(i, token + " wall tiles");
				}
				use(i, i + 1);
				return;
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
			} else if (HALL_WORDS.contains(token)) {
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
			} else if (token.equals("reverse") || token.equals("twoway")) {
				setBothWays(i);
				useStairWord(i + 1);
			} else if (token.equals("enclosed") || token.equals("walled")) {
				if (!enclosedStairs) {
					enclosedStairs = true;
					note(i, "walls round the footbridge stairwells");
				}
				useStairWord(i + 1);
			} else if (token.equals("travelator") || token.equals("travelators") || token.equals("travolator") || token.equals("travolators")) {
				setTravelators(i);
			} else if (token.equals("escalator") || token.equals("escalators")) {
				if (!escalators) {
					escalators = true;
					note(i, "up escalators beside the footbridge stairs on platforms wide enough (islands " + StationLayout.MIN_ESCALATOR_ISLAND
							+ ", side platforms " + StationLayout.MIN_ESCALATOR_SIDE + ")");
				}
			} else if (token.equals("gravel") || token.equals("ballast") || token.equals("andesite")) {
				final TrackBed bed = token.equals("andesite") ? TrackBed.ANDESITE : TrackBed.GRAVEL;
				if (trackBed == null) {
					trackBed = bed;
					note(i, bed.name().toLowerCase(java.util.Locale.ROOT) + " track bed with lit platform walls");
				} else if (trackBed != bed) {
					warn("two track beds given; the first (" + trackBed.name().toLowerCase(java.util.Locale.ROOT) + ") is used");
				}
				// "gravel bed", "andesite track bed": the nouns belong to it (the Studio and the wrench write the second)
				final int noun = at(i + 1).equals("track") ? i + 2 : i + 1;
				if (at(noun).equals("bed") || at(noun).equals("beds")) {
					use(i + 1, noun);
				}
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
			if (token.equals("real") && (CITY_BUILDINGS.containsKey(at(i + 1)) || at(i + 1).equals("st") || at(i + 1).equals("new") || at(i + 1).equals("kuala"))) {
				// "real frankfurt": the city's own station, not only its look (see cityStationName)
				use(i, i);
				return true;
			}
			if (looseReal == null && RealStation.loosely(token, at(i + 1)) != null) {
				// "utrecht station" may mean the real one: the city look stands until resolve() sees nothing against the hall
				looseReal = RealStation.loosely(token, at(i + 1));
				looseAt = i;
			}
			final RealStation named = RealStation.of(token + " " + at(i + 1));
			if (named != null) {
				if (real == null) {
					real = named;
					realAt = i;
					note(i, "the real " + named.label() + ": its " + (named.trainShed() ? "train shed, building and bridges" : "hall over the tracks")
							+ ", " + named.islands() + " island platforms and " + named.operator().name() + " signs");
				} else if (real != named) {
					warn("two real stations given; the first (" + real.label() + ") is built");
				}
				use(i, i + 1);
				return true;
			}
			if (token.equals("grand") && at(i + 1).equals("central") || token.equals("union") && at(i + 1).equals("station")
					|| token.equals("30th") && at(i + 1).equals("street")) {
				setBuildingStyle(BuildingStyle.BEAUX_ARTS);
				askBuilding(i);
				if (scale == null) {
					// the landmark's own size; a size word said before or after it wins without a warning
					scale = StationScale.GRAND;
					scaleAt = i;
					scaleFromLandmark = true;
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
			if (token.equals("kuala") && at(i + 1).equals("lumpur")) {
				cityLook(i, "kuala lumpur", BuildingStyle.KUALA_LUMPUR);
				use(i, i + 1);
				return true;
			}
			if (token.equals("hua") && at(i + 1).equals("lamphong")) {
				use(i, i);
				return true;
			}
			if (token.equals("hong") && at(i + 1).equals("kong")) {
				cityLook(i, "hong kong", BuildingStyle.HONG_KONG);
				use(i, i + 1);
				return true;
			}
			final BuildingStyle city = CITY_BUILDINGS.get(token);
			if (city != null && !(token.equals("us") && !looks.isEmpty())) {
				cityLook(i, token, city);
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
					note(i, shape.label());
				}
				if (roof == null) {
					roof = RoofType.HALL;
				}
				use(i, i);
				return true;
			}
			final CanopyShape canopyShape = CANOPY_SHAPE_WORDS.get(token);
			if (canopyShape != null) {
				setCanopyShape(canopyShape, i);
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

		/** A city or look word at {@code i}: the building look, a landmark's building, and the look list for mixing. */
		private void cityLook(int i, String word, BuildingStyle city) {
			// only "real frankfurt" is the station (the wrench's city buttons say it); a bare "frankfurt terminus" is the city's look
			final int before = word.equals("pancras") && at(i - 1).equals("st") ? i - 2 : i - 1;
			if (looks.isEmpty() && at(before).equals("real") && !at(i + 1).startsWith("style")) {
				// a real station's city word ("frankfurt terminus"; "frankfurt style" is only its look) is that station
				cityStationName = switch (word) {
					case "pancras" -> "St Pancras";
					case "frankfurt" -> "Frankfurt Hbf";
					case "paris" -> "Paris Nord";
					case "milan", "milano" -> "Milano Centrale";
					case "munich", "munchen" -> "Munich Hbf";
					case "berlin" -> "Berlin Hbf";
					case "coventry" -> "Coventry";
					case "tokyo" -> "Tokyo";
					case "beijing" -> "Beijing";
					case "kuala lumpur" -> "Kuala Lumpur";
					case "surbiton" -> "Surbiton";
					case "antwerp", "antwerpen" -> "Antwerpen Centraal";
					case "zurich" -> "Zurich HB";
					case "mumbai" -> "Mumbai CSMT";
					case "bangkok" -> "Hua Lamphong";
					default -> null;
				};
			}
			if (looks.isEmpty() && word.equals("amsterdam") && scale == null) {
				// Amsterdam Centraal's long brick front, with a tower each side of its entrance, is a grand building
				scale = StationScale.GRAND;
				scaleAt = i;
				scaleFromLandmark = true;
			}
			setBuildingStyle(city);
			if (LANDMARKS.contains(word)) {
				askBuilding(i);
			}
			final StationStyle platformStyle = word.equals("antwerp") || word.equals("antwerpen") || word.equals("belgian")
					|| word.equals("brussels") ? StationStyle.DUTCH : STYLE_WORDS.get(word);
			if (looks.stream().noneMatch(look -> look.building() == city && look.style() == platformStyle)) {
				looks.add(new Look(i, word, city, platformStyle));
			}
			if (lookOnlyWord == null && platformStyle == null && !LANDMARKS.contains(word)) {
				lookOnlyWord = word;
			}
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
			// the shed: from the last other look that has one, else from the first look (a British suburban station has
			// canopies; its ridge-and-furrow shed comes only when a train shed is asked for)
			Look shedLook = null;
			for (int k = looks.size() - 1; k >= 0 && shedLook == null; k--) {
				final Look look = looks.get(k);
				if (look.building() != BuildingStyle.VICTORIAN
						&& ShedShape.forLook(look.building(), look.style() == null ? StationStyle.MODERN : look.style()).arched()) {
					shedLook = look;
				}
			}
			if (shedLook != null && !roofNone && (roof == null && !weakRoof || roof == RoofType.HALL)) {
				if (roof == null) {
					roof = RoofType.HALL;
				}
				if (shed == null) {
					shed = ShedShape.forLook(shedLook.building(), shedLook.style() == null ? StationStyle.MODERN : shedLook.style());
					note(shedLook.at(), shed == ShedShape.ARCH ? "arched glass train shed" : shed.label());
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

		/** A terminus not asked to be small: it defaults to a main station's size. */
		private boolean bigTerminus() {
			return terminus && !small && scale != StationScale.COMPACT;
		}

		/** The first size word wins over a later one; a size taken from a landmark gives way to one written out. */
		private void setScale(StationScale value, int i) {
			if (scale == null || scaleFromLandmark) {
				scaleFromLandmark = false;
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

		private void setBuildingStyle(BuildingStyle value) {
			if (buildingStyle == null) {
				buildingStyle = value;
			}
		}

		/** A surface car park beside the street entrance (built from the paving and kerb blocks already in the mod). */
		private void setParking(int i) {
			if (!parking) {
				parking = true;
				note(i, "a surface car park beside the street entrance");
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

		/** The first canopy shape wins; it also asks for canopies when no roof was given. */
		private void setCanopyShape(CanopyShape value, int i) {
			if (canopy == null) {
				canopy = value;
				note(i, switch (value) {
					case BUTTERFLY -> "butterfly canopies: a V roof, low over the columns and rising to both edges";
					case GABLE -> "gable canopies: a pitched roof with its ridge over the columns";
					case FLAT -> "flat canopies";
				});
			} else if (canopy != value) {
				warn("canopy shape given twice; using " + canopy.label());
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

		/**
		 * A real station fixes what it really is: a through station with no building beside the tracks, its own platforms (unless
		 * the words count the tracks or list the platforms), escalators, its operator and name, Dutch platforms. Words that
		 * contradict it are dropped with a warning; words it leaves open (length, width, seed) still apply.
		 */
		/**
		 * True when nothing else in the words is against the real station "utrecht station" may mean: no building, roof, shed,
		 * canopy, style or second look of their own, no terminus, side platforms or tunnel, an even track count and platforms long
		 * enough for its hall. Otherwise the words keep the city's look on a station of their own, as before.
		 */
		private boolean looseFits() {
			final boolean ownBuilding = Boolean.TRUE.equals(building) && buildingAt != looseAt + 1;
			final boolean ownBuildingStyle = buildingStyle != null && buildingStyle != CITY_BUILDINGS.get(looseReal.phrase().split(" ")[0]);
			if (ownBuilding || ownBuildingStyle || looks.size() > 1 || roof != null || shed != null || canopy != null || terminus || hub || mezzanine
					|| sidePlatforms || tram || undergroundAt >= 0 || Boolean.FALSE.equals(footbridge) || style != null && style != StationStyle.DUTCH
					|| lengthAdjective != null) {
				return false;
			}
			if (tracks != null && (tracks < 2 || tracks % 2 != 0) || listed.values().stream().anyMatch(p -> p.kind() != PlatformSpec.Kind.ISLAND)) {
				return false;
			}
			return length == null || looseReal.trainShed() || looseReal.hallDepth(StationGeometry.builtLength(length)) > 0;
		}

		private void applyReal() {
			if (real == null && looseReal != null && looseFits()) {
				real = looseReal;
				realAt = looseAt;
				// the city word's look and building were the station itself; they go without a warning
				buildingStyle = null;
				looks.clear();
				lookOnlyWord = null;
				if (tracks != null && listed.isEmpty() && islandPlatforms == null) {
					for (int number = 1; number <= tracks / 2; number++) {
						listed.put(number, PlatformSpec.of(PlatformSpec.Kind.ISLAND));
					}
				}
				note(looseAt, "the real " + real.label() + " (say \"" + real.phrase().split(" ")[0] + " style\" for its look on a station of your own)");
			}
			if (real == null) {
				return;
			}
			if (real.trainShed()) {
				applyShed();
				return;
			}
			final String who = real.label();
			if (terminus || hub) {
				warn(who + " is a through station; the terminus is left out");
				terminus = false;
				hub = false;
			}
			if (Boolean.TRUE.equals(building) || buildingStyle != null || !looks.isEmpty()) {
				warn(who + " has its hall over the tracks, not a building beside them; the building words are left out");
			}
			building = false;
			buildingStyle = null;
			looks.clear();
			lookOnlyWord = null;
			if (mezzanine) {
				warn(who + "'s hall is its concourse; the mezzanine is left out");
				mezzanine = false;
			}
			if (Boolean.FALSE.equals(footbridge)) {
				warn(who + "'s platforms are reached from its hall over the tracks; \"no footbridge\" is left out");
			}
			footbridge = true;
			if (roof == RoofType.HALL) {
				warn(who + " has its hall over the middle of the platforms and canopies past it; the train shed is left out");
				roof = null;
			}
			if (listed.isEmpty() && tracks == null && islandPlatforms == null) {
				for (int number = 1; number <= real.islands(); number++) {
					listed.put(number, PlatformSpec.of(PlatformSpec.Kind.ISLAND));
				}
				note(realAt, real.islands() + " island platforms, " + real.tracks() + " tracks (as at " + who + ")");
			} else {
				note(realAt, who + " has " + real.islands() + " island platforms; building the ones asked for");
			}
			if (sidePlatforms) {
				warn(who + " has island platforms only; the side platforms are left out");
				sidePlatforms = false;
			}
			if (length == null && lengthAdjective == null && listed.values().stream().allMatch(p -> p.length() == 0)) {
				length = REAL_LENGTH;
				note(realAt, "length " + REAL_LENGTH + " (the real platforms are longer; " + REAL_LENGTH + " keeps the build to a size a PC can take)");
			}
			if (!escalators) {
				escalators = true;
				note(realAt, "escalators beside the stairs (as at " + who + ")");
			}
			if (operator == null) {
				operator = real.operator();
			}
			if (style == null) {
				style = StationStyle.DUTCH;
			}
			if (name.isEmpty()) {
				name = who;
			}
		}

		/**
		 * A real station under a train shed (Hamburg): a grand through station with its building beside the tracks, one arched
		 * shed over all its island platforms, the shed's length, footbridges across at both ends, escalators, its operator and
		 * name. Words that contradict it are dropped with a warning; words it leaves open (width, seed, furniture) still apply.
		 */
		private void applyShed() {
			final String who = real.label();
			if (terminus || hub) {
				warn(who + " is a through station; the terminus is left out");
				terminus = false;
				hub = false;
			}
			if (Boolean.FALSE.equals(building)) {
				warn(who + " has its station building beside the tracks; \"no building\" is left out");
			}
			if (buildingStyle != null || !looks.isEmpty()) {
				warn(who + " is built as it is; the look words are left out");
			}
			building = true;
			buildingStyle = null;
			looks.clear();
			lookOnlyWord = null;
			if (scale != null && scale != StationScale.GRAND) {
				warn(who + " is a grand station; the smaller building is left out");
			}
			scale = StationScale.GRAND;
			if (mezzanine) {
				warn(who + "'s platforms are reached from bridges over the tracks; the mezzanine is left out");
				mezzanine = false;
			}
			if (Boolean.FALSE.equals(footbridge)) {
				warn(who + "'s platforms are reached from bridges over the tracks; \"no footbridge\" is left out");
			}
			footbridge = true;
			if (roofNone || roof != null && roof != RoofType.HALL || shed != null && shed != ShedShape.ARCH) {
				warn(who + " has one arched train shed over all its platforms; the other roof is left out");
			}
			roofNone = false;
			roof = RoofType.HALL;
			shed = ShedShape.ARCH;
			if (listed.isEmpty() && tracks == null && islandPlatforms == null) {
				for (int number = 1; number <= real.islands(); number++) {
					listed.put(number, PlatformSpec.of(PlatformSpec.Kind.ISLAND));
				}
				note(realAt, real.islands() + " island platforms, " + real.tracks() + " tracks (as at " + who + "; the two through tracks"
						+ " without platforms and the S-Bahn tunnel are not built)");
			} else {
				note(realAt, who + " has " + real.islands() + " island platforms; building the ones asked for");
			}
			if (sidePlatforms) {
				warn(who + " has island platforms only; the side platforms are left out");
				sidePlatforms = false;
			}
			if (length == null && lengthAdjective == null && listed.values().stream().allMatch(p -> p.length() == 0)) {
				length = real.hallDepthBlocks();
				note(realAt, "length " + length + ", the shed's " + real.hallDepth() + " m (the real platforms run on past it)");
			}
			if (!escalators) {
				escalators = true;
				note(realAt, "escalators beside the stairs (as at " + who + ")");
			}
			if (operator == null) {
				operator = real.operator();
			}
			if (style == null) {
				style = StationStyle.GERMAN_SBAHN;
			}
			if (name.isEmpty()) {
				name = who;
			}
		}

		/**
		 * A tram stop on its own ("tram stop", not a hub's): what the words leave open is a tram's. Open platforms with
		 * shelters, {@link #TRAM_LENGTH} long and {@link #TRAM_WIDTH} wide, no building or footbridge, modern platforms; the
		 * planner lays street track under the rails and puts a tram stop sign at each platform's end.
		 */
		private void tramStopDefaults() {
			if (!tram || hub || terminus) {
				return;
			}
			if (roof == null && !roofNone) {
				roofNone = true;
				defaulted.add("open platforms with shelters (a tram stop)");
			}
			if (length == null && lengthAdjective == null && listed.isEmpty()) {
				length = TRAM_LENGTH;
				defaulted.add(TRAM_LENGTH + " long (a tram stop)");
			}
			if (width == null && !wide && listed.isEmpty()) {
				width = TRAM_WIDTH;
				defaulted.add(TRAM_WIDTH + " wide (a tram stop)");
			}
			if (footbridge == null) {
				footbridge = false;
			}
			if (building == null && looks.isEmpty()) {
				building = false;
			}
			if (style == null) {
				style = StationStyle.MODERN;
			}
		}

		/**
		 * An underground station (Enviro, 10-20) is a through station in a box dug under the street: its platforms at the
		 * bottom, the box's roof the street, and stairs from the platforms straight up to it (the footbridge's flights, with
		 * the street for a deck). So it has no roof or building of its own, no ramps onto the tracks and no tram or underpass;
		 * a look word picks its wall tiles instead of a building. A terminus underground is not built yet.
		 */
		private void undergroundDefaults() {
			if (undergroundAt < 0) {
				return;
			}
			// a terminus comes with its head building, which the warning about the terminus covers
			final boolean askedBuilding = Boolean.TRUE.equals(building) && !terminus && !hub;
			if (terminus || hub) {
				warn("an underground terminus is not built yet; building an underground through station");
				terminus = false;
				hub = false;
			}
			if (askedBuilding) {
				warn("an underground station is reached by stairs from the street; it has no station building");
			}
			building = false;
			if (roof != null && roof != RoofType.NONE) {
				warn("an underground station's roof is the street above it; no platform roof");
			}
			roof = null;
			roofNone = true;
			if (Boolean.FALSE.equals(footbridge)) {
				warn("the stairs up to the street are an underground station's way in; they stay");
			}
			footbridge = true;
			if (underpass || tram || mezzanine) {
				warn("an underground station has stairs up to the street only: no " + (underpass ? "underpass" : tram ? "tram stop" : "mezzanine"));
				underpass = false;
				tram = false;
				mezzanine = false;
			}
			if (style == null) {
				style = StationStyle.METRO;
			}
			if (tile == null && buildingStyle != null) {
				tile = switch (buildingStyle) {
					case BERLIN, CLINKER, SANDSTONE -> ATA + "metro_tile_orange";
					case CONCRETE -> ATA + "metro_ribbed_concrete";
					case GOTHIC_BRICK, VICTORIAN, ART_DECO, MODERNIST -> ATA + "metro_tile_green";
					case MODERN, AMSTERDAM -> ATA + "metro_pale_concrete";
					case TOKYO, BEIJING, HONG_KONG -> "minecraft:smooth_quartz";
					default -> null;
				};
			}
			options = options.withUnderground(true).withTile(tile == null ? "" : tile);
		}

		private StationSpec resolve(boolean blank) {
			applyReal();
			undergroundDefaults();
			tramStopDefaults();
			final StationSpec.Builder builder = StationSpec.builder();
			if (blank) {
				defaulted.add("no description: using defaults");
			}
			final boolean island = islandAt >= 0;

			// a platform list fixes the tracks and the layout; a hub needs islands all along, an even number of tracks
			final List<PlatformSpec> list = List.copyOf(listed.values());
			if (!list.isEmpty()) {
				note(-1, list.size() + (list.size() == 1 ? " platform" : " platforms") + " listed one by one");
				if (tracks != null || islandPlatforms != null || sidePlatforms) {
					// the wrench and Studio always say how many tracks; the list wins, so this is not worth a warning
					note(-1, "tracks and platforms from the platform list");
				}
				if (terminus && list.stream().anyMatch(PlatformSpec::bay)) {
					note(-1, "every track of a terminus ends at the head building, so \"bay\" changes nothing there");
				}
				final int listedTracks = StationSpec.listedTracks(list);
				if (hub && (listedTracks < 4 || listedTracks % 2 != 0 || list.stream().anyMatch(p -> p.kind() != PlatformSpec.Kind.ISLAND))) {
					warn("a hub needs island platforms only, with at least 4 tracks; building a terminus");
					hub = false;
				}
			}

			// tracks
			int trackCount;
			if (!list.isEmpty()) {
				trackCount = StationSpec.listedTracks(list);
				builder.platforms(list);
			} else if (tracks != null) {
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
			if (!list.isEmpty()) {
				layout = list.stream().allMatch(p -> p.kind() == PlatformSpec.Kind.ISLAND) ? PlatformLayout.ISLAND : PlatformLayout.SIDE;
			} else if (hub) {
				layout = PlatformLayout.ISLAND;
				if (sidePlatforms) {
					warn("a hub has island platforms, each shared by a track that ends and one that runs through");
				}
			} else if (island && sidePlatforms) {
				if (trackCount >= 3) {
					layout = PlatformLayout.ISLAND;
					note(Math.min(islandAt, sideAt), "island and side platforms");
					if (trackCount % 2 == 0) {
						// islands pair the tracks off, so only an odd last track is left for a side platform
						warn(trackCount + " tracks pair off onto island platforms, which leaves no track for a side platform; say "
								+ (trackCount + 1) + " or " + (trackCount - 1) + " tracks for islands and a side platform");
					}
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
			} else if (real != null && trackCount >= 2) {
				// a real station's platforms are what they really are, whatever the track count
				layout = PlatformLayout.ISLAND;
				defaulted.add("island platforms (as at " + real.label() + ")");
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
			// the station is as long as its longest listed platform
			final int longest = list.stream().mapToInt(PlatformSpec::length).max().orElse(0);
			if (longest > 0) {
				final int stationLength = builder.build().platformLength();
				if (longest > stationLength) {
					builder.platformLength(longest);
					note(-1, "length " + longest + " (the longest listed platform)");
				}
			}

			// island platforms need a way in that does not cross a track: a building's footbridge, or a footbridge of their own,
			// reached from a side platform on the edge or, with tracks along both edges, from a street entrance beside the first
			final TrackLayout bands = StationGeometry.layout(builder.build());
			// an island, or side platforms back to back between two tracks: a track on either side of it somewhere
			final boolean islands = !terminus && bands.platforms().stream().anyMatch(b -> bands.tracks().stream().anyMatch(t -> t.u() < b.uMin())
					&& bands.tracks().stream().anyMatch(t -> t.u() > b.uMax()));
			final boolean sideEdge = !bands.platforms().isEmpty()
					&& (!bands.platforms().get(0).trackLeft() || !bands.platforms().get(bands.platforms().size() - 1).trackRight());
			// a look word asks for a building later, in resolveLooks
			final boolean buildingComing = Boolean.TRUE.equals(building) || building == null && !looks.isEmpty();
			if (islands && !buildingComing) {
				if (footbridge == null) {
					footbridge = true;
					defaulted.add(sideEdge ? "footbridge (the platforms between the tracks need one)" : "footbridge with a street entrance (the platforms between the tracks need a way in)");
				} else if (!footbridge && !(tram && !hub)) {
					warn("with no footbridge the platforms between the tracks are reached by walking across a track");
				}
			}
			// side platforms facing each other across the tracks: with no way over, the far one is reached only across a track
			// (Enviro, 10-20). A footbridge goes over, unless the words say otherwise, the station is a halt (open platforms,
			// each with its own path) or an underpass goes under. An empty description still plans the plain defaults.
			final boolean acrossTracks = !terminus && !islands && bands.platforms().size() > 1;
			if (acrossTracks && !blank && footbridge == null && !underpass && !roofNone && stationClass != StationClass.HALT) {
				footbridge = true;
				defaulted.add("footbridge (the platforms face each other across the tracks)");
			}

			// a shorter building has no room for its stair up a storey on one side of the entrance and its lift on the other
			if (buildingComing && !terminus && builder.build().platformLength() < BUILDING_LIFT_LENGTH) {
				warn("a station building needs platforms " + BUILDING_LIFT_LENGTH + " long for its stairs and its lift; using " + BUILDING_LIFT_LENGTH);
				builder.platformLength(BUILDING_LIFT_LENGTH);
			}
			if (hub && builder.build().platformLength() < HUB_LENGTH) {
				warn("a hub needs platforms " + HUB_LENGTH + " long for its footbridge's stairs and lifts; using " + HUB_LENGTH);
				builder.platformLength(HUB_LENGTH);
			}

			// width
			// a footbridge's side platforms need room for a two-column stair, or they get no lift and are not step-free
			final boolean bridgeSide = layout != PlatformLayout.ISLAND && Boolean.TRUE.equals(footbridge) && !terminus;
			// a second stair or an up escalator needs a wider platform beside the lift: asking for one widens the platforms to fit
			final boolean stairGroup = (bothWays || escalators) && Boolean.TRUE.equals(footbridge);
			final int groupWidth = layout == PlatformLayout.ISLAND ? StationLayout.MIN_ESCALATOR_ISLAND : StationLayout.MIN_ESCALATOR_SIDE;
			final boolean autoWidth = width == null && !narrow && !wide && !small;
			final int defaultWidth = stairGroup ? groupWidth : layout == PlatformLayout.ISLAND ? StationSpec.DEFAULT_ISLAND_WIDTH
					: bridgeSide ? StationSpec.DEFAULT_FOOTBRIDGE_SIDE_WIDTH : StationSpec.DEFAULT_SIDE_WIDTH;
			if (width != null) {
				builder.platformWidth(clamp(width, StationSpec.MIN_WIDTH, StationSpec.MAX_WIDTH, "width"));
				if (stairGroup && width < groupWidth) {
					warn((bothWays ? "stairs both ways" : "escalators") + " need platforms " + groupWidth + " wide; at " + width
							+ " each platform gets one stair");
				}
			} else if (narrow) {
				builder.platformWidth(StationSpec.MIN_WIDTH);
			} else if (wide) {
				builder.platformWidth(layout == PlatformLayout.ISLAND ? 14 : 12);
			} else if (small) {
				builder.platformWidth(StationSpec.MIN_WIDTH);
			} else if (impliedHalt(buildingComing, builder.build().trackCount(), stairGroup)) {
				// no class named, but the station amounts to a halt: it gets a halt's platforms, as "halt class" would give it
				builder.platformWidth(HALT_WIDTH);
				defaulted.add("width " + HALT_WIDTH + " (a halt's: no building, " + builder.build().trackCount() + (builder.build().trackCount() == 1 ? " track)" : " tracks)"));
			} else {
				builder.platformWidth(defaultWidth);
				defaulted.add("width " + defaultWidth + (stairGroup ? " (so the " + (bothWays ? "second stair" : "escalator") + " fits beside the lift)"
						: bridgeSide ? " (default with a footbridge, so the lifts fit)" : " (default)"));
			}

			// a platform between two tracks is reached only by the footbridge: an island needs room for its stair and a walkway
			// past it, and every such platform room for a lift when step-free
			final boolean bridgeComing = !terminus && (Boolean.TRUE.equals(footbridge) || buildingComing);
			if (islands && bridgeComing || terminus && hub) {
				final boolean lift = !Boolean.FALSE.equals(stepFree);
				final int islandNeed = lift ? StationGeometry.MIN_LIFT_ISLAND_WIDTH : StationLayout.MIN_STAIR_ISLAND;
				final int narrowest = builder.build().platformWidth();
				if (list.isEmpty()) {
					if (layout == PlatformLayout.ISLAND && narrowest < islandNeed) {
						warn("island platforms need to be " + islandNeed + " wide for a footbridge stair" + (lift ? " and lift" : "") + "; using " + islandNeed);
						builder.platformWidth(islandNeed);
					}
				} else {
					final List<PlatformSpec> widened = new ArrayList<>();
					final List<String> given = new ArrayList<>();
					boolean quiet = false;
					// one side platform on an edge wide enough for its lift is a step-free way up; else every edge one gets room
					// a side platform with tracks further out on both sides is between the tracks, not on the edge, even with no track behind it
					final boolean[] innerAt = new boolean[list.size()];
					boolean liftedEntry = false;
					for (int k = 0; k < list.size(); k++) {
						final TrackLayout.PlatformBand band = bands.platforms().get(k);
						innerAt[k] = list.get(k).kind() == PlatformSpec.Kind.ISLAND || !terminus && bands.tracks().stream().anyMatch(t -> t.u() < band.uMin())
								&& bands.tracks().stream().anyMatch(t -> t.u() > band.uMax());
						liftedEntry |= !innerAt[k] && list.get(k).widthIn(narrowest) >= StationSpec.DEFAULT_FOOTBRIDGE_SIDE_WIDTH;
					}
					for (int k = 0; k < list.size(); k++) {
						final PlatformSpec p = list.get(k);
						final boolean inner = innerAt[k];
						// without a building a side platform on the edge is the way up to the footbridge, so it needs the lift too
						final boolean entry = !inner && !buildingComing && !liftedEntry;
						int need = p.kind() == PlatformSpec.Kind.ISLAND ? islandNeed
								: (inner || entry) && lift ? StationSpec.DEFAULT_FOOTBRIDGE_SIDE_WIDTH : 0;
						// an island left on "auto" gets an island's default width, not the side platforms' (the list's first platform sets the base)
						if (p.kind() == PlatformSpec.Kind.ISLAND && p.width() == 0 && autoWidth) {
							need = Math.max(need, stairGroup ? groupWidth : StationSpec.DEFAULT_ISLAND_WIDTH);
						}
						if (p.widthIn(narrowest) < need) {
							widened.add(new PlatformSpec(p.kind(), need, p.length(), p.roof(), p.bay()));
							if (p.width() != 0) {
								given.add(String.valueOf(k + 1));
							} else {
								quiet = true;
							}
						} else {
							widened.add(p);
						}
					}
					if (!given.isEmpty()) {
						warn("platform" + (given.size() == 1 ? " " : "s ") + String.join(", ", given) + " on the way to the footbridge need" + (given.size() == 1 ? "s" : "")
								+ " room for a stair"
								+ (lift ? " and lift" : "") + "; widened to fit");
					}
					if (quiet) {
						defaulted.add("platforms on the way to the footbridge widened so their stair" + (lift ? " and lift" : "") + " fit");
					}
					builder.platforms(widened);
				}
			}

			// a canopy shape asks for canopies, unless the text chose a roof (or a look would give it a train shed)
			if (canopy != null && roof == null && !roofNone) {
				roof = RoofType.CANOPY;
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
			} else if (seed != 0 && dice(1).nextInt(5) < 3) {
				// the seed picks a platform floor three times in five; the other two keep the style's own
				final SurfaceMaterial floor = pick(dice(2), SurfaceMaterial.LIGHT_PAVING, SurfaceMaterial.DARK_PAVING, SurfaceMaterial.CONCRETE,
						SurfaceMaterial.TERRAZZO, SurfaceMaterial.GRANITE);
				builder.surface(floor);
				seeded.add(floor.name().toLowerCase(Locale.ROOT).replace("_", " ") + " platforms");
			}
			if (roofMaterial == null && !roofNone && seed != 0 && dice(3).nextInt(2) == 0) {
				final RoofMaterial deck = pick(dice(4), RoofMaterial.STEEL, RoofMaterial.GLASS, RoofMaterial.TIMBER);
				builder.roofMaterial(deck);
				seeded.add(deck.name().toLowerCase(Locale.ROOT) + " roof");
			}
			if (stepFree != null) {
				builder.stepFree(stepFree);
			} else {
				defaulted.add("step-free access (default)");
			}
			if (trackBed != null) {
				builder.trackBed(trackBed);
			}
			if (operator == null) {
				cityOperator();
			}
			if (operator != null) {
				builder.operator(operator);
			}
			builder.escalators(escalators).stairsBothWays(bothWays).enclosedStairs(enclosedStairs).travelators(travelators).parking(parking).underpass(underpass).tram(tram).mezzanine(mezzanine)
					.options(options);
			if (travelators) {
				final List<TrackLayout.PlatformBand> built = StationGeometry.layout(builder.build()).platforms();
				if (built.stream().noneMatch(b -> b.length() >= StationPlanner.TRAVELATOR_PLATFORM)) {
					warn("travelators need platforms " + (StationPlanner.TRAVELATOR_PLATFORM - 1) + " long or more; these are too short for any");
				} else if (built.stream().noneMatch(b -> b.length() >= StationPlanner.TRAVELATOR_PLATFORM
						&& b.width() >= (b.island() ? StationLayout.MIN_TRAVELATOR_ISLAND : StationLayout.MIN_TRAVELATOR_SIDE))) {
					warn("travelators need platforms " + StationLayout.MIN_TRAVELATOR_SIDE + " wide (islands " + StationLayout.MIN_TRAVELATOR_ISLAND + "); these are too narrow for any");
				}
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
			if (terminus && Boolean.FALSE.equals(building)) {
				// the tracks end at the head building's buffer stops and gate line; without it there is no way off the platforms
				warn("a " + (hub ? "hub" : "terminus") + " needs its head building, so it keeps one");
				building = true;
			}
			final boolean wantsBuilding = Boolean.TRUE.equals(building);
			if (wantsBuilding) {
				final BuildingStyle look = buildingStyle != null ? buildingStyle : BuildingStyle.forStyle(style != null ? style : StationStyle.MODERN);
				builder.building(look);
				note(buildingAt, "station building: " + look.label() + ", furnished");
			} else if (lookOnlyWord != null && undergroundAt < 0) {
				// underground, the look gives the wall tiles (see undergroundDefaults)
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
			// a bigger through station (a city or grand building) gives every platform wide enough two ways down from its footbridge, so
			// the people from one train spread over two stairs; "single stairs" keeps it to one
			if (wantsBuilding && !terminus && !bothWays && !singleStairs) {
				final StationScale built = builder.build().buildingScale();
				if (built != StationScale.COMPACT) {
					builder.stairsBothWays(true);
					defaulted.add("a second footbridge stair to each wide platform (a " + built.label() + " station has two ways down; \"single stairs\" keeps one)");
				}
			}
			if (terminus && hub) {
				builder.terminus(true).hub(true);
				note(hubAt, "hub: the outer tracks run through past the head building, the inner ones end at its buffer stops");
			} else if (terminus) {
				builder.terminus(true);
				note(terminusAt, "terminus: the tracks end at buffer stops in front of the head building");
			}
			if (parking && hub) {
				warn("a hub's forecourt already has its taxi rank and bus interchange; the car park is left out");
				builder.parking(false);
			} else if (parking && terminus) {
				note(-1, "parking: a forecourt past the head building with a drop-off lay-by, cycle stands and trees");
			} else if (parking && !builder.build().sideBuilding()) {
				// a buildingless station has no street wall to park in front of
				warn("a car park is only built in front of a station building beside the platforms or past a terminus; it is left out here");
				builder.parking(false);
			}
			if (tram && terminus && !hub) {
				warn("a terminus's tram stop is built on a hub's forecourt (say \"hub\"); it is left out here");
				builder.tram(false);
			}
			if (underpass && terminus) {
				warn("a terminus is reached from its concourse, so it gets no underpass");
				builder.underpass(false);
			}
			if (mezzanine && terminus) {
				warn("a terminus is reached from its concourse, so it gets no mezzanine");
				builder.mezzanine(false);
			} else if (mezzanine && builder.build().trackCount() < 2) {
				warn("a mezzanine spans tracks; one track has nothing to span, so it is left out");
				builder.mezzanine(false);
			}
			final boolean bridge = Boolean.TRUE.equals(footbridge) || mezzanine && !terminus && builder.build().trackCount() >= 2;
			if (bridge && terminus) {
				warn("a terminus is reached from its concourse, so it gets no footbridge");
			} else if (bridge) {
				builder.footbridge(true);
			}
			if (roof != RoofType.HALL && !roofNone) {
				final BuildingStyle look = wantsBuilding ? builder.build().building() : buildingStyle != null ? buildingStyle : BuildingStyle.NONE;
				final CanopyShape own = CanopyShape.forLook(look, style != null ? style : StationStyle.MODERN);
				// the seed swaps the look's own canopy for another two times in five, but not a city's the words name (its canopy
				// is part of it, like its shed)
				final CanopyShape shape = canopy != null ? canopy : seed != 0 && looks.isEmpty() && dice(5).nextInt(5) < 2
						? pick(dice(6), Arrays.stream(CanopyShape.values()).filter(c -> c != own).toArray(CanopyShape[]::new)) : own;
				builder.canopy(shape);
				if (canopy == null && shape != own) {
					seeded.add(shape.label() + " canopies");
				} else if (canopy == null && shape != CanopyShape.FLAT) {
					defaulted.add(shape.label() + " canopies (default for this look)");
				}
			} else if (canopy != null) {
				warn("a canopy shape was given but the station has " + (roofNone ? "no roof" : "a train shed"));
			}
			if (roof == RoofType.HALL && !roofNone) {
				final ShedShape own = ShedShape.forLook(wantsBuilding ? builder.build().building() : BuildingStyle.NONE,
						style != null ? style : StationStyle.MODERN);
				// one wide arch and a row of arches are both a city's barrel-vaulted shed, so the seed may swap them; a pointed
				// (St Pancras) or flat shed is the look itself and stays, and so does the shed of a city the words name (Jah,
				// 10-24: Amsterdam Centraal has one arch and Frankfurt Hbf a row of them, whatever the seed)
				final boolean swap = shed == null && seed != 0 && looks.isEmpty() && (own == ShedShape.ARCH || own == ShedShape.MULTI_ARCH)
						&& dice(7).nextInt(5) < 2;
				builder.shed(shed != null ? shed : swap ? (own == ShedShape.ARCH ? ShedShape.MULTI_ARCH : ShedShape.ARCH) : own);
				if (swap) {
					seeded.add(own == ShedShape.ARCH ? "a train shed of several arches" : "one arched train shed");
				}
			}
			if (real != null && real.trainShed()) {
				if (RealStation.islandsOnly(builder.build())) {
					builder.real(real);
				} else {
					warn(real.label() + " has island platforms only, two tracks each; building plain platforms under a train shed");
				}
			} else if (real != null) {
				final StationSpec sized = builder.build();
				final int depth = real.hallDepth(StationGeometry.builtLength(sized.platformLength()));
				if (depth == 0) {
					warn(real.label() + "'s hall needs platforms " + (RealStation.MIN_HALL_DEPTH + 2 * RealStation.HALL_MARGIN - 1)
							+ " long or more; building plain platforms with a footbridge");
				} else if (!RealStation.islandsOnly(sized)) {
					warn(real.label() + " has island platforms only, two tracks each; building plain platforms with a footbridge");
				} else {
					builder.real(real);
					final int full = real.hallDepth(real.fullLength());
					if (depth < full) {
						warn("the platforms are too short for " + real.label() + "'s hall at its real " + full + " blocks; it is " + depth
								+ " deep (platforms " + (real.fullLength() - 1) + " long take all of it)");
					}
				}
			}
			builder.name(name);
			if (cityStationName != null && (real == null || real == RealStation.NONE) && looks.size() == 1 && !mixing) {
				// the station is itself only through its sheet: its name stays as typed (or empty), as the controls gave it
				builder.options(options.withSheet(cityStationName));
				note(looks.get(0).at(), "the real " + cityStationName + "'s front and train shed (say \"" + looks.get(0).word() + " style\" for its look on a station of your own)");
			}
			builder.stationClass(stationClass).era(era).facing(facing);
			if (seed == 0 && !seedText.isEmpty()) {
				note(Integer.MAX_VALUE, "seed " + seedText + ": no variation, the plain plan");
			}
			if (seed != 0) {
				builder.seed(seed);
				understood.add(new Note(Integer.MAX_VALUE, "seed " + seedText + (seeded.isEmpty() ? ": benches and platform colours"
						: ": " + String.join(", ", seeded) + ", benches and platform colours")));
			}
			return builder.build();
		}
	}
}
