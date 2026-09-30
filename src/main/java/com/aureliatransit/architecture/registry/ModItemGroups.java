package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.Comparator;

public final class ModItemGroups {

	public static final ItemGroup MAIN = Registry.register(
			Registries.ITEM_GROUP,
			AureliaTransitArchitecture.id("main"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup." + AureliaTransitArchitecture.MOD_ID + ".main"))
					.icon(() -> new ItemStack(ModBlocks.STATION_NAME_SIGN))
					.entries((context, entries) -> ModBlocks.entries().stream()
							.sorted(Comparator.comparing(ModBlocks.Entry::family))
							.forEach(entry -> entries.add(entry.block())))
					.build()
	);

	private ModItemGroups() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
