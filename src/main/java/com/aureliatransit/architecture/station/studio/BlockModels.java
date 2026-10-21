package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.StylePalette;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a planned block (id, facing, extra state) into its real model for Station Studio's 3D view, read from the same
 * blockstate and model files the game uses: the matching variant (or multipart pieces), the parent chain's elements and
 * textures, and the variant's rotation. Mirrors {@code BlockStateResolver.state}: FACING follows the block's front (the ticket
 * machine faces away), axis blocks take the front's axis. Anything unreadable becomes a plain cube with no texture, which the
 * page colours by role.
 */
public final class BlockModels {

	/** Reads one asset ({@code assets/<namespace>/<path>}); null when it does not exist. */
	@FunctionalInterface
	public interface AssetSource {
		byte[] read(String namespace, String path) throws IOException;
	}

	private static final Set<String> FACING_AWAY = Set.of("mtr:ticket_machine", "mtr:tactile_map", "mtr:route_sign_wall_light", "mtr:station_name_entrance");
	private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};

	private final AssetSource assets;
	private final Map<String, Map<String, Object>> files = new HashMap<>();
	private final Map<String, String> described = new HashMap<>();
	private final Map<String, Boolean> full = new HashMap<>();

	public BlockModels(AssetSource assets) {
		this.assets = assets;
	}

	/** The model of {@code blockId} facing {@code facing} with {@code state} extras, as a JSON object; cached per key. */
	public synchronized String describe(String blockId, Heading facing, String state) {
		final String key = key(blockId, facing, state);
		return described.computeIfAbsent(key, k -> build(blockId, facing, state));
	}

	/**
	 * Blocks whose {@code facing} has six values (a barrel, a rod): the game's own placing does not turn them to the plan's front
	 * ({@code BlockStateResolver} sets {@code HORIZONTAL_FACING} only), so they keep their default unless the plan says otherwise.
	 */
	private static final Set<String> SIX_WAY_FACING = Set.of("minecraft:barrel", "minecraft:lightning_rod", "minecraft:end_rod");
	/** MTR blocks that face the way the plan's front says, for when their blockstate files are not at hand (a studio without MTR's jar). */
	private static final Set<String> MTR_FACING = Set.of("mtr:ticket_machine", "mtr:tactile_map", "mtr:route_sign_wall_light", "mtr:station_name_entrance", "mtr:lift_buttons_1",
			"mtr:lift_door_1", "mtr:lift_track_1", "mtr:lift_track_floor_1", "mtr:escalator_step", "mtr:escalator_side", "mtr:glass_fence_plain");

	/**
	 * The block state properties the game gives a planned block before its neighbours join it, sorted by name: FACING or the axis
	 * from the front (the ticket machine and tactile map face away; MTR's rail node takes its boolean flags), then the plan's own
	 * extras. This is what {@code BlockStateResolver.state} and {@code withState} do, worked out from the blockstate file (which
	 * properties the block has) or, without the file, from what is known about the few blocks the planner uses. A property the block
	 * does not have is not listed, so the answer is safe to write into a schematic. Joins (panes, fences, girders) come in with
	 * {@code state}, as {@link Connections#state} gives them.
	 */
	public synchronized Map<String, String> gameProperties(String blockId, Heading facing, String state) {
		final int colon = blockId.indexOf(':');
		final String namespace = colon < 0 ? "minecraft" : blockId.substring(0, colon);
		final String name = blockId.substring(colon + 1);
		final Map<String, Object> blockstate = file(namespace, "blockstates/" + name + ".json");
		final Set<String> names = blockstate == null ? Set.of() : propertyNames(blockstate);
		final Heading heading = facing == null ? Heading.NORTH : facing;
		final Map<String, String> props = new java.util.TreeMap<>();
		if (blockId.equals(StylePalette.MTR_RAIL_NODE)) {
			props.put("facing", Boolean.toString(heading.alongX()));
			props.put("is_45", "false");
			props.put("is_22_5", "false");
			props.put("is_connected", "false");
		} else if (!SIX_WAY_FACING.contains(blockId) && (names.contains("facing") || blockstate == null && (MTR_FACING.contains(blockId) || blockId.endsWith("_stairs")))) {
			props.put("facing", (FACING_AWAY.contains(blockId) ? heading.opposite() : heading).name().toLowerCase(java.util.Locale.ROOT));
		} else if (names.contains("axis")) {
			props.put("axis", heading.alongX() ? "x" : "z");
		}
		for (final String pair : state.split(",")) {
			final int eq = pair.indexOf('=');
			if (eq > 0) {
				props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
			}
		}
		return props;
	}

	/**
	 * Whether the model shows a solid full square on its {@code side} face, as {@code isSideSolidFullSquare} would see it:
	 * one element, after the variant's turn, flush with that side and covering it edge to edge. Cached per key and side.
	 */
	public synchronized boolean fullFace(String blockId, Heading facing, String state, Heading side) {
		final String key = key(blockId, facing, state) + "|" + side.name();
		return full.computeIfAbsent(key, k -> {
			for (final Object part : Json.list(Json.object(Json.parse(describe(blockId, facing, state))).get("parts"))) {
				final Map<String, Object> p = Json.object(part);
				final int rx = (int) Json.number(p.get("x"), 0);
				final int ry = (int) Json.number(p.get("y"), 0);
				for (final Object element : Json.list(p.get("elements"))) {
					final Map<String, Object> e = Json.object(element);
					final double[] box = turned(Json.list(e.get("from")), Json.list(e.get("to")), rx, ry);
					if (covers(box, side)) {
						return true;
					}
				}
			}
			return false;
		});
	}

	/** An element's box [x0, y0, z0, x1, y1, z1] in 16ths after the variant's x then y turn about the block's middle. */
	private static double[] turned(List<Object> from, List<Object> to, int rx, int ry) {
		double[] a = {Json.number(from.get(0), 0), Json.number(from.get(1), 0), Json.number(from.get(2), 0)};
		double[] b = {Json.number(to.get(0), 16), Json.number(to.get(1), 16), Json.number(to.get(2), 16)};
		for (int i = 0; i < Math.floorMod(rx, 360) / 90; i++) {
			// x turn: (y, z) -> (z, 16 - y)
			a = new double[]{a[0], a[2], 16 - a[1]};
			b = new double[]{b[0], b[2], 16 - b[1]};
		}
		for (int i = 0; i < Math.floorMod(ry, 360) / 90; i++) {
			// y turn, north to east: (x, z) -> (16 - z, x)
			a = new double[]{16 - a[2], a[1], a[0]};
			b = new double[]{16 - b[2], b[1], b[0]};
		}
		return new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]),
				Math.max(a[2], b[2])};
	}

	private static boolean covers(double[] box, Heading side) {
		final double e = 0.01;
		final boolean tall = box[1] <= e && box[4] >= 16 - e;
		return switch (side) {
			case NORTH -> box[2] <= e && tall && box[0] <= e && box[3] >= 16 - e;
			case SOUTH -> box[5] >= 16 - e && tall && box[0] <= e && box[3] >= 16 - e;
			case WEST -> box[0] <= e && tall && box[2] <= e && box[5] >= 16 - e;
			case EAST -> box[3] >= 16 - e && tall && box[2] <= e && box[5] >= 16 - e;
		};
	}

	public static String key(String blockId, Heading facing, String state) {
		return blockId + "|" + (facing == null ? "" : facing.name()) + "|" + state;
	}

	private String build(String blockId, Heading facing, String state) {
		final int colon = blockId.indexOf(':');
		final String namespace = colon < 0 ? "minecraft" : blockId.substring(0, colon);
		final String name = blockId.substring(colon + 1);
		final Map<String, Object> blockstate = file(namespace, "blockstates/" + name + ".json");
		final List<Map<String, Object>> picks = new ArrayList<>();
		if (blockstate != null) {
			final Map<String, String> set = properties(blockId, blockstate, facing, state);
			final Map<String, String> props = new LinkedHashMap<>(set);
			for (final Map.Entry<String, List<String>> property : propertyValues(blockstate).entrySet()) {
				if (!props.containsKey(property.getKey()) && !property.getValue().isEmpty()) {
					props.put(property.getKey(), guessDefault(property.getKey(), property.getValue()));
				}
			}
			final Map<String, Object> variants = Json.object(blockstate.get("variants"));
			if (!variants.isEmpty()) {
				final Map<String, Object> pick = bestVariant(variants, set, props);
				if (pick != null) {
					picks.add(pick);
				}
			}
			for (final Object part : Json.list(blockstate.get("multipart"))) {
				final Map<String, Object> piece = Json.object(part);
				if (!piece.containsKey("when") || when(Json.object(piece.get("when")), props)) {
					final Object apply = piece.get("apply");
					picks.add(apply instanceof List<?> ? Json.object(Json.list(apply).get(0)) : Json.object(apply));
				}
			}
		}
		final StringBuilder out = new StringBuilder("{\"parts\":[");
		boolean first = true;
		for (final Map<String, Object> pick : picks) {
			final String model = model(Json.string(pick.get("model"), ""), (int) Json.number(pick.get("x"), 0), (int) Json.number(pick.get("y"), 0));
			if (model != null) {
				out.append(first ? "" : ",").append(model);
				first = false;
			}
		}
		return out.append("]}").toString();
	}

	/** The block's property values as the game would set them: FACING or axis from the front, then the plan's extras. */
	private static Map<String, String> properties(String blockId, Map<String, Object> blockstate, Heading facing, String state) {
		final Set<String> names = propertyNames(blockstate);
		final Map<String, String> props = new LinkedHashMap<>();
		final Heading heading = facing == null ? Heading.NORTH : facing;
		if (blockId.equals(StylePalette.MTR_RAIL_NODE)) {
			props.put("facing", Boolean.toString(heading.alongX()));
		} else if (names.contains("facing")) {
			props.put("facing", (FACING_AWAY.contains(blockId) ? heading.opposite() : heading).name().toLowerCase(java.util.Locale.ROOT));
		} else if (names.contains("axis")) {
			props.put("axis", heading.alongX() ? "x" : "z");
		}
		for (final String pair : state.split(",")) {
			final int eq = pair.indexOf('=');
			if (eq > 0) {
				props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
			}
		}
		return props;
	}

	private static Set<String> propertyNames(Map<String, Object> blockstate) {
		return propertyValues(blockstate).keySet();
	}

	/** Every property the blockstate file mentions, with the values it mentions, in file order. */
	private static Map<String, List<String>> propertyValues(Map<String, Object> blockstate) {
		final Map<String, List<String>> values = new LinkedHashMap<>();
		for (final String key : Json.object(blockstate.get("variants")).keySet()) {
			for (final String pair : key.split(",")) {
				final int eq = pair.indexOf('=');
				if (eq > 0) {
					addValue(values, pair.substring(0, eq), pair.substring(eq + 1));
				}
			}
		}
		for (final Object part : Json.list(blockstate.get("multipart"))) {
			collectWhen(Json.object(Json.object(part).get("when")), values);
		}
		return values;
	}

	private static void collectWhen(Map<String, Object> when, Map<String, List<String>> values) {
		for (final Map.Entry<String, Object> entry : when.entrySet()) {
			if (entry.getKey().equals("OR") || entry.getKey().equals("AND")) {
				for (final Object inner : Json.list(entry.getValue())) {
					collectWhen(Json.object(inner), values);
				}
			} else {
				final Object raw = entry.getValue();
				for (final String option : String.valueOf(raw instanceof Boolean b ? b : Json.string(raw, "")).split("\\|")) {
					addValue(values, entry.getKey(), option);
				}
			}
		}
	}

	private static void addValue(Map<String, List<String>> values, String name, String value) {
		final List<String> list = values.computeIfAbsent(name, k -> new ArrayList<>());
		if (!list.contains(value)) {
			list.add(value);
		}
	}

	/**
	 * A guess at the game's default for a property the plan does not set: false or none when the property has them (sides
	 * that are not connected), else the first value the file names.
	 */
	private static String guessDefault(String name, List<String> values) {
		return values.contains("false") ? "false" : values.contains("none") ? "none" : UNNAMED_DEFAULTS.getOrDefault(name, values.get(0));
	}

	/**
	 * Defaults the blockstate files never name because they draw nothing: a railing's {@code corner} is {@code none}
	 * ({@code RailingBlock.Corner.NONE}); guessing its first value would stand a corner post on every railing.
	 */
	private static final Map<String, String> UNNAMED_DEFAULTS = Map.of("corner", "none");

	/** The variant agreeing with the most properties: the plan's own count for more than the guessed defaults. */
	private static Map<String, Object> bestVariant(Map<String, Object> variants, Map<String, String> set, Map<String, String> props) {
		Map<String, Object> best = null;
		int bestScore = -1;
		for (final Map.Entry<String, Object> entry : variants.entrySet()) {
			int score = 0;
			boolean ok = true;
			if (!entry.getKey().isEmpty() && !entry.getKey().equals("normal")) {
				for (final String pair : entry.getKey().split(",")) {
					final int eq = pair.indexOf('=');
					final String name = pair.substring(0, Math.max(eq, 0));
					final String value = pair.substring(eq + 1);
					if (set.containsKey(name)) {
						if (!set.get(name).equals(value)) {
							ok = false;
							break;
						}
						score += 100;
					} else if (value.equals(props.get(name))) {
						score++;
					}
				}
			}
			if (ok && score > bestScore) {
				bestScore = score;
				final Object value = entry.getValue();
				best = value instanceof List<?> ? Json.object(Json.list(value).get(0)) : Json.object(value);
			}
		}
		return best;
	}

	/** A multipart condition against the block's properties (set or guessed). */
	private static boolean when(Map<String, Object> when, Map<String, String> props) {
		for (final Map.Entry<String, Object> entry : when.entrySet()) {
			if (entry.getKey().equals("OR")) {
				boolean any = false;
				for (final Object inner : Json.list(entry.getValue())) {
					any |= when(Json.object(inner), props);
				}
				if (!any) {
					return false;
				}
				continue;
			}
			if (entry.getKey().equals("AND")) {
				for (final Object inner : Json.list(entry.getValue())) {
					if (!when(Json.object(inner), props)) {
						return false;
					}
				}
				continue;
			}
			final String have = props.getOrDefault(entry.getKey(), "");
			final String wanted = String.valueOf(entry.getValue() instanceof Boolean b ? b : Json.string(entry.getValue(), ""));
			boolean match = false;
			for (final String option : wanted.split("\\|")) {
				match |= option.equals(have);
			}
			if (!match) {
				return false;
			}
		}
		return true;
	}

	/** One model part: its elements (from the nearest model in the parent chain that has some) with textures resolved. */
	private String model(String id, int x, int y) {
		final Map<String, String> textures = new HashMap<>();
		List<Object> elements = null;
		String current = id;
		boolean ambient = true;
		for (int depth = 0; depth < 16 && !current.isEmpty(); depth++) {
			final int colon = current.indexOf(':');
			final String namespace = colon < 0 ? "minecraft" : current.substring(0, colon);
			final String path = current.substring(colon + 1);
			if (path.startsWith("builtin/")) {
				break;
			}
			final Map<String, Object> model = file(namespace, "models/" + path + ".json");
			if (model == null) {
				break;
			}
			for (final Map.Entry<String, Object> t : Json.object(model.get("textures")).entrySet()) {
				textures.putIfAbsent(t.getKey(), Json.string(t.getValue(), ""));
			}
			if (elements == null && model.get("elements") != null) {
				elements = Json.list(model.get("elements"));
			}
			if (Boolean.FALSE.equals(model.get("ambientocclusion"))) {
				ambient = false;
			}
			current = Json.string(model.get("parent"), "");
		}
		final StringBuilder out = new StringBuilder("{\"x\":").append(x).append(",\"y\":").append(y).append(",\"ao\":").append(ambient)
				.append(",\"elements\":[");
		if (elements == null) {
			// a model drawn in code (or one this reader cannot follow): a full cube with its particle texture
			final String particle = texture("#particle", textures);
			out.append("{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{");
			for (int i = 0; i < FACES.length; i++) {
				out.append(i == 0 ? "" : ",").append('"').append(FACES[i]).append("\":{\"t\":").append(particle == null ? "null" : Json.quote(particle)).append('}');
			}
			return out.append("}}]}").toString();
		}
		boolean firstElement = true;
		for (final Object raw : elements) {
			final Map<String, Object> element = Json.object(raw);
			out.append(firstElement ? "" : ",").append("{\"from\":").append(numbers(element.get("from"))).append(",\"to\":")
					.append(numbers(element.get("to")));
			firstElement = false;
			final Map<String, Object> rotation = Json.object(element.get("rotation"));
			if (!rotation.isEmpty() && Json.number(rotation.get("angle"), 0) != 0) {
				out.append(",\"rot\":{\"axis\":").append(Json.quote(Json.string(rotation.get("axis"), "y"))).append(",\"angle\":")
						.append(Json.number(rotation.get("angle"), 0)).append(",\"origin\":").append(numbers(rotation.get("origin")))
						.append(",\"rescale\":").append(Boolean.TRUE.equals(rotation.get("rescale"))).append('}');
			}
			if (Boolean.FALSE.equals(element.get("shade"))) {
				out.append(",\"shade\":false");
			}
			out.append(",\"faces\":{");
			boolean firstFace = true;
			for (final Map.Entry<String, Object> face : Json.object(element.get("faces")).entrySet()) {
				final Map<String, Object> f = Json.object(face.getValue());
				final String texture = texture(Json.string(f.get("texture"), ""), textures);
				out.append(firstFace ? "" : ",").append(Json.quote(face.getKey())).append(":{\"t\":").append(texture == null ? "null" : Json.quote(texture));
				firstFace = false;
				if (f.get("uv") != null) {
					out.append(",\"uv\":").append(numbers(f.get("uv")));
				}
				if (f.get("rotation") != null) {
					out.append(",\"r\":").append((int) Json.number(f.get("rotation"), 0));
				}
				if (f.get("tintindex") != null) {
					out.append(",\"tint\":").append((int) Json.number(f.get("tintindex"), 0));
				}
				out.append('}');
			}
			out.append("}}");
		}
		return out.append("]}").toString();
	}

	/** Follows {@code #name} references to a texture id, written as the studio's texture URL path ({@code ns/block/x}). */
	private static String texture(String reference, Map<String, String> textures) {
		String value = reference;
		for (int i = 0; i < 16 && value.startsWith("#"); i++) {
			value = textures.getOrDefault(value.substring(1), "");
		}
		if (value.isEmpty() || value.startsWith("#")) {
			return null;
		}
		final int colon = value.indexOf(':');
		return (colon < 0 ? "minecraft" : value.substring(0, colon)) + "/" + value.substring(colon + 1);
	}

	private static String numbers(Object value) {
		final StringBuilder out = new StringBuilder("[");
		for (final Object n : Json.list(value)) {
			if (out.length() > 1) {
				out.append(',');
			}
			final double d = Json.number(n, 0);
			out.append(d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d));
		}
		return out.append(']').toString();
	}

	/** Whether the game has a block with this id (its blockstate file is in the assets), so a block swap to it would build. */
	public synchronized boolean known(String blockId) {
		final int colon = blockId.indexOf(':');
		return colon > 0 && file(blockId.substring(0, colon), "blockstates/" + blockId.substring(colon + 1) + ".json") != null;
	}

	private Map<String, Object> file(String namespace, String path) {
		return files.computeIfAbsent(namespace + ":" + path, k -> {
			try {
				final byte[] bytes = assets.read(namespace, path);
				return bytes == null ? null : Json.object(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
			} catch (IOException | RuntimeException e) {
				return null;
			}
		});
	}
}
