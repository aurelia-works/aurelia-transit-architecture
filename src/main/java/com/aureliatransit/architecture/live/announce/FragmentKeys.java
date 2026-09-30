package com.aureliatransit.architecture.live.announce;

import java.text.Normalizer;

/**
 * Builds the fragment keys that voice packs map to sounds. Keys are lower-case ASCII-ish ids, e.g.
 * {@code phrase.calling_at}, {@code number.3}, {@code station.central_station}, {@code route.red_line}.
 */
public final class FragmentKeys {

	public static final int MAX_NUMBER = 99;

	private FragmentKeys() {
	}

	/**
	 * Lower-case, accents removed, every run of non-alphanumeric characters becomes a single underscore.
	 * "Zürich Hbf (Main)" becomes "zurich_hbf_main". Non-Latin letters are kept.
	 */
	public static String normalise(String name) {
		if (name == null) {
			return "";
		}
		final String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
		final StringBuilder out = new StringBuilder(decomposed.length());
		boolean pendingSeparator = false;
		for (int i = 0; i < decomposed.length(); ) {
			final int cp = decomposed.codePointAt(i);
			i += Character.charCount(cp);
			if (Character.getType(cp) == Character.NON_SPACING_MARK) {
				continue;
			}
			if (Character.isLetterOrDigit(cp)) {
				if (pendingSeparator && out.length() > 0) {
					out.append('_');
				}
				pendingSeparator = false;
				out.appendCodePoint(Character.toLowerCase(cp));
			} else {
				pendingSeparator = true;
			}
		}
		return out.toString();
	}

	public static String phrase(String name) {
		return "phrase." + name;
	}

	/**
	 * Key for a whole number 0..99, else null (the announcement then falls back to text).
	 */
	public static String number(int value) {
		return value >= 0 && value <= MAX_NUMBER ? "number." + value : null;
	}

	/**
	 * A platform "3" is spoken as a number; anything else (e.g. "2a") as {@code platform.2a}.
	 */
	public static String platform(String platformName) {
		final String trimmed = platformName == null ? "" : platformName.trim();
		if (!trimmed.isEmpty() && trimmed.length() <= 2 && trimmed.chars().allMatch(Character::isDigit)) {
			final String key = number(Integer.parseInt(trimmed));
			if (key != null) {
				return key;
			}
		}
		return "platform." + normalise(trimmed);
	}

	public static String station(String displayName) {
		return "station." + normalise(displayName);
	}

	public static String route(String routeName) {
		return "route." + normalise(routeName);
	}
}
