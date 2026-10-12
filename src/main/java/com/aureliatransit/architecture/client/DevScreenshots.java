package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.station.StationRecipe;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

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
 * Both are restored afterwards. {@code open=wrench} opens the station wrench screen for the wrench in the main hand, and
 * {@code open=book page=<n>} the written book in the main hand at that page; the screen is closed after the shot. Without a
 * wrench in hand, {@code open=wrench} opens the screen anyway with {@code recipe=<encoded choices>} (or the defaults), for its
 * live preview; its actions then do nothing. {@code wait=<ticks>} waits longer before the shot (the preview plans and meshes in
 * the background). {@code use}
 * right-clicks the block under the crosshair with the main-hand item first, like a player would, and any screen that opens is
 * in the shot, then closed. {@code studio} starts Station Studio without opening a browser (its address is in the log), so a
 * script can drive it. Inert in a normal game.
 */
final class DevScreenshots {

	/** Ticks before a shot that changes the HUD or field of view, so at least one frame renders with the change. */
	private static final int HUD_SETTLE_TICKS = 3;
	/** Ticks before a shot of a screen opened for it, so its widgets and textures are laid out and drawn. */
	private static final int SCREEN_SETTLE_TICKS = 10;
	private static final Pattern FOV = Pattern.compile("fov=(\\d+)");
	private static final Pattern PAGE = Pattern.compile("page=(\\d+)");
	private static final Pattern RECIPE = Pattern.compile("recipe=(\\S+)");
	private static final Pattern WAIT = Pattern.compile("wait=(\\d+)");

	private static String pending;
	private static boolean restoreHud;
	private static Integer restoreFov;
	private static boolean closeScreen;
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
			if (closeScreen) {
				client.setScreen(null);
				closeScreen = false;
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
		if (content.contains("use") && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
			closeScreen = true;
			wait = SCREEN_SETTLE_TICKS;
			return;
		}
		if (content.contains("studio")) {
			StationStudioClient.startQuietly();
		}
		closeScreen = openScreen(client, content);
		wait = closeScreen ? SCREEN_SETTLE_TICKS : restoreHud || restoreFov != null ? HUD_SETTLE_TICKS : 1;
		final Matcher longer = WAIT.matcher(content);
		if (longer.find()) {
			wait = Math.max(wait, Math.min(400, Integer.parseInt(longer.group(1))));
		}
	}

	/** Opens the screen a request names; returns whether one was opened. */
	private static boolean openScreen(MinecraftClient client, String content) {
		if (content.contains("open=wrench")) {
			StationWrenchScreen.open(Hand.MAIN_HAND);
			if (client.currentScreen == null) {
				final Matcher recipe = RECIPE.matcher(content);
				client.setScreen(new StationWrenchScreen(Hand.MAIN_HAND, "",
						recipe.find() ? StationRecipe.decode(recipe.group(1)) : StationRecipe.defaults()));
			}
		} else if (content.contains("open=book")) {
			final ItemStack stack = client.player.getMainHandStack();
			if (!stack.isOf(Items.WRITTEN_BOOK)) {
				return false;
			}
			final BookScreen screen = new BookScreen(BookScreen.Contents.create(stack));
			client.setScreen(screen);
			final Matcher page = PAGE.matcher(content);
			if (page.find()) {
				screen.setPage(Integer.parseInt(page.group(1)) - 1);
			}
		} else {
			return false;
		}
		return client.currentScreen != null;
	}
}
