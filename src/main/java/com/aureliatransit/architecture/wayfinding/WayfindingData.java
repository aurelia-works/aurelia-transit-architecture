package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.TextSanitizer;
import com.aureliatransit.architecture.transit.StationAssociation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared, server-stored wayfinding configuration of one sign/display: the <b>manual</b> half of the model.
 * Everything a wayfinding consumer (entrance pylon, directional sign, platform sign, exit sign, street blade, bus board)
 * can show is here; each consumer shows the subset its {@link WayfindingPanelKind} uses. At render time it is merged
 * client-side with MTR-derived facts into a {@link ResolvedWayfinding} ({@link WayfindingResolver}).
 *
 * <p>Empty text means "not set": an {@code auto*} flag lets MTR fill that field, otherwise it simply is not shown.
 * Every constructor path sanitizes and bounds input, so values from NBT and packets are safe.
 *
 * @param autoStation   take station name (and, with {@code autoLines}, lines and exits) from the MTR station at the block
 * @param stationName   manual station name; overrides MTR's when not empty
 * @param secondaryName station name in a second language, shown per {@code languageLayout}
 * @param stationCode   manual station code/number ("MFL 15", "B12"); MTR has none
 * @param lines         manual line badges; with {@code autoLines} MTR's lines are used when this list is empty
 * @param autoLines     take line badges from the MTR routes serving the station
 * @param destination   manual direction/destination text ("To Frankford", "Northbound")
 * @param serviceType   stopping pattern (manual only)
 * @param serviceLabel  label for {@link ServiceType#CUSTOM}
 * @param platform      platform/track label
 * @param exitLabel     exit identifier ("A", "B", "2"); with {@code autoStation} MTR exit destinations with this name are used
 * @param streetLabel   street, landmark or connection text ("Market St", "City Hall", "To Regional Rail")
 * @param transfers     free-text transfer note, shown by consumers that have room for it
 * @param association   which MTR station the block belongs to when {@code autoStation} is on: AUTO resolves the nearest
 *                      station (the default), MANUAL pins the station (and platforms) the builder picked
 * @param view          what a station information board shows; ignored by every other block
 * @param exitSettings  per-exit settings of the multi-exit (Exits) board view (1.4, A3); at most {@link #MAX_EXIT_SETTINGS}
 */
public record WayfindingData(
		boolean autoStation,
		String stationName,
		String secondaryName,
		String stationCode,
		List<LineBadge> lines,
		boolean autoLines,
		SignArrow arrow,
		String destination,
		ServiceType serviceType,
		String serviceLabel,
		String platform,
		String exitLabel,
		String streetLabel,
		String transfers,
		LanguageLayout languageLayout,
		Pictogram pictogram,
		AccentPalette accent,
		StationAssociation association,
		BoardView view,
		List<ExitSetting> exitSettings
) {

	public static final int MAX_NAME = 32;
	public static final int MAX_CODE = 8;
	public static final int MAX_LINES = 6;
	public static final int MAX_DESTINATION = 32;
	public static final int MAX_SERVICE_LABEL = 12;
	public static final int MAX_PLATFORM = 4;
	public static final int MAX_EXIT_LABEL = 4;
	public static final int MAX_STREET = 32;
	public static final int MAX_TRANSFERS = 48;
	public static final int MAX_EXIT_SETTINGS = 12;
	public static final String NBT_KEY = "Wayfinding";

	public static final WayfindingData EMPTY = new WayfindingData(true, "", "", "", List.of(), true, SignArrow.NONE, "", ServiceType.NONE, "", "", "", "", "",
			LanguageLayout.SINGLE, Pictogram.NONE, AccentPalette.NONE, StationAssociation.AUTO, BoardView.TRAINS_THIS_SIDE);

	public WayfindingData {
		stationName = TextSanitizer.sanitize(stationName, MAX_NAME);
		secondaryName = TextSanitizer.sanitize(secondaryName, MAX_NAME);
		stationCode = TextSanitizer.sanitize(stationCode, MAX_CODE);
		final List<LineBadge> clean = new ArrayList<>();
		if (lines != null) {
			for (final LineBadge badge : lines) {
				if (badge != null && !badge.isEmpty() && clean.size() < MAX_LINES) {
					clean.add(badge);
				}
			}
		}
		lines = List.copyOf(clean);
		arrow = arrow == null ? SignArrow.NONE : arrow;
		destination = TextSanitizer.sanitize(destination, MAX_DESTINATION);
		serviceType = serviceType == null ? ServiceType.NONE : serviceType;
		serviceLabel = TextSanitizer.sanitize(serviceLabel, MAX_SERVICE_LABEL);
		platform = TextSanitizer.sanitize(platform, MAX_PLATFORM);
		exitLabel = TextSanitizer.sanitize(exitLabel, MAX_EXIT_LABEL);
		streetLabel = TextSanitizer.sanitize(streetLabel, MAX_STREET);
		transfers = TextSanitizer.sanitize(transfers, MAX_TRANSFERS);
		languageLayout = languageLayout == null ? LanguageLayout.SINGLE : languageLayout;
		pictogram = pictogram == null ? Pictogram.NONE : pictogram;
		accent = accent == null ? AccentPalette.NONE : accent;
		association = association == null ? StationAssociation.AUTO : association;
		view = view == null ? BoardView.TRAINS_THIS_SIDE : view;
		final List<ExitSetting> exits = new ArrayList<>();
		if (exitSettings != null) {
			for (final ExitSetting setting : exitSettings) {
				if (setting != null && !setting.isEmpty() && !setting.isDefault() && exits.size() < MAX_EXIT_SETTINGS) {
					exits.add(setting);
				}
			}
		}
		exitSettings = List.copyOf(exits);
	}

	/** Without per-exit settings (1.3 shape). */
	public WayfindingData(boolean autoStation, String stationName, String secondaryName, String stationCode, List<LineBadge> lines, boolean autoLines,
						  SignArrow arrow, String destination, ServiceType serviceType, String serviceLabel, String platform, String exitLabel,
						  String streetLabel, String transfers, LanguageLayout languageLayout, Pictogram pictogram, AccentPalette accent,
						  StationAssociation association, BoardView view) {
		this(autoStation, stationName, secondaryName, stationCode, lines, autoLines, arrow, destination, serviceType, serviceLabel, platform, exitLabel,
				streetLabel, transfers, languageLayout, pictogram, accent, association, view, List.of());
	}

	/**
	 * A copy with a different pictogram; convenience for blocks whose default content is a fixed pictogram.
	 */
	public WayfindingData withPictogram(Pictogram value) {
		return new WayfindingData(autoStation, stationName, secondaryName, stationCode, lines, autoLines, arrow, destination, serviceType, serviceLabel,
				platform, exitLabel, streetLabel, transfers, languageLayout, value, accent, association, view, exitSettings);
	}

	public WayfindingData withAssociation(StationAssociation value) {
		return new WayfindingData(autoStation, stationName, secondaryName, stationCode, lines, autoLines, arrow, destination, serviceType, serviceLabel,
				platform, exitLabel, streetLabel, transfers, languageLayout, pictogram, accent, value, view, exitSettings);
	}

	public WayfindingData withExitSettings(List<ExitSetting> value) {
		return new WayfindingData(autoStation, stationName, secondaryName, stationCode, lines, autoLines, arrow, destination, serviceType, serviceLabel,
				platform, exitLabel, streetLabel, transfers, languageLayout, pictogram, accent, association, view, value);
	}

	public WayfindingData withView(BoardView value) {
		return new WayfindingData(autoStation, stationName, secondaryName, stationCode, lines, autoLines, arrow, destination, serviceType, serviceLabel,
				platform, exitLabel, streetLabel, transfers, languageLayout, pictogram, accent, association, value, exitSettings);
	}

	// ---- NBT -----------------------------------------------------------------------------------------------------------

	public void writeNbt(NbtCompound nbt) {
		final NbtCompound tag = new NbtCompound();
		tag.putBoolean("AutoStation", autoStation);
		tag.putString("Station", stationName);
		tag.putString("Secondary", secondaryName);
		tag.putString("Code", stationCode);
		final NbtList list = new NbtList();
		lines.forEach(badge -> list.add(badge.toNbt()));
		tag.put("Lines", list);
		tag.putBoolean("AutoLines", autoLines);
		tag.putInt("Arrow", arrow.ordinal());
		tag.putString("Destination", destination);
		tag.putInt("Service", serviceType.ordinal());
		tag.putString("ServiceLabel", serviceLabel);
		tag.putString("Platform", platform);
		tag.putString("Exit", exitLabel);
		tag.putString("Street", streetLabel);
		tag.putString("Transfers", transfers);
		tag.putInt("Layout", languageLayout.ordinal());
		tag.putInt("Pictogram", pictogram.ordinal());
		tag.putInt("Accent", accent.ordinal());
		association.writeNbt(tag, "Association");
		tag.putInt("View", view.ordinal());
		if (!exitSettings.isEmpty()) {
			final NbtList exits = new NbtList();
			exitSettings.forEach(setting -> exits.add(setting.toNbt()));
			tag.put("ExitSettings", exits);
		}
		nbt.put(NBT_KEY, tag);
	}

	/**
	 * @param fallback returned when the NBT has no wayfinding data (a freshly placed block's default)
	 */
	public static WayfindingData readNbt(NbtCompound nbt, WayfindingData fallback) {
		if (!nbt.contains(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
			return fallback;
		}
		final NbtCompound tag = nbt.getCompound(NBT_KEY);
		final NbtList list = tag.getList("Lines", NbtElement.COMPOUND_TYPE);
		final List<LineBadge> lines = new ArrayList<>();
		for (int i = 0; i < Math.min(MAX_LINES, list.size()); i++) {
			lines.add(LineBadge.fromNbt(list.getCompound(i)));
		}
		final NbtList exitList = tag.getList("ExitSettings", NbtElement.COMPOUND_TYPE);
		final List<ExitSetting> exits = new ArrayList<>();
		for (int i = 0; i < Math.min(MAX_EXIT_SETTINGS, exitList.size()); i++) {
			exits.add(ExitSetting.fromNbt(exitList.getCompound(i)));
		}
		return new WayfindingData(tag.getBoolean("AutoStation"), tag.getString("Station"), tag.getString("Secondary"), tag.getString("Code"), lines,
				tag.getBoolean("AutoLines"), SignArrow.byOrdinal(tag.getInt("Arrow")), tag.getString("Destination"),
				ServiceType.byOrdinal(tag.getInt("Service")), tag.getString("ServiceLabel"), tag.getString("Platform"), tag.getString("Exit"),
				tag.getString("Street"), tag.getString("Transfers"), LanguageLayout.byOrdinal(tag.getInt("Layout")),
				Pictogram.byOrdinal(tag.getInt("Pictogram")), AccentPalette.byOrdinal(tag.getInt("Accent")),
				StationAssociation.readNbt(tag, "Association"), BoardView.byOrdinal(tag.getInt("View")), exits);
	}

	// ---- packets -------------------------------------------------------------------------------------------------------

	public void write(PacketByteBuf buf) {
		buf.writeBoolean(autoStation);
		buf.writeString(stationName, MAX_NAME * 4);
		buf.writeString(secondaryName, MAX_NAME * 4);
		buf.writeString(stationCode, MAX_CODE * 4);
		buf.writeVarInt(lines.size());
		lines.forEach(badge -> badge.write(buf));
		buf.writeBoolean(autoLines);
		buf.writeVarInt(arrow.ordinal());
		buf.writeString(destination, MAX_DESTINATION * 4);
		buf.writeVarInt(serviceType.ordinal());
		buf.writeString(serviceLabel, MAX_SERVICE_LABEL * 4);
		buf.writeString(platform, MAX_PLATFORM * 4);
		buf.writeString(exitLabel, MAX_EXIT_LABEL * 4);
		buf.writeString(streetLabel, MAX_STREET * 4);
		buf.writeString(transfers, MAX_TRANSFERS * 4);
		buf.writeVarInt(languageLayout.ordinal());
		buf.writeVarInt(pictogram.ordinal());
		buf.writeVarInt(accent.ordinal());
		association.write(buf);
		buf.writeVarInt(view.ordinal());
		buf.writeVarInt(exitSettings.size());
		exitSettings.forEach(setting -> setting.write(buf));
	}

	/**
	 * Reads from an untrusted packet: string reads are length-capped and the badge count is checked before allocation.
	 */
	public static WayfindingData read(PacketByteBuf buf) {
		final boolean autoStation = buf.readBoolean();
		final String station = buf.readString(MAX_NAME * 4);
		final String secondary = buf.readString(MAX_NAME * 4);
		final String code = buf.readString(MAX_CODE * 4);
		final int count = buf.readVarInt();
		if (count < 0 || count > MAX_LINES) {
			throw new IllegalArgumentException("Too many line badges: " + count);
		}
		final List<LineBadge> lines = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			lines.add(LineBadge.read(buf));
		}
		final boolean autoLines = buf.readBoolean();
		final SignArrow arrow = SignArrow.byOrdinal(buf.readVarInt());
		final String destination = buf.readString(MAX_DESTINATION * 4);
		final ServiceType service = ServiceType.byOrdinal(buf.readVarInt());
		final String serviceLabel = buf.readString(MAX_SERVICE_LABEL * 4);
		final String platform = buf.readString(MAX_PLATFORM * 4);
		final String exit = buf.readString(MAX_EXIT_LABEL * 4);
		final String street = buf.readString(MAX_STREET * 4);
		final String transfers = buf.readString(MAX_TRANSFERS * 4);
		final LanguageLayout layout = LanguageLayout.byOrdinal(buf.readVarInt());
		final Pictogram pictogram = Pictogram.byOrdinal(buf.readVarInt());
		final AccentPalette accent = AccentPalette.byOrdinal(buf.readVarInt());
		final StationAssociation association = StationAssociation.read(buf);
		final BoardView view = BoardView.byOrdinal(buf.readVarInt());
		final int exitCount = buf.readVarInt();
		if (exitCount < 0 || exitCount > MAX_EXIT_SETTINGS) {
			throw new IllegalArgumentException("Too many exit settings: " + exitCount);
		}
		final List<ExitSetting> exits = new ArrayList<>(exitCount);
		for (int i = 0; i < exitCount; i++) {
			exits.add(ExitSetting.read(buf));
		}
		return new WayfindingData(autoStation, station, secondary, code, lines, autoLines, arrow, destination, service, serviceLabel, platform, exit, street,
				transfers, layout, pictogram, accent, association, view, exits);
	}
}
