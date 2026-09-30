package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.TextSanitizer;

import java.util.ArrayList;
import java.util.List;

/**
 * One station exit as MTR defines it (exit name plus the destinations/streets it leads to), display-ready.
 */
public record ExitInfo(String label, List<String> destinations) {

	public static final int MAX_LABEL = 4;
	public static final int MAX_DESTINATIONS = 6;
	public static final int MAX_DESTINATION = 32;

	public ExitInfo {
		label = TextSanitizer.sanitize(label, MAX_LABEL);
		final List<String> clean = new ArrayList<>();
		if (destinations != null) {
			for (final String destination : destinations) {
				final String text = TextSanitizer.sanitize(destination, MAX_DESTINATION);
				if (!text.isEmpty() && clean.size() < MAX_DESTINATIONS) {
					clean.add(text);
				}
			}
		}
		destinations = List.copyOf(clean);
	}
}
