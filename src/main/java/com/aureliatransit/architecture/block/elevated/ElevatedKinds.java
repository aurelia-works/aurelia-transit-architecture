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

	/** Plan shape of a platform edge piece for curved or angled track (platform on the south-west side). */
	public enum CurveKind implements StringIdentifiable {
		/** Straight 45 degree edge across the block. */
		DIAGONAL,
		/** Convex quarter round (outside of a curve). */
		OUTER,
		/** Concave quarter round (inside of a curve). */
		INNER;

		@Override
		public String asString() {
			return id(this);
		}

		/**
		 * How far (pixels, from x = 0) the platform reaches in the one-pixel strip at depth {@code z} (0 = north, the
		 * track side). Shared by the collision shape and the generated model, so both follow the same edge line.
		 */
		public double reach(double z) {
			return switch (this) {
				case DIAGONAL -> Math.min(16, z);
				case OUTER -> Math.sqrt(Math.max(0, 256 - (16 - z) * (16 - z)));
				case INNER -> 16 - Math.sqrt(Math.max(0, 256 - z * z));
			};
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

	// ---- 1.4 additions (A12, A10, A8) ------------------------------------------------------------------------------

	/** Trackside noise barrier panel (A12): full or half height, solid absorptive or glass; stack for taller walls. */
	public enum NoiseBarrierKind implements StringIdentifiable {
		/** Absorptive ribbed panel, full block. */
		SOLID,
		/** Absorptive panel, half block. */
		SOLID_HALF,
		/** Framed glass panel, full block. */
		GLASS,
		/** Framed glass panel, half block. */
		GLASS_HALF,
		/** Solid lower half with a glass upper half. */
		SOLID_GLASS;

		@Override
		public String asString() {
			return id(this);
		}

		public boolean half() {
			return this == SOLID_HALF || this == GLASS_HALF;
		}
	}

	/** Fare gate unit (A10, prop only): a cabinet with a passage beside it. Side by side they make a gate bank. */
	public enum FareGateKind implements StringIdentifiable {
		/** Cabinet with glass paddles into its passage. */
		GATE,
		/** Cabinet with long paddles for a wide (accessible) passage. */
		WIDE,
		/** Cabinet alone, closing the end of a bank. */
		END;

		@Override
		public String asString() {
			return id(this);
		}
	}

	/** Fare card reader / validator (A10, prop only). */
	public enum CardReaderKind implements StringIdentifiable {
		/** On a waist-high post. */
		POST,
		/** On a wall. */
		WALL;

		@Override
		public String asString() {
			return id(this);
		}
	}

	/** CCTV camera housing (A10, prop only). */
	public enum CctvKind implements StringIdentifiable {
		/** Box camera on a wall bracket. */
		WALL,
		/** Box camera on a pendant from the ceiling. */
		PENDANT,
		/** Dome under the ceiling. */
		DOME;

		@Override
		public String asString() {
			return id(this);
		}
	}

	/** Lift status shown by a lift status panel (A8): set by hand in its editor, never read from MTR. */
	public enum LiftStatus implements StringIdentifiable {
		IN_SERVICE,
		OUT_OF_SERVICE,
		MAINTENANCE;

		@Override
		public String asString() {
			return id(this);
		}

		public static LiftStatus byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : IN_SERVICE;
		}
	}
}
