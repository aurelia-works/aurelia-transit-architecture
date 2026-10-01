package com.aureliatransit.architecture.block.wayfinding;

import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

/**
 * What a floor boarding marker indicates; cycled by right-clicking the placed marker.
 */
public enum BoardingMarkerType implements StringIdentifiable {
	/** Train door position. */
	DOOR,
	/** Step-free / accessible boarding position. */
	ACCESSIBLE,
	/** Stand-back / wait here line. */
	WAIT,
	/** Where a boarding ramp is laid for step-free access. */
	RAMP,
	/** Where to wait for staff assistance. */
	ASSIST;

	@Override
	public String asString() {
		return name().toLowerCase(Locale.ROOT);
	}

	public BoardingMarkerType next() {
		final BoardingMarkerType[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
