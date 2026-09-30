package com.aureliatransit.architecture.registry;

/**
 * Which chunk render layer a block needs. Kept as plain data so common code never touches client classes.
 */
public enum RenderKind {
	SOLID,
	CUTOUT,
	TRANSLUCENT
}
