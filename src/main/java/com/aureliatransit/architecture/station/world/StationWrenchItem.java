package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationRecipe;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The station wrench: the station generator without typing commands. It remembers the station chosen in its screen (the
 * choices, and the description they make or one typed in its advanced mode).
 * <ul>
 *     <li>right-click the air: open the screen (pick a look, size and roof; place, build, undo)</li>
 *     <li>right-click a block: plan the station on top of it, facing the way you look (the outline shows, and chat offers
 *     Build it / Cancel buttons)</li>
 *     <li>sneak + right-click a block: build the pending plan (never approves terrain changes)</li>
 *     <li>sneak + right-click the air: show the outline again</li>
 *     <li>left-click a block: cancel the running build or discard the plan</li>
 * </ul>
 * Every entry point checks the operator level on the server ({@link StationActions#allowed}) before running a
 * {@link StationActions} action. The wrench never breaks blocks.
 */
public class StationWrenchItem extends Item {

	/** Stack NBT key of the remembered description. Client-writable in creative, so it is bounded again on every read. */
	public static final String DESCRIPTION = "Description";
	/** Stack NBT key of the screen's choices ({@link StationRecipe#encode}); bounded like the description. */
	public static final String RECIPE = "Recipe";
	public static final int MAX_RECIPE = 160;
	/** Server ticks between two left-click cancels, so holding the button down does not repeat it. */
	static final int CANCEL_COOLDOWN_TICKS = 20;
	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".station_wrench";

	/** Client only: opens the wrench screen for the stack in {@code hand}. Set by the client initializer. */
	public static Consumer<Hand> openScreen = hand -> {
	};

	/** What the wrench screen asks the server to do (after saving its description). */
	public enum Action {
		SAVE, PLAN, PREVIEW, CONFIRM, CONFIRM_REPLACE_TERRAIN, CANCEL, UNDO
	}

	public StationWrenchItem(Settings settings) {
		super(settings);
	}

	/** The words a plan is made from: the remembered description. The airport wrench makes sure they describe an airport. */
	protected String wordsToPlan(String description) {
		return description;
	}

	public static String description(ItemStack stack) {
		final NbtCompound nbt = stack.getNbt();
		return nbt == null ? "" : bounded(nbt.getString(DESCRIPTION));
	}

	public static void setDescription(ItemStack stack, String description) {
		final String text = bounded(description).strip();
		if (text.isEmpty()) {
			stack.removeSubNbt(DESCRIPTION);
		} else {
			stack.getOrCreateNbt().putString(DESCRIPTION, text);
		}
	}

	public static StationRecipe recipe(ItemStack stack) {
		final NbtCompound nbt = stack.getNbt();
		return StationRecipe.decode(nbt == null ? "" : bounded(nbt.getString(RECIPE), MAX_RECIPE));
	}

	public static void setRecipe(ItemStack stack, String recipe) {
		final String text = bounded(recipe, MAX_RECIPE);
		if (text.isEmpty()) {
			stack.removeSubNbt(RECIPE);
		} else {
			stack.getOrCreateNbt().putString(RECIPE, StationRecipe.decode(text).encode());
		}
	}

	private static String bounded(String text, int max) {
		return text.length() > max ? text.substring(0, max) : text;
	}

	private static String bounded(String text) {
		return text.length() > StationDescriptionParser.MAX_INPUT ? text.substring(0, StationDescriptionParser.MAX_INPUT) : text;
	}

	/**
	 * Server side of the screen: saves the description to the wrench in {@code hand}, then runs {@code action}. Ignored unless
	 * that hand holds a wrench or the player is not an operator.
	 */
	public static void perform(ServerPlayerEntity player, Hand hand, Action action, String description, String recipe) {
		final ItemStack stack = player.getStackInHand(hand);
		if (!(stack.getItem() instanceof StationWrenchItem) || !StationActions.allowed(player)) {
			return;
		}
		setDescription(stack, description);
		setRecipe(stack, recipe);
		switch (action) {
			case SAVE -> player.sendMessage(Text.literal("Station description saved on the wrench.").formatted(Formatting.GRAY), true);
			case PLAN -> StationActions.plan(player, ((StationWrenchItem) stack.getItem()).wordsToPlan(description(stack)), null);
			case PREVIEW -> StationActions.preview(player);
			case CONFIRM -> StationActions.confirm(player, false);
			case CONFIRM_REPLACE_TERRAIN -> StationActions.confirm(player, true);
			case CANCEL -> StationActions.cancel(player);
			case UNDO -> StationActions.undo(player);
		}
	}

	/**
	 * Server side of Station Studio: runs {@code action} for the player with the studio's description, and remembers the design
	 * on a wrench in either hand (so its next click on the ground places it). Ignored unless the player is an operator.
	 */
	public static void performFromStudio(ServerPlayerEntity player, Action action, String description, String recipe) {
		if (!StationActions.allowed(player)) {
			return;
		}
		final String text = bounded(description).strip();
		for (final Hand hand : Hand.values()) {
			final ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof StationWrenchItem) {
				setDescription(stack, text);
				setRecipe(stack, recipe);
			}
		}
		switch (action) {
			case SAVE -> player.sendMessage(Text.literal("Station Studio design saved on the wrench.").formatted(Formatting.GRAY), true);
			case PLAN -> StationActions.plan(player, text, null);
			case PREVIEW -> StationActions.preview(player);
			case CONFIRM -> StationActions.confirm(player, false);
			case CONFIRM_REPLACE_TERRAIN -> StationActions.confirm(player, true);
			case CANCEL -> StationActions.cancel(player);
			case UNDO -> StationActions.undo(player);
		}
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		final PlayerEntity player = context.getPlayer();
		if (player == null) {
			return ActionResult.PASS;
		}
		if (player instanceof ServerPlayerEntity server && StationActions.allowed(server)) {
			if (player.isSneaking()) {
				StationActions.confirm(server, false);
			} else {
				StationActions.plan(server, wordsToPlan(description(context.getStack())), context.getBlockPos());
			}
		}
		// success on both sides, so use() (the screen) does not also fire
		return ActionResult.success(context.getWorld().isClient);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		final ItemStack stack = player.getStackInHand(hand);
		if (!player.isSneaking()) {
			if (world.isClient) {
				if (player.hasPermissionLevel(StationActions.PERMISSION_LEVEL)) {
					openScreen.accept(hand);
				} else {
					player.sendMessage(Text.literal("Only operators can use the station generator.").formatted(Formatting.RED), true);
				}
			}
		} else if (player instanceof ServerPlayerEntity server && StationActions.allowed(server)) {
			StationActions.preview(server);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	/**
	 * Left-click on a block with the wrench (from {@code AttackBlockCallback}): cancels on the server. Returns SUCCESS on both
	 * sides, so the client sends the click to the server and the server cancels breaking; PASS for any other item.
	 */
	public static ActionResult attack(PlayerEntity player, World world, Hand hand) {
		if (!(player.getStackInHand(hand).getItem() instanceof StationWrenchItem)) {
			return ActionResult.PASS;
		}
		if (player instanceof ServerPlayerEntity server && StationSessions.cancelCooldown(server.getUuid(), server.getServer().getTicks(), CANCEL_COOLDOWN_TICKS)
				&& StationActions.allowed(server)) {
			StationActions.cancel(server);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
		return false;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		final String description = description(stack);
		if (!description.isEmpty()) {
			final StationRecipe recipe = recipe(stack);
			tooltip.add(Text.literal(description.equals(recipe.description()) ? recipe.title() : "\"" + description + "\"").formatted(Formatting.AQUA));
		}
		tooltip.add(Text.translatable(TIP + ".edit").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".plan").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".build").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".cancel").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable(TIP + ".ops").formatted(Formatting.DARK_GRAY));
	}
}
