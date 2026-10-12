package com.aureliatransit.architecture.station.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Lift;
import org.mtr.core.data.LiftFloor;
import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketDeleteData;

import java.util.List;

/**
 * The calls into MTR's lift data. Only reached through {@link MtrLiftContract} once it has confirmed they exist. Creation
 * follows MTR's own Lift Refresher ({@code ItemLiftRefresher.sendUpdate}): a new lift with its stops, a 3 high 2×2 car and
 * MTR's default style, sent to MTR's simulation as {@code generate_by_lift}, which replaces any lift on the same stops.
 * Deletion follows MTR's break handler for a stop ({@code BlockLiftTrackFloor.onBreak2}).
 */
final class MtrLifts {

	private static final String FLOOR_NUMBER = "floor_number";
	private static final String FLOOR_DESCRIPTION = "floor_description";
	private static final String BUTTON_FLOORS = "track_floor_pos";

	private MtrLifts() {
	}

	static void create(ServerWorld world, List<MtrLiftContract.Shaft> shafts, List<BlockPos> buttons) {
		for (final MtrLiftContract.Shaft shaft : shafts) {
			final ObjectArrayList<LiftFloor> floors = new ObjectArrayList<>();
			for (int i = 0; i < shaft.stops().size(); i++) {
				final BlockPos stop = shaft.stops().get(i);
				final String number = Integer.toString(i);
				floors.add(new LiftFloor(new Position(stop.getX(), stop.getY(), stop.getZ()), number, ""));
				edit(world, stop, nbt -> {
					nbt.putString(FLOOR_NUMBER, number);
					nbt.putString(FLOOR_DESCRIPTION, "");
				});
			}
			final Lift lift = new Lift(new MinecraftClientData());
			lift.setFloors(floors);
			lift.setDimensions(3, 2, 2, shaft.offsetX(), 0, shaft.offsetZ());
			lift.setIsDoubleSided(false);
			lift.setStyle("default_transparent");
			lift.setAngle(switch (shaft.doors()) {
				case EAST -> Angle.E;
				case SOUTH -> Angle.S;
				case WEST -> Angle.W;
				case NORTH -> Angle.N;
			});
			Init.sendMessageC2S("generate_by_lift", new org.mtr.mapping.holder.MinecraftServer(world.getServer()),
					new org.mtr.mapping.holder.World(world), lift, null, null);
		}
		// each call button calls the lift whose stop is at its level (the button sits at hand height, one above the stop), beside it
		for (final BlockPos button : buttons) {
			BlockPos nearest = null;
			for (final MtrLiftContract.Shaft shaft : shafts) {
				for (final BlockPos stop : shaft.stops()) {
					if ((stop.getY() == button.getY() || stop.getY() == button.getY() - 1) && (nearest == null || stop.getSquaredDistance(button) < nearest.getSquaredDistance(button))) {
						nearest = stop;
					}
				}
			}
			if (nearest != null) {
				final long linked = nearest.asLong();
				edit(world, button, nbt -> nbt.putLongArray(BUTTON_FLOORS, new long[]{linked}));
			}
		}
	}

	static void remove(ServerWorld world, List<BlockPos> stops) {
		final org.mtr.mapping.holder.ServerWorld mtrWorld = new org.mtr.mapping.holder.ServerWorld(world);
		for (final BlockPos stop : stops) {
			PacketDeleteData.sendDirectlyToServerLiftFloorPosition(mtrWorld, new Position(stop.getX(), stop.getY(), stop.getZ()));
		}
	}

	/** Changes a block entity's saved data and sends it to the players watching. */
	private static void edit(ServerWorld world, BlockPos pos, java.util.function.Consumer<NbtCompound> change) {
		final BlockEntity entity = world.getBlockEntity(pos);
		if (entity == null) {
			return;
		}
		final NbtCompound nbt = entity.createNbt();
		change.accept(nbt);
		entity.readNbt(nbt);
		entity.markDirty();
		final BlockState state = world.getBlockState(pos);
		world.updateListeners(pos, state, state, net.minecraft.block.Block.NOTIFY_ALL);
	}
}
