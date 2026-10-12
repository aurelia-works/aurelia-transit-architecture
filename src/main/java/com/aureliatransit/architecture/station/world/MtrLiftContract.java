package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.station.Heading;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Generated stations get working MTR lifts. MTR forms a lift when a player right-clicks a stop with its Lift Refresher; the
 * build job does the same steps itself ({@link MtrLifts}), and undo deletes the lift before it takes the blocks away (plain
 * block resets skip MTR's break handler, which would leave a lift in MTR's data).
 *
 * <p>Those steps use MTR internals ({@code org.mtr.core.data.Lift}, {@code Init.sendMessageC2S}, {@code PacketDeleteData}).
 * If a future MTR changes them, the shafts, tracks, doors and buttons are still built and a player can form the lift with
 * the Lift Refresher; {@link MtrLifts} is loaded only after this check passes.
 */
public final class MtrLiftContract {

	private static final boolean AVAILABLE = detect();

	private MtrLiftContract() {
	}

	public static boolean available() {
		return AVAILABLE;
	}

	/**
	 * One lift to form: its stops from the bottom up, the way its doors face, and where the car's centre lies relative to the
	 * stops' block corner (MTR draws the car centred on that corner plus this offset).
	 */
	public record Shaft(List<BlockPos> stops, Heading doors, double offsetX, double offsetZ) {

		public Shaft {
			stops = List.copyOf(stops);
		}
	}

	/** Forms each lift and links each stop's call buttons to it; false when MTR's lift API is not there. */
	public static boolean create(ServerWorld world, List<Shaft> shafts, List<BlockPos> buttons) {
		if (!AVAILABLE || shafts.isEmpty()) {
			return false;
		}
		try {
			MtrLifts.create(world, shafts, buttons);
			return true;
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not form MTR lifts for a station; use the Lift Refresher on a stop", e);
			return false;
		}
	}

	/** Deletes the lifts that stop at these cells (one stop per lift is enough). */
	public static void remove(ServerWorld world, List<BlockPos> stops) {
		if (!AVAILABLE || stops.isEmpty()) {
			return;
		}
		try {
			MtrLifts.remove(world, stops);
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not delete MTR lifts of an undone station", e);
		}
	}

	private static boolean detect() {
		try {
			final ClassLoader loader = MtrLiftContract.class.getClassLoader();
			final Class<?> lift = Class.forName("org.mtr.core.data.Lift", false, loader);
			final Class<?> init = Class.forName("org.mtr.mod.Init", false, loader);
			final Class<?> delete = Class.forName("org.mtr.mod.packet.PacketDeleteData", false, loader);
			Class.forName("org.mtr.core.tool.Angle", false, loader);
			final boolean setters = has(lift, "setFloors", 1) && has(lift, "setDimensions", 6) && has(lift, "setAngle", 1)
					&& has(lift, "setIsDoubleSided", 1) && has(lift, "setStyle", 1);
			if (setters && has(init, "sendMessageC2S", 6) && has(delete, "sendDirectlyToServerLiftFloorPosition", 2)) {
				return true;
			}
			AureliaTransitArchitecture.LOGGER.warn("MTR's lift API has changed; generated stations get lift shafts to finish with the Lift Refresher");
			return false;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	private static boolean has(Class<?> type, String name, int parameters) {
		for (final Method method : type.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == parameters) {
				return true;
			}
		}
		return false;
	}
}
