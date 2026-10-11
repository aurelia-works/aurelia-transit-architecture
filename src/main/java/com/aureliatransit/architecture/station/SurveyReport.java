package com.aureliatransit.architecture.station;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What {@link SiteSurvey} found and the changes a build would make.
 *
 * @param issues         the first {@link SiteSurvey#MAX_LISTED} issues in scan order (for chat and preview markers)
 * @param counts         every issue counted by kind (kinds with no issue are absent)
 * @param ops            CLEAR ops (top-down), then FILL ops (bottom-up), then PLACE ops in plan order. CLEAR and FILL ops are
 *                       only listed when terrain changes are approved
 * @param terrainChanges cells whose existing non-air content the ops would change (clears, fills of non-air cells, placements
 *                       over non-air cells)
 * @param replaceTerrain whether the survey assumed approved terrain changes
 */
public record SurveyReport(List<SiteIssue> issues, Map<SiteIssue.Kind, Integer> counts, List<WorldOp> ops, int terrainChanges,
		boolean replaceTerrain) {

	public SurveyReport {
		issues = List.copyOf(issues);
		final Map<SiteIssue.Kind, Integer> copy = new EnumMap<>(SiteIssue.Kind.class);
		copy.putAll(counts);
		counts = java.util.Collections.unmodifiableMap(copy);
		ops = List.copyOf(ops);
	}

	public int count(SiteIssue.Kind kind) {
		return counts.getOrDefault(kind, 0);
	}

	public boolean hasHardIssue() {
		return counts.keySet().stream().anyMatch(SiteIssue.Kind::hard);
	}

	/** Issues that approval would fix (occupied, obstructed, unsupported). */
	public int approvableIssues() {
		return counts.entrySet().stream().filter(entry -> !entry.getKey().hard()).mapToInt(Map.Entry::getValue).sum();
	}

	/** True when the build may start: no hard issue, and either nothing to fix or terrain changes approved. */
	public boolean buildable() {
		return !hasHardIssue() && (approvableIssues() == 0 || replaceTerrain);
	}

	public long placeCount() {
		return ops.stream().filter(op -> op.kind() == WorldOp.Kind.PLACE).count();
	}
}
