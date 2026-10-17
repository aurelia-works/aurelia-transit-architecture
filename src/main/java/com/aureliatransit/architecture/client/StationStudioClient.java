package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.studio.StudioServer;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Hosts Station Studio for this player: a local web page (on 127.0.0.1 only) with the station generator's controls, a 3D
 * preview drawn from the game's own block models and textures, and buttons that plan, build, cancel or undo in the world as
 * this player. Opened from the station wrench screen or with {@code /ata_studio}; stopped when the game closes.
 */
public final class StationStudioClient {

	private static final Map<String, StationWrenchItem.Action> ACTIONS = Map.of("plan", StationWrenchItem.Action.PLAN, "build",
			StationWrenchItem.Action.CONFIRM, "cancel", StationWrenchItem.Action.CANCEL, "undo", StationWrenchItem.Action.UNDO, "save",
			StationWrenchItem.Action.SAVE);

	private static StudioServer server;

	private StationStudioClient() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("ata_studio").executes(context -> {
					open();
					return 1;
				})));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
	}

	/** Starts the studio if needed and opens it in the browser; says where it is in chat too, in case no browser opens. */
	public static void open() {
		final MinecraftClient client = MinecraftClient.getInstance();
		final String url;
		try {
			url = start().url();
		} catch (IOException | RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not start Station Studio", e);
			if (client.player != null) {
				client.player.sendMessage(Text.literal("Station Studio could not start: " + e.getMessage()).formatted(Formatting.RED), false);
			}
			return;
		}
		Util.getOperatingSystem().open(url);
		if (client.player != null) {
			client.player.sendMessage(Text.literal("Station Studio is open in your browser: ").formatted(Formatting.GRAY)
					.append(Text.literal(url).setStyle(Style.EMPTY.withColor(Formatting.AQUA).withUnderline(true)
							.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)))), false);
		}
	}

	/** Starts the studio without opening a browser (the dev screenshot hook); failures only go to the log. */
	static void startQuietly() {
		try {
			start();
		} catch (IOException | RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not start Station Studio", e);
		}
	}

	private static synchronized StudioServer start() throws IOException {
		if (server == null) {
			server = StudioServer.start(StationStudioClient::asset, StationStudioClient::send);
			AureliaTransitArchitecture.LOGGER.info("Station Studio at {}", server.url());
		}
		return server;
	}

	private static synchronized void stop() {
		if (server != null) {
			server.stop();
			server = null;
		}
	}

	/** Reads an asset from the game's loaded resources, so resource packs show in the studio as they do in the world. */
	private static byte[] asset(String namespace, String path) throws IOException {
		final Identifier id;
		try {
			id = new Identifier(namespace, path);
		} catch (InvalidIdentifierException e) {
			return null;
		}
		final Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().getInputStream()) {
			return in.readAllBytes();
		}
	}

	/** Runs on the studio's web thread: hands the design to the game thread, which sends it to the server as this player. */
	private static String send(String action, String description, String encoded) {
		final StationWrenchItem.Action wanted = ACTIONS.get(action);
		if (wanted == null) {
			return "Unknown action.";
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null) {
			return "Join a world first, then try again.";
		}
		client.execute(() -> {
			if (client.player != null && ClientPlayNetworking.canSend(ModPackets.STATION_STUDIO)) {
				ClientPlayNetworking.send(ModPackets.STATION_STUDIO, ModPackets.writeStationStudio(wanted, description, encoded));
			} else if (client.player != null) {
				client.player.sendMessage(Text.literal("This server does not have Aurelia Transit Architecture's Station Studio.")
						.formatted(Formatting.RED), false);
			}
		});
		return switch (wanted) {
			case PLAN -> "Sent. The plan appears in front of you, facing the way you look; chat has the details.";
			case CONFIRM -> "Sent. Building starts if the plan passed its checks; see chat.";
			case UNDO -> "Sent. Undoing your last build.";
			case CANCEL -> "Sent. The pending plan is cancelled.";
			default -> "Saved on your station wrench.";
		};
	}
}
