package com.aureliatransit.architecture.client.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.client.terminal.logic.MtrTerminalSource;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientServiceMessages;
import com.aureliatransit.architecture.client.wayfinding.logic.ClientStationSuffixes;
import com.aureliatransit.architecture.terminal.Terminals;
import com.aureliatransit.architecture.client.wayfinding.logic.MtrWayfindingSource;
import com.aureliatransit.architecture.wayfinding.ServiceMessageSync;
import com.aureliatransit.architecture.wayfinding.ServiceMessages;
import com.aureliatransit.architecture.wayfinding.StationSuffixSync;
import com.aureliatransit.architecture.wayfinding.StationSuffixes;
import com.aureliatransit.architecture.wayfinding.Wayfinding;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client init of the wayfinding logic and live information: installs the MTR-backed
 * {@link com.aureliatransit.architecture.wayfinding.WayfindingSource} (only when MTR is loaded) and receives the
 * service-message sync.
 */
public final class WayfindingLogicClient {

	private WayfindingLogicClient() {
	}

	public static void init() {
		if (FabricLoader.getInstance().isModLoaded("mtr")) {
			Wayfinding.install(new MtrWayfindingSource());
			Wayfinding.installSuffixes(ClientStationSuffixes::apply);
			Terminals.install(new MtrTerminalSource());
		}
		ClientPlayNetworking.registerGlobalReceiver(ServiceMessageSync.SYNC, (client, handler, buf, responseSender) -> {
			final ServiceMessages messages;
			try {
				messages = ServiceMessages.read(buf);
			} catch (RuntimeException malformed) {
				AureliaTransitArchitecture.LOGGER.warn("Ignoring malformed service message sync: {}", malformed.getMessage());
				return;
			}
			client.execute(() -> ClientServiceMessages.set(messages));
		});
		ClientPlayNetworking.registerGlobalReceiver(StationSuffixSync.SYNC, (client, handler, buf, responseSender) -> {
			final StationSuffixes suffixes;
			try {
				suffixes = StationSuffixes.read(buf);
			} catch (RuntimeException malformed) {
				AureliaTransitArchitecture.LOGGER.warn("Ignoring malformed station suffix sync: {}", malformed.getMessage());
				return;
			}
			client.execute(() -> ClientStationSuffixes.set(suffixes));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ClientServiceMessages.clear();
			ClientStationSuffixes.clear();
		}));
	}
}
