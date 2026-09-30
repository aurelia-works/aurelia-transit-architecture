package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.live.announce.VoicePack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads voice packs from every loaded resource pack: each {@code assets/<namespace>/aurelia_voice/*.json} is one pack
 * (see docs/VOICE_PACKS.md). The active {@link VoicePack.Stack} layers them by priority over the built-in chimes.
 * Reloads with F3+T / resource pack changes.
 */
final class VoicePackManager implements SimpleSynchronousResourceReloadListener {

	private static final String FOLDER = "aurelia_voice";
	private static final int MAX_PACKS = 32;

	private volatile VoicePack.Stack stack = new VoicePack.Stack(List.of(VoicePack.builtin(AureliaTransitArchitecture.MOD_ID)));

	void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(this);
	}

	VoicePack.Stack stack() {
		return stack;
	}

	@Override
	public Identifier getFabricId() {
		return AureliaTransitArchitecture.id("voice_packs");
	}

	@Override
	public void reload(ResourceManager manager) {
		final List<VoicePack> packs = new ArrayList<>();
		packs.add(VoicePack.builtin(AureliaTransitArchitecture.MOD_ID));
		for (final Map.Entry<Identifier, Resource> entry : manager.findResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
			if (packs.size() > MAX_PACKS) {
				break;
			}
			try (Reader reader = entry.getValue().getReader()) {
				final JsonElement root = JsonParser.parseReader(reader);
				if (root.isJsonObject()) {
					final JsonObject object = root.getAsJsonObject();
					final VoicePack pack = VoicePack.parse(entry.getKey().toString(), object);
					packs.add(pack);
					AureliaTransitArchitecture.LOGGER.info("Loaded voice pack {} (priority {}, {} fragments)", pack.id(), pack.priority(), pack.size());
				}
			} catch (Exception e) {
				AureliaTransitArchitecture.LOGGER.warn("Skipping unreadable voice pack {}: {}", entry.getKey(), e.toString());
			}
		}
		stack = new VoicePack.Stack(packs);
	}
}
