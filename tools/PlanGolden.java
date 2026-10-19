import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import com.aureliatransit.architecture.station.StationPresets;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Refactoring safety net for {@link StationPlanner}: prints one line per station description (every preset and 700 seeded random
 * descriptions): block count and a SHA-256 of the plan's sorted blocks, headroom, track envelopes, support areas and foundation.
 * Run it before and after a change that must not alter any plan and diff the two outputs; a deliberate planner change shows up
 * as differing lines.
 *
 * <pre>
 * ./gradlew compileJava --offline
 * java -cp build/classes/java/main tools/PlanGolden.java > build/golden.txt
 * </pre>
 */
public final class PlanGolden {

	public static void main(String[] args) throws Exception {
		final Map<String, String> stations = new LinkedHashMap<>();
		for (final StationPresets.Preset preset : StationPresets.ALL) {
			stations.put("preset:" + preset.key(), preset.description());
		}
		final String[] tracks = {"1 track", "2 tracks", "3 tracks", "4 tracks", "6 tracks", "8 tracks"};
		final String[] layout = {"side platforms", "island platform", "", "island platforms"};
		final String[] length = {"24 long", "32 long", "64 long", "96 long", "120 long", "200 long"};
		final String[] extras = {"footbridge", "underpass", "mezzanine", "terminus", "hub", "hall roof", "no roof", "canopy", "tram", "step-free",
				"travelators", "wings", "arched train shed", "domed hall", "tower", "bus stop", "waiting room", "shops", "far footbridge", "forecourt", "parking"};
		final String[] looks = {"brick", "glass", "paris", "dutch", "japanese", "tokyo", "utrecht", "hamburg", "london", "berlin", "mumbai", "zurich", "amsterdam",
				"st pancras", "gothic", "art deco", "modern", "clinker", "baroque", "wave roof"};
		final Random random = new Random(20261019L);
		for (int i = 0; i < 700; i++) {
			final StringBuilder text = new StringBuilder(tracks[random.nextInt(tracks.length)]);
			final String l = layout[random.nextInt(layout.length)];
			if (!l.isEmpty()) {
				text.append(", ").append(l);
			}
			text.append(", ").append(length[random.nextInt(length.length)]);
			final int n = random.nextInt(5);
			for (int k = 0; k < n; k++) {
				text.append(", ").append(extras[random.nextInt(extras.length)]);
			}
			text.append(", ").append(looks[random.nextInt(looks.length)]);
			if (random.nextInt(4) == 0) {
				text.append(", seed ").append(Integer.toString(random.nextInt(1_000_000), 36));
			}
			if (random.nextInt(3) == 0) {
				text.append(", named Test").append(i);
			}
			stations.put("rand" + i, text.toString());
		}
		final MessageDigest sha = MessageDigest.getInstance("SHA-256");
		for (final Map.Entry<String, String> entry : stations.entrySet()) {
			final String line;
			try {
				final ParseResult parsed = StationDescriptionParser.parse(entry.getValue());
				final StationPlan plan = StationPlanner.plan(parsed.spec());
				final List<String> rows = new ArrayList<>();
				for (final PlannedBlock b : plan.blocks()) {
					rows.add(b.u() + "," + b.y() + "," + b.v() + "," + b.role() + "," + b.blockId() + "," + b.front() + "," + b.text() + "," + b.state());
				}
				rows.sort(null);
				final StringBuilder all = new StringBuilder();
				rows.forEach(r -> all.append(r).append('\n'));
				all.append("headroom=").append(plan.headroom()).append("\nenvelopes=").append(plan.trackEnvelopes()).append("\nsupport=").append(plan.supportAreas()).append("\nfoundation=").append(plan.foundationId());
				final byte[] digest = sha.digest(all.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
				line = rows.size() + " " + java.util.HexFormat.of().formatHex(digest, 0, 12);
			} catch (RuntimeException e) {
				System.out.println(entry.getKey() + " ERROR " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).replace('\n', ' '));
				continue;
			}
			System.out.println(entry.getKey() + " " + line);
		}
	}
}
