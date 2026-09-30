package com.aureliatransit.architecture.live;

import com.aureliatransit.architecture.registry.LiveBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/**
 * Holds the configuration of a live display. No ticking: the client renderer reads {@link #config()} and keeps its
 * own derived layout in {@link #clientCache()}, invalidated through {@link #configVersion()}.
 */
public class PidsBlockEntity extends BlockEntity {

	private DisplayConfig config;
	private int configVersion;
	/**
	 * Client-only render cache, opaque to common code (set and read by the client renderer).
	 */
	private Object clientCache;

	public PidsBlockEntity(BlockPos pos, BlockState state) {
		super(LiveBlocks.PIDS_ENTITY, pos, state);
		this.config = DisplayConfig.defaults(kind());
	}

	public DisplayKind kind() {
		return getCachedState().getBlock() instanceof PidsBlock block ? block.kind() : DisplayKind.PIDS;
	}

	public DisplayConfig config() {
		return config;
	}

	public int configVersion() {
		return configVersion;
	}

	public Object clientCache() {
		return clientCache;
	}

	public void setClientCache(Object clientCache) {
		this.clientCache = clientCache;
	}

	public void setConfig(DisplayConfig newConfig) {
		this.config = newConfig.forKind(kind());
		configVersion++;
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		config = DisplayConfig.readNbt(nbt, kind());
		configVersion++;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		config.writeNbt(nbt);
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		return createNbt();
	}
}
