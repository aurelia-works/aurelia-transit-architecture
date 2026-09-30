package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.registry.LiveBlocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Operator-only test helper: {@code /aurelia_live_stress place <displays> <speakers>} fills a spaced grid of live
 * displays and speakers around the executor (so they never join), {@code /aurelia_live_stress clear} removes every live
 * display and speaker within 24 blocks. Used to stress-test rendering and the announcement engine (~50 displays, 20
 * speakers); see docs/LIVE_TESTING.md. Server-side only, bounded, and permission level 2.
 */
final class LiveStressCommand {

	static final int MAX_DISPLAYS = 200;
	static final int MAX_SPEAKERS = 100;
	private static final int COLUMNS = 10;
	private static final int CLEAR_RADIUS = 24;

	private LiveStressCommand() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("aurelia_live_stress").requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("place")
						.then(CommandManager.argument("displays", IntegerArgumentType.integer(0, MAX_DISPLAYS))
								.then(CommandManager.argument("speakers", IntegerArgumentType.integer(0, MAX_SPEAKERS)).executes(context -> {
									final ServerCommandSource source = context.getSource();
									final int displays = IntegerArgumentType.getInteger(context, "displays");
									final int speakers = IntegerArgumentType.getInteger(context, "speakers");
									place(source.getWorld(), BlockPos.ofFloored(source.getPosition()), displays, speakers);
									source.sendFeedback(() -> Text.literal("Placed " + displays + " displays and " + speakers + " speakers"), false);
									return displays + speakers;
								}))))
				.then(CommandManager.literal("clear").executes(context -> {
					final ServerCommandSource source = context.getSource();
					final int removed = clear(source.getWorld(), BlockPos.ofFloored(source.getPosition()));
					source.sendFeedback(() -> Text.literal("Removed " + removed + " live blocks"), false);
					return removed;
				})));
	}

	static void place(ServerWorld world, BlockPos base, int displays, int speakers) {
		final Block[] kinds = {LiveBlocks.PLATFORM_CIS, LiveBlocks.PLATFORM_PIDS, LiveBlocks.HANGING_PLATFORM_PIDS, LiveBlocks.CONCOURSE_BOARD, LiveBlocks.HANGING_PLATFORM_CIS};
		for (int i = 0; i < displays; i++) {
			final BlockPos pos = base.add(-COLUMNS + (i % COLUMNS) * 2, 1, 3 + (i / COLUMNS) * 2);
			set(world, pos, kinds[i % kinds.length].getDefaultState());
		}
		for (int i = 0; i < speakers; i++) {
			final BlockPos pos = base.add(-COLUMNS + (i % COLUMNS) * 2, 2, -3 - (i / COLUMNS) * 2);
			set(world, pos, (i % 2 == 0 ? LiveBlocks.WALL_SPEAKER : LiveBlocks.CEILING_SPEAKER).getDefaultState());
		}
	}

	static int clear(ServerWorld world, BlockPos centre) {
		int removed = 0;
		for (final BlockPos pos : BlockPos.iterate(centre.add(-CLEAR_RADIUS, -6, -CLEAR_RADIUS), centre.add(CLEAR_RADIUS, 10, CLEAR_RADIUS))) {
			if (world.isChunkLoaded(pos)) {
				final Block block = world.getBlockState(pos).getBlock();
				if (block instanceof PidsBlock || block instanceof SpeakerBlock) {
					world.removeBlock(pos, false);
					removed++;
				}
			}
		}
		return removed;
	}

	private static void set(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.isChunkLoaded(pos)) {
			world.setBlockState(pos, state);
		}
	}
}
