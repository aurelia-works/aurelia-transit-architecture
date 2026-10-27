package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.PlatformRail;
import com.aureliatransit.architecture.station.StylePalette;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.operation.UpdateDataResponse;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketDeleteData;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The calls into MTR's rail data. Only reached through {@link MtrRailContract} once it has confirmed they exist. It does what
 * MTR's Platform Rail Connector does between two nodes (the same angles, shape and transport mode, and the nodes marked
 * connected), sent as {@code update_data}; MTR answers with the platform it formed on the rail, which is then named with
 * its number like the dashboard does, and the players' maps are told.
 */
final class MtrRails {

	/** MTR's own default rail style, the one its Rail Connector gives a rail before the player picks another. */
	static final String DEFAULT_STYLE = "default";

	private MtrRails() {
	}

	/**
	 * @param done gets how many platforms were laid; called on the server thread, once per rail laid
	 * @return the platform rails laid now (nodes not both there, or already connected, are left to the player); turn-back
	 *         rails are laid too but not counted
	 */
	static int lay(ServerWorld world, List<PlatformRail> rails, UndoLog log, IntConsumer done) {
		final org.mtr.mapping.holder.MinecraftServer server = new org.mtr.mapping.holder.MinecraftServer(world.getServer());
		final org.mtr.mapping.holder.World mtrWorld = new org.mtr.mapping.holder.World(world);
		final org.mtr.mapping.holder.ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld(world);
		int laid = 0;
		for (final PlatformRail planned : rails) {
			final BlockPos a = new BlockPos(planned.x1(), planned.y1(), planned.z1());
			final BlockPos b = new BlockPos(planned.x2(), planned.y2(), planned.z2());
			final BlockState stateA = world.getBlockState(a);
			final BlockState stateB = world.getBlockState(b);
			// a turn-back rail starts at the node its platform rail ends at, which that rail has just connected
			if (!(planned.turnBack() ? isNode(stateA) : freeNode(stateA)) || !freeNode(stateB)
					|| planned.turnBack() && !MtrRailContract.turnBackRails()) {
				continue;
			}
			final Position from = new Position(a.getX(), a.getY(), a.getZ());
			final Position to = new Position(b.getX(), b.getY(), b.getZ());
			final ObjectObjectImmutablePair<Angle, Angle> angles = Rail.getAngles(from, BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(stateA)),
					to, BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(stateB)));
			// MTR draws a rail only through its styles; its own connector gives a new rail "default" (PacketUpdateLastRailStyles),
			// and a rail with none is laid and connected but invisible
			final Rail rail = planned.turnBack()
					? Rail.newTurnBackRail(from, angles.left(), to, angles.right(), Rail.Shape.QUADRATIC, 0, ObjectArrayList.of(DEFAULT_STYLE), TransportMode.TRAIN)
					: Rail.newPlatformRail(from, angles.left(), to, angles.right(), Rail.Shape.QUADRATIC, 0, ObjectArrayList.of(DEFAULT_STYLE), TransportMode.TRAIN);
			if (!rail.isValid()) {
				continue;
			}
			world.setBlockState(a, connected(stateA, true));
			world.setBlockState(b, connected(stateB, true));
			log.addRail(a, b);
			if (planned.turnBack()) {
				// no platform forms on a turn-back rail, so there is nothing to name
				Init.sendMessageC2S("update_data", server, mtrWorld, new UpdateDataRequest(new MinecraftClientData()).addRail(rail),
						(UpdateDataResponse response) -> PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, response), UpdateDataResponse.class);
				continue;
			}
			laid++;
			Init.sendMessageC2S("update_data", server, mtrWorld, new UpdateDataRequest(new MinecraftClientData()).addRail(rail), (UpdateDataResponse response) -> {
				PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, response);
				final UpdateDataRequest names = new UpdateDataRequest(new MinecraftClientData());
				int named = 0;
				for (final Platform platform : platforms(response)) {
					if (platform.containsPos(from) && platform.containsPos(to)) {
						platform.setName(planned.name());
						names.addPlatform(platform);
						named++;
					}
				}
				if (named > 0) {
					Init.sendMessageC2S("update_data", server, mtrWorld, names,
							(UpdateDataResponse renamed) -> PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, renamed), UpdateDataResponse.class);
				}
				done.accept(named);
			}, UpdateDataResponse.class);
		}
		return laid;
	}

	/** The platforms MTR formed or changed ({@code getPlatforms()} is package private; its schema field is protected). */
	@SuppressWarnings("unchecked")
	static List<Platform> platforms(UpdateDataResponse response) {
		try {
			final java.lang.reflect.Field field = org.mtr.core.generated.operation.UpdateDataResponseSchema.class.getDeclaredField("platforms");
			field.setAccessible(true);
			return (List<Platform>) field.get(response);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	/**
	 * Takes away the rails at the nodes a build laid its platforms between, as breaking the nodes would (MTR removes every
	 * rail at a node, so a line laid on from one since is cut there), and marks the nodes unconnected so undo can clear them.
	 */
	static void remove(ServerWorld world, List<BlockPos> nodes) {
		final org.mtr.mapping.holder.ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld(world);
		for (final BlockPos node : nodes) {
			PacketDeleteData.sendDirectlyToServerRailNodePosition(serverWorld, new Position(node.getX(), node.getY(), node.getZ()));
			final BlockState state = world.getBlockState(node);
			if (isNode(state)) {
				world.setBlockState(node, connected(state, false));
			}
		}
	}

	/** MTR's rail node, the block the plan's rail-node cells hold. */
	private static boolean isNode(BlockState state) {
		return net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString().equals(StylePalette.MTR_RAIL_NODE);
	}

	private static boolean freeNode(BlockState state) {
		return isNode(state) && !connectedNow(state);
	}

	private static boolean connectedNow(BlockState state) {
		final net.minecraft.state.property.Property<?> property = state.getBlock().getStateManager().getProperty("is_connected");
		return property != null && Boolean.TRUE.equals(state.get(property));
	}

	private static BlockState connected(BlockState state, boolean value) {
		final net.minecraft.state.property.Property<?> property = state.getBlock().getStateManager().getProperty("is_connected");
		return property instanceof net.minecraft.state.property.BooleanProperty bool ? state.with(bool, value) : state;
	}
}
