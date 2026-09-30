package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {

	public static final BlockEntityType<TextSignBlockEntity> TEXT_SIGN = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("text_sign"),
			BlockEntityType.Builder.create(TextSignBlockEntity::new, textSignBlocks()).build(null)
	);

	private ModBlockEntities() {
	}

	private static Block[] textSignBlocks() {
		return ModBlocks.entries().stream().map(ModBlocks.Entry::block).filter(block -> block instanceof TextSignBlock).toArray(Block[]::new);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
