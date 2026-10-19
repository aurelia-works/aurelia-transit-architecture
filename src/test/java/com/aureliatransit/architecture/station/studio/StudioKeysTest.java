package com.aureliatransit.architecture.station.studio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Walk mode listens to the player's own Minecraft keys, in the page's names. */
class StudioKeysTest {

	@AfterEach
	void reset() {
		StudioKeys.source(() -> StudioKeys.DEFAULTS);
	}

	@Test
	void minecraftKeyNamesBecomePageKeyCodes() {
		assertEquals("KeyW", StudioKeys.domCode("key.keyboard.w"));
		assertEquals("Digit3", StudioKeys.domCode("key.keyboard.3"));
		assertEquals("Space", StudioKeys.domCode("key.keyboard.space"));
		assertEquals("ShiftLeft", StudioKeys.domCode("key.keyboard.left.shift"));
		assertEquals("ControlLeft", StudioKeys.domCode("key.keyboard.left.control"));
		assertEquals("ArrowUp", StudioKeys.domCode("key.keyboard.up"));
		assertNull(StudioKeys.domCode("key.mouse.left"));
		assertNull(StudioKeys.domCode("key.keyboard.f13"));
		assertNull(StudioKeys.domCode(null));
	}

	@Test
	void outsideTheGameTheDefaultsAreMinecrafts() {
		for (final String action : StudioKeys.DEFAULTS.keySet()) {
			assertTrue(StudioKeys.json().contains("\"" + action + "\":\"" + StudioKeys.DEFAULTS.get(action) + "\""), action);
		}
	}

	@Test
	void aPlayersOwnBindingsWinAndUnreadableOnesKeepTheDefault() {
		StudioKeys.source(() -> Map.of("forward", "key.keyboard.up", "jump", "key.keyboard.right.shift", "sprint", "key.mouse.4", "other", "key.keyboard.q"));
		final String json = StudioKeys.json();
		assertTrue(json.contains("\"forward\":\"ArrowUp\""), json);
		assertTrue(json.contains("\"jump\":\"ShiftRight\""), json);
		assertTrue(json.contains("\"sprint\":\"ControlLeft\""), json);
		assertTrue(json.contains("\"back\":\"KeyS\""), json);
		assertTrue(!json.contains("other"), json);
	}
}
