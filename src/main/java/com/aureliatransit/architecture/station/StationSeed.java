package com.aureliatransit.architecture.station;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The generator seed, in one place: what a seed may look like ("seed k7f2ra": one to twelve letters or digits, read in base 36),
 * how it turns into the number in {@link StationSpec#seed()}, and the few ways the planner lets that number change a plan.
 *
 * <p>A twelve-digit base-36 number reaches 36^12 - 1 (about 4.7 x 10^18), which fits a {@code long}, so no valid seed overflows.
 * Seed 0 (also "00", "000", ...) is the plain plan, exactly as planned without a seed.
 *
 * <p><b>What a seed can change.</b> Seeds are many (36^12) but a plan only has a few places where the words leave a choice open,
 * so many seeds plan the very same station. The choices are made in two places and nowhere else:
 * <ul>
 * <li>the parser ({@link StationDescriptionParser}): the platform floor, the roof material, the canopy profile and the hall
 * roof's arch count, each only when the words left it open; they end up in the {@link StationSpec};</li>
 * <li>the planner ({@link StationPlanner}): which of the four platform colours platform 1 starts on ({@link #colourOffset}) and
 * where the first bench stands ({@link #benchStart}).</li>
 * </ul>
 * {@link #effective(StationSpec)} names exactly that, so two specs with the same words and the same effective seed plan the same
 * station, whatever their seeds are. Within one description the distinct plans a seed can reach are therefore bounded by the
 * product of those choices (a few thousand at most, 4 colours x 9 benches x the parser's floor, roof and canopy choices), however
 * long the seed is; a roll of random seeds repeats a station after a few dozen.
 */
public final class StationSeed {

	/** The most characters a seed may have. */
	public static final int MAX_CHARS = 12;
	/** The seed text a station is planned with when it has none. */
	public static final String NONE = "";

	private static final Pattern VALID = Pattern.compile("[a-z0-9]{1," + MAX_CHARS + "}");

	/** Where the first bench stands, from the platform's start, without a seed. */
	static final int PLAIN_BENCH_START = 8;
	/** With a seed the first bench stands from 4 to 4 + BENCH_SPREAD - 1 in. */
	static final int BENCH_SPREAD = 9;

	private StationSeed() {
	}

	/** True for one to twelve ASCII letters or digits (any case). */
	public static boolean valid(String text) {
		return text != null && VALID.matcher(text.toLowerCase(Locale.ROOT)).matches();
	}

	/** The seed's number: base 36, case-insensitive. Throws {@link IllegalArgumentException} for an invalid seed. */
	public static long parse(String text) {
		if (!valid(text)) {
			throw new IllegalArgumentException("a seed is 1 to " + MAX_CHARS + " letters or digits");
		}
		return Long.parseLong(text.toLowerCase(Locale.ROOT), 36);
	}

	/** The seed as the studio shows it (base 36, lower case); empty for no seed. */
	public static String format(long seed) {
		return seed == 0 ? NONE : Long.toString(seed, 36);
	}

	/** Which of {@code colours} platform colours platform 1 starts on. */
	static int colourOffset(long seed, int colours) {
		return Math.floorMod(seed, colours);
	}

	/** How far in from the platform's start the first bench stands. */
	static int benchStart(long seed, int colours) {
		return seed == 0 ? PLAIN_BENCH_START : 4 + (int) Math.floorMod(seed / colours, (long) BENCH_SPREAD);
	}

	/**
	 * Everything that decides a plan, with the seed reduced to what the planner and the palette actually read from it: two specs
	 * with an equal answer plan the same station, and two with different answers plan different ones. Choices the station cannot
	 * show are left out (platform colours are only on a terminus's gate line, benches only with furniture; a hall roof's material
	 * only shows as glass), and a floor or roof is named by the block it ends up as, so a seed that picks what the style would have
	 * picked anyway counts as no choice. Tests use it to check that seeds never plan the same station twice by accident.
	 */
	public static String effective(StationSpec spec) {
		final StylePalette palette = StylePalette.of(spec);
		final int colours = StationPlanner.PLATFORM_COLOURS.size();
		final boolean hall = spec.roof() == RoofType.HALL;
		// canopies and flat or pitched halls are roofed in the palette's roof block; an arched shed is steel cladding, glazed
		// when the roof material says glass, whatever else the palette says
		final boolean roofBlock = spec.roof() == RoofType.CANOPY || hall && !spec.shed().arched();
		final boolean glazedShed = hall && spec.shed().arched();
		return spec.toBuilder().seed(0).surface(SurfaceMaterial.STYLE_DEFAULT).roofMaterial(RoofMaterial.STYLE_DEFAULT).build()
				+ " floor=" + palette.blockId(Role.PLATFORM_SURFACE)
				+ " roof=" + (roofBlock ? palette.blockId(Role.ROOF) : "-") + " glass=" + (glazedShed ? Boolean.toString(palette.roofMaterialGlass()) : "-")
				+ " colour=" + (spec.terminus() ? Integer.toString(colourOffset(spec.seed(), colours)) : "-")
				+ " bench=" + (spec.furniture() ? Integer.toString(benchStart(spec.seed(), colours)) : "-");
	}
}
