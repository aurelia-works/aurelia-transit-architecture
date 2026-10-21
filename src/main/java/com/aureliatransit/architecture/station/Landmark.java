package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A real station's buildings as a sheet of masses (Jah, 10-21: the presets must look like the real stations, not like a
 * look after them): wall blocks with their window pattern and roof, towers with their caps, domes, giant arched windows,
 * colonnades, gables, clocks and rows of pinnacles or statues, each in its own materials. {@link PlanLandmark} draws them
 * before the generic building, which then only fills what the sheet leaves open (its doors, stairs and bridge mouth are
 * reserved first, so the sheet never closes them).
 * <p>
 * A sheet is drawn in its building's front frame: {@code x} across the street front (0 in its middle, + to the right as
 * seen from the street), {@code z} into the building from the street wall (0 is the wall), {@code y} up from the floor (the
 * walls start at y = 1). A terminus's sheet stands on its head building, a through station's on the building beside the
 * tracks. Sizes are in blocks; the sources and the real sizes are in each sheet's comment in {@link Landmarks}.
 */
public final class Landmark {

	/** Where the sheet stands. */
	public enum Anchor {
		/** On a terminus's head building, its front on the street wall across the track ends. */
		HEAD,
		/** On the building beside the tracks, its front on the street wall. */
		SIDE
	}

	/** The windows of a wall. */
	public enum Window {
		/** Closed wall. */
		NONE,
		/** Square-headed. */
		RECT,
		/** Round-headed: the top corners filled. */
		ROUND,
		/** Pointed (Gothic): the top corners filled and a peak cell over the middle. */
		POINTED,
		/** Long bands of glass between the storey bands. */
		RIBBON,
		/** Glass from floor to roof between thin mullions. */
		CURTAIN
	}

	/** A wall block's roof. */
	public enum Roof {
		/** Flat behind a parapet. */
		FLAT,
		/** Sloping in from all four walls to a ridge or point. */
		HIPPED,
		/** Ridge along x (the front): slopes face the front and back, gable walls at the ends. */
		GABLE_X,
		/** Ridge along z: slopes face the sides, a gable wall on the front. */
		GABLE_Z,
		/** A steep lower slope with dormers, then a flat top with cresting. */
		MANSARD
	}

	/** A tower's top. */
	public enum Cap {
		FLAT,
		/** A pyramid of stairs to a finial. */
		PYRAMID,
		/** A tall narrow pyramid, steeper than the {@link #PYRAMID}. */
		SPIRE,
		/** A round dome. */
		DOME,
		/** An onion dome. */
		ONION,
		/** An open lantern on posts with a small dome. */
		CUPOLA,
		/** A steep French pavilion roof to a flat top with cresting. */
		MANSARD
	}

	/**
	 * The blocks of a mass: walls, trim (pilasters, string courses, quoins, window heads), base (the ground storey's plinth),
	 * window glass, roof (a full block and its stairs), cornice (stairs, hung upside down under the eaves) and accent (bands
	 * of a second colour).
	 */
	public record Skin(String wall, String trim, String base, String glass, String roof, String roofStair, String cornice, String accent) {

		public Skin withWall(String id) {
			return new Skin(id, trim, base, glass, roof, roofStair, cornice, accent);
		}

		public Skin withTrim(String id) {
			return new Skin(wall, id, base, glass, roof, roofStair, cornice, accent);
		}

		public Skin withBase(String id) {
			return new Skin(wall, trim, id, glass, roof, roofStair, cornice, accent);
		}

		public Skin withGlass(String id) {
			return new Skin(wall, trim, base, id, roof, roofStair, cornice, accent);
		}

		public Skin withRoof(String full, String stair) {
			return new Skin(wall, trim, base, glass, full, stair, cornice, accent);
		}

		public Skin withCornice(String stair) {
			return new Skin(wall, trim, base, glass, roof, roofStair, stair, accent);
		}

		public Skin withAccent(String id) {
			return new Skin(wall, trim, base, glass, roof, roofStair, cornice, id);
		}
	}

	/**
	 * A wall's pattern: windows of {@code window} kind, {@code width} wide, in bays {@code bay} wide (a pilaster on each bay
	 * line), one row of them per storey {@code storey} high, their sills {@code sill} rows over the storey's floor and
	 * {@code height} high; {@code baseRows} rows of base at the foot; an accent band every {@code bandEvery} rows (0 for none);
	 * {@code relief} stands the pilasters and string courses a cell proud of the wall.
	 */
	public record Facade(Window window, int bay, int width, int storey, int sill, int height, int baseRows, int bandEvery, boolean relief) {

		public Facade withWindow(Window kind, int w, int h) {
			return new Facade(kind, bay, w, storey, sill, h, baseRows, bandEvery, relief);
		}

		public Facade withBay(int b) {
			return new Facade(window, b, width, storey, sill, height, baseRows, bandEvery, relief);
		}

		public Facade withStorey(int s, int sillRow) {
			return new Facade(window, bay, width, s, sillRow, height, baseRows, bandEvery, relief);
		}

		public Facade withBase(int rows) {
			return new Facade(window, bay, width, storey, sill, height, rows, bandEvery, relief);
		}

		public Facade withBands(int every) {
			return new Facade(window, bay, width, storey, sill, height, baseRows, every, relief);
		}

		public Facade withRelief(boolean r) {
			return new Facade(window, bay, width, storey, sill, height, baseRows, bandEvery, r);
		}
	}

	/** A train shed's cross-section. */
	public enum ShedProfile {
		ROUND, POINTED, GABLE
	}

	/**
	 * The train shed of a real station: its cross-section, its spans across the tracks (1 for one vault over all of them, 0 for
	 * one per platform as the generator does), its rise over its span, its eaves height ({@code eavesY}, 0 for the generator's),
	 * the glass and the cladding of its roof and where the glass runs (between {@code glazedFrom} and {@code glazedTo} of the
	 * vault's height), the colour of its trusses (the roof truss's kind: steel, blue, green or white) and the block of its side
	 * walls under the eaves (null for none).
	 */
	public record Shed(ShedProfile profile, int spans, double rise, int eavesY, String glass, String cladding, double glazedFrom,
			double glazedTo, String truss, String sideWall) {
	}

	/** One piece of a sheet. */
	public sealed interface Mass permits Volume, Tower, Dome, Arch, Colonnade, Gable, Clock, Pinnacles, Ceiling {
	}

	/**
	 * A block of building over x0..x1 by z0..z1 with walls from y = 1 to {@code height}, its roof on top; {@code rise} rows per
	 * step of a pitched roof (1 for 45 degrees, 2 or 3 for steep), {@code openBack} leaves the ground storey of its back wall
	 * open (a head building's platform side) and {@code openFront} its front's (a hall behind another block), {@code dormers}
	 * puts windows in a pitched roof's slopes; its walls start at row {@code bottom} (1 on the ground, higher for an attic
	 * standing on another block's roof).
	 */
	public record Volume(int x0, int x1, int z0, int z1, int height, Skin skin, Facade facade, Roof roof, int rise, boolean openBack,
			boolean dormers, boolean openFront, int bottom) implements Mass {
	}

	/**
	 * A tower of half width {@code r} (2r + 1 across) centred on (x, z), square or octagonal, its shaft from y = {@code y0} to
	 * {@code y0 + height - 1}, then its cap {@code capHeight} high; clock faces on its four sides near the top when
	 * {@code clock}.
	 */
	public record Tower(int x, int z, int r, boolean octagonal, int y0, int height, Skin skin, Cap cap, int capHeight, boolean clock)
			implements Mass {
	}

	/** A dome of radius {@code r} on a drum {@code drum} rows high from y0, centred on (x, z); a lantern on top when asked. */
	public record Dome(int x, int z, int r, int y0, int drum, Skin skin, boolean onion, boolean lantern) implements Mass {
	}

	/**
	 * A giant arched window in the wall plane at depth z (a face looking out to the street when {@code z} is a front), centred
	 * on x, {@code half} cells each side of the middle, from y = {@code bottom} to its springing line at {@code spring}, then a
	 * round or pointed head; glazing bars of {@code skin.trim} every third cell and a ring of trim round it.
	 */
	public record Arch(int x, int z, int half, int bottom, int spring, boolean pointed, Skin skin) implements Mass {
	}

	/** Columns at depth z every {@code every} cells from x0 to x1, {@code height} high, under an entablature row. */
	public record Colonnade(int x0, int x1, int z, int height, int every, String column, Skin skin) implements Mass {
	}

	/** A gable on the wall at depth z over x - half..x + half, from y up {@code rise} rows; stepped (Dutch) or straight. */
	public record Gable(int x, int z, int half, int y, int rise, boolean stepped, Skin skin) implements Mass {
	}

	/** A clock face on the wall at depth z, centred on (x, y), {@code r} cells each side of its middle cell (0 for one block). */
	public record Clock(int x, int y, int z, int r, String face, String rim) implements Mass {
	}

	/**
	 * A hall's ceiling at row y over x0..x1 by z0..z1 in its own block, with a light every {@code every} cells on a staggered
	 * grid (Grand Central's teal sky and its stars); drawn before the walls, so it takes the cells a flat roof would.
	 */
	public record Ceiling(int x0, int x1, int z0, int z1, int y, String block, String light, int every) implements Mass {
	}

	/** Pinnacles (or statues) on a parapet at depth z from x0 to x1 every {@code every} cells, standing on y. */
	public record Pinnacles(int x0, int x1, int z, int y, int every, String shaft, String cap) implements Mass {
	}

	private final String key;
	private final Anchor anchor;
	private final List<String> names;
	private final List<Mass> masses;
	private final Shed shed;

	private Landmark(String key, Anchor anchor, List<String> names, List<Mass> masses, Shed shed) {
		this.key = key;
		this.anchor = anchor;
		this.names = names;
		this.masses = masses;
		this.shed = shed;
	}

	public String key() {
		return key;
	}

	public Anchor anchor() {
		return anchor;
	}

	public List<Mass> masses() {
		return masses;
	}

	/** The train shed's shape and materials, or null to keep the generator's. */
	public Shed shed() {
		return shed;
	}

	/** True when a station named {@code name} is this one. */
	public boolean named(String name) {
		final String n = normal(name);
		return !n.isEmpty() && names.stream().anyMatch(x -> normal(x).equals(n));
	}

	static String normal(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
	}

	public static Builder head(String key, String... names) {
		return new Builder(key, Anchor.HEAD, names);
	}

	public static Builder side(String key, String... names) {
		return new Builder(key, Anchor.SIDE, names);
	}

	/** Builds a sheet; {@link #skin} and {@link #facade} set what the masses after them are made of. */
	public static final class Builder {
		private final String key;
		private final Anchor anchor;
		private final List<String> names;
		private final List<Mass> masses = new ArrayList<>();
		private Skin skin;
		private Facade facade = new Facade(Window.RECT, 4, 2, 5, 1, 3, 1, 0, false);
		private Shed shed;

		private Builder(String key, Anchor anchor, String... names) {
			this.key = key;
			this.anchor = anchor;
			this.names = List.of(names);
		}

		public Builder skin(Skin s) {
			skin = s;
			return this;
		}

		public Builder facade(Facade f) {
			facade = f;
			return this;
		}

		public Builder shed(Shed s) {
			shed = s;
			return this;
		}

		public Builder volume(int x0, int x1, int z0, int z1, int height, Roof roof, int rise) {
			masses.add(new Volume(x0, x1, z0, z1, height, skin, facade, roof, rise, false, false, false, 1));
			return this;
		}

		/** A volume whose ground storey is open at the back (the platform side) and whose pitched roof has dormers. */
		public Builder volume(int x0, int x1, int z0, int z1, int height, Roof roof, int rise, boolean openBack, boolean dormers) {
			masses.add(new Volume(x0, x1, z0, z1, height, skin, facade, roof, rise, openBack, dormers, false, 1));
			return this;
		}

		/** A hall behind the street block, open on its ground storey at both ends, under a flat roof. */
		public Builder hall(int x0, int x1, int z0, int z1, int height) {
			masses.add(new Volume(x0, x1, z0, z1, height, skin, facade, Roof.FLAT, 1, true, false, true, 1));
			return this;
		}

		/** A hall open at the ground at its front and back, under its own roof. */
		public Builder hall(int x0, int x1, int z0, int z1, int height, Roof roof, int rise) {
			masses.add(new Volume(x0, x1, z0, z1, height, skin, facade, roof, rise, true, false, true, 1));
			return this;
		}

		/** A block standing on another's roof: its walls from row {@code bottom} up to {@code height}. */
		public Builder upper(int x0, int x1, int z0, int z1, int bottom, int height, Roof roof, int rise) {
			masses.add(new Volume(x0, x1, z0, z1, height, skin, facade, roof, rise, false, false, false, bottom));
			return this;
		}

		public Builder tower(int x, int z, int r, int y0, int height, Cap cap, int capHeight, boolean clock) {
			masses.add(new Tower(x, z, r, false, y0, height, skin, cap, capHeight, clock));
			return this;
		}

		public Builder octTower(int x, int z, int r, int y0, int height, Cap cap, int capHeight, boolean clock) {
			masses.add(new Tower(x, z, r, true, y0, height, skin, cap, capHeight, clock));
			return this;
		}

		public Builder dome(int x, int z, int r, int y0, int drum, boolean onion, boolean lantern) {
			masses.add(new Dome(x, z, r, y0, drum, skin, onion, lantern));
			return this;
		}

		public Builder arch(int x, int z, int half, int bottom, int spring, boolean pointed) {
			masses.add(new Arch(x, z, half, bottom, spring, pointed, skin));
			return this;
		}

		/** Round-headed arches in the wall at depth z, every {@code every} cells from x0 to x1, each 2 half + 1 wide. */
		public Builder arcade(int x0, int x1, int z, int every, int half, int bottom, int spring) {
			for (int x = x0; x <= x1; x += every) {
				masses.add(new Arch(x, z, half, bottom, spring, false, skin));
			}
			return this;
		}

		public Builder ceiling(int x0, int x1, int z0, int z1, int y, String block, String light, int every) {
			masses.add(new Ceiling(x0, x1, z0, z1, y, block, light, every));
			return this;
		}

		public Builder colonnade(int x0, int x1, int z, int height, int every, String column) {
			masses.add(new Colonnade(x0, x1, z, height, every, column, skin));
			return this;
		}

		public Builder gable(int x, int z, int half, int y, int rise, boolean stepped) {
			masses.add(new Gable(x, z, half, y, rise, stepped, skin));
			return this;
		}

		public Builder clock(int x, int y, int z, int r) {
			masses.add(new Clock(x, y, z, r, Landmarks.CLOCK_FACE, skin.trim()));
			return this;
		}

		/** Chimney stacks of the wall's block on a roof at height y, along x from x0 to x1 at depth z. */
		public Builder chimneys(int x0, int x1, int z, int y, int every) {
			masses.add(new Pinnacles(x0, x1, z, y, every, skin.wall(), "minecraft:flower_pot"));
			return this;
		}

		public Builder pinnacles(int x0, int x1, int z, int y, int every, String shaft, String cap) {
			masses.add(new Pinnacles(x0, x1, z, y, every, shaft, cap));
			return this;
		}

		public Landmark build() {
			return new Landmark(key, anchor, names, List.copyOf(masses), shed);
		}
	}
}
