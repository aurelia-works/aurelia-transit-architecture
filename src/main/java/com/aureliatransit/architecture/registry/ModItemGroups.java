package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One creative tab per {@link BlockFamily.Tab}. Blocks are listed by family, then in registration order. The first tab
 * keeps the 1.0/1.1 id {@code main}.
 */
public final class ModItemGroups {

	public static final Map<BlockFamily.Tab, ItemGroup> TABS = new EnumMap<>(BlockFamily.Tab.class);

	static {
		register(BlockFamily.Tab.ARCHITECTURE, () -> ModBlocks.PLATFORM_EDGE_WARNING);
		register(BlockFamily.Tab.WAYFINDING, () -> ModBlocks.STATION_NAME_SIGN);
		register(BlockFamily.Tab.PASSENGER_EQUIPMENT, () -> LiveBlocks.PLATFORM_PIDS);
		register(BlockFamily.Tab.BUS_STREET, () -> ModBlocks.BUS_STOP_SIGN);
		register(BlockFamily.Tab.STATIONS, () -> StationBlocksNlBe.WAVE_ROOF_PANEL);
	}

	private ModItemGroups() {
	}

	private static void register(BlockFamily.Tab tab, Supplier<Block> icon) {
		TABS.put(tab, Registry.register(Registries.ITEM_GROUP, AureliaTransitArchitecture.id(tab.id()),
				FabricItemGroup.builder()
						.displayName(Text.translatable("itemGroup." + AureliaTransitArchitecture.MOD_ID + "." + tab.id()))
						.icon(() -> new ItemStack(icon.get()))
						.entries((context, entries) -> {
							ModBlocks.entries().stream()
									.filter(entry -> entry.family().tab() == tab)
									.sorted(Comparator.comparing(ModBlocks.Entry::family))
									.forEach(entry -> entries.add(entry.block()));
							if (tab == BlockFamily.Tab.PASSENGER_EQUIPMENT) {
								entries.add(ModItems.TRANSIT_CARD);
							}
						})
						.build()));
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
