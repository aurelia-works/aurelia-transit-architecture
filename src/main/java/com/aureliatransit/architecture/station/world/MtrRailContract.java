package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.station.PlatformRail;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Consumer;

/**
 * Generated stations lay their own MTR platform rails and number the platforms, so a player only has to add routes: no
 * Platform Rail Connector and no typing "1", "2" on the dashboard (Enviro, 10-20). {@link MtrRails} does what the connector
 * does; undo takes the rails away again with the nodes.
 *
 * <p>Those steps use MTR internals ({@code org.mtr.core.data.Rail}, {@code BlockNode.getAngle}, MTR's update and delete
 * packets). If a future MTR changes them, the station is still built with its rail nodes and the player connects them as
 * before; {@link MtrRails} is loaded only after this check passes.
 */
public final class MtrRailContract {

	private static final boolean AVAILABLE = detect();
	private static final boolean TURN_BACK = AVAILABLE && detectTurnBack();

	private MtrRailContract() {
	}

	public static boolean available() {
		return AVAILABLE;
	}

	/** Lays and numbers the platform rails whose nodes stand unconnected; tells the player what it did. */
	public static void lay(ServerWorld world, List<PlatformRail> rails, UndoLog log, Consumer<Text> out) {
		if (!AVAILABLE || rails.isEmpty()) {
			return;
		}
		try {
			final int laid = MtrRails.lay(world, rails, log, named -> {
				if (named == 0) {
					AureliaTransitArchitecture.LOGGER.warn("MTR formed no platform on a generated station's platform rail; number it on the dashboard");
				} else {
					AureliaTransitArchitecture.LOGGER.debug("MTR platform numbered for a generated station");
				}
			});
			if (laid > 0) {
				out.accept(Text.literal((laid == 1 ? "1 MTR platform" : laid + " MTR platforms") + " connected and numbered "
						+ rails.get(0).name() + (laid > 1 ? "–" + lastPlatform(rails) : "") + "; add routes on the dashboard.")
						.formatted(Formatting.GREEN));
			}
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not lay MTR platform rails for a station; connect the rail nodes by hand", e);
		}
	}

	private static String lastPlatform(List<PlatformRail> rails) {
		String name = "";
		for (final PlatformRail rail : rails) {
			if (!rail.turnBack()) {
				name = rail.name();
			}
		}
		return name;
	}

	/** True when this MTR can lay a turn-back rail (an overrun past a platform to the buffer stop). */
	static boolean turnBackRails() {
		return TURN_BACK;
	}

	/** Removes the rails at the nodes a build connected, and marks those nodes unconnected. */
	public static void remove(ServerWorld world, List<BlockPos> nodes) {
		if (!AVAILABLE || nodes.isEmpty()) {
			return;
		}
		try {
			MtrRails.remove(world, nodes);
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not remove the MTR platform rails of an undone station", e);
		}
	}

	private static boolean detect() {
		try {
			final ClassLoader loader = MtrRailContract.class.getClassLoader();
			final Class<?> rail = Class.forName("org.mtr.core.data.Rail", false, loader);
			final Class<?> node = Class.forName("org.mtr.mod.block.BlockNode", false, loader);
			final Class<?> update = Class.forName("org.mtr.core.operation.UpdateDataRequest", false, loader);
			final Class<?> delete = Class.forName("org.mtr.mod.packet.PacketDeleteData", false, loader);
			Class.forName("org.mtr.core.data.Platform", false, loader);
			Class.forName("org.mtr.core.generated.operation.UpdateDataResponseSchema", false, loader).getDeclaredField("platforms");
			if (has(rail, "newPlatformRail", 8) && has(rail, "getAngles", 4) && has(node, "getAngle", 1) && has(update, "addRail", 1)
					&& has(update, "addPlatform", 1) && has(delete, "sendDirectlyToServerRailNodePosition", 2)) {
				return true;
			}
			AureliaTransitArchitecture.LOGGER.warn("MTR's rail API has changed; connect generated stations' rail nodes by hand");
			return false;
		} catch (ClassNotFoundException | NoSuchFieldException | LinkageError e) {
			return false;
		}
	}

	private static boolean detectTurnBack() {
		try {
			return has(Class.forName("org.mtr.core.data.Rail", false, MtrRailContract.class.getClassLoader()), "newTurnBackRail", 8);
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
