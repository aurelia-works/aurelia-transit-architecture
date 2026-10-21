package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.StationSpec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A photo of a station into the words the Station Studio plans from (Enviro, 10-20: "get a pic of a station, for example Coventry,
 * put it in the station gen, and it recreates it"). Claude looks at the photo and answers with one description in the planner's
 * own vocabulary; the page puts it in the description box like a preset, so the player sees and can change what was read.
 * <p>
 * Needs an Anthropic API key, from {@code ANTHROPIC_API_KEY} or the {@code ata.anthropicKey} system property; without one the page
 * offers {@link #prompt()} to paste into Claude with the photo instead. Plain {@code java.net.http}: the Anthropic Java SDK would
 * bring OkHttp, Jackson and Kotlin into the mod's jar. One request at a time, only when a player asks, never polled.
 */
public final class PhotoDescriber {

	static final String MODEL = "claude-opus-5-5";
	static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
	/** The page sends the photo shrunk to this long edge, which is all the model reads well and keeps the request small. */
	public static final int MAX_EDGE = 1568;
	/** A shrunk JPEG is well under this; anything larger is not one the page sent. */
	public static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
	static final List<String> MEDIA_TYPES = List.of("image/jpeg", "image/png", "image/webp");
	private static final Duration TIMEOUT = Duration.ofSeconds(120);

	private PhotoDescriber() {
	}

	/** The API key, or null when none is set. */
	public static String key() {
		final String env = System.getenv("ANTHROPIC_API_KEY");
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		final String property = System.getProperty("ata.anthropicKey");
		return property == null || property.isBlank() ? null : property.trim();
	}

	/** What Claude is asked: the planner's vocabulary, so every word it writes is one the planner reads. */
	public static String prompt() {
		final String looks = Arrays.stream(StationRecipe.Look.values()).filter(l -> l != StationRecipe.Look.AUTO)
				.map(l -> l.name().toLowerCase(Locale.ROOT).replace('_', ' ')).collect(Collectors.joining(", "));
		return """
				You turn a photo of a railway station into one line for a Minecraft station generator, which reads plain words.
				Write only that line: comma-separated phrases, no quotes, no explanation, at most 300 characters.

				Use only these phrases:
				- tracks: "N track" (1 to %d); count the platform tracks you can see, best guess if some are hidden
				- kind: "station" (trains run through), "terminus" (tracks end at a building), "underground station" (platforms in a tiled box below the street), "tram stop"
				- platforms: "side platforms", "island platform", "two island platforms", "island and side platforms"
				- length: "N long", the platform length in metres (%d to %d); suburban about 120 to 200, city 200 to 300, metro 80 to 130
				- roof: "canopy", "glass canopy", "butterfly canopy", "gable canopy", "train shed", "arched train shed", "no roof"
				- building: "small station building", "city station building", "grand station building", or "no station building"
				- ways across: "footbridge", "underpass", "mezzanine", "escalators"
				- look, the nearest one: %s; also "victorian", "art deco", "modernist", "brick", "glass", "concrete"
				- underground walls: "orange tiles", "green tiles", "yellow tiles", "blue tiles", "grey tiles"
				- blocks: "use <minecraft block> instead of <minecraft block>" when a material is clearly not in the look (for example "use stone bricks instead of sandstone")
				- name: end with "named <Station Name>" if a sign or the photo shows the station's name

				Example: three track coventry station, island and side platforms, 250 long, city station building with footbridge, named Coventry
				""".formatted(StationSpec.MAX_TRACKS, StationSpec.MIN_LENGTH, StationSpec.MAX_LENGTH, looks);
	}

	/** The Messages API request body for one photo. */
	static String request(byte[] image, String mediaType) {
		return "{\"model\":" + Json.quote(MODEL) + ",\"max_tokens\":4000,\"fallbacks\":\"default\",\"output_config\":{\"effort\":\"low\"},"
				+ "\"system\":" + Json.quote(prompt()) + ",\"messages\":[{\"role\":\"user\",\"content\":["
				+ "{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":" + Json.quote(mediaType) + ",\"data\":\""
				+ Base64.getEncoder().encodeToString(image) + "\"}},"
				+ "{\"type\":\"text\",\"text\":\"Describe this station for the generator.\"}]}]}";
	}

	/**
	 * The description in a Messages API response: its text, on one line, cut to what the description box holds. Throws
	 * {@link IllegalStateException} (the reason is for the player) when Claude declined or said nothing.
	 */
	static String description(String response) {
		final Map<String, Object> message = Json.object(Json.parse(response));
		if ("refusal".equals(Json.string(message.get("stop_reason"), ""))) {
			throw new IllegalStateException("Claude would not describe this photo. Try another one.");
		}
		final StringBuilder text = new StringBuilder();
		for (final Object block : Json.list(message.get("content"))) {
			final Map<String, Object> b = Json.object(block);
			if ("text".equals(Json.string(b.get("type"), ""))) {
				text.append(Json.string(b.get("text"), "")).append(' ');
			}
		}
		String line = text.toString().replaceAll("\\s+", " ").trim();
		// a stray pair of quotes or a trailing full stop is not part of the words
		line = line.replaceAll("^[\"'`]+|[\"'`]+$", "").replaceAll("\\.$", "").trim();
		if (line.isEmpty()) {
			throw new IllegalStateException("Claude did not describe this photo. Try another one.");
		}
		return line.length() > StudioApi.MAX_TYPED ? line.substring(0, StudioApi.MAX_TYPED) : line;
	}

	/** Asks Claude for the photo's description. Throws {@link IllegalStateException} with a reason for the player. */
	public static String describe(byte[] image, String mediaType, String key) throws IOException, InterruptedException {
		if (!MEDIA_TYPES.contains(mediaType)) {
			throw new IllegalStateException("Send a JPEG, PNG or WebP photo.");
		}
		// a test points this at a stand-in server
		final String endpoint = System.getProperty("ata.anthropicEndpoint", ENDPOINT);
		final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(TIMEOUT)
				.header("x-api-key", key).header("anthropic-version", "2023-06-01").header("anthropic-beta", "server-side-fallback-2026-07-01")
				.header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(request(image, mediaType))).build();
		final HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
				.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 401 || response.statusCode() == 403) {
			throw new IllegalStateException("The Anthropic API key was not accepted.");
		}
		if (response.statusCode() == 429 || response.statusCode() >= 500) {
			throw new IllegalStateException("Claude is busy right now. Try again in a minute.");
		}
		if (response.statusCode() != 200) {
			String why = "HTTP " + response.statusCode();
			try {
				why = Json.string(Json.object(Json.object(Json.parse(response.body())).get("error")).get("message"), why);
			} catch (RuntimeException e) {
				// not a JSON error body: the status says enough
			}
			throw new IllegalStateException("Claude could not read the photo: " + why);
		}
		return description(response.body());
	}
}
