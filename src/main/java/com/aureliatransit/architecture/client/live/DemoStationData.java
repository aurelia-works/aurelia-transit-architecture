package com.aureliatransit.architecture.client.live;

import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationDataProvider;
import com.aureliatransit.architecture.transit.StationReference;
import com.aureliatransit.architecture.transit.StationSnapshot;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev runs only (the {@code ata.demoBoards} system property, {@code ./gradlew runClient -Pata.demoBoards=true}): every
 * live display shows one made-up station with a believable timetable that moves with the clock, so the boards can be
 * looked at without building an MTR network. Never installed in a normal game.
 */
final class DemoStationData implements StationDataProvider {

	private static final StationReference STATION = new StationReference(1, "Kingsbridge", 0x2050A0);
	private static final List<PlatformReference> PLATFORMS = List.of(new PlatformReference(11, "3", 1), new PlatformReference(12, "5b", 1),
			new PlatformReference(13, "7", 1));

	private record Train(String name, String number, int color, String destination, String platform, long platformId, int minutes, int late,
						 List<String> stops) {
	}

	/** Departures repeat every half hour; minutes are past the half hour. */
	private static final List<Train> TRAINS = List.of(
			new Train("Intercity", "IC 3045", 0xFFC917, "Amsterdam Centraal", "5b", 12, 2, 4,
					List.of("Utrecht Centraal", "Amsterdam Amstel", "Amsterdam Centraal")),
			new Train("Sprinter", "SPR 7442", 0x0063D3, "Den Haag Centraal", "5b", 12, 9, 0,
					List.of("Gouda", "Zoetermeer", "Den Haag Centraal")),
			new Train("ICE", "ICE 579", 0xEC0016, "München Hbf", "7", 13, 13, 6,
					List.of("Frankfurt (Main) Hbf", "Würzburg Hbf", "Nürnberg Hbf", "Ingolstadt Hbf", "München Hbf")),
			new Train("Regional Express", "RE 4", 0x1455C0, "Aachen Hbf", "3", 11, 17, 0,
					List.of("Düsseldorf Hbf", "Mönchengladbach Hbf", "Aachen Hbf")),
			new Train("West Coast", "WCML", 0x00A0E2, "London Euston", "3", 11, 21, 0,
					List.of("Watford Junction", "Milton Keynes Central", "Rugby", "Coventry", "Birmingham International", "London Euston")),
			new Train("Central", "1", 0xE32017, "Epping", "7", 13, 25, 2,
					List.of("Stratford", "Leytonstone", "Woodford", "Epping")));

	@Override
	public StationSnapshot resolve(BlockPos pos, StationAssociation association, boolean withServices) {
		return resolve(pos, association, withServices, false);
	}

	@Override
	public StationSnapshot resolve(BlockPos pos, StationAssociation association, boolean withServices, boolean withCallingTimes) {
		if (!withServices) {
			return new StationSnapshot(STATION, PLATFORMS, List.of(), 0);
		}
		final long now = System.currentTimeMillis();
		final long half = 30 * 60_000L;
		final long base = now - now % half;
		final List<ServiceSnapshot> out = new ArrayList<>();
		for (int round = 0; round < 2; round++) {
			for (final Train t : TRAINS) {
				final long departure = base + round * half + t.minutes * 60_000L + t.late * 60_000L;
				if (departure < now) {
					continue;
				}
				final List<Long> times = new ArrayList<>();
				for (int i = 0; i < t.stops.size(); i++) {
					times.add(departure + (i + 1) * 7 * 60_000L);
				}
				out.add(new ServiceSnapshot(t.number.hashCode(), t.name, t.number, t.color, t.destination, t.platformId, t.platform, departure - 60_000,
						departure, t.late * 60_000L, true, false, t.stops, round, withCallingTimes ? times : List.of(), "Penzance"));
			}
		}
		out.sort((a, b) -> Long.compare(a.departureMillis(), b.departureMillis()));
		return new StationSnapshot(STATION, PLATFORMS, out, now / 1000);
	}

	@Override
	public List<StationReference> listStations(int limit) {
		return List.of(STATION);
	}

	@Override
	public List<PlatformReference> listPlatforms(long stationId) {
		return PLATFORMS;
	}
}
