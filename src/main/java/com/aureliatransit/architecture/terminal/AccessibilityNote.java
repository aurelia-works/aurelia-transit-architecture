package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.wayfinding.Pictogram;

/**
 * One accessibility fact taken from ATA metadata only (an accessibility pictogram sign, help point or configured text
 * near the terminal). Never invented.
 */
public record AccessibilityNote(Pictogram pictogram, String text) {
}
