package com.aureliatransit.architecture.block;

/**
 * Where editable text sits on a sign model, in north-facing model pixels (0-16).
 *
 * @param centerY     vertical centre of the text area
 * @param height      usable text-area height
 * @param width       usable text-area width of a single block
 * @param frontZ      z of the front (north) face; text is drawn just in front of it
 * @param backZ       z of the back (south) face, or a negative value for single-sided signs
 * @param color       ARGB text colour
 * @param maxLines    number of editable lines
 * @param maxLength   characters per line
 * @param joins       whether identical signs placed side by side merge into one wide sign
 */
public record TextLayout(float centerY, float height, float width, float frontZ, float backZ, int color, int maxLines, int maxLength, boolean joins) {

	public boolean doubleSided() {
		return backZ >= 0;
	}
}
