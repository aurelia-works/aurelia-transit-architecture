package com.aureliatransit.architecture.live.announce;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A voice pack: fragment keys mapped to sound-event ids. Pure data + resolution logic (sound ids are plain strings
 * here; the client turns them into {@code Identifier}s). See docs/VOICE_PACKS.md for the JSON format.
 */
public final class VoicePack {

	public static final int DEFAULT_DURATION_MILLIS = 900;
	public static final int MAX_DURATION_MILLIS = 15_000;
	public static final int MAX_FRAGMENTS = 4096;

	/**
	 * @param soundId        sound event id as a string, e.g. "my_pack:phrase.calling_at"
	 * @param durationMillis how long to wait before the next fragment starts
	 */
	public record FragmentSound(String soundId, int durationMillis) {
	}

	private final String id;
	private final int priority;
	private final Map<String, FragmentSound> fragments;

	public VoicePack(String id, int priority, Map<String, FragmentSound> fragments) {
		this.id = id;
		this.priority = priority;
		this.fragments = Map.copyOf(fragments);
	}

	public String id() {
		return id;
	}

	public int priority() {
		return priority;
	}

	public int size() {
		return fragments.size();
	}

	public FragmentSound get(String key) {
		return fragments.get(key);
	}

	/**
	 * Parses a pack file. Malformed entries are skipped; the count is bounded. Never throws for bad content.
	 *
	 * <pre>{"priority": 10, "fragments": {"phrase.calling_at": "pack:calling_at", "number.3": {"sound": "pack:three", "duration_ms": 600}}}</pre>
	 */
	public static VoicePack parse(String id, JsonObject root) {
		final int priority = root.has("priority") && root.get("priority").isJsonPrimitive() && root.get("priority").getAsJsonPrimitive().isNumber()
				? root.get("priority").getAsInt() : 0;
		final Map<String, FragmentSound> map = new HashMap<>();
		if (root.has("fragments") && root.get("fragments").isJsonObject()) {
			for (final Map.Entry<String, JsonElement> entry : root.getAsJsonObject("fragments").entrySet()) {
				if (map.size() >= MAX_FRAGMENTS) {
					break;
				}
				final FragmentSound sound = parseFragment(entry.getValue());
				if (sound != null && !entry.getKey().isBlank()) {
					map.put(entry.getKey(), sound);
				}
			}
		}
		return new VoicePack(id, priority, map);
	}

	private static FragmentSound parseFragment(JsonElement element) {
		try {
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				return new FragmentSound(element.getAsString(), DEFAULT_DURATION_MILLIS);
			}
			if (element.isJsonObject()) {
				final JsonObject object = element.getAsJsonObject();
				if (!object.has("sound") || !object.get("sound").isJsonPrimitive()) {
					return null;
				}
				int duration = DEFAULT_DURATION_MILLIS;
				if (object.has("duration_ms") && object.get("duration_ms").isJsonPrimitive()) {
					duration = Math.max(50, Math.min(MAX_DURATION_MILLIS, object.get("duration_ms").getAsInt()));
				}
				return new FragmentSound(object.get("sound").getAsString(), duration);
			}
		} catch (RuntimeException ignored) {
			// malformed value: skip this fragment
		}
		return null;
	}

	/**
	 * The always-present built-in pack: the three original chimes and nothing else.
	 */
	public static VoicePack builtin(String namespace) {
		final Map<String, FragmentSound> map = new HashMap<>();
		map.put("chime.info", new FragmentSound(namespace + ":live.chime.info", 1500));
		map.put("chime.alert", new FragmentSound(namespace + ":live.chime.alert", 1100));
		map.put("chime.delay", new FragmentSound(namespace + ":live.chime.delay", 1600));
		return new VoicePack("builtin", Integer.MIN_VALUE, map);
	}

	/**
	 * An ordered stack of packs. Lookups try the highest priority pack first and fall through to lower ones, so a
	 * partial pack (only station names, say) can sit on top of a fuller one.
	 */
	public static final class Stack {

		private final List<VoicePack> packs;

		public Stack(List<VoicePack> packs) {
			final List<VoicePack> sorted = new ArrayList<>(packs);
			sorted.sort(Comparator.comparingInt(VoicePack::priority).reversed().thenComparing(VoicePack::id));
			this.packs = List.copyOf(sorted);
		}

		public FragmentSound find(String key) {
			for (final VoicePack pack : packs) {
				final FragmentSound sound = pack.get(key);
				if (sound != null) {
					return sound;
				}
			}
			return null;
		}

		/**
		 * Resolves the whole sentence. Returns null unless EVERY fragment has a sound, so half-spoken sentences never
		 * play; the caller then falls back to chime + subtitle. {@code missing} (optional) collects the absent keys.
		 */
		public List<FragmentSound> resolveAll(List<AnnouncementText.Fragment> fragments, List<String> missing) {
			final List<FragmentSound> sounds = new ArrayList<>(fragments.size());
			boolean complete = true;
			for (final AnnouncementText.Fragment fragment : fragments) {
				final FragmentSound sound = find(fragment.key());
				if (sound == null) {
					complete = false;
					if (missing != null) {
						missing.add(fragment.key());
					} else {
						return null;
					}
				} else {
					sounds.add(sound);
				}
			}
			return complete ? sounds : null;
		}

		public List<VoicePack> packs() {
			return packs;
		}
	}
}
