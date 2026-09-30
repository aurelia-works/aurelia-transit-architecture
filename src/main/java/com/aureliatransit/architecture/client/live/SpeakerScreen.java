package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.live.LiveSystems;
import com.aureliatransit.architecture.live.SpeakerBlockEntity;
import com.aureliatransit.architecture.live.SpeakerConfig;
import com.aureliatransit.architecture.live.announce.AnnouncementCategory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Configuration screen of a speaker: hearing radius, volume, announcement categories and the station picker.
 */
final class SpeakerScreen extends LiveConfigScreen {

	private static final int[] RADII = {4, 8, 12, 16, 24, 32};
	private static final int[] VOLUMES = {20, 40, 60, 80, 100};

	private final SpeakerBlockEntity speaker;
	private int radius;
	private int volume;
	private int categories;

	SpeakerScreen(BlockPos pos, SpeakerBlockEntity speaker) {
		super(tr("live_speaker_title"), pos, speaker.config().association());
		this.speaker = speaker;
		this.radius = speaker.config().radius();
		this.volume = speaker.config().volume();
		this.categories = speaker.config().categories();
	}

	@Override
	protected void init() {
		final int x = leftX();
		int y = topY();
		add(ButtonWidget.builder(tr("live_radius", radius), button -> {
			radius = next(RADII, radius);
			button.setMessage(tr("live_radius", radius));
		}).dimensions(x, y, COLUMN_WIDTH, 20).build());
		y += 24;
		add(ButtonWidget.builder(tr("live_volume", volume), button -> {
			volume = next(VOLUMES, volume);
			button.setMessage(tr("live_volume", volume));
		}).dimensions(x, y, COLUMN_WIDTH, 20).build());
		y += 24;
		for (final AnnouncementCategory category : AnnouncementCategory.values()) {
			add(ButtonWidget.builder(categoryText(category), button -> {
				categories ^= category.bit();
				button.setMessage(categoryText(category));
			}).dimensions(x, y, COLUMN_WIDTH, 20).build());
			y += 22;
		}
		buildAssociationWidgets();
		addDone();
	}

	private Text categoryText(AnnouncementCategory category) {
		final Text state = category.enabledIn(categories) ? Text.translatable("options.on") : Text.translatable("options.off");
		return tr("live_category", Text.translatable("screen.aurelia_transit_architecture." + category.translationKey()), state);
	}

	private static int next(int[] values, int current) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] > current) {
				return values[i];
			}
		}
		return values[0];
	}

	@Override
	protected boolean stillValid() {
		return !speaker.isRemoved();
	}

	@Override
	protected void save() {
		final SpeakerConfig config = new SpeakerConfig(association(), radius, volume, categories);
		if (!config.equals(speaker.config())) {
			ClientPlayNetworking.send(LiveSystems.UPDATE_SPEAKER, LiveSystems.writeSpeakerUpdate(pos, config));
		}
	}
}
