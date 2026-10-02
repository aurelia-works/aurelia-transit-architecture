package com.aureliatransit.architecture.wayfinding;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * World-level (overworld) persistent holder of the {@link StationSuffixes}. No MTR or client types.
 */
public final class StationSuffixState extends PersistentState {

	public static final String ID = "ata_station_suffixes";

	private StationSuffixes suffixes = StationSuffixes.EMPTY;

	public static StationSuffixState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(StationSuffixState::fromNbt, StationSuffixState::new, ID);
	}

	public static StationSuffixState fromNbt(NbtCompound nbt) {
		final StationSuffixState state = new StationSuffixState();
		state.suffixes = StationSuffixes.fromNbt(nbt);
		return state;
	}

	public StationSuffixes suffixes() {
		return suffixes;
	}

	public void set(StationSuffixes value) {
		if (!value.equals(suffixes)) {
			suffixes = value;
			markDirty();
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.copyFrom(suffixes.toNbt());
		return nbt;
	}
}
