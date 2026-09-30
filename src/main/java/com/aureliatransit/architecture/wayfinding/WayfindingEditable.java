package com.aureliatransit.architecture.wayfinding;

/**
 * Implemented by every block entity that stores {@link WayfindingData}. The shared {@code update_wayfinding} packet
 * (validated in ModPackets) writes through this interface, so new wayfinding blocks need no packet code of their own.
 */
public interface WayfindingEditable {

	WayfindingData getWayfinding();

	/** Server side: store, mark dirty and sync to clients. */
	void setWayfinding(WayfindingData data);

	WayfindingPanelKind panelKind();
}
