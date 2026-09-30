package com.aureliatransit.architecture.terminal;

/**
 * Holds the installed {@link TerminalSource} (the MTR-backed one on clients with MTR).
 */
public final class Terminals {

	private static volatile TerminalSource source = TerminalSource.NONE;

	private Terminals() {
	}

	public static void install(TerminalSource value) {
		source = value == null ? TerminalSource.NONE : value;
	}

	public static TerminalSource source() {
		return source;
	}
}
