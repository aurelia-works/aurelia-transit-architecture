package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.text.AccentPalette;
import com.aureliatransit.architecture.text.PanelLayout;
import com.aureliatransit.architecture.text.PanelLayout.Label;
import com.aureliatransit.architecture.text.PanelLayout.Rect;
import com.aureliatransit.architecture.text.SignArrow;
import com.aureliatransit.architecture.text.TextAlignment;
import com.aureliatransit.architecture.text.TextFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Pure layout of a wayfinding panel into rectangles and labels (drawn by the shared PanelDrawer). Model pixels, origin
 * at the panel centre, y up; every label and rectangle lies inside {@code w x h}. Deterministic: equal input, equal
 * output. Callers cache the result keyed by the {@link ResolvedWayfinding} and size; nothing here runs per frame.
 *
 * <p>Rules that hold for every {@link WayfindingPanelKind}:
 * <ul>
 *     <li>Empty fields are skipped and leave no gap.</li>
 *     <li>Nothing overlaps: rectangles of different colours never intersect (the drawer draws all rectangles in one
 *     plane, overlapping ones would z-fight); only same-coloured rectangles are combined (rounded badge corners,
 *     pictogram strokes). Labels sit on top of rectangles.</li>
 *     <li>Text is scaled down (and, for long names on tall panels, wrapped to at most three lines on word boundaries)
 *     until it fits; it is never clipped or altered. At least half of the width always stays with the text: badges,
 *     arrow, pictogram and chips are dropped in priority order when there is no room for them.</li>
 *     <li>{@link LanguageLayout#SIDE_BY_SIDE} splits the name area into a primary and a secondary half with a thin
 *     divider; {@link LanguageLayout#STACKED} puts the secondary below at 60 % size. A side-by-side request on an area
 *     too narrow for two readable halves degrades to stacked. Arabic-script names are laid out as plain left-to-right
 *     text, exactly as Minecraft draws them (no shaping, no bidirectional reordering).</li>
 * </ul>
 */
public final class WayfindingLayout {

	private static final float PAD = 0.75F;
	private static final float GAP = 0.8F;
	private static final float MAX_SCALE = 10.0F;
	private static final float SECONDARY_WEIGHT = 0.6F;
	private static final float SUB_WEIGHT = 0.55F;
	private static final int NEUTRAL_FILL = 0xFFF2F2EE;
	private static final int CHIP_FILL = 0xFF5F6B75;
	private static final int EXPRESS_FILL = 0xFFB23A3A;
	private static final int LIMITED_FILL = 0xFFB26A00;
	private static final String SEPARATOR = " · ";

	private WayfindingLayout() {
	}

	record Box(float l, float r, float b, float t) {

		float w() {
			return r - l;
		}

		float h() {
			return t - b;
		}

		float cx() {
			return (l + r) / 2;
		}

		float cy() {
			return (b + t) / 2;
		}

		boolean isEmpty() {
			return w() <= 0.01F || h() <= 0.01F;
		}
	}

	public static PanelLayout.Panel layout(ResolvedWayfinding r, WayfindingPanelKind kind, float w, float h, int textColor, ToIntFunction<String> measure) {
		final Canvas c = new Canvas(measure);
		final boolean stripe = r.accent() != AccentPalette.NONE && h >= 4;
		if (stripe) {
			c.rect(0, -h / 2 + PanelLayout.STRIPE / 2, w, PanelLayout.STRIPE, r.accent().argb());
		}
		final Box area = new Box(-w / 2 + PAD, w / 2 - PAD, -h / 2 + (stripe ? PanelLayout.STRIPE + 0.5F : PAD * 0.5F), h / 2 - PAD * 0.5F);
		if (area.isEmpty()) {
			return c.panel();
		}
		if (kind == WayfindingPanelKind.ENTRANCE_PYLON) {
			pylon(c, r, area, textColor);
		} else {
			horizontal(c, r, kind, area, textColor);
		}
		return c.panel();
	}

	// ---- horizontal panels ---------------------------------------------------------------------------------------

	private static void horizontal(Canvas c, ResolvedWayfinding r, WayfindingPanelKind kind, Box area, int textColor) {
		final float ah = area.h();
		final boolean hasText = hasAnyText(r);
		final float budget = hasText ? area.w() * 0.55F : area.w();
		float used = 0;
		float left = area.l();
		float right = area.r();

		// 1. arrow
		if (kind != WayfindingPanelKind.BUS_STOP && r.arrow() != SignArrow.NONE) {
			final float cell = ah * 0.7F;
			if (used + cell + GAP <= budget) {
				final boolean onLeft = r.arrow().onLeft();
				final Box box = onLeft ? new Box(left, left + cell, area.b(), area.t()) : new Box(right - cell, right, area.b(), area.t());
				c.glyph(r.arrow().glyph(), box, 0.9F, textColor);
				used += cell + GAP;
				if (onLeft) {
					left += cell + GAP;
				} else {
					right -= cell + GAP;
				}
			}
		}

		// 2. platform / exit badge (left)
		final String badgeText = kind == WayfindingPanelKind.EXIT ? r.exitLabel()
				: kind == WayfindingPanelKind.PLATFORM || kind == WayfindingPanelKind.WALL_DIRECTION || kind == WayfindingPanelKind.HANGING_DIRECTION ? r.platform() : "";
		if (!badgeText.isEmpty()) {
			final float bw = c.badgeWidth(badgeText, ah);
			if (used + bw + GAP <= budget) {
				final int fill = r.accent() == AccentPalette.NONE ? NEUTRAL_FILL : r.accent().argb();
				c.plate(badgeText, new Box(left, left + bw, area.b(), area.t()), fill);
				used += bw + GAP;
				left += bw + GAP;
			}
		}

		// 3. pictogram (left)
		if (r.pictogram() != Pictogram.NONE) {
			final float wanted = kind == WayfindingPanelKind.PICTOGRAM ? ah : ah * 0.85F;
			final float cell = hasText ? wanted : Math.min(wanted, Math.max(0, budget - GAP));
			if (used + cell + GAP <= budget) {
				// a pictogram with nothing else to show is centred
				final float x = hasText || left != area.l() || right != area.r() ? left : area.cx() - cell / 2;
				WayfindingPictograms.draw(c, r.pictogram(), new Box(x, x + cell, area.b(), area.t()), textColor);
				used += cell + GAP;
				left += cell + GAP;
			}
		}

		// 4. chips (right): service label, station code
		final boolean chipKind = kind != WayfindingPanelKind.EXIT && kind != WayfindingPanelKind.PICTOGRAM;
		final float chipH = Math.min(ah * 0.55F, 4.2F);
		String serviceForSub = "";
		if (chipKind && !r.serviceText().isEmpty()) {
			final String text = r.serviceText().toUpperCase(Locale.ROOT);
			final float cw = c.chipWidth(text, chipH);
			if (ah >= 5 && used + cw + GAP <= budget) {
				c.chip(text, serviceFill(r.serviceType()), new Box(right - cw, right, area.cy() - chipH / 2, area.cy() + chipH / 2));
				used += cw + GAP;
				right -= cw + GAP;
			} else {
				serviceForSub = r.serviceText();
			}
		}
		if (chipKind && kind != WayfindingPanelKind.BUS_STOP && !r.stationCode().isEmpty() && ah >= 5) {
			final float cw = c.chipWidth(r.stationCode(), chipH);
			if (used + cw + GAP <= budget) {
				c.chip(r.stationCode(), CHIP_FILL, new Box(right - cw, right, area.cy() - chipH / 2, area.cy() + chipH / 2));
				used += cw + GAP;
				right -= cw + GAP;
			}
		}

		// 5. line badges: beside the text on wide panels, else in a row below it
		float lineRow = 0;
		if (!r.lines().isEmpty()) {
			final boolean wide = kind != WayfindingPanelKind.BUS_STOP && area.w() >= 2.6F * ah && ah >= 5;
			final float bh = Math.min(ah * 0.72F, 5.0F);
			boolean placed = false;
			if (wide) {
				final float want = c.badgeRowWidth(r.lines(), bh, budget - used - GAP);
				if (want >= r.lines().size() * bh * 0.75F) {
					c.badgeRow(r.lines(), new Box(right - want, right, area.cy() - bh / 2, area.cy() + bh / 2), TextAlignment.RIGHT);
					used += want + GAP;
					right -= want + GAP;
					placed = true;
				}
			}
			if (!placed && ah >= 5.5F) {
				lineRow = Math.min(ah * 0.4F, 3.6F);
			}
		}

		final Box text = new Box(left, right, area.b() + (lineRow > 0 ? lineRow + GAP * 0.5F : 0), area.t());
		if (lineRow > 0) {
			c.badgeRow(r.lines(), new Box(left, right, area.b(), area.b() + lineRow), kind == WayfindingPanelKind.BUS_STOP ? TextAlignment.LEFT : TextAlignment.CENTER);
		}
		texts(c, r, kind, text, textColor, serviceForSub, ah);
	}

	private static boolean hasAnyText(ResolvedWayfinding r) {
		return !(r.stationName().isEmpty() && r.secondaryName().isEmpty() && r.destination().isEmpty() && r.streetLabel().isEmpty() && r.transfers().isEmpty()
				&& r.exitDestinations().isEmpty() && r.exitLabel().isEmpty() && r.platform().isEmpty() && r.serviceText().isEmpty() && r.stationCode().isEmpty()
				&& r.lines().isEmpty());
	}

	/**
	 * Chooses the main and sub text of a kind and places them in the text box.
	 */
	private static void texts(Canvas c, ResolvedWayfinding r, WayfindingPanelKind kind, Box box, int textColor, String serviceForSub, float areaH) {
		if (box.isEmpty()) {
			return;
		}
		String main;
		boolean mainIsStation = false;
		String sub = "";
		switch (kind) {
			case PLATFORM -> {
				main = first(r.destination());
				if (main.isEmpty()) {
					main = r.stationName();
					mainIsStation = true;
				} else {
					sub = first(r.stationName(), r.transfers(), r.streetLabel());
				}
				if (mainIsStation) {
					sub = first(r.transfers(), r.streetLabel());
				}
			}
			case STREET -> {
				main = first(r.streetLabel());
				if (main.isEmpty()) {
					main = first(r.stationName(), r.destination());
					mainIsStation = main.equals(r.stationName());
				}
				sub = first(r.transfers(), r.destination(), mainIsStation ? "" : r.stationName());
			}
			case PICTOGRAM -> {
				main = first(r.destination(), r.streetLabel(), r.stationName(), r.exitLabel());
				mainIsStation = !r.stationName().isEmpty() && main.equals(r.stationName());
				sub = first(r.transfers(), r.streetLabel());
			}
			case EXIT -> {
				final List<String> destinations = r.exitDestinations();
				if (!destinations.isEmpty()) {
					main = destinations.get(0);
					sub = destinations.size() > 1 ? String.join(SEPARATOR, destinations.subList(1, destinations.size())) : first(r.transfers());
				} else {
					main = first(r.streetLabel(), r.stationName());
					mainIsStation = main.equals(r.stationName());
					sub = first(r.transfers(), r.destination());
				}
			}
			case BUS_STOP -> {
				main = first(r.stationName(), r.streetLabel(), r.destination());
				mainIsStation = main.equals(r.stationName());
				sub = first(r.destination(), r.streetLabel(), r.transfers());
			}
			default -> {
				// WALL_DIRECTION, HANGING_DIRECTION
				main = first(r.destination());
				if (main.isEmpty()) {
					main = r.stationName();
					mainIsStation = true;
					sub = first(r.transfers(), r.streetLabel());
				} else {
					sub = first(r.transfers(), r.streetLabel(), r.stationName());
				}
			}
		}
		if (main.isEmpty() && !r.secondaryName().isEmpty() && r.languageLayout() != LanguageLayout.SINGLE) {
			main = r.secondaryName();
		}
		if (!serviceForSub.isEmpty()) {
			// no room for the service tag: the stopping pattern outranks the secondary note
			sub = serviceForSub;
		}
		if (main.equals(sub)) {
			sub = "";
		}
		if (main.isEmpty()) {
			main = sub;
			sub = "";
		}
		if (main.isEmpty()) {
			return;
		}
		final boolean withSub = !sub.isEmpty() && areaH >= 6.5F;
		final TextAlignment align = TextAlignment.LEFT;
		final String secondary = mainIsStation ? r.secondaryName() : "";
		final int maxLines = box.h() >= 12 ? 2 : 1;
		if (!withSub) {
			c.nameBlock(main, secondary, r.languageLayout(), box, maxLines, align, textColor);
			return;
		}
		final float split = box.b() + box.h() * (SUB_WEIGHT / (1 + SUB_WEIGHT));
		final Box mainBox = new Box(box.l(), box.r(), split, box.t());
		final Box subBox = new Box(box.l(), box.r(), box.b(), split);
		final float scale = c.nameBlock(main, secondary, r.languageLayout(), mainBox, 1, align, textColor);
		c.text(sub, subBox, scale > 0 ? Math.max(0.3F, scale * 0.65F) : MAX_SCALE * 0.65F, textColor, align);
	}

	// ---- entrance pylon ------------------------------------------------------------------------------------------

	private static void pylon(Canvas c, ResolvedWayfinding r, Box area, int textColor) {
		final boolean hasName = !r.stationName().isEmpty() || !r.secondaryName().isEmpty();
		final boolean hasChips = !r.stationCode().isEmpty() || !r.serviceText().isEmpty();
		final boolean hasLines = !r.lines().isEmpty();
		final boolean hasExit = !r.exitLabel().isEmpty() || r.pictogram() != Pictogram.NONE || !r.streetLabel().isEmpty();
		// rows top to bottom: name, chips, lines, exit; dropped from the bottom up when they would be too small
		final float[] weights = {hasName ? 4F : 0, hasChips ? 1.3F : 0, hasLines ? 1.8F : 0, hasExit ? 1.9F : 0};
		while (true) {
			float total = 0;
			int rows = 0;
			for (final float weight : weights) {
				total += weight;
				rows += weight > 0 ? 1 : 0;
			}
			if (total <= 0) {
				return;
			}
			final float usable = area.h() - (rows - 1) * GAP;
			boolean dropped = false;
			for (int i = weights.length - 1; i > 0 && !dropped; i--) {
				if (weights[i] > 0 && usable * weights[i] / total < 2.2F) {
					weights[i] = 0;
					dropped = true;
				}
			}
			if (!dropped) {
				break;
			}
		}
		float total = 0;
		int rows = 0;
		for (final float weight : weights) {
			total += weight;
			rows += weight > 0 ? 1 : 0;
		}
		final float usable = area.h() - (rows - 1) * GAP;
		float top = area.t();
		for (int i = 0; i < weights.length; i++) {
			if (weights[i] <= 0) {
				continue;
			}
			final float rowH = usable * weights[i] / total;
			final Box row = new Box(area.l(), area.r(), top - rowH, top);
			top -= rowH + GAP;
			switch (i) {
				case 0 -> c.nameBlock(r.stationName(), r.secondaryName(), r.languageLayout(), row, 3, TextAlignment.CENTER, textColor);
				case 1 -> pylonChips(c, r, row);
				case 2 -> c.badgeRow(r.lines(), new Box(row.l(), row.r(), row.cy() - Math.min(row.h(), 5F) / 2, row.cy() + Math.min(row.h(), 5F) / 2), TextAlignment.CENTER);
				default -> pylonExit(c, r, row, textColor);
			}
		}
	}

	private static void pylonChips(Canvas c, ResolvedWayfinding r, Box row) {
		final float ch = Math.min(row.h() * 0.85F, 4.2F);
		final String code = r.stationCode();
		final String service = r.serviceText().toUpperCase(Locale.ROOT);
		float codeW = code.isEmpty() ? 0 : c.chipWidth(code, ch);
		float serviceW = service.isEmpty() ? 0 : c.chipWidth(service, ch);
		final float avail = row.w();
		final float both = codeW + serviceW + (codeW > 0 && serviceW > 0 ? GAP : 0);
		if (both > avail) {
			final float k = avail / both;
			codeW *= k;
			serviceW *= k;
		}
		final float total = codeW + serviceW + (codeW > 0 && serviceW > 0 ? GAP : 0);
		float x = row.cx() - total / 2;
		if (codeW > 0) {
			c.chip(code, CHIP_FILL, new Box(x, x + codeW, row.cy() - ch / 2, row.cy() + ch / 2));
			x += codeW + GAP;
		}
		if (serviceW > 0) {
			c.chip(service, serviceFill(r.serviceType()), new Box(x, x + serviceW, row.cy() - ch / 2, row.cy() + ch / 2));
		}
	}

	private static void pylonExit(Canvas c, ResolvedWayfinding r, Box row, int textColor) {
		final float cell = Math.min(row.h(), row.w() * 0.4F);
		float x = row.l();
		if (r.pictogram() != Pictogram.NONE) {
			WayfindingPictograms.draw(c, r.pictogram(), new Box(x, x + cell, row.b(), row.t()), textColor);
			x += cell + GAP;
		}
		if (!r.exitLabel().isEmpty() && x < row.r()) {
			final float bw = Math.min(c.badgeWidth(r.exitLabel(), row.h()), Math.max(0, (row.r() - x) * 0.6F));
			if (bw > 0) {
				final int fill = r.accent() == AccentPalette.NONE ? NEUTRAL_FILL : r.accent().argb();
				c.plate(r.exitLabel(), new Box(x, x + bw, row.b(), row.t()), fill);
				x += bw + GAP;
			}
		}
		if (!r.streetLabel().isEmpty() && x < row.r()) {
			c.text(r.streetLabel(), new Box(x, row.r(), row.b(), row.t()), MAX_SCALE, textColor, TextAlignment.LEFT);
		}
	}

	// ---- shared helpers ------------------------------------------------------------------------------------------

	private static String first(String... values) {
		for (final String value : values) {
			if (!value.isEmpty()) {
				return value;
			}
		}
		return "";
	}

	private static int serviceFill(ServiceType type) {
		return switch (type) {
			case EXPRESS -> EXPRESS_FILL;
			case LIMITED -> LIMITED_FILL;
			default -> CHIP_FILL;
		};
	}

	/**
	 * Collects rectangles and labels; also the drawing surface of {@link WayfindingPictograms}.
	 */
	static final class Canvas {

		private final List<Rect> rects = new ArrayList<>();
		private final List<Label> labels = new ArrayList<>();
		private final ToIntFunction<String> measure;

		private Canvas(ToIntFunction<String> measure) {
			this.measure = measure;
		}

		PanelLayout.Panel panel() {
			return new PanelLayout.Panel(List.copyOf(rects), List.copyOf(labels));
		}

		void rect(float cx, float cy, float w, float h, int argb) {
			if (w > 0.01F && h > 0.01F) {
				rects.add(new Rect(cx, cy, w, h, argb));
			}
		}

		/** A rectangle given by edges. */
		void edges(float l, float r, float b, float t, int argb) {
			rect((l + r) / 2, (b + t) / 2, r - l, t - b, argb);
		}

		/**
		 * One line of text in a box. Returns the scale used (0 when nothing was drawn).
		 */
		float text(String text, Box box, float maxScale, int argb, TextAlignment align) {
			if (text.isEmpty() || box.isEmpty()) {
				return 0;
			}
			final int units = measure.applyAsInt(text);
			final float scale = TextFit.fitOne(units, box.w(), box.h(), maxScale);
			if (scale <= 0) {
				return 0;
			}
			final float pw = units * scale;
			final float cx = switch (align) {
				case LEFT -> box.l() + pw / 2;
				case RIGHT -> box.r() - pw / 2;
				default -> box.cx();
			};
			labels.add(new Label(text, cx, box.cy(), scale, argb, units));
			return scale;
		}

		/** A centred glyph (arrow, symbol) filling at most {@code fraction} of the box. */
		void glyph(String glyph, Box box, float fraction, int argb) {
			final Box inner = shrink(box, fraction);
			text(glyph, inner, MAX_SCALE, argb, TextAlignment.CENTER);
		}

		float badgeWidth(String text, float height) {
			final float units = measure.applyAsInt(text);
			final float heightScale = height * 0.68F / TextFit.ROW;
			return Math.min(height * 2, Math.max(height * 0.8F, units * heightScale + 1.6F));
		}

		float chipWidth(String text, float height) {
			final float units = measure.applyAsInt(text);
			return Math.max(height * 1.2F, units * (height * 0.72F / TextFit.ROW) + 1.6F);
		}

		/** A filled plate with a centred, contrast-coloured label (platform or exit badge). */
		void plate(String text, Box box, int fill) {
			rect(box.cx(), box.cy(), box.w(), box.h(), fill);
			final Box inner = new Box(box.l() + 0.6F, box.r() - 0.6F, box.b(), box.t());
			text(text, shrinkHeight(inner, 0.68F), MAX_SCALE, TextFit.contrastText(fill & 0xFFFFFF), TextAlignment.CENTER);
		}

		/** A small rounded-corner tag with a label (service type, station code). */
		void chip(String text, int fill, Box box) {
			final float corner = Math.min(box.h(), box.w()) * 0.2F;
			rounded(box, corner, fill);
			text(text, shrinkHeight(new Box(box.l() + 0.5F, box.r() - 0.5F, box.b(), box.t()), 0.78F), MAX_SCALE, TextFit.contrastText(fill & 0xFFFFFF), TextAlignment.CENTER);
		}

		private void rounded(Box box, float corner, int fill) {
			if (corner <= 0.05F) {
				rect(box.cx(), box.cy(), box.w(), box.h(), fill);
				return;
			}
			rect(box.cx(), box.cy(), box.w() - 2 * corner, box.h(), fill);
			rect(box.cx(), box.cy(), box.w(), box.h() - 2 * corner, fill);
		}

		private void badge(LineBadge badge, Box box) {
			final int fill = badge.argb();
			final Box shape;
			final float corner;
			switch (badge.shape()) {
				case CIRCLE -> {
					final float side = Math.min(box.w(), box.h());
					shape = new Box(box.cx() - side / 2, box.cx() + side / 2, box.cy() - side / 2, box.cy() + side / 2);
					corner = side * 0.3F;
				}
				case SQUARE -> {
					shape = box;
					corner = 0;
				}
				default -> {
					shape = box;
					corner = Math.min(box.w(), box.h()) * 0.18F;
				}
			}
			rounded(shape, corner, fill);
			text(badge.label(), shrink(shape, 0.84F), MAX_SCALE, TextFit.contrastText(badge.rgb()), TextAlignment.CENTER);
		}

		/**
		 * Width the badge row would use inside {@code maxWidth} at badge height {@code height}.
		 */
		float badgeRowWidth(List<LineBadge> lines, float height, float maxWidth) {
			if (lines.isEmpty() || maxWidth <= 0) {
				return 0;
			}
			float total = (lines.size() - 1) * GAP * 0.6F;
			for (final LineBadge line : lines) {
				total += naturalBadgeWidth(line, height);
			}
			return Math.min(total, maxWidth);
		}

		private float naturalBadgeWidth(LineBadge line, float height) {
			if (line.shape() == BadgeShape.CIRCLE) {
				return height;
			}
			final float units = measure.applyAsInt(line.label());
			return Math.max(height, Math.min(height * 2.2F, units * (height * 0.7F / TextFit.ROW) + 1.6F));
		}

		/**
		 * Badges in one row inside the box (shrunk uniformly when they do not fit), aligned per {@code align}.
		 */
		void badgeRow(List<LineBadge> lines, Box box, TextAlignment align) {
			if (lines.isEmpty() || box.isEmpty()) {
				return;
			}
			final float gap = GAP * 0.6F;
			float total = (lines.size() - 1) * gap;
			for (final LineBadge line : lines) {
				total += naturalBadgeWidth(line, box.h());
			}
			final float k = total > box.w() ? box.w() / total : 1;
			float x = switch (align) {
				case LEFT -> box.l();
				case RIGHT -> box.r() - total * k;
				default -> box.cx() - total * k / 2;
			};
			for (final LineBadge line : lines) {
				final float bw = naturalBadgeWidth(line, box.h()) * k;
				badge(line, new Box(x, x + bw, box.b(), box.t()));
				x += bw + gap * k;
			}
		}

		/**
		 * Station name area: primary, plus the secondary language per {@code layout}. Returns the scale of the primary
		 * text (0 when nothing was drawn).
		 */
		float nameBlock(String primary, String secondary, LanguageLayout layout, Box box, int maxLines, TextAlignment align, int argb) {
			if (box.isEmpty()) {
				return 0;
			}
			final boolean second = layout != LanguageLayout.SINGLE && !secondary.isEmpty();
			if (primary.isEmpty() && second) {
				return stackLines(wrap(secondary, maxLines, "", 0, box), "", 0, box, align, argb);
			}
			if (primary.isEmpty()) {
				return 0;
			}
			if (second && layout == LanguageLayout.SIDE_BY_SIDE && box.w() >= 2.2F * box.h()) {
				final float divider = 0.3F;
				final float mid = box.cx();
				final float half = (box.w() - GAP * 2 - divider) / 2;
				final Box leftBox = new Box(box.l(), box.l() + half, box.b(), box.t());
				final Box rightBox = new Box(box.r() - half, box.r(), box.b(), box.t());
				final float scale = stackLines(wrap(primary, maxLines, "", 0, leftBox), "", 0, leftBox, TextAlignment.CENTER, argb);
				stackLines(wrap(secondary, maxLines, "", 0, rightBox), "", 0, rightBox, TextAlignment.CENTER, argb);
				rect(mid, box.cy(), divider, box.h() * 0.8F, argb);
				return scale;
			}
			if (second) {
				return stackLines(wrap(primary, maxLines, secondary, SECONDARY_WEIGHT, box), secondary, SECONDARY_WEIGHT, box, align, argb);
			}
			return stackLines(wrap(primary, maxLines, "", 0, box), "", 0, box, align, argb);
		}

		/**
		 * Splits {@code text} on spaces into up to {@code maxLines} lines, choosing the line count (and cut points) that
		 * gives the largest common scale in the box together with an optional tail row. Fewer lines win unless more lines
		 * are at least 8 % larger.
		 */
		private String[] wrap(String text, int maxLines, String tail, float tailWeight, Box box) {
			final String[] words = text.split("\\s+");
			String[] best = {text};
			float bestScale = stackScale(best, tail, tailWeight, box);
			for (int n = 2; n <= maxLines && n <= words.length; n++) {
				final String[] candidate = split(words, n);
				final float scale = stackScale(candidate, tail, tailWeight, box);
				if (scale > bestScale * 1.08F) {
					best = candidate;
					bestScale = scale;
				}
			}
			return best;
		}

		private float stackScale(String[] lines, String tail, float tailWeight, Box box) {
			final int n = lines.length + (tail.isEmpty() ? 0 : 1);
			final float[] widths = new float[n];
			final float[] weights = new float[n];
			for (int i = 0; i < lines.length; i++) {
				widths[i] = measure.applyAsInt(lines[i]);
				weights[i] = 1;
			}
			if (!tail.isEmpty()) {
				widths[n - 1] = measure.applyAsInt(tail);
				weights[n - 1] = tailWeight;
			}
			return TextFit.fitStack(widths, weights, box.w(), box.h(), MAX_SCALE);
		}

		/** Word split into {@code n} lines minimising the widest line; first cut wins ties. */
		private String[] split(String[] words, int n) {
			final int m = words.length;
			String[] best = null;
			float bestWidth = Float.MAX_VALUE;
			if (n == 2) {
				for (int i = 1; i < m; i++) {
					final String[] candidate = {join(words, 0, i), join(words, i, m)};
					final float width = Math.max(measure.applyAsInt(candidate[0]), measure.applyAsInt(candidate[1]));
					if (width < bestWidth) {
						bestWidth = width;
						best = candidate;
					}
				}
			} else {
				for (int i = 1; i < m - 1; i++) {
					for (int j = i + 1; j < m; j++) {
						final String[] candidate = {join(words, 0, i), join(words, i, j), join(words, j, m)};
						final float width = Math.max(measure.applyAsInt(candidate[0]), Math.max(measure.applyAsInt(candidate[1]), measure.applyAsInt(candidate[2])));
						if (width < bestWidth) {
							bestWidth = width;
							best = candidate;
						}
					}
				}
			}
			return best == null ? new String[]{String.join(" ", words)} : best;
		}

		private static String join(String[] words, int from, int to) {
			return String.join(" ", java.util.Arrays.copyOfRange(words, from, to));
		}

		/**
		 * Places lines (weight 1) plus an optional tail row at one common scale, vertically centred in the box.
		 */
		private float stackLines(String[] lines, String tail, float tailWeight, Box box, TextAlignment align, int argb) {
			final int n = lines.length + (tail.isEmpty() ? 0 : 1);
			final String[] texts = new String[n];
			final float[] weights = new float[n];
			final float[] widths = new float[n];
			float totalWeight = 0;
			for (int i = 0; i < n; i++) {
				final boolean isTail = i == lines.length;
				texts[i] = isTail ? tail : lines[i];
				weights[i] = isTail ? tailWeight : 1;
				widths[i] = measure.applyAsInt(texts[i]);
				totalWeight += weights[i];
			}
			final float scale = TextFit.fitStack(widths, weights, box.w(), box.h(), MAX_SCALE);
			if (scale <= 0) {
				return 0;
			}
			float y = box.cy() + totalWeight * TextFit.ROW * scale / 2;
			for (int i = 0; i < n; i++) {
				final float rowH = weights[i] * TextFit.ROW * scale;
				final float cy = y - rowH / 2;
				y -= rowH;
				if (texts[i].isEmpty()) {
					continue;
				}
				final float pw = widths[i] * weights[i] * scale;
				final float cx = switch (align) {
					case LEFT -> box.l() + pw / 2;
					case RIGHT -> box.r() - pw / 2;
					default -> box.cx();
				};
				labels.add(new Label(texts[i], cx, cy, scale * weights[i], argb, (int) widths[i]));
			}
			return scale;
		}

		private static Box shrink(Box box, float fraction) {
			final float dw = box.w() * (1 - fraction) / 2;
			final float dh = box.h() * (1 - fraction) / 2;
			return new Box(box.l() + dw, box.r() - dw, box.b() + dh, box.t() - dh);
		}

		private static Box shrinkHeight(Box box, float fraction) {
			final float dh = box.h() * (1 - fraction) / 2;
			return new Box(box.l(), box.r(), box.b() + dh, box.t() - dh);
		}
	}
}
