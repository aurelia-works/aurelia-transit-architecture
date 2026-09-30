package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.block.InfoDisplayBlock;
import com.aureliatransit.architecture.block.InfoLayout;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import com.aureliatransit.architecture.text.ConfigurableTextData;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/**
 * Stores the {@link ConfigurableTextData} of an information case, pillar or timetable case. The data object is
 * immutable and replaced wholesale on edit, so renderers may cache layouts by its identity.
 */
public class InfoDisplayBlockEntity extends BlockEntity {

	private static final String KEY = "Info";

	private ConfigurableTextData text = ConfigurableTextData.EMPTY;

	public InfoDisplayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.INFO_DISPLAY, pos, state);
	}

	public ConfigurableTextData getText() {
		return text;
	}

	public InfoLayout getLayout() {
		return getCachedState().getBlock() instanceof InfoDisplayBlock block ? block.getLayout() : null;
	}

	/**
	 * Server side: replaces the content, dropping body rows this block cannot show.
	 */
	public void setText(ConfigurableTextData newText) {
		final ConfigurableTextData restricted = restrict(newText, getLayout());
		if (restricted.equals(text)) {
			return;
		}
		text = restricted;
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	public static ConfigurableTextData restrict(ConfigurableTextData data, InfoLayout layout) {
		if (layout == null || data.body().size() <= layout.maxBodyLines()) {
			return data;
		}
		return new ConfigurableTextData(data.heading(), data.body().subList(0, layout.maxBodyLines()), data.alignment(), data.accent());
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		text = restrict(ConfigurableTextData.readNbt(nbt, KEY), getLayout());
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		text.writeNbt(nbt, KEY);
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
