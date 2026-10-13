package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Consumer;

/**
 * Generated stations register themselves as an MTR station, so the dashboard, departure screens and announcements know the
 * station without anyone drawing its area: {@link MtrStations} does what the dashboard does, and undo deletes the station
 * again. Where an MTR station already covers the ground it is kept as it is.
 *
 * <p>Those steps use MTR internals ({@code org.mtr.core.data.Station}, {@code Init.sendMessageC2S}, MTR's update and delete
 * packets). If a future MTR changes them, the station is still built and a player draws the area on the dashboard as before;
 * {@link MtrStations} is loaded only after this check passes.
 */
public final class MtrStationContract {

	private static final boolean AVAILABLE = detect();

	private MtrStationContract() {
	}

	public static boolean available() {
		return AVAILABLE;
	}

	/** Registers the zone (asynchronously); the new station's id goes into the undo log. */
	public static void register(ServerWorld world, StationZone zone, UndoLog log, Consumer<Text> out) {
		if (!AVAILABLE || zone == null) {
			return;
		}
		try {
			MtrStations.create(world, zone, result -> {
				if (result.id() != 0) {
					log.addStation(result.id());
					out.accept(Text.literal("MTR station \"" + zone.name() + "\" added to the dashboard.").formatted(Formatting.GREEN));
				} else {
					out.accept(Text.literal("Inside MTR station \"" + result.existing() + "\" already; its area is unchanged.").formatted(Formatting.GRAY));
				}
			});
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not add an MTR station for a generated station; draw it on the dashboard", e);
		}
	}

	/** Asks MTR for the names of the stations within {@code radius} of a point (empty without MTR); answers later. */
	public static void namesNear(ServerWorld world, net.minecraft.util.math.BlockPos pos, int radius, Consumer<List<String>> done) {
		if (!AVAILABLE) {
			done.accept(List.of());
			return;
		}
		MtrStations.namesNear(world, pos.getX(), pos.getY(), pos.getZ(), radius, done);
	}

	/** Deletes the MTR stations a build added. */
	public static void remove(ServerWorld world, List<Long> ids) {
		if (!AVAILABLE || ids.isEmpty()) {
			return;
		}
		try {
			MtrStations.remove(world, ids);
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not delete the MTR station of an undone station", e);
		}
	}

	private static boolean detect() {
		try {
			final ClassLoader loader = MtrStationContract.class.getClassLoader();
			final Class<?> station = Class.forName("org.mtr.core.data.Station", false, loader);
			final Class<?> init = Class.forName("org.mtr.mod.Init", false, loader);
			final Class<?> update = Class.forName("org.mtr.core.operation.UpdateDataRequest", false, loader);
			final Class<?> delete = Class.forName("org.mtr.core.operation.DeleteDataRequest", false, loader);
			final Class<?> packet = Class.forName("org.mtr.mod.packet.PacketUpdateData", false, loader);
			Class.forName("org.mtr.core.operation.NearbyAreasRequest", false, loader);
			Class.forName("org.mtr.mod.packet.PacketDeleteData", false, loader);
			if (has(station, "setCorners", 2) && has(station, "setName", 1) && has(station, "setColor", 1) && has(init, "sendMessageC2S", 6)
					&& has(update, "addStation", 1) && has(delete, "addStationId", 1) && has(packet, "sendDirectlyToClientDepotUpdate", 2)) {
				return true;
			}
			AureliaTransitArchitecture.LOGGER.warn("MTR's station API has changed; draw generated stations on the dashboard");
			return false;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	private static boolean has(Class<?> type, String name, int parameters) {
		for (final java.lang.reflect.Method method : type.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == parameters) {
				return true;
			}
		}
		return false;
	}
}
