package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.live.DisplayKind;
import com.aureliatransit.architecture.live.PidsBlock;
import com.aureliatransit.architecture.live.PidsBlockEntity;
import com.aureliatransit.architecture.live.SpeakerBlock;
import com.aureliatransit.architecture.live.SpeakerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * Live passenger-information blocks: platform CIS, platform PIDS, concourse board and announcement speakers.
 * Registered through {@link ModBlocks#register}. Owned by the live-systems workstream.
 */
public final class LiveBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
	private static final String TIP_LIVE_CONFIG = TIP + "live_config";
	private static final String TIP_LIVE_JOINS = TIP + "live_joins";
	private static final String TIP_LIVE_HANGING = TIP + "live_hanging";
	private static final String TIP_SPEAKER = TIP + "live_speaker";

	public static final Block PLATFORM_CIS = ModBlocks.register("platform_cis", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new PidsBlock(ModBlocks.metal().luminance(state -> 8), DisplayKind.CIS, false, 12.5F, 0, box(0, 0, 12, 16, 16, 16)),
			TIP_LIVE_CONFIG, TIP_LIVE_JOINS);
	public static final Block HANGING_PLATFORM_CIS = ModBlocks.register("hanging_platform_cis", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new PidsBlock(ModBlocks.metal().luminance(state -> 8), DisplayKind.CIS, true, 6.5F, 3, box(0, 0, 6, 16, 16, 10)),
			TIP_LIVE_CONFIG, TIP_LIVE_JOINS, TIP_LIVE_HANGING);
	public static final Block PLATFORM_PIDS = ModBlocks.register("platform_pids", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new PidsBlock(ModBlocks.metal().luminance(state -> 8), DisplayKind.PIDS, false, 12.5F, 0, box(0, 0, 12, 16, 16, 16)),
			TIP_LIVE_CONFIG, TIP_LIVE_JOINS);
	public static final Block HANGING_PLATFORM_PIDS = ModBlocks.register("hanging_platform_pids", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new PidsBlock(ModBlocks.metal().luminance(state -> 8), DisplayKind.PIDS, true, 6.5F, 3, box(0, 0, 6, 16, 16, 10)),
			TIP_LIVE_CONFIG, TIP_LIVE_JOINS, TIP_LIVE_HANGING);
	public static final Block CONCOURSE_BOARD = ModBlocks.register("concourse_board", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new PidsBlock(ModBlocks.metal().luminance(state -> 8), DisplayKind.CONCOURSE, false, 11.5F, 0, box(0, 0, 11, 16, 16, 16)),
			TIP_LIVE_CONFIG, TIP_LIVE_JOINS);
	public static final Block WALL_SPEAKER = ModBlocks.register("wall_speaker", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new SpeakerBlock(ModBlocks.metal(), box(4, 4, 13, 12, 12, 16)),
			TIP_SPEAKER);
	public static final Block CEILING_SPEAKER = ModBlocks.register("ceiling_speaker", BlockFamily.PASSENGER_INFO, RenderKind.CUTOUT,
			new SpeakerBlock(ModBlocks.metal(), box(3, 14, 3, 13, 16, 13)),
			TIP_SPEAKER);

	public static final BlockEntityType<PidsBlockEntity> PIDS_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("live_display"),
			BlockEntityType.Builder.create(PidsBlockEntity::new, PLATFORM_CIS, HANGING_PLATFORM_CIS, PLATFORM_PIDS, HANGING_PLATFORM_PIDS, CONCOURSE_BOARD).build(null)
	);
	public static final BlockEntityType<SpeakerBlockEntity> SPEAKER_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			AureliaTransitArchitecture.id("live_speaker"),
			BlockEntityType.Builder.create(SpeakerBlockEntity::new, WALL_SPEAKER, CEILING_SPEAKER).build(null)
	);

	private LiveBlocks() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
