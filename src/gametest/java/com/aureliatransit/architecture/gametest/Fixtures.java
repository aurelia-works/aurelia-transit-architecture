package com.aureliatransit.architecture.gametest;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.registry.BlockFamily;
import com.aureliatransit.architecture.registry.DescribedBlockItem;
import com.aureliatransit.architecture.registry.ModBlocks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shared helpers for the game tests: the block registry, deterministic state sampling, tooltips and a mock player.
 */
final class Fixtures {

	/** At most this many blockstates of one block are placed (all of them when it has fewer). */
	static final int STATE_CAP = 64;
	static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** One registered Aurelia block. */
	record Entry(String id, Block block, BlockFamily family) {
		String tab() {
			return family.tab().id();
		}
	}

	private Fixtures() {
	}

	/** Every block of the mod, from the registry itself; fails loudly if the registry and ModBlocks disagree. */
	static List<Entry> blocks() {
		// Fabric asks for the test functions before the mod initializer has run
		AureliaTransitArchitecture.registerContent();
		final List<Entry> result = new ArrayList<>();
		for (final ModBlocks.Entry entry : ModBlocks.entries()) {
			final var id = Registries.BLOCK.getId(entry.block());
			if (!id.getNamespace().equals(AureliaTransitArchitecture.MOD_ID) || !id.getPath().equals(entry.id())) {
				throw new IllegalStateException("Registry mismatch for " + entry.id() + ": registered as " + id);
			}
			result.add(new Entry(entry.id(), entry.block(), entry.family()));
		}
		final long inRegistry = Registries.BLOCK.getIds().stream().filter(id -> id.getNamespace().equals(AureliaTransitArchitecture.MOD_ID)).count();
		if (inRegistry != result.size()) {
			throw new IllegalStateException("Registry holds " + inRegistry + " Aurelia blocks but ModBlocks lists " + result.size());
		}
		final long blockstates = blockstateFiles();
		if (blockstates != result.size()) {
			throw new IllegalStateException("Only " + result.size() + " blocks are registered but the mod ships " + blockstates + " blockstate files");
		}
		return result;
	}

	private static long blockstateFiles() {
		final var container = FabricLoader.getInstance().getModContainer(AureliaTransitArchitecture.MOD_ID).orElseThrow();
		final Path dir = container.findPath("assets/" + AureliaTransitArchitecture.MOD_ID + "/blockstates").orElseThrow();
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(path -> path.toString().endsWith(".json")).count();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static Entry block(String id) {
		return blocks().stream().filter(entry -> entry.id().equals(id)).findFirst().orElseThrow(() -> new IllegalStateException("No block " + id));
	}

	/**
	 * Deterministic sample of at most {@code cap} states: the default state, every single-property variation of it, then an
	 * even stride through the full list. The same block always yields the same sample.
	 */
	static List<BlockState> sampleStates(Block block, int cap) {
		final List<BlockState> all = block.getStateManager().getStates();
		if (all.size() <= cap) {
			return all;
		}
		final Set<BlockState> picked = new LinkedHashSet<>();
		final BlockState base = block.getDefaultState();
		picked.add(base);
		for (final Property<?> property : base.getProperties()) {
			for (final Comparable<?> value : property.getValues()) {
				if (picked.size() < cap / 2) {
					picked.add(with(base, property, value));
				}
			}
		}
		final double stride = all.size() / (double) cap;
		for (int i = 0; picked.size() < cap && i < cap * 2; i++) {
			picked.add(all.get((int) Math.min(all.size() - 1, Math.floor(i * stride))));
		}
		return new ArrayList<>(picked).subList(0, Math.min(cap, picked.size()));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static BlockState with(BlockState state, Property property, Comparable value) {
		return state.with(property, value);
	}

	/** Tooltip keys of a block's item without the {@code tooltip.<mod>.} prefix, e.g. {@code faces_you}. */
	static List<String> tooltips(Block block) {
		final String prefix = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";
		final List<String> keys = new ArrayList<>();
		if (block.asItem() instanceof DescribedBlockItem item) {
			for (final String key : item.tooltipKeys()) {
				keys.add(key.startsWith(prefix) ? key.substring(prefix.length()) : key);
			}
		}
		return keys;
	}

	static GameTestException fail(String message) {
		return new GameTestException(message);
	}

	/** Throws one failure listing up to {@code limit} problems, if there are any. */
	static void failIfAny(List<String> problems, String what) {
		if (!problems.isEmpty()) {
			final int limit = 8;
			final String shown = String.join("\n  - ", problems.subList(0, Math.min(limit, problems.size())));
			throw fail(what + ": " + problems.size() + " problem(s)\n  - " + shown + (problems.size() > limit ? "\n  ... and " + (problems.size() - limit) + " more" : ""));
		}
	}

	// ---- mock player and placement ---------------------------------------------------------------------------------

	/** A creative server-side mock player standing in the far corner of the 8x8x8 test area, looking {@code look}. */
	static ServerPlayerEntity player(TestContext context, Direction look) {
		final ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		face(context, player, look);
		return player;
	}

	static void face(TestContext context, PlayerEntity player, Direction look) {
		final Vec3d corner = context.getAbsolute(new Vec3d(7.5, 6.0, 7.5));
		player.refreshPositionAndAngles(corner.x, corner.y, corner.z, look.asRotation(), 0.0F);
		player.setHeadYaw(look.asRotation());
	}

	static void removePlayer(TestContext context, ServerPlayerEntity player) {
		context.getWorld().getServer().getPlayerManager().remove(player);
	}

	/**
	 * Uses the block's item on a face of the block at {@code clicked} exactly like a right-click would, so the real
	 * {@code getPlacementState} and {@code canPlaceAt} run. Returns the result; the new state is in the world.
	 */
	static ActionResult place(TestContext context, PlayerEntity player, Block block, BlockPos clickedRelative, Direction side) {
		final BlockPos clicked = context.getAbsolutePos(clickedRelative);
		final ItemStack stack = new ItemStack(block.asItem());
		player.setStackInHand(Hand.MAIN_HAND, stack);
		final Vec3d hitPos = Vec3d.ofCenter(clicked).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
		final BlockHitResult hit = new BlockHitResult(hitPos, side, clicked, false);
		final ItemPlacementContext placement = new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit);
		if (!(block.asItem() instanceof BlockItem item)) {
			throw fail(block + " has no BlockItem");
		}
		return item.place(placement);
	}

	static BlockState stateAt(TestContext context, BlockPos relative) {
		return context.getBlockState(relative);
	}

	static void clear(TestContext context, int from, int to) {
		for (int x = from; x <= to; x++) {
			for (int y = from; y <= to; y++) {
				for (int z = from; z <= to; z++) {
					context.setBlockState(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	static boolean hasFacing(BlockState state) {
		return state.contains(HorizontalFacingBlock.FACING);
	}

	static String describe(BlockState state) {
		return Registries.BLOCK.getId(state.getBlock()).getPath() + (state.getProperties().isEmpty() ? "" : state.toString().substring(state.toString().indexOf('[')));
	}
}
