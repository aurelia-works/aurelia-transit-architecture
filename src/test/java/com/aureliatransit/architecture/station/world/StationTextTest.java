package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationSpec;
import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chat lines of the station generator: the plan summary and the clickable buttons. */
class StationTextTest {

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
	}

	@Test
	void aButtonRunsItsCommandAndExplainsItselfOnHover() {
		final MutableText button = StationText.button("Undo", "/ata_station undo", Formatting.YELLOW, "Take the whole station away again");
		assertEquals("[Undo]", button.getString());
		final ClickEvent click = button.getStyle().getClickEvent();
		assertNotNull(click);
		assertEquals(ClickEvent.Action.RUN_COMMAND, click.getAction());
		assertEquals("/ata_station undo", click.getValue());
		final HoverEvent hover = button.getStyle().getHoverEvent();
		assertNotNull(hover);
		assertEquals(HoverEvent.Action.SHOW_TEXT, hover.getAction());
		assertEquals(Formatting.YELLOW.getColorValue(), button.getStyle().getColor().getRgb());
		assertTrue(button.getStyle().isBold());
	}

	@Test
	void theLineNamesTracksSizeAndTheStationName() {
		final StationSpec spec = StationDescriptionParser.parse("2 tracks, side platforms, 64 long, named Alpha").spec();
		final String line = StationText.planLine(spec);
		assertTrue(line.startsWith("2 tracks, "), line);
		assertTrue(line.contains("64 x "), line);
		assertTrue(line.contains("\"Alpha\""), line);
	}

	@Test
	void oneTrackIsSingular() {
		final StationSpec spec = StationDescriptionParser.parse("1 track, 40 long").spec();
		assertTrue(StationText.planLine(spec).startsWith("1 track, "), StationText.planLine(spec));
	}

	@Test
	void aHubAndAStepFreeStationSayso() {
		final StationSpec hub = StationDescriptionParser.parse("hub station, step-free, 3 tracks").spec();
		final String line = StationText.planLine(hub);
		assertTrue(line.contains(", step-free"), line);
		assertTrue(hub.hub(), "the words should make a hub");
		assertTrue(line.contains(", hub"), line);
	}
}
