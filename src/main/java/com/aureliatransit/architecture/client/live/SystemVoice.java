package com.aureliatransit.architecture.client.live;

import com.mojang.text2speech.Narrator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

/**
 * The computer's own text-to-speech (Windows SAPI, macOS speech, flite on Linux), through the library Minecraft's
 * narrator uses. It says the sentences a voice pack cannot: a pack holds recorded phrases and numbers, never the
 * player's own station names, so "This is Coventry, platform 3" came out as just "This is" (10-20). Created once, on
 * first use; where the system has no voice it stays silent and the recorded fragments play as before.
 */
final class SystemVoice {

	private static Narrator narrator;
	private static boolean tried;

	private SystemVoice() {
	}

	/** Whether there is a voice to speak with and the player's Voice volume is not off. */
	static boolean available(MinecraftClient client) {
		if (client.options != null && client.options.getSoundVolume(SoundCategory.VOICE) <= 0F) {
			return false;
		}
		return narrator() != null;
	}

	/** Speaks {@code text}, cutting off whatever this voice was still saying. */
	static void say(String text) {
		final Narrator voice = narrator();
		if (voice != null && text != null && !text.isBlank()) {
			voice.say(text, true);
		}
	}

	/** Roughly how long {@code text} takes to say, so speakers do not talk over it (about 2.6 words a second). */
	static long durationMillis(String text) {
		final int words = text == null || text.isBlank() ? 0 : text.trim().split("\\s+").length;
		return 600L + words * 380L;
	}

	private static Narrator narrator() {
		if (!tried) {
			tried = true;
			try {
				final Narrator made = Narrator.getNarrator();
				narrator = made != null && made.active() ? made : null;
			} catch (Throwable e) {
				narrator = null;
			}
		}
		return narrator;
	}
}
