package com.aureliatransit.architecture.text;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything an editable sign stores. Every constructor path sanitizes and bounds its input; {@link #restrictedTo}
 * drops the fields a given {@link SignStyle} cannot show.
 *
 * @param primary  main text (the station name). Replaced by the MTR station name while {@code autoName} resolves.
 * @param platform short platform number or badge text
 * @param autoName take the primary text from the MTR station at the sign's position (client-side), falling back to {@code primary}
 */
public record SignData(String primary, String secondary, TextAlignment alignment, AccentPalette accent, SignArrow arrow, String platform,
                       boolean autoName, List<RouteBadge> routes) {

	public static final int MAX_PRIMARY = 32;
	public static final int MAX_SECONDARY = 32;
	public static final int MAX_PLATFORM = 4;
	public static final SignData EMPTY = new SignData("", "", TextAlignment.CENTER, AccentPalette.NONE, SignArrow.NONE, "", false, List.of());
	private static final String KEY = "Sign";
	private static final String KEY_LEGACY_LINES = "Lines";

	public SignData {
		primary = TextSanitizer.sanitize(primary, MAX_PRIMARY);
		secondary = TextSanitizer.sanitize(secondary, MAX_SECONDARY);
		alignment = alignment == null ? TextAlignment.CENTER : alignment;
		accent = accent == null ? AccentPalette.NONE : accent;
		arrow = arrow == null ? SignArrow.NONE : arrow;
		platform = TextSanitizer.sanitize(platform, MAX_PLATFORM);
		final List<RouteBadge> clean = new ArrayList<>();
		if (routes != null) {
			for (int i = 0; i < Math.min(SignStyle.MAX_ROUTES, routes.size()); i++) {
				final RouteBadge badge = routes.get(i);
				if (badge != null && !badge.label().isEmpty()) {
					clean.add(badge);
				}
			}
		}
		routes = List.copyOf(clean);
	}

	/**
	 * Clears the fields the style cannot show so equal content compares equal.
	 */
	public SignData restrictedTo(SignStyle style) {
		return new SignData(primary, style.hasSecondary() ? secondary : "", alignment, accent, style.hasArrow() ? arrow : SignArrow.NONE,
				style.hasPlatform() ? platform : "", style.hasAutoName() && autoName, style.hasRoutes() ? routes : List.of());
	}

	/**
	 * V1 stored up to three plain lines under "Lines": line 0 was the main text (the platform number on a platform
	 * number plate), line 1 the second line.
	 */
	public static SignData fromLegacyLines(List<String> lines, SignStyle style) {
		final String first = lines.isEmpty() ? "" : lines.get(0);
		final String second = lines.size() > 1 ? lines.get(1) : "";
		if (style.isNumberPlate()) {
			return new SignData("", "", TextAlignment.CENTER, AccentPalette.NONE, SignArrow.NONE, first, false, List.of());
		}
		return new SignData(first, style.hasSecondary() ? second : "", TextAlignment.CENTER, AccentPalette.NONE, SignArrow.NONE, "", false, List.of());
	}

	public void writeNbt(NbtCompound nbt) {
		final NbtCompound tag = new NbtCompound();
		tag.putString("Primary", primary);
		tag.putString("Secondary", secondary);
		tag.putInt("Align", alignment.ordinal());
		tag.putInt("Accent", accent.ordinal());
		tag.putInt("Arrow", arrow.ordinal());
		tag.putString("Platform", platform);
		tag.putBoolean("Auto", autoName);
		final NbtList list = new NbtList();
		for (final RouteBadge badge : routes) {
			final NbtCompound route = new NbtCompound();
			route.putString("Label", badge.label());
			route.putInt("Color", badge.color().ordinal());
			list.add(route);
		}
		tag.put("Routes", list);
		nbt.put(KEY, tag);
		// Keep the V1 key so the text survives a downgrade.
		final NbtList legacy = new NbtList();
		legacy.add(NbtString.of(primary.isEmpty() ? platform : primary));
		legacy.add(NbtString.of(secondary));
		nbt.put(KEY_LEGACY_LINES, legacy);
	}

	public static SignData readNbt(NbtCompound nbt, SignStyle style) {
		if (nbt.contains(KEY, NbtElement.COMPOUND_TYPE)) {
			final NbtCompound tag = nbt.getCompound(KEY);
			final NbtList list = tag.getList("Routes", NbtElement.COMPOUND_TYPE);
			final List<RouteBadge> routes = new ArrayList<>();
			for (int i = 0; i < Math.min(SignStyle.MAX_ROUTES, list.size()); i++) {
				final NbtCompound route = list.getCompound(i);
				routes.add(new RouteBadge(route.getString("Label"), AccentPalette.byOrdinal(route.getInt("Color"))));
			}
			return new SignData(tag.getString("Primary"), tag.getString("Secondary"), TextAlignment.byOrdinal(tag.getInt("Align")),
					AccentPalette.byOrdinal(tag.getInt("Accent")), SignArrow.byOrdinal(tag.getInt("Arrow")), tag.getString("Platform"),
					tag.getBoolean("Auto"), routes).restrictedTo(style);
		}
		if (nbt.contains(KEY_LEGACY_LINES, NbtElement.LIST_TYPE)) {
			final NbtList list = nbt.getList(KEY_LEGACY_LINES, NbtElement.STRING_TYPE);
			final List<String> lines = new ArrayList<>();
			for (int i = 0; i < Math.min(3, list.size()); i++) {
				lines.add(list.getString(i));
			}
			return fromLegacyLines(lines, style);
		}
		return EMPTY;
	}

	public void write(PacketByteBuf buf) {
		buf.writeString(primary, MAX_PRIMARY * 4);
		buf.writeString(secondary, MAX_SECONDARY * 4);
		buf.writeVarInt(alignment.ordinal());
		buf.writeVarInt(accent.ordinal());
		buf.writeVarInt(arrow.ordinal());
		buf.writeString(platform, MAX_PLATFORM * 4);
		buf.writeBoolean(autoName);
		buf.writeVarInt(routes.size());
		for (final RouteBadge badge : routes) {
			buf.writeString(badge.label(), RouteBadge.MAX_LABEL * 4);
			buf.writeVarInt(badge.color().ordinal());
		}
	}

	/**
	 * Reads from an untrusted packet: string byte lengths and the route count are bounded before allocation.
	 */
	public static SignData read(PacketByteBuf buf) {
		final String primary = buf.readString(MAX_PRIMARY * 4);
		final String secondary = buf.readString(MAX_SECONDARY * 4);
		final TextAlignment alignment = TextAlignment.byOrdinal(buf.readVarInt());
		final AccentPalette accent = AccentPalette.byOrdinal(buf.readVarInt());
		final SignArrow arrow = SignArrow.byOrdinal(buf.readVarInt());
		final String platform = buf.readString(MAX_PLATFORM * 4);
		final boolean auto = buf.readBoolean();
		final int count = buf.readVarInt();
		if (count < 0 || count > SignStyle.MAX_ROUTES) {
			throw new IllegalArgumentException("Too many route badges: " + count);
		}
		final List<RouteBadge> routes = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			routes.add(new RouteBadge(buf.readString(RouteBadge.MAX_LABEL * 4), AccentPalette.byOrdinal(buf.readVarInt())));
		}
		return new SignData(primary, secondary, alignment, accent, arrow, platform, auto, routes);
	}
}
