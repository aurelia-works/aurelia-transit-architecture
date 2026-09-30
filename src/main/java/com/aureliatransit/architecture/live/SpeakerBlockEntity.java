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
 * Speaker configuration. The client-side announcement engine reads {@link #config()}; {@link #configVersion()} lets it
 * notice edits cheaply.
 */
public class SpeakerBlockEntity extends BlockEntity {

	private SpeakerConfig config = SpeakerConfig.DEFAULT;
	private int configVersion;

	public SpeakerBlockEntity(BlockPos pos, BlockState state) {
		super(LiveBlocks.SPEAKER_ENTITY, pos, state);
	}

	public SpeakerConfig config() {
		return config;
	}

	public int configVersion() {
		return configVersion;
	}

	public void setConfig(SpeakerConfig newConfig) {
		this.config = newConfig;
		configVersion++;
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		config = SpeakerConfig.readNbt(nbt);
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
