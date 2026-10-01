package com.aureliatransit.architecture.wayfinding;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Operator-only (permission level 2) management of service messages. Any number can be active at once (at most
 * {@value ServiceMessages#MAX_MESSAGES}); each has a stable id shown by {@code list}.
 * <pre>
 * /ata_message add network &lt;info|notice|disruption|severe&gt; &lt;text...&gt;
 * /ata_message add station &lt;name&gt; &lt;severity&gt; &lt;text...&gt;       (quote names with spaces)
 * /ata_message remove &lt;id&gt;
 * /ata_message clear [network | station &lt;name&gt;]                 (no argument clears everything)
 * /ata_message list
 * /ata_message network set &lt;severity&gt; &lt;text...&gt;                 (1.2 form: replaces all network messages with one)
 * /ata_message network clear
 * /ata_message station &lt;name&gt; set &lt;severity&gt; &lt;text...&gt;         (1.2 form: replaces that station's messages)
 * /ata_message station &lt;name&gt; clear
 * </pre>
 * A display's own message is typed into the display's config screen. Text is sanitised and capped at
 * {@value ServiceMessage#MAX_TEXT} characters.
 */
final class ServiceMessageCommand {

	private ServiceMessageCommand() {
	}

	private interface Action {
		int run(CommandContext<ServerCommandSource> context, MessageSeverity severity);
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		final var add = CommandManager.literal("add");
		final var addNetwork = CommandManager.literal("network");
		final var addStation = CommandManager.argument("name", StringArgumentType.string());
		final var legacyNetwork = CommandManager.literal("set");
		final var legacyStation = CommandManager.literal("set");
		for (final MessageSeverity severity : MessageSeverity.values()) {
			addNetwork.then(severityNode(severity, (c, s) -> addMessage(c, MessageScope.NETWORK, "", s)));
			addStation.then(severityNode(severity, (c, s) -> addMessage(c, MessageScope.STATION, StringArgumentType.getString(c, "name"), s)));
			legacyNetwork.then(severityNode(severity, (c, s) -> replace(c, MessageScope.NETWORK, "", s)));
			legacyStation.then(severityNode(severity, (c, s) -> replace(c, MessageScope.STATION, StringArgumentType.getString(c, "name"), s)));
		}
		add.then(addNetwork).then(CommandManager.literal("station").then(addStation));

		final var network = CommandManager.literal("network").then(legacyNetwork)
				.then(CommandManager.literal("clear").executes(context -> clear(context, MessageScope.NETWORK, "")));
		final var station = CommandManager.argument("name", StringArgumentType.string()).then(legacyStation)
				.then(CommandManager.literal("clear").executes(context -> clear(context, MessageScope.STATION, StringArgumentType.getString(context, "name"))));

		dispatcher.register(CommandManager.literal("ata_message").requires(source -> source.hasPermissionLevel(2))
				.then(add)
				.then(CommandManager.literal("remove").then(CommandManager.argument("id", IntegerArgumentType.integer(1)).executes(ServiceMessageCommand::remove)))
				.then(CommandManager.literal("clear")
						.executes(context -> clear(context, null, ""))
						.then(CommandManager.literal("network").executes(context -> clear(context, MessageScope.NETWORK, "")))
						.then(CommandManager.literal("station").then(CommandManager.argument("name", StringArgumentType.string())
								.executes(context -> clear(context, MessageScope.STATION, StringArgumentType.getString(context, "name"))))))
				.then(network)
				.then(CommandManager.literal("station").then(station))
				.then(CommandManager.literal("list").executes(ServiceMessageCommand::list)));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> severityNode(MessageSeverity severity, Action action) {
		return CommandManager.literal(severity.id()).then(CommandManager.argument("text", StringArgumentType.greedyString())
				.executes(context -> action.run(context, severity)));
	}

	private static int store(CommandContext<ServerCommandSource> context, ServiceMessages next, String feedback) {
		final ServerCommandSource source = context.getSource();
		ServiceMessageState.get(source.getServer()).set(next);
		ServiceMessageSync.broadcast(source.getServer(), next);
		source.sendFeedback(() -> Text.literal(feedback), true);
		return 1;
	}

	private static int addMessage(CommandContext<ServerCommandSource> context, MessageScope scope, String station, MessageSeverity severity) {
		final ServiceMessages current = ServiceMessageState.get(context.getSource().getServer()).messages();
		final ServiceMessages next = current.add(scope, station, severity, StringArgumentType.getString(context, "text"));
		if (next == null) {
			context.getSource().sendError(Text.literal("Cannot add the message: the text or station name is empty, or " + ServiceMessages.MAX_MESSAGES + " messages are already active"));
			return 0;
		}
		return store(context, next, "Message #" + (next.nextId() - 1) + " added (" + severity.id() + ")");
	}

	/** 1.2 behaviour: the one message of a scope is replaced. */
	private static int replace(CommandContext<ServerCommandSource> context, MessageScope scope, String station, MessageSeverity severity) {
		final ServiceMessages current = ServiceMessageState.get(context.getSource().getServer()).messages();
		final ServiceMessages cleared = scope == MessageScope.NETWORK ? current.clearNetwork() : current.clearStation(station);
		final ServiceMessages next = cleared.add(scope, station, severity, StringArgumentType.getString(context, "text"));
		if (next == null) {
			context.getSource().sendError(Text.literal("Cannot set the message: the text or station name is empty"));
			return 0;
		}
		return store(context, next, "Message #" + (next.nextId() - 1) + " set (" + severity.id() + ")");
	}

	private static int remove(CommandContext<ServerCommandSource> context) {
		final int id = IntegerArgumentType.getInteger(context, "id");
		final ServiceMessages current = ServiceMessageState.get(context.getSource().getServer()).messages();
		if (current.byId(id) == null) {
			context.getSource().sendError(Text.literal("No message #" + id));
			return 0;
		}
		return store(context, current.remove(id), "Message #" + id + " removed");
	}

	private static int clear(CommandContext<ServerCommandSource> context, MessageScope scope, String station) {
		final ServiceMessages current = ServiceMessageState.get(context.getSource().getServer()).messages();
		final ServiceMessages next = scope == null ? current.clearAll()
				: scope == MessageScope.NETWORK ? current.clearNetwork() : current.clearStation(station);
		return store(context, next, scope == null ? "All service messages cleared"
				: scope == MessageScope.NETWORK ? "Network messages cleared" : "Messages for " + station + " cleared");
	}

	private static int list(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		final ServiceMessages messages = ServiceMessageState.get(source.getServer()).messages();
		if (messages.isEmpty()) {
			source.sendFeedback(() -> Text.literal("No service messages"), false);
			return 0;
		}
		for (final ServiceMessage m : messages.all()) {
			final String where = m.scope() == MessageScope.NETWORK ? "Network" : m.station();
			source.sendFeedback(() -> Text.literal("#" + m.id() + " " + where + " [" + m.severity().id() + "]: " + m.text()), false);
		}
		return messages.size();
	}
}
