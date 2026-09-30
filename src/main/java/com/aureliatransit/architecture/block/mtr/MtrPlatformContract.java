package com.aureliatransit.architecture.block.mtr;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.util.shape.VoxelShape;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Lets ATA's walkable platform surfaces count as platforms for MTR train doors.
 *
 * <p>MTR 4 opens a vehicle doorway only if a block in a small box around it (one block sideways, two up or down) is an
 * instance of its platform marker {@code org.mtr.mod.block.PlatformHelper}, or is an unlocked PSD/APG door
 * ({@code RenderVehicleHelper.canOpenDoors}). The check looks at the block's type only, never at its shape. ATA platform
 * pieces used to be plain blocks, so a platform edge built from them kept doors shut.
 *
 * <p>{@code PlatformHelper} is a marker interface without abstract methods, and MTR only ever tests it with
 * {@code instanceof}: MTR 4.0.3 and 4.0.5 read no properties from another block's state through it. Implementing it
 * needs no mixin and changes nothing in MTR. If a future MTR removes or changes the interface, ATA falls back to plain
 * blocks instead of failing to load; the classes that name the interface are loaded only after this check passes.
 */
public final class MtrPlatformContract {

	private static final String PLATFORM_HELPER = "org.mtr.mod.block.PlatformHelper";
	private static final boolean AVAILABLE = detect();

	private MtrPlatformContract() {
	}

	public static boolean available() {
		return AVAILABLE;
	}

	/**
	 * A full platform surface block (paving, tactile paving).
	 */
	public static Block surface(AbstractBlock.Settings settings) {
		return AVAILABLE ? MtrPlatformBlocks.surface(settings) : new Block(settings);
	}

	/**
	 * A platform edge block with a facing and a shaped profile.
	 */
	public static Block edge(AbstractBlock.Settings settings, Placement placement, VoxelShape northShape) {
		return AVAILABLE ? MtrPlatformBlocks.edge(settings, placement, northShape) : new FacingShapedBlock(settings, placement, northShape);
	}

	private static boolean detect() {
		try {
			final Class<?> marker = Class.forName(PLATFORM_HELPER, false, MtrPlatformContract.class.getClassLoader());
			if (!marker.isInterface()) {
				AureliaTransitArchitecture.LOGGER.warn("{} is not an interface; ATA platform blocks will not open MTR train doors", PLATFORM_HELPER);
				return false;
			}
			for (final Method method : marker.getMethods()) {
				if (Modifier.isAbstract(method.getModifiers())) {
					AureliaTransitArchitecture.LOGGER.warn("{} now declares {}; ATA platform blocks will not open MTR train doors", PLATFORM_HELPER, method.getName());
					return false;
				}
			}
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("MTR platform marker {} not found; ATA platform blocks will not open MTR train doors", PLATFORM_HELPER);
			return false;
		}
	}
}
