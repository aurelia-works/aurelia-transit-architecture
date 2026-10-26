package com.aureliatransit.architecture.station.world;

import net.minecraft.server.world.ServerWorld;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.operation.DeleteDataRequest;
import org.mtr.core.operation.DeleteDataResponse;
import org.mtr.core.operation.NearbyAreasRequest;
import org.mtr.core.operation.NearbyAreasResponse;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.operation.UpdateDataResponse;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketDeleteData;
import org.mtr.mod.packet.PacketRequestResponseBase;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.List;
import java.util.function.Consumer;

/**
 * The calls into MTR's station data. Only reached through {@link MtrStationContract} once it has confirmed they exist. It does
 * what the dashboard does when a player draws a station: a new {@link Station} with a name, colour and corners (every height,
 * as the dashboard sets them) sent to MTR's simulation as {@code update_data}, and the answer passed on to the players so
 * their maps show it at once. First it asks MTR ({@code nearby_stations}) whether a station already covers the ground, and
 * leaves that one alone.
 */
final class MtrStations {

	private MtrStations() {
	}

	/**
	 * @param done gets the new station's id, or the name of the station already there (and 0) when one covers the zone;
	 *             called later on the server thread
	 */
	static void create(ServerWorld world, StationZone zone, Consumer<Result> done) {
		final org.mtr.mapping.holder.MinecraftServer server = new org.mtr.mapping.holder.MinecraftServer(world.getServer());
		final org.mtr.mapping.holder.World mtrWorld = new org.mtr.mapping.holder.World(world);
		final NearbyAreasRequest<Station, ?> nearby = new NearbyAreasRequest<>(new Position(zone.centerX(), zone.centerY(), zone.centerZ()), zone.reach() + 1);
		Init.sendMessageC2S("nearby_stations", server, mtrWorld, nearby, (NearbyAreasResponse response) -> {
			for (final Station station : response.getStations()) {
				if (zone.overlaps(station.getMinX(), station.getMinZ(), station.getMaxX(), station.getMaxZ())) {
					done.accept(new Result(0, station.getName()));
					return;
				}
			}
			final Station station = new Station(new MinecraftClientData());
			station.setName(zone.name());
			station.setColor(zone.color());
			station.setCorners(new Position(zone.minX(), Long.MIN_VALUE, zone.minZ()), new Position(zone.maxX(), Long.MAX_VALUE, zone.maxZ()));
			final org.mtr.mapping.holder.ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld(world);
			Init.sendMessageC2S("update_data", server, mtrWorld, new UpdateDataRequest(new MinecraftClientData()).addStation(station), (UpdateDataResponse update) -> {
				PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, update);
				done.accept(new Result(station.getId(), ""));
			}, UpdateDataResponse.class);
		}, NearbyAreasResponse.class);
	}

	/** The names of MTR's stations whose area comes within {@code radius} of the point; later, on the server thread. */
	static void namesNear(ServerWorld world, int x, int y, int z, int radius, Consumer<List<String>> done) {
		final NearbyAreasRequest<Station, ?> nearby = new NearbyAreasRequest<>(new Position(x, y, z), radius);
		Init.sendMessageC2S("nearby_stations", new org.mtr.mapping.holder.MinecraftServer(world.getServer()), new org.mtr.mapping.holder.World(world), nearby,
				(NearbyAreasResponse response) -> done.accept(response.getStations().stream().map(Station::getName).toList()), NearbyAreasResponse.class);
	}

	/**
	 * Deletes stations by id the way MTR's own delete packet does, so players' maps drop them too ({@code runServerOutbound}
	 * is protected; MTR's own helpers call it the same way with no player). If that is not reachable, only MTR's data is told.
	 */
	static void remove(ServerWorld world, List<Long> ids) {
		final DeleteDataRequest request = new DeleteDataRequest();
		ids.forEach(request::addStationId);
		delete(world, request);
	}

	/** Sends a delete to MTR: straight on the server when MTR allows it, else as a client message. */
	static void delete(ServerWorld world, DeleteDataRequest request) {
		final org.mtr.mapping.holder.ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld(world);
		try {
			final java.lang.reflect.Method outbound = PacketRequestResponseBase.class.getDeclaredMethod("runServerOutbound",
					org.mtr.mapping.holder.ServerWorld.class, org.mtr.mapping.holder.ServerPlayerEntity.class);
			outbound.setAccessible(true);
			outbound.invoke(new PacketDeleteData(request), serverWorld, null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			Init.sendMessageC2S("delete_data", new org.mtr.mapping.holder.MinecraftServer(world.getServer()), new org.mtr.mapping.holder.World(world), request,
					null, DeleteDataResponse.class);
		}
	}

	/** @param id the station made, or 0 when {@code existing} (a station already there) was kept instead */
	record Result(long id, String existing) {
	}
}
