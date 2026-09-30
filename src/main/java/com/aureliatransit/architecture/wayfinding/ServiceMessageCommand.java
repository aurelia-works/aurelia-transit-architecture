package com.aureliatransit.architecture.wayfinding;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Operator-only (permission level 2) management of service messages:
 * <pre>
 * /ata_message network set &lt;info|warning|disruption&gt; &lt;text...&gt;
 * /ata_message network clear
 * /ata_message station &lt;name&gt; set &lt;info|warning|disruption&gt; &lt;text...&gt;   (quote names with spaces)
 * /ata_message station &lt;name&gt; clear
 * /ata_message list
 * </pre>
 * Text is sanitised and capped at {@value ServiceMessage#MAX_TEXT} characters; at most
 * {@value ServiceMessages#MAX_STATIONS} stations can hold a message.
 */
final class ServiceMessageCommand {

	private ServiceMessageCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		final LiteralArgumentBuilder<ServerCommandSource> network = CommandManager.literal("network");
		network.then(CommandManager.literal("clear").executes(context -> setNetwork(context, ServiceMessage.NONE)));
		final var networkSet = CommandManager.literal("set");
		for (final MessageSeverity severity : MessageSeverity.values()) {
			networkSet.then(CommandManager.literal(severity.id()).then(CommandManager.argument("text", StringArgumentType.greedyString())
					.executes(context -> setNetwork(context, new ServiceMessage(StringArgumentType.getString(context, "text"), severity)))));
		}
		network.then(networkSet);

		final var stationName = CommandManager.argument("name", StringArgumentType.string());
		stationName.then(CommandManager.literal("clear").executes(context -> setStation(context, ServiceMessage.NONE)));
		final var stationSet = CommandManager.literal("set");
		for (final MessageSeverity severity : MessageSeverity.values()) {
			stationSet.then(CommandManager.literal(severity.id()).then(CommandManager.argument("text", StringArgumentType.greedyString())
					.executes(context -> setStation(context, new ServiceMessage(StringArgumentType.getString(context, "text"), severity)))));
		}
		stationName.then(stationSet);

		dispatcher.register(CommandManager.literal("ata_message").requires(source -> source.hasPermissionLevel(2))
				.then(network)
				.then(CommandManager.literal("station").then(stationName))
				.then(CommandManager.literal("list").executes(ServiceMessageCommand::list)));
	}

	private static int setNetwork(CommandContext<ServerCommandSource> context, ServiceMessage message) {
		final ServerCommandSource source = context.getSource();
		final ServiceMessageState state = ServiceMessageState.get(source.getServer());
		state.set(state.messages().withNetwork(message));
		ServiceMessageSync.broadcast(source.getServer(), state.messages());
		source.sendFeedback(() -> Text.literal(message.isEmpty() ? "Network message cleared" : "Network message set (" + message.severity().id() + ")"), true);
		return 1;
	}

	private static int setStation(CommandContext<ServerCommandSource> context, ServiceMessage message) {
		final ServerCommandSource source = context.getSource();
		final String name = StringArgumentType.getString(context, "name");
		final ServiceMessageState state = ServiceMessageState.get(source.getServer());
		final ServiceMessages next = state.messages().withStation(name, message);
		if (next == null) {
			source.sendError(Text.literal("Cannot set a message for '" + name + "': the name is empty or " + ServiceMessages.MAX_STATIONS + " stations already have one"));
			return 0;
		}
		state.set(next);
		ServiceMessageSync.broadcast(source.getServer(), next);
		source.sendFeedback(() -> Text.literal(message.isEmpty() ? "Message for " + name + " cleared" : "Message for " + name + " set (" + message.severity().id() + ")"), true);
		return 1;
	}

	private static int list(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		final ServiceMessages messages = ServiceMessageState.get(source.getServer()).messages();
		if (messages.isEmpty()) {
			source.sendFeedback(() -> Text.literal("No service messages"), false);
			return 0;
		}
		if (!messages.network().isEmpty()) {
			source.sendFeedback(() -> Text.literal("Network [" + messages.network().severity().id() + "]: " + messages.network().text()), false);
		}
		for (final ServiceMessages.StationMessage entry : messages.stations()) {
			source.sendFeedback(() -> Text.literal(entry.station() + " [" + entry.message().severity().id() + "]: " + entry.message().text()), false);
		}
		return messages.stations().size() + (messages.network().isEmpty() ? 0 : 1);
	}
}
