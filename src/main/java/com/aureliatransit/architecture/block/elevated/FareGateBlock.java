package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.FareGateKind;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.FareGateOpen;
import com.aureliatransit.architecture.block.mtr.MtrFareContract;
import com.aureliatransit.architecture.fare.TransitCardItem;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Map;

import static net.minecraft.block.Block.createCuboidShape;

/**
 * Fare gate unit (A10) that charges fares through MTR's own fare system, like MTR's ticket barrier: walk into the
 * passage from the front (green arrow) carrying a transit card, MTR charges the entry or exit fare from its balance and
 * the paddles open for one passage. The gate closes once the player is clear of the paddles, or two seconds after the
 * last player left the gate; it never closes on a player standing in it, so a pause in the gate is never charged twice.
 * MTR decides entry or exit from the player's record and only charges inside an MTR station area; outside one, or with
 * too little balance, the paddles stay shut (MTR says why). An {@code END} cabinet has no passage.
 *
 * <p>Collision is 1.5 blocks high, like a fence, so the gate cannot be jumped. Without MTR's fare system the gate is a
 * walkable prop, as before 1.4. No block entity and no ticking: work happens only while a player touches the block.
 */
public class FareGateBlock extends FacingKindBlock<FareGateKind> {

	public static final EnumProperty<FareGateKind> KIND = EnumProperty.of("kind", FareGateKind.class);
	public static final EnumProperty<FareGateOpen> OPEN = EnumProperty.of("open", FareGateOpen.class);
	private static final int CLOSE_DELAY_TICKS = 40;
	/** Player centre this far past the paddle line: the player's box (half width 0.3) is clear of the paddles. */
	private static final double CLEAR_OF_PADDLES = 0.4;
	private static final Map<Direction, VoxelShape> CABINET = Shapes.horizontalMap(createCuboidShape(0, 0, 1, 4, 24, 15));
	private static final Map<Direction, VoxelShape> SHUT = Shapes.horizontalMap(VoxelShapes.union(createCuboidShape(0, 0, 1, 4, 24, 15), createCuboidShape(4, 0, 7, 16, 24, 9)));
	private static final Map<Direction, VoxelShape> PROP = Shapes.horizontalMap(createCuboidShape(0, 0, 1, 4, 14, 15));

	public FareGateBlock(Settings settings) {
		super(settings, "fare_gate", Placement.TOWARD_PLAYER, FareGateKind.class, kind -> switch (kind) {
			case GATE -> VoxelShapes.union(createCuboidShape(0, 0, 1, 4, 14, 15), createCuboidShape(4, 5, 7, 9, 12, 9));
			case WIDE -> VoxelShapes.union(createCuboidShape(0, 0, 1, 4, 14, 15), createCuboidShape(4, 5, 7, 13, 12, 9));
			case END -> createCuboidShape(0, 0, 1, 4, 14, 15);
		});
		setDefaultState(getDefaultState().with(OPEN, FareGateOpen.CLOSED));
	}

	@Override
	protected EnumProperty<FareGateKind> kindProperty() {
		return KIND;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(OPEN);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		final Direction facing = state.get(FACING);
		if (!MtrFareContract.available()) {
			return PROP.get(facing);
		}
		return state.get(KIND) == FareGateKind.END || state.get(OPEN) == FareGateOpen.OPEN ? CABINET.get(facing) : SHUT.get(facing);
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient || !(entity instanceof PlayerEntity player) || state.get(KIND) == FareGateKind.END || !MtrFareContract.available()) {
			return;
		}
		// > 0: on the front (green arrow) side of the paddles; < 0: past them.
		final Direction facing = state.get(FACING);
		final double ahead = (player.getX() - pos.getX() - 0.5) * facing.getOffsetX() + (player.getZ() - pos.getZ() - 0.5) * facing.getOffsetZ();
		final FareGateOpen open = state.get(OPEN);
		if (open == FareGateOpen.OPEN && ahead < -CLEAR_OF_PADDLES) {
			world.setBlockState(pos, state.with(OPEN, FareGateOpen.CLOSED));
		} else if (open == FareGateOpen.CLOSED && ahead > 0) {
			if (!TransitCardItem.carriedBy(player)) {
				player.sendMessage(Text.translatable("message." + AureliaTransitArchitecture.MOD_ID + ".card.need_card"), true);
				return;
			}
			// The pos handed to onEntityCollision is a mutable cursor that the caller moves on to other blocks; MTR answers
			// later, so its callback must hold a copy.
			final BlockPos gatePos = pos.toImmutable();
			// PENDING until the scheduled tick: a refused player (no station, low balance) is asked again at most every
			// two seconds instead of every tick, and the gate still closes if MTR never answers.
			world.setBlockState(gatePos, state.with(OPEN, FareGateOpen.PENDING));
			world.scheduleBlockTick(gatePos, this, CLOSE_DELAY_TICKS);
			MtrFareContract.passThrough(world, gatePos, player, opened -> {
				final BlockState now = world.getBlockState(gatePos);
				if (opened && now.isOf(this)) {
					world.setBlockState(gatePos, now.with(OPEN, FareGateOpen.OPEN));
				}
			});
		}
	}

	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (state.get(OPEN) == FareGateOpen.CLOSED) {
			return;
		}
		if (!world.getEntitiesByClass(PlayerEntity.class, new Box(pos), player -> !player.isSpectator()).isEmpty()) {
			world.scheduleBlockTick(pos, this, CLOSE_DELAY_TICKS);
		} else {
			world.setBlockState(pos, state.with(OPEN, FareGateOpen.CLOSED));
		}
	}
}
