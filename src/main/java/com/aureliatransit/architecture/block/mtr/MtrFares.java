package com.aureliatransit.architecture.block.mtr;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mtr.mod.SoundEvents;
import org.mtr.mod.data.TicketSystem;

import java.util.function.Consumer;

/**
 * The calls into MTR's {@link TicketSystem}. Only reached through {@link MtrFareContract} once it has confirmed the
 * methods exist. Arguments match MTR's own ticket barrier ({@code BlockTicketBarrier.onEntityCollision2}), except that
 * entry and exit are both allowed, as on MTR's combined ticket processor: MTR picks entry or exit from the player's
 * record.
 */
final class MtrFares {

	private MtrFares() {
	}

	static void passThrough(World world, BlockPos pos, PlayerEntity player, Consumer<Boolean> opened) {
		TicketSystem.passThrough(new org.mtr.mapping.holder.World(world), new org.mtr.mapping.holder.BlockPos(pos), new org.mtr.mapping.holder.PlayerEntity(player),
				true, true,
				SoundEvents.TICKET_BARRIER.get(), SoundEvents.TICKET_BARRIER_CONCESSIONARY.get(),
				SoundEvents.TICKET_BARRIER.get(), SoundEvents.TICKET_BARRIER_CONCESSIONARY.get(),
				null, false,
				result -> opened.accept(result == TicketSystem.EnumTicketBarrierOpen.OPEN || result == TicketSystem.EnumTicketBarrierOpen.OPEN_CONCESSIONARY));
	}

	static int balance(World world, PlayerEntity player) {
		return TicketSystem.getBalance(new org.mtr.mapping.holder.World(world), new org.mtr.mapping.holder.PlayerEntity(player));
	}

	static void addBalance(World world, PlayerEntity player, int amount) {
		TicketSystem.addBalance(new org.mtr.mapping.holder.World(world), new org.mtr.mapping.holder.PlayerEntity(player), amount);
	}
}
