package com.aureliatransit.architecture.client.wayfinding;

import java.util.Locale;

/**
 * The pages of the passenger information terminal, in tab order. Pure navigation logic.
 */
public enum TerminalPage {
	HOME,
	DEPARTURES,
	MAP,
	STATION,
	SERVICE,
	ACCESSIBILITY;

	/** Language key suffix of the tab title ({@code screen.<mod>.term_tab.<id>}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public TerminalPage next() {
		final TerminalPage[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public TerminalPage previous() {
		final TerminalPage[] values = values();
		return values[(ordinal() + values.length - 1) % values.length];
	}

	public static TerminalPage byOrdinal(int ordinal) {
		final TerminalPage[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : HOME;
	}
}
