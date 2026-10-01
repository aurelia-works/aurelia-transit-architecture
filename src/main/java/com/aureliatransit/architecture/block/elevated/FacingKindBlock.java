package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A horizontally rotating block with a selectable style ("kind"): wall panels, braces, fascias, tactile junctions. The
 * kind is a block state cycled by right-clicking with an empty hand, so every look is one inventory item. Shapes are
 * authored for the north-facing model, one per kind, and rotated per facing once.
 */
public abstract class FacingKindBlock<K extends Enum<K> & StringIdentifiable> extends HorizontalFacingBlock {

	private final String id;
	private final Placement placement;
	private final Map<K, Map<Direction, VoxelShape>> shapes;

	protected FacingKindBlock(Settings settings, String id, Placement placement, Class<K> kinds, Function<K, VoxelShape> northShape) {
		super(settings);
		this.id = id;
		this.placement = placement;
		this.shapes = new EnumMap<>(kinds);
		for (final K kind : kinds.getEnumConstants()) {
			shapes.put(kind, Shapes.horizontalMap(northShape.apply(kind)));
		}
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(kindProperty(), kinds.getEnumConstants()[0]));
	}

	/** The block's (static) kind property; called while the state definition is built, so it must not use instance fields. */
	protected abstract EnumProperty<K> kindProperty();

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, kindProperty());
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final Direction looking = ctx.getHorizontalPlayerFacing();
		return getDefaultState().with(FACING, placement == Placement.TOWARD_PLAYER ? looking.getOpposite() : looking);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes.get(state.get(kindProperty())).get(state.get(FACING));
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		return StyleCycle.use(id, kindProperty(), state, world, pos, player, hand);
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
