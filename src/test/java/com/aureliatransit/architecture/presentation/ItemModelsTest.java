package com.aureliatransit.architecture.presentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every plain item (an {@code item.} lang key) has an item model and its texture, so none shows as the missing-texture cube. */
class ItemModelsTest {

	private static final String ASSETS = "/assets/aurelia_transit_architecture/";

	private static String read(String path) throws IOException {
		try (InputStream in = ItemModelsTest.class.getResourceAsStream(path)) {
			assertNotNull(in, "missing " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void everyItemHasAModelAndTexture() throws IOException {
		final Matcher items = Pattern.compile("\"item\\.aurelia_transit_architecture\\.([a-z0-9_]+)\"").matcher(read(ASSETS + "lang/en_us.json"));
		int count = 0;
		while (items.find()) {
			count++;
			final String model = read(ASSETS + "models/item/" + items.group(1) + ".json");
			final Matcher texture = Pattern.compile("\"aurelia_transit_architecture:(item/[a-z0-9_/]+)\"").matcher(model);
			while (texture.find()) {
				assertNotNull(ItemModelsTest.class.getResource(ASSETS + "textures/" + texture.group(1) + ".png"), "missing texture " + texture.group(1));
			}
		}
		assertTrue(count >= 2, "found " + count + " items");
	}
}
