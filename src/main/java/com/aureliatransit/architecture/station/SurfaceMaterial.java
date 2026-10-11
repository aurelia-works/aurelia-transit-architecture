package com.aureliatransit.architecture.station;

/**
 * Platform floor material. Every choice maps to a block that counts as an MTR platform, so train doors open beside it.
 */
public enum SurfaceMaterial {
	/** Whatever the style uses. */
	STYLE_DEFAULT,
	LIGHT_PAVING,
	DARK_PAVING,
	CONCRETE,
	TERRAZZO,
	GRANITE
}
