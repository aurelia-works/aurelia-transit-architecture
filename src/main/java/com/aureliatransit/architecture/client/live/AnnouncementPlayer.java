package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.live.LiveDebug;
import com.aureliatransit.architecture.live.announce.AnnouncementText;
import com.aureliatransit.architecture.live.announce.VoicePack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays one announcement at a time as a timed sequence of sound events (chime first). If the active voice packs cannot
 * speak every fragment, only the chime plays and the text goes to the action bar instead. All sounds use
 * {@link SoundCategory#VOICE}, so the player's Voice volume slider controls them.
 */
final class AnnouncementPlayer {

	private static final int GAP_MILLIS = 60;
	private static final boolean ALWAYS_SUBTITLE = Boolean.getBoolean("aurelia.live.subtitles");

	private final List<VoicePack.FragmentSound> steps = new ArrayList<>();
	private int next;
	private long nextAt;
	private long endAt;
	private double x;
	private double y;
	private double z;
	private float volume;

	boolean busy(long now) {
		return next < steps.size() || now < endAt;
	}

	boolean active() {
		return next < steps.size();
	}

	void stop() {
		steps.clear();
		next = 0;
		endAt = 0;
	}

	/**
	 * @param volume sound volume; values above 1 also widen the distance the sound can be heard from
	 */
	void start(MinecraftClient client, AnnouncementText.Announcement announcement, VoicePack.Stack stack, double x, double y, double z, float volume, long now) {
		stop();
		this.x = x;
		this.y = y;
		this.z = z;
		this.volume = volume;
		final VoicePack.FragmentSound chime = stack.find(announcement.chimeKey());
		if (chime != null) {
			steps.add(chime);
		}
		final List<VoicePack.FragmentSound> spoken = stack.resolveAll(announcement.fragments(), null);
		if (spoken != null) {
			steps.addAll(spoken);
		} else {
			LiveDebug.count(LiveDebug.Counter.ANNOUNCEMENTS_FALLBACK);
		}
		if ((spoken == null || ALWAYS_SUBTITLE) && client.inGameHud != null) {
			client.inGameHud.setOverlayMessage(Text.literal(announcement.subtitle()), false);
		}
		LiveDebug.count(LiveDebug.Counter.ANNOUNCEMENTS_PLAYED);
		next = 0;
		nextAt = now;
		endAt = now;
		tick(client, now);
	}

	void tick(MinecraftClient client, long now) {
		while (next < steps.size() && now >= nextAt) {
			final VoicePack.FragmentSound step = steps.get(next++);
			final Identifier id = Identifier.tryParse(step.soundId());
			if (id != null) {
				client.getSoundManager().play(new PositionedSoundInstance(SoundEvent.of(id), SoundCategory.VOICE, volume, 1.0F, Random.create(), x, y, z));
			}
			nextAt = now + step.durationMillis() + GAP_MILLIS;
			endAt = nextAt;
		}
		if (next >= steps.size()) {
			steps.clear();
			next = 0;
		}
	}
}
