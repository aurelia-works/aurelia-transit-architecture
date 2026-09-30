package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import com.aureliatransit.architecture.text.SignData;
import com.aureliatransit.architecture.text.SignStyle;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/**
 * Stores the {@link SignData} of an editable sign. V1 worlds stored plain lines under "Lines"; those are still read.
 * The data object is immutable and replaced wholesale on edit, so renderers may cache layouts by its identity.
 */
public class TextSignBlockEntity extends BlockEntity {

	private SignData data = SignData.EMPTY;

	public TextSignBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TEXT_SIGN, pos, state);
	}

	public SignData getData() {
		return data;
	}

	public TextLayout getLayout() {
		return getCachedState().getBlock() instanceof TextSignBlock sign ? sign.getLayout() : null;
	}

	public SignStyle getStyle() {
		final TextLayout layout = getLayout();
		return layout == null ? SignStyle.STATION : layout.style();
	}

	/**
	 * Server side: replaces the sign content, dropping anything this kind of sign cannot show.
	 */
	public void setData(SignData newData) {
		final SignData restricted = newData.restrictedTo(getStyle());
		if (restricted.equals(data)) {
			return;
		}
		data = restricted;
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		data = SignData.readNbt(nbt, getStyle());
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
