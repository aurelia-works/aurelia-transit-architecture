package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.block.wayfinding.WayfindingPanelSpec;
import com.aureliatransit.architecture.block.wayfinding.WayfindingPlateBlock;
import com.aureliatransit.architecture.registry.WayfindingBlocks;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingEditable;
import com.aureliatransit.architecture.wayfinding.WayfindingPanelKind;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/**
 * The one block entity type of every {@link WayfindingData} block (pylon, directional/exit signs, street blade,
 * pictogram sign, bus e-paper board). The data is immutable and replaced wholesale, so renderers cache by its identity.
 */
public class WayfindingSignBlockEntity extends BlockEntity implements WayfindingEditable {

	private WayfindingData data;

	public WayfindingSignBlockEntity(BlockPos pos, BlockState state) {
		super(WayfindingBlocks.SIGN_ENTITY, pos, state);
		this.data = defaults(state);
	}

	private static WayfindingData defaults(BlockState state) {
		return state.getBlock() instanceof WayfindingPlateBlock block ? block.spec().defaults() : WayfindingData.EMPTY;
	}

	/**
	 * The panel description of this block, or null if the block is not a wayfinding block any more.
	 */
	public WayfindingPanelSpec spec() {
		return getCachedState().getBlock() instanceof WayfindingPlateBlock block ? block.spec() : null;
	}

	@Override
	public WayfindingData getWayfinding() {
		return data;
	}

	@Override
	public void setWayfinding(WayfindingData newData) {
		if (newData.equals(data)) {
			return;
		}
		data = newData;
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	public WayfindingPanelKind panelKind() {
		final WayfindingPanelSpec spec = spec();
		return spec == null ? WayfindingPanelKind.WALL_DIRECTION : spec.kind();
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		data = WayfindingData.readNbt(nbt, defaults(getCachedState()));
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		data.writeNbt(nbt);
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
