package com.aureliatransit.architecture.text;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Translation lookup for text that is built in plain Java (terminal pages, board strips) rather than as a Minecraft
 * {@code Text}. The client installs the game's language ({@link #install}); until then, and in unit tests, the
 * mod's bundled {@code en_us.json} is used, so pure layout code stays testable and never shows a raw key.
 *
 * <p>Holds no client classes, so it is safe to load on a dedicated server.
 */
public final class Tr {

	public static final String PREFIX = "screen." + AureliaTransitArchitecture.MOD_ID + ".";

	private static volatile BiFunction<String, Object[], String> resolver = Tr::english;
	private static volatile JsonObject bundled;

	private Tr() {
	}

	/** Installs the resolver (the game's I18n on the client); null restores the bundled English. */
	public static void install(BiFunction<String, Object[], String> value) {
		resolver = value == null ? Tr::english : value;
	}

	/** Translates a key under {@value #PREFIX} (screen/terminal text). */
	public static String t(String key, Object... args) {
		return raw(PREFIX + key, args);
	}

	/** Translates a full translation key. */
	public static String raw(String fullKey, Object... args) {
		return resolver.apply(fullKey, args);
	}

	private static String english(String key, Object[] args) {
		JsonObject lang = bundled;
		if (lang == null) {
			lang = load();
			bundled = lang;
		}
		final String pattern = lang.has(key) ? lang.get(key).getAsString() : key;
		try {
			return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
		} catch (IllegalFormatException e) {
			return pattern;
		}
	}

	private static JsonObject load() {
		try (InputStream in = Tr.class.getResourceAsStream("/assets/" + AureliaTransitArchitecture.MOD_ID + "/lang/en_us.json")) {
			if (in != null) {
				return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (IOException | RuntimeException ignored) {
			// fall through to an empty table: keys are shown, nothing throws into a renderer
		}
		return new JsonObject();
	}
}
