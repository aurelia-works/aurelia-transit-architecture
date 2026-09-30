package com.aureliatransit.architecture.transit;

/**
 * An MTR station, decoupled from MTR classes. {@code rawName} is MTR's name, which may hold several
 * languages separated by '|'.
 */
public record StationReference(long id, String rawName, int color) {

	/**
	 * The name segment to display: the first segment without CJK characters, else the first segment.
	 */
	public String displayName() {
		return StationNames.display(rawName);
	}
}
