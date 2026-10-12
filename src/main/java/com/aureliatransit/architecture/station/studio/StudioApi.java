package com.aureliatransit.architecture.station.studio;

import com.aureliatransit.architecture.station.BuildingStyle;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LayoutValidator;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationGeometry;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.TrackLayout;
import com.aureliatransit.architecture.station.Violation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Station Studio's answers, as JSON text. {@link #plan} runs the same parser, planner, design rules and station checks as
 * {@code /ata_station plan}, from the studio's controls ({@link StationRecipe}) plus the typed description, and lists every
 * block with its real model so the page can draw the station before anything is built.
 */
public final class StudioApi {

	/** The studio's own limit for the typed words; the controls' words come on top. */
	public static final int MAX_TYPED = 1000;

	/** The frame the studio draws in: +x is +u, -z is +v (the game's frame for a player looking north). */
	public static final LocalFrame FRAME = new LocalFrame(0, 0, 0, Heading.NORTH);

	private final BlockModels models;

	public StudioApi(BlockModels models) {
		this.models = models;
	}

	/** The controls a request carries; missing or unreadable fields keep the defaults. */
	public static StationRecipe recipe(Map<String, String> q) {
		StationRecipe recipe = StationRecipe.defaults();
		recipe = recipe.withLook(look(q.get("look"), StationRecipe.Look.AUTO));
		recipe = recipe.withMix(look(q.get("mix"), null));
		recipe = recipe.withTerminus("terminus".equals(q.get("mode"))).withHub("hub".equals(q.get("mode")));
		recipe = recipe.withTracks(number(q.get("tracks"), recipe.tracks()));
		recipe = recipe.withPlatforms(value(StationRecipe.Platforms.class, q.get("layout"), StationRecipe.Platforms.AUTO));
		recipe = recipe.withLength(number(q.get("length"), recipe.length()));
		recipe = recipe.withRoof(value(StationRecipe.Roof.class, q.get("roof"), StationRecipe.Roof.AUTO));
		recipe = recipe.withBuilding(!"0".equals(q.get("building")));
		recipe = recipe.withSize(value(StationRecipe.Size.class, q.get("size"), StationRecipe.Size.AUTO));
		return recipe.withName(q.getOrDefault("name", ""));
	}

	/** The typed words of a request, cut to {@link #MAX_TYPED}. */
	public static String typed(Map<String, String> q) {
		final String text = q.getOrDefault("text", "").replace('\n', ' ').strip();
		return text.length() > MAX_TYPED ? text.substring(0, MAX_TYPED) : text;
	}

	/** The choices the page offers and their limits. */
	public String options() {
		final StringBuilder out = new StringBuilder("{");
		out.append("\"looks\":").append(labels(StationRecipe.Look.values()));
		out.append(",\"layouts\":").append(labels(StationRecipe.Platforms.values()));
		out.append(",\"roofs\":").append(labels(StationRecipe.Roof.values()));
		out.append(",\"sizes\":").append(labels(StationRecipe.Size.values()));
		out.append(",\"tracks\":[").append(StationRecipe.MIN_TRACKS).append(',').append(StationRecipe.MAX_TRACKS).append(']');
		out.append(",\"length\":[").append(StationRecipe.MIN_LENGTH).append(',').append(StationRecipe.MAX_LENGTH).append(',')
				.append(StationRecipe.DEFAULT_LENGTH).append(']');
		out.append(",\"maxTyped\":").append(MAX_TYPED).append(",\"maxName\":").append(StationSpec.MAX_NAME);
		out.append(",\"presets\":[");
		for (int i = 0; i < StationPresets.ALL.size(); i++) {
			final StationPresets.Preset preset = StationPresets.ALL.get(i);
			out.append(i == 0 ? "" : ",").append(preset(preset));
		}
		return out.append("]}").toString();
	}

	/**
	 * A preset as the page's controls: its tracks, length, type, platforms, city and name set the buttons, and the rest of its
	 * words (the roof, the footbridge) go in the description. The controls are always sent, so without this they would win
	 * over the preset's own numbers.
	 */
	static String preset(StationPresets.Preset preset) {
		final StationSpec spec = StationDescriptionParser.parse(preset.description()).spec();
		final String words = preset.description().replaceFirst(",?\\s*named .*$", "");
		return "{\"key\":" + Json.quote(preset.key()) + ",\"description\":" + Json.quote(preset.description()) + ",\"text\":" + Json.quote(words)
				+ ",\"tracks\":" + spec.trackCount() + ",\"length\":" + spec.platformLength() + ",\"mode\":" + Json.quote(spec.hub() ? "hub" : spec.terminus() ? "terminus" : "through")
				+ ",\"layout\":" + Json.quote(spec.platformLayout().name()) + ",\"look\":" + Json.quote(look(preset.building()).name())
				+ ",\"name\":" + Json.quote(spec.name()) + "}";
	}

	/** The wrench's city button for a building look. */
	static StationRecipe.Look look(BuildingStyle building) {
		return switch (building) {
			case AMSTERDAM -> StationRecipe.Look.AMSTERDAM;
			case GOTHIC_BRICK -> StationRecipe.Look.ST_PANCRAS;
			case ANTWERP -> StationRecipe.Look.ANTWERP;
			case SANDSTONE -> StationRecipe.Look.FRANKFURT;
			case CLINKER -> StationRecipe.Look.HAMBURG;
			case CONCRETE -> StationRecipe.Look.MUNICH;
			case BEAUX_ARTS -> StationRecipe.Look.NEW_YORK;
			case MODERN -> StationRecipe.Look.UTRECHT;
			case NONE -> StationRecipe.Look.AUTO;
		};
	}

	/** Plans the request's station and describes it for the page. */
	public String plan(Map<String, String> q) {
		final StationRecipe recipe = recipe(q);
		final String typed = typed(q);
		final String text = recipe.text(typed);
		final long started = System.nanoTime();
		final ParseResult parse = StationDescriptionParser.parse(text);
		final StationPlan plan = StationPlanner.plan(parse.spec());
		final List<Violation> violations = LayoutValidator.validate(plan);
		final StationAudit.Result audit = StationAudit.audit(plan);
		final long planned = System.nanoTime();
		final StationSpec spec = parse.spec();
		final TrackLayout layout = plan.layout();

		final Map<Long, String> idAt = new java.util.HashMap<>();
		for (final PlannedBlock block : plan.blocks()) {
			idAt.put(cell(FRAME.x(block.u(), block.v()), block.y(), FRAME.z(block.u(), block.v())), block.blockId());
		}
		final Map<String, Integer> modelIndex = new LinkedHashMap<>();
		final List<String> modelJson = new ArrayList<>();
		final StringBuilder cells = new StringBuilder();
		for (final PlannedBlock block : plan.blocks()) {
			final Heading heading = FRAME.heading(block.front());
			final String state = withConnections(block, idAt);
			final String key = BlockModels.key(block.blockId(), heading, state);
			final int model = modelIndex.computeIfAbsent(key, k -> {
				modelJson.add("{\"id\":" + Json.quote(block.blockId()) + ",\"model\":" + models.describe(block.blockId(), heading, state) + "}");
				return modelJson.size() - 1;
			});
			if (cells.length() > 0) {
				cells.append(',');
			}
			cells.append(FRAME.x(block.u(), block.v())).append(',').append(block.y()).append(',').append(FRAME.z(block.u(), block.v())).append(',')
					.append(model).append(',').append(block.role().ordinal());
		}

		final StringBuilder out = new StringBuilder("{");
		out.append("\"text\":").append(Json.quote(text));
		out.append(",\"title\":").append(Json.quote(recipe.title()));
		out.append(",\"encoded\":").append(Json.quote(recipe.encode()));
		out.append(",\"typed\":").append(Json.quote(typed));
		out.append(",\"understood\":").append(Json.strings(parse.understood()));
		out.append(",\"defaulted\":").append(Json.strings(parse.defaulted()));
		out.append(",\"warnings\":").append(Json.strings(parse.warnings()));
		out.append(",\"ignored\":").append(Json.strings(parse.ignored()));
		out.append(",\"requested\":{\"mode\":").append(Json.quote(recipe.hub() ? "Hub" : recipe.terminus() ? "Terminus" : "Through station"))
				.append(",\"layout\":").append(Json.quote(recipe.platforms().label())).append(",\"tracks\":").append(recipe.tracks())
				.append(",\"length\":").append(recipe.length()).append(",\"look\":").append(Json.quote(recipe.look().label()))
				.append(",\"roof\":").append(Json.quote(recipe.roof().label())).append(",\"building\":").append(recipe.building())
				.append(",\"size\":").append(Json.quote(recipe.size().label())).append('}');
		out.append(",\"built\":{\"mode\":").append(Json.quote(spec.hub() ? "Hub" : spec.terminus() ? "Terminus" : "Through station"))
				.append(",\"layout\":").append(Json.quote(lower(spec.platformLayout().name()) + " platforms"))
				.append(",\"tracks\":").append(spec.trackCount()).append(",\"platforms\":").append(layout.platforms().size())
				.append(",\"faces\":").append(audit.faces().size()).append(",\"length\":").append(spec.platformLength())
				.append(",\"platformWidth\":").append(spec.platformWidth()).append(",\"style\":").append(Json.quote(lower(spec.style().name())))
				.append(",\"building\":").append(Json.quote(spec.building().label()))
				.append(",\"scale\":").append(Json.quote(spec.sideBuilding() ? spec.scale().label() : "-"))
				.append(",\"roof\":").append(Json.quote(roof(spec))).append(",\"footbridge\":").append(spec.footbridge())
				.append(",\"name\":").append(Json.quote(spec.name())).append('}');
		out.append(",\"size\":{\"width\":").append(audit.width()).append(",\"length\":").append(audit.length()).append(",\"height\":")
				.append(audit.height()).append(",\"blocks\":").append(plan.blocks().size()).append('}');
		out.append(",\"buildable\":").append(violations.isEmpty() && audit.buildable());
		out.append(",\"checks\":[");
		for (int i = 0; i < audit.checks().size(); i++) {
			final StationAudit.Check c = audit.checks().get(i);
			out.append(i == 0 ? "" : ",").append("{\"topic\":").append(Json.quote(lower(c.topic().name()))).append(",\"status\":")
					.append(Json.quote(c.status().name())).append(",\"message\":").append(Json.quote(c.message())).append('}');
		}
		out.append("],\"violations\":[");
		for (int i = 0; i < violations.size(); i++) {
			final Violation v = violations.get(i);
			out.append(i == 0 ? "" : ",").append("{\"kind\":").append(Json.quote(v.kind().name())).append(",\"x\":").append(FRAME.x(v.u(), v.v()))
					.append(",\"y\":").append(v.y()).append(",\"z\":").append(FRAME.z(v.u(), v.v())).append(",\"message\":")
					.append(Json.quote(v.message())).append('}');
		}
		out.append("],\"faceLabels\":[");
		for (int i = 0; i < audit.faces().size(); i++) {
			final StationAudit.Face face = audit.faces().get(i);
			final TrackLayout.Track track = layout.tracks().get(face.track() - 1 < 0 ? 0 : Math.min(face.track() - 1, layout.tracks().size() - 1));
			final int u = track.u() + (face.leftOfTrack() ? -2 : 2);
			final int v = face.vStart() + face.length() / 2;
			out.append(i == 0 ? "" : ",").append("{\"n\":").append(i + 1).append(",\"platform\":").append(face.platform()).append(",\"x\":")
					.append(FRAME.x(u, v)).append(",\"z\":").append(FRAME.z(u, v)).append(",\"view\":").append(faceView(face, track, idAt))
					.append('}');
		}
		out.append("],\"passenger\":").append(passenger(layout, idAt));
		out.append(",\"roles\":").append(Json.strings(names(Role.values())));
		out.append(",\"models\":[").append(String.join(",", modelJson)).append(']');
		out.append(",\"cells\":[").append(cells).append(']');
		out.append(",\"ms\":").append((planned - started) / 1_000_000);
		return out.append('}').toString();
	}

	/**
	 * Rails, fences and panes join their neighbours in the game (worked out from the world when placed); the studio joins each
	 * side that has a block of the same family next to it. {@code idAt} maps {@link #cell} keys in {@link #FRAME} to block ids.
	 */
	public static String withConnections(PlannedBlock block, Map<Long, String> idAt) {
		final String family = family(block.blockId());
		if (family == null) {
			return block.state();
		}
		final int x = FRAME.x(block.u(), block.v());
		final int z = FRAME.z(block.u(), block.v());
		final StringBuilder out = new StringBuilder(block.state());
		final String[] names = {"north", "east", "south", "west"};
		final int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int i = 0; i < 4; i++) {
			final String other = idAt.get(cell(x + steps[i][0], block.y(), z + steps[i][1]));
			final boolean joined = other != null && family.equals(family(other));
			out.append(out.length() == 0 ? "" : ",").append(names[i]).append('=').append(joined);
		}
		return out.toString();
	}

	private static String family(String blockId) {
		for (final String word : new String[]{"handrail", "railing", "fence", "pane", "wall", "bars", "barrier"}) {
			if (blockId.contains(word)) {
				return word;
			}
		}
		return null;
	}

	public static long cell(int x, int y, int z) {
		return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
	}

	/**
	 * Where a passenger stands for the Passenger view: on the first platform near its start, in a cell with three clear blocks
	 * above the floor (not in a column or a stair), looking along the platform.
	 */
	private static String passenger(TrackLayout layout, Map<Long, String> idAt) {
		if (layout.platforms().isEmpty()) {
			return "null";
		}
		final TrackLayout.PlatformBand band = layout.platforms().get(0);
		final int middle = (band.uMin() + band.uMax()) / 2;
		for (int v = 3; v < layout.platformLength() / 2; v++) {
			for (int du = 0; du <= band.width(); du++) {
				final int u = middle + (du % 2 == 0 ? du / 2 : -(du / 2 + 1));
				if (u < band.uMin() || u > band.uMax()) {
					continue;
				}
				final int x = FRAME.x(u, v);
				final int z = FRAME.z(u, v);
				if (!idAt.containsKey(cell(x, 1, z)) && !idAt.containsKey(cell(x, 2, z)) && !idAt.containsKey(cell(x, 3, z))) {
					return "{\"x\":" + x + ",\"z\":" + z + ",\"tx\":" + FRAME.x(u, layout.platformLength()) + ",\"tz\":"
							+ FRAME.z(u, layout.platformLength()) + "}";
				}
			}
		}
		return "null";
	}

	/**
	 * Where a passenger stands to look along one boarding face: on its platform a block or two back from the edge, near the
	 * face's start, in a cell with three clear blocks above the floor and nothing at eye height just ahead (so not behind a
	 * column), looking at the track some way ahead.
	 */
	private static String faceView(StationAudit.Face face, TrackLayout.Track track, Map<Long, String> idAt) {
		final int inward = face.leftOfTrack() ? -1 : 1;
		final int edge = track.u() + inward * StationGeometry.EDGE_OFFSET;
		final int last = Math.max(face.vStart(), face.vEnd() - 8);
		for (int v = Math.min(face.vStart() + 3, last); v <= last; v++) {
			for (final int back : new int[]{1, 2, 0, 3}) {
				final int u = edge + inward * back;
				final int x = FRAME.x(u, v);
				final int z = FRAME.z(u, v);
				final int y = StationGeometry.WALK_Y;
				if (idAt.containsKey(cell(x, y - 1, z)) && !idAt.containsKey(cell(x, y, z)) && !idAt.containsKey(cell(x, y + 2, z))
						&& clearAhead(u, v, y + 1, Math.min(v + 8, face.vEnd()), idAt)) {
					final int ahead = Math.min(v + 20, face.vEnd());
					return "{\"x\":" + x + ",\"z\":" + z + ",\"tx\":" + FRAME.x(track.u(), ahead) + ",\"tz\":" + FRAME.z(track.u(), ahead) + "}";
				}
			}
		}
		return "null";
	}

	/** Nothing at eye height from row v to row to in column u, so no column stands right in front of the passenger. */
	private static boolean clearAhead(int u, int v, int y, int to, Map<Long, String> idAt) {
		for (int w = v; w <= to; w++) {
			if (idAt.containsKey(cell(FRAME.x(u, w), y, FRAME.z(u, w)))) {
				return false;
			}
		}
		return true;
	}

	private static String roof(StationSpec spec) {
		return switch (spec.roof()) {
			case NONE -> "no roof";
			case CANOPY -> "platform canopies";
			case HALL -> lower(spec.shed().name()).replace('_', ' ') + " train shed";
		};
	}

	private static StationRecipe.Look look(String name, StationRecipe.Look fallback) {
		return name == null || name.isEmpty() ? fallback : value(StationRecipe.Look.class, name, fallback);
	}

	private static <E extends Enum<E>> E value(Class<E> type, String name, E fallback) {
		if (name == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	private static int number(String text, int fallback) {
		try {
			return text == null ? fallback : Integer.parseInt(text.strip());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String lower(String text) {
		return text.toLowerCase(Locale.ROOT);
	}

	private static List<String> names(Enum<?>[] values) {
		final List<String> names = new ArrayList<>();
		for (final Enum<?> value : values) {
			names.add(value.name());
		}
		return names;
	}

	/** {"key":"Label",...} in declaration order. */
	private static String labels(Enum<?>[] values) {
		final StringBuilder out = new StringBuilder("[");
		for (final Enum<?> value : values) {
			final String label = value instanceof StationRecipe.Look l ? l.label() : value instanceof StationRecipe.Platforms p ? p.label()
					: value instanceof StationRecipe.Roof r ? r.label() : value instanceof StationRecipe.Size z ? z.label() : value.name();
			if (out.length() > 1) {
				out.append(',');
			}
			out.append("[").append(Json.quote(value.name())).append(',').append(Json.quote(label)).append(']');
		}
		return out.append(']').toString();
	}
}
