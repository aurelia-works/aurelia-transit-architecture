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
	void anAirportIsSummedUpAsAnAirport() {
		final StationSpec spec = StationDescriptionParser.parse("airport with 2 gates and a train station, runway 600 long, named Velare").spec();
		final String expected = "airport, 1 runway 600 x " + spec.airport().runwayWidth() + ", 2 gates, terminal, railway station under the terminal, \"Velare\"";
		assertEquals(expected, StationText.headline(spec));
		assertEquals(expected, StationText.planLine(spec));
	}

	@Test
	void aStackedStationsUpperRailNodesAreAtTheirOwnLevel() {
		final StationSpec spec = StationDescriptionParser.parse("2 track stacked deep tube station, 30 blocks deep, 40 long").spec();
		final com.aureliatransit.architecture.station.TrackLayout layout = com.aureliatransit.architecture.station.StationPlanner.plan(spec).layout();
		final com.aureliatransit.architecture.station.LocalFrame frame = com.aureliatransit.architecture.station.LocalFrame.inFrontOf(0, 64, 0,
				com.aureliatransit.architecture.station.Heading.SOUTH, layout);
		final java.util.Set<String> nodes = new java.util.HashSet<>();
		for (final com.aureliatransit.architecture.station.TrackLayout.Track track : layout.tracks()) {
			nodes.add(StationText.node(frame, track, layout.platformFrom(track)));
		}
		assertEquals(2, nodes.size(), "both tracks' first rail nodes print the same place: " + nodes);
	}

	@Test
	void aDeepStationIsNamedByItsKindAndDepthNotARoof() {
		final StationSpec spec = StationDescriptionParser.parse("2 track deep tube station, 24 blocks deep, 60 long").spec();
		assertTrue(StationText.headline(spec).contains("deep tube, 24 deep"), StationText.headline(spec));
		assertTrue(StationText.planLine(spec).contains("deep tube, 24 deep"), StationText.planLine(spec));
		assertTrue(!StationText.headline(spec).contains("roof"), StationText.headline(spec));
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
