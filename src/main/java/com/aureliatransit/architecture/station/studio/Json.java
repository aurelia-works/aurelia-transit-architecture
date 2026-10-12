package com.aureliatransit.architecture.station.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for Station Studio, with no library so the studio also runs outside the game: {@link #parse} reads model and
 * blockstate files into maps, lists, doubles, strings, booleans and nulls; {@link #quote} writes a string.
 */
public final class Json {

	private final String text;
	private int at;

	private Json(String text) {
		this.text = text;
	}

	public static Object parse(String text) {
		final Json json = new Json(text);
		json.space();
		final Object value = json.value();
		json.space();
		if (json.at != text.length()) {
			throw json.error("trailing text");
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> object(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	@SuppressWarnings("unchecked")
	public static List<Object> list(Object value) {
		return value instanceof List<?> list ? (List<Object>) list : List.of();
	}

	public static double number(Object value, double fallback) {
		return value instanceof Double d ? d : fallback;
	}

	public static String string(Object value, String fallback) {
		return value instanceof String s ? s : fallback;
	}

	private Object value() {
		if (at >= text.length()) {
			throw error("unexpected end");
		}
		final char c = text.charAt(at);
		return switch (c) {
			case '{' -> object();
			case '[' -> array();
			case '"' -> string();
			case 't' -> literal("true", Boolean.TRUE);
			case 'f' -> literal("false", Boolean.FALSE);
			case 'n' -> literal("null", null);
			default -> number();
		};
	}

	private Map<String, Object> object() {
		final Map<String, Object> map = new LinkedHashMap<>();
		at++;
		space();
		if (peek('}')) {
			at++;
			return map;
		}
		while (true) {
			space();
			final String key = string();
			space();
			expect(':');
			space();
			map.put(key, value());
			space();
			if (peek(',')) {
				at++;
				continue;
			}
			expect('}');
			return map;
		}
	}

	private List<Object> array() {
		final List<Object> list = new ArrayList<>();
		at++;
		space();
		if (peek(']')) {
			at++;
			return list;
		}
		while (true) {
			space();
			list.add(value());
			space();
			if (peek(',')) {
				at++;
				continue;
			}
			expect(']');
			return list;
		}
	}

	private String string() {
		expect('"');
		final StringBuilder out = new StringBuilder();
		while (at < text.length()) {
			final char c = text.charAt(at++);
			if (c == '"') {
				return out.toString();
			}
			if (c != '\\') {
				out.append(c);
				continue;
			}
			final char e = text.charAt(at++);
			switch (e) {
				case 'n' -> out.append('\n');
				case 't' -> out.append('\t');
				case 'r' -> out.append('\r');
				case 'b' -> out.append('\b');
				case 'f' -> out.append('\f');
				case 'u' -> {
					out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
					at += 4;
				}
				default -> out.append(e);
			}
		}
		throw error("unterminated string");
	}

	private Double number() {
		final int start = at;
		while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
			at++;
		}
		if (start == at) {
			throw error("unexpected '" + text.charAt(at) + "'");
		}
		return Double.parseDouble(text.substring(start, at));
	}

	private Object literal(String word, Object value) {
		if (!text.startsWith(word, at)) {
			throw error("expected " + word);
		}
		at += word.length();
		return value;
	}

	private void space() {
		while (at < text.length()) {
			final char c = text.charAt(at);
			if (Character.isWhitespace(c)) {
				at++;
			} else if (text.startsWith("//", at)) {
				// a few hand-made models carry comments
				while (at < text.length() && text.charAt(at) != '\n') {
					at++;
				}
			} else {
				return;
			}
		}
	}

	private boolean peek(char c) {
		return at < text.length() && text.charAt(at) == c;
	}

	private void expect(char c) {
		if (!peek(c)) {
			throw error("expected '" + c + "'");
		}
		at++;
	}

	private IllegalArgumentException error(String what) {
		return new IllegalArgumentException(what + " at " + at);
	}

	/** A JSON string literal. */
	public static String quote(String text) {
		final StringBuilder out = new StringBuilder(text.length() + 2).append('"');
		for (final char c : text.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20 || c == '<' || c == '>' || c == '&') {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.append('"').toString();
	}

	/** A JSON array of strings. */
	public static String strings(Iterable<String> values) {
		final StringBuilder out = new StringBuilder("[");
		for (final String value : values) {
			if (out.length() > 1) {
				out.append(',');
			}
			out.append(quote(value));
		}
		return out.append(']').toString();
	}
}
