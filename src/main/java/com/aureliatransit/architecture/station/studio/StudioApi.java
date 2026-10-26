package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.BlockFamilies;
import com.aureliatransit.architecture.station.BuildingStyle;
import com.aureliatransit.architecture.station.CanopyShape;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LayoutValidator;
import com.aureliatransit.architecture.station.LocalDir;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.PlanFingerprint;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.Facing;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationClass;
import com.aureliatransit.architecture.station.StationEra;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationLayout;
import com.aureliatransit.architecture.station.StationGeometry;
import com.aureliatransit.architecture.station.StationPlacement;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.RealStation;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.StationSeed;
import com.aureliatransit.architecture.station.Operator;
import com.aureliatransit.architecture.station.StationOptions;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.TrackLayout;
import com.aureliatransit.architecture.station.Violation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Station Studio's answers, as JSON text. {@link #plan} runs the same parser, planner, design rules and station checks as
 * {@code /ata_station plan}, from the studio's controls ({@link StationRecipe}) plus the typed description, and lists every
 * block with its real model so the page can draw the station before anything is built.
 */
public final class StudioApi {

	/** The studio's own limit for the typed words; the controls' words come on top. */
	public static final int MAX_TYPED = 1000;

	/** The frame the studio draws in: +x is +u, -z is +v (the game's frame for a player looking north). */
	public static final LocalFrame FRAME = new LocalFrame(0, 0, 0, Heading.NORTH);

	private final BlockModels models;

	public StudioApi(BlockModels models) {
		this.models = models;
	}

	/** The controls a request carries; missing or unreadable fields keep the defaults. */
	public static StationRecipe recipe(Map<String, String> q) {
		StationRecipe recipe = StationRecipe.defaults();
		recipe = recipe.withLook(look(q.get("look"), StationRecipe.Look.AUTO));
		recipe = recipe.withMix(look(q.get("mix"), null));
		recipe = recipe.withTerminus("terminus".equals(q.get("mode"))).withHub("hub".equals(q.get("mode")));
		recipe = recipe.withTracks(number(q.get("tracks"), recipe.tracks()));
		recipe = recipe.withPlatforms(value(StationRecipe.Platforms.class, q.get("layout"), StationRecipe.Platforms.AUTO));
		recipe = recipe.withLength(number(q.get("length"), recipe.length()));
		recipe = recipe.withRoof(value(StationRecipe.Roof.class, q.get("roof"), StationRecipe.Roof.AUTO));
		recipe = recipe.withBuilding(!"0".equals(q.get("building")));
		recipe = recipe.withSize(value(StationRecipe.Size.class, q.get("size"), StationRecipe.Size.AUTO));
		return recipe.withName(q.getOrDefault("name", ""));
	}

	/** The longest platform list a request may carry: ten platforms with every setting. */
	public static final int MAX_PLATFORM_WORDS = 600;

	/**
	 * The typed words of a request, cut to {@link #MAX_TYPED}, after the advanced platform list ("platform 1: island 12 wide;
	 * ..."), which comes first so a long description never cuts it off.
	 */
	public static String typed(Map<String, String> q) {
		String text = q.getOrDefault("text", "").replace('\n', ' ').strip();
		if (text.length() > MAX_TYPED) {
			text = text.substring(0, MAX_TYPED);
		}
		String platforms = q.getOrDefault("platforms", "").replaceAll("[^A-Za-z0-9 :;]", " ").strip();
		if (platforms.length() > MAX_PLATFORM_WORDS) {
			platforms = platforms.substring(0, platforms.lastIndexOf(';', MAX_PLATFORM_WORDS) + 1);
		}
		final String bed = q.getOrDefault("bed", "");
		if (bed.equals("gravel") || bed.equals("andesite")) {
			text = bed + " track bed " + text;
		}
		final String esc = q.getOrDefault("esc", "");
		if (esc.equals("1") || esc.equals("both")) {
			text = "escalators " + text;
		}
		if (esc.equals("reverse") || esc.equals("both")) {
			text = "stairs both ways " + text;
		}
		if (q.getOrDefault("walls", "").equals("1")) {
			text = "enclosed stairs " + text;
		}
		if (q.getOrDefault("trav", "").equals("1")) {
			text = "travelators " + text;
		}
		if (q.getOrDefault("gates", "").equals("ata")) {
			text = "ata gates " + text;
		}
		final int door = number(q.get("door"), 0);
		if (door >= StationOptions.MIN_ENTRANCE && door <= StationOptions.MAX_ENTRANCE) {
			text = "entrance " + door + " wide " + text;
		}
		text = text.strip() + swapWords(q.getOrDefault("swaps", ""));
		text = text.strip();
		return platforms.isEmpty() ? text : text.isEmpty() ? platforms : platforms + " " + text;
	}

	/** The most block swaps a request may carry ("from>to;from>to", block ids). */
	static final int MAX_SWAPS = 8;

	/** The page's block swaps ("minecraft:sandstone>minecraft:stone_bricks;...") as words: ", replace X with Y" each. */
	static String swapWords(String list) {
		final StringBuilder words = new StringBuilder();
		int n = 0;
		for (final String pair : list.split(";")) {
			final String[] ends = pair.split(">");
			if (ends.length != 2 || n >= MAX_SWAPS) {
				continue;
			}
			final String from = ends[0].strip().toLowerCase(Locale.ROOT);
			final String to = ends[1].strip().toLowerCase(Locale.ROOT);
			if (from.matches("[a-z0-9_]+:[a-z0-9_/.]+") && to.matches("[a-z0-9_]+:[a-z0-9_/.]+") && !from.equals(to)) {
				words.append(", replace ").append(from).append(" with ").append(to);
				n++;
			}
		}
		return words.toString();
	}

	/**
	 * The controls the page says the player changed ("tracks,length", the field names the studio tracks as touched). A typed
	 * description then only has to agree with those: every control left at its default is taken from the words instead.
	 */
	public static Set<String> pinned(Map<String, String> q) {
		final String list = q.getOrDefault("pinned", "").strip();
		if (list.isEmpty()) {
			return Set.of();
		}
		final Set<String> pinned = new HashSet<>();
		for (final String part : list.split(",")) {
			final String key = part.strip();
			if (!key.isEmpty()) {
				// the second look is said together with the first ("hamburg and utrecht combined"), so it pins the look
				pinned.add(key.equals("mix") ? "look" : key);
			}
		}
		// typed words win: a pinned control the words set themselves is left out, so it cannot contradict them
		pinned.removeAll(wordsWon(q));
		return pinned;
	}

	/** The controls a real station fixes itself: its look, through tracks, hall or shed, and island platforms. */
	static final List<String> REAL_FIXES = List.of("look", "mode", "building", "size", "roof", "layout");

	/** The controls a real station sets unless the player set them by hand: its own tracks and platform length. */
	static final List<String> REAL_ROLLED = List.of("tracks", "length");

	/** The pinned controls the typed words set themselves, and so overrule (sorted, for the page's note). */
	public static List<String> wordsWon(Map<String, String> q) {
		final String words = q.getOrDefault("text", "").strip();
		final String list = q.getOrDefault("pinned", "").strip();
		if (words.isEmpty() || list.isEmpty()) {
			return List.of();
		}
		final Set<String> asked = new HashSet<>();
		for (final String part : list.split(",")) {
			asked.add(part.strip().equals("mix") ? "look" : part.strip());
		}
		final List<String> won = new ArrayList<>();
		// words that name a real station ("utrecht station") set what it really is, so a pinned second city, hub, building, roof
		// or side platforms cannot turn it back into a station of the player's own (Jah 10-22; the dice pin some on a fresh page)
		if (StationDescriptionParser.parse(words).spec().real() != RealStation.NONE) {
			for (final String key : REAL_FIXES) {
				if (asked.contains(key)) {
					won.add(key);
				}
			}
			// its own track count and platform length beat what the dice rolled (a fresh page made Hamburg 1 to 4 tracks, 64
			// long, 10-24); a count or length set by hand still holds
			final Set<String> rolled = keys(q.getOrDefault("rolled", ""));
			for (final String key : REAL_ROLLED) {
				if (asked.contains(key) && rolled.contains(key)) {
					won.add(key);
				}
			}
		}
		final Set<String> said = StationDescriptionParser.controlsSaid(words);
		for (final String key : said) {
			if (won.contains(key)) {
				continue;
			}
			if (asked.contains(key)) {
				won.add(key);
			}
		}
		// words that name a city or a look ("amsterdam station") bring its own building and roof: what the dice rolled on the page
		// gives way to them (a rolled flat shed or "no building" made Amsterdam something else); a control set by hand still holds
		if (said.contains("look")) {
			final Set<String> rolled = keys(q.getOrDefault("rolled", ""));
			for (final String key : LOOK_FIXES) {
				if (asked.contains(key) && rolled.contains(key) && !won.contains(key)) {
					won.add(key);
				}
			}
		}
		won.sort(null);
		return won;
	}

	/**
	 * True when the class is one the dice rolled and the typed words name a city, a look or a real station: the city's own
	 * station then stands, as with its building and roof (a rolled grand class put "teddington station" under a train shed,
	 * 10-24). A class chosen by hand still asks for its kind of station.
	 */
	static boolean rolledClassGivesWay(Map<String, String> q) {
		final String words = q.getOrDefault("text", "").strip();
		if (words.isEmpty() || !keys(q.getOrDefault("rolled", "")).contains("class")) {
			return false;
		}
		return StationDescriptionParser.controlsSaid(words).contains("look") || StationDescriptionParser.parse(words).spec().real() != RealStation.NONE;
	}

	/** The controls a city or look named in the words brings with it, unless the player set them by hand. */
	static final List<String> LOOK_FIXES = List.of("look", "building", "roof");

	/** A page's comma-separated control names, the second look counted as the look. */
	static Set<String> keys(String list) {
		final Set<String> keys = new HashSet<>();
		for (final String part : list.split(",")) {
			final String key = part.strip();
			if (!key.isEmpty()) {
				keys.add(key.equals("mix") ? "look" : key);
			}
		}
		return keys;
	}

	/**
	 * The whole description a request plans and builds: the controls (only the pinned ones once there are typed words), the
	 * typed words, the station class, era and facing, then the dice's seed. The seed is not typed words: it must never make the
	 * controls give way to them. The studio's preview and its Plan and Build buttons all use this, so the game builds the station the
	 * page showed.
	 *
	 * <p>With a class or an era chosen, the pinned controls alone go in (even when there are no typed words), so the class fills in
	 * every control the player has not touched; a touched control and the typed words still win. The class and era are written as
	 * "halt class" and "postwar era", which the parser reads without the older meaning of the bare words, and the facing as "facing north".
	 */
	public static String description(Map<String, String> q) {
		final String airport = airportWords(q);
		if (airport != null) {
			return airport;
		}
		final StationClass stationClass = rolledClassGivesWay(q) ? StationClass.AUTO : StationClass.of(q.get("class"));
		final StationEra era = StationEra.of(q.get("era"));
		final Facing facing = Facing.of(q.get("facing"));
		String text;
		if (stationClass == StationClass.AUTO && era == StationEra.AUTO) {
			text = recipe(q).text(typed(q), pinned(q));
		} else {
			final String head = recipe(q).description(pinned(q));
			final String typed = typed(q);
			text = typed.isEmpty() ? head : head.isEmpty() ? typed : head + ", " + typed;
		}
		if (stationClass != StationClass.AUTO) {
			text = (text.isEmpty() ? "" : text + ", ") + stationClass.key() + " class";
		}
		if (era != StationEra.AUTO) {
			text = (text.isEmpty() ? "" : text + ", ") + era.key() + " era";
		}
		if (facing != Facing.AUTO) {
			text = (text.isEmpty() ? "" : text + ", ") + "facing " + facing.key();
		}
		final Operator operator = Operator.of(q.getOrDefault("operator", ""));
		if (operator != null && operator != Operator.NONE) {
			text = (text.isEmpty() ? "" : text + ", ") + operator.key() + " operator";
		}
		final String seed = q.getOrDefault("seed", "").strip().toLowerCase(Locale.ROOT);
		return StationSeed.valid(seed) ? text + ", seed " + seed : text;
	}

	/**
	 * The words of an airport request, or null for a railway station. An airport is described by the typed words alone: the
	 * station controls (tracks, look, roof, bed, stairs) say nothing about it and would only show up as ignored words. The facing,
	 * the seed and the block swaps still apply.
	 */
	static String airportWords(Map<String, String> q) {
		String text = q.getOrDefault("text", "").replace('\n', ' ').strip();
		if (text.length() > MAX_TYPED) {
			text = text.substring(0, MAX_TYPED);
		}
		// the airport generator page always plans an airport, whatever its words say about the rest
		final boolean page = "airport".equals(q.get("page"));
		if (page && !StationDescriptionParser.parse(text).spec().isAirport()) {
			text = text.isEmpty() ? "airport" : "airport, " + text;
		} else if (text.isEmpty() || !StationDescriptionParser.parse(text).spec().isAirport()) {
			return null;
		}
		text = text + swapWords(q.getOrDefault("swaps", ""));
		final Facing facing = Facing.of(q.get("facing"));
		if (facing != Facing.AUTO) {
			text += ", facing " + facing.key();
		}
		final String seed = q.getOrDefault("seed", "").strip().toLowerCase(Locale.ROOT);
		return StationSeed.valid(seed) ? text + ", seed " + seed : text;
	}

	/** The airport cards the page offers beside the station presets: key, label and the words they put in the box. */
	static final List<String[]> AIRPORTS = List.of(
			new String[]{"regional", "Regional airport", "airport, one 1700 m runway, 4 gates, terminal, named Regional Airport"},
			new String[]{"small", "Small airport", "small airport named Small Airfield"},
			new String[]{"twin", "Two runways", "airport, 2 runways, 6 gates, named Twin Runway Airport"},
			new String[]{"remote", "Airstrip", "airport, 1 runway, 2 gates, no terminal, named Remote Airstrip"});

	/**
	 * What to tell the player about a seed field that is not a seed: it is left out of the description, so the parser never sees
	 * it. Null for no seed or a good one.
	 */
	static String seedNote(Map<String, String> q) {
		final String seed = q.getOrDefault("seed", "").strip();
		if (seed.isEmpty() || StationSeed.valid(seed)) {
			return null;
		}
		final String shown = seed.length() > 20 ? seed.substring(0, 20) + "..." : seed;
		return "seed \"" + shown.replaceAll("\\p{Cntrl}", " ") + "\" ignored: a seed is 1 to " + StationSeed.MAX_CHARS
				+ " letters or digits (a-z, 0-9)";
	}

	/** The most block kinds the page lists to swap. */
	static final int MAX_MATERIALS = 48;

	/** The plan's blocks by how many it has, most first, for the page's block swapper: [{"id","count"}]. */
	static String materials(StationPlan plan) {
		final Map<String, Integer> counts = new java.util.HashMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			if (!block.blockId().equals("minecraft:air") && block.role() != Role.RAIL_NODE) {
				counts.merge(block.blockId(), 1, Integer::sum);
			}
		}
		final List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
		sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
		final StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < Math.min(MAX_MATERIALS, sorted.size()); i++) {
			out.append(i == 0 ? "" : ",").append("{\"id\":").append(Json.quote(sorted.get(i).getKey())).append(",\"count\":").append(sorted.get(i).getValue()).append('}');
		}
		return out.append(']').toString();
	}

	/** The choices the page offers and their limits. */
	public String options() {
		final StringBuilder out = new StringBuilder("{");
		out.append("\"looks\":").append(labels(StationRecipe.Look.values()));
		out.append(",\"layouts\":").append(labels(StationRecipe.Platforms.values()));
		out.append(",\"roofs\":").append(labels(StationRecipe.Roof.values()));
		out.append(",\"sizes\":").append(labels(StationRecipe.Size.values()));
		out.append(",\"tracks\":[").append(StationRecipe.MIN_TRACKS).append(',').append(StationRecipe.MAX_TRACKS).append(']');
		out.append(",\"length\":[").append(StationRecipe.MIN_LENGTH).append(',').append(StationRecipe.MAX_LENGTH).append(',')
				.append(StationRecipe.DEFAULT_LENGTH).append(']');
		out.append(",\"classes\":").append(keys(StationClass.values(), StationClass::key, StationClass::label));
		out.append(",\"eras\":").append(keys(StationEra.values(), StationEra::key, StationEra::label));
		out.append(",\"facings\":").append(keys(Facing.values(), Facing::key, f -> f == Facing.AUTO ? "Auto" : f.key().substring(0, 1).toUpperCase(Locale.ROOT) + f.key().substring(1)));
		out.append(",\"blocks\":").append(Json.strings(BlockFamilies.suggestions()));
		out.append(",\"maxTyped\":").append(MAX_TYPED).append(",\"maxName\":").append(StationSpec.MAX_NAME);
		out.append(",\"airports\":[");
		for (int i = 0; i < AIRPORTS.size(); i++) {
			final String[] a = AIRPORTS.get(i);
			out.append(i == 0 ? "" : ",").append("{\"key\":").append(Json.quote(a[0])).append(",\"label\":").append(Json.quote(a[1])).append(",\"text\":")
					.append(Json.quote(a[2])).append('}');
		}
		out.append("],\"presets\":[");
		for (int i = 0; i < StationPresets.ALL.size(); i++) {
			final StationPresets.Preset preset = StationPresets.ALL.get(i);
			out.append(i == 0 ? "" : ",").append(preset(preset));
		}
		return out.append("]}").toString();
	}

	/**
	 * A preset as the page's controls: its tracks, length, type, platforms, city and name set the buttons, and the rest of its
	 * words (the roof, the footbridge) go in the description. The controls are always sent, so without this they would win
	 * over the preset's own numbers. A real station takes no look: it is built as it is.
	 */
	static String preset(StationPresets.Preset preset) {
		final StationSpec spec = StationDescriptionParser.parse(preset.description()).spec();
		final String words = preset.description().replaceFirst(",?\\s*named .*$", "");
		return "{\"key\":" + Json.quote(preset.key()) + ",\"description\":" + Json.quote(preset.description()) + ",\"text\":" + Json.quote(words)
				+ ",\"tracks\":" + spec.trackCount() + ",\"length\":" + spec.platformLength() + ",\"mode\":" + Json.quote(spec.hub() ? "hub" : spec.terminus() ? "terminus" : "through")
				+ ",\"layout\":" + Json.quote(spec.platformLayout().name()) + ",\"look\":" + Json.quote((spec.real() == RealStation.NONE ? look(preset.building()) : StationRecipe.Look.AUTO).name())
				+ ",\"name\":" + Json.quote(spec.name()) + "}";
	}

	/** A description read from a photo, as a preset the page applies the same way. */
	static String photoPreset(String description) {
		final StationSpec spec = StationDescriptionParser.parse(description).spec();
		return preset(new StationPresets.Preset("photo", spec.building(), description));
	}

	/** The wrench's city button for a building look. */
	static StationRecipe.Look look(BuildingStyle building) {
		return switch (building) {
			case AMSTERDAM -> StationRecipe.Look.AMSTERDAM;
			case GOTHIC_BRICK -> StationRecipe.Look.ST_PANCRAS;
			case ANTWERP -> StationRecipe.Look.ANTWERP;
			case SANDSTONE -> StationRecipe.Look.FRANKFURT;
			case CLINKER -> StationRecipe.Look.HAMBURG;
			case CONCRETE -> StationRecipe.Look.MUNICH;
			case BEAUX_ARTS -> StationRecipe.Look.NEW_YORK;
			case TOKYO -> StationRecipe.Look.TOKYO;
			case ZURICH -> StationRecipe.Look.ZURICH;
			case PARIS -> StationRecipe.Look.PARIS;
			case MILAN -> StationRecipe.Look.MILAN;
			case BERLIN -> StationRecipe.Look.BERLIN;
			case HONG_KONG -> StationRecipe.Look.HONG_KONG;
			case BEIJING -> StationRecipe.Look.BEIJING;
			case BANGKOK -> StationRecipe.Look.BANGKOK;
			case KUALA_LUMPUR -> StationRecipe.Look.KUALA_LUMPUR;
			case MUMBAI -> StationRecipe.Look.MUMBAI;
			case VICTORIAN -> StationRecipe.Look.TEDDINGTON;
			case ART_DECO -> StationRecipe.Look.SURBITON;
			case MODERNIST -> StationRecipe.Look.COVENTRY;
			case MODERN -> StationRecipe.Look.UTRECHT;
			case NONE -> StationRecipe.Look.AUTO;
		};
	}

	/** One request planned, validated and audited: what {@link #plan} describes and {@link #export} writes. */
	private record Planned(StationRecipe recipe, String typed, String text, ParseResult parse, StationPlan plan, List<Violation> violations,
			StationAudit.Result audit, long started, long planned) {

		boolean buildable() {
			return violations.isEmpty() && audit.buildable();
		}
	}

	/** The city button whose words build {@code building} (Hamburg's brick hall is the CLINKER building), or null. */
	static StationRecipe.Look lookOf(BuildingStyle building) {
		if (building == BuildingStyle.NONE) {
			return null;
		}
		for (final StationRecipe.Look look : StationRecipe.Look.values()) {
			if (look != StationRecipe.Look.AUTO && StationDescriptionParser.parse(look.words()).spec().building() == building) {
				return look;
			}
		}
		return null;
	}

	/** The city button of a real station: its own city, not the first button that shares its building (Utrecht Centraal lit Tokyo). */
	static StationRecipe.Look lookOf(RealStation real) {
		final String city = real.phrase().split(" ")[0];
		for (final StationRecipe.Look look : StationRecipe.Look.values()) {
			if (look.words().equals(city)) {
				return look;
			}
		}
		return null;
	}

	/** The headline of an airport: its runways and gates. */
	static String airportTitle(com.aureliatransit.architecture.station.AirportSpec a) {
		return "Airport, " + (a.runways() == 1 ? "1 runway " : a.runways() + " runways ") + a.runwayLength() + " long, " + a.gates() + (a.gates() == 1 ? " gate" : " gates")
				+ (a.terminal() ? "" : ", no terminal");
	}

	/**
	 * What the page shows of an airport besides its blocks: the settings it was planned with, as {@code "airport"} in {@code built}
	 * (null for a railway station).
	 */
	static String airportJson(StationSpec spec) {
		if (!spec.isAirport()) {
			return "null";
		}
		final com.aureliatransit.architecture.station.AirportSpec a = spec.airport();
		final com.aureliatransit.architecture.station.AirportGeometry g = com.aureliatransit.architecture.station.AirportGeometry.of(a);
		return "{\"runways\":" + a.runways() + ",\"length\":" + a.runwayLength() + ",\"width\":" + a.runwayWidth() + ",\"gates\":" + a.gates()
				+ ",\"terminal\":" + a.terminal() + ",\"metres\":" + Math.round(a.runwayLength() / com.aureliatransit.architecture.station.AirportSpec.BLOCKS_PER_METRE)
				+ ",\"real\":" + (g.dims() == com.aureliatransit.architecture.station.AirportGeometry.REAL) + "}";
	}

	/** The headline of a station the typed words describe: what they came to, not what the settings above them say. */
	static String wordsTitle(StationSpec spec) {
		if (spec.real() != RealStation.NONE) {
			// a real station is itself, not the look its building stone shares (Hamburg Hbf is not "Frankfurt station")
			return spec.real().label() + ", " + spec.trackCount() + " tracks, " + spec.platformLength() + " long";
		}
		final StationRecipe.Look look = lookOf(spec.building());
		final String label = look != null ? look.label() : spec.building() == BuildingStyle.NONE ? "Custom" : spec.building().label();
		final String name = Character.toUpperCase(label.charAt(0)) + label.substring(1);
		final int tracks = spec.trackCount();
		final String size = spec.listed() ? spec.platforms().size() + (spec.platforms().size() == 1 ? " platform, " : " platforms, ") : "";
		return name + (spec.hub() ? " hub" : spec.terminus() ? " terminus" : spec.tram() ? " tram stop" : " station") + ", " + size + tracks + (tracks == 1 ? " track, " : " tracks, ")
				+ spec.platformLength() + " long";
	}

	private static Planned planned(Map<String, String> q) {
		final StationRecipe recipe = recipe(q);
		final String typed = typed(q);
		final String text = description(q);
		final long started = System.nanoTime();
		final ParseResult parse = StationDescriptionParser.parse(text);
		final StationPlan plan = StationPlanner.plan(parse.spec());
		final List<Violation> violations = LayoutValidator.validate(plan);
		final StationAudit.Result audit = StationAudit.audit(plan);
		return new Planned(recipe, typed, text, parse, plan, violations, audit, started, System.nanoTime());
	}

	/** Plans the request's station and describes it for the page. */
	public String plan(Map<String, String> q) {
		final Planned done = planned(q);
		final StationRecipe recipe = done.recipe();
		final String typed = done.typed();
		final String text = done.text();
		final long started = done.started();
		final ParseResult parse = done.parse();
		final StationPlan plan = done.plan();
		final List<Violation> violations = done.violations();
		final StationAudit.Result audit = done.audit();
		final long planned = done.planned();
		final StationSpec spec = parse.spec();
		final TrackLayout layout = plan.layout();
		final LocalFrame frame = StationPlacement.frame(spec);

		final Map<Long, String> idAt = new java.util.HashMap<>();
		final Map<Long, PlannedBlock> at = new java.util.HashMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			idAt.put(cell(frame.x(block.u(), block.v()), block.y(), frame.z(block.u(), block.v())), block.blockId());
			at.put(cell(frame.x(block.u(), block.v()), block.y(), frame.z(block.u(), block.v())), block);
		}
		final Connections.FullFace full = Connections.withMtrShapes((other, heading, side) -> models.fullFace(other.blockId(), heading, other.state(), side), at, frame);
		final Map<String, Integer> modelIndex = new LinkedHashMap<>();
		final List<String> modelJson = new ArrayList<>();
		final StringBuilder cells = new StringBuilder();
		final StringBuilder moving = new StringBuilder();
		for (final PlannedBlock block : plan.blocks()) {
			if (block.blockId().equals(com.aureliatransit.architecture.station.StylePalette.MTR_ESCALATOR_STEP)) {
				// MTR's step model is the same both ways along its axis, so the page is told which way each step moves
				final int du = block.front() == LocalDir.PLUS_U ? 1 : block.front() == LocalDir.MINUS_U ? -1 : 0;
				final int dv = block.front() == LocalDir.PLUS_V ? 1 : block.front() == LocalDir.MINUS_V ? -1 : 0;
				final int x = frame.x(block.u(), block.v());
				final int z = frame.z(block.u(), block.v());
				moving.append(moving.length() == 0 ? "" : ",").append(x).append(',').append(block.y()).append(',').append(z).append(',')
						.append(frame.x(block.u() + du, block.v() + dv) - x).append(',').append(frame.z(block.u() + du, block.v() + dv) - z);
			}
			final Heading heading = frame.heading(block.front());
			final String state = Connections.state(block, at, full, frame);
			final String key = BlockModels.key(block.blockId(), heading, state);
			final int model = modelIndex.computeIfAbsent(key, k -> {
				modelJson.add("{\"id\":" + Json.quote(block.blockId()) + ",\"state\":" + Json.quote(state) + ",\"model\":" + models.describe(block.blockId(), heading, state) + "}");
				return modelJson.size() - 1;
			});
			if (cells.length() > 0) {
				cells.append(',');
			}
			cells.append(frame.x(block.u(), block.v())).append(',').append(block.y()).append(',').append(frame.z(block.u(), block.v())).append(',')
					.append(model).append(',').append(block.role().ordinal());
		}

		final StringBuilder out = new StringBuilder("{");
		out.append("\"text\":").append(Json.quote(text));
		// a platform list sets the tracks (and may lengthen the station), so the title says what it built
		final String title = spec.isAirport() ? airportTitle(spec.airport()) : !typed.isEmpty() ? wordsTitle(spec) : !spec.listed() ? recipe.title() : recipe.title().replaceFirst(", \\d+ tracks?, \\d+ long$", ", "
				+ spec.platforms().size() + (spec.platforms().size() == 1 ? " platform, " : " platforms, ") + spec.trackCount()
				+ (spec.trackCount() == 1 ? " track, " : " tracks, ") + spec.platformLength() + " long");
		out.append(",\"title\":").append(Json.quote(title));
		// the city button the words came to, so the page's buttons follow what was typed; only when the words name a look or a
		// real station: the page then clears its second look, and words naming none planned with the page's own look and mix
		// (10-25: "2 track station" under a rolled Teddington + Zurich showed that mix, then the next change planned without it)
		final StationRecipe.Look wordsLook = typed.isEmpty() ? null : spec.real() != RealStation.NONE ? lookOf(spec.real())
				: StationDescriptionParser.controlsSaid(typed).contains("look") ? lookOf(spec.building()) : null;
		out.append(",\"wordsLook\":").append(Json.quote(wordsLook == null ? "" : wordsLook.name()));
		out.append(",\"seed\":").append(Json.quote(StationSeed.format(spec.seed())));
		out.append(",\"fingerprint\":").append(Json.quote(PlanFingerprint.of(plan)));
		// the roof and building size this station was planned with, as the page's options, so a locked "Auto" can keep them
		out.append(",\"resolved\":{\"roof\":").append(Json.quote(resolvedRoof(spec).name())).append(",\"size\":")
				.append(Json.quote(spec.hasBuilding() ? resolvedSize(spec).name() : "AUTO")).append(",\"class\":")
				.append(Json.quote(StationClass.of(spec).key())).append(",\"era\":").append(Json.quote(StationEra.of(spec).key())).append('}');
		out.append(",\"facing\":").append(Json.quote(spec.facing().key()));
		out.append(",\"encoded\":").append(Json.quote(recipe.encode()));
		out.append(",\"typed\":").append(Json.quote(typed));
		out.append(",\"understood\":").append(Json.strings(parse.understood()));
		out.append(",\"defaulted\":").append(Json.strings(parse.defaulted()));
		final List<String> warnings = new ArrayList<>(parse.warnings());
		final String seedNote = seedNote(q);
		if (seedNote != null) {
			warnings.add(seedNote);
		}
		for (final String to : new java.util.LinkedHashSet<>(spec.options().swaps().values())) {
			if (!models.known(to)) {
				warnings.add(to + " is not a block this game has, so it would not build; pick another to swap in");
			}
		}
		out.append(",\"warnings\":").append(Json.strings(warnings));
		out.append(",\"materials\":").append(materials(plan));
		out.append(",\"swaps\":[");
		int swapIndex = 0;
		for (final Map.Entry<String, String> swap : spec.options().swaps().entrySet()) {
			out.append(swapIndex++ == 0 ? "" : ",").append("{\"from\":").append(Json.quote(swap.getKey())).append(",\"to\":")
					.append(Json.quote(swap.getValue())).append(",\"known\":").append(models.known(swap.getValue())).append('}');
		}
		out.append(']');
		out.append(",\"gates\":").append(Json.quote(spec.options().gates().name().toLowerCase(Locale.ROOT)));
		out.append(",\"ignored\":").append(Json.strings(parse.ignored()));
		out.append(",\"wordsWon\":").append(Json.strings(wordsWon(q)));
		out.append(",\"requested\":{\"mode\":").append(Json.quote(recipe.hub() ? "Hub" : recipe.terminus() ? "Terminus" : "Through station"))
				.append(",\"layout\":").append(Json.quote(recipe.platforms().label())).append(",\"tracks\":").append(recipe.tracks())
				.append(",\"length\":").append(recipe.length()).append(",\"look\":").append(Json.quote(recipe.look().label()))
				.append(",\"roof\":").append(Json.quote(recipe.roof().label())).append(",\"building\":").append(recipe.building())
				.append(",\"size\":").append(Json.quote(recipe.size().label())).append('}');
		out.append(",\"built\":{\"mode\":").append(Json.quote(spec.isAirport() ? "Airport" : spec.hub() ? "Hub" : spec.terminus() ? "Terminus" : "Through station"))
				.append(",\"layout\":").append(Json.quote(lower(spec.platformLayout().name()) + " platforms"))
				.append(",\"tracks\":").append(spec.trackCount()).append(",\"platforms\":").append(layout.platforms().size())
				.append(",\"faces\":").append(audit.faces().size()).append(",\"length\":").append(spec.platformLength())
				.append(",\"platformWidth\":").append(spec.platformWidth()).append(",\"style\":").append(Json.quote(lower(spec.style().name())))
				.append(",\"building\":").append(Json.quote(spec.building().label()))
				.append(",\"scale\":").append(Json.quote(spec.hasBuilding() ? spec.buildingScale().label() : "-"))
				.append(",\"roof\":").append(Json.quote(roof(spec))).append(",\"footbridge\":").append(StationLayout.of(spec, layout).bridge())
				.append(",\"airport\":").append(airportJson(spec)).append(",\"underground\":").append(spec.options().underground()).append(",\"streetY\":").append(StationGeometry.UPPER_FLOOR_Y)
				.append(",\"name\":").append(Json.quote(spec.name())).append('}');
		out.append(",\"size\":{\"width\":").append(audit.width()).append(",\"length\":").append(audit.length()).append(",\"height\":")
				.append(audit.height()).append(",\"blocks\":").append(plan.blocks().size()).append('}');
		out.append(",\"buildable\":").append(violations.isEmpty() && audit.buildable());
		out.append(",\"checks\":[");
		for (int i = 0; i < audit.checks().size(); i++) {
			final StationAudit.Check c = audit.checks().get(i);
			out.append(i == 0 ? "" : ",").append("{\"topic\":").append(Json.quote(lower(c.topic().name()))).append(",\"status\":")
					.append(Json.quote(c.status().name())).append(",\"message\":").append(Json.quote(c.message())).append(",\"title\":")
					.append(Json.quote(c.title())).append(",\"fix\":").append(Json.quote(c.fix())).append('}');
		}
		out.append("],\"summary\":{\"pass\":").append(audit.count(StationAudit.Status.PASS)).append(",\"warn\":").append(audit.count(StationAudit.Status.WARN))
				.append(",\"fail\":").append(audit.count(StationAudit.Status.FAIL)).append('}');
		out.append(",\"violations\":[");
		for (int i = 0; i < violations.size(); i++) {
			final Violation v = violations.get(i);
			out.append(i == 0 ? "" : ",").append("{\"kind\":").append(Json.quote(v.kind().name())).append(",\"x\":").append(frame.x(v.u(), v.v()))
					.append(",\"y\":").append(v.y()).append(",\"z\":").append(frame.z(v.u(), v.v())).append(",\"message\":")
					.append(Json.quote(v.message())).append('}');
		}
		out.append("],\"faceLabels\":[");
		for (int i = 0; i < audit.faces().size(); i++) {
			final StationAudit.Face face = audit.faces().get(i);
			final TrackLayout.Track track = layout.tracks().get(face.track() - 1 < 0 ? 0 : Math.min(face.track() - 1, layout.tracks().size() - 1));
			final int u = track.u() + (face.leftOfTrack() ? -2 : 2);
			final int v = face.vStart() + face.length() / 2;
			out.append(i == 0 ? "" : ",").append("{\"n\":").append(i + 1).append(",\"platform\":").append(face.platform()).append(",\"x\":")
					.append(frame.x(u, v)).append(",\"z\":").append(frame.z(u, v)).append(",\"view\":").append(faceView(frame, face, track, idAt))
					.append('}');
		}
		out.append("],\"passenger\":").append(spec.isAirport() ? airportPassenger(frame, spec) : passenger(frame, layout, idAt));
		out.append(",\"street\":").append(spec.isAirport() ? airportStreet(frame, spec) : street(frame, spec, layout, idAt));
		out.append(",\"head\":").append(headView(frame, layout));
		out.append(",\"views\":").append(spec.isAirport() ? airportViews(frame, spec) : "null");
		out.append(",\"roles\":").append(Json.strings(names(Role.values())));
		out.append(",\"models\":[").append(String.join(",", modelJson)).append(']');
		out.append(",\"cells\":[").append(cells).append(']');
		out.append(",\"moving\":[").append(moving).append(']');
		out.append(",\"ms\":").append((planned - started) / 1_000_000);
		return out.append('}').toString();
	}

	/** A station as a schematic file: the bytes, the file name to offer and the numbers the headers carry. */
	public record Export(byte[] bytes, String filename, int blocks, String fingerprint) {
	}

	/**
	 * The request's station as a Sponge schematic ({@link SchematicExport}), planned exactly as {@link #plan} does. Throws
	 * {@link IllegalArgumentException} (the reason is for the player) when the station cannot be built.
	 */
	public Export export(Map<String, String> q) {
		final Planned done = planned(q);
		if (!done.buildable()) {
			final StringBuilder why = new StringBuilder("This station cannot be built, so there is nothing to export");
			for (final Violation v : done.violations()) {
				why.append("; ").append(v.message());
				break;
			}
			for (final StationAudit.Check check : done.audit().checks()) {
				if (check.status() == StationAudit.Status.FAIL) {
					why.append("; ").append(check.message());
					break;
				}
			}
			throw new IllegalArgumentException(why.toString());
		}
		final StationSpec spec = done.parse().spec();
		final StationPlan plan = done.plan();
		final Map<String, String> info = new LinkedHashMap<>();
		info.put("Generator", "Station Studio");
		info.put("Version", StudioServer.VERSION);
		info.put("Seed", StationSeed.format(spec.seed()));
		info.put("Facing", spec.facing().key());
		info.put("Class", StationClass.of(spec).key());
		info.put("Era", StationEra.of(spec).key());
		info.put("Blocks", Integer.toString(plan.blocks().size()));
		info.put("Origin", "on the ground in front of the street entrance");
		info.put("Description", done.text());
		final SchematicExport.Result result = SchematicExport.export(plan, StationPlacement.frame(spec), models, StationPlacement.pasteOrigin(spec, plan), spec.name(), info);
		final String seed = spec.seed() == 0 ? "noseed" : StationSeed.format(spec.seed());
		return new Export(result.bytes(), slug(spec.name()) + "-" + seed + ".schem", result.blocks(), result.fingerprint());
	}

	/** A file-name-safe lower case slug of {@code name}: letters, digits and single hyphens, at most 40 characters; "station" when empty. */
	static String slug(String name) {
		String slug = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (slug.length() > 40) {
			slug = slug.substring(0, 40).replaceAll("-+$", "");
		}
		return slug.isEmpty() ? "station" : slug;
	}

	/** {@code block}'s state with the joins it gets in the game ({@link Connections}); {@code at} maps {@link #cell} keys to blocks. */
	public static String withConnections(PlannedBlock block, Map<Long, PlannedBlock> at, Connections.FullFace full) {
		return Connections.state(block, at, full);
	}

	public static long cell(int x, int y, int z) {
		return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
	}

	/**
	 * Where to look at a terminus's head building from the street: the middle of its front ({@code x}, {@code z}), the way
	 * the front faces ({@code dx}, {@code dz}) and its width; null for a through station.
	 */
	private static String headView(LocalFrame frame, TrackLayout layout) {
		if (!layout.terminus()) {
			return "null";
		}
		final int u = (layout.headFrom() + layout.headTo()) / 2;
		final int v = layout.headEnd();
		final int x = frame.x(u, v);
		final int z = frame.z(u, v);
		return "{\"x\":" + x + ",\"z\":" + z + ",\"dx\":" + (frame.x(u, v + 1) - x) + ",\"dz\":" + (frame.z(u, v + 1) - z)
				+ ",\"width\":" + (layout.headTo() - layout.headFrom() + 1) + "}";
	}

	/**
	 * Where a walk from the street starts: on the ground in front of the station's street entrance (a terminus's head front, or
	 * the side building's entrance), on the first spot with three clear blocks, looking at the door; null without a building.
	 */
	private static String street(LocalFrame frame, StationSpec spec, TrackLayout layout, Map<Long, String> idAt) {
		final int u;
		final int du;
		final int v;
		final int dv;
		if (layout.terminus()) {
			u = (layout.headFrom() + layout.headTo()) / 2;
			v = layout.headEnd() + 1;
			du = 0;
			dv = 1;
		} else if (spec.hasBuilding() && layout.buildingWidth() > 0) {
			u = StationLayout.streetWall() - 1;
			v = StationLayout.of(spec, layout).entranceV();
			du = -1;
			dv = 0;
		} else if (spec.real().hallOverTracks()) {
			// Utrecht Centraal: the broad stairs up from the square to its hall, at the street end of the hall's walk
			u = 0;
			v = StationLayout.of(spec, layout).bridgeV();
			du = -1;
			dv = 0;
		} else {
			return "null";
		}
		for (int step = 0; step < 24; step++) {
			final int x = frame.x(u + du * step, v + dv * step);
			final int z = frame.z(u + du * step, v + dv * step);
			if (!idAt.containsKey(cell(x, 0, z)) && !idAt.containsKey(cell(x, 1, z)) && !idAt.containsKey(cell(x, 2, z))) {
				// a few steps further out, so the whole entrance is in view
				final int out = step + 4;
				return "{\"x\":" + frame.x(u + du * out, v + dv * out) + ",\"z\":" + frame.z(u + du * out, v + dv * out) + ",\"tx\":"
						+ frame.x(u, v) + ",\"tz\":" + frame.z(u, v) + "}";
			}
		}
		return "null";
	}

	private static String eye(LocalFrame frame, int u, double y, int v, int tu, double ty, int tv) {
		return "{\"x\":" + frame.x(u, v) + ",\"y\":" + y + ",\"z\":" + frame.z(u, v) + ",\"tx\":" + frame.x(tu, tv) + ",\"ty\":" + ty + ",\"tz\":" + frame.z(tu, tv) + "}";
	}

	/**
	 * The close views of an airport, from a point to a point (cell x, z and height y): the terminal's street front from the road, the gates
	 * and boarding bridges from the apron, and the first runway's threshold with its numbers and bars from behind its end. The terminal
	 * and gate views are left out without a terminal.
	 */
	static String airportViews(LocalFrame frame, StationSpec spec) {
		final com.aureliatransit.architecture.station.AirportGeometry g = com.aureliatransit.architecture.station.AirportGeometry.of(spec.airport());
		final com.aureliatransit.architecture.station.AirportGeometry.Dims d = g.dims();
		final StringBuilder out = new StringBuilder("{");
		if (spec.airport().terminal()) {
			final int door = (g.terminalV0() + g.terminalV1()) / 2;
			final int back = d.terminalDepth();
			out.append("\"terminal\":").append(eye(frame, g.terminalU0() - back - 6, back * 0.55, door + back / 2, g.terminalU0(), d.wallTop() * 0.4, door));
			final int stand = g.standV().get(0);
			out.append(",\"apron\":").append(eye(frame, g.apronU1() - 4, d.wallTop() * 1.6, stand + d.standPitch() / 3, g.terminalU1(), 3, stand));
			out.append(',');
		}
		final int c = g.runwayCentre(0);
		final int behind = Math.min(30, d.end() - 4);
		out.append("\"threshold\":").append(eye(frame, c, 14, g.runwayV0() - behind, c, 0, g.runwayV0() + d.numberOffset() + 10));
		return out.append('}').toString();
	}

	/** On the apron beside the first stand, looking across the taxiway at the runway. */
	private static String airportPassenger(LocalFrame frame, StationSpec spec) {
		final com.aureliatransit.architecture.station.AirportGeometry g = com.aureliatransit.architecture.station.AirportGeometry.of(spec.airport());
		final int u = g.apronU1() - 12;
		final int v = g.standV().get(0);
		final int ahead = g.runwayCentre(0);
		return "{\"x\":" + frame.x(u, v) + ",\"z\":" + frame.z(u, v) + ",\"tx\":" + frame.x(ahead, v + 40) + ",\"tz\":" + frame.z(ahead, v + 40) + "}";
	}

	/** On the road in front of the terminal, looking at its entrance; null without a terminal. */
	private static String airportStreet(LocalFrame frame, StationSpec spec) {
		if (!spec.airport().terminal()) {
			return "null";
		}
		final com.aureliatransit.architecture.station.AirportGeometry g = com.aureliatransit.architecture.station.AirportGeometry.of(spec.airport());
		final int v = (g.terminalV0() + g.terminalV1()) / 2;
		final int out = Math.max(0, g.roadU0());
		return "{\"x\":" + frame.x(out, v) + ",\"z\":" + frame.z(out, v) + ",\"tx\":" + frame.x(g.terminalU0(), v) + ",\"tz\":" + frame.z(g.terminalU0(), v) + "}";
	}

	/**
	 * Where a passenger stands for the Passenger view: on the first platform near its start, in a cell with three clear blocks
	 * above the floor (not in a column or a stair), looking along the platform.
	 */
	private static String passenger(LocalFrame frame, TrackLayout layout, Map<Long, String> idAt) {
		if (layout.platforms().isEmpty()) {
			return "null";
		}
		final TrackLayout.PlatformBand band = layout.platforms().get(0);
		final int middle = (band.uMin() + band.uMax()) / 2;
		for (int v = 3; v < layout.platformLength() / 2; v++) {
			for (int du = 0; du <= band.width(); du++) {
				final int u = middle + (du % 2 == 0 ? du / 2 : -(du / 2 + 1));
				if (u < band.uMin() || u > band.uMax()) {
					continue;
				}
				final int x = frame.x(u, v);
				final int z = frame.z(u, v);
				if (!idAt.containsKey(cell(x, 1, z)) && !idAt.containsKey(cell(x, 2, z)) && !idAt.containsKey(cell(x, 3, z))) {
					return "{\"x\":" + x + ",\"z\":" + z + ",\"tx\":" + frame.x(u, layout.platformLength()) + ",\"tz\":"
							+ frame.z(u, layout.platformLength()) + "}";
				}
			}
		}
		return "null";
	}

	/**
	 * Where a passenger stands to look along one boarding face: on its platform a block or two back from the edge, near the
	 * face's start, in a cell with three clear blocks above the floor and nothing at eye height just ahead (so not behind a
	 * column), looking at the track some way ahead.
	 */
	private static String faceView(LocalFrame frame, StationAudit.Face face, TrackLayout.Track track, Map<Long, String> idAt) {
		final int inward = face.leftOfTrack() ? -1 : 1;
		final int edge = track.u() + inward * StationGeometry.EDGE_OFFSET;
		final int last = Math.max(face.vStart(), face.vEnd() - 8);
		for (int v = Math.min(face.vStart() + 3, last); v <= last; v++) {
			for (final int back : new int[]{1, 2, 0, 3}) {
				final int u = edge + inward * back;
				final int x = frame.x(u, v);
				final int z = frame.z(u, v);
				final int y = StationGeometry.WALK_Y;
				if (idAt.containsKey(cell(x, y - 1, z)) && !idAt.containsKey(cell(x, y, z)) && !idAt.containsKey(cell(x, y + 2, z))
						&& clearAhead(frame, u, v, y + 1, Math.min(v + 8, face.vEnd()), idAt)) {
					final int ahead = Math.min(v + 20, face.vEnd());
					return "{\"x\":" + x + ",\"z\":" + z + ",\"tx\":" + frame.x(track.u(), ahead) + ",\"tz\":" + frame.z(track.u(), ahead) + "}";
				}
			}
		}
		return "null";
	}

	/** Nothing at eye height from row v to row to in column u, so no column stands right in front of the passenger. */
	private static boolean clearAhead(LocalFrame frame, int u, int v, int y, int to, Map<Long, String> idAt) {
		for (int w = v; w <= to; w++) {
			if (idAt.containsKey(cell(frame.x(u, w), y, frame.z(u, w)))) {
				return false;
			}
		}
		return true;
	}

	/** The roof option that plans this station's roof exactly, whatever look or seed chose it. */
	static StationRecipe.Roof resolvedRoof(StationSpec spec) {
		return switch (spec.roof()) {
			case NONE -> StationRecipe.Roof.OPEN;
			case CANOPY -> switch (spec.canopy()) {
				case FLAT -> StationRecipe.Roof.FLAT_CANOPIES;
				case BUTTERFLY -> StationRecipe.Roof.BUTTERFLY;
				case GABLE -> StationRecipe.Roof.GABLE;
			};
			case HALL -> switch (spec.shed()) {
				case FLAT -> StationRecipe.Roof.FLAT_SHED;
				case ARCH -> StationRecipe.Roof.ARCHED;
				case POINTED -> StationRecipe.Roof.POINTED;
				case MULTI_ARCH -> StationRecipe.Roof.ARCHES;
				case GABLED -> StationRecipe.Roof.RIDGE;
			};
		};
	}

	static StationRecipe.Size resolvedSize(StationSpec spec) {
		return switch (spec.buildingScale()) {
			case COMPACT -> StationRecipe.Size.COMPACT;
			case CITY -> StationRecipe.Size.CITY;
			case GRAND -> StationRecipe.Size.GRAND;
		};
	}

	private static String roof(StationSpec spec) {
		if (spec.options().underground()) {
			return "underground";
		}
		return switch (spec.roof()) {
			case NONE -> "no roof";
			case CANOPY -> spec.canopy() == CanopyShape.FLAT ? "platform canopies" : spec.canopy().label() + " platform canopies";
			case HALL -> lower(spec.shed().name()).replace('_', ' ') + " train shed";
		};
	}

	private static StationRecipe.Look look(String name, StationRecipe.Look fallback) {
		return name == null || name.isEmpty() ? fallback : value(StationRecipe.Look.class, name, fallback);
	}

	private static <E extends Enum<E>> E value(Class<E> type, String name, E fallback) {
		if (name == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	private static int number(String text, int fallback) {
		try {
			return text == null ? fallback : Integer.parseInt(text.strip());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}

	private static List<String> names(Enum<?>[] values) {
		final List<String> names = new ArrayList<>();
		for (final Enum<?> value : values) {
			names.add(value.name());
		}
		return names;
	}

	/** [["key","Label"],...] in declaration order. */
	private static <E> String keys(E[] values, java.util.function.Function<E, String> key, java.util.function.Function<E, String> label) {
		final StringBuilder out = new StringBuilder("[");
		for (final E value : values) {
			out.append(out.length() > 1 ? "," : "").append('[').append(Json.quote(key.apply(value))).append(',').append(Json.quote(label.apply(value))).append(']');
		}
		return out.append(']').toString();
	}

	/** {"key":"Label",...} in declaration order. */
	private static String labels(Enum<?>[] values) {
		final StringBuilder out = new StringBuilder("[");
		for (final Enum<?> value : values) {
			final String label = value instanceof StationRecipe.Look l ? l.label() : value instanceof StationRecipe.Platforms p ? p.label()
					: value instanceof StationRecipe.Roof r ? r.label() : value instanceof StationRecipe.Size z ? z.label() : value.name();
			if (out.length() > 1) {
				out.append(',');
			}
			out.append("[").append(Json.quote(value.name())).append(',').append(Json.quote(label)).append(']');
		}
		return out.append(']').toString();
	}
}
