package com.aureliatransit.architecture.block.wayfinding;

import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingPanelKind;

/**
 * Where a wayfinding panel sits on its block model and what it shows by default, in north-facing model pixels (0-16 per
 * block; a two-block-high pylon continues above 16).
 *
 * @param kind             which {@link WayfindingPanelKind} the layout draws
 * @param centerX          horizontal centre of a single block's panel (joined rows use the row centre instead)
 * @param centerY          vertical centre of the panel
 * @param width            usable width of a single block (joined rows use {@code rowLength * 16} minus a margin)
 * @param height           usable panel height
 * @param frontZ           z of the front (north) face; content is drawn just in front of it
 * @param backZ            z of the back (south) face, or a negative value for single-sided panels
 * @param joins            whether identical blocks placed side by side merge into one wide panel
 * @param textColor        ARGB default text colour handed to the layout
 * @param faceColor        ARGB colour of the model face (editor preview background)
 * @param defaultPictogram pictogram of a freshly placed block, or {@link Pictogram#NONE}
 */
public record WayfindingPanelSpec(WayfindingPanelKind kind, float centerX, float centerY, float width, float height, float frontZ, float backZ, boolean joins,
                                  int textColor, int faceColor, Pictogram defaultPictogram) {

	/** Pixels taken off a joined row so the panel stays inside the end caps. */
	public static final float JOIN_MARGIN = 1.5F;

	public boolean doubleSided() {
		return backZ >= 0;
	}

	/**
	 * Panel width for a row of {@code rowLength} blocks (the single-block width when the block does not join).
	 */
	public float panelWidth(int rowLength) {
		return joins ? Math.max(1, rowLength) * 16 - JOIN_MARGIN : width;
	}

	/**
	 * Horizontal centre of the panel in the owner block's model space.
	 */
	public float panelCenterX(int rowLength) {
		return joins ? Math.max(1, rowLength) * 8F : centerX;
	}

	/**
	 * The content of a freshly placed block.
	 */
	public WayfindingData defaults() {
		return defaultPictogram == Pictogram.NONE ? WayfindingData.EMPTY : WayfindingData.EMPTY.withPictogram(defaultPictogram);
	}
}
