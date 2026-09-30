package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.live.SpeakerBlockEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The client-side set of currently loaded speakers. Filled and emptied by Fabric's client block-entity load/unload
 * events (so unloaded chunks stop costing anything) and cleared on disconnect. Bounded by what the server sends.
 */
final class SpeakerRegistry {

	private final Set<SpeakerBlockEntity> speakers = Collections.newSetFromMap(new IdentityHashMap<>());

	void add(SpeakerBlockEntity speaker) {
		speakers.add(speaker);
	}

	void remove(SpeakerBlockEntity speaker) {
		speakers.remove(speaker);
	}

	void clear() {
		speakers.clear();
	}

	boolean isEmpty() {
		return speakers.isEmpty();
	}

	int size() {
		return speakers.size();
	}

	Iterable<SpeakerBlockEntity> all() {
		return speakers;
	}
}
