package com.aureliatransit.architecture.live.announce;

import java.util.ArrayList;
import java.util.List;

/**
 * The phrase/template system. An event becomes an {@link Announcement}: a subtitle text and the same sentence as a
 * sequence of {@link Fragment}s that a voice pack can speak. Templates are original English wording.
 */
public final class AnnouncementText {

	public static final int MAX_STOPS = 5;

	/**
	 * A speakable unit: a voice-pack key and the text it stands for.
	 */
	public record Fragment(String key, String text) {
	}

	public record Announcement(AnnouncementCategory category, String subtitle, List<Fragment> fragments) {
		public String chimeKey() {
			return "chime." + category.chime();
		}
	}

	/**
	 * Every fixed phrase with its English text; the keys are the contract with voice packs (see docs/VOICE_PACKS.md).
	 */
	public enum Phrase {
		TRAIN_APPROACHING_PLATFORM("train_approaching_platform", "The train approaching platform"),
		TRAIN_AT_PLATFORM("train_at_platform", "The train at platform"),
		TRAIN_ARRIVING_AT_PLATFORM("train_arriving_at_platform", "The train arriving at platform"),
		IS_THE("is_the", "is the"),
		SERVICE_TO("service_to", "service to"),
		CALLING_AT("calling_at", "Calling at"),
		AND("and", "and"),
		PLEASE_BOARD_NOW("please_board_now", "Please board now."),
		TERMINATES_HERE("terminates_here", "terminates here."),
		ALL_PASSENGERS_LEAVE("all_passengers_leave", "All passengers must leave the train."),
		THE("the", "The"),
		IS_DELAYED_BY("is_delayed_by", "is delayed by approximately"),
		MINUTE("minute", "minute"),
		MINUTES("minutes", "minutes"),
		APOLOGISE("apologise", "We apologise for the delay."),
		STAND_CLEAR("stand_clear", "Please stand clear of the platform edge."),
		TRAIN_IS_APPROACHING_PLATFORM("train_is_approaching_platform", "A train is approaching platform");

		private final String key;
		private final String text;

		Phrase(String key, String text) {
			this.key = FragmentKeys.phrase(key);
			this.text = text;
		}

		public String key() {
			return key;
		}

		public String text() {
			return text;
		}
	}

	private AnnouncementText() {
	}

	public static Announcement build(AnnouncementEvent event) {
		final List<Fragment> f = new ArrayList<>(16);
		switch (event.category()) {
			case APPROACHING -> {
				phrase(f, Phrase.TRAIN_APPROACHING_PLATFORM);
				platform(f, event);
				service(f, event);
				callingAt(f, event);
			}
			case STANDING -> {
				phrase(f, Phrase.TRAIN_AT_PLATFORM);
				platform(f, event);
				service(f, event);
				callingAt(f, event);
				phrase(f, Phrase.PLEASE_BOARD_NOW);
			}
			case TERMINATING -> {
				phrase(f, Phrase.TRAIN_ARRIVING_AT_PLATFORM);
				platform(f, event);
				phrase(f, Phrase.TERMINATES_HERE);
				phrase(f, Phrase.ALL_PASSENGERS_LEAVE);
			}
			case DELAY -> {
				phrase(f, Phrase.THE);
				if (!event.routeName().isBlank()) {
					f.add(new Fragment(FragmentKeys.route(event.routeName()), event.routeName()));
				}
				phrase(f, Phrase.SERVICE_TO);
				f.add(new Fragment(FragmentKeys.station(event.destination()), event.destination()));
				phrase(f, Phrase.IS_DELAYED_BY);
				final String number = FragmentKeys.number(event.delayMinutes());
				f.add(new Fragment(number == null ? "number.invalid" : number, Integer.toString(event.delayMinutes())));
				phrase(f, event.delayMinutes() == 1 ? Phrase.MINUTE : Phrase.MINUTES);
				phrase(f, Phrase.APOLOGISE);
			}
			case SAFETY -> {
				phrase(f, Phrase.STAND_CLEAR);
				phrase(f, Phrase.TRAIN_IS_APPROACHING_PLATFORM);
				platform(f, event);
			}
		}
		return new Announcement(event.category(), subtitle(f), List.copyOf(f));
	}

	private static void phrase(List<Fragment> out, Phrase phrase) {
		out.add(new Fragment(phrase.key(), phrase.text()));
	}

	private static void platform(List<Fragment> out, AnnouncementEvent event) {
		final String name = event.platformName().isBlank() ? "?" : event.platformName();
		out.add(new Fragment(FragmentKeys.platform(event.platformName()), name));
	}

	private static void service(List<Fragment> out, AnnouncementEvent event) {
		phrase(out, Phrase.IS_THE);
		if (!event.routeName().isBlank()) {
			out.add(new Fragment(FragmentKeys.route(event.routeName()), event.routeName()));
		}
		phrase(out, Phrase.SERVICE_TO);
		out.add(new Fragment(FragmentKeys.station(event.destination()), event.destination()));
	}

	private static void callingAt(List<Fragment> out, AnnouncementEvent event) {
		final List<String> stops = event.callingAt();
		if (stops.isEmpty()) {
			return;
		}
		phrase(out, Phrase.CALLING_AT);
		final int count = Math.min(MAX_STOPS, stops.size());
		// always finish on the final stop
		final List<String> shown = new ArrayList<>(count);
		for (int i = 0; i < count - 1; i++) {
			shown.add(stops.get(i));
		}
		shown.add(stops.get(stops.size() - 1));
		for (int i = 0; i < shown.size(); i++) {
			if (i == shown.size() - 1 && shown.size() > 1) {
				phrase(out, Phrase.AND);
			}
			// the comma is display-only; the fragment key is what a voice pack sees
			final boolean commaAfter = i < shown.size() - 2;
			out.add(new Fragment(FragmentKeys.station(shown.get(i)), commaAfter ? shown.get(i) + "," : shown.get(i)));
		}
	}

	private static String subtitle(List<Fragment> fragments) {
		final StringBuilder sb = new StringBuilder();
		for (final Fragment fragment : fragments) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(fragment.text());
		}
		String text = sb.toString().replace(" Calling at", ". Calling at").replace("..", ".");
		if (!text.endsWith(".")) {
			text += ".";
		}
		return text;
	}
}
