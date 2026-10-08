package com.aureliatransit.architecture.gametest;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.SignPoleBlock;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import com.aureliatransit.architecture.block.wayfinding.CarStopBlock;
import com.aureliatransit.architecture.gametest.Fixtures.Entry;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import com.aureliatransit.architecture.text.SignData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.test.CustomTestProvider;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestFunction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.aureliatransit.architecture.gametest.Fixtures.HORIZONTAL;
import static com.aureliatransit.architecture.gametest.Fixtures.describe;
import static com.aureliatransit.architecture.gametest.Fixtures.fail;
import static com.aureliatransit.architecture.gametest.Fixtures.failIfAny;

/**
 * Headless in-game tests, run with {@code ./gradlew runGametest}. Most suites are data driven: one test function per
 * block of the mod registry, so a new block is covered the moment it is registered.
 *
 * <ol>
 *     <li>{@link #everyStateOfEveryBlock}: place up to 64 sampled states, check they survive a tick and have shapes</li>
 *     <li>{@link #performanceBudget}: decorative blocks have no block entity, ticker or random tick</li>
 *     <li>{@link #lootOfEveryBlock}: breaking drops what the loot table says</li>
 *     <li>interaction, connection and placement suites below</li>
 * </ol>
 */
public class AureliaGameTests implements FabricGameTest {

	private static final String SUITE = AureliaTransitArchitecture.MOD_ID + "_gametest.";

	/**
	 * Blocks that are allowed a block entity: they hold text or configuration or draw live data. Derived from the code:
	 * every block class that implements BlockEntityProvider (TextSignBlock and subclasses, WayfindingPlateBlock and
	 * subclasses, PidsBlock, InfoDisplayBlock, ClockBlock, SpeakerBlock, TrainEdgeBlock). None of them has a ticker; the
	 * live displays update by network packets and client-side rendering. A new block with a block entity must be added here
	 * on purpose, which is the point of the budget.
	 */
	static final Map<String, String> FUNCTIONAL_BLOCK_ENTITIES = Map.ofEntries(
			// TextSignBlock: editable sign text
			Map.entry("station_name_sign", "TextSignBlock"), Map.entry("hanging_station_sign", "TextSignBlock"),
			Map.entry("platform_number_sign", "TextSignBlock"), Map.entry("direction_sign", "TextSignBlock"),
			Map.entry("composition_board", "TextSignBlock"), Map.entry("bus_stop_sign", "TextSignBlock"),
			Map.entry("psd_text_panel", "TextSignBlock"), Map.entry("stand_back_sign", "TextSignBlock"),
			Map.entry("dutch_station_sign", "TextSignBlock"), Map.entry("dutch_platform_sign", "TextSignBlock"),
			Map.entry("german_station_sign", "TextSignBlock"),
			// LiftStatusPanelBlock extends TextSignBlock: lift name and levels
			Map.entry("lift_status_panel", "TextSignBlock"),
			// WayfindingPlateBlock and subclasses (WayfindingSignBlock, EntrancePylonBlock): configurable wayfinding and terminal panels
			Map.entry("pictogram_sign", "WayfindingPlateBlock"), Map.entry("street_sign", "WayfindingPlateBlock"),
			Map.entry("wall_wayfinding_sign", "WayfindingSignBlock"), Map.entry("hanging_wayfinding_sign", "WayfindingSignBlock"),
			Map.entry("exit_sign", "WayfindingSignBlock"), Map.entry("station_info_board", "WayfindingSignBlock"),
			Map.entry("bus_epaper_board", "WayfindingSignBlock"), Map.entry("passenger_info_terminal", "WayfindingSignBlock"),
			Map.entry("entrance_pylon", "EntrancePylonBlock"), Map.entry("passenger_info_kiosk", "EntrancePylonBlock"),
			// PidsBlock: live departure boards; SpeakerBlock: announcement speakers; ClockBlock; InfoDisplayBlock: hand-typed notice cases
			Map.entry("platform_cis", "PidsBlock"), Map.entry("hanging_platform_cis", "PidsBlock"),
			Map.entry("platform_pids", "PidsBlock"), Map.entry("hanging_platform_pids", "PidsBlock"), Map.entry("concourse_board", "PidsBlock"),
			Map.entry("wall_speaker", "SpeakerBlock"), Map.entry("ceiling_speaker", "SpeakerBlock"),
			Map.entry("hanging_digital_clock", "ClockBlock"), Map.entry("wall_digital_clock", "ClockBlock"), Map.entry("station_analog_clock", "ClockBlock"),
			Map.entry("information_case", "InfoDisplayBlock"), Map.entry("information_pillar", "InfoDisplayBlock"), Map.entry("bus_timetable_case", "InfoDisplayBlock"),
			// TrainEdgeBlock: platform edges that react to a stopped train
			Map.entry("drop_barrier_edge", "TrainEdgeBlock"), Map.entry("boarding_step_edge", "TrainEdgeBlock")
	);

	private static final int[] CELL = {1, 5};

	// ---- 1. every state of every block ------------------------------------------------------------------------------

	@CustomTestProvider
	public Collection<TestFunction> everyStateOfEveryBlock() {
		final List<TestFunction> tests = new ArrayList<>();
		for (final Entry entry : Fixtures.blocks()) {
			final int rounds = (Fixtures.sampleStates(entry.block(), Fixtures.STATE_CAP).size() + 7) / 8;
			tests.add(function("states_" + entry.tab(), "states_" + entry.id(), 2 * rounds + 10, context -> placeStates(context, entry)));
		}
		return tests;
	}

	/** Eight states per round, one in each 4x4x4 cell of the test area, each wrapped in stone so wall mounts have a wall. */
	private static void placeStates(TestContext context, Entry entry) {
		final List<BlockState> states = Fixtures.sampleStates(entry.block(), Fixtures.STATE_CAP);
		final int rounds = (states.size() + 7) / 8;
		final List<String> problems = new ArrayList<>();
		for (int round = 0; round < rounds; round++) {
			final int first = round * 8;
			final List<BlockState> batch = states.subList(first, Math.min(states.size(), first + 8));
			if (round == 0) {
				placeRound(context, batch, problems);
			} else {
				context.runAtTick(2L * round, () -> placeRound(context, batch, problems));
			}
			context.runAtTick(2L * round + 1, () -> checkRound(context, batch, problems));
		}
		context.runAtTick(2L * rounds + 1, () -> {
			AureliaTransitArchitecture.LOGGER.info("[gametest] states {} {}", entry.id(), states.size());
			failIfAny(problems, entry.id() + " (" + states.size() + " states placed)");
			context.complete();
		});
	}

	private static BlockPos cell(int index) {
		return new BlockPos(CELL[index & 1], CELL[(index >> 1) & 1], CELL[(index >> 2) & 1]);
	}

	private static void placeRound(TestContext context, List<BlockState> batch, List<String> problems) {
		Fixtures.clear(context, 0, 7);
		for (int i = 0; i < batch.size(); i++) {
			final BlockPos pos = cell(i);
			final BlockState state = batch.get(i);
			try {
				for (final Direction side : Direction.values()) {
					context.setBlockState(pos.offset(side), Blocks.STONE);
				}
				// the upper half of a two-block-tall block needs its lower half underneath
				if (state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
					context.setBlockState(pos.down(), state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				}
				context.setBlockState(pos, state);
			} catch (Throwable t) {
				problems.add(describe(state) + ": exception while placing: " + t);
			}
		}
	}

	private static void checkRound(TestContext context, List<BlockState> batch, List<String> problems) {
		final ServerWorld world = context.getWorld();
		for (int i = 0; i < batch.size(); i++) {
			final BlockPos pos = cell(i);
			final BlockState expected = batch.get(i);
			try {
				final BlockState actual = context.getBlockState(pos);
				if (actual != expected) {
					problems.add(describe(expected) + ": state changed within a tick to " + describe(actual));
					continue;
				}
				final BlockPos abs = context.getAbsolutePos(pos);
				final VoxelShape outline = actual.getOutlineShape(world, abs, ShapeContext.absent());
				final VoxelShape collision = actual.getCollisionShape(world, abs, ShapeContext.absent());
				if (outline == null || collision == null) {
					problems.add(describe(expected) + ": null " + (outline == null ? "outline" : "collision") + " shape");
				}
				if (actual.getBlock() instanceof BlockEntityProvider provider) {
					final BlockEntity entity = world.getBlockEntity(abs);
					if (entity == null && provider.createBlockEntity(abs, actual) != null) {
						problems.add(describe(expected) + ": block entity missing after placement");
					}
				}
			} catch (Throwable t) {
				problems.add(describe(expected) + ": exception while checking: " + t);
			}
		}
	}

	// ---- 2. performance budget --------------------------------------------------------------------------------------

	@CustomTestProvider
	public Collection<TestFunction> performanceBudget() {
		final List<TestFunction> tests = new ArrayList<>();
		for (final Entry entry : Fixtures.blocks()) {
			tests.add(function("budget_" + entry.tab(), "budget_" + entry.id(), 5, context -> {
				checkBudget(context, entry);
				context.complete();
			}));
		}
		return tests;
	}

	private static void checkBudget(TestContext context, Entry entry) {
		final Block block = entry.block();
		final List<String> problems = new ArrayList<>();
		for (final BlockState state : Fixtures.sampleStates(block, Fixtures.STATE_CAP)) {
			if (state.hasRandomTicks()) {
				problems.add(describe(state) + ": has random ticks");
			}
		}
		final boolean hasBlockEntity = block instanceof BlockEntityProvider;
		final boolean allowed = FUNCTIONAL_BLOCK_ENTITIES.containsKey(entry.id());
		if (hasBlockEntity && !allowed) {
			problems.add("has a block entity but is not on the functional-block allowlist (AureliaGameTests.FUNCTIONAL_BLOCK_ENTITIES); "
					+ "decorative blocks must not have block entities");
		}
		if (!hasBlockEntity && allowed) {
			problems.add("is on the allowlist but has no block entity any more; remove it from FUNCTIONAL_BLOCK_ENTITIES");
		}
		if (block instanceof BlockEntityProvider provider) {
			final BlockPos abs = context.getAbsolutePos(new BlockPos(1, 1, 1));
			final BlockEntity entity = provider.createBlockEntity(abs, block.getDefaultState());
			if (entity != null && provider.getTicker(context.getWorld(), block.getDefaultState(), entity.getType()) != null) {
				problems.add("block entity has a ticker (getTicker is not null)");
			}
		}
		failIfAny(problems, entry.id());
	}

	// ---- 3. loot ----------------------------------------------------------------------------------------------------

	@CustomTestProvider
	public Collection<TestFunction> lootOfEveryBlock() {
		final List<TestFunction> tests = new ArrayList<>();
		for (final Entry entry : Fixtures.blocks()) {
			tests.add(function("loot_" + entry.tab(), "loot_" + entry.id(), 10, context -> {
				checkLoot(context, entry);
				context.complete();
			}));
		}
		return tests;
	}

	private static void checkLoot(TestContext context, Entry entry) {
		final Block block = entry.block();
		final Item self = block.asItem();
		final JsonObject table = lootTable(entry.id());
		final ServerWorld world = context.getWorld();
		final ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		final BlockPos pos = new BlockPos(3, 3, 3);
		final BlockPos abs = context.getAbsolutePos(pos);
		final List<String> problems = new ArrayList<>();
		for (final BlockState state : Fixtures.sampleStates(block, Fixtures.STATE_CAP)) {
			try {
				context.setBlockState(pos.down(), Blocks.STONE);
				if (state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
					context.setBlockState(pos.down(), state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				}
				context.setBlockState(pos, state);
				if (!state.isIn(BlockTags.PICKAXE_MINEABLE)) {
					problems.add(describe(state) + ": not in the pickaxe-mineable tag");
				}
				if (state.getHardness(world, abs) < 0) {
					problems.add(describe(state) + ": unbreakable");
				}
				final List<ItemStack> drops = Block.getDroppedStacks(state, world, abs, world.getBlockEntity(abs), null, pickaxe);
				final boolean expectSelf = tableDropsSelf(table, entry.id(), state);
				final boolean dropsSelf = drops.stream().anyMatch(stack -> stack.isOf(self) && stack.getCount() >= 1);
				if (expectSelf && !dropsSelf) {
					problems.add(describe(state) + ": loot table says it drops itself but dropped " + drops);
				}
				if (!expectSelf && !drops.isEmpty()) {
					problems.add(describe(state) + ": loot table has no drop for this state but dropped " + drops);
				}
				if (expectSelf && drops.isEmpty()) {
					problems.add(describe(state) + ": dropped nothing");
				}
			} catch (Throwable t) {
				problems.add(describe(state) + ": exception: " + t);
			}
		}
		if (!tableDropsSelf(table, entry.id(), block.getDefaultState()) && !block.getDefaultState().contains(Properties.DOUBLE_BLOCK_HALF)) {
			problems.add("the loot table never drops the block itself");
		}
		failIfAny(problems, entry.id());
	}

	private static JsonObject lootTable(String id) {
		final String path = "/data/" + AureliaTransitArchitecture.MOD_ID + "/loot_tables/blocks/" + id + ".json";
		try (InputStream in = AureliaGameTests.class.getResourceAsStream(path)) {
			if (in == null) {
				throw fail("missing loot table " + path);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw fail("cannot read loot table " + path + ": " + e);
		}
	}

	/** Reads the loot table JSON: does any entry give the block's own item for this state (honouring block_state_property conditions)? */
	private static boolean tableDropsSelf(JsonObject table, String id, BlockState state) {
		final String name = AureliaTransitArchitecture.MOD_ID + ":" + id;
		for (final JsonElement pool : table.getAsJsonArray("pools")) {
			for (final JsonElement element : pool.getAsJsonObject().getAsJsonArray("entries")) {
				final JsonObject entry = element.getAsJsonObject();
				if (!name.equals(entry.has("name") ? entry.get("name").getAsString() : "")) {
					continue;
				}
				boolean applies = true;
				if (entry.has("conditions")) {
					for (final JsonElement condition : entry.getAsJsonArray("conditions")) {
						final JsonObject c = condition.getAsJsonObject();
						if ("minecraft:block_state_property".equals(c.get("condition").getAsString())) {
							for (final Map.Entry<String, JsonElement> required : c.getAsJsonObject("properties").entrySet()) {
								final Property<?> property = state.getBlock().getStateManager().getProperty(required.getKey());
								applies &= property != null && propertyName(state, property).equals(required.getValue().getAsString());
							}
						}
					}
				}
				if (applies) {
					return true;
				}
			}
		}
		return false;
	}

	private static <T extends Comparable<T>> String propertyName(BlockState state, Property<T> property) {
		return property.name(state.get(property));
	}

	// ---- 4. interaction ---------------------------------------------------------------------------------------------

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void carStopBoardsCycleUpDownAndWrap(TestContext context) {
		final ServerPlayerEntity player = Fixtures.player(context, Direction.NORTH);
		final List<String> problems = new ArrayList<>();
		final BlockPos pos = new BlockPos(3, 3, 3);
		int boards = 0;
		for (final Entry entry : Fixtures.blocks()) {
			if (!(entry.block() instanceof CarStopBlock board)) {
				continue;
			}
			boards++;
			final IntProperty cars = board.cars();
			final int min = cars.getValues().stream().min(Integer::compare).orElseThrow();
			final int max = cars.getValues().stream().max(Integer::compare).orElseThrow();
			for (final boolean wall : new boolean[]{false, true}) {
				context.setBlockState(pos.west(), Blocks.STONE);
				context.setBlockState(pos, board.getDefaultState().with(CarStopBlock.WALL, wall).with(CarStopBlock.FACING, Direction.EAST).with(cars, min));
				player.setSneaking(false);
				// the mod hands every new player a transit card; the cycle needs an empty hand
				player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
				// right-click counts up through every number and wraps back to the lowest
				for (int expected = min + 1; expected <= max; expected++) {
					rightClick(context, player, pos);
					expectCars(context, entry, pos, cars, expected, "right-click", problems);
				}
				rightClick(context, player, pos);
				expectCars(context, entry, pos, cars, min, "right-click wrap from " + max, problems);
				// sneak-right-click counts down and wraps from the lowest to the highest
				player.setSneaking(true);
				rightClick(context, player, pos);
				expectCars(context, entry, pos, cars, max, "sneak-right-click wrap from " + min, problems);
				rightClick(context, player, pos);
				expectCars(context, entry, pos, cars, max - 1, "sneak-right-click", problems);
				player.setSneaking(false);
				// holding a block item places that block instead of cycling
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
				final int before = context.getBlockState(pos).get(cars);
				final ActionResult withBlock = use(context, player, pos);
				if (withBlock != ActionResult.PASS || context.getBlockState(pos).get(cars) != before) {
					problems.add(entry.id() + ": a block in hand must not cycle the number (result " + withBlock + ")");
				}
				player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			}
		}
		Fixtures.removePlayer(context, player);
		if (boards < 3) {
			problems.add("expected at least the 3 car stop boards, found " + boards);
		}
		failIfAny(problems, "car stop boards");
		context.complete();
	}

	private static ActionResult use(TestContext context, ServerPlayerEntity player, BlockPos pos) {
		final BlockPos abs = context.getAbsolutePos(pos);
		final BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.EAST, abs, false);
		return context.getBlockState(pos).onUse(context.getWorld(), player, Hand.MAIN_HAND, hit);
	}

	private static ActionResult lastClick = ActionResult.FAIL;
	private static ItemStack lastHand = ItemStack.EMPTY;

	private static void rightClick(TestContext context, ServerPlayerEntity player, BlockPos pos) {
		lastClick = use(context, player, pos);
		lastHand = player.getStackInHand(Hand.MAIN_HAND);
	}

	private static void expectCars(TestContext context, Entry entry, BlockPos pos, IntProperty cars, int expected, String what, List<String> problems) {
		final BlockState state = context.getBlockState(pos);
		if (!state.isOf(entry.block()) || state.get(cars) != expected) {
			problems.add(entry.id() + ": after " + what + " expected cars=" + expected + " but state is " + describe(state) + " (click result " + lastClick + ", , hand=" + lastHand + ")");
		}
	}

	/** The three new regional signs behave like the existing station name sign: right-click opens the editor, text is stored. */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void editableSignsAcceptTextLikeTheStationNameSign(TestContext context) {
		final ServerPlayerEntity player = Fixtures.player(context, Direction.NORTH);
		final List<String> problems = new ArrayList<>();
		final BlockPos pos = new BlockPos(3, 3, 3);
		final String[] ids = {"station_name_sign", "dutch_station_sign", "dutch_platform_sign", "german_station_sign"};
		for (final String id : ids) {
			final Block block = Fixtures.block(id).block();
			if (!(block instanceof TextSignBlock sign)) {
				problems.add(id + ": not a TextSignBlock");
				continue;
			}
			context.setBlockState(pos.down(), Blocks.STONE);
			final ActionResult placed = Fixtures.place(context, player, block, pos.down(), Direction.UP);
			if (!placed.isAccepted() || !context.getBlockState(pos).isOf(block)) {
				problems.add(id + ": could not be placed (" + placed + ")");
				continue;
			}
			if (!(context.getWorld().getBlockEntity(context.getAbsolutePos(pos)) instanceof TextSignBlockEntity entity)) {
				problems.add(id + ": no TextSignBlockEntity after placement");
				continue;
			}
			if (!ModBlockEntities.TEXT_SIGN.supports(context.getBlockState(pos))) {
				problems.add(id + ": TEXT_SIGN block entity type does not support it");
			}
			// empty hand: server side consumes the click (the client opens the editor); a block item in hand just places
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			final ActionResult empty = use(context, player, pos);
			if (!empty.isAccepted()) {
				problems.add(id + ": empty-hand right-click returned " + empty + ", expected the click to be consumed (editor opens)");
			}
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
			if (use(context, player, pos) != ActionResult.PASS) {
				problems.add(id + ": a block in hand must pass through so it can be placed");
			}
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			// text: stored, trimmed to the limits, and survives a save and load of the block entity
			final String longName = "Utrecht Centraal Station with an extremely long name that cannot fit";
			entity.setData(new SignData(longName, "Platform 4", null, null, null, "4", false, List.of()));
			final SignData stored = entity.getData();
			if (stored.primary().isEmpty() || stored.primary().length() > SignData.MAX_PRIMARY) {
				problems.add(id + ": primary text not stored within " + SignData.MAX_PRIMARY + " characters: '" + stored.primary() + "'");
			}
			final NbtCompound nbt = entity.createNbt();
			final TextSignBlockEntity copy = new TextSignBlockEntity(context.getAbsolutePos(pos), context.getBlockState(pos));
			copy.readNbt(nbt);
			if (!copy.getData().equals(stored)) {
				problems.add(id + ": text did not survive an NBT round trip: " + copy.getData() + " vs " + stored);
			}
			final SignData reread = readPacket(context, stored);
			if (!stored.equals(reread)) {
				problems.add(id + ": text did not survive the edit packet encoding");
			}
			context.setBlockState(pos, Blocks.AIR);
		}
		Fixtures.removePlayer(context, player);
		failIfAny(problems, "editable signs");
		context.complete();
	}

	private static SignData readPacket(TestContext context, SignData data) {
		final net.minecraft.network.PacketByteBuf buf = com.aureliatransit.architecture.network.ModPackets.writeSign(context.getAbsolutePos(new BlockPos(3, 3, 3)), data);
		buf.readBlockPos();
		return SignData.read(buf);
	}

	// ---- 5. connections ---------------------------------------------------------------------------------------------

	private static List<Entry> panes() {
		return Fixtures.blocks().stream().filter(entry -> entry.block() instanceof PaneBlock).toList();
	}

	private static List<Entry> fullGlass() {
		return Fixtures.blocks().stream().filter(entry -> entry.block() instanceof AbstractGlassBlock).toList();
	}

	/** Each glass pane joins a pane of the same kind, every other pane of the mod, and every full glass block of the mod. */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 200)
	public void glassPanesConnectToPanesAndGlassBlocks(TestContext context) {
		final ServerPlayerEntity player = Fixtures.player(context, Direction.NORTH);
		final List<String> problems = new ArrayList<>();
		final BlockPos centre = new BlockPos(3, 2, 3);
		final List<Entry> panes = panes();
		final List<Entry> glass = fullGlass();
		if (panes.size() < 9 || glass.size() < 9) {
			problems.add("expected at least 9 panes and 9 full glass blocks, found " + panes.size() + " and " + glass.size());
		}
		for (final Entry pane : panes) {
			final List<Block> neighbours = new ArrayList<>();
			panes.forEach(other -> neighbours.add(other.block()));
			glass.forEach(other -> neighbours.add(other.block()));
			neighbours.add(Blocks.GLASS);
			neighbours.add(Blocks.GLASS_PANE);
			for (final Block neighbour : neighbours) {
				for (final Direction side : HORIZONTAL) {
					Fixtures.clear(context, 0, 7);
					context.setBlockState(centre.down(), Blocks.STONE);
					context.setBlockState(centre.offset(side).down(), Blocks.STONE);
					Fixtures.place(context, player, pane.block(), centre.down(), Direction.UP);
					Fixtures.place(context, player, neighbour, centre.offset(side).down(), Direction.UP);
					final BlockState paneState = context.getBlockState(centre);
					final BlockState neighbourState = context.getBlockState(centre.offset(side));
					final String name = pane.id() + " <-> " + Registries.BLOCK.getId(neighbour).getPath() + " (" + side + ")";
					if (!paneState.isOf(pane.block()) || !neighbourState.isOf(neighbour)) {
						problems.add(name + ": placement failed: " + describe(paneState) + " / " + describe(neighbourState));
						continue;
					}
					if (!paneState.get(connection(side))) {
						problems.add(name + ": the pane does not connect to its neighbour");
					}
					if (neighbour instanceof PaneBlock && !neighbourState.get(connection(side.getOpposite()))) {
						problems.add(name + ": the neighbouring pane does not connect back");
					}
				}
			}
			if (problems.size() > 12) {
				break;
			}
		}
		Fixtures.removePlayer(context, player);
		failIfAny(problems, "glass pane connections");
		context.complete();
	}

	private static net.minecraft.state.property.BooleanProperty connection(Direction side) {
		return switch (side) {
			case NORTH -> PaneBlock.NORTH;
			case EAST -> PaneBlock.EAST;
			case SOUTH -> PaneBlock.SOUTH;
			default -> PaneBlock.WEST;
		};
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void fullGlassBlocksHideFacesAgainstThemselves(TestContext context) {
		final List<String> problems = new ArrayList<>();
		for (final Entry entry : fullGlass()) {
			final BlockState state = entry.block().getDefaultState();
			for (final Direction side : Direction.values()) {
				if (!state.isSideInvisible(state, side)) {
					problems.add(entry.id() + ": does not report " + side + " as invisible against itself");
				}
			}
		}
		failIfAny(problems, "isSideInvisible");
		context.complete();
	}

	/** Sign poles join the three regional signs and the free-standing car stop boards, from below and above, but not wall-mounted boards. */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void signPolesJoinSignsAndFreestandingBoardsOnly(TestContext context) {
		final ServerPlayerEntity player = Fixtures.player(context, Direction.NORTH);
		final Block pole = Fixtures.block("sign_pole").block();
		final List<String> problems = new ArrayList<>();
		final String[] joiners = {"dutch_station_sign", "dutch_platform_sign", "german_station_sign", "uk_car_stop_marker", "german_stop_board", "dutch_stop_board"};
		final BlockPos polePos = new BlockPos(3, 2, 3);
		for (final String id : joiners) {
			final Block sign = Fixtures.block(id).block();
			final boolean board = sign instanceof CarStopBlock;
			// sign above the pole: the pole's UP flag
			Fixtures.clear(context, 0, 7);
			context.setBlockState(polePos.down(), Blocks.STONE);
			Fixtures.place(context, player, pole, polePos.down(), Direction.UP);
			Fixtures.place(context, player, sign, polePos, Direction.UP);
			BlockState above = context.getBlockState(polePos.up());
			BlockState poleState = context.getBlockState(polePos);
			if (!above.isOf(sign)) {
				problems.add(id + ": could not be placed above a pole: " + describe(above));
			} else {
				if (board && above.get(CarStopBlock.WALL)) {
					problems.add(id + ": placed from above it should stand on its own post but is wall-mounted");
				}
				if (!poleState.get(SignPoleBlock.UP)) {
					problems.add(id + ": a pole below does not join it (up=false)");
				}
			}
			// sign hanging under a pole (pole above, sign below)
			Fixtures.clear(context, 0, 7);
			context.setBlockState(polePos.down(2), Blocks.STONE);
			Fixtures.place(context, player, sign, polePos.down(2), Direction.UP);
			Fixtures.place(context, player, pole, polePos.down(), Direction.UP);
			final BlockState hanging = context.getBlockState(polePos.down());
			final BlockState poleAbove = context.getBlockState(polePos);
			if (hanging.isOf(sign) && poleAbove.isOf(pole) && !poleAbove.get(SignPoleBlock.DOWN)) {
				problems.add(id + ": a pole above does not join it (down=false)");
			}
			// wall-mounted boards: the pole must stop short
			if (board) {
				Fixtures.clear(context, 0, 7);
				context.setBlockState(polePos.down(), Blocks.STONE);
				context.setBlockState(polePos.up().west(), Blocks.STONE);
				Fixtures.place(context, player, pole, polePos.down(), Direction.UP);
				Fixtures.place(context, player, sign, polePos.up().west(), Direction.EAST);
				final BlockState mounted = context.getBlockState(polePos.up());
				if (!mounted.isOf(sign) || !mounted.get(CarStopBlock.WALL)) {
					problems.add(id + ": could not place a wall-mounted board above the pole: " + describe(mounted));
				} else if (context.getBlockState(polePos).get(SignPoleBlock.UP)) {
					problems.add(id + ": the pole joins a wall-mounted board (up=true) but it has no post of its own");
				}
			}
		}
		Fixtures.removePlayer(context, player);
		failIfAny(problems, "sign pole connections");
		context.complete();
	}

	// ---- 6. placement -----------------------------------------------------------------------------------------------

	/** Tooltips that say the front faces the player; the block ends up facing opposite to where the player looks. */
	private static final Set<String> FACES_YOU = Set.of("faces_you");
	/** Tooltips that say the block points, rises or runs the way the player looks. */
	private static final Set<String> POINTS_AHEAD = Set.of("points_away", "slope", "rises_ahead", "brace_facing", "wf_guidance");

	@CustomTestProvider
	public Collection<TestFunction> placementFacing() {
		final List<TestFunction> tests = new ArrayList<>();
		for (final Entry entry : Fixtures.blocks()) {
			if (Fixtures.hasFacing(entry.block().getDefaultState())) {
				tests.add(function("placement_" + entry.tab(), "placement_" + entry.id(), 20, context -> {
					checkPlacement(context, entry);
					context.complete();
				}));
			}
		}
		return tests;
	}

	private static void checkPlacement(TestContext context, Entry entry) {
		final Block block = entry.block();
		final List<String> tips = Fixtures.tooltips(block);
		final boolean facesYou = tips.stream().anyMatch(FACES_YOU::contains);
		final boolean pointsAhead = tips.stream().anyMatch(POINTS_AHEAD::contains);
		final boolean wallMounted = tips.contains("wall_mounted");
		AureliaTransitArchitecture.LOGGER.info("[gametest] placement {} rule={}", entry.id(), facesYou ? "faces_you" : pointsAhead ? "points_ahead" : wallMounted ? "wall_mounted" : "distinct_facings_only");
		final List<String> problems = new ArrayList<>();
		final Set<Direction> seen = new java.util.HashSet<>();
		final ServerPlayerEntity player = Fixtures.player(context, Direction.NORTH);
		final BlockPos floor = new BlockPos(3, 1, 3);
		final BlockPos pos = floor.up();
		for (final Direction look : HORIZONTAL) {
			Fixtures.clear(context, 0, 7);
			context.setBlockState(floor, Blocks.STONE);
			Fixtures.face(context, player, look);
			final ActionResult result = Fixtures.place(context, player, block, floor, Direction.UP);
			final BlockState state = context.getBlockState(pos);
			if (!state.isOf(block)) {
				problems.add("player looking " + look + ": placing it failed (" + result + "), found " + describe(state));
				continue;
			}
			final Direction facing = state.get(net.minecraft.block.HorizontalFacingBlock.FACING);
			seen.add(facing);
			if (facesYou && facing != look.getOpposite()) {
				problems.add("player looking " + look + ": tooltip says it faces you, so facing should be " + look.getOpposite() + " but is " + facing);
			}
			if (pointsAhead && facing != look) {
				problems.add("player looking " + look + ": tooltip says it follows the way you look, so facing should be " + look + " but is " + facing);
			}
			if (wallMounted && state.contains(CarStopBlock.WALL)) {
				if (state.get(CarStopBlock.WALL) || facing != look.getOpposite()) {
					problems.add("player looking " + look + " at the floor: expected a free-standing board facing " + look.getOpposite() + ", got " + describe(state));
				}
			}
		}
		if (seen.size() != 4) {
			problems.add("the four look directions produced only " + seen.size() + " different facings: " + seen);
		}
		if (wallMounted && block.getDefaultState().contains(CarStopBlock.WALL)) {
			for (final Direction side : HORIZONTAL) {
				Fixtures.clear(context, 0, 7);
				context.setBlockState(floor, Blocks.STONE);
				Fixtures.face(context, player, Direction.NORTH);
				Fixtures.place(context, player, block, floor, side);
				final BlockState state = context.getBlockState(floor.offset(side));
				if (!state.isOf(block) || !state.get(CarStopBlock.WALL) || state.get(net.minecraft.block.HorizontalFacingBlock.FACING) != side) {
					problems.add("aimed at the " + side + " side of a wall: expected it mounted on that wall facing " + side + ", got " + describe(state));
				}
			}
		}
		Fixtures.removePlayer(context, player);
		failIfAny(problems, entry.id() + " tooltips " + tips);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	private static TestFunction function(String batch, String name, int tickLimit, Consumer<TestContext> body) {
		return new TestFunction(batch, SUITE + name, FabricGameTest.EMPTY_STRUCTURE, BlockRotation.NONE, tickLimit, 0L, true, body);
	}
}
