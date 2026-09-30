package com.aureliatransit.architecture.transit;

/**
 * MTR stores multilingual names as "segment|segment". Aurelia displays one Latin-script segment where possible.
 */
public final class StationNames {

	private StationNames() {
	}

	public static String display(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		final String[] segments = raw.split("\\|");
		for (final String segment : segments) {
			if (!segment.isBlank() && segment.codePoints().noneMatch(StationNames::isCjk)) {
				return segment.trim();
			}
		}
		return segments[0].trim();
	}

	private static boolean isCjk(int codePoint) {
		final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
		return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
				|| script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
	}
}
