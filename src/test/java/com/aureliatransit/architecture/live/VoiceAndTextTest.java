package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import com.aureliatransit.architecture.live.announce.AnnouncementEvent;
import com.aureliatransit.architecture.live.announce.AnnouncementStyle;
import com.aureliatransit.architecture.live.announce.AnnouncementText;
import com.aureliatransit.architecture.live.announce.FragmentKeys;
import com.aureliatransit.architecture.live.announce.SpeakerPicker;
import com.aureliatransit.architecture.live.announce.VoicePack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.aureliatransit.architecture.transit.StationAssociation;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
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

	@Test
	void britishWordingFollowsThePlatformPattern() {
		final AnnouncementText.Announcement next = AnnouncementText.build(event(AnnouncementCategory.APPROACHING, List.of("Alpha", "Zürich Hbf")), AnnouncementStyle.BRITISH, null);
		assertEquals("The next train to arrive at platform 3 will be the Red Line service to Zürich Hbf. Calling at Alpha and Zürich Hbf.", next.subtitle());
		assertEquals("phrase.next_train_to_arrive_at_platform", next.fragments().get(0).key());
		final AnnouncementText.Announcement standing = AnnouncementText.build(event(AnnouncementCategory.STANDING, List.of()), AnnouncementStyle.BRITISH, null);
		assertEquals("The train now standing at platform 3 is the Red Line service to Zürich Hbf. Please mind the gap between the train and the platform.", standing.subtitle());
		final AnnouncementText.Announcement delay = AnnouncementText.build(event(AnnouncementCategory.DELAY, List.of()), AnnouncementStyle.BRITISH, null);
		assertEquals("We are sorry to announce that the Red Line service to Zürich Hbf is delayed by approximately 4 minutes.", delay.subtitle());
		final AnnouncementText.Announcement safety = AnnouncementText.build(event(AnnouncementCategory.SAFETY, List.of()), AnnouncementStyle.BRITISH, null);
		assertEquals("A train is approaching platform 3. Please stand well back from the platform edge.", safety.subtitle());
		for (final AnnouncementCategory category : AnnouncementCategory.values()) {
			final AnnouncementText.Announcement a = AnnouncementText.build(event(category, List.of("Alpha")), AnnouncementStyle.BRITISH, null);
			assertTrue(a.fragments().stream().allMatch(f -> !f.key().startsWith("phrase.") || isPhrase(f.key())), category.name());
		}
	}

	private static boolean isPhrase(String key) {
		for (final AnnouncementText.Phrase phrase : AnnouncementText.Phrase.values()) {
			if (phrase.key().equals(key)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void standardSentencesCloseBeforeANewOne() {
		final AnnouncementText.Announcement standing = AnnouncementText.build(event(AnnouncementCategory.STANDING, List.of()));
		assertEquals("The train at platform 3 is the Red Line service to Zürich Hbf. Please board now.", standing.subtitle());
	}

	@Test
	void helpPointSaysTheStationAndPlatform() {
		final AnnouncementText.Announcement full = AnnouncementText.helpPoint("Central", "3", true, null);
		assertEquals("This is Central, platform 3.", full.subtitle());
		assertEquals(List.of("phrase.this_is", "station.central", "phrase.platform", "number.3"), full.fragments().stream().map(AnnouncementText.Fragment::key).toList());
		final AnnouncementText.Announcement shortForm = AnnouncementText.helpPoint("Central", "3", false, null);
		assertEquals("This is platform 3.", shortForm.subtitle());
		assertEquals(List.of("phrase.this_is_platform", "number.3"), shortForm.fragments().stream().map(AnnouncementText.Fragment::key).toList());
		assertEquals("This is platform 2a.", AnnouncementText.helpPoint("", "2a", true, null).subtitle());
		assertEquals("This is Central.", AnnouncementText.helpPoint("Central", "", true, null).subtitle());
		assertNull(AnnouncementText.helpPoint("", " ", true, null));
	}

	@Test
	void speakerStyleSurvivesSavingAndOldSpeakersKeepStandardWording() {
		final SpeakerConfig british = new SpeakerConfig(StationAssociation.AUTO, 16, 80, AnnouncementCategory.ALL_MASK, AnnouncementStyle.BRITISH);
		final NbtCompound nbt = new NbtCompound();
		british.writeNbt(nbt);
		assertEquals(british, SpeakerConfig.readNbt(nbt));
		nbt.remove("Style");
		assertEquals(AnnouncementStyle.STANDARD, SpeakerConfig.readNbt(nbt).style(), "saved before styles existed");
		final PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		british.write(buf);
		assertEquals(british, SpeakerConfig.read(buf));
		assertEquals(AnnouncementStyle.BRITISH, SpeakerConfig.DEFAULT.style(), "new speakers use the British wording");
		assertEquals(AnnouncementStyle.STANDARD, AnnouncementStyle.byOrdinal(99));
	}

	@Test
	void builtInVoiceSpeaksEveryPhraseAndNumberQuietlyUnderPacks() throws java.io.IOException {
		final String base = "/assets/aurelia_transit_architecture/";
		final JsonObject root;
		final JsonObject sounds;
		try (var in = VoiceAndTextTest.class.getResourceAsStream(base + "aurelia_voice/builtin.json");
			 var events = VoiceAndTextTest.class.getResourceAsStream(base + "sounds.json")) {
			assertNotNull(in, "built-in voice missing: run tools/make_voice.py");
			root = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			sounds = JsonParser.parseReader(new java.io.InputStreamReader(events, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		}
		final VoicePack pack = VoicePack.parse("aurelia_transit_architecture:builtin", root);
		assertTrue(pack.priority() < 0, "must sit under resource packs");
		final List<String> keys = new ArrayList<>();
		for (final AnnouncementText.Phrase phrase : AnnouncementText.Phrase.values()) {
			keys.add(phrase.key());
		}
		for (int n = 0; n <= FragmentKeys.MAX_NUMBER; n++) {
			keys.add(FragmentKeys.number(n));
		}
		for (final String key : keys) {
			final VoicePack.FragmentSound sound = pack.get(key);
			assertNotNull(sound, "no clip for " + key + ": re-run tools/make_voice.py");
			final String event = sound.soundId().substring(sound.soundId().indexOf(':') + 1);
			assertTrue(sounds.has(event), "sound event not registered: " + event);
			final String file = sounds.getAsJsonObject(event).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
			assertNotNull(VoiceAndTextTest.class.getResource(base + "sounds/" + file.substring(file.indexOf(':') + 1) + ".ogg"), "clip file missing for " + key);
		}
		final VoicePack.Stack stack = new VoicePack.Stack(List.of(pack, VoicePack.builtin("aurelia_transit_architecture")));
		assertNotNull(stack.resolveAll(AnnouncementText.helpPoint("Central", "3", false, null).fragments(), null), "help point spoken out of the box");
		assertNull(stack.resolveAll(AnnouncementText.helpPoint("Central", "3", true, null).fragments(), null), "no clip for a station name");
	}
}
