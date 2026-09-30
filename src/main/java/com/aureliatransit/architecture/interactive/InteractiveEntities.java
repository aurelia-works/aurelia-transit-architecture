package com.aureliatransit.architecture.interactive;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Entity types of the interactive systems (common code, no client classes).
 */
public final class InteractiveEntities {

	public static final EntityType<SeatEntity> SEAT = Registry.register(
			Registries.ENTITY_TYPE,
			AureliaTransitArchitecture.id("seat"),
			EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC)
					.setDimensions(0.05F, 0.05F)
					.disableSaving()
					.disableSummon()
					.makeFireImmune()
					.maxTrackingRange(6)
					.trackingTickInterval(20)
					.build("seat")
	);

	private InteractiveEntities() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
