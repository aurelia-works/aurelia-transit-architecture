package com.aureliatransit.architecture.text;

import java.util.function.ToIntFunction;

/**
 * Picks the layout of an editable sign by its {@link SignStyle}: the 1.4 styles with their own layout (lift status,
 * train composition, stand-back warning defaults) and {@link PanelLayout#sign} for the rest. Used by the world renderer
 * and the editor preview, so both always agree. Pure.
 */
public final class SignPanels {

	private SignPanels() {
	}

	/**
	 * @param primary     the main text to show (already resolved, e.g. MTR station name)
	 * @param liftStatus  lift status ordinal for {@link SignStyle#LIFT}, ignored otherwise
	 */
	public static PanelLayout.Panel layout(SignData data, String primary, SignStyle style, int liftStatus, float w, float h, ToIntFunction<String> measure) {
		return switch (style) {
			case LIFT -> LiftPanelLayout.layout(primary, data.secondary(), LiftPanelLayout.status(liftStatus), w, h, style.textColor(), style.secondaryColor(), measure);
			case COMPOSITION -> CompositionLayout.layout(primary, data.secondary(), w, h, style.secondaryColor(), measure);
			case WARNING -> PanelLayout.sign(warningDefaults(data), primary.isBlank() ? Tr.t("warning_default_primary") : primary, style, w, h, measure);
			default -> PanelLayout.sign(data, primary, style, w, h, measure);
		};
	}

	/** A stand-back sign left empty reads "Stand back" / "Non-stopping trains"; typed text replaces either line. */
	static SignData warningDefaults(SignData data) {
		if (!data.secondary().isBlank()) {
			return data;
		}
		return new SignData(data.primary(), Tr.t("warning_default_secondary"), data.alignment(), data.accent(), data.arrow(), data.platform(), data.autoName(), data.routes());
	}
}
