package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.elevated.CurvedScreenDoorBlock;
import com.aureliatransit.architecture.block.elevated.FacingKindBlock;
import com.aureliatransit.architecture.block.elevated.PlatformEdgeCurveBlock;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Angled and curved platform edges and their screen doors in every direction: the model each blockstate shows lies where
 * the hit box is, mirroring (structure blocks, schematics) gives the mirror image, and placement picks the left- or
 * right-hand piece.
 */
class CurvedEdgeDirectionsTest {

	private static final String ASSETS = "/assets/aurelia_transit_architecture/";
	/** Sample grid: 16 x 16 points per block, one in each pixel. */
	private static final int GRID = 16;

	private static List<FacingKindBlock<?>> blocks;
	private static List<String> ids;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		final AbstractBlock.Settings settings = AbstractBlock.Settings.create().nonOpaque();
		blocks = List.of(new PlatformEdgeCurveBlock(settings), new CurvedScreenDoorBlock(settings, false), new CurvedScreenDoorBlock(settings, true));
		ids = List.of("platform_edge_curve", "screen_door_panel", "screen_door_doorway");
	}

	private static JsonObject json(String path) throws Exception {
		try (InputStream in = CurvedEdgeDirectionsTest.class.getResourceAsStream(ASSETS + path)) {
			assertNotNull(in, "missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static VoxelShape shape(BlockState state) {
		return state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());
	}

	/** Which grid columns (seen from above) the shape covers at any height. */
	private static boolean[][] footprint(VoxelShape shape) {
		final boolean[][] cells = new boolean[GRID][GRID];
		for (final Box box : shape.getBoundingBoxes()) {
			mark(cells, box.minX * 16, box.minZ * 16, box.maxX * 16, box.maxZ * 16);
		}
		return cells;
	}

	private static void mark(boolean[][] cells, double x0, double z0, double x1, double z1) {
		for (int i = 0; i < GRID; i++) {
			for (int k = 0; k < GRID; k++) {
				// off the half-pixel lines, where box edges are
				final double x = (i + 0.3) * 16 / GRID;
				final double z = (k + 0.3) * 16 / GRID;
				if (x > x0 && x < x1 && z > z0 && z < z1) {
					cells[i][k] = true;
				}
			}
		}
	}

	/** The footprint of the model a blockstate file shows for {@code state}, turned by the variant's "y". */
	private static boolean[][] modelFootprint(String id, BlockState state) throws Exception {
		final JsonObject variants = json("blockstates/" + id + ".json").getAsJsonObject("variants");
		JsonObject variant = null;
		for (final String key : variants.keySet()) {
			if (matches(key, state)) {
				variant = variants.getAsJsonObject(key);
			}
		}
		assertNotNull(variant, id + ": no variant for " + state);
		final String model = variant.get("model").getAsString();
		final int y = variant.has("y") ? variant.get("y").getAsInt() : 0;
		final boolean[][] cells = new boolean[GRID][GRID];
		for (final JsonElement element : json("models/block/" + model.substring(model.indexOf('/') + 1) + ".json").getAsJsonArray("elements")) {
			final var from = element.getAsJsonObject().getAsJsonArray("from");
			final var to = element.getAsJsonObject().getAsJsonArray("to");
			double x0 = from.get(0).getAsDouble();
			double z0 = from.get(2).getAsDouble();
			double x1 = to.get(0).getAsDouble();
			double z1 = to.get(2).getAsDouble();
			// a blockstate "y" turns clockwise seen from above: (x, z) -> (16 - z, x) per quarter
			for (int q = 0; q < y / 90; q++) {
				final double nx0 = 16 - z1;
				final double nx1 = 16 - z0;
				z0 = x0;
				z1 = x1;
				x0 = nx0;
				x1 = nx1;
			}
			mark(cells, x0, z0, x1, z1);
		}
		return cells;
	}

	private static boolean matches(String key, BlockState state) {
		for (final String pair : key.split(",")) {
			final String[] kv = pair.split("=");
			final Property<?> property = state.getBlock().getStateManager().getProperty(kv[0]);
			if (property == null || !name(state, property).equals(kv[1])) {
				return false;
			}
		}
		return true;
	}

	private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
		return property.name(state.get(property));
	}

	/** Share of grid cells on which two footprints agree. */
	private static double agreement(boolean[][] a, boolean[][] b) {
		int same = 0;
		for (int i = 0; i < GRID; i++) {
			for (int k = 0; k < GRID; k++) {
				if (a[i][k] == b[i][k]) {
					same++;
				}
			}
		}
		return same / (double) (GRID * GRID);
	}

	/**
	 * Whether two footprints are the same up to a pixel. Pieces are staircases of 2 px strips across the facing; a turned
	 * staircase steps along the other axis, half a strip off the exact mirror image. (Thin curved screens are checked
	 * through the edge they stand on instead: on their steep end the two staircases differ by more.)
	 */
	private static boolean alike(boolean[][] a, boolean[][] b) {
		return agreement(a, b) > 0.9 && near(a, b, 1) >= 0.95 && near(b, a, 1) >= 0.95;
	}

	/** Share of a's cells that have a cell of b within {@code radius} pixels. */
	private static double near(boolean[][] a, boolean[][] b, int radius) {
		int total = 0;
		int close = 0;
		for (int i = 0; i < GRID; i++) {
			for (int k = 0; k < GRID; k++) {
				if (!a[i][k]) {
					continue;
				}
				total++;
				boolean found = false;
				for (int di = -radius; di <= radius && !found; di++) {
					for (int dk = -radius; dk <= radius && !found; dk++) {
						final int ni = i + di;
						final int nk = k + dk;
						found = ni >= 0 && nk >= 0 && ni < GRID && nk < GRID && b[ni][nk];
					}
				}
				close += found ? 1 : 0;
			}
		}
		return total == 0 ? 1 : close / (double) total;
	}

	private static boolean[][] mirrored(boolean[][] cells, BlockMirror mirror) {
		final boolean[][] out = new boolean[GRID][GRID];
		for (int i = 0; i < GRID; i++) {
			for (int k = 0; k < GRID; k++) {
				// FRONT_BACK swaps east and west (x), LEFT_RIGHT swaps north and south (z)
				out[i][k] = mirror == BlockMirror.FRONT_BACK ? cells[GRID - 1 - i][k] : cells[i][GRID - 1 - k];
			}
		}
		return out;
	}

	private static List<BlockState> states() {
		final List<BlockState> all = new ArrayList<>();
		for (final FacingKindBlock<?> block : blocks) {
			all.addAll(block.getStateManager().getStates());
		}
		return all;
	}

	private static String id(BlockState state) {
		return ids.get(blocks.indexOf((FacingKindBlock<?>) state.getBlock()));
	}

	private static String draw(boolean[][] cells) {
		final StringBuilder out = new StringBuilder("\n");
		for (int k = 0; k < GRID; k++) {
			for (int i = 0; i < GRID; i++) {
				out.append(cells[i][k] ? '#' : '.');
			}
			out.append('\n');
		}
		return out.toString();
	}

	@Test
	void theModelSitsWhereTheHitBoxIs() throws Exception {
		for (final BlockState state : states()) {
			final double agree = agreement(footprint(shape(state)), modelFootprint(id(state), state));
			assertTrue(agree > 0.9, id(state) + " " + state + ": model and hit box agree on only " + agree);
		}
	}

	@Test
	void mirroringGivesTheMirrorImage() {
		for (final BlockState state : blocks.get(0).getStateManager().getStates()) {
			for (final BlockMirror mirror : List.of(BlockMirror.FRONT_BACK, BlockMirror.LEFT_RIGHT)) {
				final BlockState flipped = state.mirror(mirror);
				assertTrue(alike(mirrored(footprint(shape(state)), mirror), footprint(shape(flipped))), state + " " + mirror + " -> " + flipped);
				// a screen on the edge mirrors with it, so it stays on the mirrored edge
				for (final int door : new int[]{1, 2}) {
					final BlockState screen = CurvedScreenDoorBlock.onEdge(blocks.get(door).getDefaultState(), state);
					assertEquals(CurvedScreenDoorBlock.onEdge(blocks.get(door).getDefaultState(), flipped), screen.mirror(mirror), state + " " + mirror);
				}
			}
		}
		// a straight screen only turns
		for (final BlockState state : blocks.get(1).getStateManager().getStates()) {
			if (state.toString().contains("kind=straight")) {
				for (final BlockMirror mirror : List.of(BlockMirror.FRONT_BACK, BlockMirror.LEFT_RIGHT)) {
					assertTrue(alike(mirrored(footprint(shape(state)), mirror), footprint(shape(state.mirror(mirror)))), state + " " + mirror);
				}
			}
		}
	}

	@Test
	void rotatingTurnsTheShape() {
		for (final BlockState state : states()) {
			final boolean[][] once = footprint(shape(state.rotate(BlockRotation.CLOCKWISE_90)));
			final boolean[][] cells = footprint(shape(state));
			final boolean[][] turned = new boolean[GRID][GRID];
			for (int i = 0; i < GRID; i++) {
				for (int k = 0; k < GRID; k++) {
					turned[GRID - 1 - k][i] = cells[i][k];
				}
			}
			assertTrue(agreement(turned, once) > 0.9, id(state) + " " + state);
		}
	}

	@Test
	void theLeftHandPieceIsTheMirrorImageOfTheRightHandOne() {
		for (final BlockState state : blocks.get(0).getStateManager().getStates()) {
			if (state.get(FacingKindBlock.MIRRORED)) {
				continue;
			}
			final Direction facing = state.get(FacingKindBlock.FACING);
			final boolean[][] right = footprint(shape(state));
			final boolean[][] left = footprint(shape(state.with(FacingKindBlock.MIRRORED, true)));
			// mirrored across the facing axis: a north or south piece flips east-west, an east or west one north-south
			final BlockMirror across = facing.getAxis() == Direction.Axis.Z ? BlockMirror.FRONT_BACK : BlockMirror.LEFT_RIGHT;
			assertTrue(alike(mirrored(right, across), left), state.toString());
			assertFalse(agreement(right, left) > 0.98, state + ": the two hands differ");
		}
	}

	@Test
	void aStraightScreenLooksTheSameEitherHand() {
		for (final BlockState state : blocks.get(1).getStateManager().getStates()) {
			if (state.toString().contains("kind=straight")) {
				assertTrue(shape(state.with(FacingKindBlock.MIRRORED, false)) == shape(state.with(FacingKindBlock.MIRRORED, true)), state.toString());
			}
		}
	}

	@Test
	void placementPicksTheHand() {
		// looking north: clicking the floor left of centre gives the left-hand piece
		assertTrue(FacingKindBlock.leftHand(Direction.NORTH, new Vec3d(-0.2, -0.5, 0.1), Direction.UP));
		assertFalse(FacingKindBlock.leftHand(Direction.NORTH, new Vec3d(0.2, -0.5, 0.1), Direction.UP));
		// clicking the side of an edge on your left (its east face) runs a right-hand piece on from it
		assertFalse(FacingKindBlock.leftHand(Direction.NORTH, new Vec3d(-0.5, 0.2, 0), Direction.EAST));
		assertTrue(FacingKindBlock.leftHand(Direction.NORTH, new Vec3d(0.5, 0.2, 0), Direction.WEST));
		// looking east, your left is north (-z)
		assertTrue(FacingKindBlock.leftHand(Direction.EAST, new Vec3d(0, -0.5, -0.3), Direction.UP));
		// a right-hand piece cuts the corner on your right: looking north, the north-east corner is open (track side)
		final BlockState right = blocks.get(0).getDefaultState().with(FacingKindBlock.FACING, Direction.NORTH);
		final boolean[][] cells = footprint(shape(right));
		assertFalse(cells[GRID - 1][0], "north-east corner of a right-hand piece");
		assertTrue(cells[0][GRID - 1], "south-west corner of a right-hand piece");
		final boolean[][] mirroredCells = footprint(shape(right.with(FacingKindBlock.MIRRORED, true)));
		assertFalse(mirroredCells[0][0], "north-west corner of a left-hand piece");
		assertTrue(mirroredCells[GRID - 1][GRID - 1], "south-east corner of a left-hand piece");
	}

	@Test
	void aScreenOnACurvedEdgeFollowsItsEdgeLine() {
		for (final BlockState edge : blocks.get(0).getStateManager().getStates()) {
			for (final int doorway : new int[]{1, 2}) {
				final BlockState screen = CurvedScreenDoorBlock.onEdge(blocks.get(doorway).getDefaultState(), edge);
				final boolean[][] edgeCells = footprint(shape(edge));
				final boolean[][] screenCells = footprint(shape(screen));
				int inside = 0;
				int total = 0;
				for (int i = 0; i < GRID; i++) {
					for (int k = 0; k < GRID; k++) {
						if (screenCells[i][k]) {
							total++;
							inside += edgeCells[i][k] ? 1 : 0;
						}
					}
				}
				assertTrue(total > 0 && inside >= 0.85 * total, edge + " -> " + screen + ": " + inside + " of " + total + " on the platform");
			}
		}
	}
}
