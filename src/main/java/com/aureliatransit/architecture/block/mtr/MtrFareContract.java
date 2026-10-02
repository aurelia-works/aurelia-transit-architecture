package com.aureliatransit.architecture.block.mtr;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * ATA's fare gate and transit card use MTR's own fare system: the same balance (scoreboard {@code mtr_balance}) that
 * MTR's ticket machines top up, MTR's entry/exit records and zone fares, and MTR's evasion fine. Nothing is stored by
 * ATA. {@code org.mtr.mod.data.TicketSystem} is public with public static methods, so no mixin is needed.
 *
 * <p>If a future MTR removes or changes those methods, the gate falls back to a walkable prop and {@code /card} says
 * fares are unavailable; the class that names MTR's types ({@link MtrFares}) is loaded only after this check passes.
 */
public final class MtrFareContract {

	private static final String TICKET_SYSTEM = "org.mtr.mod.data.TicketSystem";
	private static final boolean AVAILABLE = detect();

	private MtrFareContract() {
	}

	public static boolean available() {
		return AVAILABLE;
	}

	/**
	 * Asks MTR to let {@code player} through a gate at {@code pos} (server side). MTR decides entry or exit from the
	 * player's record, charges the fare and plays the barrier sound; {@code opened} receives the answer, possibly a
	 * little later. Without MTR fares the answer is always {@code false}.
	 */
	public static void passThrough(World world, BlockPos pos, PlayerEntity player, Consumer<Boolean> opened) {
		if (AVAILABLE) {
			MtrFares.passThrough(world, pos, player, opened);
		} else {
			opened.accept(false);
		}
	}

	public static int balance(World world, PlayerEntity player) {
		return AVAILABLE ? MtrFares.balance(world, player) : 0;
	}

	public static void addBalance(World world, PlayerEntity player, int amount) {
		if (AVAILABLE) {
			MtrFares.addBalance(world, player, amount);
		}
	}

	private static boolean detect() {
		try {
			final Class<?> tickets = Class.forName(TICKET_SYSTEM, false, MtrFareContract.class.getClassLoader());
			boolean pass = false;
			boolean balance = false;
			boolean add = false;
			for (final Method method : tickets.getMethods()) {
				switch (method.getName()) {
					case "passThrough" -> pass |= method.getParameterCount() == 12;
					case "getBalance" -> balance |= method.getParameterCount() == 2;
					case "addBalance" -> add |= method.getParameterCount() == 3;
					default -> {
					}
				}
			}
			if (pass && balance && add) {
				return true;
			}
			AureliaTransitArchitecture.LOGGER.warn("{} has changed; ATA fare gates are props and /card is unavailable", TICKET_SYSTEM);
			return false;
		} catch (ClassNotFoundException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("MTR fare system {} not found; ATA fare gates are props and /card is unavailable", TICKET_SYSTEM);
			return false;
		}
	}
}
