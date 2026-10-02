package com.aureliatransit.architecture.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Train composition / coach board (1.4, A18), typed by hand: MTR exposes how many cars a train has, but not their
 * class, layout or position, so nothing here is automatic.
 *
 * <p>The board's main text lists the cars left to right, separated by spaces; each token is a car's label ("1", "A12").
 * A trailing {@code +} marks first class (yellow band), a trailing {@code !} an accessible car (blue band), and a
 * {@code |} token a gap between coupled units. The second text lists the platform sector letters, spread evenly under
 * the cars ("A B C D"). Pure; model pixels, origin at the panel centre, y up.
 */
public final class CompositionLayout {

	public static final int MAX_CARS = 16;
	public static final int MAX_SECTORS = 8;
	public static final int CAR = 0xFFDDE3E8;
	public static final int CAR_TEXT = 0xFF1A1F24;
	public static final int FIRST = 0xFFF2C230;
	public static final int ACCESSIBLE = 0xFF2F6FD1;
	public static final int SECTOR = 0xFF9AA5AE;
	private static final float PAD = 0.6F;
	private static final float GAP = 0.4F;
	private static final float UNIT_GAP = 1.2F;

	/** One car as drawn. */
	public record Car(String label, boolean first, boolean accessible, boolean unitBreakBefore) {
	}

	private CompositionLayout() {
	}

	public static List<Car> parse(String text) {
		final List<Car> cars = new ArrayList<>();
		boolean breakNext = false;
		for (final String raw : text.trim().split("\\s+")) {
			if (raw.isEmpty()) {
				continue;
			}
			if (raw.equals("|")) {
				breakNext = !cars.isEmpty();
				continue;
			}
			if (cars.size() >= MAX_CARS) {
				break;
			}
			String label = raw;
			boolean first = false;
			boolean accessible = false;
			while (label.length() > 1 && (label.endsWith("+") || label.endsWith("!"))) {
				first |= label.endsWith("+");
				accessible |= label.endsWith("!");
				label = label.substring(0, label.length() - 1);
			}
			if (label.equals("+") || label.equals("!")) {
				continue;
			}
			cars.add(new Car(label, first, accessible, breakNext));
			breakNext = false;
		}
		return cars;
	}

	public static PanelLayout.Panel layout(String carsText, String sectorsText, float w, float h, int sectorColor, ToIntFunction<String> measure) {
		final List<Car> cars = parse(carsText);
		final List<String> sectors = new ArrayList<>();
		for (final String token : sectorsText.trim().split("\\s+")) {
			if (!token.isEmpty() && sectors.size() < MAX_SECTORS) {
				sectors.add(token);
			}
		}
		if (cars.isEmpty() || w <= 2 * PAD || h <= 2 * PAD) {
			return PanelLayout.Panel.EMPTY;
		}
		final List<PanelLayout.Rect> rects = new ArrayList<>();
		final List<PanelLayout.Label> labels = new ArrayList<>();
		final float inner = w - 2 * PAD;
		int breaks = 0;
		for (final Car car : cars) {
			breaks += car.unitBreakBefore() ? 1 : 0;
		}
		final float carW = (inner - GAP * (cars.size() - 1) - (UNIT_GAP - GAP) * breaks) / cars.size();
		if (carW <= 0) {
			return PanelLayout.Panel.EMPTY;
		}
		final float sectorH = sectors.isEmpty() ? 0 : h * 0.3F;
		final float carH = h - 2 * PAD - sectorH;
		final float carCy = h / 2 - PAD - carH / 2;
		float x = -w / 2 + PAD;
		for (int i = 0; i < cars.size(); i++) {
			final Car car = cars.get(i);
			if (i > 0) {
				x += car.unitBreakBefore() ? UNIT_GAP : GAP;
			}
			final float cx = x + carW / 2;
			rects.add(new PanelLayout.Rect(cx, carCy, carW, carH, CAR));
			if (car.first() || car.accessible()) {
				final float bandH = carH * 0.22F;
				rects.add(new PanelLayout.Rect(cx, carCy - carH / 2 + bandH / 2, carW, bandH, car.accessible() ? ACCESSIBLE : FIRST));
			}
			final int units = Math.max(1, measure.applyAsInt(car.label()));
			final float scale = Math.min(carH * 0.5F / 8F, carW * 0.85F / units);
			labels.add(new PanelLayout.Label(car.label(), cx, carCy + carH * 0.08F, scale, CAR_TEXT, units));
			x += carW;
		}
		if (!sectors.isEmpty()) {
			final float sectorCy = -h / 2 + PAD + sectorH / 2;
			final float slot = inner / sectors.size();
			for (int i = 0; i < sectors.size(); i++) {
				final String sector = sectors.get(i);
				final int units = Math.max(1, measure.applyAsInt(sector));
				final float scale = Math.min(sectorH * 0.8F / 8F, slot * 0.8F / units);
				labels.add(new PanelLayout.Label(sector, -w / 2 + PAD + slot * (i + 0.5F), sectorCy, scale, sectorColor, units));
			}
		}
		return new PanelLayout.Panel(rects, labels);
	}
}
