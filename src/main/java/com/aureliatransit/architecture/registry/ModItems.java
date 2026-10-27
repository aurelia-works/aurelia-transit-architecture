package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.fare.TransitCardItem;
import com.aureliatransit.architecture.station.world.StationCodexItem;
import com.aureliatransit.architecture.station.world.AirportWrenchItem;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Items that are not blocks. Listed in the passenger equipment tab after its blocks.
 */
public final class ModItems {

	public static final Item TRANSIT_CARD = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("transit_card"),
			new TransitCardItem(new Item.Settings().maxCount(1)));
	/** Station generator tool; operators only (checked on the server for every action). */
	public static final Item STATION_WRENCH = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("station_wrench"),
			new StationWrenchItem(new Item.Settings().maxCount(1)));
	/** Airport generator tool: the station wrench's way of working, planning airports; operators only. */
	public static final Item AIRPORT_WRENCH = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("airport_wrench"),
			new AirportWrenchItem(new Item.Settings().maxCount(1)));
	/** The station generator's guide; opens the Codex screen. */
	public static final Item STATION_CODEX = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("station_codex"),
			new StationCodexItem(new Item.Settings().maxCount(1)));

	private ModItems() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
