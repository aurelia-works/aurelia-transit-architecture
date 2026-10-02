package com.aureliatransit.architecture.wayfinding;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Operator-only (permission level 2) management of station suffixes. A new suffix is shown on signs and terminals;
 * {@code show} turns each context on or off.
 * <pre>
 * /ata_suffix set &lt;station&gt; &lt;station|airport|port|terminal&gt;       (quote names with spaces)
 * /ata_suffix set &lt;station&gt; custom &lt;text...&gt;
 * /ata_suffix show &lt;station&gt; &lt;signs|displays|terminals|announcements&gt; &lt;true|false&gt;
 * /ata_suffix remove &lt;station&gt;
 * /ata_suffix list
 * </pre>
 * Re-setting a station keeps the contexts it already had. Custom text is sanitised and capped at
 * {@value StationSuffixes#MAX_SUFFIX} characters.
 */
final class StationSuffixCommand {

	private StationSuffixCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		final var set = CommandManager.argument("station", StringArgumentType.string());
		for (final StationSuffixes.Kind kind : StationSuffixes.Kind.values()) {
			if (kind != StationSuffixes.Kind.CUSTOM) {
				set.then(CommandManager.literal(kind.id()).executes(context -> set(context, kind, "")));
			}
		}
		set.then(CommandManager.literal("custom").then(CommandManager.argument("text", StringArgumentType.greedyString())
				.executes(context -> set(context, StationSuffixes.Kind.CUSTOM, StringArgumentType.getString(context, "text")))));

		final var show = CommandManager.argument("station", StringArgumentType.string());
		for (final SuffixContext suffixContext : SuffixContext.values()) {
			show.then(CommandManager.literal(suffixContext.id()).then(CommandManager.argument("on", BoolArgumentType.bool())
					.executes(context -> show(context, suffixContext))));
		}

		dispatcher.register(CommandManager.literal("ata_suffix").requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("set").then(set))
				.then(CommandManager.literal("show").then(show))
				.then(CommandManager.literal("remove").then(CommandManager.argument("station", StringArgumentType.string()).executes(StationSuffixCommand::remove)))
				.then(CommandManager.literal("list").executes(StationSuffixCommand::list)));
	}

	private static int store(CommandContext<ServerCommandSource> context, StationSuffixes next, String feedback) {
		final ServerCommandSource source = context.getSource();
		StationSuffixState.get(source.getServer()).set(next);
		StationSuffixSync.broadcast(source.getServer(), next);
		source.sendFeedback(() -> Text.literal(feedback), true);
		return 1;
	}

	private static int set(CommandContext<ServerCommandSource> context, StationSuffixes.Kind kind, String text) {
		final String station = StringArgumentType.getString(context, "station");
		final StationSuffixes current = StationSuffixState.get(context.getSource().getServer()).suffixes();
		final StationSuffixes.Entry existing = current.find(station);
		final StationSuffixes next = current.set(station, kind, text, existing == null ? SuffixContext.DEFAULT_MASK : existing.contexts());
		if (next == null) {
			context.getSource().sendError(Text.literal("Cannot set the suffix: the station name or suffix is empty, or " + StationSuffixes.MAX_ENTRIES + " suffixes already exist"));
			return 0;
		}
		return store(context, next, "Suffix for " + station + " set to " + next.find(station).suffix());
	}

	private static int show(CommandContext<ServerCommandSource> context, SuffixContext suffixContext) {
		final String station = StringArgumentType.getString(context, "station");
		final boolean on = BoolArgumentType.getBool(context, "on");
		final StationSuffixes next = StationSuffixState.get(context.getSource().getServer()).suffixes().withContext(station, suffixContext, on);
		if (next == null) {
			context.getSource().sendError(Text.literal("No suffix for " + station));
			return 0;
		}
		return store(context, next, "Suffix for " + station + " " + (on ? "shown" : "hidden") + " on " + suffixContext.id());
	}

	private static int remove(CommandContext<ServerCommandSource> context) {
		final String station = StringArgumentType.getString(context, "station");
		final StationSuffixes current = StationSuffixState.get(context.getSource().getServer()).suffixes();
		if (current.find(station) == null) {
			context.getSource().sendError(Text.literal("No suffix for " + station));
			return 0;
		}
		return store(context, current.remove(station), "Suffix for " + station + " removed");
	}

	private static int list(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		final StationSuffixes suffixes = StationSuffixState.get(source.getServer()).suffixes();
		if (suffixes.isEmpty()) {
			source.sendFeedback(() -> Text.literal("No station suffixes"), false);
			return 0;
		}
		for (final StationSuffixes.Entry entry : suffixes.all()) {
			final StringBuilder on = new StringBuilder();
			for (final SuffixContext suffixContext : SuffixContext.values()) {
				if (entry.shows(suffixContext)) {
					on.append(on.length() == 0 ? "" : ", ").append(suffixContext.id());
				}
			}
			source.sendFeedback(() -> Text.literal(entry.station() + ": " + entry.suffix() + " [" + (on.length() == 0 ? "none" : on) + "]"), false);
		}
		return suffixes.all().size();
	}
}
