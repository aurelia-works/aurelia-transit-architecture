package com.aureliatransit.architecture.wayfinding;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * World-level (overworld) persistent holder of the {@link ServiceMessages}. No MTR or client types.
 */
public final class ServiceMessageState extends PersistentState {

	public static final String ID = "ata_service_messages";

	private ServiceMessages messages = ServiceMessages.EMPTY;

	public static ServiceMessageState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(ServiceMessageState::fromNbt, ServiceMessageState::new, ID);
	}

	public static ServiceMessageState fromNbt(NbtCompound nbt) {
		final ServiceMessageState state = new ServiceMessageState();
		state.messages = ServiceMessages.fromNbt(nbt);
		return state;
	}

	public ServiceMessages messages() {
		return messages;
	}

	public void set(ServiceMessages value) {
		if (!value.equals(messages)) {
			messages = value;
			markDirty();
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.copyFrom(messages.toNbt());
		return nbt;
	}
}
