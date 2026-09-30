package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.PanelLayout;

import java.util.function.ToIntFunction;

/**
 * Pure layout of a wayfinding panel into rectangles and labels (drawn by the shared PanelDrawer). Model pixels, origin
 * at the panel centre, y up; every label must fit inside {@code w x h}. Deterministic: equal input, equal output.
 *
 * <p>LEAD STUB - owned by the wayfinding-logic workstream (subagent A): line badges, destination/service labels, exit
 * lists, pictogram glyphs and side-by-side multilingual layout. Presentation code (subagent B) only calls
 * {@link #layout}; keep the signature.
 */
public final class WayfindingLayout {

	private WayfindingLayout() {
	}

	public static PanelLayout.Panel layout(ResolvedWayfinding r, WayfindingPanelKind kind, float w, float h, int textColor, ToIntFunction<String> measure) {
		return PanelLayout.Panel.EMPTY;
	}
}
