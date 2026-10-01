package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.terminal.AccessibilityNote;
import com.aureliatransit.architecture.terminal.StationInfo;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.wayfinding.ExitInfo;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.wayfinding.MessageScope;
import com.aureliatransit.architecture.wayfinding.MessageSeverity;
import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.ServiceMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure content of the text pages (station, service info, accessibility) as logical lines; the screen wraps and
 * paginates them. Nothing is invented: missing facts produce an explicit "not available" line.
 */
public final class TerminalText {

	public static final String NO_STATION = "No station linked";
	public static final String NO_NOTICES = "No service notices";
	public static final String NO_ACCESSIBILITY = "No accessibility information has been provided for this station";

	public enum Kind {
		HEADING, BODY, DIM, INFO, WARNING, DISRUPTION
	}

	public record Line(String text, Kind kind) {
	}

	private TerminalText() {
	}

	public static List<Line> stationLines(StationInfo info) {
		final List<Line> lines = new ArrayList<>();
		if (info.stationName().isEmpty() && info.platforms().isEmpty() && info.exits().isEmpty() && info.lines().isEmpty()) {
			lines.add(new Line(NO_STATION, Kind.DIM));
			return lines;
		}
		lines.add(new Line(info.stationCode().isEmpty() ? info.stationName() : info.stationName() + "  (" + info.stationCode() + ")", Kind.HEADING));
		if (!info.lines().isEmpty()) {
			final List<String> labels = new ArrayList<>();
			for (final LineBadge badge : info.lines()) {
				labels.add(badge.label());
			}
			lines.add(new Line("Lines: " + String.join(", ", labels), Kind.BODY));
		}
		if (info.platforms().isEmpty()) {
			lines.add(new Line("No platform information", Kind.DIM));
		} else {
			final List<String> names = new ArrayList<>();
			for (final PlatformReference platform : info.platforms()) {
				names.add(platform.name());
			}
			lines.add(new Line("Platforms: " + String.join(", ", names), Kind.BODY));
		}
		if (info.exits().isEmpty()) {
			lines.add(new Line("No exit information", Kind.DIM));
		}
		for (final ExitInfo exit : info.exits()) {
			lines.add(new Line("Exit " + exit.label(), Kind.HEADING));
			for (final String destination : exit.destinations()) {
				lines.add(new Line("  " + destination, Kind.BODY));
			}
		}
		if (!info.transfers().isEmpty()) {
			lines.add(new Line("Transfers: " + info.transfers(), Kind.BODY));
		}
		if (!info.streetLabel().isEmpty()) {
			lines.add(new Line("Street / landmark: " + info.streetLabel(), Kind.BODY));
		}
		return lines;
	}

	public static List<Line> accessibilityLines(StationInfo info) {
		final List<Line> lines = new ArrayList<>();
		for (final AccessibilityNote note : info.accessibility()) {
			final String name = note.pictogram() == Pictogram.NONE ? "" : pretty(note.pictogram());
			final String text = name.isEmpty() ? note.text() : note.text().isEmpty() ? name : name + ": " + note.text();
			if (!text.isEmpty()) {
				lines.add(new Line(text, Kind.BODY));
			}
		}
		if (lines.isEmpty()) {
			lines.add(new Line(NO_ACCESSIBILITY, Kind.DIM));
		}
		return lines;
	}

	/**
	 * Every applicable notice, grouped by scope in the order given (the caller passes {@link
	 * com.aureliatransit.architecture.wayfinding.ServiceMessages#applicable}): a heading when the scope changes, then
	 * one line per message prefixed with its severity.
	 */
	public static List<Line> serviceLines(List<ServiceMessage> messages) {
		final List<Line> lines = new ArrayList<>();
		MessageScope scope = null;
		for (final ServiceMessage message : messages) {
			if (message.isEmpty()) {
				continue;
			}
			if (message.scope() != scope) {
				scope = message.scope();
				lines.add(new Line(scope == MessageScope.STATION ? "Station" : scope == MessageScope.DISPLAY ? "This display" : "Network", Kind.HEADING));
			}
			lines.add(new Line(severityPrefix(message.severity()) + message.text(), kind(message.severity())));
		}
		if (lines.isEmpty()) {
			lines.add(new Line(NO_NOTICES, Kind.DIM));
		}
		return lines;
	}

	static String severityPrefix(MessageSeverity severity) {
		return switch (severity) {
			case INFO -> "";
			case WARNING -> "! ";
			case DISRUPTION -> "! ";
			case SEVERE -> "!! ";
		};
	}

	public static Kind kind(MessageSeverity severity) {
		return switch (severity) {
			case INFO -> Kind.INFO;
			case WARNING -> Kind.WARNING;
			case DISRUPTION, SEVERE -> Kind.DISRUPTION;
		};
	}

	static String pretty(Pictogram pictogram) {
		final String lower = pictogram.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
