package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import com.aureliatransit.architecture.live.announce.AnnouncementEvent;
import com.aureliatransit.architecture.live.announce.AnnouncementText;
import com.aureliatransit.architecture.live.announce.FragmentKeys;
import com.aureliatransit.architecture.live.announce.SpeakerPicker;
import com.aureliatransit.architecture.live.announce.VoicePack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.aureliatransit.architecture.live.TestData.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAndTextTest {

	private static AnnouncementEvent event(AnnouncementCategory category, List<String> stops) {
		return new AnnouncementEvent(category, 1, "Central", 3, "3", 7, "Red Line", "R1", "Zürich Hbf", 1, NOW, 4, stops, NOW, NOW + 60_000);
	}

	@Test
	void normalisationIsStable() {
		assertEquals("zurich_hbf_main", FragmentKeys.normalise("Zürich Hbf (Main)"));
		assertEquals("red_line", FragmentKeys.normalise("  Red   Line "));
		assertEquals("a_b", FragmentKeys.normalise("a--b"));
		assertEquals("", FragmentKeys.normalise(null));
		assertEquals("number.3", FragmentKeys.platform("3"));
		assertEquals("platform.2a", FragmentKeys.platform("2a"));
		assertEquals("number.12", FragmentKeys.number(12));
		assertNull(FragmentKeys.number(100));
	}

	@Test
	void approachingSentenceHasSubtitleAndFragmentsInOrder() {
		final AnnouncementText.Announcement a = AnnouncementText.build(event(AnnouncementCategory.APPROACHING, List.of("Alpha", "Beta", "Zürich Hbf")));
		assertEquals("The train approaching platform 3 is the Red Line service to Zürich Hbf. Calling at Alpha, Beta and Zürich Hbf.", a.subtitle());
		final List<String> keys = a.fragments().stream().map(AnnouncementText.Fragment::key).toList();
		assertEquals(List.of("phrase.train_approaching_platform", "number.3", "phrase.is_the", "route.red_line", "phrase.service_to", "station.zurich_hbf",
				"phrase.calling_at", "station.alpha", "station.beta", "phrase.and", "station.zurich_hbf"), keys);
		assertEquals("chime.info", a.chimeKey());
	}

	@Test
	void suffixChangesTheTextButNotTheVoiceKeys() {
		final AnnouncementText.Announcement plain = AnnouncementText.build(event(AnnouncementCategory.APPROACHING, List.of("Alpha", "Beta")));
		final AnnouncementText.Announcement suffixed = AnnouncementText.build(event(AnnouncementCategory.APPROACHING, List.of("Alpha", "Beta")),
				name -> name.equals("Beta") ? "Beta Airport" : name);
		assertTrue(suffixed.subtitle().contains("Beta Airport"), suffixed.subtitle());
		assertEquals(plain.fragments().stream().map(AnnouncementText.Fragment::key).toList(), suffixed.fragments().stream().map(AnnouncementText.Fragment::key).toList());
	}

	@Test
	void callingAtIsBoundedAndEndsOnTheFinalStop() {
		final List<String> many = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			many.add("Stop " + i);
		}
		final AnnouncementText.Announcement a = AnnouncementText.build(event(AnnouncementCategory.STANDING, many));
		assertTrue(a.fragments().stream().filter(f -> f.key().startsWith("station.stop_")).count() <= AnnouncementText.MAX_STOPS);
		assertTrue(a.subtitle().contains("and Stop 11"));
	}

	@Test
	void everyCategoryBuildsANonEmptySentence() {
		for (final AnnouncementCategory category : AnnouncementCategory.values()) {
			final AnnouncementText.Announcement a = AnnouncementText.build(event(category, List.of("Alpha")));
			assertFalse(a.fragments().isEmpty(), category.name());
			assertTrue(a.subtitle().endsWith("."), category.name());
		}
		assertEquals("alert", AnnouncementCategory.SAFETY.chime());
		assertEquals("delay", AnnouncementCategory.DELAY.chime());
	}

	@Test
	void voicePackParsingSkipsBadEntriesAndClampsDurations() {
		final JsonObject root = JsonParser.parseString("""
				{"priority": 5, "fragments": {
				  "phrase.the": "pack:the",
				  "number.3": {"sound": "pack:three", "duration_ms": 600},
				  "number.4": {"sound": "pack:four", "duration_ms": 99999999},
				  "bad.one": 12,
				  "bad.two": {"duration_ms": 5}
				}}""").getAsJsonObject();
		final VoicePack pack = VoicePack.parse("pack:test", root);
		assertEquals(5, pack.priority());
		assertEquals(3, pack.size());
		assertEquals(VoicePack.DEFAULT_DURATION_MILLIS, pack.get("phrase.the").durationMillis());
		assertEquals(600, pack.get("number.3").durationMillis());
		assertEquals(VoicePack.MAX_DURATION_MILLIS, pack.get("number.4").durationMillis());
		assertNull(pack.get("bad.one"));
	}

	@Test
	void stackFallsThroughByPriorityAndNeedsEveryFragment() {
		final VoicePack low = VoicePack.parse("a", JsonParser.parseString("{\"priority\":1,\"fragments\":{\"phrase.the\":\"a:the\",\"number.3\":\"a:three\"}}").getAsJsonObject());
		final VoicePack high = VoicePack.parse("b", JsonParser.parseString("{\"priority\":9,\"fragments\":{\"phrase.the\":\"b:the\"}}").getAsJsonObject());
		final VoicePack.Stack stack = new VoicePack.Stack(List.of(low, high, VoicePack.builtin("mod")));
		assertEquals("b:the", stack.find("phrase.the").soundId());
		assertEquals("a:three", stack.find("number.3").soundId());
		assertEquals("mod:live.chime.info", stack.find("chime.info").soundId());
		assertNull(stack.find("phrase.unknown"));

		final List<AnnouncementText.Fragment> complete = List.of(new AnnouncementText.Fragment("phrase.the", "The"), new AnnouncementText.Fragment("number.3", "3"));
		assertNotNull(stack.resolveAll(complete, null));
		final List<AnnouncementText.Fragment> partial = List.of(new AnnouncementText.Fragment("phrase.the", "The"), new AnnouncementText.Fragment("station.nowhere", "Nowhere"));
		assertNull(stack.resolveAll(partial, null), "a half-spoken sentence must fall back");
		final List<String> missing = new ArrayList<>();
		assertNull(stack.resolveAll(partial, missing));
		assertEquals(List.of("station.nowhere"), missing);
	}

	@Test
	void closestSpeakerInRangeWithTheCategoryEnabledWins() {
		final SpeakerPicker.Point near = new SpeakerPicker.Point("near", 2, 0, 0, 16, AnnouncementCategory.ALL_MASK);
		final SpeakerPicker.Point far = new SpeakerPicker.Point("far", 10, 0, 0, 16, AnnouncementCategory.ALL_MASK);
		final SpeakerPicker.Point outOfRange = new SpeakerPicker.Point("small", 6, 0, 0, 4, AnnouncementCategory.ALL_MASK);
		final SpeakerPicker.Point noDelay = new SpeakerPicker.Point("nodelay", 1, 0, 0, 16, AnnouncementCategory.ALL_MASK & ~AnnouncementCategory.DELAY.bit());
		final List<SpeakerPicker.Point> all = List.of(far, outOfRange, noDelay, near);
		assertSame(noDelay, SpeakerPicker.closest(all, 0, 0, 0, AnnouncementCategory.APPROACHING));
		assertSame(near, SpeakerPicker.closest(all, 0, 0, 0, AnnouncementCategory.DELAY));
		assertNull(SpeakerPicker.closest(all, 100, 0, 0, AnnouncementCategory.APPROACHING), "player out of range of every speaker");
		assertNull(SpeakerPicker.closest(List.of(), 0, 0, 0, AnnouncementCategory.SAFETY));
	}
}
