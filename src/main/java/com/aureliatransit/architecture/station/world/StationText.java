package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.CanopyShape;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.SiteIssue;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.SurveyReport;
import com.aureliatransit.architecture.station.TrackLayout;
import com.aureliatransit.architecture.station.Violation;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Chat lines of the station generator: the short plan summary with clickable buttons (what most players read), the full
 * details (what was understood, the footprint, rail nodes, block counts and both checks) and the site report.
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
				}) + (spec.hub() ? ", hub" : spec.terminus() ? ", terminus" : "") + (spec.stepFree() ? ", step-free" : "") + (spec.name().isEmpty() ? "" : ", \"" + spec.name() + "\"");
	}

	/** A clickable chat button that runs {@code command} (an operator's own /ata_station command). */
	static MutableText button(String label, String command, Formatting colour, String hover) {
		return Text.literal("[" + label + "]").styled(style -> style.withColor(colour).withBold(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
	}

	/** "2 tracks, side platforms, 64 long, arched train shed, clinker brick hall (after Hamburg), terminus, "Alpha"". */
	static String headline(StationSpec spec) {
		final String roof = switch (spec.roof()) {
			case NONE -> "no roof";
			case CANOPY -> spec.canopy() == CanopyShape.FLAT ? "canopies" : spec.canopy().label() + " canopies";
			case HALL -> spec.shed().label();
		};
		return spec.trackCount() + (spec.trackCount() == 1 ? " track, " : " tracks, ") + lower(spec.platformLayout().name()) + " platforms, "
				+ spec.platformLength() + " long, " + roof + (spec.hasBuilding() ? ", " + spec.building().label() : "")
				+ (spec.hub() ? ", hub" : spec.terminus() ? ", terminus" : "") + (spec.name().isEmpty() ? "" : ", \"" + spec.name() + "\"");
	}

	/**
	 * The plan in a few lines: what it is, whether it can be built here and why not, and buttons to build, clear the ground
	 * and build, cancel, or show the details.
	 */
	static void shortSummary(StationSessions.Pending pending, BlockStateResolver.Check check, Consumer<Text> out) {
		final ParseResult parse = pending.parse();
		out.accept(Text.literal("[ATA Station] ").formatted(Formatting.GOLD).append(Text.literal(headline(pending.spec())).formatted(Formatting.WHITE)));
		for (final String line : parse.understood()) {
			if (line.startsWith("mix: ")) {
				out.accept(Text.literal("  " + line.substring(5)).formatted(Formatting.GRAY));
			}
		}
		for (final String warning : parse.warnings()) {
			out.accept(Text.literal("  " + warning).formatted(Formatting.YELLOW));
		}
		final MutableText details = button("Details", "/ata_station details", Formatting.GRAY, "Everything about this plan and its checks");
		final MutableText cancel = button("Cancel", "/ata_station cancel", Formatting.RED, "Throw this plan away");
		final String problem = problem(pending, check);
		if (problem != null) {
			out.accept(Text.literal("Can't build this here: " + problem).formatted(Formatting.RED));
			out.accept(cancel.append(Text.literal(" ")).append(details));
			return;
		}
		final int blocks = pending.plan().blocks().size();
		if (!pending.report().buildable()) {
			out.accept(Text.literal("Ground, plants or blocks are in the way (" + pending.report().terrainChanges()
					+ " cells). Undo puts them back.").formatted(Formatting.YELLOW));
			out.accept(button("Clear and build", "/ata_station confirm " + StationCommand.REPLACE_TERRAIN, Formatting.GOLD,
					"Replaces what is in the way, then builds " + blocks + " blocks").append(Text.literal(" ")).append(cancel)
					.append(Text.literal(" ")).append(details));
			return;
		}
		out.accept(Text.literal("Ready: " + blocks + " blocks. The outline shows where it goes.").formatted(Formatting.GREEN));
		out.accept(button("Build it", "/ata_station confirm", Formatting.GREEN, "Build " + blocks + " blocks (undo takes it away again)")
				.append(Text.literal(" ")).append(cancel).append(Text.literal(" ")).append(details));
	}

	/** Why the plan cannot be built at all, in one line, or null. */
	private static String problem(StationSessions.Pending pending, BlockStateResolver.Check check) {
		if (!check.ok()) {
			return "these blocks are missing: " + String.join(", ", check.missing());
		}
		if (!pending.violations().isEmpty()) {
			return pending.violations().get(0).message();
		}
		for (final StationAudit.Check audit : pending.audit().problems()) {
			if (audit.status() == StationAudit.Status.FAIL) {
				return audit.message();
			}
		}
		if (pending.report().hasHardIssue()) {
			return "protected blocks, unloaded chunks, the world limit or a deep gap are in the way. Try another spot.";
		}
		return null;
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
			} else if (keptNode(pending, track.u(), layout.platformFrom(track)) && keptNode(pending, track.u(), layout.platformTo(track))) {
				what = "uses the rail nodes already at " + corner(frame, track.u(), layout.platformFrom(track)) + " and "
						+ corner(frame, track.u(), layout.platformTo(track));
			} else {
				what = "rail nodes at " + corner(frame, track.u(), layout.platformFrom(track)) + " and " + corner(frame, track.u(), layout.platformTo(track))
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
		if (violations.isEmpty()) {
			checks(pending.audit(), out);
		}
		site(pending.report(), out);
		if (violations.isEmpty() && pending.audit().buildable() && check.ok() && !pending.report().hasHardIssue()) {
			out.accept(Text.literal(pending.report().buildable() ? "/ata_station confirm to build, /ata_station cancel to discard"
					: "/ata_station cancel to discard, or plan somewhere else").formatted(Formatting.GOLD));
		}
	}

	/** One line of what was measured, then every warning and failure of the station checks. */
	static void checks(StationAudit.Result audit, Consumer<Text> out) {
		final long passed = audit.count(StationAudit.Status.PASS);
		final long warned = audit.count(StationAudit.Status.WARN);
		final long failed = audit.count(StationAudit.Status.FAIL);
		final StringBuilder line = new StringBuilder("Station checks: " + passed + " passed");
		if (warned > 0) {
			line.append(", ").append(warned).append(warned == 1 ? " warning" : " warnings");
		}
		if (failed > 0) {
			line.append(", ").append(failed).append(" failed, cannot build");
		}
		line.append(" (measured ").append(audit.faces().size()).append(audit.faces().size() == 1 ? " boarding face" : " boarding faces");
		if (!audit.faces().isEmpty()) {
			line.append(" of ").append(audit.faces().get(0).length()).append(" blocks");
		}
		line.append(", ").append(audit.width()).append(" x ").append(audit.length()).append(" x ").append(audit.height()).append(')');
		out.accept(Text.literal(line.toString()).formatted(failed > 0 ? Formatting.RED : warned > 0 ? Formatting.YELLOW : Formatting.GREEN));
		final List<StationAudit.Check> problems = audit.problems();
		for (int i = 0; i < Math.min(LISTED, problems.size()); i++) {
			final StationAudit.Check problem = problems.get(i);
			out.accept(Text.literal("  " + problem.message()).formatted(problem.status() == StationAudit.Status.FAIL ? Formatting.RED : Formatting.YELLOW));
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
