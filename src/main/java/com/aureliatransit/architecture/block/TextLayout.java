package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.text.SignStyle;

/**
 * Where editable text sits on a sign model, in north-facing model pixels (0-16).
 *
 * @param centerY vertical centre of the text area
 * @param height  usable text-area height
 * @param width   usable text-area width of a single block (joined rows use rowLength * 16 minus a margin instead)
 * @param frontZ  z of the front (north) face; text is drawn just in front of it
 * @param backZ   z of the back (south) face, or a negative value for single-sided signs
 * @param joins   whether identical signs placed side by side merge into one wide sign
 * @param style   what the sign can show (secondary line, arrow, platform badge, route badges ...) and its text colours
 */
public record TextLayout(float centerY, float height, float width, float frontZ, float backZ, boolean joins, SignStyle style) {

	public boolean doubleSided() {
		return backZ >= 0;
	}
}
