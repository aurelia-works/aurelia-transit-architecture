package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.TextSignBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import com.aureliatransit.architecture.util.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A sign with editable text. Joining signs track neighbours on their local left (-X) and right (+X) so that a row of
 * identical signs renders as one continuous panel; the leftmost (-X) block of a row owns the text.
 */
public class TextSignBlock extends FacingShapedBlock implements BlockEntityProvider {

	/**
	 * Connected to an identical sign on the model's -X side.
	 */
	public static final BooleanProperty LEFT = BooleanProperty.of("left");
	/**
	 * Connected to an identical sign on the model's +X side.
	 */
	public static final BooleanProperty RIGHT = BooleanProperty.of("right");
	public static final int MAX_ROW = 32;

	/**
	 * Set by the client initializer; opens the text editor for a sign position. A no-op on dedicated servers.
	 */
	public static Consumer<BlockPos> openEditor = pos -> {
	};

	private final TextLayout layout;
	/** Outlines per facing, indexed by joins: 0 alone, 1 left, 2 right, 3 both. */
	private final List<Map<Direction, VoxelShape>> joinedOutlines = new ArrayList<>();

	public TextSignBlock(Settings settings, TextLayout layout, VoxelShape northOutline) {
		super(settings, Placement.TOWARD_PLAYER, northOutline);
		this.layout = layout;
		for (int joins = 0; joins < 4; joins++) {
			joinedOutlines.add(Shapes.horizontalMap(joined(northOutline, (joins & 1) != 0, (joins & 2) != 0)));
		}
		setDefaultState(getDefaultState().with(LEFT, false).with(RIGHT, false));
	}

	/**
	 * A north-facing outline as a joined sign shows it: the models drop the posts and hangers on a joined side, so the outline
	 * keeps only the panel there (the part spanning the block's whole width) instead of leaving legs to stand in mid-row.
	 */
	static VoxelShape joined(VoxelShape north, boolean left, boolean right) {
		if (!left && !right) {
			return north;
		}
		final List<double[]> leftEdge = new ArrayList<>();
		final List<double[]> rightEdge = new ArrayList<>();
		north.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
			if (minX <= 0) {
				leftEdge.add(new double[]{minY, minZ, maxY, maxZ});
			}
			if (maxX >= 1) {
				rightEdge.add(new double[]{minY, minZ, maxY, maxZ});
			}
		});
		VoxelShape panel = VoxelShapes.empty();
		for (final double[] a : leftEdge) {
			for (final double[] b : rightEdge) {
				final double minY = Math.max(a[0], b[0]);
				final double minZ = Math.max(a[1], b[1]);
				final double maxY = Math.min(a[2], b[2]);
				final double maxZ = Math.min(a[3], b[3]);
				if (minY < maxY && minZ < maxZ) {
					panel = VoxelShapes.union(panel, VoxelShapes.cuboid(0, minY, minZ, 1, maxY, maxZ));
				}
			}
		}
		VoxelShape cut = VoxelShapes.empty();
		if (left) {
			cut = VoxelShapes.union(cut, VoxelShapes.cuboid(0, 0, 0, 0.5, 1, 1));
		}
		if (right) {
			cut = VoxelShapes.union(cut, VoxelShapes.cuboid(0.5, 0, 0, 1, 1, 1));
		}
		final VoxelShape trimmed = VoxelShapes.combineAndSimplify(cut, panel, BooleanBiFunction.ONLY_FIRST);
		return VoxelShapes.combineAndSimplify(north, trimmed, BooleanBiFunction.ONLY_FIRST);
	}

	private VoxelShape joinedOutline(BlockState state) {
		return joinedOutlines.get((state.get(LEFT) ? 1 : 0) | (state.get(RIGHT) ? 2 : 0)).get(state.get(FACING));
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return FreePlacement.outline(state, joinedOutline(state));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return FreePlacement.collision(state, joinedOutline(state));
	}

	public TextLayout getLayout() {
		return layout;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(LEFT, RIGHT);
		FreePlacement.addNudge(builder);
	}

	public static Direction localLeft(Direction facing) {
		return facing.rotateYCounterclockwise();
	}

	public static Direction localRight(Direction facing) {
		return facing.rotateYClockwise();
	}

	private boolean joinsWith(BlockState self, BlockState other) {
		return layout.joins() && other.isOf(this) && other.get(FACING) == self.get(FACING) && FreePlacement.joinable(self, other);
	}

	private BlockState withConnections(BlockState state, WorldAccess world, BlockPos pos) {
		final Direction facing = state.get(FACING);
		return state
				.with(LEFT, joinsWith(state, world.getBlockState(pos.offset(localLeft(facing)))))
				.with(RIGHT, joinsWith(state, world.getBlockState(pos.offset(localRight(facing)))));
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		final BlockState state = super.getPlacementState(ctx);
		return state == null ? null : withConnections(state, ctx.getWorld(), ctx.getBlockPos());
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction.getAxis().isHorizontal()) {
			return withConnections(state, world, pos);
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	/**
	 * Finds the block that owns the text for the row containing {@code pos}.
	 */
	public BlockPos findRowOwner(World world, BlockPos pos, BlockState state) {
		final Direction left = localLeft(state.get(FACING));
		BlockPos current = pos;
		BlockState currentState = state;
		for (int i = 0; i < MAX_ROW && currentState.isOf(this) && currentState.get(LEFT); i++) {
			final BlockPos next = current.offset(left);
			final BlockState nextState = world.getBlockState(next);
			if (!joinsWith(currentState, nextState)) {
				break;
			}
			current = next;
			currentState = nextState;
		}
		return current;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (player.getStackInHand(hand).getItem() instanceof BlockItem || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			openEditor.accept(findRowOwner(world, pos, state));
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new TextSignBlockEntity(pos, state);
	}
}
