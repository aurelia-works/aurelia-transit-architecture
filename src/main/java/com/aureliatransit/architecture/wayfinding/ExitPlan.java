package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.SignArrow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Merges MTR's station exits with a sign's per-exit settings (A3). Pure and deterministic.
 *
 * <ul>
 *     <li>MTR's exits keep MTR's order. An exit whose label has a setting takes that setting: hidden exits are left
 *     out, a non-empty text replaces MTR's destinations, the arrow is the setting's.</li>
 *     <li>Settings for labels MTR does not have add manual exits after MTR's, in setting order (hidden ones and ones
 *     without text are not shown: a bare label says nothing).</li>
 *     <li>At most {@code max} exits; the first setting for a label wins.</li>
 * </ul>
 */
public final class ExitPlan {

	/** One exit as a sign draws it. */
	public record Shown(String label, List<String> destinations, SignArrow arrow, boolean manual) {
		public Shown {
			destinations = List.copyOf(destinations);
		}
	}

	private ExitPlan() {
	}

	public static List<Shown> merge(List<ExitInfo> mtrExits, List<ExitSetting> settings, int max) {
		final List<Shown> out = new ArrayList<>();
		final List<String> mtrLabels = new ArrayList<>(mtrExits.size());
		for (final ExitInfo exit : mtrExits) {
			mtrLabels.add(norm(exit.label()));
			if (out.size() >= max) {
				continue;
			}
			final ExitSetting setting = find(settings, exit.label());
			if (setting == null) {
				out.add(new Shown(exit.label(), exit.destinations(), SignArrow.NONE, false));
			} else if (!setting.hidden()) {
				out.add(new Shown(exit.label(), setting.text().isEmpty() ? exit.destinations() : List.of(setting.text()), setting.arrow(), false));
			}
		}
		final List<String> added = new ArrayList<>();
		for (final ExitSetting setting : settings) {
			final String key = norm(setting.label());
			if (out.size() >= max || key.isEmpty() || mtrLabels.contains(key) || added.contains(key)) {
				continue;
			}
			added.add(key);
			if (!setting.hidden() && !setting.text().isEmpty()) {
				out.add(new Shown(setting.label(), List.of(setting.text()), setting.arrow(), true));
			}
		}
		return out;
	}

	static ExitSetting find(List<ExitSetting> settings, String label) {
		final String key = norm(label);
		for (final ExitSetting setting : settings) {
			if (norm(setting.label()).equals(key)) {
				return setting;
			}
		}
		return null;
	}

	private static String norm(String label) {
		return label.trim().toLowerCase(Locale.ROOT);
	}
}
