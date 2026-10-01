package com.aureliatransit.architecture.block.elevated;

import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

/**
 * The selectable looks of the 1.3 infrastructure blocks. Names are the block state values (and model/lang suffixes), so
 * they are persisted in worlds: only ever append.
 */
public final class ElevatedKinds {

	private ElevatedKinds() {
	}

	private static String id(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	/** Support column: heavy or narrow, steel or concrete. */
	public enum ColumnStyle implements StringIdentifiable {
		STEEL_HEAVY, STEEL_NARROW, CONCRETE, CONCRETE_NARROW;

		@Override
		public String asString() {
			return id(this);
		}
	}

	/** Beam role: all are the same family and join the same columns. */
	public enum BeamKind implements StringIdentifiable {
		/** Deep girder carrying the deck across the tracks. */
		CROSSBEAM,
		/** Box girder running along the line. */
		GIRDER,
		/** Slim I-beam under each rail. */
		STRINGER,
		/** Girder carrying a platform deck. */
		PLATFORM_SUPPORT;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum BraceKind implements StringIdentifiable {
		/** Full-block diagonal between column and beam. */
		DIAGONAL,
		/** Knee bracket carrying a canopy from a column or wall. */
		KNEE;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum EnclosureKind implements StringIdentifiable {
		/** Solid cladding. */
		CLAD,
		/** Cladding with a window. */
		WINDOWED,
		/** Fully glazed in a frame. */
		GLAZED;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum WindscreenKind implements StringIdentifiable {
		/** Glass with a solid kick plate. */
		LOWER,
		/** Glass continuing above a lower screen, with a capping rail. */
		UPPER;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum FasciaKind implements StringIdentifiable {
		PLAIN, PANELLED, RIBBED;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum FenceKind implements StringIdentifiable {
		/** Waist-high platform fencing (rails and pickets). */
		PLATFORM,
		/** Tall trackside safety mesh. */
		TRACKSIDE;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum UtilityKind implements StringIdentifiable {
		TRAY, CONDUIT;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum RailKind implements StringIdentifiable {
		/** Steel handrail with posts. */
		HANDRAIL,
		/** Glass balustrade between posts. */
		BALUSTRADE,
		/** Ramp edge: kerb plus safety rail. */
		RAMP_RAIL;

		@Override
		public String asString() {
			return id(this);
		}
	}

	public enum JunctionKind implements StringIdentifiable {
		/** Guidance line turning 90 degrees. */
		TURN,
		/** Guidance line with a side branch. */
		TEE,
		/** Crossing of two guidance lines. */
		CROSS;

		@Override
		public String asString() {
			return id(this);
		}
	}
}
