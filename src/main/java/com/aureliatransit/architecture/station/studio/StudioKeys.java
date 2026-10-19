package com.aureliatransit.architecture.station.studio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The keys the studio's walk mode listens to, in the page's own names ({@code KeyboardEvent.code}). Opened from the game they
 * are the player's own Minecraft bindings; outside the game, Minecraft's defaults.
 */
public final class StudioKeys {

	/** Minecraft's default movement keys, by action. */
	public static final Map<String, String> DEFAULTS = Map.of("forward", "KeyW", "back", "KeyS", "left", "KeyA", "right", "KeyD",
			"jump", "Space", "sneak", "ShiftLeft", "sprint", "ControlLeft");

	private static volatile Supplier<Map<String, String>> source = () -> DEFAULTS;

	private StudioKeys() {
	}

	/** The game gives the studio its player's bindings: action to Minecraft key translation key (e.g. {@code key.keyboard.w}). */
	public static void source(Supplier<Map<String, String>> translationKeys) {
		source = translationKeys;
	}

	/** The bindings as JSON, every action present (a binding the page cannot read, such as a mouse button, keeps its default). */
	public static String json() {
		final Map<String, String> bound = new LinkedHashMap<>(DEFAULTS);
		try {
			source.get().forEach((action, key) -> {
				final String code = domCode(key);
				if (code != null && DEFAULTS.containsKey(action)) {
					bound.put(action, code);
				}
			});
		} catch (RuntimeException e) {
			// the game's options are not readable (shutting down): the defaults stand
		}
		final StringBuilder out = new StringBuilder("{");
		for (final Map.Entry<String, String> entry : bound.entrySet()) {
			out.append(out.length() > 1 ? "," : "").append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
		}
		return out.append('}').toString();
	}

	/** A Minecraft key translation key as the page's {@code KeyboardEvent.code}, or null for a key the page cannot see (a mouse button). */
	public static String domCode(String translationKey) {
		if (translationKey == null || !translationKey.startsWith("key.keyboard.")) {
			return null;
		}
		final String name = translationKey.substring("key.keyboard.".length());
		if (name.length() == 1 && Character.isLetter(name.charAt(0))) {
			return "Key" + name.toUpperCase(java.util.Locale.ROOT);
		}
		if (name.length() == 1 && Character.isDigit(name.charAt(0))) {
			return "Digit" + name;
		}
		return switch (name) {
			case "space" -> "Space";
			case "left.shift" -> "ShiftLeft";
			case "right.shift" -> "ShiftRight";
			case "left.control" -> "ControlLeft";
			case "right.control" -> "ControlRight";
			case "left.alt" -> "AltLeft";
			case "right.alt" -> "AltRight";
			case "tab" -> "Tab";
			case "caps.lock" -> "CapsLock";
			case "up" -> "ArrowUp";
			case "down" -> "ArrowDown";
			case "left" -> "ArrowLeft";
			case "right" -> "ArrowRight";
			default -> null;
		};
	}
}
