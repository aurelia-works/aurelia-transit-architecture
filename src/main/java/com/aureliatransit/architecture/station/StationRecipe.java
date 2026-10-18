package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The station wrench's choices (a look, an optional second look to mix in, through station, terminus or hub, tracks, platforms,
 * length, roof, building, name), turned into a plain description for the parser, so a station chosen with buttons goes
 * through exactly the same parser, design rules and checks as a typed one. Encoded as a short string on the wrench so its
 * screen opens with the last choices.
 *
 * @param mix  the look mixed in (its train shed, or its platforms when the first look has none), or null
 * @param size how grand the station building is; {@link Size#AUTO} lets the parser choose from the tracks and length
 * @param hub   a terminus whose outer tracks run through (implies {@code terminus}): an even number of tracks, at least four, on
 *              island platforms
 * @param width every platform's width in blocks, {@link #MIN_WIDTH} to {@link #MAX_WIDTH}, or 0 to leave it to the parser
 */
public record StationRecipe(Look look, Look mix, boolean terminus, int tracks, Platforms platforms, int length, Roof roof, boolean building,
		String name, Size size, boolean hub, int width) {

	/** A city look: its button label and the words the parser reads; {@link #AUTO} leaves the look to the typed description. */
	public enum Look {
		AUTO("From description", ""),
		UTRECHT("Utrecht", "utrecht"),
		AMSTERDAM("Amsterdam", "amsterdam"),
		HAMBURG("Hamburg", "hamburg"),
		MUNICH("Munich", "munich"),
		FRANKFURT("Frankfurt", "frankfurt"),
		ST_PANCRAS("St Pancras", "st pancras"),
		ANTWERP("Antwerp", "antwerp"),
		NEW_YORK("New York", "new york"),
		TOKYO("Tokyo", "tokyo"),
		ZURICH("Zurich", "zurich"),
		PARIS("Paris", "paris"),
		MILAN("Milan", "milan"),
		BERLIN("Berlin", "berlin"),
		HONG_KONG("Hong Kong", "hong kong"),
		BEIJING("Beijing", "beijing"),
		BANGKOK("Bangkok", "bangkok"),
		KUALA_LUMPUR("Kuala Lumpur", "kuala lumpur"),
		MUMBAI("Mumbai", "mumbai");

		private final String label;
		private final String words;

		Look(String label, String words) {
			this.label = label;
			this.words = words;
		}

		public String label() {
			return label;
		}
	}

	public enum Platforms {
		AUTO("Auto"), SIDE("Side"), ISLAND("Island");

		private final String label;

		Platforms(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public enum Roof {
		AUTO("Auto (city's own)", ""),
		CANOPIES("Canopies", "canopies"),
		FLAT_CANOPIES("Flat canopies", "flat canopies"),
		BUTTERFLY("Butterfly canopies (S-Bahn)", "butterfly canopies"),
		GABLE("Gable canopies (heritage)", "gable canopies"),
		FLAT_SHED("Flat train shed", "flat roof"),
		ARCHED("Arched train shed", "arched train shed"),
		POINTED("Pointed arch shed", "pointed arch train shed"),
		ARCHES("Several arches", "multi span train shed"),
		RIDGE("Ridge and furrow shed", "ridge and furrow train shed"),
		OPEN("No roof", "no roof");

		private final String label;
		private final String words;

		Roof(String label, String words) {
			this.label = label;
			this.words = words;
		}

		public String label() {
			return label;
		}
	}

	/** The building size: {@link #AUTO} leaves it to the parser (grand for 3+ tracks or 100+ long). */
	public enum Size {
		AUTO("Auto (by size)", ""),
		COMPACT("Compact", "compact"),
		CITY("City", "city"),
		GRAND("Grand", "grand");

		private final String label;
		private final String words;

		Size(String label, String words) {
			this.label = label;
			this.words = words;
		}

		public String label() {
			return label;
		}
	}

	public static final int MIN_TRACKS = StationSpec.MIN_TRACKS;
	public static final int MAX_TRACKS = StationSpec.MAX_TRACKS;
	public static final int MIN_LENGTH = StationSpec.MIN_LENGTH;
	public static final int MAX_LENGTH = StationSpec.MAX_LENGTH;
	public static final int DEFAULT_LENGTH = 64;
	public static final int MIN_WIDTH = StationSpec.MIN_WIDTH;
	public static final int MAX_WIDTH = StationSpec.MAX_WIDTH;
	/** The lengths the old Short / Medium / Long / Huge buttons stood for, read back from wrenches saved before lengths were numbers. */
	private static final java.util.Map<String, Integer> OLD_LENGTHS = java.util.Map.of("SHORT", 32, "MEDIUM", 64, "LONG", 96, "HUGE", 128);

	public StationRecipe {
		if (look == null) {
			look = Look.UTRECHT;
		}
		if (mix == look || mix == Look.AUTO) {
			mix = null;
		}
		tracks = Math.max(MIN_TRACKS, Math.min(MAX_TRACKS, tracks));
		// an island needs two tracks; one train shed cannot span three or more tracks between two side platforms
		if (platforms == null || platforms == Platforms.ISLAND && tracks < 2 || platforms == Platforms.SIDE && tracks >= 3) {
			platforms = Platforms.AUTO;
		}
		length = Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
		if (roof == null) {
			roof = Roof.AUTO;
		}
		if (hub) {
			// the outer two tracks run through, the inner ones end in pairs at island platforms
			terminus = true;
			tracks = Math.min(MAX_TRACKS - MAX_TRACKS % 2, Math.max(4, tracks + tracks % 2));
			platforms = Platforms.AUTO;
		}
		if (terminus) {
			building = true;
		}
		name = clean(name == null ? "" : name);
		if (size == null) {
			size = Size.AUTO;
		}
		width = width <= 0 ? 0 : Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
	}

	/** The recipe of the old eleven fields, with the platform width left to the parser. */
	public StationRecipe(Look look, Look mix, boolean terminus, int tracks, Platforms platforms, int length, Roof roof, boolean building,
			String name, Size size, boolean hub) {
		this(look, mix, terminus, tracks, platforms, length, roof, building, name, size, hub, 0);
	}

	public static StationRecipe defaults() {
		return new StationRecipe(Look.UTRECHT, null, false, 2, Platforms.AUTO, DEFAULT_LENGTH, Roof.AUTO, true, "", Size.AUTO, false);
	}

	/** Names keep letters, digits, spaces and a few marks the parser accepts inside a name. */
	private static String clean(String raw) {
		final StringBuilder out = new StringBuilder();
		for (final char c : raw.toCharArray()) {
			if (Character.isLetterOrDigit(c) || c == ' ' || c == '\'' || c == '&') {
				out.append(c);
			}
		}
		final String text = out.toString().strip().replaceAll("\\s+", " ");
		return text.length() > StationSpec.MAX_NAME ? text.substring(0, StationSpec.MAX_NAME).strip() : text;
	}

	/** The description the parser reads, e.g. "two track hamburg and utrecht combined station, island platforms, 64 long". */
	public String description() {
		return description(null);
	}

	/**
	 * The description the parser reads from the given controls only: a part is left out when {@code pinned} does not contain it,
	 * so a control the player never touched does not override their typed words. {@code null} pins every control (the buttons'
	 * own description, as before).
	 */
	public String description(Set<String> pinned) {
		final boolean all = pinned == null;
		final List<String> head = new ArrayList<>();
		if (all || pinned.contains("tracks")) {
			head.add(tracks + " track");
		}
		if ((all || pinned.contains("look")) && look != Look.AUTO) {
			head.add(mix != null ? look.words + " and " + mix.words + " combined" : look.words);
		}
		if (all || pinned.contains("mode")) {
			head.add(hub ? "hub" : terminus ? "terminus" : "station");
		}
		if ((all || pinned.contains("building")) && look == Look.AUTO && building && !terminus) {
			head.add("with station building");
		}
		final List<String> text = new ArrayList<>();
		if (!head.isEmpty()) {
			text.add(String.join(" ", head));
		}
		if ((all || pinned.contains("layout")) && platforms != Platforms.AUTO) {
			text.add(platforms == Platforms.ISLAND ? (tracks >= 4 ? "two island platforms" : "island platform") : "side platforms");
		}
		if (all || pinned.contains("length")) {
			text.add(length + " long");
		}
		if ((all || pinned.contains("width")) && width > 0) {
			text.add(width + " wide platforms");
		}
		if ((all || pinned.contains("roof")) && !roof.words.isEmpty()) {
			text.add(roof.words);
		}
		if (!building && (all || pinned.contains("building"))) {
			text.add("no station building");
		} else if (building && size != Size.AUTO && (all || pinned.contains("size"))) {
			text.add(size.words + " building");
		}
		if ((all || pinned.contains("name")) && !name.isEmpty()) {
			text.add("named " + name);
		}
		return String.join(", ", text);
	}

	/**
	 * What the parser reads for these choices plus the player's own words. With a typed description, only the pinned controls go
	 * in front of it, so the words decide everything else; with no words the whole description is sent as before.
	 */
	public String text(String typed) {
		return text(typed, null);
	}

	/** {@link #text(String)} where a typed description overrides every control that is not in {@code pinned}. */
	public String text(String typed, Set<String> pinned) {
		final String words = typed == null ? "" : typed.strip();
		if (words.isEmpty()) {
			return description();
		}
		final String head = description(pinned);
		return head.isEmpty() ? words : head + ", " + words;
	}

	/** One short line for buttons and chat: "Hamburg + Utrecht terminus, 4 tracks, long". */
	public String title() {
		final String looks = look == Look.AUTO ? "Custom" : look.label + (mix == null ? "" : " + " + mix.label);
		return looks + (hub ? " hub" : terminus ? " terminus" : " station") + ", " + tracks + (tracks == 1 ? " track, " : " tracks, ") + length + " long";
	}

	public StationRecipe withLook(Look value) {
		return new StationRecipe(value, mix, terminus, tracks, platforms, length, roof, building, name, size, hub, width);
	}

	/**
	 * The Mix button's next choice: none, then every city look in order except the chosen one, then none again. Custom
	 * ({@link Look#AUTO}) is never a mix, since the recipe turns it back into none.
	 */
	public Look nextMix() {
		final Look[] all = Look.values();
		for (int i = mix == null ? 0 : mix.ordinal() + 1; i < all.length; i++) {
			if (all[i] != Look.AUTO && all[i] != look) {
				return all[i];
			}
		}
		return null;
	}

	public StationRecipe withMix(Look value) {
		return new StationRecipe(look, value, terminus, tracks, platforms, length, roof, building, name, size, hub, width);
	}

	/** Through station, terminus or hub; the type button steps through them in that order. */
	public StationRecipe withHub(boolean value) {
		return new StationRecipe(look, mix, terminus || value, tracks, platforms, length, roof, building, name, size, value, width);
	}

	public StationRecipe withTerminus(boolean value) {
		return new StationRecipe(look, mix, value, tracks, platforms, length, roof, building, name, size, value && hub, width);
	}

	/** How far the track buttons move: a hub keeps an even number of tracks, so it goes up and down by two. */
	public int trackStep() {
		return hub ? 2 : 1;
	}

	public StationRecipe withTracks(int value) {
		return new StationRecipe(look, mix, terminus, value, platforms, length, roof, building, name, size, hub, width);
	}

	public StationRecipe withPlatforms(Platforms value) {
		return new StationRecipe(look, mix, terminus, tracks, value, length, roof, building, name, size, hub, width);
	}

	public StationRecipe withLength(int value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, value, roof, building, name, size, hub, width);
	}

	public StationRecipe withRoof(Roof value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, value, building, name, size, hub, width);
	}

	public StationRecipe withBuilding(boolean value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, roof, value, name, size, hub, width);
	}

	public StationRecipe withSize(Size value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, roof, building, name, value, hub, width);
	}

	public StationRecipe withWidth(int value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, roof, building, name, size, hub, value);
	}

	public StationRecipe withName(String value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, roof, building, value, size, hub, width);
	}

	/**
	 * "look;mix;type;tracks;platforms;length;roof;building;name;size;width" (type 0 through, 1 terminus, 2 hub; width 0 for auto);
	 * read back by {@link #decode}.
	 */
	public String encode() {
		return String.join(";", look.name(), mix == null ? "" : mix.name(), hub ? "2" : terminus ? "1" : "0", Integer.toString(tracks), platforms.name(),
				Integer.toString(length), roof.name(), building ? "1" : "0", name, size.name(), Integer.toString(width));
	}

	/** Reads {@link #encode}'s form; anything unreadable falls back to the defaults, field by field. */
	public static StationRecipe decode(String text) {
		final StationRecipe d = defaults();
		if (text == null || text.isEmpty()) {
			return d;
		}
		final String[] f = text.split(";", 11);
		return new StationRecipe(
				value(Look.class, f, 0, d.look), f.length > 1 && !f[1].isEmpty() ? value(Look.class, f, 1, null) : null,
				f.length > 2 ? f[2].equals("1") || f[2].equals("2") : d.terminus, f.length > 3 ? number(f[3], d.tracks) : d.tracks,
				value(Platforms.class, f, 4, d.platforms), f.length > 5 ? OLD_LENGTHS.getOrDefault(f[5], number(f[5], d.length)) : d.length, value(Roof.class, f, 6, d.roof),
				f.length > 7 ? !f[7].equals("0") : d.building, f.length > 8 ? f[8] : "", value(Size.class, f, 9, d.size), f.length > 2 && f[2].equals("2"),
				f.length > 10 ? number(f[10], 0) : 0);
	}

	private static <E extends Enum<E>> E value(Class<E> type, String[] fields, int index, E fallback) {
		if (index >= fields.length) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, fields[index]);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	private static int number(String text, int fallback) {
		try {
			return Integer.parseInt(text.strip());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
