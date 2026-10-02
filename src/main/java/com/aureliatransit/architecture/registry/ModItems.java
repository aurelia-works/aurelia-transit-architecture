package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.fare.TransitCardItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Items that are not blocks. Listed in the passenger equipment tab after its blocks.
 */
public final class ModItems {

	public static final Item TRANSIT_CARD = Registry.register(Registries.ITEM, AureliaTransitArchitecture.id("transit_card"),
			new TransitCardItem(new Item.Settings().maxCount(1)));

	private ModItems() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
