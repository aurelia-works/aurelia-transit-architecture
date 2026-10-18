package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlanFingerprint;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.StationPlan;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A station plan as a Sponge Schematic version 2 file (".schem"), the format WorldEdit, FastAsyncWorldEdit and Litematica's importer
 * read. Written without Minecraft classes, so the studio can export outside the game.
 *
 * <p>Layout (what WorldEdit writes and Indus's exports match): a gzipped root compound named {@code Schematic} with {@code Version}
 * 2, {@code DataVersion} (1.20.1), {@code Width}/{@code Height}/{@code Length} as shorts, {@code Offset} [0, 0, 0], a {@code Palette}
 * of full block states to ids with {@code PaletteMax}, {@code BlockData} (one varint per cell, index {@code (y * Length + z) * Width + x},
 * air is palette 0) and an empty {@code BlockEntities}. The paste origin is in {@code Metadata} as WorldEdit's {@code WEOffsetX/Y/Z}
 * (the clipboard's corner minus the origin): the point in front of the street entrance, at the station's ground level, so
 * standing there and pasting puts the station in front of you. Our own facts are in {@code Metadata} too ({@code ATAVersion},
 * {@code ATASeed}, {@code ATAFacing}, {@code ATAClass}, {@code ATAEra}, {@code ATAFingerprint}, {@code ATADescription}, ...).
 *
 * <p>Block states are what {@link BlockModels#gameProperties} gives (facing or axis from the plan's front, the plan's own extras) plus
 * the joins {@link Connections} works out, so panes, rails and girders stand joined as they do in the game. There are no block entities:
 * a name sign's text, a lift's MTR data, the MTR station zone and rail connections are not in the file; the in-game build and
 * {@code /ata_station verify} cover them.
 *
 * <p>The file is deterministic: the same plan, frame and metadata give the same bytes.
 */
public final class SchematicExport {

	/** Minecraft 1.20.1's data version. */
	public static final int DATA_VERSION = 3465;
	public static final String AIR = "minecraft:air";

	private SchematicExport() {
	}

	/**
	 * @param bytes       the gzipped file
	 * @param blocks      planned blocks written (not counting air)
	 * @param palette     palette entries, air first
	 * @param width       size along x
	 * @param height      size along y
	 * @param length      size along z
	 * @param minX        world-frame x of the clipboard's corner (the frame is {@code LocalFrame} with its origin at 0, 0, 0)
	 * @param minY        y of the corner (rail level is 0)
	 * @param minZ        z of the corner
	 * @param originX     the paste origin's x, as the same coordinates
	 * @param originY     the paste origin's y
	 * @param originZ     the paste origin's z
	 * @param fingerprint {@link PlanFingerprint} of the plan
	 */
	public record Result(byte[] bytes, int blocks, List<String> palette, int width, int height, int length, int minX, int minY, int minZ,
			int originX, int originY, int originZ, String fingerprint) {
	}

	/**
	 * Writes {@code plan} placed in {@code frame}. {@code origin} is the paste origin as {@code {u, y, v}} in the plan's own frame
	 * (the clipboard is enlarged to hold it); {@code info} is written as {@code ATA<Key>} strings in the metadata, in order.
	 */
	public static Result export(StationPlan plan, LocalFrame frame, BlockModels models, int[] origin, String name, Map<String, String> info) {
		final Map<Long, PlannedBlock> at = new HashMap<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (final PlannedBlock block : plan.blocks()) {
			final int x = frame.x(block.u(), block.v());
			final int z = frame.z(block.u(), block.v());
			at.put(StudioApi.cell(x, block.y(), z), block);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, block.y());
			maxY = Math.max(maxY, block.y());
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		if (at.isEmpty()) {
			throw new IllegalArgumentException("the plan has no blocks");
		}
		final int originX = frame.x(origin[0], origin[2]);
		final int originY = origin[1];
		final int originZ = frame.z(origin[0], origin[2]);
		minX = Math.min(minX, originX);
		maxX = Math.max(maxX, originX);
		minY = Math.min(minY, originY);
		maxY = Math.max(maxY, originY);
		minZ = Math.min(minZ, originZ);
		maxZ = Math.max(maxZ, originZ);
		final int width = maxX - minX + 1;
		final int height = maxY - minY + 1;
		final int length = maxZ - minZ + 1;
		if (width > Short.MAX_VALUE || height > Short.MAX_VALUE || length > Short.MAX_VALUE) {
			throw new IllegalArgumentException("a schematic cannot be bigger than 32767 blocks along one side");
		}

		final Connections.FullFace full = Connections.withMtrShapes((other, heading, side) -> models.fullFace(other.blockId(), heading, other.state(), side), at, frame);
		final Map<String, Integer> palette = new LinkedHashMap<>();
		palette.put(AIR, 0);
		final int[] cells = new int[Math.multiplyExact(Math.multiplyExact(width, height), length)];
		final Map<String, Integer> stateIndex = new HashMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			final Heading heading = frame.heading(block.front());
			final String joined = Connections.finishedState(block, at, full, frame);
			final String key = block.blockId() + "|" + heading + "|" + joined;
			final int index = stateIndex.computeIfAbsent(key, k -> palette.computeIfAbsent(
					stateString(block.blockId(), models.gameProperties(block.blockId(), heading, joined)), s -> palette.size()));
			final int x = frame.x(block.u(), block.v()) - minX;
			final int y = block.y() - minY;
			final int z = frame.z(block.u(), block.v()) - minZ;
			cells[(y * length + z) * width + x] = index;
		}

		final ByteArrayOutputStream data = new ByteArrayOutputStream(cells.length);
		for (final int cell : cells) {
			int value = cell;
			while ((value & -128) != 0) {
				data.write(value & 127 | 128);
				value >>>= 7;
			}
			data.write(value);
		}

		final Map<String, Object> paletteTag = new LinkedHashMap<>();
		palette.forEach(paletteTag::put);
		final Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("Name", name.isEmpty() ? "Station" : name);
		metadata.put("Author", "Aurelia Transit Architecture");
		metadata.put("RequiredMods", List.of("aurelia_transit_architecture", "mtr"));
		metadata.put("WEOffsetX", minX - originX);
		metadata.put("WEOffsetY", minY - originY);
		metadata.put("WEOffsetZ", minZ - originZ);
		final String fingerprint = PlanFingerprint.of(plan);
		metadata.put("ATAFingerprint", fingerprint);
		info.forEach((key, value) -> metadata.put("ATA" + key, value));

		final Map<String, Object> root = new LinkedHashMap<>();
		root.put("Version", 2);
		root.put("DataVersion", DATA_VERSION);
		root.put("Metadata", metadata);
		root.put("Width", (short) width);
		root.put("Height", (short) height);
		root.put("Length", (short) length);
		root.put("Offset", new int[]{0, 0, 0});
		root.put("PaletteMax", palette.size());
		root.put("Palette", paletteTag);
		root.put("BlockData", data.toByteArray());
		root.put("BlockEntities", new ArrayList<>());
		return new Result(Nbt.writeGzip("Schematic", root), plan.blocks().size(), List.copyOf(palette.keySet()), width, height, length, minX, minY, minZ,
				originX, originY, originZ, fingerprint);
	}

	/** {@code id[name=value,...]} with the properties in name order, or just the id when there are none. */
	public static String stateString(String blockId, Map<String, String> properties) {
		if (properties.isEmpty()) {
			return blockId;
		}
		final StringBuilder out = new StringBuilder(blockId).append('[');
		boolean first = true;
		for (final Map.Entry<String, String> entry : new java.util.TreeMap<>(properties).entrySet()) {
			out.append(first ? "" : ",").append(entry.getKey()).append('=').append(entry.getValue());
			first = false;
		}
		return out.append(']').toString();
	}

	/**
	 * Reads back a file written by {@link #export}, or any Sponge version 2 schematic with a palette and varint block data: the
	 * size, the corner-to-origin offset and each cell's state string, in {@code (y * length + z) * width + x} order.
	 */
	public static Parsed read(byte[] gzip) {
		final Map<String, Object> root = Nbt.readGzip(gzip).value();
		if (!Integer.valueOf(2).equals(root.get("Version"))) {
			throw new IllegalArgumentException("not a Sponge schematic version 2");
		}
		final int width = ((Number) root.get("Width")).intValue() & 0xFFFF;
		final int height = ((Number) root.get("Height")).intValue() & 0xFFFF;
		final int length = ((Number) root.get("Length")).intValue() & 0xFFFF;
		@SuppressWarnings("unchecked")
		final Map<String, Object> paletteTag = (Map<String, Object>) root.get("Palette");
		final String[] states = new String[paletteTag.size()];
		for (final Map.Entry<String, Object> entry : paletteTag.entrySet()) {
			states[((Number) entry.getValue()).intValue()] = entry.getKey();
		}
		final byte[] data = (byte[]) root.get("BlockData");
		final String[] cells = new String[width * height * length];
		int at = 0;
		for (int i = 0; i < cells.length; i++) {
			int value = 0;
			int shift = 0;
			int b;
			do {
				b = data[at++];
				value |= (b & 127) << shift;
				shift += 7;
			} while ((b & 128) != 0);
			cells[i] = states[value];
		}
		@SuppressWarnings("unchecked")
		final Map<String, Object> metadata = (Map<String, Object>) root.getOrDefault("Metadata", Map.of());
		return new Parsed(width, height, length, ((Number) metadata.getOrDefault("WEOffsetX", 0)).intValue(),
				((Number) metadata.getOrDefault("WEOffsetY", 0)).intValue(), ((Number) metadata.getOrDefault("WEOffsetZ", 0)).intValue(), cells, metadata,
				((Number) root.get("DataVersion")).intValue(), ((Number) root.get("PaletteMax")).intValue());
	}

	/** A schematic read back: {@code cells} hold each position's state string. */
	public record Parsed(int width, int height, int length, int weOffsetX, int weOffsetY, int weOffsetZ, String[] cells, Map<String, Object> metadata,
			int dataVersion, int paletteMax) {

		public String at(int x, int y, int z) {
			return cells[(y * length + z) * width + x];
		}

		/**
		 * The state string of {@code block}, planned in {@code frame} and exported with {@code origin} ({@code {u, y, v}}, see
		 * {@link com.aureliatransit.architecture.station.StationPlacement#pasteOrigin}) as the paste origin.
		 */
		public String of(PlannedBlock block, LocalFrame frame, int[] origin) {
			final int x = frame.x(block.u(), block.v()) - (frame.x(origin[0], origin[2]) + weOffsetX);
			final int y = block.y() - (origin[1] + weOffsetY);
			final int z = frame.z(block.u(), block.v()) - (frame.z(origin[0], origin[2]) + weOffsetZ);
			return at(x, y, z);
		}
	}
}
