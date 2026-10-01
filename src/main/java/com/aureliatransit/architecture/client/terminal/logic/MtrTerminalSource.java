package com.aureliatransit.architecture.client.terminal.logic;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.live.cache.TimedLruCache;
import com.aureliatransit.architecture.registry.WayfindingBlocks;
import com.aureliatransit.architecture.terminal.NearbyBlocks;
import com.aureliatransit.architecture.terminal.AccessibilityNote;
import com.aureliatransit.architecture.terminal.StationInfo;
import com.aureliatransit.architecture.terminal.StationInfoBuilder;
import com.aureliatransit.architecture.terminal.SystemMap;
import com.aureliatransit.architecture.terminal.SystemMapBuilder;
import com.aureliatransit.architecture.terminal.TerminalSource;
import com.aureliatransit.architecture.transit.StationAssociation;
import com.aureliatransit.architecture.transit.StationData;
import com.aureliatransit.architecture.transit.StationNames;
import com.aureliatransit.architecture.transit.StationSnapshot;
import com.aureliatransit.architecture.wayfinding.BadgeShape;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import com.aureliatransit.architecture.wayfinding.LineBadges;
import com.aureliatransit.architecture.wayfinding.Pictogram;
import com.aureliatransit.architecture.wayfinding.StationFacts;
import com.aureliatransit.architecture.wayfinding.Wayfinding;
import com.aureliatransit.architecture.wayfinding.WayfindingData;
import com.aureliatransit.architecture.wayfinding.WayfindingEditable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.mtr.core.data.Route;
import org.mtr.core.data.SimplifiedRoute;
import org.mtr.core.data.SimplifiedRoutePlatform;
import org.mtr.mod.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The MTR-backed {@link TerminalSource}. Client-side, only called while a terminal screen is open, all caches bounded.
 *
 * <ul>
 *     <li><b>System map</b>: from MTR's synced {@code simplifiedRoutes} (platforms give station ids and names). Cached per
 *     current-station id for 10 s; a rebuild that produces an equal map returns the previous object.</li>
 *     <li><b>Station info</b>: wayfinding facts, the station snapshot's platforms and the terminal's own metadata,
 *     cached per (position, data) for 2 s.</li>
 *     <li><b>Accessibility notes</b>: from ATA metadata only. A scan of the <em>loaded</em> chunks within
 *     {@value #SCAN_RADIUS} blocks for block entities implementing {@link WayfindingEditable} whose pictogram is
 *     accessible route, lift, escalator, stairs or help point; text = the sign's destination, else street label, else the
 *     pictogram name. Standalone help point blocks have no block entity: they are found through the chunk sections of the
 *     same loaded chunks ({@link #helpPoints}; sections without one are skipped via the palette) and the nearest
 *     {@value #MAX_HELP_POINTS} are listed with their distance. Cached per position for 10 s; never run per tick.</li>
 * </ul>
 */
public final class MtrTerminalSource implements TerminalSource {

	static final long MAP_REFRESH_MILLIS = 10_000;
	static final long INFO_REFRESH_MILLIS = 2_000;
	static final long SCAN_REFRESH_MILLIS = 10_000;
	static final long EVICT_MILLIS = 60_000;
	static final int SCAN_RADIUS = 24;
	static final int MAX_NOTES = 12;
	static final int MAX_HELP_POINTS = 4;

	private record InfoKey(long pos, WayfindingData data) {
	}

	private final TimedLruCache<Long, SystemMap> maps;
	private final TimedLruCache<InfoKey, StationInfo> infos;
	private final TimedLruCache<Long, List<AccessibilityNote>> scans;

	public MtrTerminalSource() {
		this(System::currentTimeMillis);
	}

	MtrTerminalSource(LongSupplier clock) {
		this.maps = new TimedLruCache<>(8, MAP_REFRESH_MILLIS, EVICT_MILLIS, clock);
		this.infos = new TimedLruCache<>(32, INFO_REFRESH_MILLIS, EVICT_MILLIS, clock);
		this.scans = new TimedLruCache<>(32, SCAN_REFRESH_MILLIS, EVICT_MILLIS, clock);
	}

	@Override
	public SystemMap systemMap(long currentStationId) {
		return maps.get(currentStationId, (id, previous) -> {
			final SystemMap built = buildMap(id);
			return built.equals(previous) ? previous : built;
		});
	}

	private static SystemMap buildMap(long currentStationId) {
		try {
			final MinecraftClientData data = MinecraftClientData.getInstance();
			final List<SystemMapBuilder.RawRoute> raw = new ArrayList<>();
			for (final SimplifiedRoute route : data.simplifiedRoutes) {
				final Route full = data.routeIdMap.get(route.getId());
				if (full != null && full.getHidden()) {
					continue;
				}
				final String label = LineBadges.label(full == null ? "" : full.getRouteNumber(), route.getName());
				final List<SystemMapBuilder.RawStop> stops = new ArrayList<>();
				for (final SimplifiedRoutePlatform platform : route.getPlatforms()) {
					stops.add(new SystemMapBuilder.RawStop(platform.getStationId(), StationNames.display(platform.getStationName())));
				}
				raw.add(new SystemMapBuilder.RawRoute(route.getId(), new LineBadge(label, route.getColor(), BadgeShape.ROUNDED), StationNames.display(route.getName()), stops));
			}
			return SystemMapBuilder.build(raw, currentStationId);
		} catch (RuntimeException unavailable) {
			return SystemMap.EMPTY;
		}
	}

	@Override
	public StationInfo stationInfo(BlockPos pos, WayfindingData data) {
		return infos.get(new InfoKey(pos.asLong(), data), (key, previous) -> {
			final StationFacts facts = Wayfinding.source().facts(pos, data.autoStation(), data.association());
			final StationSnapshot snapshot = data.autoStation() ? StationData.provider().resolve(pos, data.association(), false) : StationSnapshot.EMPTY;
			final StationInfo info = StationInfoBuilder.build(facts, snapshot.platforms(), data, accessibility(pos));
			return info.equals(previous) ? previous : info;
		});
	}

	private List<AccessibilityNote> accessibility(BlockPos pos) {
		return scans.get(pos.asLong(), (key, previous) -> scan(pos));
	}

	private static List<AccessibilityNote> scan(BlockPos centre) {
		final ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return List.of();
		}
		final Set<AccessibilityNote> found = new HashSet<>();
		final int minX = (centre.getX() - SCAN_RADIUS) >> 4;
		final int maxX = (centre.getX() + SCAN_RADIUS) >> 4;
		final int minZ = (centre.getZ() - SCAN_RADIUS) >> 4;
		final int maxZ = (centre.getZ() + SCAN_RADIUS) >> 4;
		final long limit = (long) SCAN_RADIUS * SCAN_RADIUS;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				if (!(world.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof WorldChunk chunk)) {
					continue;
				}
				for (final BlockEntity entity : chunk.getBlockEntities().values()) {
					if (entity instanceof WayfindingEditable editable && entity.getPos().getSquaredDistance(centre) <= limit) {
						final AccessibilityNote note = note(editable.getWayfinding());
						if (note != null) {
							found.add(note);
						}
					}
				}
			}
		}
		final List<AccessibilityNote> notes = new ArrayList<>(found);
		notes.addAll(helpPoints(world, centre, minX, maxX, minZ, maxZ));
		notes.sort((a, b) -> a.pictogram() != b.pictogram() ? Integer.compare(a.pictogram().ordinal(), b.pictogram().ordinal())
				: a.pictogram() == Pictogram.HELP_POINT ? 0 : a.text().compareTo(b.text())); // help points keep their nearest-first order
		return List.copyOf(notes.size() > MAX_NOTES ? notes.subList(0, MAX_NOTES) : notes);
	}

	/**
	 * Standalone help points have no block entity, so they are found through the chunk sections of the loaded chunks
	 * around the terminal. A section whose palette holds no help point is rejected without visiting a block, so the
	 * work is bounded by the radius and runs only when the terminal's station info is requested (then cached).
	 */
	private static List<AccessibilityNote> helpPoints(ClientWorld world, BlockPos centre, int minX, int maxX, int minZ, int maxZ) {
		final List<BlockPos> candidates = new ArrayList<>();
		final int minSection = world.getSectionIndex(Math.max(world.getBottomY(), centre.getY() - SCAN_RADIUS));
		final int maxSection = world.getSectionIndex(Math.min(world.getTopY() - 1, centre.getY() + SCAN_RADIUS));
		final BlockPos.Mutable cursor = new BlockPos.Mutable();
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				if (!(world.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof WorldChunk chunk)) {
					continue;
				}
				final ChunkSection[] sections = chunk.getSectionArray();
				for (int index = Math.max(0, minSection); index <= Math.min(sections.length - 1, maxSection); index++) {
					final ChunkSection section = sections[index];
					if (section == null || section.isEmpty() || !section.hasAny(state -> state.isOf(WayfindingBlocks.HELP_POINT))) {
						continue;
					}
					final int baseY = world.sectionIndexToCoord(index) << 4;
					for (int x = 0; x < 16; x++) {
						for (int y = 0; y < 16; y++) {
							for (int z = 0; z < 16; z++) {
								if (section.getBlockState(x, y, z).isOf(WayfindingBlocks.HELP_POINT)) {
									candidates.add(cursor.set((cx << 4) + x, baseY + y, (cz << 4) + z).toImmutable());
								}
							}
						}
					}
				}
			}
		}
		final List<AccessibilityNote> notes = new ArrayList<>();
		for (final BlockPos pos : NearbyBlocks.nearest(candidates, centre, SCAN_RADIUS, MAX_HELP_POINTS)) {
			notes.add(new AccessibilityNote(Pictogram.HELP_POINT, Text.translatable("note." + AureliaTransitArchitecture.MOD_ID + ".help_point_distance", NearbyBlocks.distance(centre, pos)).getString()));
		}
		return notes;
	}

	private static AccessibilityNote note(WayfindingData data) {
		final Pictogram pictogram = data.pictogram();
		switch (pictogram) {
			case ACCESSIBLE_ROUTE, ELEVATOR, ESCALATOR, HELP_POINT, STAIRS -> {
				final String text = !data.destination().isEmpty() ? data.destination() : !data.streetLabel().isEmpty() ? data.streetLabel() : name(pictogram);
				return new AccessibilityNote(pictogram, text);
			}
			default -> {
				return null;
			}
		}
	}

	static String name(Pictogram pictogram) {
		final String lower = pictogram.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
