package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.elevated.ElevatedKinds.BeamKind;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * The viaduct beam family in one block: crossbeam, girder, stringer and platform support beam, each in steel or concrete
 * and along either horizontal axis. Right-click with an empty hand cycles the role; sneak + right-click switches
 * between steel and concrete. Shapes are authored along X and turned for Z.
 */
public class ViaductBeamBlock extends StateShapedBlock {

	public static final EnumProperty<Direction.Axis> AXIS = net.minecraft.state.property.Properties.HORIZONTAL_AXIS;
	public static final EnumProperty<BeamKind> KIND = EnumProperty.of("kind", BeamKind.class);
	public static final BooleanProperty CONCRETE = BooleanProperty.of("concrete");

	private static final Map<BeamKind, VoxelShape> ALONG_X = new EnumMap<>(BeamKind.class);
	private static final Map<BeamKind, VoxelShape> ALONG_Z = new EnumMap<>(BeamKind.class);

	static {
		ALONG_X.put(BeamKind.CROSSBEAM, box(0, 0, 3, 16, 16, 13));
		ALONG_X.put(BeamKind.GIRDER, box(0, 6, 2, 16, 16, 14));
		ALONG_X.put(BeamKind.STRINGER, union(box(0, 14, 4, 16, 16, 12), box(0, 10, 7, 16, 14, 9)));
		ALONG_X.put(BeamKind.PLATFORM_SUPPORT, union(box(0, 12, 0, 16, 16, 16), box(0, 5, 6, 16, 12, 10)));
		ALONG_X.forEach((kind, shape) -> ALONG_Z.put(kind, Shapes.rotate(shape, 1)));
	}

	public ViaductBeamBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(AXIS, Direction.Axis.X).with(KIND, BeamKind.CROSSBEAM).with(CONCRETE, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(AXIS, KIND, CONCRETE);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(AXIS, ctx.getHorizontalPlayerFacing().getAxis());
	}

	@Override
	protected VoxelShape outlineFor(BlockState state) {
		return (state.get(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z).get(state.get(KIND));
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (player.isSneaking() && hand == Hand.MAIN_HAND && player.getStackInHand(hand).isEmpty() && player.canModifyBlocks()) {
			if (!world.isClient) {
				final BlockState updated = state.cycle(CONCRETE);
				world.setBlockState(pos, updated, Block.NOTIFY_ALL);
				player.sendMessage(net.minecraft.text.Text.translatable("message.aurelia_transit_architecture.style.viaduct_beam." + (updated.get(CONCRETE) ? "concrete" : "steel")), true);
			}
			return ActionResult.success(world.isClient);
		}
		return StyleCycle.use("viaduct_beam", KIND, state, world, pos, player, hand);
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
			return state.with(AXIS, state.get(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}
}
