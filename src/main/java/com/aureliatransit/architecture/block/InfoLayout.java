package com.aureliatransit.architecture.block;

/**
 * Where the text of an information block sits, in north-facing model pixels (0-16).
 *
 * @param centerX      horizontal centre of the text area
 * @param centerY      vertical centre of the text area
 * @param width        text-area width
 * @param height       text-area height
 * @param frontZ       z of the front (north) face
 * @param backZ        z of the back (south) face, or a negative value for single-sided blocks
 * @param bodyColor    ARGB colour of body text
 * @param headingColor ARGB colour of an un-banded heading
 * @param maxBodyLines editable body rows (at most {@code ConfigurableTextData.MAX_BODY_LINES})
 * @param maxScale     cap for body text scale (model pixels per font unit)
 */
public record InfoLayout(float centerX, float centerY, float width, float height, float frontZ, float backZ, int bodyColor, int headingColor,
                         int maxBodyLines, float maxScale) {

	public boolean doubleSided() {
		return backZ >= 0;
	}
}
