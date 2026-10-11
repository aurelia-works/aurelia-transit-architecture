import com.aureliatransit.architecture.station.LayoutValidator;
import com.aureliatransit.architecture.station.LocalDir;
import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.Role;
import com.aureliatransit.architecture.station.StationAudit;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.Violation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes station plans as JSON for the 3D plan viewer: every planned block with its role, block id, front and state, plus the
 * design-rule violations, the station checks and a "suspect" flag on blocks that look out of place (nothing under something
 * that should stand, or touching nothing at all). Pure planner output, no game needed.
 *
 * <pre>
 * ./gradlew compileJava
 * java -cp build/classes/java/main tools/StationPlanExport.java build/station-plans.json ["description" ...]
 * </pre>
 * With no descriptions it exports every wrench preset and the guide's examples.
 */
public final class StationPlanExport {

	/** Roles that must rest on the cell below them (y >= 1); terrain is at y = -1, so y = 0 always rests on it. */
	private static final Set<Role> STANDING = Set.of(Role.COLUMN, Role.BRIDGE_PIER, Role.LAMP, Role.BENCH, Role.BIN, Role.NAME_SIGN,
			Role.GATE, Role.BARRIER, Role.BUFFER_STOP, Role.WALL, Role.WINDOW, Role.FIXTURE, Role.RAILING);

	public static void main(String[] args) throws IOException {
		final Map<String, String> stations = new LinkedHashMap<>();
		if (args.length > 1) {
			for (int i = 1; i < args.length; i++) {
				stations.put(args[i], args[i]);
			}
		} else {
			for (final StationPresets.Preset preset : StationPresets.ALL) {
				stations.put(preset.key(), preset.description());
			}
			stations.put("dutch_island", "two track dutch station, island platform, 64 long, named Leidsche Rijn");
			stations.put("sbahn_halt", "small single track s-bahn halt, no roof");
			stations.put("metro_hall", "two track metro station, side platforms, 48 long, hall roof");
		}
		final StringBuilder out = new StringBuilder("[");
		boolean first = true;
		for (final Map.Entry<String, String> entry : stations.entrySet()) {
			if (!first) {
				out.append(",\n");
			}
			first = false;
			export(out, entry.getKey(), entry.getValue());
		}
		out.append("]\n");
		final Path path = Path.of(args.length > 0 ? args[0] : "build/station-plans.json");
		Files.createDirectories(path.toAbsolutePath().getParent());
		Files.writeString(path, out, StandardCharsets.UTF_8);
		System.out.println("wrote " + stations.size() + " plans to " + path);
	}

	private static void export(StringBuilder out, String key, String description) {
		final ParseResult parse = StationDescriptionParser.parse(description);
		final StationPlan plan = StationPlanner.plan(parse.spec());
		final List<Violation> violations = LayoutValidator.validate(plan);
		final StationAudit.Result audit = StationAudit.audit(plan);
		final Set<Long> occupied = new HashSet<>();
		for (final PlannedBlock block : plan.blocks()) {
			occupied.add(cell(block.u(), block.y(), block.v()));
		}
		final List<String> ids = new ArrayList<>();
		final Map<String, Integer> idIndex = new LinkedHashMap<>();
		int suspects = 0;
		final StringBuilder blocks = new StringBuilder();
		for (final PlannedBlock block : plan.blocks()) {
			final int id = idIndex.computeIfAbsent(block.blockId(), k -> {
				ids.add(k);
				return ids.size() - 1;
			});
			final String suspect = suspect(block, occupied);
			if (!suspect.isEmpty()) {
				suspects++;
			}
			if (blocks.length() > 0) {
				blocks.append(',');
			}
			blocks.append('[').append(block.u()).append(',').append(block.y()).append(',').append(block.v()).append(',')
					.append(block.role().ordinal()).append(',').append(id).append(',').append(block.front().ordinal()).append(',')
					.append(quote(block.state())).append(',').append(quote(suspect)).append(']');
		}
		out.append("{\"key\":").append(quote(key)).append(",\"description\":").append(quote(description))
				.append(",\"warnings\":").append(strings(parse.warnings())).append(",\"ignored\":").append(strings(parse.ignored()))
				.append(",\"roles\":").append(strings(enumNames(Role.values())))
				.append(",\"fronts\":").append(strings(enumNames(LocalDir.values())))
				.append(",\"ids\":").append(strings(ids)).append(",\"suspects\":").append(suspects)
				.append(",\"violations\":[");
		for (int i = 0; i < violations.size(); i++) {
			final Violation v = violations.get(i);
			out.append(i == 0 ? "" : ",").append("{\"kind\":").append(quote(v.kind().name())).append(",\"u\":").append(v.u())
					.append(",\"y\":").append(v.y()).append(",\"v\":").append(v.v()).append(",\"message\":").append(quote(v.message())).append('}');
		}
		out.append("],\"checks\":[");
		final List<StationAudit.Check> checks = audit.problems();
		for (int i = 0; i < checks.size(); i++) {
			final StationAudit.Check c = checks.get(i);
			out.append(i == 0 ? "" : ",").append("{\"topic\":").append(quote(c.topic().name())).append(",\"status\":")
					.append(quote(c.status().name())).append(",\"message\":").append(quote(c.message())).append('}');
		}
		out.append("],\"blocks\":[").append(blocks).append("]}");
	}

	/** Why a block looks out of place, or "" if it does not. */
	private static String suspect(PlannedBlock block, Set<Long> occupied) {
		final int u = block.u();
		final int y = block.y();
		final int v = block.v();
		final boolean touches = occupied.contains(cell(u + 1, y, v)) || occupied.contains(cell(u - 1, y, v)) || occupied.contains(cell(u, y + 1, v))
				|| occupied.contains(cell(u, y - 1, v)) || occupied.contains(cell(u, y, v + 1)) || occupied.contains(cell(u, y, v - 1));
		if (!touches && y >= 1) {
			return "touches no other block";
		}
		if (y < 1 || !STANDING.contains(block.role()) || occupied.contains(cell(u, y - 1, v)) || block.state().contains("half=upper")) {
			return "";
		}
		final boolean alongU = block.front() == LocalDir.PLUS_U || block.front() == LocalDir.MINUS_U;
		if (block.role() == Role.FIXTURE) {
			// wall-mounted (something behind it) or hanging from something directly above
			final int bu = block.front() == LocalDir.PLUS_U ? u - 1 : block.front() == LocalDir.MINUS_U ? u + 1 : u;
			final int bv = block.front() == LocalDir.PLUS_V ? v - 1 : block.front() == LocalDir.MINUS_V ? v + 1 : v;
			if (occupied.contains(cell(bu, y, bv)) || block.blockId().contains("hanging") && occupied.contains(cell(u, y + 1, v))) {
				return "";
			}
			return "floats: nothing under, behind or above it";
		}
		// a lintel or rail spanning between neighbours along its own row is carried by them
		final boolean spans = alongU
				? occupied.contains(cell(u, y, v - 1)) && occupied.contains(cell(u, y, v + 1))
				: occupied.contains(cell(u - 1, y, v)) && occupied.contains(cell(u + 1, y, v));
		return spans ? "" : "nothing underneath";
	}

	private static long cell(int u, int y, int v) {
		return ((long) (u + 4096) << 40) | ((long) (y + 4096) << 20) | (v + 4096);
	}

	private static List<String> enumNames(Enum<?>[] values) {
		final List<String> names = new ArrayList<>();
		for (final Enum<?> value : values) {
			names.add(value.name());
		}
		return names;
	}

	private static String strings(List<String> values) {
		final StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < values.size(); i++) {
			out.append(i == 0 ? "" : ",").append(quote(values.get(i)));
		}
		return out.append(']').toString();
	}

	private static String quote(String text) {
		final StringBuilder out = new StringBuilder("\"");
		for (final char c : text.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.append('"').toString();
	}
}
