package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.FareGateKind;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.Map;

import static net.minecraft.block.Block.createCuboidShape;

/**
 * Fare gate unit (A10). A prop: no fare logic and no moving parts. The cabinet (west side of the north-facing model) is
 * solid; the paddles are drawn but have no collision, so the passage beside the cabinet is always walkable.
 */
public class FareGateBlock extends FacingKindBlock<FareGateKind> {

	public static final EnumProperty<FareGateKind> KIND = EnumProperty.of("kind", FareGateKind.class);
	private static final Map<Direction, VoxelShape> CABINET = Shapes.horizontalMap(createCuboidShape(0, 0, 1, 4, 14, 15));

	public FareGateBlock(Settings settings) {
		super(settings, "fare_gate", Placement.TOWARD_PLAYER, FareGateKind.class, kind -> switch (kind) {
			case GATE -> net.minecraft.util.shape.VoxelShapes.union(createCuboidShape(0, 0, 1, 4, 14, 15), createCuboidShape(4, 5, 7, 9, 12, 9));
			case WIDE -> net.minecraft.util.shape.VoxelShapes.union(createCuboidShape(0, 0, 1, 4, 14, 15), createCuboidShape(4, 5, 7, 13, 12, 9));
			case END -> createCuboidShape(0, 0, 1, 4, 14, 15);
		});
	}

	@Override
	protected EnumProperty<FareGateKind> kindProperty() {
		return KIND;
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return CABINET.get(state.get(FACING));
	}
}
