package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.SiteIssue;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.TrackLayout;
import com.aureliatransit.architecture.station.Violation;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Chat lines of the station generator: the plan summary (what was understood, the footprint, rail nodes, block counts and
 * both checks) and the site report.
 */
final class StationText {

	/** Violations and site issues listed individually in chat (all are counted). */
	private static final int LISTED = 5;

	private StationText() {
	}

	static String planLine(StationSpec spec) {
		return spec.trackCount() + (spec.trackCount() == 1 ? " track, " : " tracks, ") + lower(spec.platformLayout().name()) + " platforms, "
				+ spec.platformLength() + " x " + spec.platformWidth() + ", " + lower(spec.style().name()).replace('_', ' ') + ", "
				+ (switch (spec.roof()) {
					case NONE -> "no roof";
					case CANOPY -> "canopy";
					case HALL -> "hall roof";
				}) + (spec.stepFree() ? ", step-free" : "") + (spec.name().isEmpty() ? "" : ", \"" + spec.name() + "\"");
	}

	static void summary(StationSessions.Pending pending, BlockStateResolver.Check check, Consumer<Text> out) {
		final ParseResult parse = pending.parse();
		final StationPlan plan = pending.plan();
		final LocalFrame frame = pending.frame();
		final TrackLayout layout = plan.layout();
		out.accept(Text.literal("[ATA Station] " + planLine(pending.spec())).formatted(Formatting.GOLD));
		list(out, "Understood: ", parse.understood(), Formatting.GRAY);
		list(out, "Defaults: ", parse.defaulted(), Formatting.DARK_GRAY);
		list(out, "Warnings: ", parse.warnings(), Formatting.YELLOW);
		list(out, "Ignored: ", parse.ignored(), Formatting.DARK_GRAY);
		out.accept(Text.literal("Footprint " + layout.width() + " x " + (layout.vMax() - layout.vMin() + 1) + " from " + corner(frame, 0, layout.vMin())
				+ " to " + corner(frame, layout.width() - 1, layout.vMax()) + ", rail level y=" + frame.oy() + ", tracks run "
				+ lower(frame.forward().name())));
		for (final TrackLayout.Track track : layout.tracks()) {
			final String what;
			if (track.through()) {
				what = "through track (no platform)";
			} else if (!pending.spec().railNodes()) {
				what = "rail nodes off";
			} else if (pending.railSkipped()) {
				what = BlockStateResolver.RAIL_SKIPPED;
			} else if (keptNode(pending, track.u(), 0) && keptNode(pending, track.u(), layout.platformLength() - 1)) {
				what = "uses the rail nodes already at " + corner(frame, track.u(), 0) + " and " + corner(frame, track.u(), layout.platformLength() - 1);
			} else {
				what = "rail nodes at " + corner(frame, track.u(), 0) + " and " + corner(frame, track.u(), layout.platformLength() - 1)
						+ ": connect them with an MTR Platform Rail Connector";
			}
			out.accept(Text.literal("Track " + track.number() + ": " + what).formatted(Formatting.AQUA));
		}
		final StringBuilder counts = new StringBuilder("Blocks: " + plan.blocks().size() + " (");
		boolean first = true;
		for (final Map.Entry<Role, Integer> entry : plan.countByRole().entrySet()) {
			counts.append(first ? "" : ", ").append(lower(entry.getKey().name()).replace('_', ' ')).append(' ').append(entry.getValue());
			first = false;
		}
		out.accept(Text.literal(counts.append(')').toString()));
		if (!check.ok()) {
			out.accept(Text.literal("Unknown blocks: " + String.join(", ", check.missing())).formatted(Formatting.RED));
		}
		final List<Violation> violations = pending.violations();
		if (violations.isEmpty()) {
			out.accept(Text.literal("Design check: OK").formatted(Formatting.GREEN));
		} else {
			out.accept(Text.literal("Design check: " + violations.size() + " problem(s), cannot build this design").formatted(Formatting.RED));
			for (int i = 0; i < Math.min(LISTED, violations.size()); i++) {
				out.accept(Text.literal("  " + violations.get(i).message()).formatted(Formatting.RED));
			}
		}
		site(pending.report(), out);
		if (violations.isEmpty() && check.ok() && !pending.report().hasHardIssue()) {
			out.accept(Text.literal(pending.report().buildable() ? "/ata_station confirm to build, /ata_station cancel to discard"
					: "/ata_station cancel to discard, or plan somewhere else").formatted(Formatting.GOLD));
		}
	}

	static void site(SurveyReport report, Consumer<Text> out) {
		if (report.counts().isEmpty()) {
			out.accept(Text.literal("Site check: OK, no terrain is changed").formatted(Formatting.GREEN));
			return;
		}
		final StringBuilder line = new StringBuilder("Site check:");
		for (final Map.Entry<SiteIssue.Kind, Integer> entry : report.counts().entrySet()) {
			line.append(' ').append(entry.getValue()).append(' ').append(lower(entry.getKey().name()).replace('_', ' ')).append(',');
		}
		line.setLength(line.length() - 1);
		out.accept(Text.literal(line.toString()).formatted(report.hasHardIssue() ? Formatting.RED : Formatting.YELLOW));
		for (int i = 0; i < Math.min(LISTED, report.issues().size()); i++) {
			final SiteIssue issue = report.issues().get(i);
			out.accept(Text.literal("  " + lower(issue.kind().name()).replace('_', ' ') + " at " + issue.x() + " " + issue.y() + " " + issue.z()
					+ ": " + issue.block()).formatted(Formatting.GRAY));
		}
		if (report.hasHardIssue()) {
			out.accept(Text.literal("Cannot build here: protected blocks, unloaded chunks, the world limit or a deep gap are in the way.")
					.formatted(Formatting.RED));
		} else if (!report.replaceTerrain()) {
			out.accept(Text.literal("/ata_station confirm " + StationCommand.REPLACE_TERRAIN + " approves " + report.terrainChanges()
					+ " terrain changes (undo restores them)").formatted(Formatting.YELLOW));
		}
	}

	private static void list(Consumer<Text> out, String label, List<String> items, Formatting colour) {
		if (!items.isEmpty()) {
			out.accept(Text.literal(label + String.join(", ", items)).formatted(colour));
		}
	}

	/** True when the planned rail node at (u, 0, v) is kept because a node is already there (the survey wrote no op for it). */
	private static boolean keptNode(StationSessions.Pending pending, int u, int v) {
		final LocalFrame frame = pending.frame();
		final int x = frame.x(u, v);
		final int y = frame.y(0);
		final int z = frame.z(u, v);
		return pending.report().ops().stream().noneMatch(op -> op.x() == x && op.y() == y && op.z() == z);
	}

	private static String corner(LocalFrame frame, int u, int v) {
		return "(" + frame.x(u, v) + " " + frame.oy() + " " + frame.z(u, v) + ")";
	}

	private static String lower(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
