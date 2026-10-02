package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.live.LiveDebug;
import com.aureliatransit.architecture.live.SpeakerBlockEntity;
import com.aureliatransit.architecture.live.SpeakerConfig;
import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import com.aureliatransit.architecture.live.announce.AnnouncementDetector;
import com.aureliatransit.architecture.live.announce.AnnouncementEvent;
import com.aureliatransit.architecture.live.announce.AnnouncementQueue;
import com.aureliatransit.architecture.live.announce.AnnouncementText;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientStationSuffixes;
import com.aureliatransit.architecture.wayfinding.SuffixContext;
import com.aureliatransit.architecture.live.announce.SpeakerPicker;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationDataProvider;
import com.aureliatransit.architecture.transit.StationSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side announcement engine. About once a second it looks at the loaded speakers the player is within range of,
 * groups them by logical station, feeds each station's services to the {@link AnnouncementDetector}, queues the events
 * and plays at most one announcement at a time at the closest speaker.
 *
 * <p>It does nothing at all when no speaker is loaded, only resolves speakers whose radius contains the player, and
 * forgets everything when the player leaves the world. All structures are bounded.
 */
final class AnnouncementEngine {

	static final int UPDATE_INTERVAL_TICKS = 20;
	private static final int MAX_ORIGINS = 64;
	private static final long IDLE_RESET_MILLIS = 60_000;

	private static final class Group {
		long key;
		String name = "";
		final List<ServiceSnapshot> services = new ArrayList<>();
		final List<SpeakerPicker.Point> points = new ArrayList<>();
	}

	private final SpeakerRegistry registry;
	private final VoicePackManager voicePacks;
	private final AnnouncementDetector detector = new AnnouncementDetector();
	private final AnnouncementQueue queue = new AnnouncementQueue(16, 5 * 60_000L, 512, 4_000);
	private final AnnouncementPlayer player = new AnnouncementPlayer();
	private final Map<Long, Group> groups = new HashMap<>();
	private final Map<AnnouncementEvent, SpeakerBlockEntity> origins = new IdentityHashMap<>();
	private int tickCounter;
	private long lastActive;

	AnnouncementEngine(SpeakerRegistry registry, VoicePackManager voicePacks) {
		this.registry = registry;
		this.voicePacks = voicePacks;
	}

	void onTick(MinecraftClient client) {
		if (player.active()) {
			player.tick(client, System.currentTimeMillis());
		}
		if (++tickCounter < UPDATE_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;
		update(client, System.currentTimeMillis());
	}

	void reset() {
		detector.clear();
		queue.clear();
		origins.clear();
		groups.clear();
		player.stop();
	}

	private void update(MinecraftClient client, long now) {
		final ClientPlayerEntity self = client.player;
		final ClientWorld world = client.world;
		if (self == null || world == null) {
			reset();
			return;
		}
		if (registry.isEmpty()) {
			if (now - lastActive > IDLE_RESET_MILLIS) {
				reset();
			}
			return;
		}
		LiveDebug.count(LiveDebug.Counter.ENGINE_UPDATES);
		final Vec3d at = self.getPos();
		final StationDataProvider provider = StationData.provider();

		groups.clear();
		for (final SpeakerBlockEntity speaker : registry.all()) {
			if (speaker.isRemoved() || speaker.getWorld() != world) {
				continue;
			}
			final SpeakerConfig config = speaker.config();
			final BlockPos pos = speaker.getPos();
			final SpeakerPicker.Point point = new SpeakerPicker.Point(speaker, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, config.radius(), config.categories());
			// Only speakers the player can actually hear take part: out of range, nothing is resolved or played.
			if (!SpeakerPicker.inRange(point, at.x, at.y, at.z)) {
				continue;
			}
			final StationSnapshot snapshot = provider.resolve(pos, config.association(), true);
			if (!snapshot.hasStation() && snapshot.services().isEmpty()) {
				continue;
			}
			final long key = snapshot.station() != null ? snapshot.station().id() : (snapshot.platforms().isEmpty() ? 0 : -snapshot.platforms().get(0).id());
			final Group group = groups.computeIfAbsent(key, k -> new Group());
			group.key = key;
			if (snapshot.station() != null) {
				group.name = snapshot.station().displayName();
			}
			merge(group.services, snapshot.services());
			group.points.add(point);
		}

		if (!groups.isEmpty()) {
			lastActive = now;
		}
		for (final Group group : groups.values()) {
			for (final AnnouncementEvent event : detector.update(group.key, group.name, group.services, now)) {
				LiveDebug.count(LiveDebug.Counter.EVENTS_DETECTED);
				final SpeakerPicker.Point voice = SpeakerPicker.closest(group.points, at.x, at.y, at.z, event.category());
				if (voice != null && queue.offer(event, now)) {
					if (origins.size() >= MAX_ORIGINS) {
						origins.clear();
					}
					origins.put(event, (SpeakerBlockEntity) voice.handle());
				} else {
					LiveDebug.count(LiveDebug.Counter.EVENTS_DROPPED);
				}
			}
		}

		if (!player.busy(now)) {
			playNext(client, at, now);
		}
	}

	private static void merge(List<ServiceSnapshot> into, List<ServiceSnapshot> more) {
		for (final ServiceSnapshot candidate : more) {
			boolean present = false;
			for (final ServiceSnapshot existing : into) {
				if (existing.platformId() == candidate.platformId() && existing.routeId() == candidate.routeId() && existing.arrivalMillis() == candidate.arrivalMillis()) {
					present = true;
					break;
				}
			}
			if (!present) {
				into.add(candidate);
			}
		}
	}

	private void playNext(MinecraftClient client, Vec3d at, long now) {
		AnnouncementEvent event;
		while ((event = queue.poll(now)) != null) {
			final SpeakerBlockEntity speaker = origins.remove(event);
			if (speaker == null || speaker.isRemoved() || speaker.getWorld() != client.world) {
				LiveDebug.count(LiveDebug.Counter.EVENTS_DROPPED);
				continue;
			}
			final SpeakerConfig config = speaker.config();
			final BlockPos pos = speaker.getPos();
			final SpeakerPicker.Point point = new SpeakerPicker.Point(speaker, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, config.radius(), config.categories());
			// The player may have walked away while the event waited in the queue.
			if (!SpeakerPicker.inRange(point, at.x, at.y, at.z) || !event.category().enabledIn(config.categories())) {
				LiveDebug.count(LiveDebug.Counter.EVENTS_DROPPED);
				continue;
			}
			final float volume = volumeFor(config);
			player.start(client, AnnouncementText.build(event, name -> ClientStationSuffixes.apply(name, SuffixContext.ANNOUNCEMENTS)), voicePacks.stack(), point.x(), point.y(), point.z(), volume, now);
			queue.markPlayed(event, now);
			return;
		}
	}

	private static float volumeFor(SpeakerConfig config) {
		return config.volume() / 100F * Math.max(1F, config.radius() / 16F);
	}

	/**
	 * Plays a synthetic announcement of {@code category} at the player's position (used by {@code /aurelia_live test}).
	 */
	void playTest(MinecraftClient client, AnnouncementCategory category) {
		if (client.player == null) {
			return;
		}
		final long now = System.currentTimeMillis();
		final AnnouncementEvent event = new AnnouncementEvent(category, 0, "Test Central", 3, "3", 1, "Test Line", "T1", "Test Junction", 0,
				now + 30_000, 4, List.of("Alpha Street", "Beta Park", "Test Junction"), now, now + 60_000);
		final Vec3d pos = client.player.getEyePos();
		player.start(client, AnnouncementText.build(event, name -> ClientStationSuffixes.apply(name, SuffixContext.ANNOUNCEMENTS)), voicePacks.stack(), pos.x, pos.y, pos.z, 1F, now);
	}

	String describe() {
		return "speakers=" + registry.size() + ", queued=" + queue.size() + ", dropped=" + queue.droppedCount() + ", tracked trains=" + detector.trackedCount();
	}
}
