package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.CardReaderKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShapes;

import static net.minecraft.block.Block.createCuboidShape;

/** Fare card reader / validator (A10). A prop: no fare logic. */
public class CardReaderBlock extends FacingKindBlock<CardReaderKind> {

	public static final EnumProperty<CardReaderKind> KIND = EnumProperty.of("kind", CardReaderKind.class);

	public CardReaderBlock(Settings settings) {
		super(settings, "card_reader", Placement.TOWARD_PLAYER, CardReaderKind.class, kind -> switch (kind) {
			case POST -> VoxelShapes.union(createCuboidShape(6.5, 0, 6.5, 9.5, 11, 9.5), createCuboidShape(5, 11, 5, 11, 16, 11));
			case WALL -> createCuboidShape(5, 5, 12, 11, 12, 16);
		});
	}

	@Override
	protected EnumProperty<CardReaderKind> kindProperty() {
		return KIND;
	}
}
