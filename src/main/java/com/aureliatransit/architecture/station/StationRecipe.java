package com.aureliatransit.architecture.station;


/**
 * The station wrench's choices (a look, an optional second look to mix in, through station, terminus or hub, tracks, platforms,
 * length, roof, building, name), turned into a plain description for the parser, so a station chosen with buttons goes
 * through exactly the same parser, design rules and checks as a typed one. Encoded as a short string on the wrench so its
 * screen opens with the last choices.
 *
 * @param mix  the look mixed in (its train shed, or its platforms when the first look has none), or null
 * @param size how grand the station building is; {@link Size#AUTO} lets the parser choose from the tracks and length
 * @param hub  a terminus whose outer tracks run through (implies {@code terminus}): an even number of tracks, at least four, on
 *             island platforms
 */
public record StationRecipe(Look look, Look mix, boolean terminus, int tracks, Platforms platforms, int length, Roof roof, boolean building,
		String name, Size size, boolean hub) {

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
		NEW_YORK("New York", "new york");

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
		FLAT_SHED("Flat train shed", "flat roof"),
		ARCHED("Arched train shed", "arched train shed"),
		POINTED("Pointed arch shed", "pointed arch train shed"),
		ARCHES("Several arches", "multi span train shed"),
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
		final StringBuilder text = new StringBuilder().append(tracks).append(" track");
		if (look != Look.AUTO) {
			text.append(' ').append(look.words);
			if (mix != null) {
				text.append(" and ").append(mix.words).append(" combined");
			}
		}
		text.append(hub ? " hub" : terminus ? " terminus" : " station");
		if (look == Look.AUTO && building && !terminus) {
			text.append(" with station building");
		}
		if (platforms != Platforms.AUTO) {
			text.append(", ").append(platforms == Platforms.ISLAND ? (tracks >= 4 ? "two island platforms" : "island platform") : "side platforms");
		}
		text.append(", ").append(length).append(" long");
		if (!roof.words.isEmpty()) {
			text.append(", ").append(roof.words);
		}
		if (!building) {
			text.append(", no station building");
		} else if (size != Size.AUTO) {
			text.append(", ").append(size.words).append(" building");
		}
		if (!name.isEmpty()) {
			text.append(", named ").append(name);
		}
		return text.toString();
	}

	/**
	 * What the parser reads for these choices plus the player's own words: the choices come first, and the parser keeps the first
	 * value it reads for each setting, so the choices win and a clash shows up as a "given twice" warning.
	 */
	public String text(String typed) {
		final String words = typed == null ? "" : typed.strip();
		return words.isEmpty() ? description() : description() + ", " + words;
	}

	/** One short line for buttons and chat: "Hamburg + Utrecht terminus, 4 tracks, long". */
	public String title() {
		final String looks = look == Look.AUTO ? "Custom" : look.label + (mix == null ? "" : " + " + mix.label);
		return looks + (hub ? " hub" : terminus ? " terminus" : " station") + ", " + tracks + (tracks == 1 ? " track, " : " tracks, ") + length + " long";
	}

	public StationRecipe withLook(Look value) {
		return new StationRecipe(value, mix, terminus, tracks, platforms, length, roof, building, name, size, hub);
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
		return new StationRecipe(look, value, terminus, tracks, platforms, length, roof, building, name, size, hub);
	}

	/** Through station, terminus or hub; the type button steps through them in that order. */
	public StationRecipe withHub(boolean value) {
		return new StationRecipe(look, mix, terminus || value, tracks, platforms, length, roof, building, name, size, value);
	}

	public StationRecipe withTerminus(boolean value) {
		return new StationRecipe(look, mix, value, tracks, platforms, length, roof, building, name, size, value && hub);
	}

	/** How far the track buttons move: a hub keeps an even number of tracks, so it goes up and down by two. */
	public int trackStep() {
		return hub ? 2 : 1;
	}

	public StationRecipe withTracks(int value) {
		return new StationRecipe(look, mix, terminus, value, platforms, length, roof, building, name, size, hub);
	}

	public StationRecipe withPlatforms(Platforms value) {
		return new StationRecipe(look, mix, terminus, tracks, value, length, roof, building, name, size, hub);
	}

	public StationRecipe withLength(int value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, value, roof, building, name, size, hub);
	}

	public StationRecipe withRoof(Roof value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, value, building, name, size, hub);
	}

	public StationRecipe withBuilding(boolean value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, roof, value, name, size, hub);
	}

	public StationRecipe withSize(Size value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, roof, building, name, value, hub);
	}

	public StationRecipe withName(String value) {
		return new StationRecipe(look, mix, terminus, tracks, platforms, length, roof, building, value, size, hub);
	}

	/** "look;mix;type;tracks;platforms;length;roof;building;name;size" (type 0 through, 1 terminus, 2 hub); read back by {@link #decode}. */
	public String encode() {
		return String.join(";", look.name(), mix == null ? "" : mix.name(), hub ? "2" : terminus ? "1" : "0", Integer.toString(tracks), platforms.name(),
				Integer.toString(length), roof.name(), building ? "1" : "0", name, size.name());
	}

	/** Reads {@link #encode}'s form; anything unreadable falls back to the defaults, field by field. */
	public static StationRecipe decode(String text) {
		final StationRecipe d = defaults();
		if (text == null || text.isEmpty()) {
			return d;
		}
		final String[] f = text.split(";", 10);
		return new StationRecipe(
				value(Look.class, f, 0, d.look), f.length > 1 && !f[1].isEmpty() ? value(Look.class, f, 1, null) : null,
				f.length > 2 ? f[2].equals("1") || f[2].equals("2") : d.terminus, f.length > 3 ? number(f[3], d.tracks) : d.tracks,
				value(Platforms.class, f, 4, d.platforms), f.length > 5 ? OLD_LENGTHS.getOrDefault(f[5], number(f[5], d.length)) : d.length, value(Roof.class, f, 6, d.roof),
				f.length > 7 ? !f[7].equals("0") : d.building, f.length > 8 ? f[8] : "", value(Size.class, f, 9, d.size), f.length > 2 && f[2].equals("2"));
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
