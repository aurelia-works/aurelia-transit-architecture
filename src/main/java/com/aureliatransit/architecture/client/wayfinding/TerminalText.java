package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.terminal.AccessibilityNote;
import com.aureliatransit.architecture.text.Tr;
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

	/** Translation keys (under {@link Tr#PREFIX}) of the explicit "not available" lines. */
	public static final String NO_STATION_KEY = "term_no_station";
	public static final String NO_NOTICES_KEY = "term_no_notices";
	public static final String NO_ACCESSIBILITY_KEY = "term_no_accessibility";

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
			lines.add(new Line(Tr.t(NO_STATION_KEY), Kind.DIM));
			return lines;
		}
		lines.add(new Line(info.stationCode().isEmpty() ? info.stationName() : info.stationName() + "  (" + info.stationCode() + ")", Kind.HEADING));
		if (!info.lines().isEmpty()) {
			final List<String> labels = new ArrayList<>();
			for (final LineBadge badge : info.lines()) {
				labels.add(badge.label());
			}
			lines.add(new Line(Tr.t("term_lines", String.join(", ", labels)), Kind.BODY));
		}
		if (info.platforms().isEmpty()) {
			lines.add(new Line(Tr.t("term_no_platforms"), Kind.DIM));
		} else {
			final List<String> names = new ArrayList<>();
			for (final PlatformReference platform : info.platforms()) {
				names.add(platform.name());
			}
			lines.add(new Line(Tr.t("term_platforms", String.join(", ", names)), Kind.BODY));
		}
		if (info.exits().isEmpty()) {
			lines.add(new Line(Tr.t("term_no_exits"), Kind.DIM));
		}
		for (final ExitInfo exit : info.exits()) {
			lines.add(new Line(Tr.t("term_exit", exit.label()), Kind.HEADING));
			for (final String destination : exit.destinations()) {
				lines.add(new Line("  " + destination, Kind.BODY));
			}
		}
		if (!info.transfers().isEmpty()) {
			lines.add(new Line(Tr.t("term_transfers", info.transfers()), Kind.BODY));
		}
		if (!info.streetLabel().isEmpty()) {
			lines.add(new Line(Tr.t("term_street", info.streetLabel()), Kind.BODY));
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
			lines.add(new Line(Tr.t(NO_ACCESSIBILITY_KEY), Kind.DIM));
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
				lines.add(new Line(Tr.t("term_scope." + scope.name().toLowerCase(Locale.ROOT)), Kind.HEADING));
			}
			lines.add(new Line(severityPrefix(message.severity()) + message.text(), kind(message.severity())));
		}
		if (lines.isEmpty()) {
			lines.add(new Line(Tr.t(NO_NOTICES_KEY), Kind.DIM));
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
		return Tr.t("wf_pictogram." + pictogram.name().toLowerCase(Locale.ROOT));
	}
}
