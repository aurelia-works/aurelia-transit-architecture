package com.aureliatransit.architecture.transit;

/**
 * Holder for the active {@link StationDataProvider}. The client initializer installs the MTR-backed provider; on a
 * dedicated server this stays {@link StationDataProvider#NONE} and must not be used for gameplay logic.
 */
public final class StationData {

	private static StationDataProvider provider = StationDataProvider.NONE;

	private StationData() {
	}

	public static StationDataProvider provider() {
		return provider;
	}

	public static void install(StationDataProvider newProvider) {
		provider = newProvider;
	}
}
