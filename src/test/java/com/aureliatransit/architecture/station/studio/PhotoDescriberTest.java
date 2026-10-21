package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enviro, 10-20: a photo of a station into the Studio's words. */
class PhotoDescriberTest {

	/** Every phrase the prompt teaches is one the planner reads: Claude cannot be told to write words that are then ignored. */
	@Test
	void everyPhraseInThePromptIsRead() {
		final String prompt = PhotoDescriber.prompt();
		final Matcher quoted = Pattern.compile("\"([^\"]+)\"").matcher(prompt);
		int phrases = 0;
		while (quoted.find()) {
			if (quoted.group(1).contains("<")) {
				continue;
			}
			final String phrase = quoted.group(1).replace("N ", "3 ").replace("N long", "120 long");
			final ParseResult parse = StationDescriptionParser.parse("3 track station, " + phrase);
			assertEquals(List.of(), parse.ignored(), phrase);
			phrases++;
		}
		assertTrue(phrases > 25, "phrases " + phrases);
		for (final StationRecipe.Look look : StationRecipe.Look.values()) {
			if (look != StationRecipe.Look.AUTO) {
				final String word = look.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
				assertTrue(prompt.contains(word), word);
				assertEquals(List.of(), StationDescriptionParser.parse("3 track " + word + " station").ignored(), word);
			}
		}
		// the worked example plans with nothing ignored or warned
		final String example = prompt.substring(prompt.indexOf("Example: ") + "Example: ".length()).trim();
		final ParseResult parse = StationDescriptionParser.parse(example);
		assertEquals(List.of(), parse.ignored());
		assertEquals(List.of(), parse.warnings());
	}

	@Test
	void theRequestCarriesThePhotoAndTheVocabulary() {
		final Map<String, Object> body = Json.object(Json.parse(PhotoDescriber.request(new byte[]{1, 2, 3}, "image/jpeg")));
		assertEquals(PhotoDescriber.MODEL, body.get("model"));
		assertEquals(PhotoDescriber.prompt(), body.get("system"));
		final List<Object> content = Json.list(Json.object(Json.list(body.get("messages")).get(0)).get("content"));
		final Map<String, Object> source = Json.object(Json.object(content.get(0)).get("source"));
		assertEquals("image/jpeg", source.get("media_type"));
		assertEquals("AQID", source.get("data"));
	}

	@Test
	void theAnswerIsOneCleanLine() {
		final String reply = "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},"
				+ "{\"type\":\"text\",\"text\":\"\\\"two track teddington station,\\n side platforms, 200 long.\\\"\"}]}";
		assertEquals("two track teddington station, side platforms, 200 long", PhotoDescriber.description(reply));
		final String longer = "{\"content\":[{\"type\":\"text\",\"text\":\"" + "a".repeat(StudioApi.MAX_TYPED + 50) + "\"}]}";
		assertEquals(StudioApi.MAX_TYPED, PhotoDescriber.description(longer).length());
		assertThrows(IllegalStateException.class, () -> PhotoDescriber.description("{\"stop_reason\":\"refusal\",\"content\":[]}"));
		assertThrows(IllegalStateException.class, () -> PhotoDescriber.description("{\"content\":[{\"type\":\"text\",\"text\":\"  \"}]}"));
	}

	@Test
	void onlyPhotosAreSent() {
		assertThrows(IllegalStateException.class, () -> PhotoDescriber.describe(new byte[]{1}, "text/html", "key"));
	}

	@Test
	void aPhotosDescriptionBecomesAPreset() {
		final Map<String, Object> preset = Json.object(Json.parse(StudioApi.photoPreset("two track teddington station, side platforms, 200 long, named Teddington")));
		assertEquals(2.0, preset.get("tracks"));
		assertEquals("Teddington", preset.get("name"));
		assertEquals("TEDDINGTON", preset.get("look"));
	}
}
