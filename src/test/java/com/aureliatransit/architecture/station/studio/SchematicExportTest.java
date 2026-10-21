package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.PlanFingerprint;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.RailEdges;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlacement;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.StationSpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The schematic export (Sponge version 2, written without Minecraft classes) read back and compared with the plan, block by block. */
class SchematicExportTest {

	private static final BlockModels.AssetSource OWN_ASSETS = (namespace, path) -> {
		final Path file = Path.of("src/main/resources/assets", namespace, path);
		return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
	};
	private static final Pattern STATE = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_/.-]+)(?:\\[([a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*)\\])?");

	// ---- the NBT writer ----------------------------------------------------------------------------------------------

	@Test
	void nbtIsWrittenByTheSpecAndReadBack() throws IOException {
		final Map<String, Object> inner = new LinkedHashMap<>();
		inner.put("s", "hé");
		final Map<String, Object> root = new LinkedHashMap<>();
		root.put("a", 1);
		root.put("b", (byte) -2);
		root.put("c", (short) 300);
		root.put("d", 5_000_000_000L);
		root.put("e", 1.5f);
		root.put("f", 2.5);
		root.put("g", new byte[]{1, 2, 3});
		root.put("h", new int[]{7, -7});
		root.put("i", new long[]{9});
		root.put("j", List.of("x", "yz"));
		root.put("k", new ArrayList<>());
		root.put("l", inner);
		root.put("m", List.of(inner, inner));
		final byte[] gz = Nbt.writeGzip("Root", root);
		final Nbt.Root back = Nbt.readGzip(gz);
		assertEquals("Root", back.name());
		assertEquals(1, back.value().get("a"));
		assertEquals((byte) -2, back.value().get("b"));
		assertEquals((short) 300, back.value().get("c"));
		assertEquals(5_000_000_000L, back.value().get("d"));
		assertEquals(1.5f, back.value().get("e"));
		assertEquals(2.5, back.value().get("f"));
		assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) back.value().get("g"));
		assertArrayEquals(new int[]{7, -7}, (int[]) back.value().get("h"));
		assertArrayEquals(new long[]{9}, (long[]) back.value().get("i"));
		assertEquals(List.of("x", "yz"), back.value().get("j"));
		assertEquals(List.of(), back.value().get("k"));
		assertEquals(inner, back.value().get("l"));
		assertEquals(List.of(inner, inner), back.value().get("m"));
		assertArrayEquals(gz, Nbt.writeGzip("Root", root), "the same values give the same bytes");
		// the bytes, by the format: a compound "x" holding the int "a" = 1
		final Map<String, Object> tiny = new LinkedHashMap<>();
		tiny.put("a", 1);
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(Nbt.writeGzip("x", tiny)))) {
			assertArrayEquals(new byte[]{10, 0, 1, 'x', 3, 0, 1, 'a', 0, 0, 0, 1, 0}, in.readAllBytes());
		}
		assertThrows(IllegalArgumentException.class, () -> Nbt.readGzip(new byte[]{1, 2, 3}));
		assertThrows(IllegalArgumentException.class, () -> Nbt.writeGzip("x", Map.of("bad", new Object())));
		assertThrows(IllegalArgumentException.class, () -> Nbt.writeGzip("x", Map.of("mixed", List.of(1, "two"))));
	}

	// ---- the schematic against the plan --------------------------------------------------------------------------------

	private static List<String> descriptions() {
		final List<String> words = new ArrayList<>(List.of(
				"2 tracks, 64 long, utrecht",
				"3 tracks, 80 long, hamburg, station building, named Altona, parking, seed k7f2ra",
				"4 tracks island, 96 long, amsterdam, terminus, hall roof, named Centraal",
				"2 tracks, 48 long, footbridge, escalators, travelators, stairs both ways, enclosed stairs, gravel track bed",
				"platform 1: side 9 wide; platform 2: island 12 wide 60 long open; platform 3: side canopy; length 64, station building, tokyo",
				"6 tracks island, 120 long, hub, berlin",
				"3 tracks island, 96 long, station building, city, hamburg, parking",
				"city class, parking, named Lot"));
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			words.add(preset.description());
		}
		return words;
	}

	/** Decodes BlockData by the Sponge spec, with no help from the exporter's own reader. */
	private static int[] decode(byte[] data, int cells) {
		final int[] out = new int[cells];
		int at = 0;
		for (int i = 0; i < cells; i++) {
			int value = 0;
			int shift = 0;
			while (true) {
				final int b = data[at++] & 0xFF;
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					break;
				}
				shift += 7;
			}
			out[i] = value;
		}
		assertEquals(data.length, at, "BlockData holds exactly one varint per cell");
		return out;
	}

	@Test
	void everyBlockOfEveryPresetComesBackAtItsPositionWithItsIdAndState() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		for (final String words : descriptions()) {
			final Map<String, String> q = Map.of("text", words);
			final StudioApi.Export export = api.export(q);
			final String text = StudioApi.description(q);
			final StationSpec spec = StationDescriptionParser.parse(text).spec();
			final StationPlan plan = StationPlanner.plan(spec);
			assertEquals(plan.blocks().size(), export.blocks(), words);
			assertEquals(PlanFingerprint.of(plan), export.fingerprint(), words);

			final Map<String, Object> root = Nbt.readGzip(export.bytes()).value();
			assertEquals("Schematic", Nbt.readGzip(export.bytes()).name());
			assertEquals(2, root.get("Version"), words);
			assertEquals(3465, root.get("DataVersion"), "Minecraft 1.20.1");
			final int width = ((Short) root.get("Width")) & 0xFFFF;
			final int height = ((Short) root.get("Height")) & 0xFFFF;
			final int length = ((Short) root.get("Length")) & 0xFFFF;
			assertArrayEquals(new int[]{0, 0, 0}, (int[]) root.get("Offset"));
			assertEquals(List.of(), root.get("BlockEntities"), "no block entities");
			@SuppressWarnings("unchecked")
			final Map<String, Object> palette = (Map<String, Object>) root.get("Palette");
			assertEquals(palette.size(), root.get("PaletteMax"), words);
			final String[] states = new String[palette.size()];
			for (final Map.Entry<String, Object> entry : palette.entrySet()) {
				final int index = (Integer) entry.getValue();
				assertTrue(index >= 0 && index < states.length && states[index] == null, words + " palette index " + index);
				states[index] = entry.getKey();
				final Matcher m = STATE.matcher(entry.getKey());
				assertTrue(m.matches(), words + ": not a block state: " + entry.getKey());
				if (m.group(2) != null) {
					final List<String> names = new ArrayList<>();
					for (final String pair : m.group(2).split(",")) {
						names.add(pair.substring(0, pair.indexOf('=')));
					}
					final List<String> sorted = new ArrayList<>(names);
					java.util.Collections.sort(sorted);
					assertEquals(sorted, names, words + ": properties in name order in " + entry.getKey());
					assertEquals(names.size(), new java.util.HashSet<>(names).size(), "a property twice in " + entry.getKey());
				}
			}
			assertEquals("minecraft:air", states[0], "air is palette 0, as WorldEdit writes it");
			final int[] cells = decode((byte[]) root.get("BlockData"), width * height * length);

			@SuppressWarnings("unchecked")
			final Map<String, Object> meta = (Map<String, Object>) root.get("Metadata");
			final int originIndexX = -(Integer) meta.get("WEOffsetX");
			final int originIndexY = -(Integer) meta.get("WEOffsetY");
			final int originIndexZ = -(Integer) meta.get("WEOffsetZ");
			assertTrue(originIndexX >= 0 && originIndexX < width && originIndexY >= 0 && originIndexY < height && originIndexZ >= 0 && originIndexZ < length,
					words + ": the paste origin is inside the clipboard");

			// the corner: the clipboard's smallest x, y and z over the planned blocks and the origin, in the studio's frame
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			for (final PlannedBlock block : plan.blocks()) {
				minX = Math.min(minX, StudioApi.FRAME.x(block.u(), block.v()));
				minY = Math.min(minY, block.y());
				minZ = Math.min(minZ, StudioApi.FRAME.z(block.u(), block.v()));
			}
			final int[] origin = StationPlacement.pasteOrigin(spec, plan);
			minX = Math.min(minX, StudioApi.FRAME.x(origin[0], origin[2]));
			minY = Math.min(minY, origin[1]);
			minZ = Math.min(minZ, StudioApi.FRAME.z(origin[0], origin[2]));
			assertEquals(StudioApi.FRAME.x(origin[0], origin[2]) - minX, originIndexX, words);
			assertEquals(origin[1] - minY, originIndexY, words);
			assertEquals(StudioApi.FRAME.z(origin[0], origin[2]) - minZ, originIndexZ, words);

			final Map<Integer, PlannedBlock> planned = new HashMap<>();
			for (final PlannedBlock block : plan.blocks()) {
				final int x = StudioApi.FRAME.x(block.u(), block.v()) - minX;
				final int y = block.y() - minY;
				final int z = StudioApi.FRAME.z(block.u(), block.v()) - minZ;
				assertTrue(x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length, words + " " + block + " outside the clipboard");
				final int cell = (y * length + z) * width + x;
				assertEquals(null, planned.put(cell, block), words + ": two blocks in one cell");
				final String state = states[cells[cell]];
				final Matcher m = STATE.matcher(state);
				assertTrue(m.matches(), state);
				assertEquals(block.blockId(), m.group(1), words + " " + block);
				final Map<String, String> props = new HashMap<>();
				if (m.group(2) != null) {
					for (final String pair : m.group(2).split(",")) {
						props.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
					}
				}
				// the plan's own extras are all there (edge sides as the world names them)
				final Heading front = StudioApi.FRAME.heading(block.front());
				for (final String pair : RailEdges.worldState(block.state(), front).split(",")) {
					if (pair.indexOf('=') > 0) {
						assertEquals(pair.substring(pair.indexOf('=') + 1).trim(), props.get(pair.substring(0, pair.indexOf('=')).trim()), words + " " + block + " as " + state);
					}
				}
				// a facing or axis that is there follows the front
				final Heading heading = front;
				if (props.containsKey("facing") && !block.blockId().equals("mtr:rail") && !block.state().contains("facing=")) {
					final String want = heading.name().toLowerCase();
					final String away = heading.opposite().name().toLowerCase();
					assertTrue(props.get("facing").equals(want) || props.get("facing").equals(away) && (block.blockId().equals("mtr:ticket_machine")
							|| block.blockId().equals("mtr:tactile_map") || block.blockId().equals("mtr:route_sign_wall_light")
							|| block.blockId().equals("mtr:station_name_entrance")), words + " " + block + " as " + state);
				}
				if (props.containsKey("axis") && !block.state().contains("axis=")) {
					assertEquals(heading.alongX() ? "x" : "z", props.get("axis"), words + " " + block);
				}
			}
			int nonAir = 0;
			for (int i = 0; i < cells.length; i++) {
				if (cells[i] != 0) {
					nonAir++;
					assertTrue(planned.containsKey(i), words + ": a block nobody planned at cell " + i + " " + states[cells[i]]);
				}
			}
			assertEquals(plan.blocks().size(), nonAir, words);

			// the information we add
			assertEquals(export.fingerprint(), meta.get("ATAFingerprint"));
			assertEquals(text, meta.get("ATADescription"));
			assertEquals(StationSeedFormat.of(spec), meta.get("ATASeed"));
			assertEquals("Aurelia Transit Architecture", meta.get("Author"));
			assertEquals(spec.name().isEmpty() ? "Station" : spec.name(), meta.get("Name"));
		}
	}

	/** The paste origin is out on the street, in front of the entrance, with room to stand. */
	@Test
	void theOriginIsOutsideTheEntranceOnTheGround() {
		for (final Map.Entry<String, String> test : Map.of(
				"through", "3 tracks island, 96 long, station building, utrecht",
				"terminus", "4 tracks island, 96 long, terminus, hamburg",
				"hub", "6 tracks island, 120 long, hub, berlin",
				"parking", "2 tracks, 64 long, station building, parking, amsterdam").entrySet()) {
			final StationSpec spec = StationDescriptionParser.parse(test.getValue()).spec();
			final StationPlan plan = StationPlanner.plan(spec);
			final int[] origin = StationPlacement.pasteOrigin(spec, plan);
			final java.util.Set<String> taken = new java.util.HashSet<>();
			for (final PlannedBlock block : plan.blocks()) {
				taken.add(block.u() + "," + block.y() + "," + block.v());
			}
			for (int y = 0; y <= 2; y++) {
				assertFalse(taken.contains(origin[0] + "," + (origin[1] + y) + "," + origin[2]), test.getKey() + ": no room to stand at height " + y);
			}
			assertEquals(0, origin[1], test.getKey() + ": ground level");
			if (spec.terminus()) {
				assertEquals(plan.layout().vMax() + 1, origin[2], test.getKey() + ": just past the head building's front");
			} else {
				assertTrue(origin[0] < 0, test.getKey() + ": outside the street wall, u " + origin[0]);
			}
		}
		// without a building: in front of the first ramp
		final StationSpec bare = StationDescriptionParser.parse("2 tracks, 48 long").spec();
		final StationPlan plan = StationPlanner.plan(bare);
		final int[] origin = StationPlacement.pasteOrigin(bare, plan);
		assertEquals(plan.layout().vMin() - 1, origin[2]);
	}

	@Test
	void theSameRequestGivesTheSameBytesAndAnotherSeedDoesNot() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		final Map<String, String> q = Map.of("look", "HAMBURG", "tracks", "3", "length", "80", "building", "1", "seed", "k7f2ra", "name", "Altona");
		final StudioApi.Export a = api.export(q);
		assertArrayEquals(a.bytes(), api.export(q).bytes());
		assertEquals("altona-k7f2ra.schem", a.filename());
		final Map<String, String> other = new HashMap<>(q);
		other.put("seed", "zz9");
		assertNotEquals(a.fingerprint(), api.export(other).fingerprint());
		other.remove("seed");
		assertEquals("altona-noseed.schem", api.export(other).filename());
	}

	@Test
	void fileNamesAreSafeSlugs() {
		assertEquals("station", StudioApi.slug(""));
		assertEquals("leidsche-rijn", StudioApi.slug("Leidsche  Rijn!"));
		assertEquals("zurich-hb", StudioApi.slug("Zürich HB"));
		assertEquals("station", StudioApi.slug("../../☃"));
		assertTrue(StudioApi.slug("a".repeat(100)).length() <= 40);
		assertFalse(StudioApi.slug("-x-").startsWith("-"));
	}

	@Test
	void joinsAreWrittenSoPanesStandJoined() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		final SchematicExport.Parsed schematic = SchematicExport.read(api.export(Map.of("text", "3 tracks island, 96 long, station building, utrecht, footbridge")).bytes());
		final Matcher panes = Pattern.compile("aurelia_transit_architecture:\\w*(?:glass|pane)\\w*\\[[^\\]]*\\]").matcher(String.join("\n", schematic.cells()));
		boolean joined = false;
		while (panes.find()) {
			joined |= panes.group().contains("=true");
		}
		// stairs, railings and girders of a footbridge station join their neighbours in the file, not at paste time
		boolean railingJoined = false;
		for (final String cell : schematic.cells()) {
			railingJoined |= cell.contains("handrail[") && cell.contains("=true") || cell.contains("station_girder[") && cell.contains("=true");
		}
		assertTrue(joined || railingJoined, "no joined pane, rail or girder found in a footbridge station");
	}

	/** Turned to face any of the four ways, every block lands in its place with its facing turned the same way. */
	@Test
	void anyHeadingTurnsBlocksAndFacingsTogether() {
		final BlockModels models = new BlockModels(OWN_ASSETS);
		final StationSpec spec = StationDescriptionParser.parse("2 tracks, 48 long, utrecht, station building, footbridge, named Turn").spec();
		final StationPlan plan = StationPlanner.plan(spec);
		final int[] origin = StationPlacement.pasteOrigin(spec, plan);
		final Map<Heading, String> fingerprints = new HashMap<>();
		for (final Heading heading : Heading.values()) {
			final com.aureliatransit.architecture.station.LocalFrame frame = new com.aureliatransit.architecture.station.LocalFrame(0, 0, 0, heading);
			final SchematicExport.Result result = SchematicExport.export(plan, frame, models, origin, spec.name(), Map.of());
			assertEquals(PlanFingerprint.of(plan), result.fingerprint(), "turning the station does not change what it is");
			final SchematicExport.Parsed parsed = SchematicExport.read(result.bytes());
			int turned = 0;
			for (final PlannedBlock block : plan.blocks()) {
				final String state = parsed.of(block, frame, origin);
				assertTrue(state.startsWith(block.blockId()), heading + " " + block + " as " + state);
				if (block.blockId().startsWith("aurelia_transit_architecture:") && state.contains("facing=") && !block.state().contains("facing=")) {
					assertTrue(state.contains("facing=" + frame.heading(block.front()).name().toLowerCase()), heading + " " + block + " as " + state);
					turned++;
				}
			}
			assertTrue(turned > 20, "platform edges were checked: " + turned);
			assertEquals(plan.blocks().size(), java.util.Arrays.stream(parsed.cells()).filter(c -> !c.equals("minecraft:air")).count(), heading.name());
			// the paste origin is clear and sits inside the clipboard whichever way round it is
			assertEquals("minecraft:air", parsed.at(-parsed.weOffsetX(), -parsed.weOffsetY(), -parsed.weOffsetZ()), heading.name());
			fingerprints.put(heading, java.util.Arrays.toString(parsed.cells()).hashCode() + ":" + parsed.width() + "x" + parsed.length());
		}
		assertNotEquals(fingerprints.get(Heading.NORTH), fingerprints.get(Heading.EAST), "east is a different file from north");
		assertEquals(fingerprints.get(Heading.NORTH).split(":")[1].split("x")[0], fingerprints.get(Heading.SOUTH).split(":")[1].split("x")[0], "south has north's width");
		assertEquals(fingerprints.get(Heading.NORTH).split(":")[1].split("x")[0], fingerprints.get(Heading.EAST).split(":")[1].split("x")[1], "east swaps width and length");
	}

	/** Escalators and lift doors are written the way MTR shapes them once the flight and shaft stand. */
	@Test
	void escalatorsAndLiftDoorsTakeTheirFinishedShapes() {
		final StudioApi api = new StudioApi(new BlockModels(OWN_ASSETS));
		final String words = "platform 1: side 9 wide; platform 2: side 6 wide; length 24, footbridge, escalators, stairs both ways, enclosed, named Test";
		final SchematicExport.Parsed parsed = SchematicExport.read(api.export(Map.of("text", words)).bytes());
		final java.util.Set<String> orientations = new java.util.TreeSet<>();
		int doors = 0;
		for (final String cell : parsed.cells()) {
			if (cell.startsWith("mtr:escalator_step[") || cell.startsWith("mtr:escalator_side[")) {
				final Matcher m = Pattern.compile("orientation=([a-z_]+)").matcher(cell);
				assertTrue(m.find(), cell);
				orientations.add(m.group(1));
			}
			if (cell.startsWith("mtr:lift_door_1[")) {
				doors++;
				assertTrue(cell.contains("end=false"), cell);
			}
		}
		assertTrue(doors > 0, "this station has lift doors");
		assertEquals(java.util.Set.of("landing_bottom", "transition_bottom", "slope", "transition_top", "landing_top"), orientations);
	}

	// ---- over HTTP ------------------------------------------------------------------------------------------------------

	@Test
	void exportEndpointServesTheFileWithItsHeaders() throws Exception {
		final StudioServer server = StudioServer.start(OWN_ASSETS, null, null, 25790);
		try {
			final HttpClient http = HttpClient.newHttpClient();
			final String base = server.url();
			final String token = http.send(HttpRequest.newBuilder(URI.create(base)).build(), HttpResponse.BodyHandlers.ofString()).body()
					.replaceAll("(?s).*data-token=\"([0-9a-f]+)\".*", "$1");
			final HttpResponse<byte[]> get = http.send(HttpRequest.newBuilder(URI.create(base + "api/export?look=UTRECHT&tracks=2&length=40&name=Leiden&seed=k7f2ra&building=1&token=" + token))
					.build(), HttpResponse.BodyHandlers.ofByteArray());
			assertEquals(200, get.statusCode());
			assertEquals("application/octet-stream", get.headers().firstValue("Content-Type").orElse(""));
			assertEquals("attachment; filename=\"leiden-k7f2ra.schem\"", get.headers().firstValue("Content-Disposition").orElse(""));
			final SchematicExport.Parsed parsed = SchematicExport.read(get.body());
			final int blocks = Integer.parseInt(get.headers().firstValue("X-ATA-Blocks").orElse("x"));
			int nonAir = 0;
			for (final String cell : parsed.cells()) {
				nonAir += cell.equals("minecraft:air") ? 0 : 1;
			}
			assertEquals(blocks, nonAir);
			assertTrue(get.headers().firstValue("X-ATA-Fingerprint").orElse("").matches("[0-9a-f]{16}"));
			assertEquals(get.headers().firstValue("X-ATA-Fingerprint").orElse(""), parsed.metadata().get("ATAFingerprint"));
			// no token and no word from the browser that the page asked: refused (a plain client, or an old browser)
			assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(base + "api/export?tracks=2")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
			// from a page on another site: refused, unless it knows the token
			assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(base + "api/export?tracks=2")).header("Sec-Fetch-Site", "cross-site").build(),
					HttpResponse.BodyHandlers.ofString()).statusCode());
			assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(base + "api/export?tracks=2")).header("Sec-Fetch-Site", "same-site").build(),
					HttpResponse.BodyHandlers.ofString()).statusCode());
			assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(base + "api/export?tracks=2&token=0123456789abcdef0123456789abcdef")).build(),
					HttpResponse.BodyHandlers.ofString()).statusCode());
			// typed in the address bar ("none") or followed on the page ("same-origin") is fine
			for (final String site : new String[]{"same-origin", "none"}) {
				assertEquals(200, http.send(HttpRequest.newBuilder(URI.create(base + "api/export?tracks=2&length=24")).header("Sec-Fetch-Site", site).build(),
						HttpResponse.BodyHandlers.ofByteArray()).statusCode(), site);
			}
			// a POST needs the page's token
			final HttpRequest.Builder post = HttpRequest.newBuilder(URI.create(base + "api/export")).POST(HttpRequest.BodyPublishers.ofString("tracks=1&length=24"));
			assertEquals(403, http.send(post.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
			final HttpResponse<byte[]> posted = http.send(post.header("X-Studio-Token", token).build(), HttpResponse.BodyHandlers.ofByteArray());
			assertEquals(200, posted.statusCode());
			assertTrue(posted.headers().firstValue("Content-Disposition").orElse("").contains("station-noseed.schem"));
			// a request that cannot be read is a 400 with the reason
			final HttpResponse<String> tooBig = http.send(HttpRequest.newBuilder(URI.create(base + "api/export")).header("X-Studio-Token", token)
					.POST(HttpRequest.BodyPublishers.ofString("text=" + "x".repeat(20000))).build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(400, tooBig.statusCode());
			assertTrue(tooBig.body().startsWith("{\"error\":"), tooBig.body());
		} finally {
			server.stop();
		}
	}

	/** The seed as the schematic names it. */
	private static final class StationSeedFormat {
		static String of(StationSpec spec) {
			return com.aureliatransit.architecture.station.StationSeed.format(spec.seed());
		}
	}
}
