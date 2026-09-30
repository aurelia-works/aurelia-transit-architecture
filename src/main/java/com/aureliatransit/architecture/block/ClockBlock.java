package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.ClockBlockEntity;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/**
 * A station clock showing Minecraft world time. The block entity only exists so the renderer can draw; it holds no
 * data and is never ticked.
 */
public class ClockBlock extends FacingShapedBlock implements BlockEntityProvider {

	/**
	 * @param analog  analog dial instead of a digital readout
	 * @param centerY vertical centre of the face in model pixels
	 * @param width   face width in model pixels
	 * @param height  face height in model pixels
	 * @param frontZ  z of the front face
	 * @param backZ   z of the back face, or a negative value for single-sided clocks
	 */
	public record Face(boolean analog, float centerY, float width, float height, float frontZ, float backZ) {

		public boolean doubleSided() {
			return backZ >= 0;
		}
	}

	private final Face face;

	public ClockBlock(Settings settings, Placement placement, VoxelShape northOutline, Face face) {
		super(settings, placement, northOutline);
		this.face = face;
	}

	public Face getFace() {
		return face;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ClockBlockEntity(pos, state);
	}
}
