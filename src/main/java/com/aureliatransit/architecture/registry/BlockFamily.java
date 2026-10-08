package com.aureliatransit.architecture.registry;

/**
 * Feature families, in creative-tab order. Each family belongs to exactly one {@link Tab}.
 */
public enum BlockFamily {
	PLATFORMS(Tab.ARCHITECTURE),
	ARCHITECTURE(Tab.ARCHITECTURE),
	FURNITURE(Tab.ARCHITECTURE),
	CATENARY(Tab.ARCHITECTURE),
	/** 1.3 elevated viaduct and station infrastructure. */
	ELEVATED(Tab.ARCHITECTURE),
	SIGNAGE(Tab.WAYFINDING),
	WAYFINDING(Tab.WAYFINDING),
	PASSENGER_INFO(Tab.PASSENGER_EQUIPMENT),
	ACCESSIBILITY(Tab.PASSENGER_EQUIPMENT),
	/** 1.4 station equipment props: fare gates, card readers, booth windows, CCTV (no fare logic). */
	STATION_EQUIPMENT(Tab.PASSENGER_EQUIPMENT),
	BUS(Tab.BUS_STREET),
	STREET(Tab.BUS_STREET),
	/** 1.5 architectural glass: float, tinted, reflective, frosted, fritted and wired glass, glass brick, curtain wall pieces. */
	GLASS(Tab.GLASS);

	/**
	 * The dedicated creative tabs. Tab ids are not stored in worlds, so regrouping never touches block or item ids.
	 */
	public enum Tab {
		ARCHITECTURE("main"),
		WAYFINDING("wayfinding"),
		PASSENGER_EQUIPMENT("passenger_equipment"),
		BUS_STREET("bus_street"),
		GLASS("glass");

		private final String id;

		Tab(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}
	}

	private final Tab tab;

	BlockFamily(Tab tab) {
		this.tab = tab;
	}

	public Tab tab() {
		return tab;
	}
}
