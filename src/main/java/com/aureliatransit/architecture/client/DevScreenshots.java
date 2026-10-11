package com.aureliatransit.architecture.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev runs only (the {@code ata.devScreenshots} system property, set by the runClient task): takes the game's own F2
 * screenshot for every file dropped into {@code <run dir>/screenshot-requests/}, so a script can capture the view while the
 * window stays in the background. The request's file name names the screenshot ({@code screenshots/<name>.png}); a request
 * containing {@code nohud} hides the HUD and chat for that shot, and {@code fov=<degrees>} sets the field of view for it.
 * Both are restored afterwards. Inert in a normal game.
 */
final class DevScreenshots {

	/** Ticks before a shot that changes the HUD or field of view, so at least one frame renders with the change. */
	private static final int HUD_SETTLE_TICKS = 3;
	private static final Pattern FOV = Pattern.compile("fov=(\\d+)");

	private static String pending;
	private static boolean restoreHud;
	private static Integer restoreFov;
	private static int wait;

	private DevScreenshots() {
	}

	static void init() {
		if (!Boolean.getBoolean("ata.devScreenshots")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(DevScreenshots::tick);
	}

	private static void tick(MinecraftClient client) {
		if (pending != null) {
			if (--wait > 0) {
				return;
			}
			ScreenshotRecorder.saveScreenshot(client.runDirectory, pending + ".png", client.getFramebuffer(), message -> {
			});
			if (restoreHud) {
				client.options.hudHidden = false;
			}
			if (restoreFov != null) {
				client.options.getFov().setValue(restoreFov);
				restoreFov = null;
			}
			pending = null;
			return;
		}
		if (client.world == null || client.player == null || client.getWindow() == null) {
			return;
		}
		final File[] requests = new File(client.runDirectory, "screenshot-requests").listFiles(File::isFile);
		if (requests == null || requests.length == 0) {
			return;
		}
		final File request = requests[0];
		String content = "";
		try {
			content = Files.readString(request.toPath(), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// an unreadable request still takes a plain shot
		}
		request.delete();
		pending = request.getName().replaceAll("[^A-Za-z0-9_.-]", "_").replaceAll("\\.png$", "");
		restoreHud = content.contains("nohud") && !client.options.hudHidden;
		if (restoreHud) {
			client.options.hudHidden = true;
		}
		final Matcher fov = FOV.matcher(content);
		if (fov.find()) {
			restoreFov = client.options.getFov().getValue();
			client.options.getFov().setValue(Math.max(30, Math.min(110, Integer.parseInt(fov.group(1)))));
		}
		wait = restoreHud || restoreFov != null ? HUD_SETTLE_TICKS : 1;
	}
}
