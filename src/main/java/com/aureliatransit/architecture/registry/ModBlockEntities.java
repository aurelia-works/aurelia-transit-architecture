package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.ClockBlock;
import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.ClockBlockEntity;
import com.aureliatransit.architecture.block.entity.InfoDisplayBlockEntity;
import com.aureliatransit.architecture.block.elevated.TrainEdgeBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.block.entity.TrainEdgeBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.function.Predicate;

public final class ModBlockEntities {

	public static final BlockEntityType<TextSignBlockEntity> TEXT_SIGN = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("text_sign"),
			BlockEntityType.Builder.create(TextSignBlockEntity::new, blocks(block -> block instanceof TextSignBlock)).build(null)
	);

	public static final BlockEntityType<InfoDisplayBlockEntity> INFO_DISPLAY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("info_display"),
			BlockEntityType.Builder.create(InfoDisplayBlockEntity::new, blocks(block -> block instanceof InfoDisplayBlock)).build(null)
	);

	public static final BlockEntityType<ClockBlockEntity> CLOCK = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("clock"),
			BlockEntityType.Builder.create(ClockBlockEntity::new, blocks(block -> block instanceof ClockBlock)).build(null)
	);

	public static final BlockEntityType<TrainEdgeBlockEntity> TRAIN_EDGE = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("train_edge"),
			BlockEntityType.Builder.create(TrainEdgeBlockEntity::new, blocks(block -> block instanceof TrainEdgeBlock)).build(null)
	);

	private ModBlockEntities() {
	}

	private static Block[] blocks(Predicate<Block> filter) {
		return ModBlocks.entries().stream().map(ModBlocks.Entry::block).filter(filter).toArray(Block[]::new);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
