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
		TRAIN_IS_APPROACHING_PLATFORM("train_is_approaching_platform", "A train is approaching platform"),
		// British style
		NEXT_TRAIN_TO_ARRIVE_AT_PLATFORM("next_train_to_arrive_at_platform", "The next train to arrive at platform"),
		WILL_BE_THE("will_be_the", "will be the"),
		TRAIN_NOW_STANDING_AT_PLATFORM("train_now_standing_at_platform", "The train now standing at platform"),
		MIND_THE_GAP("mind_the_gap", "Please mind the gap between the train and the platform."),
		TRAIN_NOW_ARRIVING_AT_PLATFORM("train_now_arriving_at_platform", "The train now arriving at platform"),
		TAKE_BELONGINGS("take_belongings", "Please take all of your belongings with you."),
		SORRY_TO_ANNOUNCE("sorry_to_announce", "We are sorry to announce that the"),
		STAND_WELL_BACK("stand_well_back", "Please stand well back from the platform edge."),
		// help points
		THIS_IS("this_is", "This is"),
		PLATFORM("platform", "platform"),
		THIS_IS_PLATFORM("this_is_platform", "This is platform");

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
		return build(event, AnnouncementStyle.STANDARD, java.util.function.UnaryOperator.identity());
	}

	public static Announcement build(AnnouncementEvent event, java.util.function.UnaryOperator<String> stationText) {
		return build(event, AnnouncementStyle.STANDARD, stationText);
	}

	/**
	 * @param stationText how a station name reads in the text (station suffixes, A5); voice-pack fragment keys always
	 *                    use the plain name, so recorded clips keep matching
	 */
	public static Announcement build(AnnouncementEvent event, AnnouncementStyle style, java.util.function.UnaryOperator<String> stationText) {
		final java.util.function.UnaryOperator<String> named = stationText == null ? java.util.function.UnaryOperator.identity() : stationText;
		if (style == AnnouncementStyle.BRITISH) {
			return british(event, named);
		}
		final List<Fragment> f = new ArrayList<>(16);
		switch (event.category()) {
			case APPROACHING -> {
				phrase(f, Phrase.TRAIN_APPROACHING_PLATFORM);
				platform(f, event);
				service(f, event, named);
				callingAt(f, event, named);
			}
			case STANDING -> {
				phrase(f, Phrase.TRAIN_AT_PLATFORM);
				platform(f, event);
				service(f, event, named);
				callingAt(f, event, named);
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
				f.add(new Fragment(FragmentKeys.station(event.destination()), named.apply(event.destination())));
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

	private static Announcement british(AnnouncementEvent event, java.util.function.UnaryOperator<String> named) {
		final List<Fragment> f = new ArrayList<>(16);
		switch (event.category()) {
			case APPROACHING -> {
				phrase(f, Phrase.NEXT_TRAIN_TO_ARRIVE_AT_PLATFORM);
				platform(f, event);
				phrase(f, Phrase.WILL_BE_THE);
				routeAndDestination(f, event, named);
				callingAt(f, event, named);
			}
			case STANDING -> {
				phrase(f, Phrase.TRAIN_NOW_STANDING_AT_PLATFORM);
				platform(f, event);
				service(f, event, named);
				callingAt(f, event, named);
				phrase(f, Phrase.MIND_THE_GAP);
			}
			case TERMINATING -> {
				phrase(f, Phrase.TRAIN_NOW_ARRIVING_AT_PLATFORM);
				platform(f, event);
				phrase(f, Phrase.TERMINATES_HERE);
				phrase(f, Phrase.TAKE_BELONGINGS);
			}
			case DELAY -> {
				phrase(f, Phrase.SORRY_TO_ANNOUNCE);
				routeAndDestination(f, event, named);
				phrase(f, Phrase.IS_DELAYED_BY);
				final String number = FragmentKeys.number(event.delayMinutes());
				f.add(new Fragment(number == null ? "number.invalid" : number, Integer.toString(event.delayMinutes())));
				phrase(f, event.delayMinutes() == 1 ? Phrase.MINUTE : Phrase.MINUTES);
			}
			case SAFETY -> {
				phrase(f, Phrase.TRAIN_IS_APPROACHING_PLATFORM);
				platform(f, event);
				phrase(f, Phrase.STAND_WELL_BACK);
			}
		}
		return new Announcement(event.category(), subtitle(f), List.copyOf(f));
	}

	/**
	 * What a help point says: "This is Central, platform 3." With {@code withStation} false (or no station known) it is
	 * just "This is platform 3."; with no platform it is "This is Central.". Null when neither is known.
	 */
	public static Announcement helpPoint(String stationName, String platformName, boolean withStation, java.util.function.UnaryOperator<String> stationText) {
		final java.util.function.UnaryOperator<String> named = stationText == null ? java.util.function.UnaryOperator.identity() : stationText;
		final boolean station = stationName != null && !stationName.isBlank();
		final boolean platform = platformName != null && !platformName.isBlank();
		final List<Fragment> f = new ArrayList<>(4);
		if (platform && (!station || !withStation)) {
			phrase(f, Phrase.THIS_IS_PLATFORM);
			f.add(new Fragment(FragmentKeys.platform(platformName), platformName.trim()));
		} else if (platform) {
			phrase(f, Phrase.THIS_IS);
			f.add(new Fragment(FragmentKeys.station(stationName), named.apply(stationName) + ","));
			phrase(f, Phrase.PLATFORM);
			f.add(new Fragment(FragmentKeys.platform(platformName), platformName.trim()));
		} else if (station) {
			phrase(f, Phrase.THIS_IS);
			f.add(new Fragment(FragmentKeys.station(stationName), named.apply(stationName)));
		} else {
			return null;
		}
		// help points use the information chime
		return new Announcement(AnnouncementCategory.STANDING, subtitle(f), List.copyOf(f));
	}

	private static void routeAndDestination(List<Fragment> out, AnnouncementEvent event, java.util.function.UnaryOperator<String> named) {
		if (!event.routeName().isBlank()) {
			out.add(new Fragment(FragmentKeys.route(event.routeName()), event.routeName()));
		}
		phrase(out, Phrase.SERVICE_TO);
		out.add(new Fragment(FragmentKeys.station(event.destination()), named.apply(event.destination())));
	}

	private static void phrase(List<Fragment> out, Phrase phrase) {
		out.add(new Fragment(phrase.key(), phrase.text()));
	}

	private static void platform(List<Fragment> out, AnnouncementEvent event) {
		final String name = event.platformName().isBlank() ? "?" : event.platformName();
		out.add(new Fragment(FragmentKeys.platform(event.platformName()), name));
	}

	private static void service(List<Fragment> out, AnnouncementEvent event, java.util.function.UnaryOperator<String> named) {
		phrase(out, Phrase.IS_THE);
		if (!event.routeName().isBlank()) {
			out.add(new Fragment(FragmentKeys.route(event.routeName()), event.routeName()));
		}
		phrase(out, Phrase.SERVICE_TO);
		out.add(new Fragment(FragmentKeys.station(event.destination()), named.apply(event.destination())));
	}

	private static void callingAt(List<Fragment> out, AnnouncementEvent event, java.util.function.UnaryOperator<String> named) {
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
			out.add(new Fragment(FragmentKeys.station(shown.get(i)), commaAfter ? named.apply(shown.get(i)) + "," : named.apply(shown.get(i))));
		}
	}

	private static String subtitle(List<Fragment> fragments) {
		final StringBuilder sb = new StringBuilder();
		for (final Fragment fragment : fragments) {
			if (sb.length() > 0) {
				// a capitalised phrase starts a new sentence: close the one before it
				if (fragment.key().startsWith("phrase.") && Character.isUpperCase(fragment.text().charAt(0)) && sb.charAt(sb.length() - 1) != '.') {
					sb.append('.');
				}
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
