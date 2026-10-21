package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.DropRodBlock;
import com.aureliatransit.architecture.block.ShapedBlock;
import com.aureliatransit.architecture.block.TicketVendingMachineBlock;
import com.aureliatransit.architecture.block.elevated.GirderBlock;
import net.minecraft.block.Block;

import static com.aureliatransit.architecture.util.Shapes.box;

/**
 * Station fittings from Jah's hub play-test (2026-10-20): a lattice roof truss for glass halls in place of the plain girder, a
 * thin drop rod that screens hang from, ATA's own ticket vending machine and the pendant lamps hung in a glass hall. All static models; nothing ticks.
 * Registered via ModBlocks.register.
 */
public final class StationFittingBlocks {

	private static final String TIP = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".";

	/** The girder's block, states and joins, with a lattice model: it joins girders and trusses alike. */
	public static final Block ROOF_TRUSS = ModBlocks.register("roof_truss", BlockFamily.ARCHITECTURE, RenderKind.CUTOUT,
			new GirderBlock(ModBlocks.metal().nonOpaque()), TIP + "style_cycle", TIP + "girder_joins");
	public static final Block DROP_ROD = ModBlocks.register("drop_rod", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new DropRodBlock(ModBlocks.metal()), TIP + "drop_rod");
	public static final Block TICKET_VENDING_MACHINE = ModBlocks.register("ticket_vending_machine", BlockFamily.STATION_EQUIPMENT,
			RenderKind.CUTOUT, new TicketVendingMachineBlock(ModBlocks.metal().luminance(state -> 7), box(1, 0, 2, 15, 16, 16),
					box(1, 0, 2.5, 15, 14.5, 16)), TIP + "faces_you", TIP + "ticket_vending_machine");
	/** An enamel lamp shade on a drop rod, lit underneath: a glass hall's lights at night. Static light, no ticking. */
	public static final Block HALL_PENDANT_LAMP = ModBlocks.register("hall_pendant_lamp", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal().nonOpaque().luminance(state -> 15), box(3, 7, 3, 13, 16, 13)), TIP + "hall_pendant_lamp");

	private StationFittingBlocks() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
