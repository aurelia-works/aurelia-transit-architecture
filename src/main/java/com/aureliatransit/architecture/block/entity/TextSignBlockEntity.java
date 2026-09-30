package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.block.TextLayout;
import com.aureliatransit.architecture.block.TextSignBlock;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;

/**
 * Stores the plain-text lines of an editable sign.
 */
public class TextSignBlockEntity extends BlockEntity {

	public static final int MAX_LINES = 3;
	public static final int MAX_LENGTH = 40;
	private static final String KEY_LINES = "Lines";

	private final String[] lines = new String[MAX_LINES];

	public TextSignBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TEXT_SIGN, pos, state);
		Arrays.fill(lines, "");
	}

	public List<String> getLines() {
		return List.of(lines);
	}

	public TextLayout getLayout() {
		return getCachedState().getBlock() instanceof TextSignBlock sign ? sign.getLayout() : null;
	}

	public void setLines(List<String> newLines) {
		final TextLayout layout = getLayout();
		final int maxLines = layout == null ? MAX_LINES : Math.min(MAX_LINES, layout.maxLines());
		final int maxLength = layout == null ? MAX_LENGTH : Math.min(MAX_LENGTH, layout.maxLength());
		for (int i = 0; i < MAX_LINES; i++) {
			lines[i] = i < maxLines && i < newLines.size() ? sanitize(newLines.get(i), maxLength) : "";
		}
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	public static String sanitize(String text, int maxLength) {
		final String stripped = Formatting.strip(text == null ? "" : text);
		final String clean = stripped == null ? "" : stripped.replaceAll("\\p{Cntrl}", "").trim();
		return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		Arrays.fill(lines, "");
		final NbtList list = nbt.getList(KEY_LINES, NbtElement.STRING_TYPE);
		for (int i = 0; i < Math.min(MAX_LINES, list.size()); i++) {
			lines[i] = sanitize(list.getString(i), MAX_LENGTH);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		final NbtList list = new NbtList();
		for (final String line : lines) {
			list.add(NbtString.of(line));
		}
		nbt.put(KEY_LINES, list);
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
