package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.client.VehicleRidingMovement;
import org.mtr.mod.data.VehicleExtension;

import java.lang.reflect.Method;

/**
 * Loose coupling with Aurelia Transit Utilities (ATU, suggested, never required). ATU announces on the train the player
 * rides; ATA announces on the platforms. While the player rides an MTR train with ATU's Announcer on, the station speakers
 * stay quiet so nothing is said twice. Without ATU (or with its Announcer off) the speakers play as always.
 *
 * <p>No compile-time dependency: ATU's {@code Announcer.enabled()} is looked up by name once; if it is not there the
 * speakers never go quiet.
 */
final class AtuCompat {

	static final String ATU_ID = "aurelia_transit_utilities";
	private static final String ANNOUNCER = "com.aureliatransit.utilities.client.Announcer";
	private static Method enabled;
	private static boolean looked;

	private AtuCompat() {
	}

	/** True while ATU's on-train Announcer is speaking for the player's ride. */
	static boolean onTrainAnnouncerActive() {
		return announcerOn() && riding();
	}

	private static boolean announcerOn() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded(ATU_ID)) {
				try {
					enabled = Class.forName(ANNOUNCER).getMethod("enabled");
				} catch (ReflectiveOperationException | LinkageError e) {
					AureliaTransitArchitecture.LOGGER.info("Aurelia Transit Utilities is loaded but its Announcer was not found; station speakers play while riding");
				}
			}
		}
		if (enabled == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(enabled.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException e) {
			enabled = null;
			return false;
		}
	}

	private static boolean riding() {
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (VehicleRidingMovement.isRiding(vehicle.getId())) {
				return true;
			}
		}
		return false;
	}
}
